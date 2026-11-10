package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.chat.VoiceFlow
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** CHAT-07 ِ برنامهٔ خرید (همان `store/chat-voice.js` و `voice` ِ `store/chat.js`): دکمه، سقف‌ها، بدنهٔ پیام و حالِ پخش. */
class ShopperVoiceTest {

  @AfterTest fun resetLang() {
    ShopperText.lang = ShopperLang.FA
  }

  private val open = ChatThread(ChatConversation("sch_1"))

  @Test fun micOnlyWithTheShopFlagAPlatformRecorderAndAnOpenThread() {
    val on = ChatOpenLimits(audioMax = 3_145_728, voice = true)
    assertTrue(ChatVoice.canRecord(on, device = true, t = open))
    assertFalse(ChatVoice.canRecord(on.copy(voice = false), true, open), "پرچمِ خاموشِ shop.chat.voice ⇒ دکمه نیست")
    assertFalse(ChatVoice.canRecord(ChatOpenLimits(), true, open), "سرورِ کهنه بی limits ⇒ نه")
    assertFalse(ChatVoice.canRecord(null, true, open))
    assertFalse(ChatVoice.canRecord(on, device = false, t = open), "سکوی بی ضبط‌کننده ⇒ نه دکمهٔ مرده")
    assertFalse(ChatVoice.canRecord(on, true, null), "پیش از پاسخِ گشودن ⇒ نه")
    val closed = open.merge(ChatChanges(ChatConversation("sch_1", canSend = false, life = ChatLife(state = "closed")), emptyList(), 0))
    assertFalse(ChatVoice.canRecord(on, true, closed), "رشتهٔ بسته")
  }

  @Test fun timeAndSizeLimitsStopTheRecordingLikeTheWeb() {
    val cap = 3_145_728L
    assertNull(ChatVoice.limit(1_000, 10_000, cap))
    assertEquals(ChatVoiceSay.LIMIT, ChatVoice.limit(ChatVoice.MAX_MS, 10_000, cap), "۵ دقیقه")
    assertEquals(ChatVoiceSay.FULL, ChatVoice.limit(1_000, cap - ChatVoice.HEAD, cap), "۶۴ کیلوبایت پیش از سقف")
    assertNull(ChatVoice.limit(1_000, cap - ChatVoice.HEAD - 1, cap))
    assertEquals(ChatVoiceSay.FULL, ChatVoice.limit(1_000, 75_000, 100_000), "سقفِ کوچک: حاشیه یک‌چهارم")
    assertNull(ChatVoice.limit(1_000, 0, 100_000), "هنوز بایتی نیامده")
    assertNull(ChatVoice.limit(1_000, 9_000_000, 0), "سقفِ نگفته")
    assertEquals(1, ChatVoice.duration(500, 500), "مدت دست‌کم ۱ (همان بازهٔ سرور)")
    assertEquals(ChatVoice.SERVER_MAX_MS, ChatVoice.duration(0, 99 * 60_000))
    assertEquals(42_000, ChatVoice.duration(1_000, 43_000))

    assertEquals(ChatVoiceSay.BIG, ChatVoice.readySay(cap + 1, cap, ChatVoiceSay.LIMIT), "بزرگ‌تر از سقف ⇒ فقط «لغو»")
    assertEquals(ChatVoiceSay.LIMIT, ChatVoice.readySay(10, cap, ChatVoiceSay.LIMIT))
    assertEquals(ChatVoiceSay.HIDDEN, ChatVoice.readySay(10, cap, ChatVoiceSay.HIDDEN))
    assertEquals(ChatVoiceSay.READY, ChatVoice.readySay(10, cap, null))
    assertEquals("ضبط به سقفِ ۵ دقیقه رسید و تمام شد.", ChatVoice.text(ChatVoiceSay.LIMIT))
    assertEquals(ShopperText.vcReady, ChatVoice.text(ChatVoiceSay.HIDDEN), "پس رفتنِ برنامه همان «گوش کنید و بفرستید» ِ وب")
    assertEquals("۴:۰۵", ShopperText.mmss(245_900))
    ShopperText.lang = ShopperLang.EN
    assertEquals("0:09", ShopperText.mmss(9_999))
  }

  @Test fun voiceGoesThroughTheQueueWithOneClientIdDurationAndBytesOnlyInMemory() {
    val (q, a) = ChatOutbox.add(emptyList(), "golestan", "sch_1", "audio", "", 5, Random(1), durationMs = 4_200)
    val body = Json.parseToJsonElement(ChatOutbox.body(a.copy(mediaId = "scf_9"))).jsonObject
    assertEquals("audio", body["kind"]!!.jsonPrimitive.content)
    assertEquals("scf_9", body["mediaId"]!!.jsonPrimitive.content)
    assertEquals("4200", body["meta"]!!.jsonObject["durationMs"]!!.jsonPrimitive.content)
    assertEquals(a.clientId, body["clientId"]!!.jsonPrimitive.content)
    assertFalse("body" in body)
    assertEquals(emptyList(), ChatOutbox.load(ChatOutbox.dump(q)), "صدا روی دیسک نمی‌رود (بایت فقط در حافظه)")
    assertTrue(ChatOutbox.validId(a.clientId))
  }

