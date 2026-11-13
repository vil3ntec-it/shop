package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.chat.VoiceFlow
import ir.vil3ntec.tohid.core.shopper.ChatOutbox
import ir.vil3ntec.tohid.core.shopper.ChatPlayState
import ir.vil3ntec.tohid.core.shopper.ChatThread
import ir.vil3ntec.tohid.core.shopper.ChatVoice
import ir.vil3ntec.tohid.core.shopper.ChatVoiceSay
import ir.vil3ntec.tohid.core.shopper.CustomerTicket
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperError
import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.ChatModel
import ir.vil3ntec.tohid.customer.ui.Load
import ir.vil3ntec.tohid.customer.ui.VoiceDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CHAT-07 ِ برنامهٔ خرید روی سرورِ جعلی و میکروفونِ ساختگی (بی اندروید) — همان `store/chat-voice.js` و `voice` ِ
 *  `store/chat.js`: دکمه فقط با پرچم و سکو؛ «پایان و فرستادن» از همان صفِ عکس با یک `clientId` و همان بایت؛ سقفِ حجم،
 *  زمان و پس رفتنِ برنامه ⇒ پیش‌شنوایی؛ رشتهٔ دیگر ⇒ لغو و میکروفونِ بسته، هرگز فرستادن به آن؛ پخش بی هیچ بایت پیش از «پخش».
 */
class CustomerChatVoiceTest {

  private var now = 1_000_000L
  /** on · off · old (بی limits) */
  private var flag = "on"
  private var audioMax = 3_145_728L
  /** ok · offline */
  private var upload = "ok"
  /** ok · offline · gone · auth */
  private var media = "ok"
  private val seen = mutableListOf<ShopperRequest>()
  private val uploads = mutableListOf<ByteArray>()
  private var rev = 2L

