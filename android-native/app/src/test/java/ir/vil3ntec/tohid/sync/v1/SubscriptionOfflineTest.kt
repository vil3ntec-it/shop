package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.account.SubscriptionClock
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.sync.License
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 *  خواستِ صاحب‌کار ۱۴۰۵/۰۸/۲۲: «اشتراک تکون نمی‌خورد و بی اینترنت تا ابد کار می‌کند».
 *   • تپشِ کش‌شده با ساعتِ مطمئن: روزِ مانده کم می‌شود و پس از پایان «فعال» نیست؛
 *   • قفلِ نرم بی تپشِ تازه هم بسته می‌شود: پایانِ کش‌شده گذشت ⇒ ENDED، مجوزِ امضاشده تمام شد ⇒ RENEW؛
 *   • مجوزِ فعال یا در مهلت (یا کدِ آفلاین) ⇒ باز؛ دستگاهِ بی حساب (NONE) همان امروز.
 */
class SubscriptionOfflineTest {
  private val day = SubscriptionClock.DAY
  private val t0 = 1_800_000_000_000L
  private fun beat(ends: Long, active: Boolean = true, status: String = "active", total: Int = 30): JsonObject =
    Json.parseToJsonElement(
      """{"ok":true,"subscription":{"plan":"m1","status":"$status","active":$active,"permanent":false,"startsAt":${ends - total * day},
         "endsAt":$ends,"graceEndsAt":0,"daysLeft":25,"totalDays":$total,"progress":83,"color":"yellow"}}""",
    ).jsonObject

  private fun sub(o: JsonObject) = o["subscription"]!!.jsonObject

  @Test fun daysLeftCountsDownFromTheCachedEnd() {
    val b = beat(t0 + 25 * day)
    assertEquals(25, ApiJson.int(sub(HeartbeatCache.withClock(b, t0)), "daysLeft"))
    val later = sub(HeartbeatCache.withClock(b, t0 + 10 * day))
    assertEquals("ده روز بی اینترنت ⇒ ۱۵ روز، نه همان ۲۵", 15, ApiJson.int(later, "daysLeft"))
    assertEquals(true, ApiJson.bool(later, "active"))
    assertEquals(50, ApiJson.int(later, "progress"))
    val over = sub(HeartbeatCache.withClock(b, t0 + 26 * day))
    assertEquals(0, ApiJson.int(over, "daysLeft"))
    assertEquals("پایان گذشت ⇒ دیگر فعال نیست", false, ApiJson.bool(over, "active"))
    assertEquals("red", ApiJson.text(over, "color"))
    assertEquals("بقیهٔ بدنه دست‌نخورده", true, ApiJson.bool(HeartbeatCache.withClock(b, t0 + 26 * day), "ok"))
  }

  @Test fun permanentAndUnknownEndAreUntouched() {
    val perm = Json.parseToJsonElement("""{"subscription":{"permanent":true,"active":true,"endsAt":1,"daysLeft":0}}""").jsonObject
    assertSame(perm, HeartbeatCache.withClock(perm, t0))
    val trial = Json.parseToJsonElement("""{"subscription":{"status":"trial","active":true,"endsAt":0,"daysLeft":5}}""").jsonObject
    assertSame(trial, HeartbeatCache.withClock(trial, t0 + 99 * day))
  }

  @Test fun softLockDecisionWithoutAFreshHeartbeat() {
    assertNull(SoftLock.decide(License.State.ACTIVE, cacheEnded = true))
    assertNull(SoftLock.decide(License.State.GRACE, cacheEnded = true))
    assertEquals(SoftLock.Reason.ENDED, SoftLock.decide(License.State.EXPIRED, cacheEnded = true))
    assertEquals("مجوزِ ۱۰ روزه بی اینترنت تمام شد ⇒ قفل، با «یک بار وصل شوید»",
      SoftLock.Reason.RENEW, SoftLock.decide(License.State.EXPIRED, cacheEnded = false))
    assertEquals(SoftLock.Reason.ENDED, SoftLock.decide(License.State.NONE, cacheEnded = true))
    assertNull("بی حساب و بی تپشِ تمام‌شده ⇒ همان امروز", SoftLock.decide(License.State.NONE, cacheEnded = false))
    assertNull(SoftLock.decide(null, cacheEnded = false))
  }
}
