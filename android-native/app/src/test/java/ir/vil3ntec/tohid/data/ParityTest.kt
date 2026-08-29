package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  برابریِ منطق با نسخهٔ وب.
 *
 *  هر تست اینجا یک رفتارِ مشخصِ `index.html` را می‌سنجد — نه «کار می‌کند
 *  یا نه»، بلکه «همان عددی درمی‌آید که وب درمی‌آورد یا نه». چون فروشنده
 *  ممکن است صبح با گوشی بفروشد و شب با سایت حساب کند؛ اگر دو عدد فرق
 *  کنند، هیچ‌کدام قابلِ اعتماد نیست.
 */
class ParityTest {

  private var seq = 0
  private fun newId(): String = "x${seq++}"

  private val today = "2026-01-15"

  private fun shop(): ShopData = ShopData(
    products = listOf(
      Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0, minStock = 5.0),
      Product(id = "p2", name = "روغن", unit = "عدد", purchasePrice = 100.0, salePrice = 150.0),
    ),
    debtors = listOf(Debtor(id = "d1", name = "احمد")),
    suppliers = listOf(Supplier(id = "s1", name = "تأمین‌کننده")),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 20.0, unit = "کیلو", price = 200.0, date = today),
      WarehouseEntry(id = "w2", productId = "p2", units = 10.0, unit = "عدد", price = 100.0, date = today),
    ),
  )

  private fun ok(r: SalesEngine.Result): SalesEngine.Result.Ok {
    assertTrue("انتظار می‌رفت ثبت شود، ولی: $r", r is SalesEngine.Result.Ok)
    return r as SalesEngine.Result.Ok
  }

  private fun failed(r: SalesEngine.Result): String {
    assertTrue("انتظار می‌رفت رد شود", r is SalesEngine.Result.Failed)
    return (r as SalesEngine.Result.Failed).message
  }

  private fun sell(
    d: ShopData = shop(),
    cart: List<SalesEngine.CartLine> = listOf(SalesEngine.CartLine("p1", 2.0)),
    checkout: SalesEngine.Checkout = SalesEngine.Checkout(),
  ) = SalesEngine.record(d, cart, checkout, today, 1_000L, ::newId)

  /* ============================ فروش نقدی ============================ */

  @Test
  fun `فروش نقدی کامل تسویه می شود`() {
    val r = ok(sell())
    val sale = r.data.sales.single()
    assertEquals(600.0, sale.finalTotal, 0.001)
    assertEquals(600.0, sale.paidAmount, 0.001)
    assertEquals(0.0, sale.remaining, 0.001)
    assertEquals("cash", sale.paymentMethod)
    assertEquals(0.0, sale.debtGiven, 0.001)
  }

  @Test
  fun `زنجیرهٔ فروش کامل ساخته می شود`() {
    // Sale → SaleItems → StockMovement → Audit
    val r = ok(sell())
    val saleId = r.data.sales.single().id
    assertEquals(1, r.data.saleItems.count { it.saleId == saleId })
    assertEquals(1, r.data.stockMovements.count { it.refId == saleId && it.type == "sale" })
    assertEquals(1, r.data.auditLog.count { it.refId == saleId && it.type == "sale" })
    // حرکتِ انبارِ فروش منفی است
    assertEquals(-2.0, r.data.stockMovements.single { it.refId == saleId }.qty, 0.001)
  }

  @Test
  fun `قیمت خرید لحظهٔ فروش روی قلم فاکتور می ماند`() {
    // اگر بعداً قیمتِ خرید عوض شود، سودِ فاکتورهای قدیمی نباید تکان بخورد
    val r = ok(sell())
    assertEquals(200.0, r.data.saleItems.single().purchasePrice, 0.001)
    assertEquals(300.0, r.data.saleItems.single().unitPrice, 0.001)
  }

  /* ============================= تخفیف ============================= */

  @Test
  fun `تخفیف درصدی گرد می شود`() {
    // ۶۰۰ با ۱۰٪ → ۶۰ تخفیف → ۵۴۰
    val r = ok(sell(checkout = SalesEngine.Checkout(
      discountType = SalesEngine.DiscountType.PERCENT, discountValue = 10.0,
    )))
    val sale = r.data.sales.single()
    assertEquals(600.0, sale.total, 0.001)
    assertEquals(60.0, sale.discount, 0.001)
    assertEquals(540.0, sale.finalTotal, 0.001)
  }

  @Test
  fun `تخفیف درصدی از صد بیشتر نمی شود`() {
    val r = ok(sell(checkout = SalesEngine.Checkout(
      discountType = SalesEngine.DiscountType.PERCENT, discountValue = 250.0,
    )))
    assertEquals(0.0, r.data.sales.single().finalTotal, 0.001)
  }

  @Test
  fun `تخفیف مبلغی از جمع فاکتور بیشتر نمی شود`() {
    val r = ok(sell(checkout = SalesEngine.Checkout(
      discountType = SalesEngine.DiscountType.AMOUNT, discountValue = 5000.0,
    )))
    val sale = r.data.sales.single()
    assertEquals(600.0, sale.discount, 0.001)
    assertEquals(0.0, sale.finalTotal, 0.001)
  }

  /* =========================== فروش نسیه =========================== */

  @Test
  fun `پرداخت جزئی، دقیقاً همان باقی مانده را در حساب قرض دار می گذارد`() {
    // نمونهٔ خودِ پرامپت: کل ۱۰۰۰، پرداختی ۴۰۰، باقی ۶۰۰
    val d = shop().let {
      it.copy(products = it.products.map { p -> if (p.id == "p1") p.copy(salePrice = 500.0) else p })
    }
    val r = ok(sell(
      d = d,
      cart = listOf(SalesEngine.CartLine("p1", 2.0)),
      checkout = SalesEngine.Checkout(
        payment = SalesEngine.Payment.CREDIT, paidAmount = 400.0, debtorId = "d1",
      ),
    ))
    val sale = r.data.sales.single()
    assertEquals(1000.0, sale.finalTotal, 0.001)
    assertEquals(400.0, sale.paidAmount, 0.001)
    assertEquals(600.0, sale.remaining, 0.001)
    assertEquals(600.0, sale.debtGiven, 0.001)
    // و همان ۶۰۰ در دفترِ قرض‌دار
    assertEquals(600.0, ShopStore.debt(r.data, "d1"), 0.001)
    assertEquals(1, r.data.transactions.count { it.debtorId == "d1" && it.type == "give" })
  }

  @Test
  fun `نسیهٔ بی قرض دار رد می شود`() {
    val message = failed(sell(checkout = SalesEngine.Checkout(
      payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, debtorId = null,
    )))
    assertTrue(message, message.contains("قرض‌دار"))
  }

  @Test
  fun `نسیه ای که کامل پرداخت شده، بدهی نمی سازد`() {
    val r = ok(sell(checkout = SalesEngine.Checkout(
      payment = SalesEngine.Payment.CREDIT, paidAmount = 600.0, debtorId = "d1",
    )))
    assertEquals(0.0, ShopStore.debt(r.data, "d1"), 0.001)
    assertEquals(0, r.data.transactions.size)
  }

  @Test
  fun `پرداختی بیشتر از فاکتور، تا سقف فاکتور کوتاه می شود`() {
    val r = ok(sell(checkout = SalesEngine.Checkout(
      payment = SalesEngine.Payment.CREDIT, paidAmount = 9999.0, debtorId = "d1",
    )))
    assertEquals(600.0, r.data.sales.single().paidAmount, 0.001)
    assertEquals(0.0, r.data.sales.single().remaining, 0.001)
  }

  /* ========================= سنجشِ موجودی ========================= */

  @Test
  fun `فروش بیشتر از موجودی، لحظهٔ ثبت رد می شود`() {
    val message = failed(sell(cart = listOf(SalesEngine.CartLine("p1", 999.0))))
    assertTrue(message, message.contains("موجود است"))
  }

  @Test
  fun `کالای بی موجودی پیام خودش را می دهد`() {
    val d = shop().copy(warehouseEntries = emptyList())
    val message = failed(sell(d = d))
    assertTrue(message, message.contains("موجودی ندارد"))
  }

  @Test
  fun `فروش، موجودی را دقیقاً به همان اندازه کم می کند`() {
    val r = ok(sell(cart = listOf(SalesEngine.CartLine("p1", 3.0))))
    assertEquals(17.0, ShopStore.stock(r.data, "p1"), 0.001)
  }

  @Test
  fun `سبد خالی ثبت نمی شود`() {
    failed(sell(cart = emptyList()))
  }

  /* ========================== مقدار اعشاری ========================== */

  @Test
  fun `مقدار اعشاری در سبد گرد نمی شود ولی شناور هم نمی لغزد`() {
    var cart = SalesEngine.addToCart(emptyList(), "p1", 0.1)
    cart = SalesEngine.addToCart(cart, "p1", 0.2)
    cart = SalesEngine.setCartQty(cart, "p1", cart.single().quantity)
    assertEquals(0.3, cart.single().quantity, 0.0001)
  }

  @Test
  fun `کالای تکراری ردیف تازه نمی سازد`() {
    var cart = SalesEngine.addToCart(emptyList(), "p1", 1.0)
    cart = SalesEngine.addToCart(cart, "p1", 2.0)
    assertEquals(1, cart.size)
    assertEquals(3.0, cart.single().quantity, 0.001)
  }

  @Test
  fun `تعداد صفر، ردیف را از سبد بیرون می برد`() {
    val cart = SalesEngine.setCartQty(SalesEngine.addToCart(emptyList(), "p1", 2.0), "p1", 0.0)
    assertTrue(cart.isEmpty())
  }

  /* ============================== لغو ============================== */

  @Test
  fun `لغو فروش، موجودی را برمی گرداند و فاکتور را پاک نمی کند`() {
    val sold = ok(sell(cart = listOf(SalesEngine.CartLine("p1", 5.0))))
    assertEquals(15.0, ShopStore.stock(sold.data, "p1"), 0.001)

    val cancelled = ok(SalesEngine.cancel(sold.data, sold.saleId, today, 2_000L, ::newId))
    assertEquals(20.0, ShopStore.stock(cancelled.data, "p1"), 0.001)
    assertEquals(1, cancelled.data.sales.size)
    assertEquals("cancelled", cancelled.data.sales.single().status)
  }

  @Test
  fun `لغو نسیه، بدهی قرض دار را صفر می کند`() {
    val sold = ok(sell(checkout = SalesEngine.Checkout(
      payment = SalesEngine.Payment.CREDIT, paidAmount = 100.0, debtorId = "d1",
    )))
    assertEquals(500.0, ShopStore.debt(sold.data, "d1"), 0.001)

    val cancelled = ok(SalesEngine.cancel(sold.data, sold.saleId, today, 2_000L, ::newId))
    assertEquals(0.0, ShopStore.debt(cancelled.data, "d1"), 0.001)
  }

  @Test
  fun `فاکتور دوبار لغو نمی شود`() {
    val sold = ok(sell())
    val once = ok(SalesEngine.cancel(sold.data, sold.saleId, today, 2_000L, ::newId))
    assertTrue(SalesEngine.cancel(once.data, sold.saleId, today, 3_000L, ::newId)
      is SalesEngine.Result.Failed)
  }

  /* ============================= مرجوعی ============================= */

  @Test
  fun `مرجوعی جزئی، موجودی و دفتر را درست می کند`() {
    val sold = ok(sell(cart = listOf(SalesEngine.CartLine("p1", 5.0))))
    val itemId = sold.data.saleItems.single().id

    val returned = ok(SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 2.0), "خراب بود", today, 2_000L, ::newId,
    ))
    assertEquals(17.0, ShopStore.stock(returned.data, "p1"), 0.001)
    assertEquals(2.0, returned.data.saleItems.single().returnedQty, 0.001)
    assertEquals(600.0, returned.data.saleReturns.single().amount, 0.001)
    assertEquals(1, returned.data.stockMovements.count { it.type == "customer_return" })
    assertEquals(1, returned.data.auditLog.count { it.type == "return" })
  }

  @Test
  fun `بیشتر از مقدار فاکتور برگردانده نمی شود`() {
    val sold = ok(sell(cart = listOf(SalesEngine.CartLine("p1", 3.0))))
    val itemId = sold.data.saleItems.single().id

    // ۱۰۰ خواسته شده ولی فقط ۳ تا فروخته شده
    val returned = ok(SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 100.0), "", today, 2_000L, ::newId,
    ))
    assertEquals(3.0, returned.data.saleReturns.single().quantity, 0.001)
    assertEquals(20.0, ShopStore.stock(returned.data, "p1"), 0.001)
  }

  @Test
  fun `مرجوعیِ دوم از باقی ماندهٔ همان قلم حساب می شود`() {
    val sold = ok(sell(cart = listOf(SalesEngine.CartLine("p1", 5.0))))
    val itemId = sold.data.saleItems.single().id

    val once = ok(SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 3.0), "", today, 2_000L, ::newId,
    ))
    val twice = ok(SalesEngine.recordReturn(
      once.data, sold.saleId, mapOf(itemId to 99.0), "", today, 3_000L, ::newId,
    ))
    // فقط ۲ تای باقی‌مانده برگشته، نه بیشتر
    assertEquals(5.0, twice.data.saleItems.single().returnedQty, 0.001)
    assertEquals(20.0, ShopStore.stock(twice.data, "p1"), 0.001)
  }

  @Test
  fun `مرجوعی به قیمت همان فاکتور برمی گردد، نه قیمت امروز`() {
    val sold = ok(sell(cart = listOf(SalesEngine.CartLine("p1", 4.0))))
    val itemId = sold.data.saleItems.single().id
    // قیمت امروز بالا می‌رود
    val pricier = sold.data.copy(
      products = sold.data.products.map { if (it.id == "p1") it.copy(salePrice = 900.0) else it }
    )
    val returned = ok(SalesEngine.recordReturn(
      pricier, sold.saleId, mapOf(itemId to 1.0), "", today, 2_000L, ::newId,
    ))
    assertEquals(300.0, returned.data.saleReturns.single().amount, 0.001)
  }

  @Test
  fun `مرجوعیِ نسیه، از بدهی قرض دار کم می کند`() {
    val sold = ok(sell(
      cart = listOf(SalesEngine.CartLine("p1", 4.0)),
      checkout = SalesEngine.Checkout(
        payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, debtorId = "d1",
      ),
    ))
    assertEquals(1200.0, ShopStore.debt(sold.data, "d1"), 0.001)

    val itemId = sold.data.saleItems.single().id
    val returned = ok(SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 1.0), "", today, 2_000L, ::newId,
    ))
    assertEquals(900.0, ShopStore.debt(returned.data, "d1"), 0.001)
  }

  @Test
  fun `مرجوعی و بعد لغو، بدهی را منفی نمی کند`() {
    // این همان جایی است که حساب راحت خراب می‌شود
    val sold = ok(sell(
      cart = listOf(SalesEngine.CartLine("p1", 4.0)),
      checkout = SalesEngine.Checkout(
        payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, debtorId = "d1",
      ),
    ))
    val itemId = sold.data.saleItems.single().id
    val returned = ok(SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 2.0), "", today, 2_000L, ::newId,
    ))
    val cancelled = ok(SalesEngine.cancel(returned.data, sold.saleId, today, 3_000L, ::newId))
    assertEquals(0.0, ShopStore.debt(cancelled.data, "d1"), 0.001)
  }

  @Test
  fun `مرجوعیِ بی مقدار رد می شود`() {
    val sold = ok(sell())
    val itemId = sold.data.saleItems.single().id
    failed(SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 0.0), "", today, 2_000L, ::newId,
    ))
  }

  /* ======================= شمارهٔ فاکتور ======================= */

  @Test
  fun `شمارهٔ فاکتور یکی یکی بالا می رود`() {
    val one = ok(sell())
    val two = ok(SalesEngine.record(
      one.data, listOf(SalesEngine.CartLine("p2", 1.0)), SalesEngine.Checkout(), today, 2_000L, ::newId,
    ))
    assertEquals(one.invoiceNumber + 1, two.invoiceNumber)
    assertEquals(two.invoiceNumber + 1, two.data.nextInvoiceNo)
  }

  /* ===================== بدهیِ تأمین‌کننده ===================== */

  @Test
  fun `بدهی تأمین کننده از مبلغ منهای پرداختی حساب می شود`() {
    val d = shop().copy(
      purchases = listOf(
        Purchase(id = "pu1", supplierId = "s1", totalAmount = 1000.0, paidAmount = 400.0, debt = 600.0),
      ),
    )
    assertEquals(600.0, ShopStore.supplierDebt(d, "s1"), 0.001)
  }

  @Test
  fun `پشتیبانِ قدیمی بدون فیلد debt هم درست خوانده می شود`() {
    // نسخه‌های قدیمیِ وب فیلد debt را نداشتند؛ اگر از خودِ فیلد حساب
    // می‌کردیم، بدهی صفر یا منفی درمی‌آمد
    val d = shop().copy(
      purchases = listOf(
        Purchase(id = "pu1", supplierId = "s1", totalAmount = 1000.0, paidAmount = 400.0, debt = 0.0),
      ),
    )
    assertEquals(600.0, ShopStore.supplierDebt(d, "s1"), 0.001)
  }

  @Test
  fun `پرداخت جداگانه به تأمین کننده هم از بدهی کم می شود`() {
    val d = shop().copy(
      purchases = listOf(
        Purchase(id = "pu1", supplierId = "s1", totalAmount = 1000.0, paidAmount = 400.0, debt = 600.0),
      ),
      supplierPayments = listOf(SupplierPayment(id = "sp1", supplierId = "s1", amount = 250.0, date = today)),
    )
    assertEquals(350.0, ShopStore.supplierDebt(d, "s1"), 0.001)
  }

  /* ==================== حذفِ ورودِ انبار ==================== */

  @Test
  fun `ورودی ای که جنسش فروخته شده، حذف نمی شود`() {
    val sold = ok(sell(cart = listOf(SalesEngine.CartLine("p1", 18.0))))
    // ۲۰ آمده، ۱۸ رفته؛ حذفِ ورودیِ ۲۰تایی موجودی را منفی می‌کند
    val result = WarehouseEngine.deleteEntry(sold.data, "w1", today, 3_000L, ::newId)
    assertTrue(result is WarehouseEngine.Result.Failed)
    val message = (result as WarehouseEngine.Result.Failed).message
    assertTrue(message, message.contains("فروخته شده"))
  }

  @Test
  fun `ورودی دست نخورده حذف می شود و سابقه اش می ماند`() {
    val d = shop()
    val result = WarehouseEngine.deleteEntry(d, "w1", today, 3_000L, ::newId)
    assertTrue(result is WarehouseEngine.Result.Ok)
    val after = (result as WarehouseEngine.Result.Ok).data
    assertEquals(0.0, ShopStore.stock(after, "p1"), 0.001)
    assertEquals(1, after.auditLog.count { it.type == "delete_entry" })
  }

  /* ==================== موجودی هرگز منفی نشود ==================== */

  @Test
  fun `هیچ مسیری موجودی را منفی نمی کند`() {
    var d = shop()
    val sold = ok(sell(d = d, cart = listOf(SalesEngine.CartLine("p1", 20.0))))
    d = sold.data
    assertEquals(0.0, ShopStore.stock(d, "p1"), 0.001)
    // فروش دوم باید رد شود
    failed(SalesEngine.record(
      d, listOf(SalesEngine.CartLine("p1", 1.0)), SalesEngine.Checkout(), today, 2_000L, ::newId,
    ))
    // حذف ورودی هم باید رد شود
    assertTrue(WarehouseEngine.deleteEntry(d, "w1", today, 3_000L, ::newId)
      is WarehouseEngine.Result.Failed)
  }
}
