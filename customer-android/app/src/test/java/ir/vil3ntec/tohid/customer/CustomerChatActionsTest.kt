package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.ChatDeleteScope
import ir.vil3ntec.tohid.core.shopper.ChatThread
import ir.vil3ntec.tohid.core.shopper.CustomerTicket
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperError
import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.ChatModel
import ir.vil3ntec.tohid.customer.ui.Load
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  گفت‌وگوی برنامهٔ خرید روی سرورِ جعلیِ کوچک (بی اندروید): «خواندم» فقط وقتی صفحه دیده می‌شود (CHAT-04) و
 *  «حذف برای من / برای همه» با پرسشِ تأیید، یک درخواست برای دوبار زدن و خطای روشن (CHAT-08) — همان `store/chat.js`.
 */
class CustomerChatActionsTest {

  private val now = 1_000_000L

  private class Msg(val id: String, val seq: Long, var rev: Long, val sender: String, val until: Long? = null) {
    var deleted = false
    var hidden = false
  }

  private val msgs = mutableListOf(
    Msg("m1", 1, 1, "shop"), Msg("m2", 2, 2, "customer", now + 60_000), Msg("m3", 3, 3, "shop"), Msg("m4", 4, 4, "customer", now + 60_000),
  )
  private var rev = 4L
  private var closed = false
  /** ok · offline · late · closed · auth */
  private var mode = "ok"
  private var myD = 0L
  private var myR = 0L
  private val seen = mutableListOf<ShopperRequest>()

  private fun conv() = """{"id":"sch_1","canSend":${!closed},"life":{"kind":"general","state":"${if (closed) "closed" else "open"}"},""" +
    """"shop":{"name":"گلستان"},"receipts":{"myDeliveredSeq":$myD,"myReadSeq":$myR}}"""

  private fun json(m: Msg): String {
    val base = """"id":"${m.id}","seq":${m.seq},"rev":${m.rev},"sender":"${m.sender}","createdAt":${now - 1000}"""
    return when {
      m.hidden -> """{$base,"hidden":true}"""
      m.deleted -> """{$base,"deleted":true,"deletedAt":$now}"""
      m.sender == "customer" -> """{$base,"kind":"text","body":"پیامِ ${m.id}","mine":true,"clientId":"c-${m.id}abcdefg","status":"sent","deleteUntil":${m.until}}"""
      else -> """{$base,"kind":"text","body":"پیامِ ${m.id}"}"""
    }
  }

  private fun body(r: ShopperRequest) = Json.parseToJsonElement(r.body!!).jsonObject

  private val http = ShopperHttp { r ->
    seen += r
    val p = r.path
    when {
      p.endsWith("/chat/conversations") && r.method == "POST" -> ShopperResponse(201, """{"conversation":${conv()}}""")
      p.contains("/messages?after=") -> {
        val after = p.substringAfter("after=").substringBefore('&').toLong()
        ShopperResponse(200, """{"conversation":${conv()},"messages":[${msgs.filter { it.rev > after }.joinToString(",") { json(it) }}],"cursor":$rev}""")
      }
      p.endsWith("/receipts") -> {
        myD = maxOf(myD, body(r)["deliveredSeq"]!!.jsonPrimitive.content.toLong())
        myR = maxOf(myR, body(r)["readSeq"]!!.jsonPrimitive.content.toLong())
        ShopperResponse(200, """{"conversation":${conv()}}""")
      }
      r.method == "DELETE" && p.contains("/messages/") -> {
        val id = p.substringAfter("/messages/").substringBefore('?')
        val all = p.endsWith("?for=all")
        when (mode) {
          "offline" -> throw java.io.IOException("offline")
          "late" -> ShopperResponse(403, """{"error":{"code":"delete_window_passed","message":"مهلتِ حذف برای همه گذشته است"}}""")
          "auth" -> ShopperResponse(401, """{"error":{"code":"verification_required"}}""")
          "closed" -> { closed = true; ShopperResponse(409, """{"error":{"code":"chat_closed"}}""") }
          else -> {
            val m = msgs.first { it.id == id }
            m.rev = ++rev
            if (all) m.deleted = true else m.hidden = true
            ShopperResponse(200, """{"message":${json(m)}}""")
          }
        }
      }
      else -> ShopperResponse(404, """{"error":{"code":"not_found"}}""")
    }
  }

