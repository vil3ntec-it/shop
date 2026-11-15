package ir.vil3ntec.tohid.desktop.hub

import ir.vil3ntec.tohid.core.hub.HubAppend
import ir.vil3ntec.tohid.core.hub.HubEntry
import ir.vil3ntec.tohid.core.hub.HubLog
import ir.vil3ntec.tohid.core.hub.HubLogBox
import ir.vil3ntec.tohid.core.hub.HubLogForwarder
import ir.vil3ntec.tohid.core.hub.HubLogLimits
import ir.vil3ntec.tohid.core.hub.HubLogState
import ir.vil3ntec.tohid.core.hub.HubMarks
import ir.vil3ntec.tohid.core.hub.HubMdns
import ir.vil3ntec.tohid.core.hub.HubPair
import ir.vil3ntec.tohid.core.hub.HubPeer
import ir.vil3ntec.tohid.core.hub.HubPeers
import ir.vil3ntec.tohid.core.hub.HubReply
import ir.vil3ntec.tohid.core.hub.HubV1Cloud
import ir.vil3ntec.tohid.core.hw.Mdns
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 *  SYN-09/S29 — منطقِ خالصِ هاب: برگهٔ جفت‌سازی (فقط صاحب/مدیر، یک‌بارمصرف، کوتاه، ۵ تلاش)، QR ِ بی راز، اثباتِ کد،
 *  کلیدِ دستگاه، دفترِ فقط‌افزودنیِ v1 (یکتایی، ترتیبِ هر دستگاه، یک بار به سرور، خوراکِ دستگاه‌های دیگر) و mDNS.
 */
class HubPairLogTest {

  private val rnd = Random(7)
  private val random: (Int) -> ByteArray = { n -> rnd.nextBytes(n) }
  private val pin = "ab".repeat(32)
  private val t0 = 1_000_000L

  private fun ticket(role: String = "owner") = (HubPair.issue(role, "صاحب", t0, random) as HubPair.Issue.Ok).ticket

  /* ------------------------------ جفت‌سازی ------------------------------ */

  @Test fun onlyOwnerOrManagerCanIssueAPairingTicket() {
    for (r in listOf("staff", "cashier", "courier", "accountant", "", null)) {
      assertEquals(r.toString(), HubPair.Issue.Denied(HubPair.Refusal.ROLE), HubPair.issue(r, "x", t0, random))
    }
    for (r in listOf("owner", "Manager")) assertTrue(HubPair.issue(r, "x", t0, random) is HubPair.Issue.Ok)
    val t = ticket()
    assertEquals(6, t.code.length)
    assertTrue(t.code.all { it.isDigit() })
    assertEquals(40, t.token.length)
    assertEquals(t0 + HubPair.TTL_MS, t.expiresAt)
  }

  @Test fun ticketIsSingleUseAndReplayIsRejected() {
    val t = ticket()
    val (used, ok) = HubPair.claim(t, t.code, t0 + 1)
    assertTrue(ok is HubPair.Claim.Ok)
    assertEquals(HubPair.Claim.Refused(HubPair.Refusal.USED), HubPair.claim(used, t.code, t0 + 2).second)
    assertEquals(HubPair.Claim.Refused(HubPair.Refusal.USED), HubPair.claim(used, t.token, t0 + 2).second)
    //  راهِ QR هم یک‌باره است، و کدِ همان برگه پس از آن هم
    val q = ticket()
    val (qUsed, qOk) = HubPair.claim(q, q.token.uppercase(), t0 + 1)
    assertTrue(qOk is HubPair.Claim.Ok)
    assertEquals(HubPair.Claim.Refused(HubPair.Refusal.USED), HubPair.claim(qUsed, q.code, t0 + 2).second)
  }

  @Test fun ticketIsShortLivedAndClockBackIsExpired() {
    val t = ticket()
    assertEquals(HubPair.Claim.Refused(HubPair.Refusal.EXPIRED), HubPair.claim(t, t.code, t0 + HubPair.TTL_MS + 1).second)
    assertEquals(HubPair.Claim.Refused(HubPair.Refusal.EXPIRED), HubPair.claim(t, t.code, t0 - 1).second)
    assertFalse(HubPair.alive(t, t0 + HubPair.TTL_MS + 1))
    assertEquals(HubPair.Claim.Refused(HubPair.Refusal.NONE), HubPair.claim(null, "123456", t0).second)
  }

