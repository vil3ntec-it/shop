package ir.vil3ntec.tohid.core.shopper

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** گفت‌وگو با دکان (CHAT-02/06): صفِ بی‌اینترنت، ادغامِ تغییرها و عکس پیش از فرستادن. */
class ShopperChatTest {

  private fun err(status: Int, code: String = "x") = ShopperError(status, code)

  @Test fun queueKeepsOneClientIdPerMessageAndOrderPerConversation() {
    var q = emptyList<ChatOutItem>()
    val r = Random(7)
    val (q1, a) = ChatOutbox.add(q, "golestan", "c1", "text", " سلام ", now = 1000, random = r)
    val (q2, b) = ChatOutbox.add(q1, "golestan", "c1", "text", "برنج دارید؟", now = 1000, random = r)
    val (q3, other) = ChatOutbox.add(q2, "golestan", "c2", "text", "پیامِ دیگر", now = 1000, random = r)
    q = q3
    assertTrue(ChatOutbox.validId(a.clientId) && ChatOutbox.validId(b.clientId), "همان الگوی `CLIENT_ID_RE` ِ سرور")
    assertNotEquals(a.clientId, b.clientId)
    assertTrue(b.createdAt > a.createdAt, "دو پیام در یک میلی‌ثانیه هم ترتیب دارند")
    assertEquals("سلام", a.body)
    assertEquals(a, ChatOutbox.next(q, "c1", 1000), "فقط سرِ صفِ همان گفت‌وگو")
    assertEquals(other, ChatOutbox.next(q, "c2", 1000), "گفت‌وگوی دیگر جدا")

    //  بی‌اینترنت ⇒ «منتظرِ اینترنت» با backoff و همان شناسه؛ پشتِ سری نمی‌رود (ترتیب)
    q = ChatOutbox.failed(q, a.clientId, ShopperError(0, ShopperError.NETWORK), now = 1000, rand = 1.0)
    val waiting = q.first { it.clientId == a.clientId }
    assertEquals(ChatOutbox.WAITING, waiting.state)
    assertEquals(1000 + 2000, waiting.nextAt)
    assertNull(ChatOutbox.next(q, "c1", 2000), "پیش از موعد هیچ؛ پیامِ دوم جلو نمی‌زند")
    assertEquals(a.clientId, ChatOutbox.next(q, "c1", 3000)?.clientId, "موعد ⇒ همان پیام با همان شناسه")
    q = ChatOutbox.online(q)
    assertEquals(a.clientId, ChatOutbox.next(q, "c1", 1001)?.clientId, "اینترنت برگشت ⇒ همین حالا")

    //  بلیتِ منقضی ⇒ صف می‌ماند تا تأییدِ دوباره
    q = ChatOutbox.failed(q, a.clientId, err(401, "verification_required"), 1000, 0.0)
    assertEquals(ChatOutbox.VERIFY, q.first { it.clientId == a.clientId }.state)
    assertNull(ChatOutbox.next(q, "c1", 99_999))
    q = ChatOutbox.reverified(q)
    assertEquals(a.clientId, ChatOutbox.next(q, "c1", 1000)?.clientId)

    //  خطای قطعی ⇒ «نرفت» و صف گیر نمی‌کند
    q = ChatOutbox.failed(q, a.clientId, err(409, "chat_closed"), 1000, 0.0)
    assertEquals(b.clientId, ChatOutbox.next(q, "c1", 1000)?.clientId)
    q = ChatOutbox.retry(q, a.clientId)
    assertEquals(a.clientId, ChatOutbox.next(q, "c1", 1000)?.clientId, "«دوباره» همان شناسه")
    q = ChatOutbox.done(q, a.clientId)
    assertEquals(b.clientId, ChatOutbox.next(q, "c1", 1000)?.clientId)
  }

  @Test fun appClosedMidSendRequeuesTheSameIdAndPhotosStayInMemory() {
    val (q1, a) = ChatOutbox.add(emptyList(), "golestan", "c1", "text", "سلام", 5, Random(1))
    val (q2, img) = ChatOutbox.add(q1, "golestan", "c1", "image", "", 6, Random(2), width = 800, height = 600)
    val sending = ChatOutbox.patch(q2, a.clientId) { it.copy(state = ChatOutbox.SENDING) }
    val back = ChatOutbox.load(ChatOutbox.dump(sending))
    assertEquals(listOf(a.clientId), back.map { it.clientId }, "فقط متن روی دیسک")
    assertEquals(ChatOutbox.QUEUED, back[0].state, "وسطِ ارسال بسته شد ⇒ دوباره در صف")
    assertEquals(emptyList(), ChatOutbox.load("{خراب"))
    assertEquals(emptyList(), ChatOutbox.load("""[{"clientId":"bad","slug":"s","convId":"c"}]"""), "شناسهٔ نادرست کنار")
    val body = Json.parseToJsonElement(ChatOutbox.body(img.copy(mediaId = "scf_1", thumbId = "scf_2"))).jsonObject
    assertEquals("image", body["kind"]!!.jsonPrimitive.content)
    assertEquals("scf_1", body["mediaId"]!!.jsonPrimitive.content)
    assertEquals("800", body["meta"]!!.jsonObject["width"]!!.jsonPrimitive.content)
    val text = Json.parseToJsonElement(ChatOutbox.body(a.copy(body = "«نقل» \"x\""))).jsonObject
    assertEquals("«نقل» \"x\"", text["body"]!!.jsonPrimitive.content)
    assertEquals(setOf("clientId", "kind", "body"), text.keys)
  }

