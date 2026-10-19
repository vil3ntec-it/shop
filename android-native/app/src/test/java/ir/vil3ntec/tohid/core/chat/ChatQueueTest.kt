package ir.vil3ntec.tohid.core.chat

import ir.vil3ntec.tohid.core.net.ApiFailure
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  صفِ ارسال و backoff ِ چتِ دکان (v3 §۳۳؛ CHAT-02/03/04/11) — بی شبکه در صف،
 *  پس از وصل هر پیام یک بار با همان clientId، پاسخِ گم‌شده، رسیدِ واقعی.
 */
class ChatQueueTest {

  private class Clock(var t: Long = 1_000_000L) { fun now() = t }

  /** حافظهٔ ساختگیِ دیسک: همان JSON که `ChatOutbox` روی پرونده می‌نویسد. */
  private class Disk {
    var text: String? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(OutboxItem.serializer())
    fun load(): List<OutboxItem>? = text?.let { json.decodeFromString(ser, it) }
    fun save(items: List<OutboxItem>) { text = json.encodeToString(ser, items) }
  }

  private fun queue(disk: Disk = Disk(), clock: Clock = Clock(), rand: Double = 0.0) =
    ChatQueue(disk::load, disk::save, now = clock::now, rand = { rand })

  @Test fun backoffDoublesWithHalfJitterAndCaps() {
    assertEquals(1000L, ChatBackoff.delayMs(1) { 0.0 })
    assertEquals(2000L, ChatBackoff.delayMs(1) { 1.0 })
    assertEquals(2000L, ChatBackoff.delayMs(2) { 0.0 })
    assertEquals(6000L, ChatBackoff.delayMs(3) { 0.5 })
    for (n in listOf(9, 10, 50, Int.MAX_VALUE)) {
      val v = ChatBackoff.delayMs(n) { 1.0 }
      assertTrue("n=$n ⇒ $v", v in 150_000L..300_000L)
    }
  }

  @Test fun errorKinds() {
    assertEquals(ChatErrorKind.RETRY, ChatBackoff.kindOf(ApiFailure.Offline()))
    assertEquals(ChatErrorKind.RETRY, ChatBackoff.kindOf(ApiFailure.Timeout()))
    assertEquals(ChatErrorKind.RETRY, ChatBackoff.kindOf(ApiFailure.ServerFault("x", "x")))
    assertEquals(ChatErrorKind.RETRY, ChatBackoff.kindOf(ApiFailure.RateLimited("x", "x")))
    assertEquals(ChatErrorKind.RETRY, ChatBackoff.kindOf(java.io.IOException("reset")))
    assertEquals(ChatErrorKind.AUTH, ChatBackoff.kindOf(ApiFailure.SessionExpired()))
    assertEquals(ChatErrorKind.FAIL, ChatBackoff.kindOf(ApiFailure.Invalid("بزرگ", "too_large")))
    assertEquals(ChatErrorKind.FAIL, ChatBackoff.kindOf(ApiFailure.Forbidden("بسته", "chat_blocked")))
    assertEquals(ChatErrorKind.FAIL, ChatBackoff.kindOf(ApiFailure.Conflict("x", "client_id_reused")))
  }

  @Test fun clientIdsMatchServerPatternAndAreUnique() {
    val q = queue(rand = 0.0)
    val ids = (1..300).map { q.add("c1", body = "$it").clientId }
    assertEquals(300, ids.toSet().size)
    ids.forEach { assertTrue(it, ChatIds.valid(it)) }
  }

