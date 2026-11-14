package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrderLineDto
import ir.vil3ntec.tohid.core.orders.OrderReturnRequests
import ir.vil3ntec.tohid.core.returns.ReturnReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  RET-11 — درخواستِ برگشتِ ویترین ⇒ همان برگهٔ برگشتِ موجود (`ReturnFlow.post`) با اقلامِ پرشده: ردیفِ فروشِ همان سفارش،
 *  مقدار به واحدِ موجودی (کارتن ⇒ دانه)، هرگز بیش از مانده، سندِ ثابتِ `o-<سفارش>-rq-<درخواست>` (دوباره ⇒ «پیش‌تر ثبت شده»)،
 *  و پول/موجودی فقط از راهِ همان برگشت.
 */
class ReturnRequestPrefillTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", salePrice = 120.0, purchasePrice = 90.0)
  private val cola = Product(id = "p2", name = "نوشابه", unit = "دانه", saleUnit = "کارتن", unitFactor = 24.0, salePrice = 30.0, purchasePrice = 20.0)
  private val rid = "01ARZ3NDEKTSV4RRFFQ69G5FAV"

  private fun delivered(): ShopData {
    val ledger = ShopData(
      products = listOf(rice, cola),
      warehouseEntries = listOf(WarehouseEntry(id = "we1", productId = "p1", units = 10.0), WarehouseEntry(id = "we2", productId = "p2", units = 48.0)),
      nextInvoiceNo = 7,
    )
    val o = OrderDto(
      id = "01ORDER", orderNo = 12, status = "picked_up", fulfillment = "pickup", paymentMethod = "cod", paymentStatus = "paid",
      customer = OrderCustomerDto(name = "احمد"), subtotalMinor = 30000 + 72000, discountMinor = 0, deliveryFeeMinor = 0, totalMinor = 30000 + 72000,
      lines = listOf(
        OrderLineDto(lineNo = 1, productId = "p1", name = "برنج", unit = "کیلو", qtyMilli = 2500, unitPriceMinor = 12000, lineTotalMinor = 30000),
        OrderLineDto(lineNo = 2, productId = "p2", name = "نوشابه", unit = "کارتن", qtyMilli = 1000, unitPriceMinor = 72000, lineTotalMinor = 72000),
      ),
      events = listOf(OrderEventDto(kind = "status", fromStatus = "ready_for_pickup", toStatus = "picked_up", at = 5_000L)),
    )
    val r = OrderFulfillment.fulfill(ledger, o, "2026-11-10", 5_000L)
    assertTrue("$r", r is OrderFulfillment.Result.Ok)
    return (r as OrderFulfillment.Result.Ok).data
  }

  private fun request(vararg lines: Pair<Int, Long>, reason: String = "damaged") = OrderReturnRequests.Request(
    id = rid, orderId = "01ORDER", orderNo = 12, status = "accepted", reason = reason, text = "", method = "drop_off",
    lines = lines.map { (no, q) -> OrderReturnRequests.Line(no, "", "", if (no == 2) "کارتن" else "کیلو", q, 0) },
    photos = emptyList(), decisionNote = null, createdAt = 0, decidedAt = 1, completedAt = null, returnDocId = null,
  )

  @Test
  fun `قلم‌ها ردیفِ فروشِ همان سفارش می‌شوند و کارتن به دانه`() {
    val d = delivered()
    val p = ReturnRequestPrefill.of(d, request(1 to 1500L, 2 to 500L), ReturnReasons.DEFAULT)
    assertEquals("o-01ORDER-rq-$rid", p.docId)
    assertEquals(mapOf("o-01ORDER-1" to "1.5", "o-01ORDER-2" to "12"), p.qtys)
    assertEquals(ReturnReasons.DEFAULT[0], p.reason)
    assertNull("دلیلی که دکان ندارد پیشنهاد نمی‌شود", ReturnRequestPrefill.of(d, request(1 to 1L), listOf("فقط این")).reason)
  }

  @Test
  fun `بی فروشِ همان سفارش هیچ پیش‌پری نیست`() {
    val p = ReturnRequestPrefill.of(ShopData(products = listOf(rice)), request(1 to 1000L), ReturnReasons.DEFAULT)
    assertTrue(p.qtys.isEmpty())
    assertNull(ReturnRequestPrefill.saleOf(ShopData(), request(1 to 1000L)))
  }

  @Test
  fun `پذیرفتن از راهِ برگشتِ موجود — یک سند، موجودی برمی‌گردد، دوباره پیش‌تر ثبت شده`() {
    val d = delivered()
    val r = request(1 to 1000L)
    val p = ReturnRequestPrefill.of(d, r, ReturnReasons.DEFAULT)
    val sale = ReturnRequestPrefill.saleOf(d, r)!!
    val req = ReturnFlow.Request(
      docId = p.docId, saleId = sale.id, picks = p.qtys.mapValues { ReturnFlow.Pick(it.value.toDouble()) },
      reason = p.reason!!, reasons = ReturnReasons.DEFAULT, mode = ReturnFlow.CASH,
    )
    val before = ShopStore.stock(d, "p1")
    var n = 0
    val posted = ReturnFlow.post(d, req, "2026-11-11", 6_000L) { "row-${n++}" }
    assertTrue("$posted", posted is ReturnFlow.Outcome.Ok)
    val after = (posted as ReturnFlow.Outcome.Ok).value.data
    assertEquals(before + 1.0, ShopStore.stock(after, "p1"), 1e-9)
    assertEquals(listOf(p.docId), after.saleReturns.map { it.docId }.distinct())
    //  ماندهٔ همان ردیف کم شد ⇒ پیش‌پرِ درخواستِ بعدی هرگز بیش از مانده نیست
    assertEquals("1.5", ReturnRequestPrefill.of(after, request(1 to 9000L), ReturnReasons.DEFAULT).qtys["o-01ORDER-1"])
    val again = ReturnFlow.post(after, req, "2026-11-11", 7_000L) { "row-${n++}" }
    assertEquals(ReturnFlow.Code.ALREADY_POSTED, (again as ReturnFlow.Outcome.Bad).code)
  }

  @Test
  fun `نوشتهٔ مقدار با رقمِ لاتین و بی صفرِ پایانی`() {
    assertEquals("2", ReturnRequestPrefill.text(2.0))
    assertEquals("0.25", ReturnRequestPrefill.text(0.25))
    assertEquals("1.333", ReturnRequestPrefill.text(1.3333333))
  }
}
