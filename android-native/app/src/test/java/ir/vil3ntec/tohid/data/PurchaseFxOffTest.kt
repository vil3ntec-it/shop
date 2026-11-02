package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v3.V3Convert
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 *  PUR-11 — تنظیمِ «خرید به ارزِ دیگر» خاموش (پیش‌فرض) ⇒ فاکتورِ خرید، خریدِ تکی، بهای میانگین، خروجیِ صاحب و
 *  همگام‌سازی بایت‌به‌بایت همان پیش از PUR-11. [scenario] فقط راه‌های امروز را می‌رود (بی هیچ نامِ تازه)؛ اثرِ انگشتِ
 *  SHA-256 ِ خروجی‌اش روی همان main ِ پیش از این تغییر گرفته شد. فیلدِ تازه‌ای که `null` هم نوشته شود اثر را عوض می‌کرد.
 */
class PurchaseFxOffTest {

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val json = Json { encodeDefaults = true }
  private val today = "2026-11-02"

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "روغن", salePrice = 2450.0, purchasePrice = 2000.0, unit = "عدد"),
      Product(id = "p2", name = "برنج", salePrice = 800.0, purchasePrice = 600.0, unit = "کیلو"),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "عمده‌فروشی")),
  )

  private fun book(r: PurchaseBook.Result): ShopData = (r as PurchaseBook.Result.Ok).data

  private fun scenario(): String {
    val out = StringBuilder()
    var d = base
    //  فاکتورِ چندردیفه با پرداختِ جزئی، هزینهٔ جانبی، کاربر و سررسید؛ فاکتورِ دوم بی آن‌ها؛ خریدِ تکی
    d = book(PurchaseBook.record(
      d, "s1", listOf(PurchaseBook.Line("p1", 10.0, 2100.0), PurchaseBook.Line("p2", 2.5, 610.5, expiryDate = "2027-01-01")),
      15000.0, today, "فاکتورِ فروشنده ۷", today, 100, newId, landedTotal = 350.0, userId = "رضا", dueDate = "2026-12-01",
    ))
    d = book(PurchaseBook.record(d, "s1", listOf(PurchaseBook.Line("p1", 3.0, 1999.99)), 0.0, today, "", today, 200, newId))
    d = (LedgerEngine.addPurchase(
      d, LedgerEngine.PurchaseDraft(supplierId = "s1", productId = "p2", quantity = 4.0, purchasePrice = 620.0, paidAmount = 1000.0),
      today, 300, newId,
    ) as LedgerEngine.Result.Ok).data
    //  فهرست و برگهٔ فاکتورها و حسابِ تأمین‌کننده
    val invs = PurchaseBook.invoices(d)
    for (inv in invs) {
      out.append(inv.key).append(' ').append(inv.total).append(' ').append(inv.paid).append('\n')
      out.append(ReportCsv.text(PurchaseBook.invoiceSheet(d, inv))).append('\n')
    }
    out.append(ReportCsv.text(PurchaseBook.invoicesSheet(d, invs, today))).append('\n')
    out.append(PurchaseBook.suppliers(d, today)).append('\n')
    //  بهای تمام‌شده (میانگینِ موزونِ متحرک)
    val view = StockLedger.replay(d)
    for (p in listOf("p1", "p2")) out.append(StockLedger.productView(view, p)).append('\n')
    //  خروجیِ صاحب (JSON و برگه‌ها)
    val meta = OwnerExport.Meta(1_700_000_000_000, "desktop", "2.14.5", "دکان")
    out.append(OwnerExport.json(d, meta)).append('\n')
    for (sh in OwnerExport.sheets(OwnerExport.tree(d, meta))) out.append(sh.name).append(sh.rows).append('\n')
    //  همگام‌سازی: تفاضلِ v1 (بی opId ِ تصادفی) و همان opها در v3
    for (op in LedgerDiff.diff(LedgerDiff.toJson(base), LedgerDiff.toJson(d), now = 1)) {
      out.append(op.table).append(' ').append(op.rowId).append(' ').append(op.type).append(' ').append(op.fields).append('\n')
      out.append(V3Convert.fromLegacyOp(op.copy(opId = "op"), "hlc")).append('\n')
    }
    out.append(json.encodeToString(d))
    return out.toString()
  }

  private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

  @Test fun `تنظیمِ خاموش ⇒ فاکتورِ خرید و بها و خروجی و همگام‌سازی همان امروز`() {
    val a = scenario()
    seq = 0
    assertEquals("سناریو باید قطعی باشد", a, scenario())
    assertEquals(GOLDEN, sha(a))
  }

  private companion object {
    const val GOLDEN = "44b0c5573b5092bfa0932a006f73a8baa946a3998a4c8711b623c366bfa495f4"
  }
}
