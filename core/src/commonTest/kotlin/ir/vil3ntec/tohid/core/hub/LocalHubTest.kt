package ir.vil3ntec.tohid.core.hub

import ir.vil3ntec.tohid.core.server.ServerProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SYN-09 — صف، فرستادن، یکتایی و ترتیبِ هابِ محلی؛ ورود با توکنِ سرور؛ انتخابِ مسیرِ دستگاه */
class LocalHubTest {

  /** سرورِ ساختگی با همان قاعدهٔ سرورِ واقعی: `op_id` ِ تکراری ⇒ «duplicate»، ردیفِ دوم نه */
  private class FakeCloud : HubCloud {
    var online = true
    var status = 200
    val tokens = HashMap<String, String>()
    val rows = LinkedHashMap<String, Long>()
    val pushes = ArrayList<List<String>>()
    var verifies = 0

    override fun push(batch: HubBatch, token: String): HubReply {
      if (!online) return HubReply(0, null)
      if (tokens[token] != batch.deviceId) return HubReply(401, """{"error":"invalid_token"}""")
      if (status != 200) return HubReply(status, """{"error":"x"}""")
      pushes += batch.opIds
      val res = batch.opIds.joinToString(",") { id ->
        val st = if (id in rows) "duplicate" else "applied".also { rows[id] = rows.size + 1L }
        """{"op_id":"$id","status":"$st","server_seq":${rows[id]}}"""
      }
      return HubReply(200, """{"ok":true,"results":[$res]}""")
    }

    override fun verify(token: String, deviceId: String, app: String): CloudCheck {
      verifies++
      if (!online) return CloudCheck.UNREACHABLE
      return if (tokens[token] == deviceId) CloudCheck.VALID else CloudCheck.INVALID
    }
  }

  private class MemBox(var state: HubQueueState = HubQueueState()) : HubBox {
    var failNext = false
    var saved = HubQueue.encode(state)
    override fun read() = state
    override fun update(change: (HubQueueState) -> HubQueueState): HubQueueState {
      val n = change(state)
      if (failNext) { failNext = false; error("سقوط پیش از ذخیره") }
      saved = HubQueue.encode(n)
      state = n
      return n
    }
  }

  private fun body(device: String, vararg ids: String) =
    """{"device_id":"$device","protocol":3,"ops":[""" +
      ids.joinToString(",") { """{"op_id":"$it","table":"sales","row_id":"r-$it","kind":"insert","fields":{"totalMinor":100}}""" } +
      """],"queued":0}"""

  private fun MemBox.put(device: String, vararg ids: String, now: Long = 1_000): HubOffer {
    val (n, r) = HubQueue.offer(state, device, ids.toList(), body(device, *ids), "shop", "2.14.5", now)
    state = n
    saved = HubQueue.encode(n)
    return r
  }

  private val tokA = "tok-A-0123456789abcdef"
  private val tokB = "tok-B-0123456789abcdef"

  private fun cloud() = FakeCloud().apply { tokens[tokA] = "devA"; tokens[tokB] = "devB" }

  private fun tokenOf(map: Map<String, String>): (String) -> String? = { map[it] }

  /* ------------------------------ بدنه و صف ------------------------------ */

  @Test
  fun parseExtractsOpIdsAndRefusesBadBodies() {
    assertEquals(HubParse.Ok(listOf("o1", "o2")), HubQueue.parse(body("devA", "o1", "o2", "o1"), "devA"))
    assertEquals(HubParse.Bad("hub_device_mismatch"), HubQueue.parse(body("devB", "o1"), "devA"))
    assertEquals(HubParse.Bad("hub_bad_json"), HubQueue.parse("[1,2]", "devA"))
    assertEquals(HubParse.Bad("hub_no_ops"), HubQueue.parse("""{"device_id":"devA"}""", "devA"))
    assertEquals(HubParse.Bad("hub_ops_count"), HubQueue.parse("""{"device_id":"devA","ops":[]}""", "devA"))
    assertEquals(HubParse.Bad("hub_bad_op_id"), HubQueue.parse(body("devA", "bad id"), "devA"))
    assertEquals(HubParse.Bad("hub_bad_op_id"), HubQueue.parse("""{"device_id":"devA","ops":[{"op_id":7}]}""", "devA"))
    val many = (1..501).map { "o$it" }.toTypedArray()
    assertEquals(HubParse.Bad("hub_ops_count"), HubQueue.parse(body("devA", *many), "devA"))
    val big = """{"device_id":"devA","pad":"${"x".repeat(330 * 1024)}","ops":[{"op_id":"o1"}]}"""
    assertEquals(HubParse.Bad("hub_too_large"), HubQueue.parse(big, "devA"))
    assertFalse(HubQueue.validDevice("dev/../x"))
    assertTrue(HubQueue.validDevice("01HXDEVICE.a_b-c"))
  }