  @Test fun headOfLineOrderTransientHoldsPermanentDoesNot() {
    val clock = Clock()
    val q = queue(clock = clock)
    val a = q.add("c1", body = "یک")
    val b = q.add("c1", body = "دو")
    val x = q.add("c2", body = "دیگری")
    assertTrue(a.createdAt < b.createdAt)
    assertEquals(a.clientId, q.next("c1")?.clientId)
    assertEquals(x.clientId, q.next("c2")?.clientId)

    q.sending(a.clientId)
    assertNull("یکی‌یکی", q.next("c1"))
    q.failed(a.clientId, ChatErrorKind.RETRY, "بی شبکه")
    assertEquals(ChatQueue.WAITING, q.get(a.clientId)?.state)
    assertEquals(clock.t + 1000, q.get(a.clientId)?.nextAt)
    assertNull("دومی پیش از اولی نمی‌رود", q.next("c1"))
    clock.t += 1000
    assertEquals(a.clientId, q.next("c1")?.clientId)

    q.sending(a.clientId)
    q.failed(a.clientId, ChatErrorKind.FAIL, "بزرگ")
    assertEquals(ChatQueue.FAILED, q.get(a.clientId)?.state)
    assertEquals("بزرگ", q.get(a.clientId)?.error)
    assertEquals("پیامِ نرفته صف را قفل نمی‌کند", b.clientId, q.next("c1")?.clientId)
    assertEquals(listOf("c1", "c2"), q.convs())

    assertEquals(ChatQueue.QUEUED, q.retry(a.clientId)?.state)
    assertEquals("«دوباره» همان شناسه", a.clientId, q.next("c1")?.clientId)
    q.sending(a.clientId)
    assertNull("در راه لغو نمی‌شود", q.cancel(a.clientId))
    q.failed(a.clientId, ChatErrorKind.AUTH, "نشست")
    assertNull("منتظرِ هویت صف را نگه می‌دارد", q.next("c1"))
    assertTrue(q.resumeAuth())
    assertEquals(a.clientId, q.next("c1")?.clientId)
    assertEquals(emptyList<String>(), q.cancel(a.clientId))
    assertNull(q.get(a.clientId))
  }

  @Test fun survivesRestartMidSendWithSameIdAndCancelReturnsLocalMedia() {
    val disk = Disk()
    val q1 = queue(disk)
    val a = q1.add("c1", kind = "image", blobKey = "k1", thumbKey = "k2", meta = ChatMeta(width = 800, height = 600))
    q1.sending(a.clientId)
    val q2 = queue(disk)
    val back = q2.get(a.clientId)
    assertNotNull(back)
    assertEquals(ChatQueue.QUEUED, back!!.state)
    assertEquals(800, back.meta.width)
    assertEquals(listOf("k1", "k2"), q2.cancel(a.clientId))
    assertTrue(queue(disk).all().isEmpty())
  }

  @Test fun drainOfflineThenOnlineSendsEachOnceInOrderWithSameClientId() = runBlocking {
    val clock = Clock()
    val q = queue(clock = clock)
    q.add("c1", body = "یک")
    q.add("c1", body = "دو")
    val server = LinkedHashMap<String, ChatMessage>()
    val calls = mutableListOf<String>()
    var online = false
    val send: suspend (OutboxItem) -> ChatMessage = { item ->
      calls += item.clientId
      if (!online) throw ApiFailure.Offline()
      server.getOrPut(item.clientId) { ChatMessage(id = "m${server.size + 1}", seq = server.size + 1L, body = item.body, clientId = item.clientId) }
    }
    q.drain("c1", send)
    assertEquals("سرِ صف یک بار تلاش کرد و صف ایستاد", 1, calls.size)
    assertEquals(2, q.all().size)
    assertTrue(q.wake())
    online = true
    val sent = mutableListOf<String>()
    q.drain("c1", send, onSent = { _, m, _ -> sent += m.body })
    assertEquals(listOf("یک", "دو"), sent)
    assertEquals(2, server.size)
    assertTrue(q.all().isEmpty())
    assertEquals("تلاشِ دوباره با همان شناسه", calls[0], calls[1])
  }

  @Test fun lostResponseRetriesWithSameIdAndServerKeepsOne() = runBlocking {
    val clock = Clock()
    val q = queue(clock = clock)
    q.add("c1", body = "پاسخِ گم‌شده")
    val server = LinkedHashMap<String, ChatMessage>()
    var dropped = 0
    val send: suspend (OutboxItem) -> ChatMessage = { item ->
      val m = server.getOrPut(item.clientId) { ChatMessage(id = "m1", seq = 1, body = item.body, clientId = item.clientId) }
      if (dropped++ == 0) throw ApiFailure.Timeout()   // ثبت شد، جواب نرسید
      m
    }
    q.drain("c1", send)
    assertEquals(ChatQueue.WAITING, q.all().single().state)
    clock.t = q.nextWake()!!
    q.drain("c1", send)
    assertEquals(1, server.size)
    assertTrue(q.all().isEmpty())
  }

  @Test fun permanentFailureDoesNotBlockTheRest() = runBlocking {
    val q = queue()
    q.add("c1", body = "بد")
    q.add("c1", body = "خوب")
    val sent = mutableListOf<String>()
    q.drain("c1", { item ->
      if (item.body == "بد") throw ApiFailure.Invalid("پیام خیلی بلند است", "message_too_long")
      ChatMessage(id = "m", seq = 1, body = item.body, clientId = item.clientId)
    }, onSent = { _, m, _ -> sent += m.body })
    assertEquals(listOf("خوب"), sent)
    assertEquals(ChatQueue.FAILED, q.all().single().state)
    assertEquals("پیام خیلی بلند است", q.all().single().error)
  }