  /** نخِ «io» ِ دستی: درخواست‌ها تا [run] منتظر می‌مانند ⇒ دوبار زدن وسطِ درخواستِ در راه آزموده می‌شود */
  private class Queue : CoroutineDispatcher() {
    private val tasks = ArrayDeque<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
    fun run() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
  }

  private var lost = 0
  private val ticket = CustomerTicket("c700000002@test.local", "v2.ticket", Long.MAX_VALUE)

  private fun model(io: CoroutineDispatcher = Dispatchers.Unconfined) = ChatModel(
    ShopperApi(http), object : ShopperKeyValue {
      val map = HashMap<String, String>()
      override fun get(key: String): String? = map[key]
      override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }, CoroutineScope(Dispatchers.Unconfined), io, { now }, { ticket }, { lost++ }, pollWaitMs = 0,
  )

  private fun receipts() = seen.filter { it.path.endsWith("/receipts") }.map { body(it)["deliveredSeq"]!!.jsonPrimitive.content.toLong() to body(it)["readSeq"]!!.jsonPrimitive.content.toLong() }
  private fun deletes() = seen.filter { it.method == "DELETE" }
  private fun ChatModel.t(): ChatThread = (thread as Load.Ready).value

  @Test fun readOnlyWhileTheScreenIsSeenDeliveredInTheBackgroundAndReadOnResume() {
    val m = model()
    m.visible(false)   //  Activity.onPause پیش از رسیدنِ پیام‌ها (صفحهٔ گفت‌وگو باز، برنامه پس)
    m.open("golestan", null, "احمد")
    assertEquals(listOf("m1", "m2", "m3", "m4"), m.t().messages.map { it.id })
    assertEquals("پس‌زمینه ⇒ فقط «رسید» تا آخرین پیامِ دکان", listOf(3L to 0L), receipts())
    assertEquals(0L, myR)
    m.visible(false)
    assertEquals("هنوز پس ⇒ درخواستِ دیگری نیست", 1, receipts().size)
    m.visible(true)   //  onResume
    assertEquals("پیش آمد ⇒ «خواندم» تا آخرین پیامِ دیده‌شده", listOf(3L to 0L, 3L to 3L), receipts())
    assertEquals(3L, m.t().conversation.receipts.myReadSeq)
    m.visible(true)
    assertEquals("خوانده‌شده تکرار نمی‌شود", 2, receipts().size)
    m.leave()
    m.visible(true)
    assertEquals("صفحه بسته ⇒ هیچ", 2, receipts().size)
  }

