package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.ChatDeleteScope
import ir.vil3ntec.tohid.core.shopper.ChatPhoto
import ir.vil3ntec.tohid.core.shopper.ChatThread
import ir.vil3ntec.tohid.core.shopper.CustomerTicket
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperError
import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.ChatFocus
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
 *  CHAT-14 ِ برنامهٔ خرید روی سرورِ جعلیِ کوچک (بی اندروید) — همان `store/chat-acts.js` و دستهٔ ۴۲ ِ `store/chat.js`:
 *  پاسخ از همان صف، ویرایش بی صف با `POST …/edit`، سنجاق و جست‌وجو؛ و ⛔ پیوندِ رشته: پیام همیشه به رشته‌ای می‌رود که در
 *  آن نوشته شد، پاسخِ دیرِ گشودنِ پیشین نمی‌نشیند، و خطا یا نتیجهٔ دیرِ رشتهٔ پیشین روی رشتهٔ تازه نمی‌آید.
 */
class CustomerChatActsTest {

  private val now = 1_000_000L

  private class Msg(val id: String, val conv: String, val seq: Long, var rev: Long, val sender: String, var body: String) {
    var edited = false
    var deleted = false
    var hidden = false
  }

  private val msgs = mutableListOf(
    Msg("m1", "sch_3", 1, 1, "shop", "برنج تازه رسید"), Msg("m2", "sch_3", 2, 2, "customer", "پیامِ m2"),
    Msg("m9", "sch_4", 1, 3, "shop", "پیامِ سفارشِ چهار"),
  )
  private var rev = 3L
  private val pins = mutableMapOf<String, MutableList<String>>()
  /** ok · offline · late · limit · auth */
  private var mode = "ok"
  private val seen = mutableListOf<ShopperRequest>()

  private fun pinJson(id: String) = """{"messageId":"$id","seq":1,"pinnedAt":5}"""

  private fun conv(id: String) = """{"id":"$id","canSend":true,"life":{"kind":"order","state":"open"},"shop":{"name":"گلستان"},""" +
    """"pinned":[""" + pins[id].orEmpty().joinToString(",") { pinJson(it) } + "]}"

  private fun json(m: Msg): String {
    val head = """"id":"${m.id}","seq":${m.seq},"rev":${m.rev},"sender":"${m.sender}","createdAt":${now - 1000}"""
    if (m.hidden) return """{$head,"hidden":true}"""
    if (m.deleted) return """{$head,"deleted":true}"""
    val base = """$head,"kind":"text","body":"${m.body}""""
    val ed = if (m.edited) ""","editedAt":$now""" else ""
    return if (m.sender == "customer") {
      """{$base$ed,"mine":true,"clientId":"c-${m.id}abcdefg","status":"sent","editUntil":${now + 60_000},"deleteUntil":${now + 60_000}}"""
    } else {
      "{$base$ed}"
    }
  }

  private fun body(r: ShopperRequest) = Json.parseToJsonElement(r.body!!).jsonObject
  private fun err(status: Int, code: String) = ShopperResponse(status, """{"error":{"code":"$code","message":"پیامِ سرور"}}""")

