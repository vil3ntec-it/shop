package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderLineDto
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  تحویلِ سفارشِ آنلاین ⇒ فروشِ عادی در دفترِ دکان (`OrderFulfillment`) — تصمیمِ صاحبِ مخزن:
 *  سایت و سرور به موجودی دست نمی‌زنند؛ کسر فقط همین‌جاست.
 *
 *    • همان `SalesEngine.record`: موجودی یک بار کم، قیمت‌ها و جمع‌ها دقیقاً همانِ سفارش؛
 *    • یکتا با شناسهٔ سفارش: تکرار ⇒ «پیش‌تر ثبت شده»؛ دو دستگاه ⇒ همان ردیف‌ها و پس از
 *      همگام‌سازی یک فروش و یک بار کسر؛ فاکتورِ W ِ قدیمی هم «فروخته» شمرده می‌شود؛
 *    • کمبود ⇒ سیاستِ صندوق با پیامِ روشن (جمعِ قلم‌های یک کالا با هم)؛
 *    • پول: آنلاین و نقدِ پیک بیرونِ دخل، نقدِ سرِ پیشخوان در دخل (`CashShift`).
 *  قرینهٔ JS: `server/test/e2e/lib/native.mjs` (`fulfillOrder`) در `stock.e2e.mjs`.
 */
class OrderFulfillmentTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", salePrice = 120.0, purchasePrice = 90.0)
  private val cola = Product(id = "p2", name = "نوشابه", unit = "دانه", saleUnit = "کارتن", unitFactor = 24.0, salePrice = 30.0, purchasePrice = 20.0)
  private fun ledger(riceUnits: Double = 10.0) = ShopData(
    products = listOf(rice, cola),
    warehouseEntries = listOf(
      WarehouseEntry(id = "we1", productId = "p1", units = riceUnits),
      WarehouseEntry(id = "we2", productId = "p2", units = 48.0),
    ),
    debtors = listOf(Debtor(id = "c1", name = "احمد", phone = "0791 234 567")),
    nextInvoiceNo = 7,
  )

  private fun order(
    fulfillment: String = "pickup",
    paymentMethod: String = "cod",
    paymentStatus: String = "unpaid",
    lines: List<OrderLineDto> = listOf(
      OrderLineDto(lineNo = 1, productId = "p1", name = "برنج", unit = "کیلو", qtyMilli = 1500, unitPriceMinor = 12000, lineTotalMinor = 18000),
      OrderLineDto(lineNo = 2, productId = "", name = "کیسه", qtyMilli = 1000, unitPriceMinor = 550, lineTotalMinor = 550),
    ),
    feeMinor: Long = 0,
    discountMinor: Long = 1000,
    phone: String? = null,
  ): OrderDto {
    val subtotal = lines.sumOf { it.lineTotalMinor }
    return OrderDto(
      id = "01ORDER", orderNo = 12, status = "picked_up", fulfillment = fulfillment, paymentMethod = paymentMethod,
      paymentStatus = paymentStatus, customer = OrderCustomerDto(name = "احمد", phone = phone),
      subtotalMinor = subtotal, discountMinor = discountMinor, deliveryFeeMinor = feeMinor,
      totalMinor = subtotal - discountMinor + feeMinor, lines = lines,
    )
  }

  private fun ok(r: OrderFulfillment.Result): OrderFulfillment.Result.Ok {
    assertTrue("$r", r is OrderFulfillment.Result.Ok)
    return r as OrderFulfillment.Result.Ok
  }

  @Test
  fun `تحویل ⇒ فروشِ عادی با شناسه‌های ثابت، قیمت‌های دقیقِ سفارش و یک بار کسرِ موجودی`() {
    val r = ok(OrderFulfillment.fulfill(ledger(), order(), "2026-10-19", 1_000L, series = "A1", seller = "علی"))
    val d = r.data
    val sale = d.sales.single()
    assertEquals("o-01ORDER", sale.id)
    assertEquals("o-01ORDER", r.saleId)
    assertEquals("01ORDER", sale.orderId)
    assertEquals(12L, sale.orderNo)
    assertEquals("online", sale.channel)
    assertTrue(sale.isOnlineOrder())
    assertEquals("A1-7", sale.invoiceLabel())
    assertEquals(8, d.nextInvoiceNo)
    assertEquals("علی", sale.seller)
    assertEquals(185.5, sale.total, 1e-9)
    assertEquals(10.0, sale.discount, 1e-9)
    assertEquals(175.5, sale.finalTotal, 1e-9)
    assertEquals(sale.finalTotal, sale.paidAmount, 1e-9)
    assertEquals(0.0, sale.remaining, 1e-9)
    assertEquals(0.0, sale.debtGiven, 1e-9)

    assertEquals(listOf("o-01ORDER-1", "o-01ORDER-2"), d.saleItems.map { it.id })
    val riceItem = d.saleItems[0]
    assertEquals("p1", riceItem.productId)
    assertEquals(1.5, riceItem.quantity, 1e-9)
    assertEquals(180.0, riceItem.totalPrice, 1e-9)
    assertEquals(90.0, riceItem.purchasePrice, 1e-9)
    val bag = d.saleItems[1]
    assertEquals("", bag.productId)
    assertEquals("کیسه", bag.name)
    assertEquals(5.5, bag.totalPrice, 1e-9)
    assertEquals(listOf("o-01ORDER-1-m"), d.stockMovements.map { it.id })
    assertEquals(-1.5, d.stockMovements.single().qty, 1e-9)
    assertEquals(listOf("o-01ORDER-a"), d.auditLog.map { it.id })
    assertTrue(d.auditLog.single().notes.contains("#12"))

    assertEquals(8.5, ShopStore.stock(d, "p1"), 1e-9)
    //  دفتر همان شکلِ مدل را دارد ⇒ هیچ پژواکی (op ِ اضافه) پس از ذخیره نیست
    val json = LedgerDiff.toJson(d)
    assertEquals(0, LedgerDiff.diff(LedgerDiff.shadowOf(json), LedgerDiff.toJson(LedgerDiff.fromJson(json))).size)
  }

  @Test
  fun `تکرار، فروشِ دستگاهِ دیگر و فاکتورِ W ِ قدیمی ⇒ پیش‌تر ثبت شده، هیچ تغییری`() {
    val once = ok(OrderFulfillment.fulfill(ledger(), order(), "2026-10-19", 1L)).data
    val again = OrderFulfillment.fulfill(once, order(), "2026-10-19", 2L)
    assertEquals(OrderFulfillment.Result.Already("o-01ORDER"), again)
    val legacy = ledger().copy(sales = listOf(Sale(id = "w-01ORDER", series = "W", channel = "online")))
    assertEquals(OrderFulfillment.Result.Already("w-01ORDER"), OrderFulfillment.fulfill(legacy, order(), "2026-10-19", 3L))
    assertFalse(OrderFulfillment.needsSale(legacy, order()))
    assertTrue(OrderFulfillment.needsSale(ledger(), order()))
    assertFalse("سفارشِ آزمایشی هرگز", OrderFulfillment.needsSale(ledger(), order().copy(isTest = true)))
    assertFalse("پیش از تحویل نه", OrderFulfillment.needsSale(ledger(), order().copy(status = "packed")))
  }

  @Test
  fun `دو دستگاه هم‌زمان تحویل را ثبت می‌کنند ⇒ پس از همگام‌سازی یک فروش و یک بار کسر`() {
    val base = ledger()
    val a = ok(OrderFulfillment.fulfill(base, order(), "2026-10-19", 10L, series = "A1")).data
    val b = ok(OrderFulfillment.fulfill(base, order(), "2026-10-19", 11L, series = "B1")).data
    //  همان شناسه‌ها روی هر دو دستگاه
    assertEquals(a.saleItems.map { it.id }, b.saleItems.map { it.id })
    assertEquals(a.stockMovements.map { it.id }, b.stockMovements.map { it.id })
    //  opهای B (همان که B به سرور می‌فرستد) روی دفترِ A
    val opsB = LedgerDiff.diff(LedgerDiff.shadowOf(base), LedgerDiff.toJson(b))
    val merged = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(a), opsB))
    assertEquals(1, merged.sales.size)
    assertEquals(2, merged.saleItems.size)
    assertEquals(8.5, ShopStore.stock(merged, "p1"), 1e-9)
  }

  @Test
  fun `کمبود ⇒ سیاستِ صندوق با پیامِ روشن؛ دو قلمِ یک کالا با هم سنجیده می‌شوند`() {
    val short = OrderFulfillment.fulfill(ledger(riceUnits = 1.0), order(), "2026-10-19", 1L)
    assertTrue("$short", short is OrderFulfillment.Result.Failed)
    val msg = (short as OrderFulfillment.Result.Failed).message
    assertTrue(msg, msg.startsWith("سفارش #12: ") && msg.contains("برنج"))
    //  ۲ + ۲ کیلو از یک کالا با ۳ کیلو موجودی
    val twice = order(lines = listOf(
      OrderLineDto(lineNo = 1, productId = "p1", name = "برنج", unit = "کیلو", qtyMilli = 2000, unitPriceMinor = 12000, lineTotalMinor = 24000),
      OrderLineDto(lineNo = 2, productId = "p1", name = "برنج", unit = "کیلو", qtyMilli = 2000, unitPriceMinor = 12000, lineTotalMinor = 24000),
    ), discountMinor = 0)
    assertTrue(OrderFulfillment.fulfill(ledger(riceUnits = 3.0), twice, "2026-10-19", 1L) is OrderFulfillment.Result.Failed)
    assertTrue(OrderFulfillment.fulfill(ledger(riceUnits = 4.0), twice, "2026-10-19", 1L) is OrderFulfillment.Result.Ok)
  }

  @Test
  fun `واحدِ فروش (کارتن) با ضریب به واحدِ موجودی؛ هزینهٔ ارسال قلمِ آزاد؛ مشتری با شماره وصل`() {
    val o = order(
      fulfillment = "delivery",
      lines = listOf(OrderLineDto(lineNo = 1, productId = "p2", name = "نوشابه", unit = "کارتن", qtyMilli = 1000, unitPriceMinor = 70000, lineTotalMinor = 70000)),
      feeMinor = 5000, discountMinor = 0, phone = "+93791234567",
    )
    val d = ok(OrderFulfillment.fulfill(ledger(), o, "2026-10-19", 1L)).data
    assertEquals(24.0, d.saleItems.first { it.productId == "p2" }.quantity, 1e-9)
    assertEquals(24.0, ShopStore.stock(d, "p2"), 1e-9)
    val fee = d.saleItems.first { it.id == "o-01ORDER-fee" }
    assertEquals(OrderFulfillment.FEE_LABEL, fee.name)
    assertEquals("", fee.productId)
    assertEquals(50.0, fee.totalPrice, 1e-9)
    assertEquals(750.0, d.sales.single().finalTotal, 1e-9)
    assertEquals("c1", d.sales.single().debtorId)
    assertTrue("مشتری بدهکار نمی‌شود", d.transactions.isEmpty())
  }

  /* ───────────── پول: یک آزمون برای هر حالت (بازبینیِ پول) ───────────── */

  private fun drawer(sale: Sale): CashShift.Totals {
    var d = ShopData()
    var seq = 0
    d = (CashShift.open(d, 1000.0, "", "2026-10-19", 100, { "x${seq++}" }) as CashShift.Result.Ok).data
    d = d.copy(sales = listOf(sale.copy(createdAt = 110)))
    return CashShift.totalsOf(d, CashShift.current(d)!!, 200)
  }

  @Test
  fun `پرداختِ آنلاین ⇒ online با ردیفِ other؛ پولِ دخل نیست`() {
    val paid = ok(OrderFulfillment.fulfill(ledger(), order(paymentMethod = "online", paymentStatus = "paid"), "2026-10-19", 1L)).data.sales.single()
    assertEquals("online", paid.paymentMethod)
    val row = paid.payments!!.single()
    assertEquals("other", row.kind)
    assertEquals(17550L, row.amountMinor)
    assertEquals("confirmed", row.status)
    assertEquals("provider", row.verification)
    assertNull(row.custody)
    assertTrue(CashShift.isOnline(paid))
    assertEquals(0.0, drawer(paid).sales, 1e-9)
    assertEquals(1000.0, drawer(paid).expected, 1e-9)
    //  پرداختِ آنلاینِ تأییدنشده «در انتظار» است
    val unpaid = ok(OrderFulfillment.fulfill(ledger(), order(paymentMethod = "online"), "2026-10-19", 1L)).data.sales.single()
    assertEquals("pending", unpaid.payments!!.single().status)
    assertNull(unpaid.payments!!.single().verification)
  }

  @Test
  fun `نقدِ هنگامِ تحویل با پیک ⇒ cod با ردیفِ نقدِ custody=courier؛ تا تسویهٔ پیک پولِ دخل نیست`() {
    val sale = ok(OrderFulfillment.fulfill(ledger(), order(fulfillment = "delivery"), "2026-10-19", 1L)).data.sales.single()
    assertEquals("cod", sale.paymentMethod)
    val row = sale.payments!!.single()
    assertEquals("cash", row.kind)
    assertEquals("courier", row.custody)
    assertEquals("pending", row.status)
    assertTrue(CashShift.isOnline(sale))
    assertFalse(CashShift.paidAtCounter(sale))
    assertEquals(0.0, drawer(sale).sales, 1e-9)
    assertEquals(1000.0, drawer(sale).expected, 1e-9)
  }

  @Test
  fun `تحویل در دکان و نقد سرِ پیشخوان ⇒ cash با ردیفِ نقدِ بی‌امانت؛ تنها حالتی که به دخل می‌آید`() {
    val sale = ok(OrderFulfillment.fulfill(ledger(), order(fulfillment = "pickup"), "2026-10-19", 1L)).data.sales.single()
    assertEquals("cash", sale.paymentMethod)
    assertEquals("online", sale.channel)
    val row = sale.payments!!.single()
    assertEquals("cash", row.kind)
    assertNull(row.custody)
    assertEquals("confirmed", row.status)
    assertTrue(CashShift.paidAtCounter(sale))
    assertFalse(CashShift.isOnline(sale))
    assertTrue("گزارش هنوز «اینترنتی» می‌شمارد", sale.isOnlineOrder())
    assertEquals(175.5, drawer(sale).sales, 1e-9)
    assertEquals(1175.5, drawer(sale).expected, 1e-9)
  }

  @Test
  fun `فروشِ تحویل از صندوق لغو نمی‌شود؛ برگشتِ کالا مرجوعیِ عادی است`() {
    val d = ok(OrderFulfillment.fulfill(ledger(), order(), "2026-10-19", 1L)).data
    val cancel = SalesEngine.cancel(d, "o-01ORDER", "2026-10-19", 2L, { "c" }, reason = "x")
    assertEquals(SalesEngine.Result.Failed(SalesEngine.ONLINE_ORDER_LOCKED), cancel)
    var seq = 0
    val back = SalesEngine.recordReturn(d, "o-01ORDER", mapOf("o-01ORDER-1" to 1.5), "", "2026-10-19", 3L, { "r${seq++}" })
    assertTrue("$back", back is SalesEngine.Result.Ok)
    assertEquals(10.0, ShopStore.stock((back as SalesEngine.Result.Ok).data, "p1"), 1e-9)
  }
}
