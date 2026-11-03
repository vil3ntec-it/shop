package ir.vil3ntec.tohid.core.chat

import ir.vil3ntec.tohid.core.chat.ChatActions.Action
import ir.vil3ntec.tohid.core.net.ApiJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CHAT-14 — کارهای یک پیام در گفت‌وگوی برنامهٔ مدیریت (پاسخ، ویرایش، سنجاق) و جست‌وجو، با همان قاعده‌های سرور
 *  (`shop-chat.js`، `shop-chat-pins.js`، آزمونِ سرور `shop-chat-pins.test.js`).
 */
class ChatActionsTest {

  private val now = 10_000_000L
  private val window = 15 * 60_000L
  private val reply = ChatPermissions(reply = true)
  private val conv = ChatConversation(id = "c1")

  private fun msg(id: String, mine: Boolean = false, kind: String = "text", body: String = "سلام", at: Long = now - 60_000, deleted: Boolean = false) =
    ChatMessage(id = id, seq = id.filter(Char::isDigit).toLongOrNull() ?: 0, sender = if (mine) "shop" else "customer", mine = mine, kind = kind, body = body, createdAt = at, deleted = deleted)

  @Test fun customerMessageCanBeRepliedToAndPinnedButNotEdited() {
    assertEquals(listOf(Action.REPLY, Action.PIN), ChatActions.of(msg("m1"), conv, reply, window, now))
  }

  @Test fun ownTextInsideWindowCanBeEditedOutsideNot() {
    assertEquals(listOf(Action.REPLY, Action.EDIT, Action.PIN), ChatActions.of(msg("m1", mine = true), conv, reply, window, now))
    val old = msg("m2", mine = true, at = now - window - 1)
    assertFalse(Action.EDIT in ChatActions.of(old, conv, reply, window, now))
    //  سرورِ پیشین مهلت نمی‌فرستد ⇒ دکمه هست و سرور خودش می‌سنجد
    assertTrue(Action.EDIT in ChatActions.of(old, conv, reply, null, now))
    //  عکس و صدای خودم ویرایش نمی‌شوند
    assertFalse(Action.EDIT in ChatActions.of(msg("m3", mine = true, kind = "image"), conv, reply, window, now))
  }

  @Test fun deletedOrNoReplyPermissionHasNoActions() {
    assertEquals(emptyList<Action>(), ChatActions.of(msg("m1", deleted = true), conv, reply, window, now))
    assertEquals(emptyList<Action>(), ChatActions.of(msg("m1"), conv, ChatPermissions(reply = false), window, now))
  }

  @Test fun closedForSendingStillPinsButNoReply() {
    assertEquals(listOf(Action.PIN), ChatActions.of(msg("m1"), conv.copy(canSend = false), reply, window, now))
  }

  @Test fun pinnedShowsUnpinAndCapStopsNewPins() {
    val one = conv.copy(pinned = listOf(ChatPin("m1", 1, 5)))
    assertEquals(listOf(Action.REPLY, Action.UNPIN), ChatActions.of(msg("m1"), one, reply, window, now))
    val full = conv.copy(pinned = (1..ChatActions.MAX_PINS).map { ChatPin("p$it", it.toLong(), it.toLong()) })
    assertEquals(listOf(Action.REPLY), ChatActions.of(msg("m9"), full, reply, window, now))
    assertEquals(listOf(Action.REPLY, Action.UNPIN), ChatActions.of(msg("p3"), full, reply, window, now))
  }

  @Test fun pinnedListNewestFirstWithoutDeletedOrMissing() {
    val list = listOf(msg("m1"), msg("m2"), msg("m3", deleted = true))
    val c = conv.copy(pinned = listOf(ChatPin("m1", 1, 100), ChatPin("m2", 2, 300), ChatPin("m3", 3, 400), ChatPin("gone", 9, 500)))
    assertEquals(listOf("m2", "m1"), ChatActions.pinned(c, list).map { it.id })
    assertEquals(emptyList<ChatMessage>(), ChatActions.pinned(null, list))
  }

  @Test fun quotedAndSnippet() {
    val asked = msg("m1", body = "برنجِ   سیلا\nدارید؟")
    val answer = msg("m2", mine = true, body = "بله").copy(replyTo = "m1")
    assertEquals(asked, ChatActions.quoted(answer, listOf(asked, answer)))
    assertNull(ChatActions.quoted(answer.copy(replyTo = "gone"), listOf(asked)))
    assertNull(ChatActions.quoted(asked, listOf(asked)))
    assertEquals("برنجِ سیلا دارید؟", ChatActions.snippet(asked))
    assertEquals("ابجد…", ChatActions.snippet(msg("m4", body = "ابجدهوز"), max = 5))
    assertNull(ChatActions.snippet(msg("m5", deleted = true)))
    assertNull(ChatActions.snippet(msg("m6", kind = "image", body = " ")))
  }

  @Test fun searchNeedsTwoLetters() {
    assertFalse(ChatActions.canSearch(" ب "))
    assertTrue(ChatActions.canSearch("بر"))
  }

  @Test fun replyToTravelsThroughTheDiskQueue() {
    var text: String? = null
    val ser = ListSerializer(OutboxItem.serializer())
    val json = Json { ignoreUnknownKeys = true }
    val q = ChatQueue({ text?.let { json.decodeFromString(ser, it) } }, { text = json.encodeToString(ser, it) }, now = { 1_000L }, rand = { 0.0 })
    q.add("c1", body = "بله", replyTo = "m1")
    q.add("c1", body = "بی پاسخ", replyTo = " ")
    val back = ChatQueue({ text?.let { json.decodeFromString(ser, it) } }, { text = json.encodeToString(ser, it) }, now = { 1_000L }, rand = { 0.0 })
    assertEquals(listOf("m1", null), back.forConv("c1").map { it.replyTo })
  }

  @Test fun serverShapesDecode() {
    val c = ApiJson.format.decodeFromString<ChatConversation>(
      """{"id":"c1","pinned":[{"messageId":"m1","seq":1,"pinnedAt":5}],"unknown":1}""",
    )
    assertEquals(listOf(ChatPin("m1", 1, 5)), c.pinned)
    val r = ApiJson.format.decodeFromString<ChatSearchResult>(
      """{"results":[{"conversationId":"c1","id":"m2","seq":2,"sender":"shop","kind":"text","body":"بیست کیلو","createdAt":9,"senderUserId":"u"}]}""",
    )
    assertEquals(listOf(ChatSearchHit("c1", "m2", 2, "shop", "text", "بیست کیلو", 9)), r.results)
    assertEquals(900_000L, ApiJson.format.decodeFromString<ChatLimits>("""{"editWindowMs":900000}""").editWindowMs)
    assertNull(ApiJson.format.decodeFromString<ChatLimits>("{}").editWindowMs)
  }
}