  private val http = ShopperHttp { r ->
    seen += r
    val p = r.path
    val cid = p.substringAfter("/conversations/", "").substringBefore('/').substringBefore('?')
    when {
      p.endsWith("/chat/conversations") && r.method == "POST" -> {
        val no = body(r)["orderNo"]?.jsonPrimitive?.content ?: "0"
        ShopperResponse(201, """{"conversation":${conv("sch_$no")},"limits":{"voice":false}}""")
      }
      p.contains("/messages?after=") -> {
        val after = p.substringAfter("after=").substringBefore('&').toLong()
        ShopperResponse(200, """{"conversation":${conv(cid)},"messages":[${msgs.filter { it.conv == cid && it.rev > after }.joinToString(",") { json(it) }}],"cursor":$rev}""")
      }
      p.endsWith("/receipts") -> ShopperResponse(200, """{"conversation":${conv(cid)}}""")
      p.endsWith("/edit") -> when (mode) {
        "offline" -> throw java.io.IOException("offline")
        "late" -> err(403, "edit_window_passed")
        "auth" -> err(401, "verification_required")
        else -> {
          val m = msgs.first { it.id == p.substringAfter("/messages/").substringBefore('/') }
          m.body = body(r)["body"]!!.jsonPrimitive.content
          m.edited = true
          m.rev = ++rev
          ShopperResponse(200, """{"message":${json(m)}}""")
        }
      }
      p.contains("/pins/") -> {
        if (mode == "limit") return@ShopperHttp err(409, "pin_limit")
        if (mode == "offline") throw java.io.IOException("offline")
        val mid = p.substringAfter("/pins/")
        val list = pins.getOrPut(cid) { mutableListOf() }
        if (r.method == "PUT") list += mid else list -= mid
        ShopperResponse(200, """{"conversation":${conv(cid)}}""")
      }
      p.contains("/chat/search") -> ShopperResponse(200, """{"results":[{"conversationId":"sch_3","id":"m1","seq":1,"sender":"shop","kind":"text","body":"برنج تازه رسید","createdAt":1},""" +
        """{"conversationId":"sch_4","id":"m9","seq":1,"sender":"shop","kind":"text","body":"پیامِ سفارشِ چهار","createdAt":2}]}""")
      r.method == "DELETE" && p.contains("/messages/") -> {
        val mm = msgs.first { it.id == p.substringAfter("/messages/").substringBefore('?') }
        mm.rev = ++rev
        if (p.endsWith("?for=all")) mm.deleted = true else mm.hidden = true
        ShopperResponse(200, """{"message":${json(mm)}}""")
      }
      p.endsWith("/messages") && r.method == "POST" -> {
        val b = body(r)
        val m = Msg("n" + (++rev), cid, 10 + rev, rev, "customer", b["body"]!!.jsonPrimitive.content)
        msgs += m
        ShopperResponse(201, """{"message":${json(m).replace("c-${m.id}abcdefg", b["clientId"]!!.jsonPrimitive.content)}}""")
      }
      else -> err(404, "not_found")
    }
  }

  /** نخِ «io» ِ دستی: درخواست‌ها تا [run] منتظر می‌مانند ⇒ پاسخِ دیر و رشتهٔ تازه در این میان آزموده می‌شود */
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

  private fun ChatModel.t(): ChatThread = (thread as Load.Ready).value
  private fun sends() = seen.filter { it.method == "POST" && it.path.endsWith("/messages") }

  @Test fun lateOpenOfThePreviousThreadNeverLandsAndTheDraftNeverMovesToAnotherThread() {
    val io = Queue()
    val m = model(io)
    m.open("golestan", 3, "")
    m.open("golestan", 4, "")   //  پیش از پاسخِ گشودنِ سفارشِ ۳
    assertTrue("تا پاسخ هیچ رشته‌ای نیست (نوشتن، عکس، صدا خاموش)", m.thread is Load.Busy)
    m.draft = "نباید جایی برود"
    m.send()
    assertTrue(m.outbox.isEmpty())
    io.run()
    assertEquals("پاسخِ دیرِ رشتهٔ ۳ نمی‌نشیند", "sch_4", m.t().conversation.id)
    assertEquals(listOf("m9"), m.t().messages.map { it.id })
    assertEquals("", m.draft)

    //  متنِ نیمه‌نوشتهٔ رشتهٔ ۴ و «پاسخ به» ِ آن به رشتهٔ ۳ نمی‌روند
    m.draft = "این برای سفارشِ چهار است"
    m.acts.reply(m.t().messages.single())
    assertEquals("m9", m.compose.replyTo)
    m.open("golestan", 3, "")
    assertNull("گشودنِ دیگر ⇒ پاسخ همان دم رها", m.compose.replyTo)
    io.run()
    assertEquals("sch_3", m.t().conversation.id)
    assertEquals("متنِ رشتهٔ دیگر این‌جا نمی‌آید", "", m.draft)
    m.send()
    assertTrue(sends().isEmpty())

    //  نوشته در رشتهٔ ۳ ⇒ فقط به رشتهٔ ۳، حتی اگر پیش از رفتن رشتهٔ دیگری باز شود
    m.draft = "برنج دارید؟"
    m.send()
    assertEquals("sch_3", m.outbox.single().convId)
    m.open("golestan", 4, "")
    io.run()
    assertEquals("/api/store/golestan/chat/conversations/sch_3/messages", sends().single().path)
    assertEquals("برنج دارید؟", body(sends().single())["body"]!!.jsonPrimitive.content)
    assertTrue(m.outbox.isEmpty())

    //  گشودنِ دوبارهٔ همان رشته نوشته را نگه می‌دارد (همان `draftOf` ِ وب)
    m.draft = "نیمه‌نوشته"
    m.open("golestan", 4, "")
    io.run()
    assertEquals("نیمه‌نوشته", m.draft)
  }

