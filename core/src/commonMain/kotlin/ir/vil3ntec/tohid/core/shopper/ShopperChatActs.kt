package ir.vil3ntec.tohid.core.shopper

import kotlinx.serialization.Serializable

/*
 *  برنامهٔ خرید — پاسخ، ویرایش، سنجاق و جست‌وجوی مشتری در گفت‌وگو با دکان (CHAT-14)؛ همان `store/chat-acts.js` ِ ویترین
 *  روی همان درهای `/api/store/:slug/chat/…` (سرور همه را دوباره می‌سنجد؛ `shop-chat-web-acts.test.js`):
 *    پاسخ ──── `replyTo` در همان صفِ CHAT-02؛ فقط در رشتهٔ باز و نه به سنگِ قبر.
 *    ویرایش ── فقط پیامِ متنیِ زندهٔ خودم در رشتهٔ باز و پیش از `editUntil` ِ ساعتِ سرور؛ در صف نمی‌رود (بی‌اینترنت ⇒ متن
 *              در کادر می‌ماند). از راهِ `POST …/messages/:id/edit` (همان PATCH؛ HttpURLConnection PATCH نمی‌فرستد).
 *    سنجاق ─── فقط برای خودم (دکان نمی‌بیند)، حداکثر ۵؛ نوار فقط از `conversation.pinned` ِ سرور (بی متن؛ متن از رشته).
 *    جست‌وجو ─ `GET …/chat/search` ِ همین دکان و همین شماره، دست‌کم ۲ نویسه؛ نتیجهٔ رشتهٔ دیگر فقط خواندنی.
 *
 *  ⛔ پیوندِ رشته (دستهٔ ۴۲ ِ وب: `hold`، `opening`، `draftOf`): هر نوشته — متن، پاسخ، ویرایش، عکس، صدا — به رشته‌ای
 *  بسته است که در آن نوشته شد. رشتهٔ دیگری باز شد ⇒ نوشته پاک، هرگز فرستادن به رشتهٔ پیشین یا دیگر؛ پاسخِ دیرِ گشودن
 *  یا کارِ رشتهٔ پیشین روی رشتهٔ تازه نمی‌نشیند ([ChatOpening]).
 */

/** یک سنجاقِ مشتری (`conversation.pinned`): فقط شناسه، seq و زمان — هرگز متن */
@Serializable
data class ChatPin(val messageId: String, val seq: Long = 0, val pinnedAt: Long = 0)

/** سقف‌های `limits` ِ `POST …/conversations`؛ [voice] = پرچمِ خاموشِ `shop.chat.voice`. سرورِ کهنه ⇒ همه صفر و خاموش. */
@Serializable
data class ChatOpenLimits(val imageMax: Long = 0, val thumbMax: Long = 0, val audioMax: Long = 0, val voice: Boolean = false)

/** پاسخِ گشودنِ گفت‌وگو: رشته و سقف‌ها */
class ChatOpened(val conversation: ChatConversation, val limits: ChatOpenLimits = ChatOpenLimits())

/** یک نتیجهٔ جست‌وجو (`results` ِ `GET …/chat/search`؛ متن تا ۲۰۰ نویسه) */
@Serializable
data class ChatHit(
  val conversationId: String = "",
  val id: String = "",
  val seq: Long = 0,
  val sender: String = "",
  val kind: String = "text",
  val body: String = "",
  val createdAt: Long = 0,
)

/** پیامِ متنیِ تازه برای صف — همیشه به رشته‌ای که در آن نوشته شد */
data class ChatOutgoing(val convId: String, val body: String, val replyTo: String?)

/** ویرایشی که باید برود: همان پیامِ همان رشته با متنِ تازه */
data class ChatSave(val convId: String, val messageId: String, val text: String)

/**
 *  کادرِ نوشتن، بسته به یک رشته ([convId]): متن، «پاسخ به» ([replyTo]) یا «ویرایش» ([editing]) — همان `draftOf` و
 *  `reply.cid`/`editing.cid` ِ وب: رشتهٔ دیگر ⇒ کادرِ تهی.
 */
