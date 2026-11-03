package ir.vil3ntec.tohid.ios

import android.content.Context
import android.content.IosContext
import android.location.Location
import android.location.LocationManager
import ir.vil3ntec.tohid.AppStart
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.DeepLink
import ir.vil3ntec.tohid.data.DeviceLocation
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.TillSale
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.pay.HesabPaySettle
import ir.vil3ntec.tohid.scan.ScanGate
import ir.vil3ntec.tohid.ui.v3.modes.ModeState
import ir.vil3ntec.tohid.update.ReleaseVersion
import ir.vil3ntec.tohid.update.Updater
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  گامِ ۷ ِ آیفون روی شبیه‌ساز (کارِ `ios-app`؛ روی لینوکس فقط کامپایل می‌شود): **خودِ** `data/DeviceLocation.kt` ِ
 *  اندروید روی shim ِ `android.location`، منطقی که از جایگزین‌ها به کدِ مشترک رفت (`AppStart`، `ScanGate`،
 *  `ReleaseVersion`)، و کارهای دستهٔ ۱۴ و ۱۵ روی آیفون: اثرِ انگشتِ حسابِ گیرندهٔ حساب‌پی (حالتِ ساده)، دری و یادداشتِ
 *  فاکتورِ صندوقِ گوشی (`TillSale.record`).
 */
@OptIn(ExperimentalForeignApi::class)
class Phase7IosTest {
  private fun fix(lat: Double, lng: Double, meters: Double, at: Double) = CLLocation(
    coordinate = CLLocationCoordinate2DMake(lat, lng),
    altitude = 0.0,
    horizontalAccuracy = meters,
    verticalAccuracy = -1.0,
    timestamp = NSDate.dateWithTimeIntervalSince1970(at),
  )

  @Test
  fun coreLocationFixLooksLikeAnAndroidLocation() {
    val near = Location.of(fix(34.5281, 69.1723, 20.0, 1_700_000_000.0))
    assertEquals(LocationManager.GPS_PROVIDER, near.provider)
    assertEquals(34.5281, near.latitude)
    assertEquals(69.1723, near.longitude)
    assertTrue(near.hasAccuracy())
    assertEquals(20f, near.accuracy)
    assertEquals(1_700_000_000_000L, near.time)
    //  دقتِ درشت ⇐ «network» (همان برچسبی که سرور از اندروید می‌گیرد)؛ دقتِ نامعلوم ⇐ hasAccuracy = false
    assertEquals(LocationManager.NETWORK_PROVIDER, Location.of(fix(34.5, 69.1, 650.0, 1.0)).provider)
    assertFalse(Location.of(fix(34.5, 69.1, -1.0, 1.0)).hasAccuracy())
  }

  @Test
  fun androidDeviceLocationRunsOnTheShim() {
    val manager = IosContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    assertEquals(listOf(LocationManager.GPS_PROVIDER), manager.getProviders(true))
    assertTrue(manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
    assertFalse(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))

    //  پروندهٔ `tohid-location` با همان کلیدها و بیت‌های اندروید (java.lang.Double ِ shim) ⇐ `remembered` ِ خودِ فایلِ اندروید
    val prefs = IosContext.getSharedPreferences("tohid-location", Context.MODE_PRIVATE)
    prefs.edit()
      .putLong("lat", java.lang.Double.doubleToRawLongBits(34.5281))
      .putLong("lng", java.lang.Double.doubleToRawLongBits(69.1723))
      .putFloat("acc", 12f)
      .putString("src", "gps")
      .putLong("at", 1_700_000_000_000L)
      .commit()
    val back = DeviceLocation.remembered(IosContext)!!
    assertEquals(34.5281, back.lat)
    assertEquals(69.1723, back.lng)
    assertEquals(12f, back.accuracy)
    assertEquals("gps", back.source)
    assertEquals(1_700_000_000_000L, back.at)
    IosContext.deleteSharedPreferences("tohid-location")
    assertNull(DeviceLocation.remembered(IosContext))
  }

  @Test
  fun sharedShellOpensTheLinkLikeMainActivity() {
    val before = ModeState.pendingLink.value
    AppStart.openLink(IosContext, "https://example.com/not-ours")
    assertEquals(before, ModeState.pendingLink.value)
    AppStart.openLink(IosContext, "vill3n://app/settings/security")
    assertEquals(DeepLink(AppMode.SHOP, "settings/security"), ModeState.pendingLink.value)
    ModeState.pendingLink.value = before
  }

  @Test
  fun scanGateAndReleaseRulesAreTheSharedOnes() {
    val gate = ScanGate()
    assertTrue(gate.accept("6260000000017", now = 1_000))
    assertFalse(gate.accept("6260000000017", now = 2_199))
    assertTrue(gate.accept("6260000000017", now = 2_200))
    assertEquals("3.2.45", ReleaseVersion.of("Tohid-Native-3.2.45.apk", "v1"))
    assertTrue(Updater.isNewer("1.3.0", "1.2.9"))
    assertFalse(Updater.isNewer("1.3.0", "1.3.0-preview"))
    assertEquals("2.0", Updater.versionOf("app.ipa", "v2.0"))
  }

  @Test
  fun batch14HesabPayPayeeAndDariWorkOnIos() {
    //  حالتِ سادهٔ حساب‌پی: اثرِ انگشتِ حسابِ گیرندهٔ سرور روی ردیفِ حساب‌پی می‌نشیند، نه روی نقد
    val ok = HesabPaySettle.settleSimple(1450.0, PayFlow.Draft(total = 1450.0, cash = "450"), "HP-1", " hp1_abc ")
    val payments = (ok as PayFlow.Outcome.Ok).value.payments
    assertEquals(listOf(null, "hp1_abc"), payments.map { it.payee })
    assertEquals("REF_REQUIRED", (HesabPaySettle.settleSimple(1450.0, null, "  ") as PayFlow.Outcome.Bad).problem.code)

    //  دری: همان متنِ فارسی از لایهٔ واژه؛ فارسی دست نمی‌خورد
    assertEquals("گدام خالی است", I18n.pick(Lang.PRS, "انبار خالی است", null, null))
    assertEquals("انبار خالی است", I18n.pick(Lang.FA, "انبار خالی است", null, null))
  }

  @Test
  fun batch15PhoneTillNoteIsRecordedOnTheSale() {
    val data = ShopData(
      products = listOf(Product(id = "p1", name = "روغن", salePrice = 1000.0, purchasePrice = 700.0, unit = "عدد")),
      warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 5.0)),
    )
    val cart = listOf(SalesEngine.CartLine("p1", 1.0))
    var seq = 0
    val r = TillSale.record(
      data, cart, SalesEngine.Checkout(seller = "رضا"), "کادو بپیچید", null, null,
      "2026-11-03", 1_800_000_000_000L, { "id${++seq}" }, saleId = "s1",
    ) as SalesEngine.Result.Ok
    assertEquals("کادو بپیچید", r.data.sales.single { it.id == r.saleId }.note)
    //  بی امتیاز ⇒ همان سبد
    assertEquals(cart, TillSale.cart(cart, null))
  }
}
