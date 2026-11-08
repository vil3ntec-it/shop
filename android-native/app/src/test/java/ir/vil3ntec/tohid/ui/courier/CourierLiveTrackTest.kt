package ir.vil3ntec.tohid.ui.courier

import ir.vil3ntec.tohid.core.courier.LiveTrack
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.CourierCodec
import ir.vil3ntec.tohid.data.LocationFix
import ir.vil3ntec.tohid.data.repo.CourierWire
import ir.vil3ntec.tohid.ui.v3.nav.SourceGraph
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  DLV-16 در برنامهٔ پیک: «سرویسِ پیش‌زمینهٔ موقعیت فقط در زمانِ مأموریت» (§۳.۱۱). تا این آزمون برنامه کلیدِ
 *  `tracking` ِ کارتِ سرور (`server/src/lib/courier-map.js` ‏`decorate`) را اصلاً نمی‌خواند و هیچ سرویسی نبود.
 *  این‌جا: کارت و کارتِ نگه‌داشته کلید را دارند، یک دورِ سرویس همان قاعدهٔ `LiveTrack` را با درِ واقعیِ سرور
 *  (`POST /api/courier/missions/:id/location`) می‌زند، مانیفست سرویسِ نوعِ `location` را بی مکانِ پس‌زمینه دارد،
 *  و از ریشهٔ برنامه (`AppRoot`) تا سرویس راه هست (همان لغزشِ «بی صدازننده» ِ دستهٔ ۱۳ نه).
 */
class CourierLiveTrackTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
  private val courier = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/courier")
  private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

  /* ------------------------------- سیم و کارتِ نگه‌داشته ------------------------------- */

  @Test fun `کارتِ سرور tracking را می‌خواند و بی کلید هرگز موقعیت نمی‌خواهد`() {
    val on = CourierWire.card(obj("""{"id":"A","orderNo":5,"status":"moving","tracking":{"location":true,"minIntervalMs":5000},"updatedAt":9}"""))!!
    assertTrue(on.trackLocation)
    assertEquals(5000L, on.trackMinIntervalMs)
    val off = CourierWire.card(obj("""{"id":"B","orderNo":6,"status":"assigned","tracking":{"location":false,"minIntervalMs":5000}}"""))!!
    assertFalse(off.trackLocation)
    //  پرچم‌های دکان خاموش ⇒ سرور کلید را نمی‌فرستد (همان شیءِ پیشین)
    val none = CourierWire.card(obj("""{"id":"C","orderNo":7,"status":"moving"}"""))!!
    assertFalse(none.trackLocation)
    assertEquals(0L, none.trackMinIntervalMs)
    //  شکلِ نادرست ⇒ نه
    assertFalse(CourierWire.card(obj("""{"id":"D","orderNo":8,"status":"moving","tracking":{"location":"yes"}}"""))!!.trackLocation)
    assertEquals(0L, CourierWire.card(obj("""{"id":"E","orderNo":9,"status":"moving","tracking":{"location":true,"minIntervalMs":-4}}"""))!!.trackMinIntervalMs)
  }

  @Test fun `کارتِ نگه‌داشته همان را دارد تا سرویس پس از بسته شدنِ صفحه هم بخواند`() {
    val c = MissionCard("A", 5, "moving", trackLocation = true, trackMinIntervalMs = 5000, updatedAt = 3)
    val back = CourierCodec.cards(CourierCodec.cardsJson(listOf(c, c.copy(id = "B", trackLocation = false, trackMinIntervalMs = 0))))
    assertEquals(listOf(true, false), back.map { it.trackLocation })
    assertEquals(listOf(5000L, 0L), back.map { it.trackMinIntervalMs })
    //  کارتِ کهنه‌ترِ گوشی (پیش از این نسخه) ⇒ بی موقعیت
    val old = CourierCodec.cards("""[{"id":"X","orderNo":1,"status":"moving"}]""").single()
    assertFalse(old.trackLocation)
  }

  /* ------------------------------- یک دورِ سرویس ------------------------------- */

  private val now = 1_800_000_000_000L
  private val point = LiveTrack.Fix(34.531, 69.172, 15, now - 1_000)

  @Test fun `یک دور فقط مأموریتِ در راه را با درِ موقعیتِ سرور می‌فرستد`() = runBlocking {
    val cards = listOf(
      MissionCard("on", 1, "moving", trackLocation = true, trackMinIntervalMs = 5000, updatedAt = 1),
      MissionCard("off", 2, "moving", trackLocation = false),
      MissionCard("done", 3, "delivered", trackLocation = true),
    )
    val sent = ArrayList<Pair<String, LiveTrack.Fix>>()
    val left = CourierTrack.tick(cards, QueueState(), LiveTrack.Pacer(), now, { point }) { t, f -> sent += t.assignmentId to f; LiveTrack.Sent(true) }
    assertEquals(1, left)
    assertEquals(listOf("on" to point), sent)
    assertEquals("/courier/missions/on/location", ApiEndpoints.Courier.location("on"))
  }

  @Test fun `پاسخِ سرور و شبکه همان کدِ خود را به قاعده می‌دهد`() = runBlocking {
    assertEquals(LiveTrack.Sent(true), CourierTrack.sentOf(ApiResult.Success(true)))
    //  نقطهٔ تندتر از minIntervalMs را سرور بی خطا کنار می‌گذارد ⇒ همان «پذیرفته» (بی تلاشِ دوباره)
    assertEquals(LiveTrack.Sent(true), CourierTrack.sentOf(ApiResult.Success(false)))
    assertEquals(LiveTrack.Sent(false, "offline"), CourierTrack.sentOf(ApiResult.Failure(ApiFailure.Offline())))
    val stop = CourierTrack.sentOf(ApiResult.Failure(ApiFailure.Conflict("این مأموریت «در راه» نیست", "not_on_the_way")))
    assertEquals("not_on_the_way", stop.code)
    //  «رسیدم» در سرور ⇒ ۴۰۹ ⇒ سرویس بی هدف می‌ماند و بسته می‌شود
    val cards = listOf(MissionCard("a", 1, "moving", trackLocation = true, updatedAt = 4))
    assertEquals(0, CourierTrack.tick(cards, QueueState(), LiveTrack.Pacer(), now, { point }) { _, _ -> stop })
    //  بی اینترنت ⇒ نقطه دور، هدف می‌ماند
    assertEquals(1, CourierTrack.tick(cards, QueueState(), LiveTrack.Pacer(), now, { point }) { _, _ -> LiveTrack.Sent(false, "offline") })
  }

  @Test fun `نقطهٔ دستگاه با شعاعِ خطای درست و زمانِ خودش`() {
    val f = CourierTrack.fixOf(LocationFix(34.5, 69.1, accuracy = 8.4f, source = "gps", at = 77))
    assertEquals(LiveTrack.Fix(34.5, 69.1, 8, 77), f)
    assertEquals(null, CourierTrack.fixOf(LocationFix(34.5, 69.1, accuracy = -1f, at = 77)).accuracyM)
  }

  /* ------------------------------- سکو ------------------------------- */

  @Test fun `مانیفست سرویسِ location را دارد و مکانِ پس‌زمینه را نه`() {
    val m = File(repo, "android-native/app/src/main/AndroidManifest.xml").readText()
    assertTrue(Regex("""android:name="\.ui\.courier\.CourierTrackService"\s+android:exported="false"\s+android:foregroundServiceType="location"""").containsMatchIn(m))
    assertTrue(m.contains("android:name=\"android.permission.FOREGROUND_SERVICE_LOCATION\""))
    assertTrue(m.contains("android:name=\"android.permission.ACCESS_FINE_LOCATION\""))
    assertFalse(m.contains("ACCESS_BACKGROUND_LOCATION"))
    val svc = File(courier, "CourierTrackAndroid.kt").readText()
    assertTrue("نوعِ سرویس در startForeground همان مانیفست", svc.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION"))
    assertTrue("پس از کشته شدنِ فرایند خودبه‌خود بالا نمی‌آید", svc.contains("return START_NOT_STICKY") && !svc.contains("START_STICKY\n"))
    assertTrue("حلقه همان حلقهٔ مشترک", svc.contains("CourierTrack.loop(this@CourierTrackService)"))
    assertTrue("بی هدف ⇒ بسته", svc.contains("if (!CourierTrack.wanted(this))"))
    //  نقطه روی گوشی نوشته نمی‌شود و کهنه‌تر از یک دقیقه نیست
    val shared = File(courier, "CourierLiveTrackAndroid.kt").readText()
    assertTrue(shared.contains("DeviceLocation.current(context, force = true, maxAgeMs = LiveTrack.MAX_FIX_AGE_MS, keep = false)"))
    //  کامپیوتر سرویسِ اندروید را نمی‌سازد؛ همتای بی‌اثر دارد
    val desk = File(repo, "desktop/build.gradle.kts").readText()
    assertTrue(desk.contains("\"ui/courier/CourierTrackAndroid.kt\""))
    assertTrue(File(repo, "desktop/src/main/kotlin/ir/vil3ntec/tohid/ui/courier/CourierTrackDesk.kt").readText().contains("object CourierTrackPlatform"))
  }

  @Test fun `از ریشهٔ برنامه تا سرویس راه هست و خروج خاموشش می‌کند`() {
    val graph = SourceGraph.of(File(repo, "android-native/app/src/main/java"))
    val reached = graph.reach(listOf("ir.vil3ntec.tohid.ui.AppRoot"))
    val need = listOf("CourierHome", "CourierLiveTrack", "CourierTrackPlatform", "CourierTrackService", "CourierTrack")
      .map { "ir.vil3ntec.tohid.ui.courier.$it" }
    for (n in need) assertTrue("$n تعریف نشده", n in graph.declared)
    assertEquals("بی صدازننده از AppRoot", emptyList<String>(), need.filterNot { it in reached })
    val home = File(courier, "CourierHome.kt").readText()
    assertTrue(home.contains("CourierLiveTrack(active, queue)"))
    assertTrue("خروج ⇒ سرویس خاموش", home.contains("CourierTrackPlatform.sync(context, false)"))
  }

  @Test fun `اعلانِ همیشگی به چهار زبان`() {
    val fa = Triple(StrCourierApp.trackChannel, StrCourierApp.trackTitle, StrCourierApp.trackLine)
    assertTrue(fa.third.contains("«رسیدم»"))
    I18n.source = { Lang.EN }
    assertEquals("Your location for this order", StrCourierApp.trackTitle)
    assertTrue(StrCourierApp.trackLine.contains("I've arrived"))
    I18n.source = { Lang.PS }
    assertNotEquals(fa.second, StrCourierApp.trackTitle)
    I18n.source = { Lang.PRS }
    assertTrue(StrCourierApp.trackLine.isNotBlank())
  }
}
