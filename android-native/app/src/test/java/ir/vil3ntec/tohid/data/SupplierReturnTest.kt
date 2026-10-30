package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  برگشت به تأمین‌کننده با سندِ کسر (PUR-08) و سررسید/یادآورِ پرداخت (PUR-07).
 *
 *  سندِ کسر یک ردیفِ خریدِ منفی است: `ShopStore.supplierDebt` (و برنامهٔ قدیمی) بی هیچ
 *  تغییری ماندهٔ درست را می‌دهد، صندوق (`supplierPayments`) دست نمی‌خورد، موجودی کم
 *  می‌شود و پلِ موجودی آن را «برگشت به تأمین‌کننده» به بهای میانگین می‌خواند.
 */
class SupplierReturnTest {
  private var n = 0
  private fun newId() = "r${++n}"
  private val today = "2026-10-15"
  private val now = 1_790_000_000_000L
  private val wire = Json { encodeDefaults = true }

  private fun shop(): ShopData {
    val d0 = ShopData(
      products = listOf(Product(id = "p1", name = "روغن", unit = "عدد", purchasePrice = 100.0)),
      suppliers = listOf(Supplier(id = "s1", name = "شرکت"), Supplier(id = "s2", name = "دیگر")),
    )
    return (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 10.0, 100.0)), 200.0, "2026-10-01", "", today, now, ::newId)
      as PurchaseBook.Result.Ok).data
  }

  @Test
  fun returnReducesStockAndDebtButNotCash() {
    val d = shop()
    assertEquals(800.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    val r = PurchaseBook.supplierReturn(d, "s1", "p1", 3.0, 100.0, "خراب رسید", "", today, now + 1, ::newId, userId = "کریم")
      as PurchaseBook.Result.Ok
    val after = r.data
    assertEquals(7.0, ShopStore.stock(after, "p1"), 0.0)
    assertEquals(500.0, ShopStore.supplierDebt(after, "s1"), 0.0)
    assertEquals(d.supplierPayments, after.supplierPayments)
    val credit = after.purchases.last()
    assertEquals(-300.0, credit.totalAmount, 0.0)
    assertEquals(-3.0, credit.quantity, 0.0)
    val entry = after.warehouseEntries.last()
    assertTrue(entry.isAdjustment)
    assertEquals("supplier_return", entry.adjKind)
    assertEquals(credit.id, entry.purchaseId)
    assertEquals("supplier_return", after.stockMovements.last().type)

    //  صورت‌حساب: سندِ کسر در ستونِ «پرداخت»، ماندهٔ آخر = بدهی
    val ledger = PurchaseBook.ledger(after, "s1")
    val last = ledger.last()
    assertEquals(0.0, last.bill, 0.0)
    assertEquals(300.0, last.pay, 0.0)
    assertEquals(500.0, last.balance, 0.0)
    val summary = PurchaseBook.suppliers(after, today).first { it.supplier.id == "s1" }
    assertEquals(300.0, summary.returned, 0.0)
    assertEquals(1000.0, summary.bought, 0.0)
    assertEquals(1, summary.invoices)
    assertTrue(PurchaseBook.invoices(after, "s1").first { it.lines.single().id == credit.id }.isReturn)

    //  پلِ موجودی: برگشت به بهای میانگین، تطبیقِ بها درست
    val on = StockActions.setMode(after, true, "", today, 5, ::newId)
    val view = StockLedger.replay(on)
    val row = view.rows.first { it.id == entry.id }
    assertEquals("supplier_return", row.type)
    assertEquals(30000L, row.costMinor)
    assertTrue(StockLedger.reconcileAll(view))
  }

  @Test
  fun returnIsValidated() {
    val d = shop()
    fun fail(r: PurchaseBook.Result) = assertTrue(r.toString(), r is PurchaseBook.Result.Failed)
    fail(PurchaseBook.supplierReturn(d, "s1", "p1", 11.0, 100.0, "زیاد", "", today, now, ::newId))
    fail(PurchaseBook.supplierReturn(d, "s1", "p1", 1.0, 100.0, "  ", "", today, now, ::newId))
    fail(PurchaseBook.supplierReturn(d, "s1", "p1", 0.0, 100.0, "صفر", "", today, now, ::newId))
    fail(PurchaseBook.supplierReturn(d, "s1", "p1", 1.0, -1.0, "منفی", "", today, now, ::newId))
    fail(PurchaseBook.supplierReturn(d, "sX", "p1", 1.0, 1.0, "؟", "", today, now, ::newId))
    fail(PurchaseBook.supplierReturn(d, "s1", "p1", 1.0, 1.0, "تاریخ", "?", today, now, ::newId))
    assertEquals(100.0, PurchaseBook.lastPrice(d, "s1", "p1")!!, 0.0)
    assertNull(PurchaseBook.lastPrice(d, "s2", "p1"))
    assertEquals(listOf("p1"), PurchaseBook.boughtFrom(d, "s1").map { it.id })
  }

  /** سقفِ برگشت = خریدِ همان (تأمین‌کننده، کالا) منهای برگشت‌های پیشین — نه کلِ موجودی */
  @Test
  fun returnIsCappedByWhatThisSupplierSold() {
    val d0 = ShopData(
      products = listOf(Product(id = "p1", name = "روغن", unit = "عدد", purchasePrice = 100.0)),
      suppliers = listOf(Supplier(id = "A", name = "الف"), Supplier(id = "B", name = "ب")),
    )
    val a = (PurchaseBook.record(d0, "A", listOf(PurchaseBook.Line("p1", 1.0, 100.0)), 0.0, "2026-10-01", "", today, now, ::newId)
      as PurchaseBook.Result.Ok).data
    val d = (PurchaseBook.record(a, "B", listOf(PurchaseBook.Line("p1", 100.0, 100.0)), 0.0, "2026-10-02", "", today, now + 1, ::newId)
      as PurchaseBook.Result.Ok).data
    assertEquals(101.0, ShopStore.stock(d, "p1"), 0.0)
    assertEquals(1.0, PurchaseBook.returnable(d, "A", "p1"), 0.0)
    assertTrue(PurchaseBook.supplierReturn(d, "A", "p1", 2.0, 100.0, "بیش از خرید", "", today, now + 2, ::newId)
      is PurchaseBook.Result.Failed)

    //  دو برگشتِ پیاپی که روی‌هم از خریدِ B بیشتر شوند
    val once = (PurchaseBook.supplierReturn(d, "B", "p1", 60.0, 100.0, "اول", "", today, now + 3, ::newId)
      as PurchaseBook.Result.Ok).data
    assertEquals(40.0, PurchaseBook.returnable(once, "B", "p1"), 0.0)
    assertTrue(PurchaseBook.supplierReturn(once, "B", "p1", 41.0, 100.0, "دوم", "", today, now + 4, ::newId)
      is PurchaseBook.Result.Failed)
    assertTrue(PurchaseBook.supplierReturn(once, "B", "p1", 40.0, 100.0, "دوم", "", today, now + 4, ::newId)
      is PurchaseBook.Result.Ok)
    //  برگشت به A دست‌نخورده می‌ماند
    assertEquals(1.0, PurchaseBook.returnable(once, "A", "p1"), 0.0)
  }

  @Test
  fun dueDatesAndReminders() {
    val d0 = shop()
    //  بی سررسید ⇒ فیلد اصلاً نوشته نمی‌شود (برنامهٔ قدیمی و Sync v1 همان ردیف را می‌بینند)
    assertFalse("dueDate" in wire.encodeToJsonElement(Purchase.serializer(), d0.purchases.last()).jsonObject)
    assertTrue(PurchaseBook.overdue(d0, today).isEmpty())

    val late = (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 2.0, 100.0)), 0.0, "2026-09-01", "", today, now + 2, ::newId,
      dueDate = "2026-10-10") as PurchaseBook.Result.Ok).data
    val soon = (LedgerEngine.addPurchase(late, LedgerEngine.PurchaseDraft(supplierId = "s2", productId = "p1", quantity = 1.0, purchasePrice = 50.0,
      date = today, dueDate = "2026-10-20"), today, now + 3, ::newId) as LedgerEngine.Result.Ok).data
    assertTrue(LedgerEngine.addPurchase(soon, LedgerEngine.PurchaseDraft(supplierId = "s2", productId = "p1", quantity = 1.0, purchasePrice = 50.0,
      dueDate = "1405/08/01"), today, now, ::newId) is LedgerEngine.Result.Failed)

    val over = PurchaseBook.overdue(soon, today)
    assertEquals(1, over.size)
    assertEquals("2026-10-10", over.single().dueDate)
    assertEquals(200.0, over.single().remaining, 0.0)
    assertEquals(listOf("2026-10-20"), PurchaseBook.dueSoon(soon, today).map { it.dueDate })
    val s1 = PurchaseBook.suppliers(soon, today).first { it.supplier.id == "s1" }
    assertEquals(200.0, s1.overdue, 0.0)

    //  پرداختِ کامل ⇒ دیگر سررسیدگذشته نیست
    val paid = (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 2.0, 100.0)), 200.0, "2026-09-01", "", today, now + 2, ::newId,
      dueDate = "2026-10-10") as PurchaseBook.Result.Ok).data
    assertTrue(PurchaseBook.overdue(paid, today).isEmpty())

    //  سررسیدِ پیش از تاریخِ فاکتور رد می‌شود — فاکتورِ چندردیفه و خریدِ تکی
    assertEquals(
      PurchaseBook.Result.Failed(PurchaseBook.DUE_BEFORE_DATE),
      PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 1.0, 100.0)), 0.0, "2026-10-12", "", today, now, ::newId, dueDate = "2026-10-11"),
    )
    assertEquals(
      LedgerEngine.Result.Failed(PurchaseBook.DUE_BEFORE_DATE),
      LedgerEngine.addPurchase(d0, LedgerEngine.PurchaseDraft(supplierId = "s1", productId = "p1", quantity = 1.0, purchasePrice = 1.0,
        date = "2026-10-12", dueDate = "2026-10-01"), today, now, ::newId),
    )
    //  همان روز درست است
    assertTrue(PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 1.0, 100.0)), 0.0, "2026-10-12", "", today, now, ::newId,
      dueDate = "2026-10-12") is PurchaseBook.Result.Ok)
  }

  @Test
  fun laterPaymentsAndCreditNotesClearRemindersOldestFirst() {
    //  فاکتورِ الف (۲۰۰، سررسید ۱۰/۰۵) قدیمی‌تر از فاکتورِ ب (۸۰۰، سررسید ۱۰/۱۰)؛ هر دو گذشته
    var d = shop().let { s -> s.copy(purchases = s.purchases.map { it.copy(dueDate = "2026-10-10") }) }
    d = (PurchaseBook.record(d, "s1", listOf(PurchaseBook.Line("p1", 2.0, 100.0)), 0.0, "2026-09-01", "", today, now + 2, ::newId,
      dueDate = "2026-10-05") as PurchaseBook.Result.Ok).data
    assertEquals(listOf(200.0, 800.0), PurchaseBook.overdue(d, today).map { it.remaining })
    assertEquals(1000.0, PurchaseBook.suppliers(d, today).first { it.supplier.id == "s1" }.overdue, 0.0)

    //  پرداختِ بعدیِ ۳۰۰ ⇒ اول الف (۲۰۰) کامل، ۱۰۰ از ب
    d = (LedgerEngine.paySupplier(d, "s1", 300.0, today, "پرداخت", today, now + 3, ::newId) as LedgerEngine.Result.Ok).data
    val after = PurchaseBook.overdue(d, today)
    assertEquals(listOf("2026-10-10"), after.map { it.dueDate })
    assertEquals(700.0, after.single().remaining, 0.0)
    assertEquals(700.0, ShopStore.supplierDebt(d, "s1"), 0.0)

    //  سندِ کسرِ ۳۰۰ (برگشت) هم همان‌طور روی ب
    d = (PurchaseBook.supplierReturn(d, "s1", "p1", 3.0, 100.0, "خراب", "", today, now + 4, ::newId) as PurchaseBook.Result.Ok).data
    assertEquals(400.0, PurchaseBook.overdue(d, today).single().remaining, 0.0)
    val sum = PurchaseBook.suppliers(d, today).first { it.supplier.id == "s1" }
    assertEquals(400.0, sum.overdue, 0.0)
    assertEquals(sum.balance, PurchaseBook.open(d, "s1").sumOf { it.remaining }, 0.0)

    //  تسویه ⇒ یادآور پاک می‌شود
    d = (LedgerEngine.paySupplier(d, "s1", 400.0, today, "پرداخت", today, now + 5, ::newId) as LedgerEngine.Result.Ok).data
    assertTrue(PurchaseBook.overdue(d, today).isEmpty())
    assertTrue(PurchaseBook.dueSoon(d, "2026-10-01").isEmpty())
    //  تأمین‌کنندهٔ دیگر دست نمی‌خورد
    d = (LedgerEngine.addPurchase(d, LedgerEngine.PurchaseDraft(supplierId = "s2", productId = "p1", quantity = 1.0, purchasePrice = 50.0,
      date = "2026-09-01", dueDate = "2026-10-01"), today, now + 6, ::newId) as LedgerEngine.Result.Ok).data
    assertEquals(listOf("s2" to 50.0), PurchaseBook.overdue(d, today).map { it.supplierId to it.remaining })
  }

  @Test
  fun returnKeyMakesDoubleClickOneDocument() {
    val d = shop()
    fun ret(from: ShopData, at: Long) =
      PurchaseBook.supplierReturn(from, "s1", "p1", 1.0, 100.0, "خراب", "", today, at, ::newId, key = "K1")
    val a = ret(d, now + 1) as PurchaseBook.Result.Ok
    //  کلیکِ دوم روی همان دفترِ کهنه ⇒ همان شناسه‌ها، پس ذخیرهٔ دوم سندِ دومی نمی‌سازد
    val b = ret(d, now + 2) as PurchaseBook.Result.Ok
    fun ids(x: ShopData) = listOf(x.purchases.last().id, x.warehouseEntries.last().id, x.stockMovements.last().id, x.auditLog.last().id)
    assertEquals(ids(a.data), ids(b.data))
    assertEquals("K1", a.data.purchases.last().id)
    //  دفترِ تازه که همان سند را دارد ⇒ رد
    assertEquals(PurchaseBook.Result.Failed(PurchaseBook.ALREADY_RETURNED), ret(a.data, now + 3))
    assertEquals(9.0, ShopStore.stock(a.data, "p1"), 0.0)
  }
}
