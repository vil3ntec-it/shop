package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPageDto
import ir.vil3ntec.tohid.core.model.OrderStockHoldDto
import ir.vil3ntec.tohid.core.model.OrderStockHoldLineDto
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.ui.theme.V3Tone
import kotlinx.serialization.decodeFromString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  O-06 = ب در برنامهٔ مدیریت: `stockHold` ِ جزئیات و `stockReview` ِ کارتِ صف (سرور: `stock-reserve.orderView` و
 *  `reviewIdsOf`) با همان `ApiJson` ِ برنامه؛ نبودِ کلید (سرورِ پیشین / پرچمِ خاموش) ⇒ هیچ نشان و هیچ پیام.
 */
class OrderStockHoldTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  private fun parse(json: String): OrderDto = ApiJson.format.decodeFromString<OrderDto>(json)
  private val stamp: (Long) -> String = { "ساعت-$it" }
  private fun hold(status: String, review: Boolean = false, expiresAt: Long? = null, orderStatus: String = "new") =
    OrderDto(id = "o", orderNo = 7, status = orderStatus, stockHold = OrderStockHoldDto(status, review, expiresAt))

  @Test
  fun `سرورِ پیشین یا پرچمِ خاموش — بی کلید یا null ⇒ بی نشان و بی پیام`() {
    for (json in listOf("""{"id":"o1","status":"new"}""", """{"id":"o1","status":"new","stockHold":null,"stockReview":null}""")) {
      val o = parse(json)
      assertNull(json, o.stockHold)
      assertFalse(json, o.stockReview)
      assertFalse(json, OrderStockHold.review(o))
      assertNull(json, OrderStockHold.note(o, stamp))
    }
  }

  @Test
  fun `کارتِ صف — stockReview از فهرست ⇒ نشان، بی پیامِ جزئیات؛ کارتِ دیگرِ همان صفحه بی نشان`() {
    val page = ApiJson.format.decodeFromString<OrderPageDto>(
      """{"tab":"new","items":[{"id":"a","status":"new","stockReview":true,"itemsPreview":["برنج"]},{"id":"b","status":"new"}],"nextCursor":null}""",
    )
    assertEquals(listOf(true, false), page.items.map(OrderStockHold::review))
    assertNull("کارت stockHold ندارد", OrderStockHold.note(page.items[0], stamp))
  }

  @Test
  fun `جزئیات — شکلِ سرور با کلیدِ ناشناخته و expiresAt ِ null خوانده می‌شود`() {
    val o = parse(
      """{"id":"o","status":"accepted","stockHold":{"status":"held","review":true,"expiresAt":null,"future":1,
        "lines":[{"lineNo":1,"status":"held","short":true},{"lineNo":2,"status":"held","short":false,"x":"y"}]},"unknown":{"a":1}}""",
    )
    assertEquals(
      OrderStockHoldDto("held", true, null, listOf(OrderStockHoldLineDto(1, "held", true), OrderStockHoldLineDto(2, "held", false))),
      o.stockHold,
    )
    assertTrue(OrderStockHold.review(o))
    val withExpiry = parse("""{"id":"o","status":"new","stockHold":{"status":"held","expiresAt":1700000000000}}""")
    assertEquals(1_700_000_000_000L, withExpiry.stockHold?.expiresAt)
    assertFalse(withExpiry.stockHold!!.review)
    assertTrue(withExpiry.stockHold!!.lines.isEmpty())
  }

  @Test
  fun `نشانِ «بررسی کنید» — هشدار با همان متنِ کارت، پیش از هر حالِ دیگر`() {
    val n = OrderStockHold.note(hold("held", review = true, expiresAt = 5L), stamp)!!
    assertEquals(V3Tone.WARNING, n.tone)
    assertEquals("موجودیِ رزروشده کم آمد — بررسی کنید", n.title)
    assertEquals(StrOrdersHub.stockReview, n.title)
    assertEquals(StrOrdersHub.stockReviewBody, n.body)
    assertTrue(OrderStockHold.review(hold("held", review = true)))
    assertFalse(OrderStockHold.review(hold("held")))
  }

  @Test
  fun `حالِ نگه‌داشتن — مهلت‌دار، بی‌مهلت، گذشته، انجام‌شده، آزاد؛ حالِ ناشناخته ⇒ هیچ`() {
    val timed = OrderStockHold.note(hold("held", expiresAt = 42L), stamp)!!
    assertEquals(V3Tone.INFO, timed.tone)
    assertEquals("کالای این سفارش تا ساعت-42 نگه داشته شده است", timed.title)
    assertEquals("سفارشِ تازه: «قبول نکنید ⇒ آزاد»", StrOrdersHub.stockHeldAcceptHint, timed.body)
    //  پس از تحویل مهلتِ پشتیبان دارد ولی «قبول» دیگر معنا ندارد
    assertNull(OrderStockHold.note(hold("held", expiresAt = 42L, orderStatus = "picked_up"), stamp)!!.body)
    assertEquals(StrOrdersHub.stockHeldOpen, OrderStockHold.note(hold("held", orderStatus = "accepted"), stamp)!!.title)

    val lapsed = OrderStockHold.note(hold("expired"), stamp)!!
    assertEquals(V3Tone.WARNING, lapsed.tone)
    assertEquals(StrOrdersHub.stockExpired, lapsed.title)
    assertEquals(StrOrdersHub.stockExpiredHint, lapsed.body)
    val lapsedDone = OrderStockHold.note(hold("expired", orderStatus = "rejected"), stamp)!!
    assertEquals(V3Tone.INFO, lapsedDone.tone)
    assertNull(lapsedDone.body)

    assertEquals(V3Tone.SUCCESS, OrderStockHold.note(hold("committed", orderStatus = "picked_up"), stamp)!!.tone)
    assertEquals(StrOrdersHub.stockReleased, OrderStockHold.note(hold("released", orderStatus = "rejected"), stamp)!!.title)
    assertNull("حالِ تازهٔ سرورِ آینده ⇒ بی پیام", OrderStockHold.note(hold("frozen"), stamp))
    assertNull(OrderStockHold.note(hold(""), stamp))
  }

  @Test
  fun `سه زبان و دری — زمان دست نمی‌خورد`() {
    I18n.source = { Lang.EN }
    assertEquals("Reserved stock ran short — review", StrOrdersHub.stockReview)
    assertEquals("Stock is held for this order until ساعت-9", OrderStockHold.note(hold("held", expiresAt = 9L), stamp)!!.title)
    I18n.source = { Lang.PS }
    assertTrue(StrOrdersHub.stockReview.isNotBlank() && StrOrdersHub.stockReview != "موجودیِ رزروشده کم آمد — بررسی کنید")
    I18n.source = { Lang.PRS }
    assertTrue(OrderStockHold.note(hold("held", expiresAt = 9L), stamp)!!.title.contains("ساعت-9"))
  }
}
