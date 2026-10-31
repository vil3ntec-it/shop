package ir.vil3ntec.tohid.core.gateway

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** حلقهٔ انتظارِ بلند: پایان با وضعیتِ پایانی، پسروی در بی‌نتی، ساعتِ سرور داور */
class IntentPollerTest {

  private class Clock(var t: Long = 0)

  private fun poller(t: FakeTransport, clock: Clock, slept: MutableList<Long>) =
    IntentPoller(HesabPayClient(t), now = { clock.t }, sleep = { ms -> slept += ms; clock.t += ms })

  private val start = PaymentIntent.fromMap(intentJson())!!

  @Test
  fun stopsOnPaidAndReportsUpdates() {
    val clock = Clock()
    val slept = ArrayList<Long>()
    val t = FakeTransport(ok(intentJson()), ok(intentJson(status = "paid")))
    //  هر خواندن ۲۰ ثانیه نگه داشته می‌شود (انتظارِ بلند)
    t.onCall = { clock.t += 20_000 }
    val events = ArrayList<IntentPoller.Event>()
    val end = runSync { poller(t, clock, slept).run(start) { events += it } }
    assertEquals(IntentStatus.PAID, (end as IntentPoller.End.Final).intent.status)
    assertEquals(listOf("GET /shop/payments/intents/pi_1?wait=20", "GET /shop/payments/intents/pi_1?wait=20"), t.calls)
    assertTrue(slept.isEmpty())
    assertEquals(1, events.size)
  }

  @Test
  fun eachTerminalStatusEndsTheLoop() {
    for (s in listOf("failed", "expired", "cancelled", "refunded")) {
      val end = runSync { poller(FakeTransport(ok(intentJson(status = s))), Clock(), ArrayList()).run(start) }
      assertEquals(s, (end as IntentPoller.End.Final).intent.status.wire)
    }
  }

  @Test
  fun alreadyTerminalIntentDoesNotCallServer() {
    val t = FakeTransport()
    val paid = PaymentIntent.fromMap(intentJson(status = "paid"))!!
    assertIs<IntentPoller.End.Final>(runSync { poller(t, Clock(), ArrayList()).run(paid) })
    assertTrue(t.calls.isEmpty())
  }

  @Test
  fun offlineBacksOffAndResetsAfterSuccess() {
    val clock = Clock()
    val slept = ArrayList<Long>()
    val t = FakeTransport(
      GatewayReply(0), GatewayReply(0), GatewayReply(503), ok(intentJson()), GatewayReply(0), ok(intentJson(status = "paid")),
    )
    val events = ArrayList<IntentPoller.Event>()
    val end = runSync { poller(t, clock, slept).run(start) { events += it } }
    assertIs<IntentPoller.End.Final>(end)
    //  ۱، ۲، ۴ ثانیه؛ پاسخِ زودِ «هنوز در انتظار» ⇐ دست‌کم ۲ ثانیه؛ پس از موفقیت دوباره از ۱
    assertEquals(listOf(1_000L, 2_000L, 4_000L, IntentPoller.MIN_ROUND_MS, 1_000L), slept)
    //  پس از گرفتاری، خواندنِ درستِ بی‌تغییر هم «Update» می‌دهد (صفحه «در حالِ اتصال» را برمی‌دارد)
    assertEquals(
      listOf("T", "T", "T", "U", "T", "U"),
      events.map { if (it is IntentPoller.Event.Trouble) "T" else "U" },
    )
  }

  @Test
  fun backoffIsCapped() {
    val b = IntentPoller.Backoff()
    assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 15_000L), List(6) { b.next() })
  }

  @Test
  fun serverClockDecidesExpiryNotDeviceClock() {
    //  ساعتِ دستگاه از مهلت گذشته ولی سرور هنوز «در انتظار» می‌گوید ⇐ ادامه، تا خودِ سرور expired کند
    val clock = Clock(t = 1_000_000L + 60_000)
    val t = FakeTransport(ok(intentJson()), ok(intentJson()), ok(intentJson(status = "expired")))
    t.onCall = { clock.t += 20_000 }
    val end = runSync { poller(t, clock, ArrayList()).run(start) }
    assertEquals(IntentStatus.EXPIRED, (end as IntentPoller.End.Final).intent.status)
    assertEquals(3, t.calls.size)
  }

  @Test
  fun offlinePastExpiryGivesUpAsUnknown() {
    val clock = Clock(t = 1_000_000L + IntentPoller.OFFLINE_GIVE_UP_MS - 500)
    val t = FakeTransport(GatewayReply(0), GatewayReply(0), GatewayReply(0))
    val end = runSync { poller(t, clock, ArrayList()).run(start) }
    //  پرداخت نامعلوم ⇐ نه «پرداخت شد»، نه «ناموفق»
    assertEquals(IntentStatus.PENDING, (end as IntentPoller.End.TimedOut).last.status)
  }

  @Test
  fun unknownIntentIsLost() {
    val end = runSync { poller(FakeTransport(GatewayReply(404, code = "payment_not_found")), Clock(), ArrayList()).run(start) }
    assertIs<IntentPoller.End.Lost>(end)
  }
}
