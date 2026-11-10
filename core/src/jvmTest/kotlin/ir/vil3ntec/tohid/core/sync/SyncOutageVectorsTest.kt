package ir.vil3ntec.tohid.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  `SyncOutage` = `outageOf` / `outageStreak` / `outageFrom` / `dotOf` ِ وب و `SyncReconnect` = `reconnectDelayMs` /
 *  `reconnectHold` ِ وب: همان `core/test-vectors/sync-outage-v1.json` که `server/test/sync-outage.test.js` هم می‌خواند
 *  (SYN-12). گردشِ کامل روی JVM و شبیه‌سازِ iOS: `commonTest/…/SyncOutageTest.kt` و `SyncReconnectTest.kt`.
 */
class SyncOutageVectorsTest {
  private val text = SyncVectors.text("sync-outage-v1.json")

  private fun rows(section: String) = SyncVectors.rows(SyncVectors.section(text, section))

  private fun Map<String, String>.bool(k: String): Boolean = getValue(k) == "true"
  private fun Map<String, String>.int(k: String): Int = getValue(k).toInt()
  private fun Map<String, String>.long(k: String): Long = getValue(k).toLong()

  private fun show(v: SyncOutage.Verdict) = v.kind.id + "/" + v.level.id

  @Test
  fun sustainMatchesVectors() {
    assertEquals(SyncOutage.SUSTAIN_FAILURES, Regex("\"sustainFailures\"\\s*:\\s*(\\d+)").find(text)!!.groupValues[1].toInt())
    assertEquals(SyncOutage.SUSTAIN_MS, Regex("\"sustainMs\"\\s*:\\s*(\\d+)").find(text)!!.groupValues[1].toLong())
  }

  @Test
  fun classifyMatchesVectors() {
    val list = rows("classify")
    assertTrue(list.size >= 25)
    for (r in list) {
      val got = SyncOutage.classify(r.bool("online"), r.bool("failed"), r.int("status"), r.getValue("code"), r.int("failures"), r.long("streakMs"))
      assertEquals(r.getValue("kind") + "/" + r.getValue("level"), show(got), r.getValue("name"))
    }
  }

  @Test
  fun streakMatchesVectors() {
    val list = rows("streak")
    assertTrue(list.size >= 5)
    for (r in list) {
      val prev = if (r.long("prevSince") < 0) null else SyncOutage.Streak(0, "network", r.getValue("prevError"), r.long("prevSince"))
      val got = SyncOutage.next(prev, r.getValue("before"), r.int("status"), r.getValue("code"), r.getValue("error"), r.long("now"))
      assertEquals(SyncOutage.Streak(r.int("status"), r.getValue("code"), r.getValue("error"), r.long("since")), got, r.getValue("name"))
    }
  }

  @Test
  fun ofMatchesVectors() {
    val list = rows("of")
    assertTrue(list.size >= 9)
    for (r in list) {
      val s = if (r.long("since") < 0) null else SyncOutage.Streak(r.int("sStatus"), r.getValue("sCode"), r.getValue("sError"), r.long("since"))
      val got = SyncOutage.of(s, r.getValue("lastError"), r.bool("online"), r.int("failures"), r.long("now"))
      assertEquals(r.getValue("kind") + "/" + r.getValue("level"), show(got), r.getValue("name"))
    }
  }

  @Test
  fun dotMatchesVectors() {
    val list = rows("dot")
    assertTrue(list.size >= 10)
    for (r in list) {
      val got = SyncOutage.dot(r.bool("error"), r.bool("alarm"), r.bool("online"), r.bool("signedIn"), r.bool("configured"), r.int("queued"), r.bool("busy"))
      assertEquals(r.getValue("dot"), got.id, r.getValue("name"))
    }
  }

  /** `random` ِ رشته‌ای ("NaN"، "Infinity") همان `Number(…)` ِ JS است */
  private fun Map<String, String>.rnd(): Double = getValue("random").toDouble()

  @Test
  fun reconnectSpreadMatchesVectors() {
    assertEquals(SyncReconnect.SPREAD_MS, Regex("\"spreadMs\"\\s*:\\s*(\\d+)").find(text)!!.groupValues[1].toLong())
    val list = rows("reconnectDelay")
    assertTrue(list.size >= 10)
    for (r in list) assertEquals(r.long("ms"), SyncReconnect.delayMs(r.rnd()), r.getValue("name"))
  }

  @Test
  fun reconnectHoldMatchesVectors() {
    val list = rows("reconnectHold")
    assertTrue(list.size >= 12)
    for (r in list) {
      val got = SyncReconnect.hold(r.long("until"), r.getValue("cause"), r.long("now"), r.rnd())
      assertEquals(SyncReconnect.Hold(r.long("untilOut"), r.long("wait")), got, r.getValue("name"))
    }
  }
}
