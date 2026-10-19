package ir.vil3ntec.tohid.core.chat

import ir.vil3ntec.tohid.core.net.ApiFailure
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 *  صفِ ارسالِ چتِ دکان — اندروید و کامپیوتر (v3 §۳۳؛ CHAT-02/03/11). قراردادِ
 *  سرور: `clientId` ِ یکتا در `server/src/lib/shop-chat.js` (PR #259).
 *
 *  ── چرا صف ──────────────────────────────────────────────────────────────
 *  دکان روی اینترنتِ ضعیف کار می‌کند. پیامی که نوشته شد نباید با قطعِ شبکه
 *  گم شود و نباید با تلاشِ دوباره دو بار برسد. پس:
 *    • هر پیام همان لحظهٔ نوشتن یک `clientId` می‌گیرد و تا رسیدن همان را
 *      نگه می‌دارد؛ سرور با همین شناسه پیامِ تکراری نمی‌سازد.
 *    • در هر گفت‌وگو فقط سرِ صف فرستاده می‌شود — ترتیب همان ترتیبِ نوشتن.
 *    • خطای گذرا (بی شبکه، تأخیر، ۴۲۹، ۵xx) ⇒ «منتظرِ اینترنت» با backoff ِ
 *      نمایی و لرزش (۲، ۴، ۸… ثانیه تا ۵ دقیقه).
 *    • خطای قطعی (۴۰۰، ۴۰۳، ۴۰۹، ۴۱۳…) ⇒ «نرفت» با پیامِ سرور؛ کاربر «دوباره»
 *      یا «لغو» می‌زند و صفِ پشتِ سرش گیر نمی‌کند.
 *    • نشستِ از دست‌رفته ⇒ «منتظرِ ورود»؛ پیام می‌ماند تا ورودِ دوباره.
 *    • برنامه وسطِ ارسال بسته شد ⇒ همان شناسه دوباره در صف.
 *  ──────────────────────────────────────────────────────────────────────
 *
 *  ⛔ این کلاس هیچ درخواستی نمی‌فرستد و به اندروید وابسته نیست؛ ذخیره و
 *  فرستادن کارِ صدا زننده است (`load`/`save`، `drain(send)`).
 */
@Serializable
data class OutboxItem(
  val clientId: String,
  val convId: String,
  val kind: String = "text",
  val body: String = "",
  val meta: ChatMeta = ChatMeta(),
  /** بایت‌های رسانه روی همین دستگاه (پرونده در `ChatOutbox`) */
  val blobKey: String? = null,
  val thumbKey: String? = null,
  val size: Long = 0,
  /** بارگذاری‌شده ⇒ تلاشِ بعدی دوباره نمی‌فرستد */
  val mediaId: String? = null,
  val thumbId: String? = null,
  val state: String = ChatQueue.QUEUED,
  val attempts: Int = 0,
  val nextAt: Long = 0,
  val error: String = "",
  val createdAt: Long = 0,
)

enum class ChatErrorKind { RETRY, AUTH, FAIL }

object ChatBackoff {
  const val BASE_MS = 2_000L
  const val MAX_MS = 5 * 60 * 1000L

  /** مهلتِ تلاشِ nام (از ۱) با لرزشِ نیمه: [d/2, d] */
  fun delayMs(attempt: Int, rand: () -> Double = { Random.nextDouble() }): Long {
    val n = max(1, attempt)
    val d = min(MAX_MS, BASE_MS shl min(n - 1, 20))
    return (d / 2.0 + rand().coerceIn(0.0, 1.0) * (d / 2.0)).roundToLong()
  }

  /** خطای درخواست ⇐ «دوباره بعداً»، «هویت» یا «قطعی». */
  fun kindOf(error: Throwable): ChatErrorKind = when (error) {
    is ApiFailure.SessionExpired, is ApiFailure.Unauthorized -> ChatErrorKind.AUTH
    is ApiFailure.RateLimited -> ChatErrorKind.RETRY
    is ApiFailure -> if (error.retryable) ChatErrorKind.RETRY else ChatErrorKind.FAIL
    is java.io.IOException -> ChatErrorKind.RETRY
    else -> ChatErrorKind.FAIL
  }
}

object ChatIds {
  private val RE = Regex("^[A-Za-z0-9_.:-]{8,128}$")
  private const val ABC = "abcdefghijklmnopqrstuvwxyz0123456789"

  fun valid(id: String?): Boolean = id != null && RE.matches(id)

  /** زمان (یکتا در همین صف) + ۲۰ نویسهٔ تصادفی — با الگوی سرور جور است. */
  fun next(at: Long, random: Random = Random): String =
    "k" + at.toString(36) + "-" + (1..20).map { ABC[random.nextInt(ABC.length)] }.joinToString("")
}

class ChatQueue(
  load: () -> List<OutboxItem>?,
  private val save: (List<OutboxItem>) -> Unit,
  private val now: () -> Long = { System.currentTimeMillis() },
  private val rand: () -> Double = { Random.nextDouble() },
  private val random: Random = Random,
) {
  companion object {
    const val QUEUED = "queued"
    const val SENDING = "sending"
    const val WAITING = "waiting"
    const val AUTH = "auth"
    const val FAILED = "failed"
  }

  private val items: MutableList<OutboxItem> = mutableListOf()

  init {
    val raw = runCatching { load() }.getOrNull().orEmpty()
    var fixed = false
    raw.filter { ChatIds.valid(it.clientId) && it.convId.isNotBlank() }.forEach {
      //  برنامه وسطِ ارسال بسته شد ⇒ همان شناسه دوباره در صف (سرور تکراری نمی‌سازد)
      if (it.state == SENDING) { items += it.copy(state = QUEUED, nextAt = 0); fixed = true } else items += it
    }
    if (fixed) persist()
  }

  private fun persist() { runCatching { save(items.toList()) } }

  @Synchronized fun all(): List<OutboxItem> = items.toList()

  @Synchronized fun get(clientId: String): OutboxItem? = items.firstOrNull { it.clientId == clientId }

  @Synchronized fun forConv(convId: String): List<OutboxItem> =
    items.filter { it.convId == convId }.sortedBy { it.createdAt }

  /** گفت‌وگوهایی که چیزی برای فرستادن دارند (بی «نرفته»ها). */
  @Synchronized fun convs(): List<String> =
    items.filter { it.state != FAILED }.map { it.convId }.distinct()

  @Synchronized
  fun add(
    convId: String,
    kind: String = "text",
    body: String = "",
    meta: ChatMeta = ChatMeta(),
    blobKey: String? = null,
    thumbKey: String? = null,
    size: Long = 0,
    clientId: String? = null,
  ): OutboxItem {
    //  ترتیب حتی با دو پیام در یک میلی‌ثانیه؛ همان عدد در شناسه ⇒ یکتا
    val at = max(now(), (items.maxOfOrNull { it.createdAt } ?: 0) + 1)
    val item = OutboxItem(
      clientId = clientId?.takeIf { ChatIds.valid(it) } ?: ChatIds.next(at, random),
      convId = convId, kind = kind, body = body, meta = meta,
      blobKey = blobKey, thumbKey = thumbKey, size = size, createdAt = at,
    )
    items += item
    persist()
    return item
  }

  /**
   *  سرِ صفِ این گفت‌وگو، اگر الان وقتش است — وگرنه null. «نرفته»ها رد
   *  می‌شوند؛ «در راه»، «منتظرِ هویت» و «منتظرِ اینترنت»ِ زود صف را نگه می‌دارند.
   */
  @Synchronized
  fun next(convId: String, at: Long = now()): OutboxItem? {
    for (x in forConv(convId)) {
      when {
        x.state == FAILED -> continue
        x.state == SENDING || x.state == AUTH -> return null
        x.state == WAITING && x.nextAt > at -> return null
        else -> return x
      }
    }
    return null
  }

  @Synchronized
  fun patch(clientId: String, change: (OutboxItem) -> OutboxItem): OutboxItem? {
    val i = items.indexOfFirst { it.clientId == clientId }
    if (i < 0) return null
    items[i] = change(items[i])
    persist()
    return items[i]
  }

  fun sending(clientId: String) = patch(clientId) { it.copy(state = SENDING) }

  /** رفت: از صف بیرون — کلیدهای رسانهٔ محلی برای پاک کردن. */
  @Synchronized
  fun done(clientId: String): List<String> {
    val x = items.firstOrNull { it.clientId == clientId } ?: return emptyList()
    items.remove(x)
    persist()
    return listOfNotNull(x.blobKey, x.thumbKey)
  }

  fun failed(clientId: String, kind: ChatErrorKind, message: String, at: Long = now()): OutboxItem? = patch(clientId) {
    when (kind) {
      ChatErrorKind.RETRY -> {
        val n = it.attempts + 1
        it.copy(state = WAITING, attempts = n, nextAt = at + ChatBackoff.delayMs(n, rand), error = message)
      }
      ChatErrorKind.AUTH -> it.copy(state = AUTH, error = message)
      ChatErrorKind.FAIL -> it.copy(state = FAILED, error = message)
    }
  }

  /** «دوباره» ِ کاربر — همان شناسه، از سرِ نو. */
  @Synchronized
  fun retry(clientId: String): OutboxItem? {
    val x = get(clientId) ?: return null
    if (x.state == SENDING) return null
    return patch(clientId) { it.copy(state = QUEUED, attempts = 0, nextAt = 0, error = "") }
  }

  /** «لغو» — فقط وقتی در راه نیست. کلیدهای رسانه برای پاک کردن، یا null. */
  @Synchronized
  fun cancel(clientId: String): List<String>? {
    val x = get(clientId) ?: return null
    if (x.state == SENDING) return null
    return done(clientId)
  }

  /** اینترنت برگشت ⇒ هر «منتظر» همین حالا. */
  @Synchronized
  fun wake(): Boolean {
    var any = false
    items.replaceAll { if (it.state == WAITING) { any = true; it.copy(nextAt = 0) } else it }
    if (any) persist()
    return any
  }

  /** ورودِ دوباره ⇒ پیام‌های «منتظرِ هویت» دوباره در صف. */
  @Synchronized
  fun resumeAuth(): Boolean {
    var any = false
    items.replaceAll { if (it.state == AUTH) { any = true; it.copy(state = QUEUED, nextAt = 0) } else it }
    if (any) persist()
    return any
  }

  @Synchronized fun nextWake(): Long? = items.filter { it.state == WAITING }.minOfOrNull { it.nextAt }

  /** پیامی که سرور با همین clientId دارد دیگر در صف نمی‌ماند (نمایشِ دوباره ممنوع). */
  @Synchronized
  fun reconcile(convId: String, serverClientIds: Collection<String>): List<String> {
    val ids = serverClientIds.toSet()
    val gone = items.filter { it.convId == convId && it.clientId in ids && it.state != SENDING }
    if (gone.isEmpty()) return emptyList()
    items.removeAll(gone.toSet())
    persist()
    return gone.flatMap { listOfNotNull(it.blobKey, it.thumbKey) }
  }

  /**
   *  فرستادنِ صفِ یک گفت‌وگو، یکی‌یکی و به ترتیب. خطای قطعی صفِ پشتِ سر را
   *  نگه نمی‌دارد؛ خطای گذرا و هویت نگه می‌دارد (ترتیب).
   */
  suspend fun drain(
    convId: String,
    send: suspend (OutboxItem) -> ChatMessage,
    onSent: (OutboxItem, ChatMessage, List<String>) -> Unit = { _, _, _ -> },
    onChange: () -> Unit = {},
  ) {
    while (true) {
      val item = next(convId) ?: return
      sending(item.clientId)
      onChange()
      val result = runCatching { send(get(item.clientId) ?: item) }
      val message = result.getOrNull()
      if (message != null) {
        val keys = done(item.clientId)
        onSent(item, message, keys)
        onChange()
        continue
      }
      val error = result.exceptionOrNull() ?: IllegalStateException()
      if (error is kotlinx.coroutines.CancellationException) {
        //  صفحه بسته شد: پیام همان‌طور در صف می‌ماند، نه «نرفت»
        patch(item.clientId) { it.copy(state = QUEUED) }
        throw error
      }
      val kind = ChatBackoff.kindOf(error)
      val updated = failed(item.clientId, kind, (error as? ApiFailure)?.userMessage ?: error.message.orEmpty())
      onChange()
      if (updated?.state != FAILED) return
    }
  }
}

/**
 *  نمای یک گفت‌وگو: پیام‌ها با شناسه، جلو رفتن با `after=<rev>` (CHAT-03/08).
 *  ویرایش و حذف همان ردیف را جایگزین می‌کنند؛ «حذف برای من» دیده نمی‌شود.
 */
class ChatThread {
  var cursor: Long = 0
    private set
  var conversation: ChatConversation? = null
  private val byId = LinkedHashMap<String, ChatMessage>()

  /** @return آیا چیزی برای نمایش عوض شد */
  @Synchronized
  fun apply(changes: ChatChanges): Boolean {
    var changed = false
    for (m in changes.messages) {
      val old = byId[m.id]
      if (old == null || old.rev != m.rev) { byId[m.id] = m; changed = true }
    }
    if (changes.cursor > cursor) cursor = changes.cursor
    changes.conversation?.let {
      if (it != conversation) changed = true
      conversation = it
    }
    return changed
  }

  @Synchronized fun add(message: ChatMessage): Boolean = apply(ChatChanges(messages = listOf(message)))

  @Synchronized fun list(): List<ChatMessage> = byId.values.filter { !it.hidden }.sortedBy { it.seq }

  @Synchronized fun maxSeq(): Long = byId.values.maxOfOrNull { it.seq } ?: 0

  data class Receipt(val deliveredSeq: Long, val readSeq: Long)

  /** رسیدی که باید اعلام شود، یا null. «خوانده» فقط وقتی گفت‌وگو جلوی چشم است. */
  @Synchronized
  fun receiptNeed(visible: Boolean): Receipt? {
    val r = conversation?.receipts ?: return null
    val top = maxSeq()
    val d = if (top > r.myDeliveredSeq) top else 0
    val read = if (visible && top > r.myReadSeq) top else 0
    if (d == 0L && read == 0L) return null
    return Receipt(maxOf(d, read, r.myDeliveredSeq), if (read > 0) read else r.myReadSeq)
  }

  companion object {
    /** وضعیتِ پیامِ خودم فقط از رسیدهای واقعیِ طرف (CHAT-04). */
    fun statusOf(m: ChatMessage, receipts: ChatReceipts?): String = when {
      m.deleted || m.hidden -> ""
      receipts == null -> m.status ?: "sent"
      m.seq <= receipts.peerReadSeq -> "read"
      m.seq <= receipts.peerDeliveredSeq -> "delivered"
      else -> "sent"
    }

    val LABEL = mapOf(
      ChatQueue.QUEUED to "در صفِ دستگاه",
      ChatQueue.SENDING to "در حالِ ارسال…",
      ChatQueue.WAITING to "منتظرِ اینترنت",
      ChatQueue.AUTH to "منتظرِ ورودِ دوباره",
      ChatQueue.FAILED to "نرفت",
      "sent" to "به سرور رسید",
      "delivered" to "به دستگاهِ طرف رسید",
      "read" to "خوانده شد",
    )
  }
}
