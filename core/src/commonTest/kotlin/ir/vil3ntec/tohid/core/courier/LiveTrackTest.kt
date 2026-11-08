package ir.vil3ntec.tohid.core.courier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  DLV-16 — قاعده‌های سرویسِ موقعیتِ پیک (`LiveTrack`): فقط «در راه» و فقط با خواستِ سرور، نه تندتر از سرور و باتری،
 *  فقط آخرین نقطهٔ تازه، و «دیگر نه» ِ سرور تا کارتِ تازه. همان `ON_THE_WAY` ِ `server/src/lib/delivery-live.js`.
 */
class LiveTrackTest {

  private val now = 1_800_000_000_000L
  private fun card(id: String, status: String, track: Boolean = true, min: Long = 5_000, v: Long = 1) =
    MissionCard(id, 1, status, trackLocation = track, trackMinIntervalMs = min, updatedAt = v)
  private val fix = LiveTrack.Fix(34.53, 69.17, 12, now - 2_000)

  @Test fun onlyOnTheWayAndOnlyWhenTheServerAsks() {
    assertEquals(setOf("picked_up", "moving"), LiveTrack.ON_THE_WAY)
    for (s in CourierFlow.STATUSES) {
      val t = LiveTrack.targets(listOf(card("a", s)), QueueState())
      assertEquals(s in LiveTrack.ON_THE_WAY, t.isNotEmpty(), "وضعیتِ $s")
    }
    //  بی `tracking.location` ِ سرور (پرچمِ خاموش) ⇒ هرگز، حتی «در راه»
    assertTrue(LiveTrack.targets(listOf(card("a", "moving", track = false)), QueueState()).isEmpty())
  }

  @Test fun theQueuedStepTheCourierSawDecides() {
    //  «رسیدم» ِ بی اینترنت همان دم می‌بندد، پیش از آن‌که سرور بداند
    val (q, _) = CourierQueue.enqueue(QueueState(), "cev-00000001", "a", "arrived", now, from = "moving")
    assertTrue(LiveTrack.targets(listOf(card("a", "moving")), q).isEmpty())
    //  «گرفتم» ِ نرفته باز نمی‌کند: سرور هنوز «قبول شد» می‌داند و `tracking.location` نداده
    val (q2, _) = CourierQueue.enqueue(QueueState(), "cev-00000002", "b", "picked_up", now, from = "assigned")
    assertTrue(LiveTrack.targets(listOf(card("b", "assigned", track = false)), q2).isEmpty())
  }

  @Test fun neverFasterThanTheServerOrTheBatteryBudget() {
    val t = LiveTrack.targets(listOf(card("a", "moving", min = 5_000), card("b", "picked_up", min = 120_000)), QueueState())
    assertEquals(listOf(LiveTrack.PERIOD_MS, 120_000L), t.map { it.intervalMs })
    val pacer = LiveTrack.Pacer()
    assertEquals(listOf("a", "b"), pacer.due(t, now).map { it.assignmentId })
    t.forEach { pacer.sent(it, now) }
    assertTrue(pacer.due(t, now + LiveTrack.PERIOD_MS - 1).isEmpty())
    assertEquals(listOf("a"), pacer.due(t, now + LiveTrack.PERIOD_MS).map { it.assignmentId })
    assertEquals(listOf("a", "b"), pacer.due(t, now + 120_000).map { it.assignmentId })
    //  ساعتِ گوشی عقب رفت ⇒ نوبت گم نمی‌شود
    assertEquals(2, pacer.due(t, now - 60_000).size)
  }

  @Test fun onlyAFreshValidPoint() {
    assertTrue(LiveTrack.usable(fix, now))
    assertFalse(LiveTrack.usable(null, now))
    assertFalse(LiveTrack.usable(fix.copy(at = now - LiveTrack.MAX_FIX_AGE_MS - 1), now), "نقطهٔ کهنه")
    assertTrue(LiveTrack.usable(fix.copy(at = now - LiveTrack.MAX_FIX_AGE_MS), now))
    assertFalse(LiveTrack.usable(fix.copy(at = now + LiveTrack.MAX_FIX_SKEW_MS + 1), now), "نقطهٔ آینده")
    assertFalse(LiveTrack.usable(fix.copy(lat = 91.0), now))
    assertFalse(LiveTrack.usable(fix.copy(lng = Double.NaN), now))
    assertFalse(LiveTrack.usable(fix.copy(lat = 0.0, lng = 0.0), now), "نقطهٔ پیش‌فرضِ خراب")
    assertEquals(13, LiveTrack.accuracyOf(12.6f))
    assertEquals(null, LiveTrack.accuracyOf(-1f))
    assertEquals(null, LiveTrack.accuracyOf(Float.NaN))
    assertEquals(100_000, LiveTrack.accuracyOf(5e6f))
  }

