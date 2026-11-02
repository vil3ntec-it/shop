package ir.vil3ntec.tohid.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 *  POS-27 گام ۲ — تنظیمِ خاموش (پیش‌فرض) ⇒ صندوق، فاکتور، برگشت، لغو، رسیدِ قرض‌دار، سفارشِ ویژه، دخل، همگام‌سازی و
 *  خروجیِ مالک بایت‌به‌بایت همان پیش از گام ۲. [scenario] فقط راه‌های امروز را می‌رود (بی هیچ نامِ تازه)؛ اثرِ انگشتِ
 *  SHA-256 ِ خروجی‌اش روی همان شاخهٔ پیش از این گام گرفته شد. دفتر به JSON ِ مدل (همان که دیسک و Sync می‌نویسند) —
 *  فیلدِ تازه‌ای که نوشته شود، ردیفِ تازه یا عوض‌شدنِ دخل اثر را عوض می‌کرد.
 */
class LayawayOffTest {

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
    debtors = listOf(Debtor(id = "d1", name = "نوشا"), Debtor(id = "d2", name = "کریم")),
    auditLog = listOf(AuditEntry(id = "open1", type = CashShift.OPEN, date = today, notes = "باز", createdAt = 50, amount = 500.0)),
  )

  private fun <T> ok(o: PayFlow.Outcome<T>): T = (o as PayFlow.Outcome.Ok).value
  private fun sale(r: SalesEngine.Result): ShopData = (r as SalesEngine.Result.Ok).data
  private fun ret(r: ReturnFlow.Outcome<ReturnFlow.Posted>): ShopData = (r as ReturnFlow.Outcome.Ok).value.data
  private fun built(b: SpecialOrders.Built): ShopData = (b as SpecialOrders.Built.Ok).data

  /** همهٔ راه‌های امروزِ صندوق و حساب؛ خروجی = متنی که اثرش سنجیده می‌شود */
  private fun scenario(): String {
    val out = StringBuilder()
    var d = base
    //  نقد، نسیهٔ جزئی با مشتری و نسیهٔ کامل
    d = sale(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 1.0), SalesEngine.CartLine("p2", 1.0)), SalesEngine.Checkout(seller = "رضا"), today, 100, newId, saleId = "sA"))
    val part = ok(PayFlow.checkout(PayFlow.Draft(1600.0, "600", customerId = "d1")))
    d = sale(SalesEngine.record(d, listOf(SalesEngine.CartLine("p2", 2.0)), SalesEngine.Checkout(settlement = part, debtorId = "d1"), today, 200, newId, saleId = "sB"))
    d = sale(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 1.0)), SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, debtorId = "d2"), today, 250, newId, saleId = "sC"))
    //  رسیدِ دستیِ قرض‌دار
    d = d.copy(transactions = d.transactions + DebtTransaction(id = "t1", debtorId = "d2", type = "receive", amount = 450.0, date = today, createdAt = 260))
    //  برگشت و لغو
    val itemA = d.saleItems.first { it.saleId == "sA" && it.productId == "p1" }.id
    d = ret(ReturnFlow.post(d, ReturnFlow.Request(docId = "rA", saleId = "sA", picks = mapOf(itemA to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, 300, newId))
    d = sale(SalesEngine.cancel(d, "sB", today, 350, newId, reason = "اشتباه", by = "رضا"))
    //  سفارشِ ویژه با پیش‌پرداخت و لغوِ آن (بازپرداخت از دخل)
    d = built(SpecialOrders.create(d, "o1", SpecialOrders.NewOrder("d1", null, "یخچال", 1000, 2_500_000, 500_000, "2026-11-10"), today, 400, "رضا", newId))
    d = built(SpecialOrders.cancel(d, "c1", "o1", "مشتری منصرف شد", today, 450, "رضا", newId))
    //  دخل: حسابِ ساده و رویدادهای هستهٔ صندوق؛ خروجیِ مالک؛ دفتر
    val open = d.auditLog.first { it.id == "open1" }
    out.append(CashShift.totals(d, 50, 700, 500.0, "open1")).append('\n')
    out.append(PayFlow.ledgerEvents(d, open, 700).joinToString("\n")).append('\n')
    out.append(ShopStore.debt(d, "d1")).append(',').append(ShopStore.debt(d, "d2")).append('\n')
    out.append(OwnerExport.json(d, OwnerExport.Meta(1_760_000_000_000, "android", "2.14.5", "دکان آزمون"))).append('\n')
    out.append(json.encodeToString(d))
    return out.toString()
  }

  private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

  @Test fun `تنظیمِ خاموش ⇒ صندوق و حساب و دخل و همگام‌سازی و خروجی همان امروز`() {
    val s = scenario()
    System.getProperty("tohid.golden")?.let { File(it).writeText(s) }
    assertEquals(GOLDEN, sha(s))
  }

  private companion object {
    const val GOLDEN = "8525a0a9fe4cb81181549dd36c00b689b5af20f9fdb11e12f468d2a6a564060f"
  }
}