  @Test fun deleteForEveryoneAsksFirstSendsOneRequestOnDoubleTapAndShowsTheTombstone() {
    val io = Queue()
    val m = model(io)
    m.open("golestan", null, "")
    io.run()
    val t = m.t()
    val mine = t.messages.first { it.id == "m2" }
    val theirs = t.messages.first { it.id == "m1" }
    assertTrue(m.canDeleteForAll(mine))
    assertFalse("پیامِ دکان فقط «حذف برای من»", m.canDeleteForAll(theirs))

    m.actions(mine)
    assertNull("نخست فهرست، نه حذف", m.act?.ask)
    m.confirmDelete()
    assertTrue("بی پرسشِ تأیید هیچ درخواستی نمی‌رود", deletes().isEmpty())
    m.askDelete(ChatDeleteScope.ALL)
    assertEquals(ChatDeleteScope.ALL, m.act?.ask)
    m.confirmDelete()
    m.confirmDelete()   //  دوبار زدن
    assertTrue(m.act!!.busy)
    m.closeActions()
    assertTrue("تا پاسخ بسته نمی‌شود", m.act!!.busy)
    io.run()
    assertEquals(1, deletes().size)
    assertEquals("/api/store/golestan/chat/conversations/sch_1/messages/m2?for=all", deletes().single().path)
    assertEquals("v2.ticket", deletes().single().headers["X-Store-Ticket"])
    assertNull(m.act)
    assertEquals(ShopperText.chatDeleteAllDone, m.deletedNote)
    val tomb = m.t().messages.first { it.id == "m2" }
    assertTrue("سنگِ قبر، بی متن", tomb.deleted && tomb.body.isEmpty())
    assertFalse("سنگِ قبر «حذف برای همه» ندارد", m.canDeleteForAll(tomb))

    //  «حذف برای من» ِ پیامِ دکان ⇒ از رشته بیرون؛ پس از بازکردنِ دوباره هم نیست
    m.actions(theirs)
    m.askDelete(ChatDeleteScope.ME)
    m.confirmDelete()
    io.run()
    assertEquals("/api/store/golestan/chat/conversations/sch_1/messages/m1?for=me", deletes().last().path)
    assertEquals(listOf("m2", "m3", "m4"), m.t().messages.map { it.id })
    assertEquals(ShopperText.chatDeleteMeDone, m.deletedNote)
    m.open("golestan", null, "")
    io.run()
    assertEquals(listOf("m2", "m3", "m4"), m.t().messages.map { it.id })
  }

  @Test fun offlineExpiredClosedAndLostTicketEachShowAClearReason() {
    val m = model()
    m.open("golestan", null, "")
    val m4 = { m.t().messages.first { it.id == "m4" } }

    mode = "offline"
    m.actions(m4())
    m.askDelete(ChatDeleteScope.ALL)
    m.confirmDelete()
    assertEquals(ShopperText.chatDeleteOffline, m.act?.note)
    assertNull("خطا ⇒ دوباره فهرست", m.act?.ask)
    assertFalse(m.act!!.busy)
    assertTrue("بی‌اینترنت مهلت را نمی‌بندد", m.canDeleteForAll(m4()))

    mode = "late"
    m.askDelete(ChatDeleteScope.ALL)
    m.confirmDelete()
    assertEquals(ShopperText.error(ShopperError(403, "delete_window_passed")), m.act?.note)
    assertFalse("سرور گفت مهلت گذشت ⇒ گزینه می‌رود", m.canDeleteForAll(m4()))
    m.askDelete(ChatDeleteScope.ALL)
    assertNull("پرسشِ «برای همه» دیگر باز نمی‌شود", m.act?.ask)
    assertEquals(2, deletes().size)

    val m2 = { m.t().messages.first { it.id == "m2" } }
    mode = "closed"
    m.actions(m2())
    m.askDelete(ChatDeleteScope.ALL)
    m.confirmDelete()
    assertEquals(ShopperText.chatDeleteClosed, m.act?.note)
    assertEquals("رشتهٔ بسته ⇒ حالِ تازه از سرور", "closed", m.t().conversation.life.state)
    assertFalse(m.t().conversation.canSend)
    assertFalse("رشتهٔ بسته «حذف برای همه» ندارد (#907)", m.canDeleteForAll(m2()))
    m.askDelete(ChatDeleteScope.ALL)
    assertEquals(ShopperText.chatDeleteClosed, m.act?.note)
    assertEquals(3, deletes().size)

    mode = "ok"
    m.askDelete(ChatDeleteScope.ME)
    m.confirmDelete()
    assertFalse("رشتهٔ بسته: «حذف برای من» باز است", m.t().messages.any { it.id == "m2" })

    mode = "auth"
    m.actions(m4())
    m.askDelete(ChatDeleteScope.ME)
    m.confirmDelete()
    assertNull(m.act)
    assertEquals("بلیتِ منقضی ⇒ تأییدِ دوبارهٔ شماره", 1, lost)
    assertTrue(m.needVerify)
  }
}
