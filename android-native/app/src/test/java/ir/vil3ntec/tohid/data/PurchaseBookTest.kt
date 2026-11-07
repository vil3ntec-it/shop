package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** فاکتورِ خریدِ چندردیفه — انبار، بدهیِ تأمین‌کننده و صورت‌حساب */
class PurchaseBookTest {
  private var seq = 0
  private fun newId() = "x${seq++}"
  private val d0 = ShopData(
    products = listOf(
      Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 100.0),
      Product(id = "p2", name = "روغن", unit = "عدد", purchasePrice = 500.0),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "عمده‌فروشی")),
  )

  @Test fun `چند ردیف زیرِ یک فاکتور، پرداخت پخش و انبار پر`() {
    val r = PurchaseBook.record(
      d0, "s1",
      listOf(PurchaseBook.Line("p1", 10.0, 120.0, expiryDate = "2027-01-01"), PurchaseBook.Line("p2", 2.0, 550.0)),
      paid = 1500.0, date = "2026-10-10", notes = "", today = "2026-10-10", now = 1, newId = ::newId,
    ) as PurchaseBook.Result.Ok
    val d = r.data
    assertEquals(1, r.invoiceNo)
    assertEquals(2, d.purchases.size)
    assertTrue(d.purchases.all { it.invoiceNo == 1 })
    //  جمع ۱۲۰۰ + ۱۱۰۰ = ۲۳۰۰؛ پرداخت ۱۵۰۰ ⇒ ردیفِ اول کامل، دوم ۳۰۰
    assertEquals(listOf(1200.0, 300.0), d.purchases.map { it.paidAmount })
    assertEquals(800.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    assertEquals(10.0, ShopStore.stock(d, "p1"), 0.0)
    assertEquals("2027-01-01", d.warehouseEntries.first { it.productId == "p1" }.expiryDate)
    assertEquals(120.0, d.products.first { it.id == "p1" }.purchasePrice, 0.0)

    val inv = PurchaseBook.invoices(d).single()
    assertEquals(2300.0, inv.total, 0.0)
    assertEquals(800.0, inv.debt, 0.0)

    val led = PurchaseBook.ledger(d, "s1")
    assertEquals(listOf(2300.0, 800.0), led.map { it.balance })
    assertEquals(2, PurchaseBook.nextNo(d))
  }

  @Test fun `ردیفِ خراب ⇒ هیچ چیز ثبت نمی‌شود`() {
    val r = PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 0.0, 10.0)), 0.0, "", "", "2026-10-10", 1, ::newId)
    assertTrue(r is PurchaseBook.Result.Failed)
    val r2 = PurchaseBook.record(d0, "nope", listOf(PurchaseBook.Line("p1", 1.0, 10.0)), 0.0, "", "", "2026-10-10", 1, ::newId)
    assertTrue(r2 is PurchaseBook.Result.Failed)
  }

  @Test fun `پرداختِ بعدی در صورت‌حساب`() {
    var d = (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 5.0, 100.0)), 0.0, "2026-10-01", "", "2026-10-01", 1, ::newId) as PurchaseBook.Result.Ok).data
    d = d.copy(supplierPayments = d.supplierPayments + SupplierPayment("sp", "s1", 200.0, "2026-10-05", "", 2))
    assertEquals(listOf(500.0, 300.0), PurchaseBook.ledger(d, "s1").map { it.balance })
    assertEquals(300.0, PurchaseBook.suppliers(d).single().balance, 0.0)
    assertEquals(5, PurchaseBook.invoiceSheet(d, PurchaseBook.invoices(d).single()).rows.size)
  }

  /*
   *  PUR-05: دو دستگاه بی همگام‌سازی هر کدام «فاکتورِ خرید #۱» زدند — یکی از s1، یکی از s2 (`nextNo` روی دفترِ همان
   *  دستگاه). پس از همگام‌سازی دو فاکتورِ جدا با تأمین‌کنندهٔ خودشان می‌مانند، کلیدشان یکتاست (همان کلید در فهرستِ
   *  یک تأمین‌کننده) و یادآورِ سررسید ماندهٔ هر کدام را با پرداخت‌های همان تأمین‌کننده می‌سنجد.
   */
  @Test fun `فاکتورهای هم‌شمارهٔ دو دستگاه برای دو تأمین‌کننده یکی نمی‌شوند`() {
    val base = d0.copy(suppliers = d0.suppliers + Supplier(id = "s2", name = "پخش"))
    val a = PurchaseBook.record(base, "s1", listOf(PurchaseBook.Line("p1", 10.0, 100.0)), 0.0, "2026-10-01", "", "2026-10-01", 1, ::newId,
      dueDate = "2026-10-05") as PurchaseBook.Result.Ok
    val b = PurchaseBook.record(base, "s2", listOf(PurchaseBook.Line("p2", 1.0, 500.0)), 0.0, "2026-10-02", "", "2026-10-02", 2, ::newId,
      dueDate = "2026-10-06") as PurchaseBook.Result.Ok
    assertEquals(listOf(1, 1), listOf(a.invoiceNo, b.invoiceNo))
    val d = a.data.copy(
      purchases = a.data.purchases + b.data.purchases,
      warehouseEntries = a.data.warehouseEntries + b.data.warehouseEntries,
      stockMovements = a.data.stockMovements + b.data.stockMovements,
      //  s2 بعداً همه را پرداخت
      supplierPayments = listOf(SupplierPayment("sp2", "s2", 500.0, "2026-10-03", "", 3)),
    )
    val all = PurchaseBook.invoices(d)
    assertEquals(2, all.size)
    assertEquals(mapOf("s1" to 1000.0, "s2" to 500.0), all.associate { it.supplierId to it.total })
    assertEquals(2, all.map { it.key }.toSet().size)
    assertEquals(all.single { it.supplierId == "s2" }.key, PurchaseBook.invoices(d, "s2").single().key)
    //  یادآور: فقط s1 با ۱۰۰۰ (نه ۱۵۰۰ ِ یکی‌شده)؛ s2 پرداخته
    val late = PurchaseBook.overdue(d, "2026-10-10")
    assertEquals(listOf("s1" to 1000.0), late.map { it.supplierId to it.remaining })
    assertEquals(1000.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    assertEquals(0.0, ShopStore.supplierDebt(d, "s2"), 0.0)
  }

  /*
   *  PUR-05: ورودِ انبارِ یک فاکتورِ خرید (و سندِ کسرِ برگشت) جدا حذف نمی‌شود — وگرنه موجودی می‌رفت و بدهیِ تأمین‌کننده و
   *  جمعِ فاکتور می‌ماند. اصلاح از راهِ «برگشت به تأمین‌کننده» است که هر دو را با هم کم می‌کند. ورودِ دستیِ انبار مثلِ امروز.
   */
  @Test fun `ورودِ انبارِ فاکتورِ خرید جدا حذف نمی‌شود و موجودی و بدهی با هم می‌مانند`() {
    var d = (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 10.0, 100.0)), 0.0, "2026-10-01", "", "2026-10-01", 1, ::newId)
      as PurchaseBook.Result.Ok).data
    val bought = d.warehouseEntries.single()
    val r = WarehouseEngine.deleteEntry(d, bought.id, "2026-10-02", 2, ::newId)
    assertTrue(r.toString(), r is WarehouseEngine.Result.Failed)
    //  سندِ کسر هم
    d = (PurchaseBook.supplierReturn(d, "s1", "p1", 2.0, 100.0, "خراب", "2026-10-02", "2026-10-02", 3, ::newId, key = "ret1")
      as PurchaseBook.Result.Ok).data
    assertTrue(WarehouseEngine.deleteEntry(d, "ret1-w", "2026-10-03", 4, ::newId) is WarehouseEngine.Result.Failed)
    assertEquals(8.0, ShopStore.stock(d, "p1"), 0.0)
    assertEquals(800.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    //  ورودِ دستی (بی فاکتور) مثلِ امروز حذف می‌شود
    d = d.copy(warehouseEntries = d.warehouseEntries + WarehouseEntry(id = "wm", productId = "p1", units = 5.0, price = 90.0, date = "2026-10-03"))
    val ok = WarehouseEngine.deleteEntry(d, "wm", "2026-10-03", 5, ::newId)
    assertTrue(ok.toString(), ok is WarehouseEngine.Result.Ok)
    assertEquals(8.0, ShopStore.stock((ok as WarehouseEngine.Result.Ok).data, "p1"), 0.0)
    //  ویرایش: مقدار یا کالای ورودِ خرید عوض نمی‌شود؛ انقضا و یادداشت مثلِ امروز
    val edit = WarehouseEngine.EntryDraft(productId = "p1", units = 10.0, unit = bought.unit, price = 100.0, date = "2026-10-01", expiryDate = "2027-02-01")
    assertTrue(WarehouseEngine.editEntry(d, bought.id, edit.copy(units = 12.0), "2026-10-03") is WarehouseEngine.Result.Failed)
    assertTrue(WarehouseEngine.editEntry(d, bought.id, edit.copy(productId = "p2"), "2026-10-03") is WarehouseEngine.Result.Failed)
    val dated = WarehouseEngine.editEntry(d, bought.id, edit, "2026-10-03")
    assertTrue(dated.toString(), dated is WarehouseEngine.Result.Ok)
    assertEquals("2027-02-01", (dated as WarehouseEngine.Result.Ok).data.warehouseEntries.first { it.id == bought.id }.expiryDate)
  }

  /*
   *  PUR-05: کلیدِ فرمِ «ثبت خرید» — دو کلیک یا F9 ِ پشتِ‌سرِهم یک فاکتور است. با دفترِ کهنه (پیش از رسیدنِ ذخیرهٔ اول)
   *  همان شناسه‌ها ساخته می‌شوند تا ادغام یکی‌شان کند؛ با دفترِ تازه رد می‌شود و موجودی و بدهی یک بار می‌مانند.
   */
  @Test fun `کلیدِ فاکتورِ خرید — دو بار ثبت یک فاکتور است`() {
    val lines = listOf(PurchaseBook.Line("p1", 10.0, 120.0), PurchaseBook.Line("p2", 2.0, 550.0))
    val first = PurchaseBook.record(d0, "s1", lines, 500.0, "2026-10-10", "", "2026-10-10", 1, ::newId, key = "k1") as PurchaseBook.Result.Ok
    val d1 = first.data
    assertEquals(2, d1.purchases.size)
    assertTrue(d1.purchases.all { it.id.startsWith("k1-") })
    val again = PurchaseBook.record(d1, "s1", lines, 500.0, "2026-10-10", "", "2026-10-10", 2, ::newId, key = "k1")
    assertEquals(PurchaseBook.Result.Failed(PurchaseBook.ALREADY_RECORDED), again)
    val stale = PurchaseBook.record(d0, "s1", lines, 500.0, "2026-10-10", "", "2026-10-10", 2, ::newId, key = "k1") as PurchaseBook.Result.Ok
    assertEquals(d1.purchases.map { it.id }, stale.data.purchases.map { it.id })
    assertEquals(d1.warehouseEntries.map { it.id }, stale.data.warehouseEntries.map { it.id })
    assertEquals(d1.stockMovements.map { it.id }, stale.data.stockMovements.map { it.id })
    assertEquals(d1.auditLog.map { it.id }, stale.data.auditLog.map { it.id })
    assertEquals(10.0, ShopStore.stock(d1, "p1"), 0.0)
    assertEquals(1800.0, ShopStore.supplierDebt(d1, "s1"), 0.0)
    //  کلیدِ دیگر ⇒ فاکتورِ دیگر؛ بی کلید همان رفتارِ پیشین
    val other = PurchaseBook.record(d1, "s1", lines.take(1), 0.0, "2026-10-11", "", "2026-10-11", 3, ::newId, key = "k2") as PurchaseBook.Result.Ok
    assertEquals(2, other.invoiceNo)
    assertEquals(20.0, ShopStore.stock(other.data, "p1"), 0.0)
    //  خریدِ تک‌ردیفه با شناسه‌های همان کلید (فرمِ گوشی بی ردیفِ افزوده) هم دوباره نمی‌نشیند
    val single = LedgerEngine.addPurchase(d1, LedgerEngine.PurchaseDraft(supplierId = "s1", productId = "p1", quantity = 1.0, purchasePrice = 120.0),
      "2026-10-12", 4, PurchaseBook.keyedIds("k1"))
    assertEquals(LedgerEngine.Result.Failed(PurchaseBook.ALREADY_RECORDED), single)
  }

  /*
   *  PUR-07: کلیدِ برگهٔ «پرداخت به تأمین‌کننده» — دو لمسِ پشتِ‌سرِهم یک پرداخت است (همان شناسه با دفترِ کهنه، رد با دفترِ
   *  تازه)؛ بدهی یک بار کم می‌شود. بی کلید همان رفتارِ پیشین (هر بار پرداختِ تازه).
   */
  @Test fun `کلیدِ پرداخت به تأمین‌کننده — دو لمس یک پرداخت است`() {
    val d1 = (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 10.0, 100.0)), 0.0, "2026-10-01", "", "2026-10-01", 1, ::newId)
      as PurchaseBook.Result.Ok).data
    val paid = LedgerEngine.paySupplier(d1, "s1", 300.0, "2026-10-02", "", "2026-10-02", 2, ::newId, key = "pay1") as LedgerEngine.Result.Ok
    assertEquals("pay1", paid.id)
    assertEquals(700.0, ShopStore.supplierDebt(paid.data, "s1"), 0.0)
    assertEquals(LedgerEngine.Result.Failed(LedgerEngine.ALREADY_PAID), LedgerEngine.paySupplier(paid.data, "s1", 300.0, "2026-10-02", "", "2026-10-02", 3, ::newId, key = "pay1"))
    val stale = LedgerEngine.paySupplier(d1, "s1", 300.0, "2026-10-02", "", "2026-10-02", 3, ::newId, key = "pay1") as LedgerEngine.Result.Ok
    assertEquals(paid.data.supplierPayments.map { it.id }, stale.data.supplierPayments.map { it.id })
    assertEquals(paid.data.auditLog.map { it.id }, stale.data.auditLog.map { it.id })
    val second = LedgerEngine.paySupplier(paid.data, "s1", 100.0, "2026-10-03", "", "2026-10-03", 4, ::newId) as LedgerEngine.Result.Ok
    assertEquals(600.0, ShopStore.supplierDebt(second.data, "s1"), 0.0)
  }
}
