package ir.vil3ntec.tohid.ui.v3.online.chat

import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.chat.ChatLast
import ir.vil3ntec.tohid.core.chat.ChatLife
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.ui.theme.V3Tone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** چرخهٔ عمرِ رشته در برنامهٔ دکان: «بسته شد»، «مشکل دارم» و رشته‌های پیشین (سرور: `lib/shop-chat-life.js`) */
class ChatLifeModelTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  private val now = 1_800_000_000_000L
  private val day = 24L * 3600 * 1000

  @Test
  fun `رشتهٔ باز پیامی ندارد`() {
    assertNull(ChatLifeModel.banner(ChatLife()))
  }

  @Test
  fun `بسته شدهٔ سفارش — تا مهلت تاریخ را می‌گوید، پس از آن «مهلت گذشت»`() {
    val (text, tone) = ChatLifeModel.banner(ChatLife(kind = "order", state = "closed", reason = "completed", issueUntil = now + 3 * day)) { "7/25 14:05" }!!
    assertEquals(V3Tone.WARNING, tone)
    assertTrue(text.contains("7/25 14:05") && text.contains("مشکل دارم"))
    //  پس از مهلت سرور `issueUntil` نمی‌دهد (`viewLife`)
    val late = ChatLifeModel.banner(ChatLife(kind = "order", state = "closed", issueUntil = null)) { "x" }!!.first
    assertTrue(late.contains("گذشته") && !late.contains("x"))
  }

  @Test
  fun `عمومیِ بی‌کار و دوباره‌باز`() {
    assertEquals(StrOrdersHub.closedIdle, ChatLifeModel.banner(ChatLife(kind = "general", state = "closed", reason = "idle"))!!.first)
    assertEquals(StrOrdersHub.reopenedNote, ChatLifeModel.banner(ChatLife(state = "reopened", reopenedAt = now))!!.first)
  }

  @Test
  fun `رشته‌های پیشین — نام، وضع و پیش‌نمایش`() {
    val c = ChatConversation(id = "c1", orderNo = 12, life = ChatLife(state = "closed"), lastMessage = ChatLast(preview = "ممنون"))
    assertEquals("سفارشِ #12", ChatLifeModel.title(c))
    assertEquals("بسته شد · ممنون", ChatLifeModel.subtitle(c))
    assertEquals(StrOrdersHub.generalThread, ChatLifeModel.title(ChatConversation(id = "g")))
    assertEquals(StrOrdersHub.earlierEmpty, ChatLifeModel.subtitle(ChatConversation(id = "g")))
  }

  @Test
  fun `پاسخِ سرور «life» را می‌خواند و بی آن باز است (سرورِ کهنه)`() {
    val json = """{"id":"c1","status":"closed","canSend":false,"life":{"kind":"order","state":"closed","reason":"completed","closedAt":1,"issueUntil":2,"reopenedAt":null,"sessionAt":null,"canIssue":false,"canRestart":false}}"""
    val c = ApiJson.format.decodeFromString(ChatConversation.serializer(), json)
    assertTrue(c.life.closed)
    assertEquals(2L, c.life.issueUntil)
    assertFalse(c.canSend)
    assertFalse(ApiJson.format.decodeFromString(ChatConversation.serializer(), """{"id":"c2"}""").life.closed)
  }
}
