package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.Loyalty
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 *  PRM-13 — تنظیمِ خاموش (پیش‌فرض) ⇒ صندوق، فاکتور، برگشت، لغو، دخل و همگام‌سازی بایت‌به‌بایت همان پیش از امتیاز.
 *  [scenario] فقط راه‌های امروز را می‌رود (بی هیچ نامِ تازه) — همان راه‌هایی که حالا قلابِ امتیاز دارند (`cancel`،
 *  `recordReturn`، `ReturnFlow.post`)؛ اثرِ انگشتِ SHA-256 ِ خروجی‌اش روی همان main ِ پیش از این تغییر گرفته شد. دفتر
 *  به JSON ِ مدل (همان که دیسک و Sync می‌نویسند) — فیلد یا ردیفِ تازه اثر را عوض می‌کرد.
 */
class LoyaltyOffTest {

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val json = Json { encodeDefaults = true }
  private val today = "2026-11-02"

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "روغن", salePrice = 2450.0, purchasePrice = 2000.0, unit = "عدد"),
      Product(id = "p2", name = "برنج", salePrice = 800.0, purchasePrice = 600.0, unit = "عدد"),
    ),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 20.0), WarehouseEntry(id = "w2", productId = "p2", units = 20.0)),
    debtors = listOf(Debtor(id = "d1", name = "نوشا")),
    auditLog = listOf(AuditEntry(id = "open1", type = CashShift.OPEN, date = today, notes = "باز", createdAt = 50, amount = 500.0)),
  )

  private fun sale(r: SalesEngine.Result): ShopData = (r as SalesEngine.Result.Ok).data
  private fun ret(r: ReturnFlow.Outcome<ReturnFlow.Posted>): ShopData = (r as ReturnFlow.Outcome.Ok).value.data

  /** همهٔ راه‌های امروزِ صندوق با مشتری و بی مشتری؛ خروجی = متنی که اثرش سنجیده می‌شود */
  private fun scenario(results: MutableList<SalesEngine.Result>? = null): Pair<String, ShopData> {
    val out = StringBuilder()
    var d = base
    fun rec(r: SalesEngine.Result): ShopData { results?.add(r); return sale(r) }
    d = rec(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 1.0), SalesEngine.CartLine("p2", 1.0)), SalesEngine.Checkout(seller = "رضا", debtorId = "d1"), today, 100, newId, saleId = "sA"))
    d = rec(SalesEngine.record(d, listOf(SalesEngine.CartLine("p2", 2.0)), SalesEngine.Checkout(debtorId = "d1", payment = SalesEngine.Payment.CREDIT, paidAmount = 600.0), today, 200, newId, saleId = "sB"))
    d = rec(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 1.0)), SalesEngine.Checkout(), today, 300, newId, saleId = "sC"))
    //  برگشتِ تازه، مرجوعیِ امروز، و لغو (یکی پس از برگشت)
    val itemA = d.saleItems.first { it.saleId == "sA" && it.productId == "p2" }.id
    d = ret(ReturnFlow.post(d, ReturnFlow.Request(docId = "rA", saleId = "sA", picks = mapOf(itemA to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, 400, newId))
    val itemB = d.saleItems.first { it.saleId == "sB" }.id
    d = sale(SalesEngine.recordReturn(d, "sB", mapOf(itemB to 1.0), "دیگر", today, 500, newId, by = "رضا"))
    d = sale(SalesEngine.cancel(d, "sC", today, 600, newId, reason = "اشتباه", by = "رضا"))
    d = sale(SalesEngine.cancel(d, "sA", today, 650, newId, reason = "اشتباه"))
    val open = d.auditLog.first { it.id == "open1" }
    out.append(CashShift.totals(d, 50, 700, 500.0, "open1")).append('\n')
    out.append(PayFlow.ledgerEvents(d, open, 700).joinToString("\n")).append('\n')
    out.append(json.encodeToString(d))
    return out.toString() to d
  }

  private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

  @Test fun `تنظیمِ خاموش ⇒ صندوق و فاکتور و برگشت و لغو و دخل و همگام‌سازی همان امروز`() {
    val s = scenario().first
    System.getProperty("tohid.golden")?.let { File(it).writeText(s) }
    assertEquals(GOLDEN, sha(s))
  }

  @Test fun `قلابِ ثبت با تنظیمِ خاموش یا بی امتیاز همان نتیجه را برمی‌گرداند`() {
    val results = ArrayList<SalesEngine.Result>()
    val d = scenario(results).second
    assertEquals(3, results.size)
    for (r in results) {
      assertSame(r, LoyaltyPoints.post(r, false, Loyalty.Rule(), null, "رضا"))
      assertSame(r, LoyaltyPoints.afterCancel(r, today, 1, null))
    }
    assertSame(d, LoyaltyPoints.afterReturn(base, d, "sA", "rA", today, 1, null))
    assertTrue(d.sales.all { LoyaltyPoints.receiptLines(d, it.id).isEmpty() })
    assertTrue(LoyaltyPoints.history(d, "d1").isEmpty())
    assertFalse(d.auditLog.any(LoyaltyPoints::isRow))
    //  `AuditEntry.points` ِ خالی نوشته نمی‌شود
    assertFalse(json.encodeToString(AuditEntry(id = "a")).contains("points"))
  }

  private companion object {
    const val GOLDEN = "1e08e7ceaa05f4725a29838dfed25400825420e61a83ff42adf95e3ffb5f561f"
  }
}
