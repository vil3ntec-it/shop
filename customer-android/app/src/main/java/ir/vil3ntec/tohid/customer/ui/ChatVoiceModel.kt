package ir.vil3ntec.tohid.customer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.chat.VoiceFlow
import ir.vil3ntec.tohid.core.shopper.ChatActs
import ir.vil3ntec.tohid.core.shopper.ChatMessage
import ir.vil3ntec.tohid.core.shopper.ChatPlay
import ir.vil3ntec.tohid.core.shopper.ChatPlayState
import ir.vil3ntec.tohid.core.shopper.ChatRecording
import ir.vil3ntec.tohid.core.shopper.ChatVoice
import ir.vil3ntec.tohid.core.shopper.ChatVoiceSay
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperResult
import ir.vil3ntec.tohid.core.shopper.ShopperText
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  ضبط و پخشِ پیامِ صوتی روی سکو (CHAT-07) — اندروید: `VoiceAndroid`؛ آیفون هنوز نه (null ⇒ نه دکمهٔ میکروفون، نه پخش،
 *  و حبابِ صدا پیامِ «این برنامه این نوعِ صدا را پخش نمی‌کند» بی هیچ بارگیری). بی android.* (صفحه‌های مشترکِ آیفون).
 */
interface VoiceDevice {
  /** میکروفونی هست (وگرنه دکمه نیست — نه دکمهٔ مرده) */
  val canRecord: Boolean

  /** اجازه (نخستین بار با پرسشِ سیستم، فقط با زدنِ میکروفون) و آغاز؛ [done] با null = در حالِ ضبط، وگرنه [DENIED]/[NO_MIC]/[BUSY]/[FAIL] */
  fun record(done: (String?) -> Unit)

  /** بایت‌هایی که تا این دم نوشته شده (سنجشِ نزدیکیِ `audioMax`) */
  fun recorded(): Long

  /** پایانِ ضبط و بستنِ میکروفون ⇒ بایت‌ها، یا null (چیزی ضبط نشد) */
  fun finish(): ByteArray?

  /** لغو و بستنِ میکروفون، هر جا که باشد (بی ضبط ⇒ هیچ) */
  fun cancel()

  /** این سکو این نوع را پخش می‌کند؟ (نه ⇒ پیامِ روشن بی بارگیری؛ همان `canPlayType` ِ وب) */
  fun canPlay(mime: String?): Boolean

  /** پخش از آغاز؛ [ended] در پایان؛ false ⇒ پخش نشد (رمزگشا نشناخت) */
  fun play(bytes: ByteArray, ended: () -> Unit): Boolean

  fun pause()

  fun resume()

  fun stopPlay()

  /** جای پخش و مدتِ خودِ صدا، میلی‌ثانیه (۰ ⇒ نامعلوم) */
  fun position(): Long

  fun length(): Long

  companion object {
    const val DENIED = "denied"
    const val NO_MIC = "nomic"
    const val BUSY = "busy"
    const val FAIL = "fail"
  }
}

/**
 *  پیامِ صوتیِ مشتری — همان `store/chat-voice.js` (ضبط با ماشینِ `VoiceFlow`، راهِ زدن) و `voice` ِ `store/chat.js` (پخش).
 *  قاعده‌ها در `ChatVoice` ِ core. ⛔ ضبط به رشته‌ای بسته است که در آن آغاز شد: رشتهٔ دیگر یا بستنِ صفحه ⇒ لغو و
 *  میکروفونِ بسته؛ فرستادن فقط به همان رشته (`ChatModel.sendVoice`)، از راهِ همان صفِ عکس.
 *  [tick] را صفحه هنگامِ ضبط هر ۲۵۰ میلی‌ثانیه می‌زند (سقفِ زمان و حجم)؛ این‌جا هیچ زمان‌سنجی نیست.
 */
class ChatVoiceModel internal constructor(private val chat: ChatModel, private val api: ShopperApi, private val device: VoiceDevice?) {
  var rec: ChatRecording? by mutableStateOf(null)
    private set
  /** ناحیهٔ زندهٔ صفحه‌خوان — فقط با گذرها عوض می‌شود، نه با تیکِ زمان */
  var say: String by mutableStateOf("")
    private set
  /** زمانِ ضبط برای نوار (با [tick]) */
  var elapsed: Long by mutableStateOf(0L)
    private set
  var play: ChatPlay? by mutableStateOf(null)
    private set
  /** پیش‌شنواییِ ضبطِ آماده در حالِ پخش */
  var previewing: Boolean by mutableStateOf(false)
    private set

  private var bytes: ByteArray? = null
  /** آخرین صدای بارگیری‌شده (شناسهٔ رسانه، بایت) — پخشِ دوباره بی درخواستِ تازه */
  private var heard: Pair<String, ByteArray>? = null
  private var turn = 0

