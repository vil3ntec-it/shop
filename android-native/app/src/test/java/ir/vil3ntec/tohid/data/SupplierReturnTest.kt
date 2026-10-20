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
    assertEquals(200.0, over.single().debt, 0.0)
    assertEquals(listOf("2026-10-20"), PurchaseBook.dueSoon(soon, today).map { it.dueDate })
    val s1 = PurchaseBook.suppliers(soon, today).first { it.supplier.id == "s1" }
    assertEquals(200.0, s1.overdue, 0.0)

    //  پرداختِ کامل ⇒ دیگر سررسیدگذشته نیست
    val paid = (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 2.0, 100.0)), 200.0, "2026-09-01", "", today, now + 2, ::newId,
      dueDate = "2026-10-10") as PurchaseBook.Result.Ok).data
    assertTrue(PurchaseBook.overdue(paid, today).isEmpty())
  }
}
