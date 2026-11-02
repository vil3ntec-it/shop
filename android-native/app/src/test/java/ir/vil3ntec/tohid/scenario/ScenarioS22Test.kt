package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.promo.PromoCore
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
 *  `ShopPolicy.blockExpired`.
 *
 *  ماندهٔ سناریو (pending، نه سرخ):
 *   • «۳۰ روز، قابلِ تنظیم»: موتور روز را می‌گیرد (`ExpiryEngine.alerts(days)`) ولی هیچ تنظیمی نیست؛ همهٔ صفحه‌ها همان
 *     ثابتِ `NEAR_DAYS` = ۳۰ را می‌فرستند.
 *   • «منقضی فقط با مجوز»: فقط سیاستِ کلیِ دکان هست — بستن یا فقط هشدار، پیش‌فرض «فقط هشدار» (یعنی بی مجوز فروخته
 *     می‌شود). مجوزِ موردی (PIN یا نقشِ مدیر) برای فروشِ همان یک قلمِ منقضی نیست.
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