  @Test fun lateResultsOfThePreviousThreadNeverLandOnTheNewOne() {
    val io = Queue()
    val m = model(io)
    m.open("golestan", 3, "")
    io.run()
    val mine = m.t().messages.first { it.id == "m2" }
    m.acts.edit(mine)
    m.draft = "ویرایش"
    mode = "late"
    m.submit()   //  در راه
    m.acts.pin(m.t().messages.first { it.id == "m1" }, true)   //  در راه
    m.acts.query = "برنج"
    m.acts.find()   //  در راه
    m.open("golestan", 4, "")
    io.run()
    assertEquals("sch_4", m.t().conversation.id)
    assertNull("خطای دیرِ ویرایشِ رشتهٔ ۳ این‌جا نمی‌نشیند", m.acts.note)
    assertNull(m.compose.editing)
    assertNull("نتیجهٔ دیرِ جست‌وجوی رشتهٔ ۳ نه", m.acts.hits)
    assertTrue("سنجاقِ دیرِ رشتهٔ ۳ نوارِ رشتهٔ ۴ را نمی‌سازد", m.t().conversation.pinned.isEmpty())
    assertFalse(m.acts.saving)

    //  عکسی که برای رشتهٔ ۳ برگزیده شد به رشتهٔ ۴ نمی‌رود (همان `phDrop` ِ وب)
    m.open("golestan", 3, "")
    io.run()
    val forConv = m.photoStart()
    assertEquals("sch_3", forConv)
    m.open("golestan", 4, "")
    io.run()
    m.sendPhoto(ChatPhoto(byteArrayOf(1, 2, 3), null, 10, 10), forConv)
    assertTrue(m.outbox.isEmpty())
    assertFalse(m.preparing)
  }

