package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.chat.VoiceFlow
import kotlin.math.min
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/*
 *  برنامهٔ خرید — پیامِ صوتیِ مشتری (CHAT-07)؛ همان `store/chat-voice.js` ِ ویترین (ضبط) و `voice` ِ `store/chat.js` (پخش):
 *    ضبط ──── فقط با پرچمِ خاموشِ `shop.chat.voice` (`limits.voice`) و سکویی که میکروفون دارد (نه دکمهٔ مرده)؛ ماشینِ حالت
 *              همان `VoiceFlow` (راهِ زدن): «پایان و فرستادن» یا «لغو»؛ ۵ دقیقه، نزدیکِ `audioMax` یا پس رفتنِ برنامه ⇒
 *              ایستادن و پیش‌شنوایی پیش از «فرستادن»؛ بزرگ‌تر از سقف ⇒ فقط «لغو». میکروفون با هر بیرون‌رفتن بسته.
 *              ⛔ ضبط به رشته‌ای بسته است که در آن آغاز شد ([ChatRecording.convId]): رشتهٔ دیگر ⇒ لغو، هرگز فرستادن به آن.
 *    فرستادن ─ همان صفِ عکس (`ChatOutbox`، `kind = audio`، `meta.durationMs`): یک `clientId` و همان بایت در هر تلاش ⇒
 *              سرور همان رسانه (`reused`) و همان پیام (`replayed`)؛ بایت‌ها فقط در حافظه.
 *    پخش ──── هیچ بایتی پیش از زدنِ «پخش»؛ بایت با بلیت از همان `GET …/media/:id`؛ نوعی که سکو نمی‌شناسد ⇒ پیامِ روشن بی
 *              بارگیری؛ ۴۰۴/۴۱۰ ⇒ «پاک شده یا مهلتش گذشته» بی «دوباره»؛ بی‌اینترنت/۴۲۹/۵xx ⇒ «تلاشِ دوباره»؛ ۴۰۱ ⇒ تأییدِ شماره.
 *  مرز سرور است، نه پرچم: نوع از بایت، `audioMax`، مدت ۱ تا ۱۵ دقیقه، سطلِ (دکان، شماره)، گفت‌وگوی خودِ مشتری.
 */

/** آن‌چه نوارِ ضبط و ناحیهٔ زندهٔ صفحه‌خوان می‌گویند (همان کلیدهای `vc.*` ِ وب) */
enum class ChatVoiceSay { ASK, RECORDING, READY, LIMIT, FULL, HIDDEN, BIG, DENIED, NO_MIC, BUSY, FAIL }

/** ضبطِ در راه، بسته به رشتهٔ [convId] (پیش از پاسخِ میکروفون [VoiceFlow.Phase.IDLE] با [ChatVoiceSay.ASK]) */
data class ChatRecording(
  val convId: String,
  val phase: VoiceFlow.Phase = VoiceFlow.Phase.IDLE,
  val startedAt: Long = 0,
  val durationMs: Long = 0,
  val size: Long = 0,
  val say: ChatVoiceSay = ChatVoiceSay.ASK,
) {
  /** دلیلی که ضبطی نساخت (اجازه، میکروفون، سکو): فقط پیام و «لغو» */
  val refused: Boolean get() = say == ChatVoiceSay.DENIED || say == ChatVoiceSay.NO_MIC || say == ChatVoiceSay.BUSY || say == ChatVoiceSay.FAIL
}

/** حالِ پخشِ یک پیامِ صوتی */
enum class ChatPlayState { LOADING, PLAYING, PAUSED, GONE, RETRY, CODEC }

/** [note] = پیامِ روشنِ خطا برای [ChatPlayState.RETRY] (بی‌اینترنت، شلوغی، خطای سرور) */
data class ChatPlay(val messageId: String, val state: ChatPlayState, val note: String = "")

object ChatVoice {
  /** سقفِ ضبط (همان `MAX_MS` ِ وب و `ChatMedia.VOICE_MAX_MS` ِ دکان) */
  const val MAX_MS = 5 * 60 * 1000L

  /** حاشیهٔ سقفِ حجم: ضبط پیش از رسیدن به `audioMax` می‌ایستد (همان `HEAD` ِ وب) */
  const val HEAD = 64 * 1024L

  /** بازهٔ مدتِ سرور (`intIn(durationMs, 1 تا ۱۵ دقیقه)`) */
  const val SERVER_MAX_MS = 15 * 60 * 1000L