  @Test fun fiveWrongTriesBurnTheTicket() {
    var t: ir.vil3ntec.tohid.core.hub.HubTicket? = ticket()
    val wrong = ((t!!.code.toInt() + 1) % 1_000_000).toString().padStart(6, '0')
    repeat(HubPair.MAX_TRIES - 1) {
      val (n, c) = HubPair.claim(t, wrong, t0 + 1)
      assertEquals(HubPair.Claim.Refused(HubPair.Refusal.WRONG), c)
      t = n
    }
    val (n, c) = HubPair.claim(t, "abc", t0 + 1)
    assertEquals(HubPair.Claim.Refused(HubPair.Refusal.LOCKED), c)
    assertEquals(HubPair.Claim.Refused(HubPair.Refusal.LOCKED), HubPair.claim(n, n!!.code, t0 + 2).second)
  }

  @Test fun codeProofAsksAreCountedAndBoundToThePin() {
    var t: ir.vil3ntec.tohid.core.hub.HubTicket? = ticket()
    repeat(HubPair.MAX_ASKS) { val (n, why) = HubPair.ask(t, t0 + 1); assertNull(why); t = n }
    assertEquals(HubPair.Refusal.LOCKED, HubPair.ask(t, t0 + 1).second)
    val code = t!!.code
    val n = "0f".repeat(16)
    val p = HubPair.proof(code, "hub", pin, n)
    assertEquals(p, HubPair.proof(code, "hub", pin.uppercase(), n))
    assertNotEquals(p, HubPair.proof(code, "hub", "cd".repeat(32), n))
    assertNotEquals(p, HubPair.proof(code, "hub", pin, "1f".repeat(16)))
    assertNotEquals(p, HubPair.proof(((code.toInt() + 1) % 1_000_000).toString().padStart(6, '0'), "hub", pin, n))
    assertNotEquals(p, HubPair.proof(code, "phone", pin, n))
  }

  @Test fun qrCarriesNoSecretBeyondTheOneTimeToken() {
    val t = ticket()
    val qr = HubPair.qr("https://192.168.1.20:47110", "srv_abc-1", pin, t.token)
    assertFalse("کدِ شش‌رقمی در QR", qr.contains(t.code) && !t.token.contains(t.code))
    val parsed = HubPair.parseQr(qr)!!
    assertEquals("https://192.168.1.20:47110", parsed.url)
    assertEquals("srv_abc-1", parsed.serverId)
    assertEquals(pin, parsed.pin)
    assertEquals(t.token, parsed.token)
    //  فقط https ِ شبکهٔ دکان، سنجاق و توکنِ درست
    assertNull(HubPair.parseQr(HubPair.qr("http://192.168.1.20:47110", "s", pin, t.token)))
    assertNull(HubPair.parseQr(HubPair.qr("https://example.com:47110", "s", pin, t.token)))
    assertNull(HubPair.parseQr(HubPair.qr("https://8.8.8.8:47110", "s", pin, t.token)))
    assertNull(HubPair.parseQr(HubPair.qr("https://192.168.1.20:47110", "s", "12", t.token)))
    assertNull(HubPair.parseQr(HubPair.qr("https://192.168.1.20:47110", "s", pin, "zz")))
    assertNull(HubPair.parseQr("tohid-other:1?u=x"))
  }

  @Test fun peerKeyMustMatchDeviceAndShop() {
    val key = HubPair.newKey(random)
    val p = HubPair.pairPeer(HubPeers(), HubPeer("devA", "shop-1", HubPair.keyHash(key), t0))!!
    assertNotNull(HubPair.peerOf(p, "devA", key, "shop-1"))
    assertNull(HubPair.peerOf(p, "devB", key, "shop-1"))
    assertNull(HubPair.peerOf(p, "devA", key, "shop-2"))
    assertNull(HubPair.peerOf(p, "devA", HubPair.newKey(random), "shop-1"))
    assertNull(HubPair.peerOf(p, "devA", null, "shop-1"))
    assertFalse("کلیدِ خام روی دیسک", HubPair.encode(p).contains(key))
    assertNull(HubPair.peerOf(HubPair.unpair(p, "devA"), "devA", key, "shop-1"))
    var full = HubPeers()
    repeat(HubPair.MAX_PEERS) { full = HubPair.pairPeer(full, HubPeer("d$it", "shop-1", "h", t0))!! }
    assertNull(HubPair.pairPeer(full, HubPeer("new", "shop-1", "h", t0)))
    assertNotNull("جفتِ دوبارهٔ همان دستگاه", HubPair.pairPeer(full, HubPeer("d1", "shop-1", "h2", t0)))
  }

  /* ------------------------------ دفترِ v1 ------------------------------ */

  private fun body(device: String, vararg ids: String) =
    """{"device_id":"$device","schema_version":1,"queued":0,"ops":[""" +
      ids.joinToString(",") { """{"op_id":"$it","ts":1,"table":"products","row_id":"r-$it","type":"insert","fields":{"name":"$it"}}""" } + "]}"