data class ChatCompose(val convId: String? = null, val text: String = "", val replyTo: String? = null, val editing: String? = null) {
  /** رشتهٔ [id] پیش روست: نوشته، پاسخ و ویرایشِ رشتهٔ دیگر این‌جا نمی‌آیند */
  fun bind(id: String?): ChatCompose = if (id != null && id == convId) this else ChatCompose(id)

  /** نوشتن در رشتهٔ [id]؛ کادر بیش از سقفِ سرور (۴۰۰۰) نمی‌گیرد، پس پیامِ فرستاده هرگز بی‌صدا بریده نمی‌شود */
  fun type(id: String, s: String): ChatCompose = bind(id).copy(text = s.take(ChatActs.TEXT_MAX))

  /** پایانِ پاسخ یا ویرایش (همان `stop(clear)` ِ وب): [clear] ⇒ متنِ ویرایش هم می‌رود؛ متنِ نوشتهٔ تازه می‌ماند */
  fun stop(clear: Boolean): ChatCompose = copy(text = if (editing != null && clear) "" else text, replyTo = null, editing = null)

  /** پیامِ تازه، یا null: فقط در همان رشته‌ای که متن در آن نوشته شد و اینک باز است، و نه در حالِ ویرایش */
  fun outgoing(t: ChatThread?): ChatOutgoing? {
    val id = t?.conversation?.id ?: return null
    if (id != convId || editing != null) return null
    val body = text.trim().takeIf { it.isNotEmpty() } ?: return null
    return ChatOutgoing(id, body, replyTo)
  }

  /** ویرایش، یا null: فقط پیامی از همین رشته، با متنِ ناتهی */
  fun saving(t: ChatThread?): ChatSave? {
    val id = editing ?: return null
    val conv = convId ?: return null
    if (t?.conversation?.id != conv) return null
    val body = text.trim().takeIf { it.isNotEmpty() } ?: return null
    return ChatSave(conv, id, body)
  }
}

/**
 *  نوبتِ گشودنِ رشته (همان `opening` ِ وب): هر گشودن نوبتی تازه؛ پاسخِ گشودنِ پیشین ([live] = false) هیچ‌جا نمی‌نشیند —
 *  نه رشته، نه خطا (پیش‌تر پیامِ سفارشِ #۴ در این فاصله به رشتهٔ #۳ می‌رفت).
 */
class ChatOpening {
  private var turn = 0

  fun next(): Int = ++turn

  fun live(t: Int): Boolean = t == turn
}

object ChatActs {
  /** سقفِ متنِ سرور (`MAX_TEXT`) */
  const val TEXT_MAX = 4000
  const val QUERY_MIN = 2
  const val QUERY_MAX = 100
  const val SEARCH_LIMIT = 30
  private const val SNIP = 80

  /** رشتهٔ باز و نوشتنی (همان `open()` ِ `chat-acts.js`) */
  fun writable(t: ChatThread?): Boolean = t != null && t.conversation.canSend && t.conversation.life.state != "closed"

  private fun mine(m: ChatMessage) = m.mine || m.sender == "customer"

  private fun here(t: ChatThread, m: ChatMessage) = t.messages.any { it.id == m.id }

  fun canReply(t: ChatThread?, m: ChatMessage): Boolean = t != null && writable(t) && !m.deleted && here(t, m)

  /** فقط پیامِ متنیِ زندهٔ خودم در رشتهٔ باز، پیش از `editUntil` ِ سرور و نه پیامی که سرور گفت مهلتش گذشته ([passed]) */
  fun canEdit(t: ChatThread?, m: ChatMessage, now: Long, passed: Set<String> = emptySet()): Boolean =
    t != null && writable(t) && mine(m) && m.kind == "text" && !m.deleted && here(t, m) && m.id !in passed && (m.editUntil ?: 0) > now

