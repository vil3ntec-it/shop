package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrExpiryAlert
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.v3.shell.ShellTargets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  هشدارِ انقضا (۱۴۰۵/۰۸/۲۴): یکی برای هر محموله، «نزدیکِ انقضا» جدا از «منقضی‌شده»، با مرزِ قابلِ تنظیم؛ محمولهٔ فروخته‌شده
 *  هشداری ندارد؛ و یادآوریِ روزانه همان را از خلاصهٔ کوچکِ دفتر می‌گوید.
 */
class ExpiryAlertsTest {

  private var seq = 0
  private fun newId(): String = "x${seq++}"
  private val today = "2026-11-15"

  private val milk = Product(id = "milk", name = "شیر", unit = "عدد", purchasePrice = 50.0, salePrice = 70.0)
  private val yogurt = Product(id = "yog", name = "ماست", unit = "عدد", purchasePrice = 40.0, salePrice = 60.0)

  private fun lot(id: String, product: String, units: Double, expiry: String, batch: String = "") =
    WarehouseEntry(id = id, productId = product, units = units, expiryDate = expiry, batch = batch)

  private fun ledger(vararg e: WarehouseEntry) = ShopData(products = listOf(milk, yogurt), warehouseEntries = e.toList())

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() { ExpiryEngine.nearDays = ExpiryEngine.NEAR_DAYS }

  @Test
  fun `محمولهٔ نزدیکِ انقضا هشدار می‌گیرد، دورتر از مرز نه`() {
    val d = ledger(lot("near", "milk", 5.0, "2026-11-27"), lot("far", "yog", 5.0, "2027-02-01"))
    val a = ExpiryAlerts.of(d, today).single()
    assertEquals("milk", a.productId)
    assertEquals(12L, a.daysLeft)
    assertFalse(a.expired)
    assertEquals(5.0, a.remaining, 0.0)
    //  مرزِ قابلِ تنظیمِ همین دستگاه (`ExpiryPref` ⇒ `ExpiryEngine.nearDays`)
    ExpiryEngine.nearDays = 90
    assertEquals(listOf("milk", "yog"), ExpiryAlerts.of(d, today).map { it.productId })
    assertTrue(ExpiryAlerts.of(d, today, days = 7).isEmpty())
  }

  @Test
  fun `محمولهٔ منقضی هشدارِ «منقضی‌شده» می‌گیرد، جدا و فوری`() {
    val d = ledger(lot("old", "milk", 3.0, "2026-11-10"), lot("soon", "yog", 4.0, "2026-11-20"))
    val alerts = ExpiryAlerts.of(d, today)
    val gone = alerts.single { it.expired }
    assertEquals("milk", gone.productId)
    assertEquals(-5L, gone.daysLeft)
    val counts = ExpiryAlerts.counts(alerts)
    assertEquals(ExpiryAlerts.Counts(expired = 1, near = 1, expiredNames = listOf("شیر"), nearNames = listOf("ماست")), counts)
    //  «هشدارها»: منقضی سرخ و فوری ⇐ پالایهٔ «منقضی» ِ انبار؛ نزدیک زرد ⇐ «نزدیکِ انقضا»
    val home = HomeAlerts.build(HomeAlerts.Input(expiring = counts.near, expired = counts.expired, expiredNames = "شیر", expiringNames = "ماست"))
    val expired = home.single { it.route == ShellTargets.EXPIRED }
    assertEquals(StrExpiryAlert.expired(1), expired.title)
    assertEquals("شیر", expired.body)
    assertTrue(expired.urgent)
    val near = home.single { it.route == ShellTargets.EXPIRING }
    assertEquals(StrHome.alertExpiring(1), near.title)
    assertFalse(near.urgent)
    assertTrue(StrExpiryAlert.expired(1).contains("منقضی"))
    //  بی نام ⇒ همان جملهٔ همیشگی
    assertEquals(StrExpiryAlert.expiredBody, HomeAlerts.build(HomeAlerts.Input(expired = 2)).single().body)
  }