  @Test fun reconcileDropsWhatServerAlreadyHas() {
    val q = queue()
    val a = q.add("c1", body = "x", blobKey = "b")
    q.add("c1", body = "y")
    assertEquals(listOf("b"), q.reconcile("c1", listOf(a.clientId)))
    assertEquals(1, q.all().size)
  }

  @Test fun threadMergesByRevHidesHiddenAndReceiptsComeOnlyFromServer() {
    val t = ChatThread()
    fun conv(r: ChatReceipts = ChatReceipts()) = ChatConversation(id = "c1", receipts = r)
    assertTrue(t.apply(ChatChanges(conv(), listOf(ChatMessage("a", seq = 1, rev = 1, sender = "customer"), ChatMessage("b", seq = 2, rev = 2, sender = "shop")), cursor = 2)))
    assertEquals(2L, t.cursor)
    assertEquals(ChatThread.Receipt(2, 0), t.receiptNeed(false))
    assertEquals(ChatThread.Receipt(2, 2), t.receiptNeed(true))
    t.apply(ChatChanges(conv(ChatReceipts(myDeliveredSeq = 2, myReadSeq = 2)), emptyList(), cursor = 2))
    assertNull("تکرار نمی‌شود", t.receiptNeed(true))
    assertTrue("بلاک ⇒ تازه‌سازیِ نما", t.apply(ChatChanges(conv(ChatReceipts(myDeliveredSeq = 2, myReadSeq = 2)).copy(canSend = false), emptyList(), cursor = 2)))
    t.apply(ChatChanges(null, listOf(ChatMessage("a", seq = 1, rev = 3, body = "ویرایش"), ChatMessage("b", seq = 2, rev = 4, hidden = true)), cursor = 4))
    assertEquals(listOf("a"), t.list().map { it.id })
    assertEquals("ویرایش", t.list()[0].body)
    val mine = ChatMessage("m", seq = 3)
    assertEquals("sent", ChatThread.statusOf(mine, ChatReceipts(peerDeliveredSeq = 2, peerReadSeq = 1)))
    assertEquals("delivered", ChatThread.statusOf(mine, ChatReceipts(peerDeliveredSeq = 3, peerReadSeq = 1)))
    assertEquals("read", ChatThread.statusOf(mine, ChatReceipts(peerDeliveredSeq = 3, peerReadSeq = 3)))
    assertEquals("", ChatThread.statusOf(mine.copy(deleted = true), ChatReceipts()))
  }

  @Test fun serverPayloadDecodes() {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    val c = json.decodeFromString(ChatChanges.serializer(), """
      {"conversation":{"id":"sch_1","status":"open","seq":3,"rev":5,"unread":1,"orderNo":12,"orderId":"o1",
       "lastMessage":{"kind":"image","sender":"customer","at":5,"preview":""},"lastMessageAt":5,"createdAt":1,"updatedAt":5,
       "customer":{"name":"احمد","phoneMasked":"+937•••••233"},"blockedCustomer":false,"customerBlockedShop":false,"canSend":true,
       "receipts":{"peerDeliveredSeq":2,"peerReadSeq":1,"myDeliveredSeq":3,"myReadSeq":3}},
       "messages":[{"id":"m1","seq":1,"rev":1,"sender":"shop","createdAt":1,"kind":"text","body":"سلام","replyTo":null,"meta":{},
       "editedAt":null,"mine":true,"media":null,"clientId":"k1-abcdefgh","status":"read","senderUserId":"u1"},
       {"id":"m2","seq":2,"rev":4,"sender":"customer","createdAt":2,"deleted":true,"deletedAt":4},
       {"id":"m3","seq":3,"rev":5,"sender":"customer","createdAt":3,"kind":"image","body":"","meta":{"width":800,"height":1600},
       "media":{"id":"scf_1","mime":"image/jpeg","size":1000,"thumbId":"scf_2","thumbSize":100}}],
       "cursor":5,"hasMore":false}
    """.trimIndent())
    assertEquals("+937•••••233", c.conversation!!.customer.shownPhone)
    assertEquals(12L, c.conversation!!.orderNo)
    assertTrue(c.messages[1].deleted)
    assertEquals(1600, c.messages[2].meta.height)
    assertEquals("scf_2", c.messages[2].media!!.thumbId)
    assertEquals("+93791112233", ChatCustomer(phoneMasked = "•", phone = "+93791112233").shownPhone)
  }
}
