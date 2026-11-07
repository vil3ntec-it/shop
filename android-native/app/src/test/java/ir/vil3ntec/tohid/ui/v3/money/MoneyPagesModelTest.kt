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
import org.junit.Assert.assertNotNull
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

  /** PUR-05: فاکتورِ خریدِ چندردیفه در گوشی — همان `PurchaseBook.record` ِ کامپیوتر */
  @Test
  fun `فرمِ خرید — ردیف‌های افزوده جمع و خطای خودشان را دارند و با PurchaseBook یک فاکتور می‌شوند`() {
    val p = ledger.products.first()
    val one = PurchaseForm(supplierId = "s1", productId = p.id, quantity = "2", price = "95", paid = "300", date = "2026-10-20")
    //  بی ردیفِ افزوده ⇒ همان تک‌ردیفهٔ امروز (همان جمع، همان راهِ `addPurchase`)
    assertFalse(PurchaseFormModel.multi(one))
    assertEquals(190.0, PurchaseFormModel.total(one), 0.0)
    var f = PurchaseFormModel.addRow(one)
    assertTrue(PurchaseFormModel.multi(f))
    //  ردیفِ خالی خطای خودش را دارد و فرم ثبت نمی‌شود
    val bad = PurchaseFormModel.errors(f)
    assertTrue(bad.any)
    assertEquals(1, bad.rows.size)
    assertTrue(bad.rows[0].product != null && bad.rows[0].quantity != null && bad.rows[0].price != null)
    //  کالای ردیف قیمتِ خریدِ خودش را پیشنهاد می‌گیرد؛ هر ردیف جدا گرد می‌شود
    f = PurchaseFormModel.pickRowProduct(f, 0, p)
    assertEquals("95", f.more[0].price)
    f = PurchaseFormModel.setRow(f, 0, f.more[0].copy(quantity = "1.5", price = "33"))
    assertFalse(PurchaseFormModel.errors(f).any)
    assertEquals(190.0 + 50.0, PurchaseFormModel.total(f), 0.0)
    assertEquals(0.0, PurchaseFormModel.remaining(f.copy(paid = "240")), 0.0)
    //  برداشتن و نشانیِ بیرونِ ردیف‌ها
    assertEquals(one, PurchaseFormModel.removeRow(f, 0))
    assertEquals(f, PurchaseFormModel.removeRow(f, 5))
    assertEquals(f, PurchaseFormModel.setRow(f, 5, PurchaseRow()))
    //  سقفِ ردیف‌ها (ردیفِ نخست هم شمرده می‌شود)
    var big = one
    repeat(PurchaseFormModel.MAX_ROWS + 5) { big = PurchaseFormModel.addRow(big) }
    assertEquals(PurchaseFormModel.MAX_ROWS - 1, big.more.size)
    //  ثبت: همان فاکتورِ چندردیفهٔ کامپیوتر با یک شماره و پرداختِ پخش‌شده به ترتیب
    val lines = PurchaseFormModel.lines(f) { id -> if (id == p.id) "کیلو" else "" }
    assertEquals(listOf(p.id, p.id), lines.map { it.productId })
    assertEquals(listOf(2.0, 1.5), lines.map { it.quantity })
    assertEquals(listOf("کیلو", "کیلو"), lines.map { it.unit })
    val supplier = ledger.suppliers.first().id
    var n = 0
    val r = ir.vil3ntec.tohid.data.PurchaseBook.record(
      ledger, supplier, lines, PurchaseFormModel.paid(f), f.date, "", "2026-10-20", 1L, { "pur5-${n++}" },
    )
    assertTrue(r is ir.vil3ntec.tohid.data.PurchaseBook.Result.Ok)
    val ok = r as ir.vil3ntec.tohid.data.PurchaseBook.Result.Ok
    val added = ok.data.purchases.filter { it.invoiceNo == ok.invoiceNo }
    assertEquals(2, added.size)
    assertEquals(240.0, added.sumOf { it.totalAmount }, 0.0)
    assertEquals(240.0, added.sumOf { it.paidAmount }, 0.0)
  }

  @Test
  fun `فرمِ خرید — ردیف‌های افزوده با چرخشِ صفحه نمی‌روند`() {
    //  همان قالبِ `PurchaseFormSaver`: هشت کادر (با انقضای ردیفِ نخست، PUR-05)، سپس چهارتایی‌های ردیف (با انقضای هر ردیف)
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/ui/v3/money/PurchaseFormScreen.kt") }.first { it.isFile }.readText()
    assertTrue(src.contains("it.more.flatMap { r -> listOf(r.productId.orEmpty(), r.quantity, r.price, r.expiry) }"))
    assertTrue(src.contains("more = v.drop(8).chunked(4).filter { it.size == 4 }.map { r -> PurchaseRow(r[0].ifEmpty { null }, r[1], r[2], r[3]) })"))
    assertTrue(src.contains("it.paid, it.date, it.notes, it.expiry) +"))
    assertTrue(src.contains("PurchaseForm(v[0].ifEmpty { null }, v[1].ifEmpty { null }, v[2], v[3], v[4], v[5], v[6], expiry = v[7],"))
    //  تک‌ردیفه همان `onSave` (موتورِ امروز)، چندردیفه `onSaveMany` ⇒ `PurchaseBook.record`
    assertTrue(src.contains("else if (!PurchaseFormModel.multi(form)) { if (onSave(PurchaseFormModel.draft(form, product?.unit.orEmpty()))) onBack() }"))
    val routes = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/ui/v3/money/MoneyRoutes.kt") }.first { it.isFile }.readText()
    //  PUR-05 (دورِ ۳۱): همان فراخوانی با کلیدِ همین فرم — دو لمسِ «ثبت» یک فاکتور است؛ تک‌ردیفه هم با شناسه‌های همان کلید
    //  (PUR-05، هزینهٔ جانبی و سررسید: همان فراخوانی، با `extras` ِ فرم پیش از کلید)
    assertTrue(routes.contains("ir.vil3ntec.tohid.data.PurchaseBook.record(data, supplierId, lines, paid, date, notes, todayIso(), System.currentTimeMillis(), ::moneyId, landedTotal = extras.landedTotal, dueDate = extras.dueDate, key = saveKey)"))
    assertTrue(routes.contains("LedgerEngine.addPurchase(data, draft, todayIso(), System.currentTimeMillis(), ir.vil3ntec.tohid.data.PurchaseBook.keyedIds(saveKey))"))
    assertTrue(routes.contains("val saveKey = remember { moneyId() }"))
  }

  /** PUR-05 (مانده): «هزینهٔ جانبی» (PUR-06) و «سررسید» (PUR-07) در فرمِ «ثبت خرید» ِ گوشیِ نسخهٔ ۳ — همان `PurchaseBook.record` ِ کامپیوتر */
  @Test
  fun `فرمِ خرید — هزینهٔ جانبی روی بهای ردیف‌ها و سررسید روی فاکتور می‌نشیند، نه روی بدهیِ تأمین‌کننده`() {
    val p = ledger.products.first()
    val due = ir.vil3ntec.tohid.Jalali.parseTyped("1405-08-20")!!
    val one = PurchaseForm(supplierId = "s3", productId = p.id, quantity = "10", price = "100", paid = "", date = "2026-10-20")
    //  خالی ⇒ همان فرمِ پیشین: بی خطا، تک‌ردیفه، بی سررسید
    assertFalse(PurchaseFormModel.errors(one).any)
    assertFalse(PurchaseFormModel.multi(one))
    assertNull(PurchaseFormModel.draft(one, "کیلو").dueDate)
    assertEquals(PurchaseFormModel.Extras(), PurchaseFormModel.extras(one, on = true))
    //  نادرست ⇒ خطای درجای خودش و ثبت نمی‌شود
    assertNotNull(PurchaseFormModel.errors(one.copy(landed = "-5")).landed)
    assertNotNull(PurchaseFormModel.errors(one.copy(landed = "x")).landed)
    assertNotNull(PurchaseFormModel.errors(one.copy(due = "1405-13-40")).due)
    assertEquals(ir.vil3ntec.tohid.data.PurchaseBook.DUE_BEFORE_DATE, PurchaseFormModel.errors(one.copy(due = "1405-07-01")).due)
    assertTrue(PurchaseFormModel.errors(one.copy(due = "1405-07-01")).any)
    //  سررسیدِ درست: تک‌ردیفه همان `addPurchase` با سررسید
    val withDue = one.copy(due = "1405-08-20")
    assertFalse(PurchaseFormModel.errors(withDue).any)
    assertFalse(PurchaseFormModel.multi(withDue))
    assertEquals(due, PurchaseFormModel.draft(withDue, "کیلو").dueDate)
    //  هزینهٔ جانبی فقط با `PurchaseBook` روی بها پخش می‌شود ⇒ تک‌ردیفه هم فاکتورِ دفترِ خرید
    val f = PurchaseFormModel.setRow(PurchaseFormModel.addRow(withDue.copy(landed = "300")), 0, PurchaseRow(p.id, "5", "40"))
    assertTrue(PurchaseFormModel.multi(withDue.copy(landed = "300")))
    assertFalse(PurchaseFormModel.errors(f).any)
    //  موتورِ تازهٔ موجودی خاموش ⇒ کادرها پنهان و هیچ اثری (همان شرطِ کامپیوتر)
    assertEquals(PurchaseFormModel.Extras(), PurchaseFormModel.extras(f, on = false))
    val ex = PurchaseFormModel.extras(f, on = true)
    assertEquals(PurchaseFormModel.Extras(300.0, due), ex)
    var n = 0
    val r = ir.vil3ntec.tohid.data.PurchaseBook.record(
      ledger, "s3", PurchaseFormModel.lines(f) { "کیلو" }, PurchaseFormModel.paid(f), f.date, "", "2026-10-20", 1L, { "pur5x-${n++}" },
      landedTotal = ex.landedTotal, dueDate = ex.dueDate,
    ) as ir.vil3ntec.tohid.data.PurchaseBook.Result.Ok
    val added = r.data.purchases.filter { it.supplierId == "s3" }
    assertEquals(2, added.size)
    //  ۳۰۰ به نسبتِ جمعِ ردیف‌ها (۱۰۰۰ و ۲۰۰) ⇒ ۲۵۰ و ۵۰؛ بدهیِ تأمین‌کننده فقط جمعِ کالا (۱۲۰۰)
    assertEquals(listOf(250.0, 50.0), added.sortedByDescending { it.totalAmount }.map { it.landedCost })
    assertEquals(1200.0, ir.vil3ntec.tohid.data.ShopStore.supplierDebt(r.data, "s3"), 0.0)
    assertEquals(listOf(due, due), added.map { it.dueDate })
    assertEquals(due, ir.vil3ntec.tohid.data.PurchaseBook.invoices(r.data, "s3").single().dueDate)
  }

  @Test
  fun `فرمِ خرید — هزینهٔ جانبی و سررسید فقط با موتورِ تازه دیده می‌شوند و با چرخشِ صفحه نمی‌روند`() {
    fun src(rel: String) = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, rel) }.first { it.isFile }.readText()
    val screen = src("ir/vil3ntec/tohid/ui/v3/money/PurchaseFormScreen.kt")
    assertTrue(screen.contains("+ listOf(it.landed, it.due) }"))
    assertTrue(screen.contains(".let { f -> if ((v.size - 8) % 4 == 2) f.copy(landed = v[v.size - 2], due = v[v.size - 1]) else f }"))
    assertTrue(screen.indexOf("if (extrasOn) {") in 0 until screen.indexOf("label = StrPurchasePages.landedAfn"))
    assertTrue(screen.contains("PurchaseFormModel.paid(form), form.date, form.notes.trim(), PurchaseFormModel.extras(form, extrasOn),"))
    val routes = src("ir/vil3ntec/tohid/ui/v3/money/MoneyRoutes.kt")
    assertTrue(routes.contains("extrasOn = remember(data.auditLog, ir.vil3ntec.tohid.data.StockLedger.serverFlag) { ir.vil3ntec.tohid.data.StockLedger.isOn(data) },"))
    //  همان شرطِ کامپیوتر
    assertTrue(src("ir/vil3ntec/tohid/ui/screens/DeskPurchasing.kt").contains("ir.vil3ntec.tohid.data.StockLedger.isOn(d)"))
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

  /**
   *  PUR-05 — انقضای هر ردیفِ خرید در گوشی: همان کادرِ «ورودِ کالا» ِ انبار (شمسی، اختیاری). تک‌ردیفه با `addPurchase`
   *  و چندردیفه با `PurchaseBook.record` انقضا را روی ورودِ انبارِ همان محموله می‌نشانند (FEFO و هشدارِ انقضا)؛
   *  تایپِ نادرست ثبت نمی‌شود؛ خالی همان خریدِ امروز.
   */
  @Test
  fun `فرمِ خرید — انقضای هر ردیف روی ورودِ انبارِ همان محموله می‌نشیند و تایپِ نادرست ثبت نمی‌شود`() {
    val p = ledger.products.first()
    val supplier = ledger.suppliers.first().id
    val one = PurchaseForm(supplierId = supplier, productId = p.id, quantity = "2", price = "95", date = "2026-10-20")
    //  خالی ⇒ بی خطا و بی انقضا (همان امروز)
    assertNull(PurchaseFormModel.errors(one).expiry)
    assertEquals("", PurchaseFormModel.draft(one, "کیلو").expiryDate)
    //  نادرست ⇒ خطای درجا و فرم ثبت نمی‌شود
    val bad = PurchaseFormModel.errors(one.copy(expiry = "۱۴۰۶/۱۳/۴۰"))
    assertTrue(bad.any)
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrStock.errExpiry, bad.expiry)
    //  تک‌ردیفه: انقضای شمسی ⇒ ISO روی ورودِ انبار
    val iso = PurchaseFormModel.expiryIso("۱۴۰۶/۰۳/۳۱")
    assertNotNull(iso)
    val single = one.copy(expiry = "۱۴۰۶/۰۳/۳۱")
    assertFalse(PurchaseFormModel.errors(single).any)
    assertEquals(iso, PurchaseFormModel.draft(single, "کیلو").expiryDate)
    var n = 0
    val r1 = ir.vil3ntec.tohid.data.LedgerEngine.addPurchase(ledger, PurchaseFormModel.draft(single, "کیلو"), "2026-10-20", 1L) { "pe1-${n++}" }
    assertTrue(r1 is ir.vil3ntec.tohid.data.LedgerEngine.Result.Ok)
    val d1 = (r1 as ir.vil3ntec.tohid.data.LedgerEngine.Result.Ok).data
    assertEquals(iso, d1.warehouseEntries.last().expiryDate)

    //  چندردیفه: هر ردیف انقضای خودش؛ ردیفِ بی انقضا بی انقضا؛ ردیفِ نادرست خطای خودش
    var f = PurchaseFormModel.pickRowProduct(PurchaseFormModel.addRow(single), 0, p)
    f = PurchaseFormModel.setRow(f, 0, f.more[0].copy(quantity = "1", expiry = "1406/1/2"))
    f = PurchaseFormModel.pickRowProduct(PurchaseFormModel.addRow(f), 1, p)
    f = PurchaseFormModel.setRow(f, 1, f.more[1].copy(quantity = "1"))
    assertFalse(PurchaseFormModel.errors(f).any)
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrStock.errExpiry, PurchaseFormModel.errors(PurchaseFormModel.setRow(f, 1, f.more[1].copy(expiry = "x"))).rows[1].expiry)
    val lines = PurchaseFormModel.lines(f) { "کیلو" }
    assertEquals(listOf(iso, PurchaseFormModel.expiryIso("1406/1/2"), ""), lines.map { it.expiryDate })
    val r = ir.vil3ntec.tohid.data.PurchaseBook.record(ledger, supplier, lines, 0.0, f.date, "", "2026-10-20", 1L, { "pe2-${n++}" })
    assertTrue(r is ir.vil3ntec.tohid.data.PurchaseBook.Result.Ok)
    val d2 = (r as ir.vil3ntec.tohid.data.PurchaseBook.Result.Ok).data
    assertEquals(listOf(iso, PurchaseFormModel.expiryIso("1406/1/2"), ""), d2.warehouseEntries.takeLast(3).map { it.expiryDate })
  }
}