  @Test
  fun `محمولهٔ فروخته‌شده هشدارش می‌رود — بی هیچ پاک کردنِ دستی`() {
    val d = ledger(lot("a", "milk", 2.0, "2026-11-20"))
    assertEquals(1, ExpiryAlerts.of(d, today).size)
    val sold = (SalesEngine.record(d, listOf(SalesEngine.CartLine("milk", 2.0)), SalesEngine.Checkout(), today, 0, ::newId) as SalesEngine.Result.Ok).data
    assertTrue(ExpiryAlerts.of(sold, today).isEmpty())
    //  بخشی فروخته ⇒ همان یک هشدار با ماندهٔ کمتر
    val half = (SalesEngine.record(d, listOf(SalesEngine.CartLine("milk", 1.0)), SalesEngine.Checkout(), today, 0, ::newId) as SalesEngine.Result.Ok).data
    assertEquals(1.0, ExpiryAlerts.of(half, today).single().remaining, 0.0)
    //  برداشته با «اصلاحِ موجودی» هم (منفی ⇒ از محموله‌ها کم می‌شود)
    val removed = d.copy(warehouseEntries = d.warehouseEntries + WarehouseEntry(id = "adj", productId = "milk", units = -2.0, isAdjustment = true))
    assertTrue(ExpiryAlerts.of(removed, today).isEmpty())
  }

  @Test
  fun `یک هشدار برای هر محموله — دو رسیدِ یک سری یکی، سریِ دیگر جدا`() {
    val d = ledger(
      lot("r1", "milk", 2.0, "2026-11-25", batch = "B7"),
      lot("r2", "milk", 3.0, "2026-11-25", batch = " B7 "),
      lot("r3", "milk", 1.0, "2026-11-25", batch = "B8"),
    )
    val alerts = ExpiryAlerts.of(d, today)
    assertEquals(listOf("B7" to 5.0, "B8" to 1.0), alerts.map { it.batch to it.remaining })
    assertEquals(2, ExpiryAlerts.counts(alerts).near)
    assertEquals(listOf("شیر"), ExpiryAlerts.counts(alerts).nearNames)
  }

  @Test
  fun `یادآوریِ روزانه از خلاصهٔ کوچکِ دفتر — منقضی پیش از کم‌بودن، هر صبح با همان روز`() {
    val d = ledger(lot("old", "milk", 3.0, "2026-11-10"), lot("soon", "yog", 4.0, "2026-11-20"), lot("later", "yog", 1.0, "2027-06-01"))
    val summary = LedgerSummary.of(d, now = 0L, today = today)
    //  تا یک سالِ دیگر در خلاصه (مرزِ هر تنظیمی)، با تاریخِ خودِ محموله
    assertEquals(listOf("2026-11-10", "2026-11-20", "2027-06-01"), summary.expiry.map { it.expiry })
    val (title, text) = Reminders.summary(summary, today = today, nearDays = 30)!!
    assertEquals(StrExpiryAlert.expired(1), title)
    assertTrue(text, text.contains(StrExpiryAlert.itemExpired("شیر")))
    assertTrue(text, text.contains(StrExpiryAlert.itemNear("ماست", 5L)))
    //  محمولهٔ دورِ «ماست» (۲۰۲۷) هنوز نزدیک نیست ⇒ فقط یک خطِ «تا انقضا»
    assertEquals(text, 1, text.split(" • ").count { it.contains("تا انقضا") })
    //  ده روز بعد «ماست» هم گذشته است — همان خلاصه، صبحِ دیگر
    assertEquals(StrExpiryAlert.expired(2), Reminders.summary(summary, today = "2026-11-25", nearDays = 30)!!.first)
    //  بی هیچ چیز ⇒ بی اعلان؛ خلاصهٔ کهنهٔ بی خطِ انقضا هم همان رفتارِ پیشین
    assertNull(Reminders.summary(LedgerSummary(), today = today, nearDays = 30))
    assertEquals(StrHome.alertExpiring(1), Reminders.summary(LedgerSummary(expiry = summary.expiry.drop(1).take(1)), today = today, nearDays = 30)!!.first)
  }
}
