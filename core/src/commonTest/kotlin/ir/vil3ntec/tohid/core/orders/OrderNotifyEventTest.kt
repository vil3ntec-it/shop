package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** M6 × M5 (#429): برچسبِ اعلان بر اساسِ `event` ِ ردیفِ `/order-notify/pending` */
class OrderNotifyEventTest {

  @Test
  fun reviewIsLabelledInReviewNotNewOrder() {
    val review = OrderNotice("n1", "o1", 12, OrderNotifyAcks.EVENT_REVIEW)
    assertTrue(review.isReview)
    val (t, b) = OrderNotifyAcks.label(review)
    assertEquals("سفارش به بررسی رفت — #12", t)
    assertFalse(t.contains("تازه") || b.contains("تازه"))
    //  همان عنوانِ نگهبان برای یک سفارش
    assertEquals(StockHold.notice(listOf(StockHold.Ref("o1", 12)))!!.first, t)
  }

  @Test
  fun createdAndUnknownStayNewOrder() {
    assertEquals("سفارشِ تازه #7" to "برای دیدن و پذیرفتن لمس کنید",
      OrderNotifyAcks.label(OrderNotice("n1", "o1", 7)))
    //  سرورِ کهنه بی `event` یا رویدادِ ناشناخته ⇒ همان «تازه» ِ پیشین
    assertFalse(OrderNotice("n1", "o1", 7, "something_else").isReview)
    assertEquals("سفارشِ تازه", OrderNotifyAcks.label(OrderNotice("n1", "o1", 0, OrderNotifyAcks.EVENT_CREATED)).first)
    assertEquals("سفارش به بررسی رفت", OrderNotifyAcks.label(OrderNotice("n1", "o1", 0, OrderNotifyAcks.EVENT_REVIEW)).first)
  }

  @Test
  fun labelHasNoCustomerData() {
    for (e in listOf(OrderNotifyAcks.EVENT_CREATED, OrderNotifyAcks.EVENT_REVIEW)) {
      val (t, b) = OrderNotifyAcks.label(OrderNotice("n1", "ord_secret", 3, e))
      assertFalse((t + b).contains("ord_secret"))
    }
  }
}
