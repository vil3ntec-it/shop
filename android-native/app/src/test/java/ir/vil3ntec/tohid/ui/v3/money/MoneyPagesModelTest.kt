package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.SupplierPayment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** رسیدها، تأمین‌کننده‌ها و مصارف: عددها همان موتورند، فیلترها و ترتیب درست‌اند */
class MoneyPagesModelTest {

  private val ali = Debtor(id = "d1", name = "علی")
  private val ledger = ShopData(
    debtors = listOf(ali),
    transactions = listOf(
      DebtTransaction(id = "t1", debtorId = "d1", type = "give", amount = 500.0, date = "2026-09-10"),
      DebtTransaction(id = "t2", debtorId = "d1", type = "receive", amount = 200.0, date = "2026-10-05", notes = "نقد"),
      DebtTransaction(id = "t3", debtorId = "gone", type = "receive", amount = 50.0, date = "2025-03-01"),
    ),
    expenses = listOf(
      Expense(id = "e1", title = "کرایه", category = "کرایه", amount = 6000.0, date = "2026-10-02", createdAt = 1),
      Expense(id = "e2", title = "", category = "برق", amount = 850.0, date = "2026-10-12", createdAt = 3),
      Expense(id = "e3", title = "معاش", category = "معاش", amount = 9000.0, date = "2026-09-20", createdAt = 2),
    ),
    products = listOf(Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 95.0)),
    suppliers = listOf(Supplier(id = "s1", name = "البرز"), Supplier(id = "s2", name = "پامیر"), Supplier(id = "s3", name = "کابل")),
    purchases = listOf(
      Purchase(id = "u1", productId = "p1", supplierId = "s1", quantity = 20.0, purchasePrice = 95.0, totalAmount = 1900.0, paidAmount = 1000.0, debt = 900.0, date = "2026-10-01", createdAt = 10),
      Purchase(id = "u2", productId = "p1", supplierId = "s2", quantity = 1.0, purchasePrice = 100.0, totalAmount = 100.0, paidAmount = 0.0, debt = 100.0, date = "2026-10-02", createdAt = 11),
    ),
    supplierPayments = listOf(
      SupplierPayment(id = "y1", supplierId = "s1", amount = 400.0, date = "2026-10-03", createdAt = 12),
      SupplierPayment(id = "y2", supplierId = "s2", amount = 150.0, date = "2026-10-04", createdAt = 13),
    ),
  )

  /* ------------------------------ رسیدها ------------------------------ */

  @Test
  fun `رسیدها — فقط دریافت‌ها، تازه‌ترین اول، قرض‌دارِ حذف‌شده بی پیوند`() {
    val rows = ReceiptsModel.rows(ledger, ReceiptsModel.Tab.DEBTORS)
    assertEquals(listOf("t2", "t3"), rows.map { it.id })
    assertTrue(rows[0].linked)
    assertFalse(rows[1].linked)
    assertEquals(250.0, ReceiptsModel.total(rows), 0.0)
  }

  @Test
  fun `رسیدها — سال و ماهِ خورشیدی و فیلترِ قرض‌دار`() {
    val rows = ReceiptsModel.rows(ledger, ReceiptsModel.Tab.DEBTORS)
    assertEquals(listOf(1405, 1403), ReceiptsModel.years(rows))
    assertEquals(listOf("t2"), ReceiptsModel.filter(rows, 1405, null, null).map { it.id })
    assertEquals(listOf("t2"), ReceiptsModel.filter(rows, null, 7, null).map { it.id })
    assertEquals(listOf("t2"), ReceiptsModel.filter(rows, null, null, "d1").map { it.id })
  }

  @Test
  fun `رسیدها — تبِ مصارف و پیوندِ قدیم`() {
    val rows = ReceiptsModel.rows(ledger, ReceiptsModel.Tab.EXPENSES)
    assertEquals(listOf("e2", "e1", "e3"), rows.map { it.id })
    assertEquals("مصرف", rows[0].title)
    assertNull(rows[0].debtorId)
    assertEquals(ReceiptsModel.Tab.DEBTORS, ReceiptsModel.Tab.parse("receipts"))
    assertEquals(ReceiptsModel.Tab.EXPENSES, ReceiptsModel.Tab.parse("expenses"))
  }

  /* ------------------------------ تأمین‌کننده‌ها ------------------------------ */

  @Test
  fun `تأمین‌کننده — بدهی، پیش‌پرداخت و صاف`() {
    val rows = SuppliersModel.rows(ledger).associateBy { it.supplier.id }
    assertEquals(500.0, rows.getValue("s1").debt, 0.0)
    assertEquals(SuppliersModel.State.OWE, rows.getValue("s1").state)
    assertEquals(SuppliersModel.State.PREPAID, rows.getValue("s2").state)
    assertEquals(SuppliersModel.State.SETTLED, rows.getValue("s3").state)
    //  پیش‌پرداختِ یکی از بدهیِ دیگری کم نمی‌شود
    assertEquals(500.0, SuppliersModel.owedTotal(ledger), 0.0)
  }

  @Test
  fun `حسابِ تأمین‌کننده — سه عدد و تاریخچهٔ تازه‌ترین اول`() {
    val t = SuppliersModel.totals(ledger, "s1")
    assertEquals(1900.0, t.bought, 0.0)
    assertEquals(1400.0, t.paid, 0.0)
    assertEquals(500.0, t.debt, 0.0)
    val lines = SuppliersModel.lines(ledger, "s1")
    assertEquals(listOf("y1", "u1"), lines.map { it.id })
    assertEquals(SuppliersModel.Kind.PAYMENT, lines[0].kind)
    assertEquals(900.0, lines[1].left, 0.0)
  }

  /* ------------------------------ فرمِ خرید ------------------------------ */

  @Test
  fun `فرمِ خرید — جمع و باقیِ زنده با گِردکردنِ موتور`() {
    val f = PurchaseForm(supplierId = "s1", productId = "p1", quantity = "2.5", price = "95", paid = "100")
    assertEquals(238.0, PurchaseFormModel.total(f), 0.0)
    assertEquals(138.0, PurchaseFormModel.remaining(f), 0.0)
    assertEquals(0.0, PurchaseFormModel.remaining(f.copy(paid = "1000")), 0.0)
  }

  @Test
  fun `فرمِ خرید — قیمتِ کالا پیشنهاد می‌شود ولی نوشتهٔ کاربر می‌ماند`() {
    val p = ledger.products.first()
    assertEquals("95", PurchaseFormModel.pickProduct(PurchaseForm(), p).price)
    assertEquals("80", PurchaseFormModel.pickProduct(PurchaseForm(price = "80"), p).price)
  }

  @Test
  fun `فرمِ خرید — خطای درجا و پیش‌نویسِ موتور`() {
    val empty = PurchaseFormModel.errors(PurchaseForm())
    assertTrue(empty.any)
    assertTrue(empty.supplier != null && empty.product != null && empty.quantity != null && empty.price != null)
    assertNull(empty.paid)
    val ok = PurchaseForm(supplierId = "s1", productId = "p1", quantity = "3", price = "0", paid = "", date = "2026-10-20", notes = " بار ")
    assertFalse(PurchaseFormModel.errors(ok).any)
    val draft = PurchaseFormModel.draft(ok, "کیلو")
    assertEquals(3.0, draft.quantity, 0.0)
    assertEquals(0.0, draft.purchasePrice, 0.0)
    assertEquals(0.0, draft.paidAmount, 0.0)
    assertEquals("بار", draft.notes)
    assertEquals("کیلو", draft.unit)
    val initial = PurchaseFormModel.initial("s1", "2026-10-20")
    assertFalse(PurchaseFormModel.dirty(initial, initial))
    assertTrue(PurchaseFormModel.dirty(initial.copy(quantity = "1"), initial))
  }

  /* ------------------------------ مصارف ------------------------------ */

  @Test
  fun `مصارف — ماه‌ها، جمع و تفکیک از موتور`() {
    assertEquals(listOf("2026-11", "2026-10", "2026-09"), ExpensesModel.months(ledger, "2026-11-02"))
    assertEquals(6850.0, ExpensesModel.total(ledger, "2026-10"), 0.0)
    assertEquals(listOf("کرایه" to 6000.0, "برق" to 850.0), ExpensesModel.byCategory(ledger, "2026-10"))
    assertEquals(listOf("e2", "e1"), ExpensesModel.shown(ledger, "2026-10", null).map { it.id })
    assertEquals(listOf("e2"), ExpensesModel.shown(ledger, "2026-10", "برق").map { it.id })
  }

  @Test
  fun `مصارف — ماهِ پیش و بعد از مرزِ سال، و ماهِ نامعتبر`() {
    assertEquals("2025-12", ExpensesModel.shift("2026-01", -1))
    assertEquals("2027-01", ExpensesModel.shift("2026-12", 1))
    assertEquals("2026-10", ExpensesModel.parseMonth("2026-10"))
    assertNull(ExpensesModel.parseMonth("2026-13"))
    assertNull(ExpensesModel.parseMonth("x"))
  }

  @Test
  fun `مصارف — سهمِ دسته‌ها`() {
    val shares = ExpensesModel.shares(listOf("a" to 300.0, "b" to 100.0))
    assertEquals(0.75f, shares[0], 0.0001f)
    assertEquals(0.25f, shares[1], 0.0001f)
    assertEquals(listOf(0f), ExpensesModel.shares(listOf("a" to 0.0)))
  }
}
