package ir.vil3ntec.tohid.ui.v3.online.chat

import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.chat.ChatMessage
import ir.vil3ntec.tohid.core.chat.ChatQueue
import ir.vil3ntec.tohid.core.chat.ChatReceipts
import ir.vil3ntec.tohid.core.chat.ChatThread
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline

/**
 *  حالتِ خالصِ گفت‌وگو (آزمون: `ChatModelTest`): پیش‌نمایشِ آخرین پیامِ صندوق، فیلترها، حرفِ
 *  آواتار و برچسبِ وضعیتِ هر حباب — همان قاعده‌های صفحهٔ پیشین (`ChatThread.statusOf` برای رسیدها).
 */
object ChatModel {
  val STATUSES = listOf("open", "closed", "all")
  val REPORT_REASONS = listOf("spam", "abuse", "fraud", "other")

  fun statusLabel(s: String): String = when (s) {
    "open" -> StrOnline.ctOpen
    "closed" -> StrOnline.ctClosed
    else -> StrOnline.all
  }

  /** «دکان: …» برای پیامِ خودمان؛ عکس/صدا/فایل با نامِ گونه؛ پاک‌شده و «هنوز پیامی نیست» */
  fun preview(c: ChatConversation): String {
    val m = c.lastMessage ?: return StrOnline.ctNoMessageYet
    if (m.deleted) return StrOnline.ctDeletedMsg
    val who = if (m.sender == "shop") StrOnline.ctShopPrefix else ""
    return who + (m.preview?.takeIf { it.isNotBlank() } ?: OnlineFmt.ctKind(m.kind))
  }

  /** گونهٔ آخرین پیام برای آیکنِ کنارِ پیش‌نمایش (متن ⇒ null) */
  fun previewKind(c: ChatConversation): String? = c.lastMessage?.takeIf { !it.deleted && it.kind != "text" }?.kind

  fun name(c: ChatConversation?): String = c?.customer?.name?.takeIf { it.isNotBlank() } ?: StrOnline.customer

  fun initial(c: ChatConversation): String = name(c).trim().take(1).ifEmpty { "•" }

  fun whenOf(c: ChatConversation): Long = c.lastMessageAt ?: c.updatedAt

  fun subtitle(c: ChatConversation?): String =
    listOfNotNull(c?.customer?.shownPhone?.ifBlank { null }?.let(OnlineFmt::ltr), c?.orderNo?.let(StrOnline::ctOrderChip)).joinToString(" · ")

  /** «۱:۰۵» */
  fun mmss(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
  }

  /** برچسبِ رسیدِ پیامِ خودم (فقط از رسیدهای واقعیِ طرف) */
  fun receipt(m: ChatMessage, receipts: ChatReceipts?): String = OnlineFmt.ctState(ChatThread.statusOf(m, receipts))

  fun isRead(m: ChatMessage, receipts: ChatReceipts?): Boolean = ChatThread.statusOf(m, receipts) == "read"

  /** پیامِ در صف: برچسب، و آیا «دوباره»/«لغو» دارد */
  fun queuedLabel(state: String, error: String): String =
    OnlineFmt.ctState(state) + if (state == ChatQueue.FAILED && error.isNotBlank()) " — $error" else ""

  fun canRetry(state: String): Boolean = state != ChatQueue.SENDING && state != ChatQueue.QUEUED

  fun canCancel(state: String): Boolean = state != ChatQueue.SENDING
}