  @Test fun playbackNeverGuessesGoneAndCodecStayRetryReloads() {
    assertEquals(ChatPlayState.GONE, ChatVoice.playFailure(ShopperError(404, ShopperError.BAD_RESPONSE)))
    assertEquals(ChatPlayState.GONE, ChatVoice.playFailure(ShopperError(410, ShopperError.BAD_RESPONSE)))
    assertEquals(ChatPlayState.RETRY, ChatVoice.playFailure(ShopperError(0, ShopperError.NETWORK)))
    assertEquals(ChatPlayState.RETRY, ChatVoice.playFailure(ShopperError(429, "rate_limited")))
    assertEquals(ChatPlayState.RETRY, ChatVoice.playFailure(ShopperError(503, ShopperError.BAD_RESPONSE)))

    assertNull(ChatVoice.tap(null, "m1"), "نخستین زدن ⇒ بارگیری")
    assertNull(ChatVoice.tap(ChatPlay("m2", ChatPlayState.PLAYING), "m1"), "پیامِ دیگر ⇒ بارگیریِ همین")
    assertEquals(ChatPlayState.PAUSED, ChatVoice.tap(ChatPlay("m1", ChatPlayState.PLAYING), "m1"))
    assertEquals(ChatPlayState.PLAYING, ChatVoice.tap(ChatPlay("m1", ChatPlayState.PAUSED), "m1"))
    assertEquals(ChatPlayState.GONE, ChatVoice.tap(ChatPlay("m1", ChatPlayState.GONE), "m1"), "پاک شده ⇒ بی «دوباره»")
    assertEquals(ChatPlayState.CODEC, ChatVoice.tap(ChatPlay("m1", ChatPlayState.CODEC), "m1"))
    assertEquals(ChatPlayState.LOADING, ChatVoice.tap(ChatPlay("m1", ChatPlayState.LOADING), "m1"), "دوبار زدن ⇒ یک درخواست")
    assertNull(ChatVoice.tap(ChatPlay("m1", ChatPlayState.RETRY), "m1"), "«تلاشِ دوباره» ⇒ بارگیریِ دوباره")

    val voice = ChatMessage("m1", kind = "audio", media = ChatMedia("scf_1", "audio/mp4"), meta = JsonObject(mapOf("durationMs" to JsonPrimitive(61_000))))
    assertTrue(ChatVoice.playable(voice))
    assertFalse(ChatVoice.playable(voice.copy(deleted = true)), "سنگِ قبر")
    assertFalse(ChatVoice.playable(voice.copy(media = null)))
    assertEquals(61_000, ChatVoice.durationOf(voice))
    assertEquals(0, ChatVoice.durationOf(voice.copy(meta = JsonObject(mapOf("durationMs" to JsonPrimitive("x"))))), "نادرست ⇒ از خودِ صدا")
    assertEquals(0, ChatVoice.durationOf(voice.copy(meta = null)))
    assertEquals(ChatVoice.SERVER_MAX_MS, ChatVoice.durationOf(voice.copy(meta = JsonObject(mapOf("durationMs" to JsonPrimitive(99_999_999))))))
  }

  @Test fun recordingStateKnowsRefusalsAndUsesTheSharedVoiceFlow() {
    val r = ChatRecording("sch_1")
    assertFalse(r.refused)
    for (s in listOf(ChatVoiceSay.DENIED, ChatVoiceSay.NO_MIC, ChatVoiceSay.BUSY, ChatVoiceSay.FAIL)) assertTrue(r.copy(say = s).refused, s.name)
    //  راهِ زدن ِ VoiceFlow: آغاز ⇒ TAP؛ پایان ⇒ STOP_SEND؛ سقف ⇒ STOP؛ لغو در آماده ⇒ DISCARD
    assertEquals(VoiceFlow.Phase.TAP, VoiceFlow.next(VoiceFlow.Phase.IDLE, VoiceFlow.Input.START).phase)
    assertEquals(VoiceFlow.Effect.STOP_SEND, VoiceFlow.next(VoiceFlow.Phase.TAP, VoiceFlow.Input.FINISH).effect)
    assertEquals(VoiceFlow.Effect.STOP, VoiceFlow.next(VoiceFlow.Phase.TAP, VoiceFlow.Input.LIMIT).effect)
    assertEquals(VoiceFlow.Effect.DISCARD, VoiceFlow.next(VoiceFlow.Phase.READY, VoiceFlow.Input.CANCEL).effect)
  }
}