  @Test fun aStopFromTheServerHoldsUntilTheCardChanges() {
    val pacer = LiveTrack.Pacer()
    val v1 = LiveTrack.targets(listOf(card("a", "moving", v = 1)), QueueState())
    pacer.failed(v1[0], "offline")
    assertEquals(1, pacer.due(v1, now).size, "بی اینترنت ⇒ نوبتِ بعد دوباره")
    pacer.failed(v1[0], "not_on_the_way")
    assertTrue(pacer.live(v1).isEmpty())
    assertTrue(pacer.due(v1, now + 3_600_000).isEmpty(), "همان کارت ⇒ دیگر نه")
    val v2 = LiveTrack.targets(listOf(card("a", "moving", v = 2)), QueueState())
    assertEquals(1, pacer.due(v2, now).size, "کارتِ تازهٔ سرور ⇒ دوباره")
    for (code in listOf("assignment_not_found", "live_location_disabled", "courier_inactive", "session_expired", "courier_scope")) {
      assertTrue(code in LiveTrack.STOP_CODES, code)
    }
    assertFalse("timeout" in LiveTrack.STOP_CODES)
    assertFalse("rate_limited" in LiveTrack.STOP_CODES)
  }

  @Test fun aTickAsksForAPointOnlyWhenSomethingIsDueAndNeverQueues() {
    val pacer = LiveTrack.Pacer()
    val cards = listOf(card("a", "moving"), card("b", "picked_up"), card("c", "assigned"), card("d", "delivered"))
    var asked = 0
    val sent = ArrayList<String>()
    val live = LiveTrack.tick(cards, QueueState(), pacer, now, { asked++; fix }, { t, f -> sent += "${t.assignmentId}@${f.lat}"; LiveTrack.Sent(true) })
    assertEquals(2, live)
    assertEquals(1, asked, "یک نقطه برای همه")
    assertEquals(listOf("a@34.53", "b@34.53"), sent)
    //  نوبت نرسیده ⇒ نقطه پرسیده نمی‌شود
    LiveTrack.tick(cards, QueueState(), pacer, now + 1_000, { asked++; fix }, { _, _ -> error("نباید فرستاده شود") })
    assertEquals(1, asked)
    //  بی اینترنت ⇒ نقطه دور ریخته می‌شود؛ دورِ بعد نقطهٔ تازه، نه نقطهٔ کهنه
    val later = now + LiveTrack.PERIOD_MS
    val f2 = fix.copy(lat = 34.6, at = later)
    sent.clear()
    LiveTrack.tick(cards, QueueState(), pacer, later, { f2 }, { t, _ -> sent += t.assignmentId; LiveTrack.Sent(false, "offline") })
    assertEquals(listOf("a", "b"), sent)
    val f3 = fix.copy(lat = 34.7, at = later + 1_000)
    sent.clear()
    LiveTrack.tick(cards, QueueState(), pacer, later + 1_000, { f3 }, { t, f -> sent += "${t.assignmentId}@${f.lat}"; LiveTrack.Sent(true) })
    assertEquals(listOf("a@34.7", "b@34.7"), sent)
    //  نقطهٔ کهنه ⇒ هیچ فرستادنی
    LiveTrack.tick(cards, QueueState(), LiveTrack.Pacer(), now, { fix.copy(at = now - 120_000) }, { _, _ -> error("نقطهٔ کهنه رفت") })
  }

  @Test fun theLoopEndsWhenNothingIsLeft() {
    val pacer = LiveTrack.Pacer()
    val cards = listOf(card("a", "moving"), card("b", "moving"))
    val left = LiveTrack.tick(cards, QueueState(), pacer, now, { fix }) { t, _ ->
      if (t.assignmentId == "a") LiveTrack.Sent(false, "not_on_the_way") else LiveTrack.Sent(false, "live_location_disabled")
    }
    assertEquals(0, left, "هیچ هدفی ⇒ سرویس بسته شود")
    //  کارت رفت (پایان/پس‌گرفته) ⇒ صفر، بی پرسیدنِ نقطه
    assertEquals(0, LiveTrack.tick(emptyList(), QueueState(), pacer, now, { error("نقطه پرسیده شد") }) { _, _ -> error("فرستاده شد") })
  }
}