  @Test
  fun offerStoresFreshBatchOnceAndKnowsDuplicates() {
    val box = MemBox()
    assertIs<HubOffer.Accepted>(box.put("devA", "o1", "o2"))
    assertEquals(HubOffer.Known(settled = false), box.put("devA", "o2", "o1"))
    //  دستهٔ بزرگ‌ترِ همان opها با یک op ِ تازه کامل ذخیره می‌شود؛ بدنه دست نمی‌خورد
    val wider = box.put("devA", "o1", "o2", "o3")
    assertIs<HubOffer.Accepted>(wider)
    assertEquals(body("devA", "o1", "o2", "o3"), wider.batch.body)
    assertEquals(listOf(1L, 2L), box.state.batches.map { it.seq })
    //  شناسهٔ op در دستگاهِ دیگر دسته‌ای جداست (سرور برای هر حساب می‌سنجد)
    assertIs<HubOffer.Accepted>(box.put("devB", "o1"))
  }

  @Test
  fun offerRefusesWhenDeviceOrHubIsFull() {
    val tight = HubLimits(perDevice = 2, maxBatches = 3)
    var s = HubQueueState()
    fun put(d: String, id: String) = HubQueue.offer(s, d, listOf(id), body(d, id), "", "", 1, tight).also { s = it.first }.second
    assertIs<HubOffer.Accepted>(put("devA", "a1"))
    assertIs<HubOffer.Accepted>(put("devA", "a2"))
    assertEquals(HubOffer.Full("hub_device_full"), put("devA", "a3"))
    assertIs<HubOffer.Accepted>(put("devB", "b1"))
    assertEquals(HubOffer.Full("hub_full"), put("devC", "c1"))
    val bytes = HubLimits(maxBytes = 300)
    val (_, r) = HubQueue.offer(HubQueueState(), "devA", listOf("x"), "x".repeat(301), "", "", 1, bytes)
    assertEquals(HubOffer.Full("hub_full"), r)
  }

  /* ------------------------------ فرستادن ------------------------------ */

  @Test
  fun duplicateForwardMakesOneCloudRow() {
    val c = cloud()
    val box = MemBox()
    box.put("devA", "o1", "o2")
    //  همان opها پیش‌تر از راهِ خودِ دستگاه به سرور رسیده بودند
    c.rows["o1"] = 1
    val pass = HubForwarder(box, c).drain(tokenOf(mapOf("devA" to tokA)), { _, _ -> })
    assertEquals(1, pass.forwarded)
    assertEquals(listOf("o1", "o2"), c.rows.keys.toList())
    assertTrue(box.state.batches.isEmpty())
    assertEquals(listOf("o1", "o2"), box.state.settled)
    //  دستگاه همان دسته را دوباره می‌دهد ⇒ هاب نگه نمی‌دارد و دوباره نمی‌فرستد
    assertEquals(HubOffer.Known(settled = true), box.put("devA", "o1", "o2"))
    assertEquals(1, c.pushes.size)
  }

  @Test
  fun forwardKeepsArrivalOrderPerDevice() {
    val c = cloud()
    val box = MemBox()
    box.put("devA", "a1")
    box.put("devB", "b1")
    box.put("devA", "a2", "a3")
    box.put("devB", "b2")
    val pass = HubForwarder(box, c).drain(tokenOf(mapOf("devA" to tokA, "devB" to tokB)), { _, _ -> })
    assertEquals(4, pass.forwarded)
    assertEquals(listOf(listOf("a1"), listOf("b1"), listOf("a2", "a3"), listOf("b2")), c.pushes)
    assertEquals(listOf("a1", "b1", "a2", "a3", "b2"), c.rows.keys.toList())
  }