  @Test fun replyEditPinAndSearchMirrorTheWeb() {
    val m = model()
    m.open("golestan", 3, "")
    val shopMsg = m.t().messages.first { it.id == "m1" }
    val mine = m.t().messages.first { it.id == "m2" }

    //  پاسخ: از فهرستِ کارها، با `replyTo` در همان صف
    m.actions(shopMsg)
    assertTrue(m.acts.canReply(shopMsg))
    m.acts.reply(shopMsg)
    assertNull("نوارِ کارها بسته", m.act)
    assertEquals(ChatFocus(null), m.focus)
    m.draft = "پاسخِ من"
    m.submit()
    assertEquals("m1", body(sends().single())["replyTo"]!!.jsonPrimitive.content)
    assertNull(m.compose.replyTo)
    assertEquals("", m.draft)

    //  ویرایش: کادر همان متن؛ «ذخیره» ⇒ POST …/edit، بی پیامِ تازه
    assertFalse("پیامِ دکان ویرایش نمی‌شود", m.acts.canEdit(shopMsg))
    assertTrue(m.acts.canEdit(mine))
    m.acts.edit(mine)
    assertEquals("پیامِ m2", m.draft)
    m.draft = "پیامِ ویرایش‌شده"
    m.submit()
    val edit = seen.last()
    assertEquals("POST" to "/api/store/golestan/chat/conversations/sch_3/messages/m2/edit", edit.method to edit.path)
    assertEquals("پیامِ ویرایش‌شده", body(edit)["body"]!!.jsonPrimitive.content)
    assertEquals(1, sends().size)
    assertEquals("پیامِ ویرایش‌شده", m.t().messages.first { it.id == "m2" }.body)
    assertTrue(m.t().messages.first { it.id == "m2" }.editedAt != null)
    assertEquals(ShopperText.chatEditOk, m.acts.note)
    assertNull(m.compose.editing)
    assertEquals("", m.draft)

    //  بی‌اینترنت ⇒ متن در کادر، ویرایش باز؛ مهلتِ سرور گذشت ⇒ ویرایش بسته، متن می‌ماند، «ویرایش» دیگر نیست
    m.acts.edit(m.t().messages.first { it.id == "m2" })
    m.draft = "دوباره"
    mode = "offline"
    m.submit()
    assertEquals(ShopperText.chatEditOff, m.acts.note)
    assertEquals("m2", m.compose.editing)
    assertEquals("دوباره", m.draft)
    mode = "late"
    m.submit()
    assertEquals(ShopperText.error(ShopperError(403, "edit_window_passed")), m.acts.note)
    assertNull(m.compose.editing)
    assertEquals("دوباره", m.draft)
    assertFalse("سرور گفت گذشته ⇒ حتی با ساعتِ گوشی", m.acts.canEdit(m.t().messages.first { it.id == "m2" }))
    m.draft = ""

    //  سنجاق: فقط برای خودم؛ نوار از `pinned` ِ پاسخِ سرور
    mode = "ok"
    m.acts.pin(shopMsg, true)
    assertEquals("PUT" to "/api/store/golestan/chat/conversations/sch_3/pins/m1", seen.last().method to seen.last().path)
    assertTrue(m.acts.pinned(shopMsg))
    assertEquals(ShopperText.chatPinOk, m.acts.note)
    m.acts.go("m1")
    assertEquals("m1", m.acts.jump)
    m.acts.jumped()
    assertFalse("پیامی که در این صفحه نیست", m.acts.go("zz"))
    assertEquals(ShopperText.chatFar, m.acts.note)
    mode = "limit"
    m.acts.pin(mine, true)
    assertEquals(ShopperText.error(ShopperError(409, "pin_limit")), m.acts.note)
    mode = "ok"
    m.acts.pin(shopMsg, false)
    assertEquals("DELETE", seen.last().method)
    assertFalse(m.acts.pinned(shopMsg))

    //  جست‌وجو: کوتاه ⇒ بی درخواست؛ نتیجهٔ همین رشته ⇒ رفتن به حباب، رشتهٔ دیگر فقط خواندنی
    val before = seen.size
    m.acts.query = " ب "
    m.acts.find()
    assertEquals(before, seen.size)
    assertEquals(ShopperText.error(ShopperError(400, "query_too_short")), m.acts.findNote)
    m.acts.toggleFind()
    m.acts.query = "برنج"
    m.acts.find()
    assertTrue(seen.last().path.startsWith("/api/store/golestan/chat/search?q="))
    val hits = m.acts.hits!!
    assertEquals(ShopperText.chatHits(2), m.acts.findNote)
    assertTrue(m.acts.here(hits[0]) && !m.acts.here(hits[1]))
    assertEquals("دکان، پیامِ سفارشِ چهار، در گفت‌وگوی دیگر", m.acts.hitLine(hits[1]))
    m.acts.open(hits[1])
    assertNull("رشتهٔ دیگر ⇒ رفتن نه", m.acts.jump)
    m.acts.open(hits[0])
    assertEquals("m1", m.acts.jump)
    assertFalse("نوارِ جست‌وجو بسته", m.acts.finding)

    //  بلیتِ منقضی ⇒ تأییدِ دوبارهٔ شماره
    mode = "auth"
    m.acts.edit(m.t().messages.first { it.id == "n4" })   //  همان پاسخِ رفته (پیامِ خودم، در مهلت)
    assertEquals("n4", m.compose.editing)
    m.draft = "x"
    m.submit()
    assertEquals(1, lost)
    assertTrue(m.needVerify)
  }

  //  دستهٔ ۴۲: پس از حذف تمرکز گم نمی‌شود — سنگِ قبر ⇒ دکمهٔ «کارها» ِ همان حباب؛ حبابِ رفته ⇒ کادرِ نوشتن
  @Test fun focusAfterDeleteGoesToTheTombstoneOrTheComposerAndNeverToAnotherThread() {
    val m = model()
    m.open("golestan", 3, "")
    assertNull(m.focus)
    m.actions(m.t().messages.first { it.id == "m2" })
    m.askDelete(ChatDeleteScope.ALL)
    m.confirmDelete()
    assertTrue(m.t().messages.first { it.id == "m2" }.deleted)
    assertEquals(ChatFocus("m2"), m.focus)
    m.focused()
    assertNull(m.focus)
    m.actions(m.t().messages.first { it.id == "m1" })
    m.askDelete(ChatDeleteScope.ME)
    m.confirmDelete()
    assertFalse(m.t().messages.any { it.id == "m1" })
    assertEquals(ChatFocus(null), m.focus)

    //  حذفِ در راهِ رشتهٔ ۴، سپس رشتهٔ ۳ ⇒ نه پیامِ «پاک شد» و نه تمرکز در رشتهٔ ۳
    val io = Queue()
    val q = model(io)
    q.open("golestan", 4, "")
    io.run()
    q.actions(q.t().messages.single())
    q.askDelete(ChatDeleteScope.ME)
    q.confirmDelete()
    q.open("golestan", 3, "")
    io.run()
    assertEquals("sch_3", q.t().conversation.id)
    assertNull(q.focus)
    assertNull(q.deletedNote)
    assertNull(q.act)
  }
}
