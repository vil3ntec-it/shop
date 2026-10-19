package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.EventResult
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.QueueState
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
}
