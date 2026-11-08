package ir.vil3ntec.tohid.core.shopper

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 *  برنامهٔ خریدِ مشتری، مرحلهٔ ۲ — گفت‌وگو با دکان (v3 §۳۳؛ CHAT-02/06) روی همان درهای ویترین
 *  (`/api/store/:slug/chat/…`، `server/src/routes/shop-chat-store.js`).
 *
 *  ⛔ هویتِ مشتری همان شمارهٔ تأییدشده است (`X-Store-Ticket`، مثلِ `store/chat.js` ِ وب)؛ shop_id از slug. نشستِ حساب
 *  (`X-Store-Session`) پس از ورودِ مشتری (D-09) می‌آید و این‌جا فرستاده نمی‌شود.
 *  ⛔ تصمیمِ «بسته/مشکل دارم/دورِ تازه» فقط `life` ِ سرور است؛ برنامه هیچ زمانی را خودش حساب نمی‌کند.
 *
 *  CHAT-02 (صف): همان قاعده‌های `ChatQueue` ِ برنامهٔ دکان — هر پیام هنگامِ نوشتن یک `clientId` می‌گیرد و تا رسیدن
 *  همان را نگه می‌دارد (سرور تکراری نمی‌سازد)، در هر گفت‌وگو فقط سرِ صف می‌رود (ترتیبِ نوشتن)، خطای گذرا ⇒ backoff ِ
 *  نمایی با لرزش (۲ ثانیه تا ۵ دقیقه)، خطای قطعی ⇒ «نرفت» بی گیر انداختنِ پشتِ سری‌ها، بلیتِ منقضی ⇒ «منتظرِ تأییدِ
 *  شماره» (پیام می‌ماند)، و برنامه وسطِ ارسال بسته شد ⇒ همان شناسه دوباره در صف. صفِ متن روی دیسک می‌ماند؛ بایت‌های
 *  عکس فقط در حافظه (بسته شدنِ برنامه عکسِ نفرستاده را می‌برد و صفحه «نرفت» نمی‌گوید چون دیگر در صف نیست).
 */

@Serializable
data class ChatLife(
  val kind: String = "general",
  /** open · reopened · closed */
  val state: String = "open",
  val reason: String = "",
  val closedAt: Long? = null,
  val issueUntil: Long? = null,
  val canIssue: Boolean = false,
  val canRestart: Boolean = false,
)

@Serializable
data class ChatReceipts(val peerDeliveredSeq: Long = 0, val peerReadSeq: Long = 0, val myDeliveredSeq: Long = 0, val myReadSeq: Long = 0)

@Serializable
data class ChatShopName(val name: String = "")

@Serializable
data class ChatConversation(
  val id: String,
  val status: String = "open",
  val seq: Long = 0,
  val rev: Long = 0,
  val unread: Long = 0,
  val orderNo: Long? = null,
  val lastMessageAt: Long? = null,
  val life: ChatLife = ChatLife(),
  val shop: ChatShopName = ChatShopName(),
  val canSend: Boolean = true,
  val blockedShop: Boolean = false,
  val receipts: ChatReceipts = ChatReceipts(),
)

@Serializable
data class ChatMedia(val id: String, val mime: String? = null, val size: Long? = null, val thumbId: String? = null, val thumbSize: Long? = null)

@Serializable
data class ChatMessage(
  val id: String,
  val seq: Long = 0,
  val rev: Long = 0,
  /** customer · shop */
  val sender: String = "",
  val createdAt: Long = 0,
  val kind: String = "text",
  val body: String = "",
  val replyTo: String? = null,
  val meta: JsonObject? = null,
  val editedAt: Long? = null,
  val mine: Boolean = false,
  val media: ChatMedia? = null,
  /** فقط برای پیامِ خودِ بیننده */
  val clientId: String? = null,
  /** sent · delivered · read */
  val status: String? = null,
  val hidden: Boolean = false,
  val deleted: Boolean = false,
)

@Serializable
data class ChatChanges(
  val conversation: ChatConversation,
  val messages: List<ChatMessage> = emptyList(),
  val cursor: Long = 0,
  val hasMore: Boolean = false,
)

@Serializable
data class ChatSent(val message: ChatMessage, val replayed: Boolean = false)

@Serializable
data class ChatUpload(val mediaId: String, val kind: String = "", val mime: String = "", val size: Long = 0, val role: String = "main", val reused: Boolean = false)

/** پیامی که هنوز نرسیده (صفِ CHAT-02). [photo]/[thumb] فقط در حافظه‌اند (روی دیسک نمی‌روند). */
@Serializable
data class ChatOutItem(
  val clientId: String,
  val slug: String,
  val convId: String,
  val kind: String = "text",
  val body: String = "",
  val width: Int? = null,
  val height: Int? = null,
  /** بارگذاری‌شده ⇒ تلاشِ بعدی بایت‌ها را دوباره نمی‌فرستد */
  val mediaId: String? = null,
  val thumbId: String? = null,
  val state: String = ChatOutbox.QUEUED,
  val attempts: Int = 0,
  val nextAt: Long = 0,
  /** کدِ خطای سرور برای «نرفت» (متن را [ShopperText.error] می‌گوید) */
  val error: String = "",
  val createdAt: Long = 0,
)

