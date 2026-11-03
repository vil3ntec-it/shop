package ir.vil3ntec.tohid.core.chat

import kotlinx.serialization.Serializable

/*
 *  چتِ مشتری ↔ دکان — شکلِ پاسخ‌های `/api/shop/chat` (v3 §۳۳؛ سرور: PR #259،
 *  `server/src/lib/shop-chat.js`). فقط آن‌چه برنامه می‌خواند؛ کلیدِ ناشناخته
 *  نادیده گرفته می‌شود (`ApiJson.format`).
 */

@Serializable
data class ChatLimits(
  val bodyMax: Int = 4000,
  val imageMax: Long = 1536L * 1024,
  val thumbMax: Long = 64L * 1024,
  val audioMax: Long = 3072L * 1024,
  /** مهلتِ ویرایشِ پیامِ خود (سرور: `CHAT_EDIT_WINDOW_MIN`)؛ سرورِ پیشین نمی‌فرستد ⇒ null (سرور خودش می‌سنجد) */
  val editWindowMs: Long? = null,
)

@Serializable
data class ChatPermissions(
  val reply: Boolean = false,
  val moderate: Boolean = false,
  /** شمارهٔ کاملِ مشتری (`orders.contact`) — وگرنه فقط شمارهٔ پوشیده */
  val contact: Boolean = false,
)

/** `GET /config` — ⛔ `enabled = false` یعنی بخش اصلاً دیده نمی‌شود. */
@Serializable
data class ChatConfig(
  val enabled: Boolean = false,
  val limits: ChatLimits = ChatLimits(),
  val reasons: List<String> = listOf("spam", "abuse", "fraud", "other"),
  val permissions: ChatPermissions = ChatPermissions(),
)

@Serializable
data class ChatReceipts(
  val peerDeliveredSeq: Long = 0,
  val peerReadSeq: Long = 0,
  val myDeliveredSeq: Long = 0,
  val myReadSeq: Long = 0,
)

@Serializable
data class ChatCustomer(
  val name: String = "",
  val phoneMasked: String = "",
  /** فقط وقتی سرور برای این نقش داده (`orders.contact`) */
  val phone: String? = null,
) {
  val shownPhone: String get() = phone?.takeIf { it.isNotBlank() } ?: phoneMasked
}

@Serializable
data class ChatLast(
  val kind: String = "text",
  val sender: String = "",
  val at: Long = 0,
  val preview: String? = null,
  val deleted: Boolean = false,
)

/**
 *  چرخهٔ عمرِ رشته (`lib/shop-chat-life.js`، یک رشته برای هر سفارش): `state` = open | closed | reopened؛
 *  `kind` = order | general؛ `reason` ِ بستن = completed | ended | idle. «بسته» یعنی هر دو طرف فقط می‌خوانند
 *  (سرور ۴۰۹ `chat_closed` می‌دهد). `issueUntil` = پایانِ مهلتِ «مشکل دارم» ِ مشتری.
 */
@Serializable
data class ChatLife(
  val kind: String = "order",
  val state: String = "open",
  val reason: String = "",
  val closedAt: Long? = null,
  val issueUntil: Long? = null,
  val reopenedAt: Long? = null,
  val sessionAt: Long? = null,
) {
  val closed: Boolean get() = state == "closed"
  val reopened: Boolean get() = state == "reopened"
}

@Serializable
data class ChatConversation(
  val id: String,
  val status: String = "open",
  val seq: Long = 0,
  val rev: Long = 0,
  val unread: Int = 0,
  val orderNo: Long? = null,
  val orderId: String? = null,
  val lastMessage: ChatLast? = null,
  val lastMessageAt: Long? = null,
  val createdAt: Long = 0,
  val updatedAt: Long = 0,
  val customer: ChatCustomer = ChatCustomer(),
  val blockedCustomer: Boolean = false,
  val customerBlockedShop: Boolean = false,
  val canSend: Boolean = true,
  val receipts: ChatReceipts = ChatReceipts(),
  val life: ChatLife = ChatLife(),
  /** سنجاق‌های طرفِ دکان (CHAT-14، مهاجرتِ ۱۴۱) — همهٔ کارمندانِ همین دکان می‌بینند، مشتری نه */
  val pinned: List<ChatPin> = emptyList(),
)

@Serializable
data class ChatPin(
  val messageId: String,
  val seq: Long = 0,
  val pinnedAt: Long = 0,
)

@Serializable
data class ChatMeta(
  val durationMs: Long? = null,
  val width: Int? = null,
  val height: Int? = null,
) {
  val isEmpty: Boolean get() = durationMs == null && width == null && height == null
}

@Serializable
data class ChatMediaRef(
  val id: String,
  val mime: String? = null,
  val size: Long? = null,
  val thumbId: String? = null,
  val thumbSize: Long? = null,
)

@Serializable
data class ChatMessage(
  val id: String,
  val seq: Long = 0,
  val rev: Long = 0,
  val sender: String = "",
  val createdAt: Long = 0,
  val hidden: Boolean = false,
  val deleted: Boolean = false,
  val kind: String = "text",
  val body: String = "",
  val replyTo: String? = null,
  val meta: ChatMeta = ChatMeta(),
  val editedAt: Long? = null,
  val mine: Boolean = false,
  val media: ChatMediaRef? = null,
  /** فقط برای پیامِ طرفِ خودِ بیننده */
  val clientId: String? = null,
  val status: String? = null,
)

@Serializable
data class ChatChanges(
  val conversation: ChatConversation? = null,
  val messages: List<ChatMessage> = emptyList(),
  val cursor: Long = 0,
  val hasMore: Boolean = false,
)

@Serializable
data class ChatList(
  val conversations: List<ChatConversation> = emptyList(),
  val next: String? = null,
)

/** یک یافتهٔ `GET /search` (CHAT-14): پیامِ مجازِ همین دکان — پاک‌شده و «حذف برای من» نمی‌آیند */
@Serializable
data class ChatSearchHit(
  val conversationId: String,
  val id: String,
  val seq: Long = 0,
  val sender: String = "",
  val kind: String = "text",
  val body: String = "",
  val createdAt: Long = 0,
)

@Serializable
data class ChatSearchResult(
  val results: List<ChatSearchHit> = emptyList(),
)