  private fun HubLogState.add(device: String, vararg ids: String, now: Long = t0): Pair<HubLogState, HubAppend> =
    HubLog.append(this, device, ids.toList(), body(device, *ids), "shop", "2.14.5", "", now)

  @Test fun logIsAppendOnlyAndDeduplicatesByOpId() {
    var s = HubLogState(marks = HubMarks(epoch = "e1"))
    val (s1, r1) = s.add("devA", "a1", "a2"); s = s1
    assertTrue(r1 is HubAppend.Accepted)
    assertEquals(HubAppend.Known, s.add("devA", "a1", "a2").second)
    assertEquals(HubAppend.Known, s.add("devA", "a2").second)
    val (s2, r2) = s.add("devA", "a2", "a3"); s = s2
    assertEquals(2L, (r2 as HubAppend.Accepted).entry.seq)
    //  دستهٔ پیشین دست نخورد
    assertEquals(listOf("a1", "a2"), s.entries[0].opIds)
    assertEquals(body("devA", "a1", "a2"), s.entries[0].body)
    //  فشرده‌سازیِ فرستاده‌های کهنه `seq` را دوباره به کار نمی‌برد
    s = HubLog.settle(s, s.entries[0], mapOf("a1" to ("applied" to 5L), "a2" to ("applied" to 6L)))
    s = HubLog.compact(s, t0 + HubLogLimits().feedKeepMs + 1)
    assertEquals(listOf(2L), s.entries.map { it.seq })
    assertEquals(1L, s.marks.compactedTo)
    val (s3, r3) = s.add("devB", "b1")
    assertEquals(3L, (r3 as HubAppend.Accepted).entry.seq)
    assertEquals(3L, s3.head)
  }

  private class Cloud : HubV1Cloud {
    var online = true
    val tokens = mapOf("tA" to "devA", "tB" to "devB")
    val rows = LinkedHashMap<String, Long>()
    val order = ArrayList<String>()
    override fun pushV1(entry: HubEntry, token: String): HubReply {
      if (!online) return HubReply(0, null)
      if (tokens[token] != entry.deviceId) return HubReply(401, "{}")
      val res = entry.opIds.joinToString(",") { id ->
        val st = if (id in rows) "duplicate" else "applied".also { rows[id] = rows.size + 1L; order += id }
        """{"op_id":"$id","status":"$st","server_seq":${rows[id]}}"""
      }
      return HubReply(200, """{"ok":true,"results":[$res]}""")
    }
  }

  private class Box(var s: HubLogState) : HubLogBox {
    override fun read() = s
    override fun update(change: (HubLogState) -> HubLogState): HubLogState { s = change(s); return s }
  }

  @Test fun forwardKeepsOrderPerDeviceAndSendsEachOpOnce() {
    var s = HubLogState(marks = HubMarks(epoch = "e1"))
    s = s.add("devA", "a1").first
    s = s.add("devB", "b1").first
    s = s.add("devA", "a2", "a3").first
    s = s.add("devB", "b2").first
    val box = Box(s)
    val cloud = Cloud()
    val tokens = HashMap<String, String>()
    val f = HubLogForwarder(box, cloud)
    //  بی اینترنت: هیچ، و «دوباره»
    cloud.online = false
    tokens["devA"] = "tA"; tokens["devB"] = "tB"
    assertTrue(f.drain({ tokens[it] }, { _, _ -> }).retry)
    assertTrue(cloud.rows.isEmpty())
    //  توکنِ دستگاهِ B در حافظه نیست ⇒ B منتظر، A جلو می‌رود
    cloud.online = true
    tokens.remove("devB")
    val p1 = f.drain({ tokens[it] }, { _, _ -> })
    assertEquals(setOf("devB"), p1.waiting)
    assertEquals(listOf("a1", "a2", "a3"), cloud.order)
    tokens["devB"] = "tB"
    f.drain({ tokens[it] }, { _, _ -> })
    assertEquals(listOf("a1", "a2", "a3", "b1", "b2"), cloud.order)
    assertTrue(HubLog.pending(box.s).isEmpty())
    //  دورِ دوباره و دستهٔ تکراری هیچ ردیفِ تازه نمی‌سازد
    assertEquals(0, f.drain({ tokens[it] }, { _, _ -> }).forwarded)
    assertEquals(5, cloud.rows.size)
    assertEquals(3L, box.s.marks.serverSeq["a3"])
  }

