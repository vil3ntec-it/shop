package ir.vil3ntec.tohid.core.orders

import ir.vil3ntec.tohid.core.orders.OrderNotifyAcks.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  رسیدِ «باز شد» ِ اعلانِ سفارش (M6) — همان قراردادِ `POST /api/shop/order-notify/:id/ack`.
 *  مرجعِ رفتارِ سرور: `server/test/order-notify.test.js` (ack ⇒ `acked` با `ack_source = 'device'`).
 */
class OrderNotifyAcksTest {

  private val n1 = "01JABCDEFGHJKMNPQRSTVWXYZ0"
  private val n2 = "01JABCDEFGHJKMNPQRSTVWXYZ1"
  private val o1 = "ord_123"

  @Test fun `لمس همان لحظه در صف می‌نشیند و تکرارش صف را دو تا نمی‌کند`() {
    var s = OrderNotifyAcks.tap(AckState(), n1, o1, now = 1_000)
    s = OrderNotifyAcks.tap(s, n1, o1, now = 2_000)
    assertEquals(1, s.pending.size)
    assertEquals(PendingAck(n1, o1, 1_000, 0, 1_000), s.pending.single())
    assertEquals(listOf(n1), s.shown)
    assertEquals(listOf(s.pending.single()), OrderNotifyAcks.due(s, now = 1_000))
  }

  @Test fun `شناسهٔ بدشکل به صف نمی‌رود`() {
    for (bad in listOf("", "a|b", "a\nb", "x".repeat(65), "../x")) {
      assertEquals(AckState(), OrderNotifyAcks.tap(AckState(), bad, o1, 0))
    }
    assertEquals(AckState(), OrderNotifyAcks.tap(AckState(), n1, "o|1", 0))
  }

  @Test fun `۲xx بیرون؛ ۴۰۴ و ۴۰۰ بیرون؛ بقیه با فاصلهٔ فزاینده می‌ماند`() {
    assertEquals(Outcome.ACKED, OrderNotifyAcks.outcomeOf(200))
    assertEquals(Outcome.GONE, OrderNotifyAcks.outcomeOf(404, "notification_not_found"))
    assertEquals(Outcome.GONE, OrderNotifyAcks.outcomeOf(400, "bad_ack_kind"))
    for (st in listOf(0, 401, 403, 429, 500, 503)) assertEquals("$st", Outcome.RETRY, OrderNotifyAcks.outcomeOf(st))

    val s0 = OrderNotifyAcks.tap(OrderNotifyAcks.tap(AckState(), n1, o1, 0), n2, o1, 0)
    assertTrue(OrderNotifyAcks.applyResult(s0, n1, Outcome.ACKED, 10).pending.none { it.notifId == n1 })
    assertTrue(OrderNotifyAcks.applyResult(s0, n1, Outcome.GONE, 10).pending.none { it.notifId == n1 })

    var s = OrderNotifyAcks.applyResult(s0, n1, Outcome.RETRY, now = 100)
    val r = s.pending.first { it.notifId == n1 }
    assertEquals(1, r.attempts)
    assertEquals(100 + OrderNotifyAcks.BASE_BACKOFF_MS, r.nextAt)
    assertEquals(listOf(n2), OrderNotifyAcks.due(s, now = 101).map { it.notifId })
    s = OrderNotifyAcks.applyResult(s, n1, Outcome.RETRY, now = 200)
    assertEquals(200 + 2 * OrderNotifyAcks.BASE_BACKOFF_MS, s.pending.first { it.notifId == n1 }.nextAt)
    assertEquals(OrderNotifyAcks.MAX_BACKOFF_MS, OrderNotifyAcks.backoffMs(50))
  }

  @Test fun `رسیدِ کهنه‌تر از یک هفته فرستاده نمی‌شود و بیرون می‌رود`() {
    val s = OrderNotifyAcks.tap(AckState(), n1, o1, now = 0)
    val late = OrderNotifyAcks.TTL_MS + 1
    assertTrue(OrderNotifyAcks.due(s, late).isEmpty())
    assertTrue(OrderNotifyAcks.expire(s, late).pending.isEmpty())
    assertEquals(s, OrderNotifyAcks.expire(s, OrderNotifyAcks.TTL_MS))
  }

  @Test fun `اعلانِ سرور فقط یک بار نشان داده می‌شود؛ لمس‌شده دیگر نه`() {
    val list = listOf(OrderNotice(n1, o1, 7), OrderNotice(n2, "ord_2", 8), OrderNotice(n2, "ord_2", 8), OrderNotice("a|b", o1, 9))
    val (s1, first) = OrderNotifyAcks.fresh(AckState(), list)
    assertEquals(listOf(n1, n2), first.map { it.id })
    val (_, again) = OrderNotifyAcks.fresh(s1, list)
    assertTrue(again.isEmpty())
    val tapped = OrderNotifyAcks.tap(AckState(), n1, o1, 0).copy(shown = emptyList())
    assertEquals(listOf(n2), OrderNotifyAcks.fresh(tapped, list).second.map { it.id })
  }

  @Test fun `ذخیره و بازخوانی؛ متنِ خراب صفِ خالی است`() {
    var s = OrderNotifyAcks.tap(AckState(), n1, o1, 5)
    s = OrderNotifyAcks.applyResult(s, n1, Outcome.RETRY, 9)
    s = OrderNotifyAcks.fresh(s, listOf(OrderNotice(n2, "ord_2", 3))).first
    assertEquals(s, OrderNotifyAcks.decode(OrderNotifyAcks.encode(s)))
    assertEquals(AckState(), OrderNotifyAcks.decode(null))
    assertEquals(AckState(), OrderNotifyAcks.decode("v9\na|$n1|$o1|1|0|1"))
    val messy = "v1\na|$n1|$o1|1|0|1\na|bad id|x|1|0|1\na|$n2|$o1|x|0|1\ns|$n2\ns|a b\n"
    val d = OrderNotifyAcks.decode(messy)
    assertEquals(listOf(n1), d.pending.map { it.notifId })
    assertEquals(listOf(n2), d.shown)
  }

  @Test fun `صف بی‌پایان رشد نمی‌کند`() {
    var s = AckState()
    for (i in 0 until OrderNotifyAcks.MAX_PENDING + 5) s = OrderNotifyAcks.tap(s, "n$i", o1, i.toLong())
    assertEquals(OrderNotifyAcks.MAX_PENDING, s.pending.size)
    assertEquals("n5", s.pending.first().notifId)
    val batch = OrderNotifyAcks.due(s, now = 1_000)
    assertEquals(OrderNotifyAcks.MAX_BATCH, batch.size)
    assertEquals("n5", batch.first().notifId)
  }
}