  /** دکمهٔ میکروفون: سکو + پرچمِ دکان (`limits.voice`) + رشتهٔ باز */
  fun canRecord(): Boolean = device != null && ChatVoice.canRecord(chat.limits, device.canRecord, chat.current())

  /** پخش‌کننده‌ای هست */
  val canPlay: Boolean get() = device != null

  /** میکروفون: ضبطِ تازه؛ یکی در راه است ⇒ هیچ */
  fun start() {
    if (rec != null || !canRecord()) return
    val d = device ?: return
    val conv = chat.current()?.conversation?.id ?: return
    stopPlayback()
    val r = ChatRecording(conv)
    rec = r
    say = ""
    d.record { err ->
      if (rec !== r) {   //  در این میان لغو شد یا رشتهٔ دیگری باز شد ⇒ میکروفون بسته
        if (err == null) d.cancel()
        return@record
      }
      if (err != null) {
        val why = when (err) {
          VoiceDevice.DENIED -> ChatVoiceSay.DENIED
          VoiceDevice.NO_MIC -> ChatVoiceSay.NO_MIC
          VoiceDevice.BUSY -> ChatVoiceSay.BUSY
          else -> ChatVoiceSay.FAIL
        }
        rec = r.copy(say = why)
        say = ChatVoice.text(why)
        return@record
      }
      val s = VoiceFlow.next(VoiceFlow.Phase.IDLE, VoiceFlow.Input.START)
      rec = r.copy(phase = s.phase, startedAt = chat.clock(), say = ChatVoiceSay.RECORDING)
      elapsed = 0
      say = ShopperText.vcRecording
    }
  }

  /** هنگامِ ضبط (صفحه هر ۲۵۰ms): رشتهٔ دیگر ⇒ لغو؛ سقفِ زمان یا نزدیکِ `audioMax` ⇒ ایستادن و پیش‌شنوایی */
  fun tick() {
    val r = rec ?: return
    if (r.phase != VoiceFlow.Phase.TAP) return
    if (!chat.live(r.convId)) { drop(); return }
    elapsed = chat.clock() - r.startedAt
    ChatVoice.limit(elapsed, device?.recorded() ?: 0, chat.limits.audioMax)?.let { input(VoiceFlow.Input.LIMIT, it) }
  }

  /** «پایان و فرستادن» */
  fun finish() = input(VoiceFlow.Input.FINISH, null)

  /** «فرستادن» ِ ردیفِ آماده (بزرگ‌تر از سقف ⇒ هیچ؛ فقط «لغو») */
  fun send() = input(VoiceFlow.Input.SEND, null)

  /** برنامه پس رفت: میکروفون بسته، ضبط تا همین‌جا نگه داشته (نه میکروفونِ روشن در پس‌زمینه) */
  internal fun hidden() {
    if (rec?.phase == VoiceFlow.Phase.TAP) input(VoiceFlow.Input.LIMIT, ChatVoiceSay.HIDDEN)
  }

  private fun input(i: VoiceFlow.Input, why: ChatVoiceSay?) {
    val r = rec ?: return
    val s = VoiceFlow.next(r.phase, i)
    when (s.effect) {
      VoiceFlow.Effect.STOP, VoiceFlow.Effect.STOP_SEND -> stopped(r, s.effect == VoiceFlow.Effect.STOP_SEND, why)
      VoiceFlow.Effect.SEND -> sendNow(r)
      VoiceFlow.Effect.CANCEL, VoiceFlow.Effect.DISCARD -> cancel()
      else -> Unit
    }
  }

  /** ضبط ایستاد ⇒ بایت‌ها، میکروفون بسته، `settle` (جا شد و «پایان و فرستادن» ⇒ همین حالا؛ وگرنه ردیفِ پیش از فرستادن) */
  private fun stopped(r: ChatRecording, send: Boolean, why: ChatVoiceSay?) {
    val b = device?.finish()
    val ms = ChatVoice.duration(r.startedAt, chat.clock())
    val cap = chat.limits.audioMax
    val s = VoiceFlow.settle(send, b?.size?.toLong(), cap, chat.live(r.convId) && ChatActs.writable(chat.current()))
    if (b == null || s.say == VoiceFlow.Say.EMPTY) { close(ShopperText.vcEmpty); return }
    bytes = b
    val ready = r.copy(phase = VoiceFlow.Phase.READY, durationMs = ms, size = b.size.toLong(), say = ChatVoice.readySay(b.size.toLong(), cap, why))
    rec = ready
    elapsed = ms
    if (s.effect == VoiceFlow.Effect.SEND) { sendNow(ready); return }
    say = ChatVoice.text(ready.say)
  }

