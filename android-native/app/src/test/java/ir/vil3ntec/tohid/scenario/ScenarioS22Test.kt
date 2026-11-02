package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.core.stock.ExpiryGate
import ir.vil3ntec.tohid.data.ExpiryEngine
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S22 — انقضا (PRD-16، INV-18، PRM-10): «شیر» با سه محموله — منقضی، ۵ روز مانده و ۴۰ روز مانده. سر تا ته روی
 *  `ExpiryEngine` (FEFO و هشدار)، `PromoEngine` (قاعدهٔ `expiry`، PRM-10) و `SalesEngine.record` با سیاستِ دکان
 *  `ShopPolicy.blockExpired`. «۳۰ روز، قابلِ تنظیم»: `ExpiryEngine.nearDays` از `ExpiryPref` (همین دستگاه، پیش‌فرض ۳۰)؛
 *  «منقضی فقط با مجوز»: گزینهٔ «با مجوزِ مدیر» (`ExpiryPref.approval` ⇒ `Checkout.expiryGate`) — فروشی که از ماندهٔ
 *  منقضی برمی‌دارد بی تأییدِ مدیر/صاحبِ همان کالاها (`ExpiryGate`، همان PIN ِ سقفِ اعتبار) ثبت نمی‌شود.
 */
class ScenarioS22Test {

  private var seq = 0
  private fun newId() = "e${seq++}"

  private val today = "2026-11-02"
  private val milk = Product(id = "milk", name = "شیر", unit = "عدد", purchasePrice = 50.0, salePrice = 100.0)
  private val d0 = ShopData(
    products = listOf(milk),
    warehouseEntries = listOf(
      WarehouseEntry(id = "old", productId = "milk", units = 2.0, expiryDate = "2026-10-30"),
      WarehouseEntry(id = "near", productId = "milk", units = 4.0, expiryDate = "2026-11-07"),
      WarehouseEntry(id = "far", productId = "milk", units = 10.0, expiryDate = "2026-12-12"),
    ),
  )
  private fun sell(d: ShopData, q: Double, block: Boolean) =
    SalesEngine.record(d, listOf(SalesEngine.CartLine("milk", q)), SalesEngine.Checkout(blockExpired = block), today, 1_000, ::newId)

  @Test
  fun `S22 — فروش از محمولهٔ نزدیک‌تر به انقضا (FEFO)`() {
    //  فقط هشدار (پیش‌فرض) ⇒ ۵ فروخته می‌شود: ۲ ِ منقضی تمام و از «نزدیک» ۳
    val sold = (sell(d0, 5.0, block = false) as SalesEngine.Result.Ok).data
    val left = ExpiryEngine.lots(sold, today).associate { it.entry.id to it.remaining }
    assertEquals(mapOf("near" to 1.0, "far" to 10.0), left)
  }

  @Test
  fun `S22 — هشدارِ ۳۰ روز پیش از انقضا`() {
    assertEquals(30, ExpiryEngine.NEAR_DAYS)
    assertEquals(listOf("old", "near"), ExpiryEngine.alerts(d0, today).map { it.entry.id })
    //  محمولهٔ ۴۰روزه وقتی ۳۰ روز مانده هشدار می‌گیرد، نه زودتر
    assertTrue(ExpiryEngine.alerts(d0, "2026-11-12").any { it.entry.id == "far" })
    assertTrue(ExpiryEngine.alerts(d0, "2026-11-11").none { it.entry.id == "far" })
  }

  @Test
  fun `S22 — روزِ هشدار قابلِ تنظیم است و پیش‌فرض همان ۳۰`() {
    assertEquals(ExpiryEngine.NEAR_DAYS, ExpiryEngine.nearDays)
    try {
      //  ۴۵ روز ⇒ محمولهٔ ۴۰روزه از همین امروز هشدار می‌گیرد؛ ۳ روز ⇒ فقط منقضی
      ExpiryEngine.nearDays = 45
      assertEquals(listOf("old", "near", "far"), ExpiryEngine.alerts(d0, today).map { it.entry.id })
      ExpiryEngine.nearDays = 3
      assertEquals(listOf("old"), ExpiryEngine.alerts(d0, today).map { it.entry.id })
      //  بیرون از ۱ تا ۳۶۵ ⇒ همان پیش‌فرض
      ExpiryEngine.nearDays = 0
      assertEquals(ExpiryEngine.NEAR_DAYS, ExpiryEngine.nearDays)
      ExpiryEngine.nearDays = 400
      assertEquals(ExpiryEngine.NEAR_DAYS, ExpiryEngine.nearDays)
    } finally {
      ExpiryEngine.nearDays = ExpiryEngine.NEAR_DAYS
    }
  }

