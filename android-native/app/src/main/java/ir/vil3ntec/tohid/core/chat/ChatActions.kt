package ir.vil3ntec.tohid.core.chat

/**
 *  CHAT-14 — کارهای یک پیام در گفت‌وگوی برنامهٔ مدیریت (پاسخ، ویرایش، سنجاق) و جست‌وجو در پیام‌ها.
 *
 *  همان قاعده‌های سرور (`server/src/lib/shop-chat.js`، `shop-chat-pins.js`) — سرور هر کار را دوباره می‌سنجد (۴۰۳
 *  `edit_window_passed`، ۴۰۹ ِ سقفِ سنجاق…)؛ این‌جا فقط دکمه‌ای که حتماً رد می‌شود نشان داده نمی‌شود:
 *   • پاک‌شده یا پنهان ⇒ هیچ کار.
 *   • پاسخ: اجازهٔ `chat.reply` و گفت‌وگویی که می‌پذیرد (`canSend`).
 *   • ویرایش: فقط نویسندهٔ همان پیام (`mine`)، فقط متن، و در مهلتِ `editWindowMs` (نبود ⇒ سرور می‌سنجد).
 *   • سنجاق: اجازهٔ `chat.reply`؛ سنجاق‌شده ⇒ «برداشتن»، وگرنه تا [MAX_PINS].
 *  Kotlin خالص است — بی android.*، بی java.*.
 */
object ChatActions {

  enum class Action { REPLY, EDIT, PIN, UNPIN }

  /** همان `MAX_PINS` ِ `shop-chat-pins.js` */
  const val MAX_PINS = 5

  /** همان «دست‌کم دو نویسه» ِ `search` در سرور */
  const val SEARCH_MIN = 2

  fun of(m: ChatMessage, conv: ChatConversation?, perms: ChatPermissions, editWindowMs: Long?, now: Long): List<Action> {
    if (m.deleted || m.hidden || !perms.reply) return emptyList()
    val out = ArrayList<Action>(3)
    if (conv?.canSend != false) out += Action.REPLY
    if (m.mine && m.kind == "text" && (editWindowMs == null || now - m.createdAt <= editWindowMs)) out += Action.EDIT
    val pins = conv?.pinned.orEmpty()
    when {
      pins.any { it.messageId == m.id } -> out += Action.UNPIN
      pins.size < MAX_PINS -> out += Action.PIN
    }
    return out
  }

  /** پیام‌های سنجاق‌شدهٔ همین رشته، تازه‌ترین سنجاق اول؛ پیامِ پاک‌شده یا نرسیده نه */
  fun pinned(conv: ChatConversation?, messages: List<ChatMessage>): List<ChatMessage> {
    val byId = messages.associateBy { it.id }
    return conv?.pinned.orEmpty()
      .sortedWith(compareByDescending<ChatPin> { it.pinnedAt }.thenByDescending { it.seq })
      .mapNotNull { p -> byId[p.messageId]?.takeIf { !it.deleted && !it.hidden } }
  }

  /** پیامی که [m] به آن پاسخ داده (اگر در همین رشته رسیده) */
  fun quoted(m: ChatMessage, messages: List<ChatMessage>): ChatMessage? =
    m.replyTo?.let { id -> messages.firstOrNull { it.id == id } }

  /** چکیدهٔ یک خطیِ متنِ پیام برای نقلِ پاسخ و نوارِ سنجاق — پاک‌شده/رسانه ⇒ null (صفحه نامِ گونه را می‌گذارد) */
  fun snippet(m: ChatMessage, max: Int = 80): String? {
    if (m.deleted) return null
    val one = m.body.replace(Regex("\\s+"), " ").trim()
    if (one.isEmpty()) return null
    return if (one.length <= max) one else one.take(max - 1).trimEnd() + "…"
  }

  fun canSearch(q: String): Boolean = q.trim().length >= SEARCH_MIN
}