  @Test
  fun deviceWithoutTokenWaitsWithoutBlockingOthers() {
    val c = cloud()
    val box = MemBox()
    box.put("devA", "a1")
    box.put("devB", "b1")
    box.put("devA", "a2")
    val f = HubForwarder(box, c)
    val first = f.drain(tokenOf(mapOf("devB" to tokB)), { _, _ -> })
    assertEquals(setOf("devA"), first.waiting)
    assertEquals(listOf(listOf("b1")), c.pushes)
    assertEquals(listOf("a1", "a2"), box.state.batches.flatMap { it.opIds })
    f.drain(tokenOf(mapOf("devA" to tokA)), { _, _ -> })
    assertEquals(listOf(listOf("b1"), listOf("a1"), listOf("a2")), c.pushes)
  }

  @Test
  fun crashBetweenCloudReplyAndSaveResendsAsDuplicate() {
    val c = cloud()
    val box = MemBox()
    box.put("devA", "a1")
    box.put("devA", "a2")
    box.failNext = true
    assertFailsWith<IllegalStateException> { HubForwarder(box, c).drain(tokenOf(mapOf("devA" to tokA)), { _, _ -> }) }
    assertEquals(listOf("a1"), c.rows.keys.toList())
    //  راه‌اندازیِ دوباره از همان متنِ روی دیسک — دستهٔ نخست هنوز آن‌جاست
    val restarted = MemBox(assertNotNull(HubQueue.decode(box.saved)))
    assertEquals(2, restarted.state.batches.size)
    val pass = HubForwarder(restarted, c).drain(tokenOf(mapOf("devA" to tokA)), { _, _ -> })
    assertEquals(2, pass.forwarded)
    assertEquals(listOf("a1", "a2"), c.rows.keys.toList())
    assertEquals(listOf(listOf("a1"), listOf("a1"), listOf("a2")), c.pushes)
  }

  @Test
  fun restartFromDiskTextResumesMidQueue() {
    val c = cloud()
    val box = MemBox()
    box.put("devA", "a1")
    box.put("devA", "a2")
    box.put("devA", "a3")
    val f = HubForwarder(box, c)
    f.drain(tokenOf(mapOf("devA" to tokA)), { _, _ -> }, max = 1)
    c.online = false
    assertTrue(f.drain(tokenOf(mapOf("devA" to tokA)), { _, _ -> }).retry)
    //  خاموش و روشن: صف از متن، توکن‌ها نه (فقط در حافظه بودند)
    val again = MemBox(assertNotNull(HubQueue.decode(box.saved)))
    assertEquals(listOf("a2", "a3"), again.state.batches.flatMap { it.opIds })
    assertEquals(3, again.state.seq)
    c.online = true
    val f2 = HubForwarder(again, c)
    assertEquals(setOf("devA"), f2.drain(tokenOf(emptyMap()), { _, _ -> }).waiting)
    assertEquals(2, f2.drain(tokenOf(mapOf("devA" to tokA)), { _, _ -> }).forwarded)
    assertEquals(listOf("a1", "a2", "a3"), c.rows.keys.toList())
    assertNull(HubQueue.decode("{bad"))
    assertNull(HubQueue.decode("""{"version":2}"""))
  }

  @Test
  fun outcomesRetryAuthUpgradeAndDropped() {
    assertEquals(HubOutcome.DONE, HubQueue.outcomeOf(200, """{"results":[]}"""))
    assertEquals(HubOutcome.RETRY, HubQueue.outcomeOf(200, "{}"))
    for (s in listOf(0, -1, 404, 408, 429, 500, 503)) assertEquals(HubOutcome.RETRY, HubQueue.outcomeOf(s, null), "$s")
    assertEquals(HubOutcome.AUTH, HubQueue.outcomeOf(401, null))
    assertEquals(HubOutcome.AUTH, HubQueue.outcomeOf(403, null))
    assertEquals(HubOutcome.UPGRADE, HubQueue.outcomeOf(426, null))
    for (s in listOf(400, 409, 413, 422)) assertEquals(HubOutcome.DROPPED, HubQueue.outcomeOf(s, null), "$s")

    val c = cloud()
    val box = MemBox()
    box.put("devA", "a1")
    box.put("devB", "b1")
    val failed = ArrayList<String>()
    //  توکنِ کهنهٔ A ⇒ A کنار، B می‌رود
    val p = HubForwarder(box, c).drain(tokenOf(mapOf("devA" to "tok-A-expired-000000", "devB" to tokB)), { d, _ -> failed += d })
    assertEquals(listOf("devA"), failed)
    assertEquals(setOf("devA"), p.authFailed)
    assertEquals(listOf("a1"), box.state.batches.flatMap { it.opIds })
    c.status = 426
    assertTrue(HubForwarder(box, c).drain(tokenOf(mapOf("devA" to tokA)), { _, _ -> }).upgrade)
    assertEquals(1, box.state.batches.size)
    c.status = 400
    assertEquals(1, HubForwarder(box, c).drain(tokenOf(mapOf("devA" to tokA)), { _, _ -> }).dropped)
    assertTrue(box.state.batches.isEmpty())
    assertEquals(1L, box.state.dropped)
    assertEquals(5_000L, HubForwarder.backoffMs(0))
    assertEquals(300_000L, HubForwarder.backoffMs(40))
  }