  @Test
  fun `S22 — منقضی فقط با مجوزِ مدیر، برای همان کالا و تا همان مقدار`() {
    val gate = SalesEngine.Checkout(expiryGate = true)
    val cart = listOf(SalesEngine.CartLine("milk", 15.0))
    //  ۱۴ سالم است؛ پانزدهمی از ماندهٔ منقضی برمی‌دارد ⇒ تأیید
    assertEquals(mapOf("milk" to 1_000L), SalesEngine.expiredNeed(d0, cart, today))
    assertTrue(SalesEngine.expiredNeed(d0, listOf(SalesEngine.CartLine("milk", 14.0)), today).isEmpty())
    val no = SalesEngine.record(d0, cart, gate, today, 1_000, ::newId)
    assertTrue(no is SalesEngine.Result.Failed && no.message.contains("منقضی"))
    //  تأییدِ شاگرد، کالای دیگر یا مقدارِ کمتر نمی‌پوشاند
    for (bad in listOf(
      ExpiryGate.Approval("رحیم", "staff", mapOf("milk" to 1_000L), "مشتری خواست"),
      ExpiryGate.Approval("مدیر", "manager", mapOf("bread" to 1_000L), "مشتری خواست"),
      ExpiryGate.Approval("مدیر", "manager", mapOf("milk" to 500L), "مشتری خواست"),
      ExpiryGate.Approval("مدیر", "manager", mapOf("milk" to 1_000L), " "),
    )) assertTrue(SalesEngine.record(d0, cart, gate.copy(expiryApproval = bad), today, 1_000, ::newId) is SalesEngine.Result.Failed)
    //  تأییدِ مدیر ⇒ فروش، با نام و دلیل در سابقه
    val ok = ExpiryGate.Approval("مدیر", "manager", mapOf("milk" to 1_000L), "مشتری برای خوراکِ دام خواست")
    val sold = (SalesEngine.record(d0, cart, gate.copy(expiryApproval = ok), today, 1_000, ::newId) as SalesEngine.Result.Ok).data
    val log = sold.auditLog.single { it.type == "expired_sale" }
    assertEquals("مدیر", log.approvedBy)
    assertEquals("مشتری برای خوراکِ دام خواست", log.reason)
    //  «فروخته نشود» همیشه می‌بندد، حتی با تأیید
    assertTrue(SalesEngine.record(d0, cart, gate.copy(blockExpired = true, expiryApproval = ok), today, 1_000, ::newId) is SalesEngine.Result.Failed)
    //  خاموش ⇒ همان «فقط هشدار» ِ امروز، بی سابقهٔ تازه
    val warn = (SalesEngine.record(d0, cart, SalesEngine.Checkout(), today, 1_000, ::newId) as SalesEngine.Result.Ok).data
    assertTrue(warn.auditLog.none { it.type == "expired_sale" })
  }

  @Test
  fun `S22 — تخفیفِ خودکار N روز پیش از انقضا، اگر تعریف شده باشد`() {
    val cart = listOf(SalesEngine.CartLine("milk", 8.0))
    val rule = PromoCore.Rule(id = "ex", name = "نزدیکِ انقضا", kind = "expiry", bp = 3000, nearDays = 7, channels = listOf("pos"))
    //  تعریف نشده ⇒ هیچ تخفیف
    assertEquals(null, PromoEngine.evaluate(d0, cart, PromoEngine.Snapshot(enabled = true, rules = emptyList()), emptyList(), today, 600))
    //  ۳۰٪ فقط روی ۴ واحدِ محمولهٔ ۵روزه: منقضی و دیرتر نه ⇒ ۰٫۳ × ۴ × ۱۰۰ = ۱۲۰
    val o = PromoEngine.evaluate(d0, cart, PromoEngine.Snapshot(enabled = true, rules = listOf(rule)), emptyList(), today, 600)!!
    assertEquals(120.0, o.discount, 0.0)
  }

  @Test
  fun `S22 — با سیاستِ بستن، منقضی فروخته نمی‌شود و فقط مقدارِ سالم`() {
    val r = sell(d0, 15.0, block = true)
    assertTrue(r is SalesEngine.Result.Failed)
    assertTrue((r as SalesEngine.Result.Failed).message.contains("منقضی"))
    assertTrue(sell(d0, 14.0, block = true) is SalesEngine.Result.Ok)
  }
}