  private val clip = ByteArray(2_000) { (it % 7).toByte() }
  private val voiceBytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112)

  private fun limits() = when (flag) {
    "old" -> ""
    else -> ""","limits":{"imageMax":1572864,"thumbMax":65536,"audioMax":$audioMax,"voice":${flag == "on"}}"""
  }

  private fun shopVoice(id: String, mediaId: String, seq: Long) =
    """{"id":"$id","seq":$seq,"rev":$seq,"sender":"shop","createdAt":${now - 1000},"kind":"audio","body":"",""" +
      """"media":{"id":"$mediaId","mime":"audio/mp4","size":8},"meta":{"durationMs":3000}}"""

  private fun body(r: ShopperRequest) = Json.parseToJsonElement(r.body!!).jsonObject

  private val http = ShopperHttp { r ->
    seen += r
    val p = r.path
    val cid = p.substringAfter("/conversations/", "").substringBefore('/').substringBefore('?')
    when {
      p.endsWith("/chat/conversations") && r.method == "POST" -> {
        val no = body(r)["orderNo"]?.jsonPrimitive?.content ?: "1"
        ShopperResponse(201, """{"conversation":{"id":"sch_$no","canSend":true,"life":{"state":"open"}}${limits()}}""")
      }
      p.contains("/messages?after=") -> ShopperResponse(200, """{"conversation":{"id":"$cid","canSend":true},"messages":[""" +
        (if (cid == "sch_1") shopVoice("v1", "scf_a", 1) + "," + shopVoice("v2", "scf_gone", 2) else "") + """],"cursor":2}""")
      p.endsWith("/receipts") -> ShopperResponse(200, """{"conversation":{"id":"$cid","canSend":true}}""")
      p.contains("/media?") && r.method == "POST" -> {
        if (upload == "offline") throw java.io.IOException("offline")
        uploads += r.bytes!!
        ShopperResponse(201, """{"mediaId":"scf_up","kind":"audio","mime":"audio/mp4","size":${r.bytes!!.size},"role":"main","reused":${uploads.size > 1}}""")
      }
      p.contains("/media/") && r.method == "GET" -> when {
        media == "offline" -> throw java.io.IOException("offline")
        media == "auth" -> ShopperResponse(401, """{"error":{"code":"verification_required"}}""")
        p.endsWith("/scf_gone") || media == "gone" -> ShopperResponse(404, """{"error":{"code":"media_gone"}}""")
        else -> ShopperResponse(200, "", bytes = voiceBytes)
      }
      p.endsWith("/messages") && r.method == "POST" -> {
        val b = body(r)
        ShopperResponse(201, """{"message":{"id":"a${++rev}","seq":$rev,"rev":$rev,"sender":"customer","mine":true,"kind":"audio",""" +
          """"clientId":"${b["clientId"]!!.jsonPrimitive.content}","media":{"id":"scf_up","mime":"audio/mp4"}}}""")
      }
      else -> ShopperResponse(404, """{"error":{"code":"not_found"}}""")
    }
  }

  /** میکروفون و پخش‌کنندهٔ ساختگی: پاسخِ اجازه را آزمون می‌دهد ([later] ⇒ دیرتر، با [grant]) */
  private class FakeVoice : VoiceDevice {
    override var canRecord = true
    var answer: String? = null
    var later = false
    var pending: ((String?) -> Unit)? = null
    var recording = false
    var bytes: ByteArray? = null
    var written = 0L
    var cancels = 0
    var finishes = 0
    var playable = true
    var decodes = true
    val played = mutableListOf<ByteArray>()
    var paused = false
    var stops = 0

    override fun record(done: (String?) -> Unit) {
      if (later) { pending = done; return }
      recording = answer == null
      done(answer)
    }

    fun grant() {
      recording = true
      pending?.invoke(null)
      pending = null
    }

    override fun recorded(): Long = written
    override fun finish(): ByteArray? { finishes++; recording = false; return bytes }
    override fun cancel() { cancels++; recording = false }
    override fun canPlay(mime: String?): Boolean = playable
    var ending: (() -> Unit)? = null
    override fun play(bytes: ByteArray, ended: () -> Unit): Boolean {
      if (decodes) { played += bytes; ending = ended }
      return decodes
    }
    override fun pause() { paused = true }
    override fun resume() { paused = false }
    override fun stopPlay() { stops++ }
    override fun position(): Long = 0
    override fun length(): Long = 0
  }

  private var lost = 0
  private val ticket = CustomerTicket("c700000002@test.local", "v2.ticket", Long.MAX_VALUE)

  private fun model(device: VoiceDevice? = FakeVoice()) = ChatModel(
    ShopperApi(http), object : ShopperKeyValue {
      val map = HashMap<String, String>()
      override fun get(key: String): String? = map[key]
      override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { now }, { ticket }, { lost++ }, pollWaitMs = 0, device = device,
  )

  private fun ChatModel.t(): ChatThread = (thread as Load.Ready).value
  private fun sends() = seen.filter { it.method == "POST" && it.path.endsWith("/messages") }
  private fun mediaGets() = seen.filter { it.method == "GET" && it.path.contains("/media/") }

  @Test fun micOnlyWithTheShopFlagARecorderAndAnOpenThread() {
    val d = FakeVoice()
    val m = model(d)
    m.open("golestan", 1, "")
    assertTrue(m.voice.canRecord())
    flag = "off"
    m.open("golestan", 1, "")
    assertFalse("پرچمِ خاموشِ shop.chat.voice ⇒ دکمه نیست", m.voice.canRecord())
    m.voice.start()
    assertNull("بی دکمه ⇒ میکروفون هرگز باز نمی‌شود", m.voice.rec)
    assertFalse(d.recording)
    flag = "old"
    m.open("golestan", 1, "")
    assertFalse("سرورِ کهنه بی limits ⇒ نه", m.voice.canRecord())
    flag = "on"
    d.canRecord = false
    m.open("golestan", 1, "")
    assertFalse("گوشیِ بی میکروفون ⇒ نه دکمهٔ مرده", m.voice.canRecord())
    val none = model(null)
    none.open("golestan", 1, "")
    assertFalse("سکوی بی ضبط‌کننده (آیفونِ امروز)", none.voice.canRecord())
    assertFalse(none.voice.canPlay)
  }

  @Test fun finishSendsOnceThroughTheQueueWithItsDurationAndRetryReusesTheSameBytes() {
    val d = FakeVoice().apply { bytes = clip; written = 1_000 }
    val m = model(d)
    m.open("golestan", 1, "")
    m.voice.start()
    assertEquals(VoiceFlow.Phase.TAP, m.voice.rec!!.phase)
    assertEquals(ShopperText.vcRecording, m.voice.say)
    now += 4_200
    m.voice.tick()
    assertEquals(4_200L, m.voice.elapsed)
    upload = "offline"
    m.voice.finish()
    assertEquals(1, d.finishes)
    assertFalse("میکروفون بسته", d.recording)
    assertNull("نوار بسته؛ پیام در صف", m.voice.rec)
    val q = m.outbox.single()
    assertEquals("audio" to "sch_1", q.kind to q.convId)
    assertEquals(4_200L, q.durationMs)
    assertEquals(ChatOutbox.WAITING, q.state)
    assertTrue("بایتِ صدا روی دیسک نمی‌رود", ChatOutbox.load(ChatOutbox.dump(m.outbox)).isEmpty())
    upload = "ok"
    m.online()
    assertTrue(m.outbox.isEmpty())
    assertArrayEquals("همان بایت", clip, uploads.single())
    val s = sends().single()
    assertEquals("/api/store/golestan/chat/conversations/sch_1/messages", s.path)
    val b = body(s)
    assertEquals("audio", b["kind"]!!.jsonPrimitive.content)
    assertEquals("scf_up", b["mediaId"]!!.jsonPrimitive.content)
    assertEquals("4200", b["meta"]!!.jsonObject["durationMs"]!!.jsonPrimitive.content)
    assertEquals(q.clientId, b["clientId"]!!.jsonPrimitive.content)
    assertEquals(ShopperText.vcSent, m.voice.say)
    assertTrue(m.t().messages.any { it.kind == "audio" && it.mine })
  }

  @Test fun sizeTimeAndBackgroundStopToAPreviewAndTooBigOnlyCancels() {
    val d = FakeVoice().apply { bytes = clip }
    val m = model(d)
    m.open("golestan", 1, "")
    //  نزدیکِ سقفِ حجم ⇒ ایستادن و پیش‌شنوایی (نه فرستادن)
    audioMax = 100_000
    m.open("golestan", 1, "")
    m.voice.start()
    d.written = 80_000
    now += 1_000
    m.voice.tick()
    assertEquals(VoiceFlow.Phase.READY, m.voice.rec!!.phase)
    assertEquals(ChatVoiceSay.FULL, m.voice.rec!!.say)
    assertEquals(ShopperText.vcFull, m.voice.say)
    assertTrue(m.outbox.isEmpty())
    m.voice.preview()
    assertArrayEquals("پیش‌شنوایی از همان بایت، بی درخواست", clip, d.played.single())
    m.voice.send()
    assertTrue(m.outbox.isEmpty())
    assertEquals(1, sends().size)

    //  سقفِ ۵ دقیقه
    d.written = 0
    m.voice.start()
    now += ChatVoice.MAX_MS
    m.voice.tick()
    assertEquals(ChatVoiceSay.LIMIT, m.voice.rec!!.say)
    m.voice.cancel()
    assertNull(m.voice.rec)
    assertEquals(ShopperText.vcCancelled, m.voice.say)

    //  برنامه پس رفت ⇒ میکروفون بسته، ضبط تا همین‌جا برای شنیدن
    m.voice.start()
    val f = d.finishes
    m.visible(false)
    assertEquals(f + 1, d.finishes)
    assertEquals(ChatVoiceSay.HIDDEN, m.voice.rec!!.say)
    assertEquals(ShopperText.vcReady, m.voice.say)
    m.visible(true)
    m.voice.cancel()

    //  بزرگ‌تر از سقف ⇒ «پایان و فرستادن» نمی‌فرستد؛ «فرستادن» هم نه؛ فقط «لغو»
    audioMax = 1_000
    m.open("golestan", 1, "")
    m.voice.start()
    m.voice.finish()
    assertEquals(ChatVoiceSay.BIG, m.voice.rec!!.say)
    m.voice.send()
    assertEquals(1, sends().size)
    assertTrue(m.outbox.isEmpty())
    m.voice.cancel()

    //  چیزی ضبط نشد
    d.bytes = null
    audioMax = 3_145_728
    m.open("golestan", 1, "")
    m.voice.start()
    m.voice.finish()
    assertNull(m.voice.rec)
    assertEquals(ShopperText.vcEmpty, m.voice.say)
  }

  //  ⛔ ضبط به رشته‌ای بسته است که در آن آغاز شد: رشتهٔ دیگر ⇒ لغو و میکروفونِ بسته، هرگز فرستادن به آن
  @Test fun anotherThreadCancelsTheRecordingAndNothingIsEverSentThere() {
    val d = FakeVoice().apply { bytes = clip }
    val m = model(d)
    m.open("golestan", 1, "")
    m.voice.start()
    m.open("golestan", 2, "")
    assertEquals(1, d.cancels)
    assertFalse(d.recording)
    assertNull(m.voice.rec)
    m.voice.finish()
    assertTrue(m.outbox.isEmpty())

    //  اجازهٔ دیررسیده پس از گشودنِ رشتهٔ دیگر ⇒ میکروفون همان دم بسته، هیچ ضبطی در رشتهٔ تازه
    d.later = true
    m.voice.start()
    assertEquals(ChatVoiceSay.ASK, m.voice.rec!!.say)
    m.open("golestan", 1, "")
    d.grant()
    assertNull(m.voice.rec)
    assertFalse(d.recording)
    assertTrue(d.cancels >= 2)

    //  ضبطِ آمادهٔ رشتهٔ ۱ پس از رفتن به رشتهٔ ۲ هم دور (بستنِ صفحه هم)
    d.later = false
    m.voice.start()
    m.voice.tick()
    m.leave()
    assertNull(m.voice.rec)
    assertTrue(m.outbox.isEmpty())
    assertTrue(sends().isEmpty())
  }

  @Test fun refusalsShowAClearReasonAndOnlyCancel() {
    val d = FakeVoice().apply { answer = VoiceDevice.DENIED }
    val m = model(d)
    m.open("golestan", 1, "")
    m.voice.start()
    assertTrue(m.voice.rec!!.refused)
    assertEquals(ShopperText.vcDenied, m.voice.say)
    m.voice.finish()
    assertTrue(m.outbox.isEmpty())
    m.voice.cancel()
    assertNull(m.voice.rec)
    assertEquals("", m.voice.say)
    for ((code, text) in listOf(VoiceDevice.NO_MIC to ShopperText.vcNoMic, VoiceDevice.BUSY to ShopperText.vcBusy, "x" to ShopperText.vcFail)) {
      d.answer = code
      m.voice.start()
      assertEquals(text, m.voice.say)
      m.voice.cancel()
    }
  }

  @Test fun playbackLoadsNothingBeforePlayAndNeverRetriesAGoneVoice() {
    val d = FakeVoice()
    val m = model(d)
    m.open("golestan", 1, "")
    val v1 = m.t().messages.first { it.id == "v1" }
    val v2 = m.t().messages.first { it.id == "v2" }
    assertTrue("هیچ بایتی پیش از «پخش»", mediaGets().isEmpty())
    assertEquals(3_000L, m.voice.length(v1))
    m.voice.toggle(v1)
    assertEquals("/api/store/golestan/chat/conversations/sch_1/media/scf_a", mediaGets().single().path)
    assertEquals("v2.ticket", mediaGets().single().headers["X-Store-Ticket"])
    assertArrayEquals(voiceBytes, d.played.single())
    assertEquals(ChatPlayState.PLAYING, m.voice.play!!.state)
    m.voice.toggle(v1)
    assertEquals(ChatPlayState.PAUSED, m.voice.play!!.state)
    assertTrue(d.paused)
    m.voice.toggle(v1)
    assertEquals(ChatPlayState.PLAYING, m.voice.play!!.state)
    assertFalse(d.paused)

    m.voice.toggle(v2)
    assertEquals(ChatPlayState.GONE, m.voice.play!!.state)
    m.voice.toggle(v2)
    assertEquals("پاک شده ⇒ بی «دوباره»", 2, mediaGets().size)

    media = "offline"
    m.voice.toggle(v1.copy(id = "v1b", media = v1.media!!.copy(id = "scf_b")))
    assertEquals(ChatPlayState.RETRY, m.voice.play!!.state)
    assertEquals(ShopperText.error(ShopperError(0, ShopperError.NETWORK)), m.voice.play!!.note)
    media = "ok"
    m.voice.toggle(v1.copy(id = "v1b", media = v1.media!!.copy(id = "scf_b")))
    assertEquals("«تلاشِ دوباره» ⇒ همان رسانه دوباره", ChatPlayState.PLAYING, m.voice.play!!.state)
    assertEquals(4, mediaGets().size)

    //  توقف/ادامه و پخشِ دوبارهٔ همان صدا پس از پایان ⇒ بی درخواستِ تازه
    val v1c = v1.copy(id = "v1c", media = v1.media!!.copy(id = "scf_c"))
    m.voice.toggle(v1c)
    val n = mediaGets().size
    m.voice.toggle(v1c)
    m.voice.toggle(v1c)
    d.ending!!.invoke()
    assertNull("پایانِ پخش ⇒ دکمه دوباره «پخش»", m.voice.play)
    m.voice.toggle(v1c)
    assertEquals(ChatPlayState.PLAYING, m.voice.play!!.state)
    assertEquals(n, mediaGets().size)

    //  نوعی که سکو نمی‌شناسد ⇒ پیامِ روشن بی بارگیری؛ رمزگشا نشناخت ⇒ همان پیام
    d.playable = false
    m.voice.toggle(v1.copy(id = "v1d", media = v1.media!!.copy(id = "scf_d")))
    assertEquals(ChatPlayState.CODEC, m.voice.play!!.state)
    assertEquals(n, mediaGets().size)
    d.playable = true
    d.decodes = false
    m.voice.toggle(v1.copy(id = "v1e", media = v1.media!!.copy(id = "scf_e")))
    assertEquals(ChatPlayState.CODEC, m.voice.play!!.state)

    //  بلیتِ منقضی ⇒ تأییدِ دوبارهٔ شماره
    media = "auth"
    d.decodes = true
    m.voice.toggle(v1.copy(id = "v1f", media = v1.media!!.copy(id = "scf_f")))
    assertNull(m.voice.play)
    assertEquals(1, lost)
    assertTrue(m.needVerify)

    //  بستنِ صفحه ⇒ پخش ایستاد
    val stops = d.stops
    m.leave()
    assertTrue(d.stops >= stops)
    assertNull(m.voice.play)
  }
}