  @Test fun authFailureSetsTheDeviceAsideWithoutDroppingItsOps() {
    val box = Box(HubLogState(marks = HubMarks(epoch = "e")).add("devA", "a1").first)
    var failed: String? = null
    val p = HubLogForwarder(box, Cloud()).drain({ "wrong" }, { d, _ -> failed = d })
    assertEquals(setOf("devA"), p.authFailed)
    assertEquals("devA", failed)
    assertEquals(1, HubLog.pending(box.s).size)
  }

  @Test fun feedGivesOtherDevicesOpsOnceAndSkipsRejectedOrAlreadyPulled() {
    var s = HubLogState(marks = HubMarks(epoch = "e9"))
    s = s.add("devA", "a1", "a2").first
    s = s.add("devB", "b1").first
    s = s.add("devA", "a2", "a3").first
    val forB = HubLog.feed(s, "devB", 0, 0)
    assertEquals(listOf("a1", "a2", "a3"), forB.ops.map { (it["op_id"] as JsonPrimitive).content })
    assertEquals(3L, forB.next)
    assertEquals("e9", forB.epoch)
    assertEquals("devA", (forB.ops[0]["device_id"] as JsonPrimitive).content)
    assertTrue(HubLog.feed(s, "devB", forB.next, 0).ops.isEmpty())
    assertEquals(listOf("b1"), HubLog.feed(s, "devA", 0, 0).ops.map { (it["op_id"] as JsonPrimitive).content })
    //  سرور a1 را رد کرد و a2 را با شمارهٔ ۴۰ پذیرفت ⇒ a1 هرگز، a2 فقط برای گوشیِ عقب‌تر از ۴۰
    s = HubLog.settle(s, s.entries[0], mapOf("a1" to ("rejected" to 0L), "a2" to ("applied" to 40L)))
    assertEquals(listOf("a2", "a3"), HubLog.feed(s, "devB", 0, 39).ops.map { (it["op_id"] as JsonPrimitive).content })
    assertEquals(listOf("a3"), HubLog.feed(s, "devB", 0, 40).ops.map { (it["op_id"] as JsonPrimitive).content })
    //  دسته‌ی کلاً ردشده
    s = HubLog.drop(s, s.entries[2])
    assertEquals(listOf("a2"), HubLog.feed(s, "devB", 0, 0).ops.map { (it["op_id"] as JsonPrimitive).content })
  }

  @Test fun feedPagesNeverSplitABatch() {
    var s = HubLogState(marks = HubMarks(epoch = "e"))
    s = s.add("devA", "a1", "a2").first
    s = s.add("devA", "a3", "a4").first
    val lim = HubLogLimits(feedOps = 3)
    val p1 = HubLog.feed(s, "devB", 0, 0, lim)
    assertEquals(2, p1.ops.size)
    assertTrue(p1.more)
    assertEquals(1L, p1.next)
    val p2 = HubLog.feed(s, "devB", p1.next, 0, lim)
    assertEquals(listOf("a3", "a4"), p2.ops.map { (it["op_id"] as JsonPrimitive).content })
    assertFalse(p2.more)
  }

  /* ------------------------------ mDNS ------------------------------ */

  @Test fun mdnsAnswerRoundTripsThroughTheSharedParser() {
    val q = Mdns.questions(Mdns.query(HubMdns.SERVICE, 0x1234))!!
    assertEquals(0x1234, q.id)
    assertTrue(HubMdns.asksForHub(q))
    assertFalse("پرسشِ چاپگر", HubMdns.asksForHub(Mdns.questions(Mdns.query(Mdns.PDL, 1))!!))
    assertNull("پاسخ پرسش نیست", Mdns.questions(HubMdns.answer(1, "Tohid Hub ab12", "tohid-hub-ab12", "192.168.1.20", 47110, listOf("v=1"), 10, true)))
    val reply = HubMdns.answer(0x1234, "Tohid Hub ab12", "tohid-hub-ab12", "192.168.1.20", 47110, listOf("v=1", "port=47110"), 10, true)
    val m = Mdns.parse(reply)!!
    assertTrue(m.response)
    assertEquals(0x1234, m.id)
    assertEquals(listOf(HubMdns.Found("192.168.1.20", 47110, "Tohid Hub ab12")), HubMdns.hubs(listOf(m)))
    //  چاپگر‌یاب این را چاپگر نمی‌شمارد
    assertTrue(Mdns.printers(listOf(m)).isEmpty())
    //  نشانیِ عمومی پذیرفته نیست
    val pub = Mdns.parse(HubMdns.answer(1, "x", "h", "8.8.8.8", 47110, emptyList(), 10, false))!!
    assertTrue(HubMdns.hubs(listOf(pub)).isEmpty())
  }
}
