package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.CourierSettlement
import ir.vil3ntec.tohid.core.courier.CourierSummary
import ir.vil3ntec.tohid.core.courier.EventResult
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.MissionStep
import ir.vil3ntec.tohid.core.courier.PeriodStats
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.core.courier.UnsettledDelivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** صفِ پیک روی گوشی پس از بسته شدنِ برنامه همان است — DLV-10 */
class CourierCodecTest {

  @Test fun `صف رفت و برگشت می‌کند، با کد، نقد و ردشده‌ها`() {
    var q = QueueState()
    q = CourierQueue.enqueue(q, "cev-aaaaaaaa-1", "A", "assigned", 1_000, from = "requested").first
    val (q2, d) = CourierQueue.enqueue(q, "cev-aaaaaaaa-2", "A", "delivered", 2_000, cashCollectedMinor = 1_200_000, cashReason = "partial_payment", note = "کمتر داد", code = "123456")
    q = q2
    val (q3, f) = CourierQueue.enqueue(q, "cev-aaaaaaaa-3", "B", "failed", 3_000, reason = "no_answer")
    q = CourierQueue.applyResults(q3, listOf(f), listOf(EventResult(f.eventId, "rejected", "status_changed", "عوض شده")))
    val back = CourierCodec.queue(CourierCodec.queueJson(q))
    assertEquals(q, back)
    assertEquals(d, back.pending[1])
    assertNull(back.pending[0].cashCollectedMinor)
  }

  @Test fun `صفِ خراب یا خالی ⇒ صفِ تازه؛ ترتیب هرگز پایین نمی‌آید`() {
    assertEquals(QueueState(), CourierCodec.queue(null))
    assertEquals(QueueState(), CourierCodec.queue("{not json"))
    val raw = """{"v":1,"nextSeq":1,"pending":[{"eventId":"cev-bbbbbbbb-9","assignmentId":"A","type":"assigned","deviceTime":5,"deviceSeq":41}],"rejected":[]}"""
    val q = CourierCodec.queue(raw)
    assertEquals(42L, q.nextSeq)
    val (_, e) = CourierQueue.enqueue(q, "cev-bbbbbbbb-10", "A", "picked_up", 6)
    assertEquals(42L, e.deviceSeq)
  }

  @Test fun `کارت رفت و برگشت می‌کند و پوچ‌ها پوچ می‌مانند`() {
    val c = MissionCard(
      id = "A", orderNo = 12, status = "moving", landmark = "مسجد", addressText = "کوچه", lat = 34.5, lng = 69.1,
      customerName = "احمد", phoneMasked = "0791•••567", itemsCount = 2, paymentMethod = "cod",
      expectedCollectMinor = 1_240_000, cashCollectedMinor = null, fareMinor = 5_000, requiresCode = true,
      photoAllowed = true, recallRequested = true, updatedAt = 99,
    )
    val back = CourierCodec.cards(CourierCodec.cardsJson(listOf(c, c.copy(id = "B", lat = null, lng = null, cashCollectedMinor = 7))))
    assertEquals(c, back[0])
    assertNull(back[1].lat)
    assertEquals(7L, back[1].cashCollectedMinor)
    assertTrue(CourierCodec.cards("[[[").isEmpty())
  }

  @Test fun `خطِ زمانی و «نقدِ من» بی اینترنت هم همان می‌مانند`() {
    val c = MissionCard(
      id = "A", orderNo = 12, status = "delivered",
      timeline = listOf(MissionStep("created", "requested", 1, "", "shop"), MissionStep("status", "delivered", 9, "", "courier")),
    )
    assertEquals(c, CourierCodec.cards(CourierCodec.cardsJson(listOf(c))).single())
    val s = CourierSummary(
      shopName = "دکان", shopPhone = "0700000000", cashInHandMinor = 98_000, liveExpectedMinor = 552_000,
      cashCapMinor = 1_000_000, headroomMinor = 350_000,
      unsettled = listOf(UnsettledDelivery(1038, 5, 98_000, 98_000, 5_000)), unsettledCount = 57,
      settlements = listOf(CourierSettlement(11, 7, "confirmed", 4, 920_000, 28_000, 920_000, 900_000, 20_000, 0, "کسری")),
      today = PeriodStats(1, 2, 1, 98_000, 11_000), week = PeriodStats(0, 14, 2, 3_120_000, 76_000), serverTime = 9,
    )
    assertEquals(s, CourierCodec.summary(CourierCodec.summaryJson(s)))
    //  هنوز نیامده یا خراب ⇒ null، نه صفرِ ساختگی
    assertNull(CourierCodec.summary(null))
    assertNull(CourierCodec.summary("{{"))
  }
}