  @Test fun failureKindsAndBackoffBounds() {
    assertEquals(ChatFailure.RETRY, ChatOutbox.failureOf(err(0, ShopperError.NETWORK)))
    assertEquals(ChatFailure.RETRY, ChatOutbox.failureOf(err(429)))
    assertEquals(ChatFailure.RETRY, ChatOutbox.failureOf(err(503)))
    assertEquals(ChatFailure.VERIFY, ChatOutbox.failureOf(err(401, "verification_required")))
    assertEquals(ChatFailure.FAIL, ChatOutbox.failureOf(err(400, "empty_message")))
    assertEquals(ChatFailure.FAIL, ChatOutbox.failureOf(err(409, "client_id_reused")))
    assertEquals(1000, ChatOutbox.delayMs(1, 0.0))
    assertEquals(2000, ChatOutbox.delayMs(1, 1.0))
    assertEquals(ChatOutbox.MAX_MS, ChatOutbox.delayMs(40, 1.0), "سقفِ ۵ دقیقه")
  }

  @Test fun threadMergesEditsDeletesAndArrivedQueueItems() {
    val conv = ChatConversation("c1")
    val m1 = ChatMessage("m1", seq = 1, rev = 1, sender = "customer", body = "سلام", mine = true, clientId = "c-abcdefgh1")
    val m2 = ChatMessage("m2", seq = 2, rev = 2, sender = "shop", body = "بفرمایید")
    var t = ChatThread(conv).merge(ChatChanges(conv, listOf(m2, m1), cursor = 2))
    assertEquals(listOf("m1", "m2"), t.messages.map { it.id }, "ترتیبِ seq")
    assertEquals(2, t.cursor)
    t = t.merge(ChatChanges(conv, listOf(m2.copy(rev = 3, deleted = true, body = "")), cursor = 3))
    assertTrue(t.messages.first { it.id == "m2" }.deleted, "حذف همان شناسه را جایگزین می‌کند")
    assertEquals(2, t.messages.size)
    t = t.merge(ChatChanges(conv, listOf(m1.copy(rev = 0, body = "کهنه")), cursor = 1))
    assertEquals("سلام", t.messages.first { it.id == "m1" }.body, "rev ِ کهنه‌تر نمی‌نشیند")
    assertEquals(3, t.cursor, "نشانگر عقب نمی‌رود")
    assertTrue(t.arrived("c-abcdefgh1"))
    assertEquals(2, t.lastShopSeq())
    assertEquals(t, t.withMine(m1), "پیامِ رسیده دوباره افزوده نمی‌شود")
    val hidden = t.merge(ChatChanges(conv, listOf(ChatMessage("m3", seq = 3, rev = 4, hidden = true)), cursor = 4))
    assertEquals(2, hidden.messages.size, "«حذف برای من» دیده نمی‌شود")
  }

  @Test fun ownStatusFollowsOnlyRealShopReceiptsLiveAndNeverGoesBack() {
    val conv = ChatConversation("c1")
    val mine = ChatMessage("m1", seq = 2, rev = 2, sender = "customer", body = "سلام", mine = true, clientId = "c-abcdefgh1", status = "sent")
    val theirs = ChatMessage("m2", seq = 1, rev = 1, sender = "shop", body = "بفرمایید")
    fun st(t: ChatThread, id: String) = t.statusOf(t.messages.first { it.id == id })
    var t = ChatThread(conv).merge(ChatChanges(conv, listOf(theirs, mine), cursor = 2))
    assertEquals("sent", st(t, "m1"), "بی رسیدِ دکان فقط «رفت»")
    assertNull(st(t, "m2"), "پیامِ دکان وضعیتِ من ندارد")
    //  long-poll ِ بی پیام فقط گفت‌وگو با رسیدِ تازهٔ دکان را می‌آورد
    t = t.merge(ChatChanges(conv.copy(receipts = ChatReceipts(peerDeliveredSeq = 2)), emptyList(), cursor = 2))
    assertEquals("delivered", st(t, "m1"))
    t = t.merge(ChatChanges(conv.copy(receipts = ChatReceipts(peerDeliveredSeq = 2, peerReadSeq = 2)), emptyList(), cursor = 2))
    assertEquals("read", st(t, "m1"))
    t = t.merge(ChatChanges(conv.copy(receipts = ChatReceipts(peerDeliveredSeq = 1)), emptyList(), cursor = 2))
    assertEquals("read", st(t, "m1"), "پاسخِ کهنه‌تر عقب نمی‌برد")
    assertEquals(2, t.conversation.receipts.peerReadSeq)
    val later = mine.copy(id = "m3", seq = 3, rev = 3, clientId = "c-abcdefgh2")
    t = t.merge(ChatChanges(conv, listOf(later), cursor = 3))
    assertEquals("sent", st(t, "m3"), "پیامِ تازه‌تر از «خواندم» ِ دکان «رفت» می‌ماند")
    assertEquals("read", st(t, "m1"))
    assertNull(t.statusOf(mine.copy(deleted = true)), "سنگِ قبر وضعیت ندارد")
  }