  private fun sendNow(r: ChatRecording) {
    val b = bytes ?: return
    if (VoiceFlow.tooBig(b.size.toLong(), chat.limits.audioMax)) return
    close(ShopperText.vcUp)
    //  ⛔ فقط به رشته‌ای که ضبط در آن آغاز شد؛ رشتهٔ دیگری باز است ⇒ دور، هرگز به رشتهٔ تازه
    if (!chat.sendVoice(r.convId, b, r.durationMs)) say = ""
  }

  /** «لغو» از هر حال: ضبط یا پیش‌نویس دور، میکروفون بسته (VoiceFlow: CANCEL/DISCARD) */
  fun cancel() {
    val r = rec ?: return
    if (r.phase.recording || r.say == ChatVoiceSay.ASK) device?.cancel()
    close(if (r.refused) "" else ShopperText.vcCancelled)
  }

  /** رشتهٔ دیگری باز شد یا صفحه بسته شد: ضبط/پیش‌نویس لغو و میکروفون بسته، پخش ایستاده — بی پیام */
  internal fun drop() {
    rec?.let { r -> if (r.phase.recording || r.say == ChatVoiceSay.ASK) device?.cancel() }
    close("")
    stopPlayback()
    heard = null
  }

  /** پیامِ صوتیِ صف رسید (`ChatModel.drain`) */
  internal fun sent(convId: String) {
    if (rec == null && chat.live(convId)) say = ShopperText.vcSent
  }

  private fun close(text: String) {
    if (previewing) device?.stopPlay()
    previewing = false
    rec = null
    bytes = null
    elapsed = 0
    say = text
  }

  /** پیش‌شنوایی ِ ضبطِ آماده: پخش از آغاز یا ایستادن */
  fun preview() {
    val d = device ?: return
    val b = bytes ?: return
    if (previewing) { d.stopPlay(); previewing = false; return }
    stopPlayback()
    val my = ++turn
    previewing = d.play(b) { if (my == turn) previewing = false }
  }

  // ── پخشِ پیامِ رسیده ──

  /** «پخش/توقف»: هیچ بایتی پیش از همین زدن؛ نوعِ ناشناخته بی بارگیری؛ ۴۰۴/۴۱۰ بی «دوباره»؛ ۴۰۱ ⇒ تأییدِ شماره */
  fun toggle(m: ChatMessage) {
    val media = m.media ?: return
    if (!ChatVoice.playable(m)) return
    when (ChatVoice.tap(play, m.id)) {
      ChatPlayState.PAUSED -> { device?.pause(); play = ChatPlay(m.id, ChatPlayState.PAUSED); return }
      ChatPlayState.PLAYING -> { device?.resume(); play = ChatPlay(m.id, ChatPlayState.PLAYING); return }
      ChatPlayState.LOADING, ChatPlayState.GONE, ChatPlayState.CODEC -> return
      ChatPlayState.RETRY, null -> Unit
    }
    stopPlayback()
    if (previewing) { device?.stopPlay(); previewing = false }
    val d = device
    if (d == null || !d.canPlay(media.mime)) { play = ChatPlay(m.id, ChatPlayState.CODEC); return }
    heard?.takeIf { it.first == media.id }?.let { start(m.id, it.second); return }
    val conv = chat.current()?.conversation?.id ?: return
    val slug = chat.slug ?: return
    val tk = chat.ticketOrLost() ?: return
    val my = ++turn
    play = ChatPlay(m.id, ChatPlayState.LOADING)
    chat.scope.launch {
      val r = withContext(chat.io) { api.chatMedia(slug, tk, conv, media.id) }
      if (my != turn || !chat.live(conv)) return@launch
      when (r) {
        is ShopperResult.Ok -> {
          heard = media.id to r.value
          start(m.id, r.value)
        }
        is ShopperResult.Fail -> if (r.error.status == 401) {
          play = null
          chat.lost()
        } else {
          val st = ChatVoice.playFailure(r.error)
          play = ChatPlay(m.id, st, if (st == ChatPlayState.RETRY) ShopperText.error(r.error) else "")
        }
      }
    }
  }

  private fun start(id: String, b: ByteArray) {
    val d = device ?: return
    val my = turn
    val ok = d.play(b) { if (my == turn && play?.messageId == id) play = null }
    play = ChatPlay(id, if (ok) ChatPlayState.PLAYING else ChatPlayState.CODEC)
  }

  private fun stopPlayback() {
    turn++
    if (play?.state == ChatPlayState.PLAYING || play?.state == ChatPlayState.PAUSED) device?.stopPlay()
    play = null
  }

  /** جای پخش و مدت برای نوارِ پیشرفت (فقط پیامی که اینک پخش/ایستاده است) */
  fun position(id: String): Long = if (play?.messageId == id) device?.position() ?: 0 else 0

  fun length(m: ChatMessage): Long = ChatVoice.durationOf(m).takeIf { it > 0 } ?: if (play?.messageId == m.id) device?.length() ?: 0 else 0
}
