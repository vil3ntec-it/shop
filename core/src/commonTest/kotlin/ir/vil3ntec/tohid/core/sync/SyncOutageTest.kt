package ir.vil3ntec.tohid.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** گردشِ `SyncOutage` روی هر هدف (JVM و شبیه‌سازِ iOS)؛ بردارهای مشترک با وب: `jvmTest/…/SyncOutageVectorsTest.kt` */
class SyncOutageTest {

  /** قطعیِ سراسری: کوتاه ⇒ بی هشدار، ماندگار ⇒ اعلان، سرور پاسخ داد ⇒ «سرور»، دورِ موفق ⇒ از نو */
  @Test
  fun outageLifecycleNeverAlarms() {
    val net = "به سرور نرسیدیم"
    var streak: SyncOutage.Streak? = null
    var lastError = ""
    var failures = 0
    fun fail(status: Int, code: String, error: String, now: Long) {
      streak = SyncOutage.next(streak, lastError, status, code, error, now)
      lastError = error
      failures++
    }
    fun at(now: Long, online: Boolean = true) = SyncOutage.of(streak, lastError, online, failures, now)

    assertEquals(SyncOutage.Kind.OK, at(0).kind)
    fail(SyncOutage.NO_RESPONSE, "network", net, 1_000)
    assertEquals(SyncOutage.Kind.RETRYING, at(1_000).kind)
    assertFalse(at(1_000).alarm)
    fail(SyncOutage.NO_RESPONSE, "network", net, 3_000)
    assertEquals(SyncOutage.Kind.RETRYING, at(60_999).kind, "دو شکست ولی هنوز زیرِ یک دقیقه")
    val down = at(61_000)
    assertEquals(SyncOutage.Kind.UNREACHABLE, down.kind)
    assertEquals(SyncOutage.Level.NOTICE, down.level)
    assertFalse(down.alarm)
    assertEquals(SyncOutage.Kind.OFFLINE, at(61_000, online = false).kind)

    fail(503, "db_busy", "خطای سرور (503)", 70_000)
    assertEquals(SyncOutage.Kind.SERVER, at(70_000).kind, "همان رشته ادامه دارد")
    assertEquals(SyncOutage.Dot.YELLOW, SyncOutage.dot(true, at(70_000).alarm, true, true, true, 0, false), "هرگز سبز، هرگز سرخ")

    //  دورِ موفق
    lastError = ""; failures = 0
    assertEquals(SyncOutage.Kind.OK, at(80_000).kind)
    fail(SyncOutage.NO_RESPONSE, "network", net, 500_000)
    assertEquals(SyncOutage.Kind.RETRYING, at(500_000).kind, "رشتهٔ تازه")
  }

  /** نشست و خطای واقعی همان دم سرخ‌اند، حتی بی‌نت؛ رشتهٔ ناهم‌خطا ⇒ ناشناخته ⇒ سرخِ پیشین */
  @Test
  fun authAndRealErrorsStillAlarm() {
    assertTrue(SyncOutage.classify(false, true, 401, "session_required", 0, 0).alarm)
    assertEquals(SyncOutage.Kind.AUTH, SyncOutage.classify(true, true, SyncOutage.NO_RESPONSE, "session_expired", 1, 0).kind)
    assertEquals(SyncOutage.Kind.ERROR, SyncOutage.classify(true, true, 400, "batch_too_large", 1, 0).kind)
    assertEquals(SyncOutage.Kind.ERROR, SyncOutage.classify(false, true, SyncOutage.LOCAL, "", 1, 0).kind)
    val other = SyncOutage.Streak(SyncOutage.NO_RESPONSE, "network", "به سرور نرسیدیم", 0)
    assertEquals(SyncOutage.Kind.ERROR, SyncOutage.of(other, "ژورنال نخواند", true, 5, 120_000).kind)
    assertEquals(SyncOutage.Dot.RED, SyncOutage.dot(true, true, false, true, true, 0, false))
  }
}
