package ir.vil3ntec.tohid.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 *  PAY-24 گام ۲ — تنظیمِ خاموش (پیش‌فرض) ⇒ صندوق، فاکتور، برگشت، لغو، دخل و همگام‌سازی بایت‌به‌بایت همان پیش از گام ۲.
 *  [scenario] فقط راه‌های امروز را می‌رود (بی هیچ نامِ تازه)؛ اثرِ انگشتِ SHA-256 ِ خروجی‌اش روی همان main ِ پیش از
 *  این گام گرفته شد. دفتر به JSON ِ مدل (همان که دیسک و Sync می‌نویسند) — فیلدِ تازه یا ردیفِ تازه اثر را عوض می‌کرد.
 */
class GiftCardOffTest {

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

  private fun <T> ok(o: PayFlow.Outcome<T>): T = (o as PayFlow.Outcome.Ok).value
  private fun sale(r: SalesEngine.Result): ShopData = (r as SalesEngine.Result.Ok).data
  private fun ret(r: ReturnFlow.Outcome<ReturnFlow.Posted>): ShopData = (r as ReturnFlow.Outcome.Ok).value.data

  /** همهٔ راه‌های امروزِ صندوق؛ خروجی = متنی که اثرش سنجیده می‌شود */
  private fun scenario(): String {
    val out = StringBuilder()
    out.append(PayFlow.panelMethods().joinToString(",") { it.id }).append('\n')
    for (m in listOf("gift_card", "store_credit")) {
      val p = PayFlow.preview(PayFlow.Draft(2450.0, "", listOf(PayFlow.DraftRow(m, "ABCD", "100")), "d1"))
      out.append(m).append('=').append((p as PayFlow.Outcome.Bad).problem.code).append('\n')
    }
    var d = base
    //  فاکتورِ ترکیبی (نقد + HesabPay)، نسیهٔ جزئی با مشتری، و فاکتورِ امروز بی تسویه
    val mixed = ok(PayFlow.checkout(PayFlow.Draft(3250.0, "2000", listOf(PayFlow.DraftRow("hesabpay", "HP-77", "1250")))))
    d = sale(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 1.0), SalesEngine.CartLine("p2", 1.0)), SalesEngine.Checkout(seller = "رضا", settlement = mixed), today, 100, newId, saleId = "sA"))
    val part = ok(PayFlow.checkout(PayFlow.Draft(1600.0, "600", customerId = "d1")))
    d = sale(SalesEngine.record(d, listOf(SalesEngine.CartLine("p2", 2.0)), SalesEngine.Checkout(settlement = part, debtorId = "d1"), today, 200, newId, saleId = "sB"))
    d = sale(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 1.0)), SalesEngine.Checkout(), today, 300, newId, saleId = "sC"))
    //  برگشت به روشِ اصلی، به اعتبارِ فروشگاه، و لغو
    val itemA = d.saleItems.first { it.saleId == "sA" && it.productId == "p1" }.id
    d = ret(ReturnFlow.post(d, ReturnFlow.Request(docId = "rA", saleId = "sA", picks = mapOf(itemA to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, 400, newId))
    val itemB = d.saleItems.first { it.saleId == "sB" }.id
    d = ret(ReturnFlow.post(d, ReturnFlow.Request(docId = "rB", saleId = "sB", picks = mapOf(itemB to ReturnFlow.Pick(1.0)), reason = "دیگر", mode = ReturnFlow.STORE_CREDIT), today, 500, newId))
    d = sale(SalesEngine.cancel(d, "sC", today, 600, newId, reason = "اشتباه", by = "رضا"))
    //  دخل: حسابِ ساده و رویدادهای هستهٔ صندوق
    val open = d.auditLog.first { it.id == "open1" }
    out.append(CashShift.totals(d, 50, 700, 500.0, "open1")).append('\n')
    out.append(PayFlow.ledgerEvents(d, open, 700).joinToString("\n")).append('\n')
    out.append(json.encodeToString(d))
    return out.toString()
  }

  private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

  @Test fun `تنظیمِ خاموش ⇒ صندوق و فاکتور و برگشت و دخل و همگام‌سازی همان امروز`() {
    val s = scenario()
    System.getProperty("tohid.golden")?.let { File(it).writeText(s) }
    assertEquals(GOLDEN, sha(s))
  }

  private companion object {
    const val GOLDEN = "da4d1775bf0603179c9f0f8bcdbb056f96f6936ed89f18797c341085dc62ff40"
  }
}
