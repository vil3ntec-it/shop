package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrderLineDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.model.RefundEventDto
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  بازپرداختِ سفارشِ تحویل‌شده ⇒ سندِ برگشتِ همان فروش در دفترِ دکان (`OrderFulfillment.refund`،
 *  RET-11، FIN-01، DATA-04). قرینهٔ سرور: `server/test/order-refund-ledger.test.js`.
 *
 *    • کامل یا جزئی ⇒ دقیقاً همان مبلغ، در روز و زمانِ بازپرداخت؛ فاکتور، اقلام و موجودی دست نمی‌خورند؛
 *    • یک بار: تکرار، دستگاهِ دوم و ادغامِ Sync v1 ⇒ یک سند (شناسهٔ ثابت، بی `$inc`)؛
 *    • پول: آنلاین به همان روش (دخل نه)، نقدِ پیشخوان و پیک از دخل — همان قاعدهٔ برگشتِ صندوق؛
 *    • کالا فقط با «کالا به انبار برگشت؟» و فقط قلم‌های زده‌شده، یک بار؛ برگشتِ دستیِ بعدی همان را نه.
 */
class OrderRefundLedgerTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", salePrice = 120.0, purchasePrice = 90.0)
  private fun ledger() = ShopData(
    products = listOf(rice),
    warehouseEntries = listOf(WarehouseEntry(id = "we1", productId = "p1", units = 10.0)),
    nextInvoiceNo = 7,
  )

  private val saleDay = "2026-10-19"
  private val refundDay = "2026-10-25"
  private val refundAt = 9_000_000L
  private val day: (Long) -> String = { at -> if (at >= refundAt) refundDay else saleDay }

  private fun order(
    fulfillment: String = "pickup",
    paymentMethod: String = "cod",
    paymentStatus: String = "unpaid",
    events: List<RefundEventDto> = emptyList(),
    firstStatus: String = "picked_up",
  ): OrderDto {
    val lines = listOf(
      OrderLineDto(lineNo = 1, productId = "p1", name = "برنج", unit = "کیلو", qtyMilli = 1500, unitPriceMinor = 12000, lineTotalMinor = 18000),
      OrderLineDto(lineNo = 2, productId = "", name = "کیسه", qtyMilli = 1000, unitPriceMinor = 550, lineTotalMinor = 550),
    )
    return OrderDto(
      id = "01ORDER", orderNo = 12, status = "refunded_full", fulfillment = fulfillment, paymentMethod = paymentMethod,
      paymentStatus = paymentStatus, customer = OrderCustomerDto(name = "احمد"),
      subtotalMinor = 18550, discountMinor = 1000, totalMinor = 17550, lines = lines,
      events = listOf(OrderEventDto(kind = "status", toStatus = firstStatus, at = 5_000L)),
      action = "refund", refundEvents = events,
    )
  }

  private fun full(minor: Long = 17550, source: String = "status") = RefundEventDto("full", minor, refundAt, source)

  private fun ok(r: OrderFulfillment.Result): OrderFulfillment.Result.Ok {
    assertTrue("$r", r is OrderFulfillment.Result.Ok)
    return r as OrderFulfillment.Result.Ok
  }

  /** فروشِ تحویلِ همان سفارش در روزِ فروش */
  private fun sold(o: OrderDto = order()): ShopData = ok(OrderFulfillment.fulfill(ledger(), o, saleDay, 5_000L, allowShortage = true)).data

  private fun refunded(o: OrderDto, base: ShopData = sold(o)): ShopData = ok(OrderFulfillment.refund(base, o, "علی", day)).data

  /** دخلِ صندوقی که پیش از فروش باز شد و پس از بازپرداخت هنوز باز است */
  private fun drawer(d: ShopData): CashShift.Totals {
    var seq = 0
    val opened = (CashShift.open(ShopData(), 1000.0, "", saleDay, 100, { "s${seq++}" }) as CashShift.Result.Ok).data
    val x = d.copy(auditLog = d.auditLog + opened.auditLog)
    return CashShift.totalsOf(x, CashShift.current(x)!!, refundAt + 1)
  }

  @Test
  fun `بازپرداختِ کامل ⇒ سندِ برگشت با همان مبلغ در روزِ بازپرداخت و فاکتور و موجودی دست نمی‌خورند`() {
    val o = order(events = listOf(full()))
    val before = sold(o)
    val d = refunded(o, before)
    val r = d.saleReturns.single()
    assertEquals("o-01ORDER-rf-full", r.docId)
    assertEquals("o-01ORDER-rf-full-0", r.id)
    assertEquals("o-01ORDER", r.saleId)
    assertEquals(175.5, r.amount, 1e-9)
    assertEquals(0.0, r.quantity, 1e-9)
    assertEquals(refundDay, r.date)
    assertEquals(refundAt, r.createdAt)
    assertEquals(ReturnFlow.ORIGINAL, r.refund)
    assertEquals("علی", r.by)
    //  سندِ ثبت‌شده عوض نمی‌شود و شمارنده‌ای بالا نمی‌رود
    assertEquals(before.sales, d.sales)
    assertEquals(before.saleItems, d.saleItems)
    assertEquals(8.5, ShopStore.stock(d, "p1"), 1e-9)
    val mark = d.auditLog.single { it.id == "o-01ORDER-rf-full-a" }
    assertEquals("return", mark.type)
    assertEquals(refundDay, mark.date)
    //  تکرار ⇒ هیچ
    assertTrue(OrderFulfillment.refund(d, o, "علی", day) is OrderFulfillment.Result.Already)
    assertEquals(175.5, ReturnFlow.returnedAmount(d, "o-01ORDER"), 1e-9)
  }

  @Test
  fun `دو دستگاه همان بازپرداخت را می‌نشانند ⇒ پس از ادغام یک سند و یک بار در گزارش`() {
    val o = order(events = listOf(full()))
    val base = sold(o)
    val a = refunded(o, base)
    val b = refunded(o, base)
    assertEquals(a.saleReturns, b.saleReturns)
    assertEquals(a.auditLog, b.auditLog)
    val opsB = LedgerDiff.diff(LedgerDiff.shadowOf(base), LedgerDiff.toJson(b))
    val merged = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(a), opsB))
    assertEquals(1, merged.saleReturns.size)
    assertEquals(175.5, ReportEngine.sales(merged, refundDay, refundDay).returnAmount, 1e-9)
    assertEquals(8.5, ShopStore.stock(merged, "p1"), 1e-9)
  }

  @Test
  fun `بازپرداخت‌های جزئیِ دفتر ⇒ دقیقاً همان مبلغ‌ها و رویدادِ تازه فقط خودش`() {
    val r1 = RefundEventDto("r1", 3000, refundAt, "ledger")
    val first = refunded(order(events = listOf(r1)))
    assertEquals(listOf(30.0), first.saleReturns.map { it.amount })
    val r2 = RefundEventDto("r2", 1755, refundAt + 10, "ledger")
    val second = ok(OrderFulfillment.refund(first, order(events = listOf(r1, r2)), "", day)).data
    assertEquals(listOf("o-01ORDER-rf-r1", "o-01ORDER-rf-r2"), second.saleReturns.map { it.docId })
    assertEquals(listOf(30.0, 17.55), second.saleReturns.map { it.amount })
    assertEquals(47.55, ReportEngine.sales(second, refundDay, refundDay).returnAmount, 1e-9)
    assertTrue(OrderFulfillment.refund(second, order(events = listOf(r1, r2)), "", day) is OrderFulfillment.Result.Already)
  }

  @Test
  fun `پولِ آنلاین به همان روش برمی‌گردد و دخل دست نمی‌خورد اما نقدِ پیشخوان و پیک از دخل`() {
    val online = refunded(order(paymentMethod = "online", paymentStatus = "paid", events = listOf(full(source = "gateway"))))
    assertEquals(mapOf("online" to 175.5), online.saleReturns.single().toMethods)
    assertEquals(0.0, drawer(online).returns, 1e-9)
    assertEquals(0.0, ReturnFlow.drawerAmount(online.saleReturns.single()), 1e-9)

    val counter = refunded(order(events = listOf(full())))
    assertNull(counter.saleReturns.single().toMethods)
    assertEquals(175.5, drawer(counter).returns, 1e-9)

    val courier = refunded(order(fulfillment = "delivery", firstStatus = "out_for_delivery", events = listOf(full())))
    assertEquals("cod", courier.sales.single().paymentMethod)
    assertEquals(175.5, drawer(courier).returns, 1e-9)
  }

  @Test
  fun `گزارش بازپرداخت را در روزِ خودش نشان می‌دهد نه روزِ فروش`() {
    val d = refunded(order(events = listOf(full())))
    val saleDayReport = ReportEngine.sales(d, saleDay, saleDay)
    assertEquals(1, saleDayReport.count)
    assertEquals(175.5, saleDayReport.net, 1e-9)
    assertEquals(0.0, saleDayReport.returnAmount, 1e-9)
    val refundReport = ReportEngine.sales(d, refundDay, refundDay)
    assertEquals(0, refundReport.count)
    assertEquals(175.5, refundReport.returnAmount, 1e-9)
    //  کالا پیشِ مشتری ماند ⇒ کلِ پولِ برگشته از سود کم می‌شود
    assertEquals(-175.5, refundReport.grossProfit, 1e-9)
    //  جمعِ دو روز = فروش منهای بازپرداخت
    assertEquals(0.0, ReportEngine.sales(d, saleDay, refundDay).net - ReportEngine.sales(d, saleDay, refundDay).returnAmount, 1e-9)
  }

  @Test
  fun `کالا فقط برای قلمِ زده‌شده به انبار برمی‌گردد و پیش از پاسخ می‌پرسد و دو بار نه`() {
    val o = order(events = listOf(full()))
    val d = refunded(o)
    assertEquals(listOf("full"), OrderFulfillment.restockPending(d, o.id))
    assertEquals(listOf("o-01ORDER"), OrderFulfillment.restockPendingSales(d).map { it.id })
    //  قلمِ آزاد (کیسه) پرسیده نمی‌شود
    assertEquals(listOf(1), OrderFulfillment.restockChoices(d, o.id).map { it.first })

    //  «هیچ» ⇒ موجودی همان، پرسش تمام
    val none = ok(OrderFulfillment.restock(d, o.id, o.orderNo, emptySet(), "علی", refundDay, refundAt + 5)).data
    assertEquals(8.5, ShopStore.stock(none, "p1"), 1e-9)
    assertTrue(OrderFulfillment.restockPending(none, o.id).isEmpty())
    assertTrue(none.auditLog.any { it.id == "o-01ORDER-rs-full" })
    assertTrue(OrderFulfillment.restockPendingSales(none).isEmpty())

    //  قلمِ ۱ زده شد ⇒ ۱٫۵ کیلو برمی‌گردد، با شناسه‌های ثابت
    val back = ok(OrderFulfillment.restock(d, o.id, o.orderNo, setOf(1, 2), "علی", refundDay, refundAt + 5)).data
    assertEquals(10.0, ShopStore.stock(back, "p1"), 1e-9)
    assertEquals(1.5, back.warehouseEntries.single { it.id == "o-01ORDER-1-rs" }.units, 1e-9)
    assertTrue(back.stockMovements.any { it.id == "o-01ORDER-1-rsm" && it.type == "customer_return" })
    val row = back.saleReturns.single { it.id == "o-01ORDER-1-rsr" }
    assertEquals(0.0, row.amount, 1e-9)
    assertEquals(ReturnFlow.RESTOCK, row.disposition)
    assertEquals(0.0, back.saleItems.single { it.id == "o-01ORDER-1" }.returnedQty, 1e-9)
    //  بهای کالای برگشته همان روز به سود برمی‌گردد
    assertEquals(-175.5 + 135.0, ReportEngine.sales(back, refundDay, refundDay).grossProfit, 1e-9)
    //  تکرار ⇒ هیچ؛ دو دستگاه ⇒ یک بار
    assertTrue(OrderFulfillment.restock(back, o.id, o.orderNo, setOf(1), "", refundDay, refundAt + 9) is OrderFulfillment.Result.Already)
    val other = ok(OrderFulfillment.restock(d, o.id, o.orderNo, setOf(1), "", refundDay, refundAt + 5)).data
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(d), LedgerDiff.toJson(other))
    val merged = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(back), ops))
    assertEquals(10.0, ShopStore.stock(merged, "p1"), 1e-9)
    //  برگشتِ دستیِ بعدی همان قلم را دوباره برنمی‌گرداند
    val item = back.saleItems.single { it.id == "o-01ORDER-1" }
    assertEquals(1.5, OrderFulfillment.restockedQty(back, item.id), 1e-9)
    val quote = ReturnFlow.quote(back, "o-01ORDER", mapOf(item.id to ReturnFlow.Pick(1.0)))
    assertTrue("$quote", quote is ReturnFlow.Outcome.Bad && quote.code == ReturnFlow.Code.QTY_EXCEEDS)
  }

  @Test
  fun `بازپرداختِ درگاه و گذارِ کامل یک کلیدند و مرجوعیِ دستیِ پیشین کم می‌شود`() {
    val o = order(events = listOf(full(source = "gateway")))
    var seq = 0
    val manual = (SalesEngine.recordReturn(sold(o), "o-01ORDER", mapOf("o-01ORDER-1" to 0.5), "خراب", saleDay, 6_000L) { "m${seq++}" }
      as SalesEngine.Result.Ok).data
    val manualAmount = ReturnFlow.returnedAmount(manual, "o-01ORDER")
    assertTrue(manualAmount > 0)
    val d = ok(OrderFulfillment.refund(manual, o, "", day)).data
    assertEquals(175.5, ReturnFlow.returnedAmount(d, "o-01ORDER"), 1e-9)
    assertEquals(175.5 - manualAmount, d.saleReturns.single { it.docId == "o-01ORDER-rf-full" }.amount, 1e-9)
    //  دوباره همان کلید از منبعِ دیگر ⇒ هیچ
    assertTrue(OrderFulfillment.refund(d, order(events = listOf(full(source = "status"))), "", day) is OrderFulfillment.Result.Already)
    //  ردیفِ دفترِ دیرتر که چیزی برایش نمانده ⇒ فقط سابقه، بی سند
    val late = ok(OrderFulfillment.refund(d, order(events = listOf(full(), RefundEventDto("r1", 2000, refundAt + 50))), "", day)).data
    assertTrue(late.saleReturns.none { it.docId == "o-01ORDER-rf-r1" })
    assertTrue(late.auditLog.any { it.id == "o-01ORDER-rf-r1-a" })
  }

  @Test
  fun `بی فروشِ تحویل یا با فروشِ لغوشده هیچ سندی ساخته نمی‌شود`() {
    val o = order(events = listOf(full()))
    assertTrue(OrderFulfillment.refund(ledger(), o, "", day) is OrderFulfillment.Result.Failed)
    val d = sold(o)
    val cancelled = d.copy(sales = d.sales.map { it.copy(status = "cancelled") })
    assertTrue(OrderFulfillment.refund(cancelled, o, "", day) is OrderFulfillment.Result.Already)
    assertTrue(OrderFulfillment.restockPending(cancelled, o.id).isEmpty())
  }

  @Test
  fun `اجازه از پرچمِ سرور و بی نوشتنِ فروش هیچ`() {
    val perms = OrderPermsDto(recordSale = true)
    assertTrue(OrderFulfillment.refundAllowed(order(), perms))
    assertFalse(OrderFulfillment.refundAllowed(order().copy(refundable = false), perms))
    assertTrue(OrderFulfillment.refundAllowed(order().copy(refundable = true), perms))
    assertFalse(OrderFulfillment.refundAllowed(order().copy(refundable = true), OrderPermsDto()))
  }
}
