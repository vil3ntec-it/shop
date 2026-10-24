package ir.vil3ntec.tohid.ui.v3.online.chat

import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.chat.ChatCustomer
import ir.vil3ntec.tohid.core.chat.ChatLast
import ir.vil3ntec.tohid.core.chat.ChatMessage
import ir.vil3ntec.tohid.core.chat.ChatQueue
import ir.vil3ntec.tohid.core.chat.ChatReceipts
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOnline
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** پیش‌نمایشِ صندوق، نام و زیرنویس، رسیدِ واقعی و کارهای صفِ دستگاه */
class ChatModelTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  private val conv = ChatConversation("c1", customer = ChatCustomer(name = "مریم", phoneMasked = "07•• ••• 456"), orderNo = 1042)

  @Test
  fun `پیش‌نمایشِ آخرین پیام`() {
    assertEquals(StrOnline.ctNoMessageYet, ChatModel.preview(conv))
    assertEquals("سلام", ChatModel.preview(conv.copy(lastMessage = ChatLast(sender = "customer", preview = "سلام"))))
    assertTrue(ChatModel.preview(conv.copy(lastMessage = ChatLast(sender = "shop", preview = "بله"))).startsWith(StrOnline.ctShopPrefix))
    assertEquals(StrOnline.ctKind("image"), ChatModel.preview(conv.copy(lastMessage = ChatLast(kind = "image", sender = "customer"))))
    assertEquals(StrOnline.ctDeletedMsg, ChatModel.preview(conv.copy(lastMessage = ChatLast(sender = "customer", deleted = true))))
    assertEquals("image", ChatModel.previewKind(conv.copy(lastMessage = ChatLast(kind = "image"))))
    assertNull(ChatModel.previewKind(conv.copy(lastMessage = ChatLast(kind = "text"))))
  }

  @Test
  fun `نام و زیرنویس و حرفِ نخست`() {
    assertEquals("مریم", ChatModel.name(conv))
    assertEquals("م", ChatModel.initial(conv))
    assertEquals(StrOnline.customer, ChatModel.name(conv.copy(customer = ChatCustomer())))
    assertEquals(StrOnline.customer, ChatModel.name(null))
    assertEquals("07•• ••• 456 · " + StrOnline.ctOrderChip(1042), ChatModel.subtitle(conv))
    //  شمارهٔ کامل فقط وقتی سرور داد
    assertTrue(ChatModel.subtitle(conv.copy(customer = conv.customer.copy(phone = "0700000456"))).startsWith("0700000456"))
    assertEquals("", ChatModel.subtitle(null))
  }

  @Test
  fun `رسید فقط از رسیدهای طرف`() {
    val m = ChatMessage("m", seq = 5, sender = "shop", mine = true)
    assertEquals(StrOnline.ctState("sent"), ChatModel.receipt(m, ChatReceipts(peerDeliveredSeq = 4)))
    assertEquals(StrOnline.ctState("delivered"), ChatModel.receipt(m, ChatReceipts(peerDeliveredSeq = 5)))
    assertTrue(ChatModel.isRead(m, ChatReceipts(peerDeliveredSeq = 6, peerReadSeq = 6)))
    assertFalse(ChatModel.isRead(m, ChatReceipts(myReadSeq = 9)))
  }

  @Test
  fun `صفِ دستگاه و زمانِ صدا`() {
    assertEquals("1:05", ChatModel.mmss(65_400))
    assertEquals("0:00", ChatModel.mmss(0))
    assertFalse(ChatModel.canRetry(ChatQueue.SENDING))
    assertFalse(ChatModel.canRetry(ChatQueue.QUEUED))
    assertTrue(ChatModel.canRetry(ChatQueue.FAILED))
    assertFalse(ChatModel.canCancel(ChatQueue.SENDING))
    assertTrue(ChatModel.canCancel(ChatQueue.WAITING))
    assertEquals(StrOnline.ctState(ChatQueue.FAILED) + " — too_large", ChatModel.queuedLabel(ChatQueue.FAILED, "too_large"))
    assertEquals(StrOnline.ctState(ChatQueue.WAITING), ChatModel.queuedLabel(ChatQueue.WAITING, "x"))
    I18n.source = { Lang.EN }
    assertEquals("Waiting for internet", ChatModel.queuedLabel(ChatQueue.WAITING, ""))
    assertTrue(ChatModel.REPORT_REASONS.all { StrOnline.ctReason(it).none { c -> c in '؀'..'ۿ' } })
  }
}