/** بایت‌های عکسِ آماده (پس از چرخش، حذفِ EXIF و فشرده‌سازی؛ [ShopperPhoto]) — فقط در حافظه. */
class ChatPhoto(val main: ByteArray, val thumb: ByteArray?, val width: Int, val height: Int)

enum class ChatFailure { RETRY, VERIFY, FAIL }

object ChatOutbox {
  const val KEY = "shopper.chat.v1"
  const val QUEUED = "queued"
  const val SENDING = "sending"
  const val WAITING = "waiting"
  /** بلیتِ شماره منقضی/نادرست ⇒ تا تأییدِ دوباره می‌ماند */
  const val VERIFY = "verify"
  const val FAILED = "failed"
  const val MAX_ITEMS = 200
  const val BASE_MS = 2_000L
  const val MAX_MS = 5 * 60 * 1000L
  private val ID_RE = Regex("^[A-Za-z0-9_.:-]{8,128}$")
  private const val ABC = "abcdefghijklmnopqrstuvwxyz0123456789"

  /** همان `CLIENT_ID_RE` ِ سرور */
  fun validId(id: String?): Boolean = id != null && ID_RE.matches(id)

  fun newId(at: Long, random: Random = Random): String =
    "c" + at.toString(36) + "-" + (1..20).map { ABC[random.nextInt(ABC.length)] }.joinToString("")

  /** مهلتِ تلاشِ nام (از ۱) با لرزشِ نیمه: [d/2, d] — همان `ChatBackoff` ِ دکان */
  fun delayMs(attempt: Int, rand: Double): Long {
    val n = max(1, attempt)
    val d = min(MAX_MS, BASE_MS shl min(n - 1, 20))
    return (d / 2.0 + rand.coerceIn(0.0, 1.0) * (d / 2.0)).roundToLong()
  }

  /** خطای سرور ⇐ «دوباره بعداً»، «تأییدِ دوبارهٔ شماره» یا «قطعی» */
  fun failureOf(e: ShopperError): ChatFailure = when {
    e.network || e.status == 429 || e.status >= 500 || e.status == 408 -> ChatFailure.RETRY
    e.status == 401 || e.code == "verification_required" -> ChatFailure.VERIFY
    else -> ChatFailure.FAIL
  }

