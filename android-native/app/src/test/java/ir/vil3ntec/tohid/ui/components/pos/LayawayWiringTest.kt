package ir.vil3ntec.tohid.ui.components.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-27 گام ۲ — فروشِ امانی در هر سه صندوق، پروندهٔ مشتری و تنظیمات (حالتِ Compose، پس متن سنجیده می‌شود): فقط با
 *  `LayawayPref` ِ همین دستگاه، هر نوشتن فقط از `LayawayPref.record` (دفترِ همین لحظهٔ دیسک) با کلیدِ ساخته از سند، و
 *  فروشِ تحویل فقط از `Layaways.deliver`.
 */
class LayawayWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private fun count(rel: String, what: String) = Regex(Regex.escape(what)).findAll(src("ir/vil3ntec/tohid/$rel")).count()

  private val bar = "ui/components/pos/LayawayBarAndroid.kt"

  @Test fun `فقط با تنظیمِ همین دستگاه`() {
    val s = src("ir/vil3ntec/tohid/$bar")
    assertEquals(2, count(bar, "val on = remember { LayawayPref.on(context) }"))
    assertTrue(s.indexOf("if (!on) return") in 0 until s.indexOf("OutlinedButton("))
  }

  @Test fun `هر نوشتن فقط از LayawayPref record با کلیدِ ساخته از سند`() {
    val s = src("ir/vil3ntec/tohid/$bar")
    for (x in listOf("store.save", "store.update", "SalesEngine.record", "DebtorEngine.")) assertFalse(x, s.contains(x))
    assertEquals(4, count(bar, "LayawayPref.record(store, key,"))
    for (x in listOf("Layaways.create(", "Layaways.pay(", "Layaways.deliver(", "Layaways.cancel(")) assertTrue(x, s.contains(x))
    for (x in listOf("Layaways.payKey(v)", "Layaways.deliverKey(v)", "Layaways.cancelKey(v)", "Layaways.hasPayment(v.id, m)")) assertTrue(x, s.contains(x))
    //  لغو و بازپرداخت فقط با مجوزِ «لغوِ فاکتور»؛ چاپ از صفِ چاپِ همان چاپگرِ فاکتور
    assertTrue(s.contains("ShopRole.canCancelSale(context)"))
    assertTrue(s.contains("PrintSpooler.submitAndWait(context, PrintQueue.Kind.REPORT"))
  }

  @Test fun `هر سه صندوق و پروندهٔ مشتری و تنظیمات`() {
    val call = "LayawayBar(d, store, cartStore, ::toast"
    for (rel in listOf("ui/screens/Sale.kt", "ui/legacy/SaleLegacy.kt", "ui/screens/DeskSale.kt")) assertEquals(rel, 1, count(rel, call))
    assertEquals(1, count("ui/legacy/DebtorsLegacy.kt", "LayawayCustomerPanel(d, store, account.debtor.id"))
    assertEquals(1, count("ui/legacy/SettingsLegacy.kt", "LayawaySettingsWired(context)"))
    assertEquals(1, count("ui/screens/Settings.kt", "LayawaySettingsWired(context)"))
    assertEquals(1, count("ui/legacy/AppRootLegacy.kt", "StrLayaway.settingsTitle to"))
  }

  @Test fun `بازپرداختِ امانی در دخل و رویدادهای صندوق`() {
    assertEquals(1, count("data/CashShift.kt", "Layaways.isRefund(it)"))
    assertEquals(2, count("data/PayFlow.kt", "Layaways.isRefund(t)"))
  }

  @Test fun `کنار گذاشتنِ کالا فقط از یک گلوگاه و با تنظیمِ همین دستگاه`() {
    //  سقفِ سبد و ثبتِ فروش هر دو از `ProductInfoMap.sellable`؛ موجودیِ انبار (`ShopStore.stock`) دست نمی‌خورد
    assertEquals(1, count("data/ProductInfoMap.kt", "Layaways.reserved(d, productId, except)"))
    assertEquals(1, count("data/SalesEngine.kt", "ProductInfoMap.sellable(d, p.id, ownReservation)"))
    assertEquals(0, count("data/ShopStore.kt", "Layaways."))
    assertEquals(1, count("data/Layaways.kt", "ownReservation = v.id"))
    for (rel in listOf("ui/AppRoot.kt", "ui/legacy/AppRootLegacy.kt")) assertEquals(rel, 1, count(rel, "LayawayPref.load(context)"))
  }
}
