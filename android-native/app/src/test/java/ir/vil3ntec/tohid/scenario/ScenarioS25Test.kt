package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StockActions
import ir.vil3ntec.tohid.data.StockLedger
import ir.vil3ntec.tohid.data.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  S25 — برگشت به تأمین‌کننده (PUR-08): خریدِ ۱۰ روغن به ۱۰۰ با پیش‌پرداختِ ۲۰۰، سپس برگشتِ ۳ تا با «سندِ کسر»
 *  (`PurchaseBook.supplierReturn`)، برگهٔ چاپیِ همان سند با عنوانِ «سندِ کسر» (`PurchaseBook.invoiceSheet`) — در کامپیوتر
 *  دکمهٔ «چاپ» ِ خرید و در گوشی «چاپِ سندِ کسر» پس از ثبتِ برگشت، هر دو همان `ReportPrint` — و بها روی پلِ موجودی
 *  (`StockLedger`).
 */
class ScenarioS25Test {

  private var n = 0
  private fun newId() = "r${++n}"
  private val today = "2026-11-02"
  private val now = 1_790_000_000_000L

  private fun bought(): ShopData {
    val d0 = ShopData(
      products = listOf(Product(id = "p1", name = "روغن", unit = "عدد", purchasePrice = 100.0)),
      suppliers = listOf(Supplier(id = "s1", name = "شرکت")),
    )
    return (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 10.0, 100.0)), 200.0, "2026-10-20", "", today, now, ::newId)
      as PurchaseBook.Result.Ok).data
  }

  @Test
  fun `S25 — موجودی و بدهی کم، سندِ کسر چاپی، بهای تمام‌شده طبقِ قاعده`() {
    val d = bought()
    assertEquals(800.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    val after = (PurchaseBook.supplierReturn(d, "s1", "p1", 3.0, 100.0, "خراب رسید", today, today, now + 1, ::newId)
      as PurchaseBook.Result.Ok).data

    //  موجودی کم می‌شود؛ بدهی کم می‌شود و صندوق نه
    assertEquals(7.0, ShopStore.stock(after, "p1"), 0.0)
    assertEquals(500.0, ShopStore.supplierDebt(after, "s1"), 0.0)
    assertEquals(d.supplierPayments, after.supplierPayments)

    //  سندِ کسر: برگهٔ چاپیِ خودش با عنوانِ «سندِ کسر»، ردیفِ ۳ روغن به ۱۰۰ = ۳۰۰، کسر از بدهی و دلیل
    val credit = PurchaseBook.invoices(after, "s1").single { it.isReturn }
    val sheet = PurchaseBook.invoiceSheet(after, credit)
    assertTrue(sheet.name.startsWith(PurchaseBook.CREDIT_NOTE_TITLE))
    assertEquals("سندِ کسر", PurchaseBook.CREDIT_NOTE_TITLE)
    assertTrue(sheet.rows.any { row -> row == listOf("1", "روغن", "3", "عدد", "100", "300") })
    assertTrue(sheet.rows.any { row -> row[1] == "کسر از بدهی به «شرکت»" && row.last() == "300" })
    assertTrue(sheet.rows.any { row -> row[1] == "دلیل: خراب رسید" })
    assertEquals(-300.0, credit.total, 0.0)
    //  فاکتورِ خریدِ معمولی همان برگهٔ پیشین است
    val bill = PurchaseBook.invoices(after, "s1").single { !it.isReturn }
    assertTrue(PurchaseBook.invoiceSheet(after, bill).name.startsWith("فاکتور-خرید-"))

    //  بها: فیِ خریدِ کالا و تاریخچهٔ قیمت دست نمی‌خورند؛ برگشت به بهای میانگین بیرون می‌رود و تطبیقِ بها درست است
    assertEquals(100.0, after.products.single().purchasePrice, 0.0)
    assertEquals(d.priceHistory, after.priceHistory)
    val view = StockLedger.replay(StockActions.setMode(after, true, "", today, 5, ::newId))
    val row = view.rows.single { it.type == "supplier_return" }
    assertEquals(30_000L, row.costMinor)
    assertTrue(StockLedger.reconcileAll(view))
  }

  @Test
  fun `S25 — سندِ کسر در گوشی و کامپیوتر از همان راهِ چاپ می‌رود`() {
    fun src(rel: String): String =
      listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
        .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")
    //  گوشی: برگهٔ «برگشت به تأمین‌کننده» ِ نسخهٔ ۳ (`purchasing/suppliers/{id}/return`) — صفحهٔ خریدِ رابطِ قدیم
    //  (`ui/screens/Purchasing.kt`) پس از دستهٔ ۱۳ بی‌راه بود؛ رسیدنِ این مسیر را `V3ReachabilityTest` می‌سنجد
    val phone = src("ir/vil3ntec/tohid/ui/v3/money/SupplierReturnAndroid.kt")
    assertTrue(phone.contains("PurchaseBook.invoiceSheet(r.data, credit).let { sh -> ir.vil3ntec.tohid.print.ReportPrint.print(context, sh, sh.name) }"))
    assertTrue(phone.contains("PurchaseBook.supplierReturn("))
    val desk = src("ir/vil3ntec/tohid/ui/screens/DeskPurchasing.kt")
    assertTrue(desk.contains("PurchaseBook.invoiceSheet(d, inv).let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) }"))
  }
}