  @Test
  fun expireDropsOnlyOldBatches() {
    val box = MemBox()
    box.put("devA", "a1", now = 0)
    box.put("devA", "a2", now = 10L * 24 * 3_600_000)
    val day = 24 * 3_600_000L
    val s = HubQueue.expire(box.state, 15 * day)
    assertEquals(listOf("a2"), s.batches.flatMap { it.opIds })
    assertEquals(1L, s.dropped)
    //  ساعتِ عقب‌رفته چیزی پاک نمی‌کند
    assertEquals(box.state, HubQueue.expire(box.state, -5 * day))
  }

  /* ------------------------------ ورود ------------------------------ */

  @Test
  fun authVerifiesOnlineAndTrustsOfflineForTtl() {
    val c = cloud()
    var passes = HubPasses()
    fun ask(token: String?, device: String?, now: Long): HubVerdict {
      val r = HubAuth.decide(passes, token, device, now) { c.verify(token!!, device!!, "shop") }
      passes = HubAuth.apply(passes, r.change)
      return r.verdict
    }
    assertEquals(HubVerdict.Refuse(HubRefusal.MALFORMED), ask(null, "devA", 0))
    assertEquals(HubVerdict.Refuse(HubRefusal.MALFORMED), ask("short", "devA", 0))
    assertEquals(HubVerdict.Refuse(HubRefusal.MALFORMED), ask(tokA, "dev A", 0))
    assertEquals(0, c.verifies)
    //  بی اینترنت، توکنِ ناشناس رد
    c.online = false
    assertEquals(HubVerdict.Refuse(HubRefusal.UNVERIFIED), ask(tokA, "devA", 0))
    c.online = true
    assertIs<HubVerdict.Accept>(ask(tokA, "devA", 1_000))
    assertEquals(2, c.verifies)
    //  تا ده دقیقه بی تماس
    assertEquals(HubVerdict.Accept(passes.passes.single(), offline = false), ask(tokA, "devA", 1_000 + HubAuth.RECHECK_MS))
    assertEquals(2, c.verifies)
    c.online = false
    assertEquals(true, (ask(tokA, "devA", 1_000 + HubAuth.OFFLINE_TTL_MS) as HubVerdict.Accept).offline)
    assertEquals(HubVerdict.Refuse(HubRefusal.STALE), ask(tokA, "devA", 2_000 + HubAuth.OFFLINE_TTL_MS))
    //  روی دیسک فقط درهم است، نه خودِ توکن
    val text = HubAuth.encode(passes)
    assertFalse(tokA in text)
    assertEquals(passes, HubAuth.decode(text))
  }

  @Test
  fun authBindsTokenToOneDeviceAndForgetsInvalid() {
    val c = cloud()
    var passes = HubPasses()
    fun ask(token: String, device: String, now: Long = 0): HubVerdict {
      val r = HubAuth.decide(passes, token, device, now) { c.verify(token, device, "shop") }
      passes = HubAuth.apply(passes, r.change)
      return r.verdict
    }
    assertIs<HubVerdict.Accept>(ask(tokA, "devA"))
    assertEquals(HubVerdict.Refuse(HubRefusal.MISMATCH), ask(tokA, "devB"))
    //  سرور توکن را باطل کرد ⇒ از فهرست بیرون
    c.tokens.remove(tokA)
    assertEquals(HubVerdict.Refuse(HubRefusal.INVALID), ask(tokA, "devA", HubAuth.RECHECK_MS + 1))
    assertTrue(passes.passes.isEmpty())
    c.online = false
    assertEquals(HubVerdict.Refuse(HubRefusal.UNVERIFIED), ask(tokA, "devA", HubAuth.RECHECK_MS + 2))
    //  revoke ِ پس از ۴۰۱ ِ فرستادن
    c.online = true
    ask(tokB, "devB")
    assertTrue(HubAuth.revoke(passes, tokB).passes.isEmpty())
  }

