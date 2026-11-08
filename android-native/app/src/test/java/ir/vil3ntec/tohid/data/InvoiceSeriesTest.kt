package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  F0-03 — شمارهٔ فاکتور میان گوشی و کامپیوترِ یک دکان تکراری نمی‌شود.
 *
 *  هر دستگاه «سری سند»ِ خودش را از سرور می‌گیرد (`DocSeries`) و شمارهٔ
 *  نمایشی `{سری}-{شماره}` می‌شود. شمارنده همان شمارندهٔ هر دستگاه است
 *  (`nextInvoiceNo`)؛ فاکتورِ بی سری (قدیمی، وب) همان عدد را نشان می‌دهد.
 *  قرینهٔ سرور: `server/test/doc-series.test.js`.
 */
class InvoiceSeriesTest {

  private val rice = Product(id = "p1", name = "برنج", salePrice = 300.0)
  private val ahmad = Debtor(id = "d1", name = "احمد")
  private fun ledger(next: Int = 1) = ShopData(
    products = listOf(rice),
    debtors = listOf(ahmad),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 100.0)),
    nextInvoiceNo = next,
  )

  private var seq = 0
  private fun newId(): String = "t${seq++}"
  private val cart = listOf(SalesEngine.CartLine("p1", 1.0))
  private val credit = SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, debtorId = "d1")

  private fun sell(d: ShopData, series: String, checkout: SalesEngine.Checkout = SalesEngine.Checkout()): SalesEngine.Result.Ok {
    val r = SalesEngine.record(d, cart, checkout, today = "2026-10-13", now = 1_700_000_000_000, newId = ::newId, series = series)
    assertTrue("$r", r is SalesEngine.Result.Ok)
    return r as SalesEngine.Result.Ok
  }

  @Test
  fun `شمارهٔ نمایشی سری را جلوی شماره می‌گذارد و فاکتورِ بی سری همان عدد است`() {
    assertEquals("154", Sale(id = "s", invoiceNumber = 154).invoiceLabel())
    assertEquals("B1-154", Sale(id = "s", invoiceNumber = 154, series = "B1").invoiceLabel())
    assertEquals("154", Sale(id = "s", invoiceNumber = 154, series = " ").invoiceLabel())
    assertNull(Sale(id = "s", series = "B1").invoiceLabel())
  }

  @Test
  fun `گوشی و کامپیوتر هر دو به شمارهٔ ۵ رسیده‌اند — دو فاکتورِ جدا، نه دو «#۵»`() {
    val phone = sell(ledger(next = 5), series = "A1")
    val desk = sell(ledger(next = 5), series = "B1")
    val a = phone.data.sales.single()
    val b = desk.data.sales.single()
    assertEquals(5, a.invoiceNumber)
    assertEquals(5, b.invoiceNumber)
    assertEquals("A1-5", a.invoiceLabel())
    assertEquals("B1-5", b.invoiceLabel())
    assertNotEquals(a.invoiceLabel(), b.invoiceLabel())
    //  شمارندهٔ هر دستگاه همان‌طور یکی‌یکی بالا می‌رود
    assertEquals(6, phone.data.nextInvoiceNo)
  }

  @Test
  fun `شرحِ قرض و انبار و سابقه هم شمارهٔ نمایشی را دارند`() {
    val r = sell(ledger(next = 12), series = "B1", checkout = credit)
    assertEquals("فروش نسیه — فاکتور #B1-12", r.data.transactions.single().notes)
    assertEquals("فاکتور #B1-12", r.data.stockMovements.single().notes)
    assertTrue(r.data.auditLog.single().notes.startsWith("ثبت فروش فاکتور #B1-12 "))
  }

  @Test
  fun `بی سری (هنوز نرسیده) همان رفتارِ پیشین — شماره‌های گذشته عوض نمی‌شوند`() {
    val r = sell(ledger(next = 7), series = "", checkout = credit)
    val sale = r.data.sales.single()
    assertNull(sale.series)
    assertEquals("7", sale.invoiceLabel())
    assertEquals("فروش نسیه — فاکتور #7", r.data.transactions.single().notes)
  }

  @Test
  fun `فاکتورِ بی سری در Sync v1 فیلدِ series نمی‌سازد — هیچ opِ اضافه‌ای برای فاکتورهای قدیمی`() {
    val old = ShopData(sales = listOf(Sale(id = "s-old", invoiceNumber = 3)))
    val tree = LedgerDiff.toJson(old)
    val row = (tree["sales"] as kotlinx.serialization.json.JsonArray)[0] as kotlinx.serialization.json.JsonObject
    assertTrue("series نباید نوشته شود: $row", "series" !in row)
    //  سایهٔ ساخته‌شده پیش از این نسخه (بی کلیدِ series) ⇒ تفاضل صفر
    assertEquals(0, LedgerDiff.diff(LedgerDiff.shadowOf(tree), tree).size)

    //  و فاکتورِ سری‌دار سری‌اش را می‌برد و از دستگاهِ دیگر برمی‌گردد
    val withSeries = ShopData(sales = listOf(Sale(id = "s-new", invoiceNumber = 3, series = "B1")))
    val back = LedgerDiff.fromJson(LedgerDiff.toJson(withSeries))
    assertEquals("B1", back.sales.single().series)
  }

  /** Snapshot ِ سرور از دفتر (همان شکلِ `GET /sync/v1/snapshot`) ⇒ دفترِ تازهٔ حسابِ دیگر (`LedgerDiff.fromSnapshot`) */
  private fun fromServer(d: ShopData): ShopData {
    val tree = LedgerDiff.toJson(d)
    val tables = kotlinx.serialization.json.buildJsonObject {
      for (key in LedgerDiff.COLLECTIONS) {
        val rows = tree[key] as? kotlinx.serialization.json.JsonArray ?: continue
        put(key, kotlinx.serialization.json.JsonArray(rows.map { r ->
          val o = r as kotlinx.serialization.json.JsonObject
          kotlinx.serialization.json.buildJsonObject { put("id", o["id"]!!); put("data", o) }
        }))
      }
    }
    val snap = kotlinx.serialization.json.buildJsonObject { put("tables", tables) }
    return LedgerDiff.fromJson(LedgerDiff.fromSnapshot(snap, LedgerDiff.toJson(ShopData())))
  }

  @Test
  fun `همان گوشی، حسابِ دیگر با همان سری — دفترِ تازه از Snapshot شمارهٔ فروخته‌شده را دوباره نمی‌زند`() {
    //  صاحب روی گوشی تا A1-3 فروخت؛ شاگرد با کدِ شاگرد روی همان گوشی وارد شد (سری مالِ دستگاه است)
    var owner = ledger()
    repeat(3) { owner = sell(owner, series = "A1").data }
    val staff = fromServer(owner)
    assertEquals("شمارنده همگام نمی‌شود", 1, staff.nextInvoiceNo)
    val first = sell(staff, series = "A1").data
    assertEquals("A1-4", first.sales.last().invoiceLabel())

    //  صاحب برمی‌گردد: دفترِ خودش (شمارندهٔ ۴) + فاکتورِ شاگرد از همگام‌سازی ⇒ ۵، نه ۴ ِ دوباره
    val back = owner.copy(sales = owner.sales + first.sales.last())
    assertEquals(4, back.nextInvoiceNo)
    val again = sell(back, series = "A1").data
    assertEquals("A1-5", again.sales.last().invoiceLabel())
    val labels = again.sales.map { it.invoiceLabel() }
    assertEquals(labels.toSet().size, labels.size)
  }

  @Test
  fun `فقط سریِ همین دستگاه شمرده می‌شود — فاکتورِ دستگاهِ دیگر پرشِ بیهوده نمی‌سازد`() {
    val d = ledger(next = 3).copy(sales = listOf(
      Sale(id = "x1", invoiceNumber = 40, series = "B1"),
      Sale(id = "x2", invoiceNumber = 9, series = "O"),
      Sale(id = "x3", invoiceNumber = 2, series = "A1"),
    ))
    assertEquals(3, InvoiceNumber.next(d, "A1"))
    assertEquals(41, InvoiceNumber.next(d, "B1"))
    assertEquals("بی سری فقط فاکتورهای بی سری", 3, InvoiceNumber.next(d, ""))
    assertEquals(8, InvoiceNumber.next(d.copy(sales = d.sales + Sale(id = "x4", invoiceNumber = 7)), " "))
    //  شمارنده‌ای که جلوتر است دست نمی‌خورد (فاکتورِ لغوشده هم شماره‌اش را نگه می‌دارد)
    assertEquals(12, InvoiceNumber.next(d.copy(nextInvoiceNo = 12), "A1"))
  }
}