  /** فاصلهٔ سنجشِ سقف‌ها هنگامِ ضبط */
  const val TICK_MS = 250L

  /** دکمهٔ میکروفون: پرچمِ دکان + سکویی که ضبط می‌کند + رشتهٔ باز. سرورِ کهنه بی `limits` ⇒ نه. */
  fun canRecord(limits: ChatOpenLimits?, device: Boolean, t: ChatThread?): Boolean = device && limits?.voice == true && ChatActs.writable(t)

  /** هنگامِ ضبط: نزدیکِ سقفِ حجم ⇒ [ChatVoiceSay.FULL]، سقفِ زمان ⇒ [ChatVoiceSay.LIMIT]، وگرنه null */
  fun limit(elapsedMs: Long, bytes: Long, cap: Long): ChatVoiceSay? = when {
    cap > 0 && bytes > 0 && bytes >= cap - min(HEAD, cap / 4) -> ChatVoiceSay.FULL
    elapsedMs >= MAX_MS -> ChatVoiceSay.LIMIT
    else -> null
  }

  /** مدتِ ضبط برای `meta.durationMs`: همان بازهٔ سرور */
  fun duration(startedAt: Long, now: Long): Long = (now - startedAt).coerceIn(1, SERVER_MAX_MS)

  /** پس از ایستادن: ردیفِ پیش از فرستادن چه بگوید (بزرگ ⇒ «کوتاه‌تر»، سقفِ حجم، سقفِ زمان، پس رفتن، وگرنه «گوش کنید») */
  fun readySay(size: Long, cap: Long, why: ChatVoiceSay?): ChatVoiceSay = when {
    VoiceFlow.tooBig(size, cap) -> ChatVoiceSay.BIG
    why == ChatVoiceSay.FULL || why == ChatVoiceSay.LIMIT || why == ChatVoiceSay.HIDDEN -> why
    else -> ChatVoiceSay.READY
  }

  /** مدتِ پیامِ رسیده از `meta.durationMs` (نبود یا نادرست ⇒ ۰، یعنی پس از پخش از خودِ صدا) */
  fun durationOf(m: ChatMessage): Long = ((m.meta?.get("durationMs") as? JsonPrimitive)?.longOrNull ?: 0L).coerceIn(0L, SERVER_MAX_MS)

  /** پیامِ صوتیِ پخش‌شدنی: زنده، با رسانه */
  fun playable(m: ChatMessage): Boolean = m.kind == "audio" && m.media != null && !m.deleted

  /** خطای خواندنِ بایت‌های صدا ⇒ حالِ پخش (۴۰۱ را صفحه جدا می‌گیرد: تأییدِ دوبارهٔ شماره) */
  fun playFailure(e: ShopperError): ChatPlayState = if (!e.network && (e.status == 404 || e.status == 410)) ChatPlayState.GONE else ChatPlayState.RETRY

  /** «پخش/توقف» روی [id]: توقفِ همان، ادامهٔ همان، یا بارگیریِ تازه (null) — پاک‌شده و نوعِ ناشناخته دوباره بارگیری نمی‌شوند */
  fun tap(cur: ChatPlay?, id: String): ChatPlayState? {
    if (cur == null || cur.messageId != id) return null
    return when (cur.state) {
      ChatPlayState.PLAYING -> ChatPlayState.PAUSED
      ChatPlayState.PAUSED -> ChatPlayState.PLAYING
      ChatPlayState.LOADING, ChatPlayState.GONE, ChatPlayState.CODEC -> cur.state
      ChatPlayState.RETRY -> null
    }
  }

  /** متنِ نوارِ ضبط */
  fun text(say: ChatVoiceSay): String = when (say) {
    ChatVoiceSay.ASK -> ""
    ChatVoiceSay.RECORDING -> ShopperText.vcRecording
    ChatVoiceSay.READY -> ShopperText.vcReady
    ChatVoiceSay.LIMIT -> ShopperText.vcLimit((MAX_MS / 60_000).toInt())
    ChatVoiceSay.FULL -> ShopperText.vcFull
    ChatVoiceSay.HIDDEN -> ShopperText.vcReady
    ChatVoiceSay.BIG -> ShopperText.vcBig
    ChatVoiceSay.DENIED -> ShopperText.vcDenied
    ChatVoiceSay.NO_MIC -> ShopperText.vcNoMic
    ChatVoiceSay.BUSY -> ShopperText.vcBusy
    ChatVoiceSay.FAIL -> ShopperText.vcFail
  }
}