  /** خواندن از دیسک: خراب ⇒ تهی؛ «در راه» (برنامه وسطِ ارسال بسته شد) ⇒ دوباره در صف با همان شناسه */
  fun load(raw: String?): List<ChatOutItem> = try {
    raw?.let { ShopperJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ChatOutItem.serializer()), it) }.orEmpty()
      .filter { validId(it.clientId) && it.convId.isNotBlank() && it.slug.isNotBlank() }
      .map { if (it.state == SENDING) it.copy(state = QUEUED, nextAt = 0) else it }
  } catch (_: Exception) {
    emptyList()
  }

  /** روی دیسک فقط پیام‌های متنی (بایتِ عکس در حافظه است، پس عکسِ نیمه‌کاره پس از بسته شدن بی‌معناست) */
  fun dump(items: List<ChatOutItem>): String =
    ShopperJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(ChatOutItem.serializer()), items.filter { it.kind == "text" })

  /** پیامِ تازه، پشتِ صفِ همان گفت‌وگو؛ ترتیب حتی با دو پیام در یک میلی‌ثانیه */
  fun add(items: List<ChatOutItem>, slug: String, convId: String, kind: String, body: String, now: Long, random: Random = Random,
          width: Int? = null, height: Int? = null): Pair<List<ChatOutItem>, ChatOutItem> {
    val at = max(now, (items.maxOfOrNull { it.createdAt } ?: 0) + 1)
    val item = ChatOutItem(newId(at, random), slug, convId, kind, body.trim(), width, height, createdAt = at)
    //  سقف: کهنه‌ترین «نرفته»ها اول می‌روند، هرگز پیامی که هنوز در راه است
    val kept = if (items.size < MAX_ITEMS) items else items.filter { it.state != FAILED }.takeLast(MAX_ITEMS - 1)
    return (kept + item) to item
  }

  fun forConv(items: List<ChatOutItem>, convId: String): List<ChatOutItem> = items.filter { it.convId == convId }.sortedBy { it.createdAt }

  /**
   *  سرِ صفِ این گفت‌وگو اگر الان وقتش است، وگرنه null. «نرفته»ها رد می‌شوند؛ «در راه»، «منتظرِ تأیید» و
   *  «منتظرِ اینترنت» ِ زود صف را نگه می‌دارند (ترتیب).
   */
  fun next(items: List<ChatOutItem>, convId: String, now: Long): ChatOutItem? {
    for (x in forConv(items, convId)) {
      when {
        x.state == FAILED -> continue
        x.state == SENDING || x.state == VERIFY -> return null
        x.state == WAITING && x.nextAt > now -> return null
        else -> return x
      }
    }
    return null
  }

  fun patch(items: List<ChatOutItem>, clientId: String, change: (ChatOutItem) -> ChatOutItem): List<ChatOutItem> =
    items.map { if (it.clientId == clientId) change(it) else it }

  fun done(items: List<ChatOutItem>, clientId: String): List<ChatOutItem> = items.filter { it.clientId != clientId }

  fun failed(items: List<ChatOutItem>, clientId: String, e: ShopperError, now: Long, rand: Double): List<ChatOutItem> = patch(items, clientId) {
    when (failureOf(e)) {
      ChatFailure.RETRY -> (it.attempts + 1).let { n -> it.copy(state = WAITING, attempts = n, nextAt = now + delayMs(n, rand), error = e.code) }
      ChatFailure.VERIFY -> it.copy(state = VERIFY, error = e.code)
      ChatFailure.FAIL -> it.copy(state = FAILED, error = e.code)
    }
  }

  /** «دوباره» ِ کاربر، یا شماره دوباره تأیید شد — همان شناسه، از سرِ نو */
  fun retry(items: List<ChatOutItem>, clientId: String): List<ChatOutItem> =
    patch(items, clientId) { it.copy(state = QUEUED, nextAt = 0, attempts = 0, error = "") }

  fun reverified(items: List<ChatOutItem>): List<ChatOutItem> =
    items.map { if (it.state == VERIFY) it.copy(state = QUEUED, nextAt = 0, error = "") else it }

  /** اینترنت برگشت: «منتظرِ اینترنت»ها همین حالا (همان رویدادِ `online` ِ وب) */
  fun online(items: List<ChatOutItem>): List<ChatOutItem> = items.map { if (it.state == WAITING) it.copy(nextAt = 0) else it }

  /**
   *  زودترین زمانی که صف باید دوباره بیدار شود: فقط «منتظرِ اینترنت»ها (پیامِ در صفِ پشتِ سری با رسیدنِ سرِ صف
   *  خودش می‌رود). null ⇒ چیزی منتظرِ زمان نیست — هرگز بیدار شدنِ بی‌پایانِ پیاپی.
   */
  fun wakeAt(items: List<ChatOutItem>): Long? = items.filter { it.state == WAITING }.minOfOrNull { it.nextAt }

  /** بدنهٔ `POST …/messages` (همان `send` ِ سرور) */
  fun body(x: ChatOutItem): String {
    val parts = buildList {
      add("\"clientId\":" + q(x.clientId))
      add("\"kind\":" + q(x.kind))
      if (x.body.isNotEmpty()) add("\"body\":" + q(x.body))
      x.mediaId?.let { add("\"mediaId\":" + q(it)) }
      x.thumbId?.let { add("\"thumbId\":" + q(it)) }
      if (x.kind == "image" && x.width != null && x.height != null) add("\"meta\":{\"width\":${x.width},\"height\":${x.height}}")
    }
    return parts.joinToString(",", "{", "}")
  }

  private fun q(s: String): String = ShopperJson.encodeToString(kotlinx.serialization.serializer<String>(), s)
}

/**
 *  رشتهٔ پیام‌های یک گفت‌وگو روی برنامه — ادغامِ پاسخ‌های `GET …/messages?after=<rev>`: ویرایش/حذف همان شناسه را با
 *  `rev` ِ تازه برمی‌گرداند ⇒ جایگزین، نه افزوده. ترتیبِ نمایش `seq` ِ سرور است.
 */
data class ChatThread(val conversation: ChatConversation, val messages: List<ChatMessage> = emptyList(), val cursor: Long = 0) {
  fun merge(c: ChatChanges): ChatThread {
    val byId = LinkedHashMap<String, ChatMessage>()
    messages.forEach { byId[it.id] = it }
    c.messages.forEach { m -> val old = byId[m.id]; if (old == null || m.rev >= old.rev) byId[m.id] = m }
    return ChatThread(c.conversation, byId.values.filter { !it.hidden }.sortedBy { it.seq }, max(cursor, c.cursor))
  }

  /** پیامِ تازهٔ خودم که همین حالا رسید (پیش از رسیدنِ تغییرها) */
  fun withMine(m: ChatMessage): ChatThread =
    if (messages.any { it.id == m.id }) this else copy(messages = (messages + m).sortedBy { it.seq })

  /** بالاترین `seq` ِ پیامِ دکان که اینک دیده شد ⇒ رسید و «خواندم» (`POST …/receipts`) */
  fun lastShopSeq(): Long = messages.filter { it.sender == "shop" }.maxOfOrNull { it.seq } ?: 0

  /** پیامِ صف که سرور دارد (جوابِ گم‌شده + تغییرها) ⇒ از صف بیرون، بی تکرار */
  fun arrived(clientId: String): Boolean = messages.any { it.mine && it.clientId == clientId }
}