  /** سنجاق برای هر پیامِ زندهٔ همین رشته — رشتهٔ بسته هم (سنجاق کارِ خواندن است، نه نوشتن) */
  fun canPin(t: ChatThread?, m: ChatMessage): Boolean = t != null && !m.deleted && here(t, m)

  fun pinned(t: ChatThread?, id: String): Boolean = t?.conversation?.pinned?.any { it.messageId == id } == true

  /** «پاسخ» ⇒ کادرِ همین رشته با برچسبِ پاسخ (ویرایشِ نیمه‌کاره رها، متنِ نوشتهٔ تازه می‌ماند) */
  fun reply(c: ChatCompose, t: ChatThread, m: ChatMessage): ChatCompose? =
    if (!canReply(t, m)) null else c.bind(t.conversation.id).stop(true).copy(replyTo = m.id)

  /** «ویرایش» ⇒ کادرِ همین رشته با متنِ همان پیام */
  fun edit(t: ChatThread, m: ChatMessage, now: Long, passed: Set<String> = emptySet()): ChatCompose? =
    if (!canEdit(t, m, now, passed)) null else ChatCompose(t.conversation.id, m.body, editing = m.id)

  /** ردِ سرور که حالتِ ویرایش را می‌بندد؛ متن در کادر می‌ماند تا اگر خواست پیامِ تازه بفرستد (همان `stop(false)` ِ وب) */
  fun editEnds(e: ShopperError): Boolean = e.code == "edit_window_passed" || e.code == "not_author" || e.code == "message_not_found"

  /** پرسشِ جست‌وجو، یا null (کوتاه‌تر از ۲ نویسه ⇒ بی درخواست، همان `query_too_short` ِ سرور) */
  fun query(q: String): String? = q.trim().take(QUERY_MAX).trim().takeIf { it.length >= QUERY_MIN }

  /** نقلِ کوتاه (همان `snip` ِ وب): پاک‌شده یا نیامده ⇒ «پیامِ پیشین»، بی متن ⇒ «عکس»/«پیامِ صوتی»، بلند ⇒ ۸۰ نویسه + … */
  fun snip(m: ChatMessage?): String = if (m == null || m.deleted) ShopperText.chatGone else snip(m.kind, m.body)

  fun snip(h: ChatHit): String = snip(h.kind, h.body)

  private fun snip(kind: String, body: String): String = when {
    body.isEmpty() -> when (kind) {
      "image" -> ShopperText.chatImage
      "audio" -> ShopperText.chatAudio
      else -> ShopperText.chatGone
    }
    body.length > SNIP -> body.take(if (body[SNIP - 1].isHighSurrogate()) SNIP - 1 else SNIP) + "…"
    else -> body
  }

  /** نوارِ سنجاق‌ها به ترتیبِ سرور: (شناسه، نقل از پیامِ همین رشته) */
  fun pins(t: ChatThread?): List<Pair<String, String>> =
    t?.conversation?.pinned.orEmpty().map { p -> p.messageId to snip(t?.messages?.firstOrNull { it.id == p.messageId }) }

  /** یک خطِ نتیجه: «شما/دکان، نقل» و برای رشتهٔ دیگر «…، در گفت‌وگوی دیگر» (فقط خواندنی) */
  fun hitLine(h: ChatHit, convId: String?): String {
    val who = if (h.sender == "customer") ShopperText.chatMe else ShopperText.chatShopSide
    val line = who + ShopperText.sep + snip(h)
    return if (h.conversationId == convId) line else line + ShopperText.sep + ShopperText.chatOther
  }

  /** جایِ یک پیام در رشتهٔ همین صفحه (رفتن به حباب)، یا null ⇒ «این پیام در این صفحه باز نیست» */
  fun indexOf(t: ChatThread?, id: String): Int? = t?.messages?.indexOfFirst { it.id == id }?.takeIf { it >= 0 }
}