  @Test
  fun authClockBackwardsNeedsTheCloud() {
    val p = HubPasses(passes = listOf(HubPass(HubAuth.hashOf(tokA), "devA", 10_000_000)))
    var asked = 0
    val r = HubAuth.decide(p, tokA, "devA", 5_000) { asked++; CloudCheck.UNREACHABLE }
    assertEquals(1, asked)
    assertEquals(HubVerdict.Refuse(HubRefusal.STALE), r.verdict)
    //  سقفِ فهرست
    var many = HubPasses()
    for (i in 0 until HubAuth.MAX_PASSES + 10) many = HubAuth.apply(many, HubPassChange.Put(HubPass("h$i", "d$i", i.toLong())))
    assertEquals(HubAuth.MAX_PASSES, many.passes.size)
    assertEquals("h10", many.passes.first().hash)
  }

  @Test
  fun limiterAllowsBurstThenRefills() {
    val l = HubLimiter(perMinute = 60, burst = 3, maxKeys = 2)
    assertTrue(l.allow("ip1", 0))
    assertTrue(l.allow("ip1", 0))
    assertTrue(l.allow("ip1", 0))
    assertFalse(l.allow("ip1", 0))
    assertTrue(l.allow("ip1", 1_000))
    assertFalse(l.allow("ip1", 1_000))
    assertTrue(l.allow("ip2", 0))
    //  کلیدِ سوم کهنه‌ترین را بیرون می‌کند؛ ip1 دوباره پُر شروع می‌کند
    assertTrue(l.allow("ip3", 1_000))
    assertTrue(l.allow("ip1", 1_000))
  }

  /* ------------------------------ مسیرِ دستگاه ------------------------------ */

  @Test
  fun routePrefersCloudAndUsesHubOnlyForSameServer() {
    val sid = "srv_0123456789abcdef0123"
    val profile = ServerProfile(id = "p1", name = "دکان", urls = listOf("https://api.example.com"), serverId = sid)
    val pin = "ab".repeat(32)
    val hub = HubRoute.HubInfo(sid, HubRoute.PROTOCOL)
    val lan = "https://192.168.1.20:47110"
    fun route(
      enabled: Boolean = true, p: ServerProfile? = profile, cloud: Boolean = false,
      url: String? = lan, hubPin: String? = pin, info: HubRoute.HubInfo? = hub,
    ) = HubRoute.choose(enabled, p, cloud, url, hubPin, info)

    assertEquals(HubRoute.Decision(HubRoute.Route.CLOUD, HubRoute.Why.OFF), route(enabled = false))
    assertEquals(HubRoute.Why.OFF, route(p = null).why)
    assertEquals(HubRoute.Decision(HubRoute.Route.CLOUD, HubRoute.Why.CLOUD_OK), route(cloud = true))
    assertEquals(HubRoute.Why.NO_HUB, route(url = null).why)
    assertEquals(HubRoute.Why.UNPINNED_SERVER, route(p = profile.copy(serverId = null)).why)
    assertEquals(HubRoute.Why.HUB_NOT_LAN, route(url = "https://hub.example.com").why)
    assertEquals(HubRoute.Why.HUB_NOT_LAN, route(url = "http://192.168.1.20:47110").why)
    assertEquals(HubRoute.Why.HUB_NOT_LAN, route(url = "https://127.0.0.1:47110").why)
    assertEquals(HubRoute.Why.HUB_NOT_PINNED, route(hubPin = null).why)
    assertEquals(HubRoute.Why.HUB_UNREACHABLE, route(info = null).why)
    assertEquals(HubRoute.Why.HUB_UNREACHABLE, route(info = hub.copy(protocol = 2)).why)
    assertEquals(HubRoute.Why.OTHER_SERVER, route(info = hub.copy(serverId = "srv_fedcba9876543210fedc")).why)
    assertEquals(HubRoute.Why.OTHER_SERVER, route(info = hub.copy(serverId = null)).why)
    assertEquals(HubRoute.Decision(HubRoute.Route.HUB, HubRoute.Why.OK), route())

    assertEquals(hub, HubRoute.parseHello("""{"ok":true,"hub":1,"server_id":"$sid","queued":0}"""))
    assertNull(HubRoute.parseHello("""{"ok":false,"hub":1}"""))
    assertNull(HubRoute.parseHello("nope"))
  }
}