  @Test fun photoOrientationRotationAndTransparencyLikeTheShopApp() {
    //  JPEG ِ کمینه با APP1/Exif و جهتِ ۶ (۹۰° ساعت‌گرد)، هر دو ترتیبِ بایت
    fun jpeg(orientation: Int, le: Boolean): ByteArray {
      fun u16(v: Int) = if (le) byteArrayOf(v.toByte(), (v shr 8).toByte()) else byteArrayOf((v shr 8).toByte(), v.toByte())
      fun u32(v: Int) = if (le) u16(v and 0xFFFF) + u16(v ushr 16) else u16(v ushr 16) + u16(v and 0xFFFF)
      val tiff = (if (le) "II".encodeToByteArray() else "MM".encodeToByteArray()) + u16(42) + u32(8) +
        u16(1) + u16(0x0112) + u16(3) + u32(1) + u16(orientation) + u16(0) + u32(0)
      val app1 = "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff
      val len = app1.size + 2
      return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) + app1 +
        byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 2)
    }
    assertEquals(6, ShopperPhoto.orientationOf(jpeg(6, le = true)))
    assertEquals(8, ShopperPhoto.orientationOf(jpeg(8, le = false)))
    assertEquals(1, ShopperPhoto.orientationOf(byteArrayOf(1, 2, 3, 4)), "JPEG نیست ⇒ ۱")
    assertEquals(1, ShopperPhoto.orientationOf(jpeg(6, true).copyOf(20)), "بریده ⇒ ۱، بی کرش")
    assertEquals("image/jpeg", ShopperPhoto.mimeOf(jpeg(1, true)))
    assertEquals("image/png", ShopperPhoto.mimeOf(byteArrayOf(0x89.toByte()) + "PNG\r\n\u001a\n".encodeToByteArray()))
    assertNull(ShopperPhoto.mimeOf("GIF89a".encodeToByteArray()), "GIF را سرور هم رد می‌کند")

    //  ۲×۱: [A B] ⇐ جهتِ ۶ ⇒ ۱×۲: [A; B] ؛ جهتِ ۳ ⇒ [B A]
    val (r6, w6, h6) = ShopperPhoto.orient(intArrayOf(1, 2), 2, 1, 6)
    assertEquals(1 to 2, w6 to h6)
    assertContentEquals(intArrayOf(1, 2), r6)
    val (r8, _, _) = ShopperPhoto.orient(intArrayOf(1, 2), 2, 1, 8)
    assertContentEquals(intArrayOf(2, 1), r8)
    assertContentEquals(intArrayOf(2, 1), ShopperPhoto.orient(intArrayOf(1, 2), 2, 1, 3).first)
    assertContentEquals(intArrayOf(1, 2), ShopperPhoto.orient(intArrayOf(1, 2), 2, 1, 1).first)

    val px = ShopperPhoto.flattenOnWhite(intArrayOf(0x00000000, 0x80000000.toInt(), 0xFF102030.toInt()))
    assertEquals(0xFFFFFFFF.toInt(), px[0], "شفاف ⇒ سفید، نه سیاه")
    assertEquals(0xFF7F7F7F.toInt(), px[1])
    assertEquals(0xFF102030.toInt(), px[2])
    assertEquals(1600 to 1200, ShopperPhoto.fit(4000, 3000, ShopperPhoto.MAIN_PX))
    assertEquals(200 to 100, ShopperPhoto.fit(200, 100, ShopperPhoto.MAIN_PX), "هرگز بزرگ‌تر")
    assertEquals(listOf(82, 72, 62, 52, 42), ShopperPhoto.qualities(ShopperPhoto.MAIN_QUALITY))
  }
}
