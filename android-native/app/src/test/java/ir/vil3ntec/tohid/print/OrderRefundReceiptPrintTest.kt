package ir.vil3ntec.tohid.print

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrRefundSlip
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.orders.OrderRefundSlip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  E2E-RET-11 — `order.refund.receipts` ِ `GET /api/shop/orders/:id` (همان شکلِ `receiptsOf` ِ سرور، با دلیل و کننده)
 *  بی کم و زیاد به مدلِ کاغذ می‌رسد و کاغذ همان عددها را می‌گوید؛ سرورِ پیشین یا پرچمِ خاموش (بی کلید) ⇒ هیچ رسیدی.
 */
class OrderRefundReceiptPrintTest {

  private val withReceipts = """
    {"id":"o1","orderNo":71,"totalMinor":125000,"refund":{"totalMinor":125000,"refundedMinor":50000,"remainingMinor":75000,
     "currency":"AFN","entries":[{"seq":1}],
     "receipts":[
      {"no":"71-R1","seq":1,"at":1000,"orderNo":71,"currency":"AFN","method":"cash","final":false,"totalMinor":125000,
       "refundedBeforeMinor":0,"amountMinor":30000,"refundedAfterMinor":30000,"netMinor":95000,"reason":"کمبود","actorId":"u1"},
      {"no":"71-R2","seq":2,"at":2000,"orderNo":71,"currency":"AFN","method":"hesabpay","final":true,"totalMinor":125000,
       "refundedBeforeMinor":30000,"amountMinor":20000,"refundedAfterMinor":50000,"netMinor":75000,"reason":"","actorId":"u1"}]}}
  """.trimIndent()

  private fun parse(json: String) = ApiJson.format.decodeFromString(OrderDto.serializer(), json)

  @Test fun serverReceiptsReachThePaperModelUnchanged() {
    val r = parse(withReceipts).refund!!.receipts
    assertEquals(listOf("71-R1", "71-R2"), r.map { it.no })
    assertEquals(
      OrderRefundSlip.Receipt("71-R2", 71, 2000, "hesabpay", true, 125_000, 30_000, 20_000, 50_000, 75_000, ""),
      OrderSlipPrint.refundOf(r[1]),
    )
    assertTrue(r.all { OrderRefundSlip.consistent(OrderSlipPrint.refundOf(it)) })
  }

  @Test fun paperSaysTheServerAmountsInTheAppLanguage() {
    I18n.source = { Lang.FA }
    val r = parse(withReceipts).refund!!.receipts[1]
    val rows = OrderRefundSlip.rows(OrderSlipPrint.refundOf(r), StrRefundSlip, 3000, { "t" })
    assertNotNull(rows)
    val text = rows!!.map { it.text }
    assertTrue(text.contains(StrRefundSlip.title("71-R2")))
    assertTrue(text.contains(StrRefundSlip.thisRefund("200")))
    assertTrue(text.contains(StrRefundSlip.refundedBefore("300")))
    assertTrue(text.contains(StrRefundSlip.net("750")))
    assertTrue(text.contains(StrRefundSlip.hesabpay))
    assertTrue(text.contains(StrRefundSlip.closed))
  }

  @Test fun flagOffOrOlderServerMeansNoReceipts() {
    val off = parse("""{"id":"o1","orderNo":71,"refund":{"totalMinor":125000,"refundedMinor":0,"remainingMinor":125000}}""")
    assertEquals(0, off.refund!!.receipts.size)
    assertNull(parse("""{"id":"o1","orderNo":71}""").refund)
  }
}
