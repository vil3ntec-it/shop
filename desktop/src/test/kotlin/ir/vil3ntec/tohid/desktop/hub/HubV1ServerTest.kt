package ir.vil3ntec.tohid.desktop.hub

import ir.vil3ntec.tohid.core.hub.CloudAnswer
import ir.vil3ntec.tohid.core.hub.CloudCheck
import ir.vil3ntec.tohid.core.hub.HubBatch
import ir.vil3ntec.tohid.core.hub.HubCloud
import ir.vil3ntec.tohid.core.hub.HubEntry
import ir.vil3ntec.tohid.core.hub.HubLog
import ir.vil3ntec.tohid.core.hub.HubMdns
import ir.vil3ntec.tohid.core.hub.HubPair
import ir.vil3ntec.tohid.core.hub.HubReply
import ir.vil3ntec.tohid.core.hub.HubV1Cloud
import ir.vil3ntec.tohid.core.hw.MdnsProbe
import ir.vil3ntec.tohid.platform.HubExchangeV1
import ir.vil3ntec.tohid.platform.HubPairClient
import ir.vil3ntec.tohid.platform.HubPeerLink
import ir.vil3ntec.tohid.platform.PinnedTls
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.nio.file.Files
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

/**
 *  S29/SYN-09 — هابِ واقعی روی `127.0.0.1` با TLS ِ سنجاق‌شده و سرورِ ساختگی با قاعدهٔ سرورِ واقعی (`op_id` ِ تکراری ⇒
 *  «duplicate»): جفت‌سازی با QR و کد (سرخ: بازپخشِ برگه، دستگاهِ جفت‌نشده، دکانِ دیگر، نقشِ شاگرد)، دفترِ v1 و خوراکِ
 *  دستگاه‌های دیگر با همان `HubPeerLink` ِ گوشی، ماندن روی دیسک، و پاسخ‌دهندهٔ mDNS.
 */
class HubV1ServerTest {

  private class Cloud : HubCloud, HubV1Cloud {
    @Volatile var online = true
    val tokens = HashMap<String, String>()
    val shops = HashMap<String, String>()
    val rows = LinkedHashMap<String, Long>()
    var verifies = 0
    var pushes = 0

    @Synchronized override fun push(batch: HubBatch, token: String) = HubReply(404, "{}")

    @Synchronized override fun verify(token: String, deviceId: String, app: String): CloudAnswer {
      verifies++
      if (!online) return CloudAnswer(CloudCheck.UNREACHABLE)
      return if (tokens[token] == deviceId) CloudAnswer(CloudCheck.VALID, shops[token] ?: SHOP) else CloudAnswer(CloudCheck.INVALID)
    }

    @Synchronized override fun pushV1(entry: HubEntry, token: String): HubReply {
      if (!online) return HubReply(0, null)
      if (tokens[token] != entry.deviceId) return HubReply(401, "{}")
      pushes++
      return HubReply(200, """{"ok":true,"results":[${apply(entry.opIds)}]}""")
    }

    /** خودِ گوشی، بی هاب */
    @Synchronized fun direct(ids: List<String>) = apply(ids)

    private fun apply(ids: List<String>) = ids.joinToString(",") { id ->
      val st = if (id in rows) "duplicate" else "applied".also { rows[id] = rows.size + 1L }
      """{"op_id":"$id","status":"$st","server_seq":${rows[id]}}"""
    }
  }

  private companion object {
    const val SHOP = "shop-A"
    const val SID = "srv_0123456789abcdef0123"
  }

  private val tokA = "tok-A-0123456789abcdef"
  private val tokB = "tok-B-0123456789abcdef"
  private val tokX = "tok-X-0123456789abcdef"
  private val seal: (String) -> String = { "S1:" + Base64.getEncoder().encodeToString(it.toByteArray().reversedArray()) }
  private val open: (String) -> String? = { s -> if (s.startsWith("S1:")) String(Base64.getDecoder().decode(s.substring(3)).reversedArray()) else null }
  private val started = ArrayList<HubServer>()
  private val dirs = ArrayList<File>()
  private var clock = 1_700_000_000_000L

  @After fun cleanUp() {
    started.forEach { runCatching { it.stop() } }
    dirs.forEach { it.deleteRecursively() }
  }

  private fun newDir() = Files.createTempDirectory("tohid-hub-v1").toFile().also { dirs += it }

  private fun cloud() = Cloud().apply { tokens[tokA] = "devA"; tokens[tokB] = "devB"; tokens[tokX] = "devX"; shops[tokX] = "shop-B" }

  private class Hub(val server: HubServer, val pin: String) {
    val url get() = "https://127.0.0.1:${server.port}"
    val v1 get() = server.v1!!
  }

  private fun hub(dir: File, cloud: Cloud, burst: Int = 200): Hub {
    val tls = HubTls.loadOrCreate(dir, seal, open)
    val s = HubServer(dir, tls, SID, SHOP, cloud, seal, open, clock = { clock }, autoForward = false, requestsPerMinute = 600, requestBurst = burst, v1Cloud = cloud)
    s.start(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    started += s
    return Hub(s, tls.pin)
  }

  private fun id(token: String, device: String) = mapOf("Authorization" to "Bearer $token", "X-Device" to device, "X-App" to "shop", "X-App-Version" to "2.14.5")

  private fun raw(h: Hub, method: String, path: String, headers: Map<String, String>, body: String? = null): Pair<Int, String> {
    val c = URL(h.url + path).openConnection(Proxy.NO_PROXY) as HttpsURLConnection
    PinnedTls.of(h.pin).applyTo(c)
    c.requestMethod = method
    c.connectTimeout = 5_000
    c.readTimeout = 10_000
    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
    if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
    val st = c.responseCode
    return st to ((if (st >= 400) c.errorStream else c.inputStream)?.use { it.readBytes().decodeToString() }.orEmpty())
  }

  /** جفت با QR روی نشانیِ loopback (نشانیِ QR فقط https ِ شبکهٔ دکان می‌پذیرد؛ همان توکن و سنجاق) */
  private fun pair(h: Hub, token: String, device: String): HubPairClient.Paired {
    val t = h.v1.status().ticket ?: run { h.v1.newTicket("manager", "مدیر"); h.v1.status().ticket!! }
    val (st, text) = raw(h, "POST", "/hub/v1/pair", id(token, device), """{"secret":"${t.token}"}""")
    assertEquals(text, 200, st)
    val key = (HubPeerLink.parse(text)!!["key"] as JsonPrimitive).content
    return HubPairClient.Paired(h.url, h.pin, key, SID)
  }

  private fun link(p: HubPairClient.Paired, token: String, device: String) = HubPeerLink(p.url, p.pin, p.key, p.serverId, { id(token, device) })

  private fun ops(vararg ids: String) = ids.mapIndexed { i, it -> SyncOp(it, 1_000L + i, "products", "r-$it", SyncOp.INSERT, JsonObject(mapOf("name" to JsonPrimitive(it)))) }

  private class MemStore : HubExchangeV1.Store {
    override var since = 0L
    override var epoch = ""
    val cloud = HashSet<String>()
    override fun seenFromCloud(ids: Collection<String>) = ids.filterTo(HashSet()) { it in cloud }
    override fun sawFromCloud(ids: Collection<String>) { cloud += ids }
  }

  /* ------------------------------ جفت‌سازی ------------------------------ */

  @Test fun qrPairingWorksOnceAndAReplayOfTheSameTokenIsRejected() {
    val c = cloud()
    val h = hub(newDir(), c)
    h.v1.newTicket("owner", "صاحب")
    val token = h.v1.status().ticket!!.token
    val (st, text) = raw(h, "POST", "/hub/v1/pair", id(tokA, "devA"), """{"secret":"$token"}""")
    assertEquals(text, 200, st)
    val o = HubPeerLink.parse(text)!!
    assertEquals(SID, (o["server_id"] as JsonPrimitive).content)
    assertEquals(h.pin, (o["pin"] as JsonPrimitive).content)
    //  بازپخشِ همان توکن — با دستگاهِ دیگرِ همان دکان، و با همان دستگاه
    val (st2, text2) = raw(h, "POST", "/hub/v1/pair", id(tokB, "devB"), """{"secret":"$token"}""")
    assertEquals(403, st2)
    assertTrue(text2, text2.contains("hub_pair_used"))
    assertEquals(403, raw(h, "POST", "/hub/v1/pair", id(tokA, "devA"), """{"secret":"$token"}""").first)
    assertEquals(listOf("devA"), h.v1.status().peers.map { it.deviceId })
    //  QR ِ واقعی (نشانیِ شبکهٔ دکان) را `HubPairClient` می‌خواند و همان سنجاق را نگه می‌دارد
    val q = HubPair.parseQr(HubPair.qr("https://192.168.1.20:47110", SID, h.pin, token))!!
    assertEquals(h.pin, q.pin)
    assertTrue("QR ِ بدشکل", HubPairClient.withQr("tohid-hub:1?u=http://x", id(tokB, "devB")) is HubPairClient.Result.Failed)
  }

  @Test fun codePairingChecksTheHubProofBeforeSendingAnySecret() {
    val c = cloud()
    val h = hub(newDir(), c)
    h.v1.newTicket("owner", "صاحب")
    val code = h.v1.status().ticket!!.code
    //  کدِ نادرست ⇒ اثباتِ هاب جور نمی‌شود ⇒ هیچ توکنی به هاب نرفت (سنجشی نزدِ سرور هم نشد)
    val wrong = ((code.toInt() + 1) % 1_000_000).toString().padStart(6, '0')
    val before = c.verifies
    assertEquals(HubPairClient.Result.Failed("hub_not_trusted"), HubPairClient.withCode(h.url, wrong, id(tokB, "devB")))
    assertEquals(before, c.verifies)
    val r = HubPairClient.withCode(h.url, code, id(tokB, "devB"))
    assertTrue(r.toString(), r is HubPairClient.Result.Ok)
    assertEquals(h.pin, (r as HubPairClient.Result.Ok).paired.pin)
    assertEquals(SID, r.paired.serverId)
    //  همان کد دوباره ⇒ رد
    assertEquals(HubPairClient.Result.Failed("hub_pair_used"), HubPairClient.withCode(h.url, code, id(tokA, "devA")))
  }

  @Test fun onlyOwnerOrManagerCanOpenPairingAndNoTicketMeansNoPairing() {
    val c = cloud()
    val h = hub(newDir(), c)
    assertEquals(HubPair.Issue.Denied(HubPair.Refusal.ROLE), h.v1.newTicket("staff", "شاگرد"))
    assertEquals(HubPair.Issue.Denied(HubPair.Refusal.ROLE), h.v1.newTicket("cashier", "صندوق"))
    val before = c.verifies
    val (st, text) = raw(h, "POST", "/hub/v1/pair", id(tokA, "devA"), """{"secret":"123456"}""")
    assertEquals(403, st)
    assertTrue(text.contains("hub_pair_none"))
    assertEquals("بی برگه هیچ سنجشی نزدِ سرور", before, c.verifies)
    //  برگهٔ گذشته
    h.v1.newTicket("owner", "صاحب")
    val t = h.v1.status().ticket!!
    clock += HubPair.TTL_MS + 1
    assertTrue(raw(h, "POST", "/hub/v1/pair", id(tokA, "devA"), """{"secret":"${t.code}"}""").second.contains("hub_pair_expired"))
  }

  @Test fun otherShopTokenCannotPairAndDoesNotBurnTheTicket() {
    val c = cloud()
    val h = hub(newDir(), c)
    h.v1.newTicket("owner", "صاحب")
    val t = h.v1.status().ticket!!
    val (st, text) = raw(h, "POST", "/hub/v1/pair", id(tokX, "devX"), """{"secret":"${t.code}"}""")
    assertEquals(403, st)
    assertTrue(text, text.contains("hub_other_shop"))
    assertTrue("برگه سالم ماند", HubPair.alive(h.v1.status().ticket, clock))
    assertEquals(200, raw(h, "POST", "/hub/v1/pair", id(tokA, "devA"), """{"secret":"${t.code}"}""").first)
  }

  @Test fun unpairedDeviceSeesNothingAndCannotPushEvenWithAValidToken() {
    val c = cloud()
    val h = hub(newDir(), c)
    val a = pair(h, tokA, "devA")
    link(a, tokA, "devA").relay(ir.vil3ntec.tohid.sync.v1.SyncV1Client.pushBody("devA", ops("a1"), 1, true, 1, null))
    val before = c.verifies
    val headers = id(tokB, "devB") + ("X-Server-Id" to SID)
    for ((m, p, b) in listOf(Triple("GET", "/hub/v1/ops?since=0&cursor=0", null), Triple("POST", "/hub/v1/ops/push", """{"device_id":"devB","ops":[{"op_id":"b1"}]}"""))) {
      val (st, text) = raw(h, m, p, headers, b)
      assertEquals("$m $p", 403, st)
      assertTrue(text.contains("hub_not_paired"))
      //  کلیدِ ساختگی، و کلیدِ دستگاهِ دیگر
      assertEquals(403, raw(h, m, p, headers + (HubPair.KEY_HEADER to "00".repeat(32)), b).first)
      assertEquals(403, raw(h, m, p, headers + (HubPair.KEY_HEADER to a.key), b).first)
    }
    assertEquals("جفت‌نشده سرور را هم نمی‌کوبد", before, c.verifies)
    //  جفت‌شده با توکنِ نادرست ⇒ ۴۰۱
    val (st, _) = raw(h, "GET", "/hub/v1/ops?since=0&cursor=0", id("tok-bad-0123456789abcdef", "devA") + ("X-Server-Id" to SID) + (HubPair.KEY_HEADER to a.key))
    assertEquals(401, st)
    //  «جدا کردن» ⇒ همان دستگاه دیگر هیچ
    h.v1.unpair("devA")
    val after = link(a, tokA, "devA").relay(ir.vil3ntec.tohid.sync.v1.SyncV1Client.pushBody("devA", ops("a2"), 1, true, 1, null))
    assertEquals(403, after.status)
    assertEquals("hub_not_paired", after.error)
  }

  @Test fun pairEndpointsAreRateLimitedPerAddress() {
    val h = hub(newDir(), cloud())
    h.v1.newTicket("owner", "صاحب")
    val codes = (1..8).map { raw(h, "POST", "/hub/v1/pair/hello", emptyMap(), """{"nonce":"${"0a".repeat(16)}"}""").first }
    assertTrue(codes.toString(), 429 in codes)
    //  و اثبات‌خواهی سقفِ خودش را دارد (۵)
    assertTrue(codes.toString(), codes.count { it == 200 } <= HubPair.MAX_ASKS)
  }

  /* ------------------------------ دفتر و رد و بدل ------------------------------ */

  @Test fun opsThroughTheHubReachPeersAndTheCloudExactlyOnce() {
    val c = cloud()
    val h = hub(newDir(), c)
    val a = pair(h, tokA, "devA")
    val b = pair(h, tokB, "devB")
    val la = link(a, tokA, "devA")
    val lb = link(b, tokB, "devB")
    c.online = false
    //  گوشیِ A بی اینترنت: صفش (۴۵۰ op، چند دسته) به هاب؛ گوشیِ B همان‌ها را می‌گیرد
    val queueA = ops(*(1..450).map { "a$it" }.toTypedArray())
    val sa = MemStore()
    val outA = HubExchangeV1(la, sa).exchange("devA", queueA, queueA.size, { true }, 1, null, 0) { }
    assertEquals(450, outA.relayed)
    val got = ArrayList<SyncOp>()
    val sb = MemStore()
    val outB = HubExchangeV1(lb, sb).exchange("devB", ops("b1"), 1, { true }, 1, null, 0) { got += it }
    assertEquals(1, outB.relayed)
    assertEquals(queueA.map { it.opId }, got.map { it.opId })
    //  دورِ دوباره: هیچ دستهٔ تازه؛ A فقط b1 را می‌گیرد و B هیچ op ِ دوباره
    val gotA = ArrayList<SyncOp>()
    HubExchangeV1(la, sa).exchange("devA", queueA, queueA.size, { true }, 1, null, 0) { gotA += it }
    assertEquals(listOf("b1"), gotA.map { it.opId })
    got.clear()
    HubExchangeV1(lb, sb).exchange("devB", ops("b1"), 1, { true }, 1, null, 0) { got += it }
    assertTrue(got.isEmpty())
    gotA.clear()
    HubExchangeV1(la, sa).exchange("devA", emptyList(), 0, { true }, 1, null, 0) { gotA += it }
    assertTrue(gotA.isEmpty())
    val log = h.server.v1!!.snapshot()
    assertEquals(450 + 1, log.entries.flatMap { it.opIds }.size)
    //  سرور نمی‌رسد ⇒ هیچ
    assertTrue(h.server.forwardV1Now()!!.retry)
    assertTrue(c.rows.isEmpty())
    //  اینترنت برگشت: هاب فرستاد؛ خودِ گوشی هم همان‌ها را می‌فرستد ⇒ «duplicate»، ردیفِ دوم نه
    c.online = true
    val pass = h.server.forwardV1Now()!!
    assertFalse(pass.retry)
    assertEquals(451, c.rows.size)
    assertTrue(c.direct(queueA.map { it.opId }).contains("duplicate"))
    assertEquals(451, c.rows.size)
    assertEquals((1..450).map { "a$it" }, c.rows.keys.filter { it.startsWith("a") })
    assertEquals(0, HubLog.pending(h.server.v1!!.snapshot()).size)
    //  گوشیِ جلوتر از سرور (cursor ِ ۴۵۱) چیزی از هاب نمی‌گیرد
    val sc = MemStore()
    val late = ArrayList<SyncOp>()
    HubExchangeV1(lb, sc).exchange("devB", emptyList(), 0, { true }, 1, null, 451) { late += it }
    assertTrue(late.map { it.opId }.toString(), late.isEmpty())
  }

  @Test fun logSurvivesRestartAndAHalfWrittenLineIsSetAside() {
    val c = cloud()
    val dir = newDir()
    val h = hub(dir, c)
    val a = pair(h, tokA, "devA")
    c.online = false
    val la = link(a, tokA, "devA")
    la.relay(ir.vil3ntec.tohid.sync.v1.SyncV1Client.pushBody("devA", ops("a1", "a2"), 2, true, 1, null))
    la.relay(ir.vil3ntec.tohid.sync.v1.SyncV1Client.pushBody("devA", ops("a3"), 1, true, 1, null))
    val epoch = h.v1.snapshot().marks.epoch
    h.server.stop()
    //  سقوط وسطِ افزودن: نیمِ یک خط
    File(dir, HubV1.LOG_FILE).appendText("S1:AAAA")
    assertFalse("بدنهٔ op باز روی دیسک", File(dir, HubV1.LOG_FILE).readText().contains("a1"))
    val h2 = hub(dir, c)
    val s = h2.v1.snapshot()
    assertEquals(listOf(1L, 2L), s.entries.map { it.seq })
    assertEquals(epoch, s.marks.epoch)
    assertEquals("جفت‌شده‌ها ماندند", listOf("devA"), h2.v1.status().peers.map { it.deviceId })
    //  توکن فقط در حافظه بود ⇒ تا دستگاه دوباره بیاید منتظر
    c.online = true
    assertEquals(setOf("devA"), h2.server.forwardV1Now()!!.waiting)
    val l2 = link(a.copy(url = h2.url), tokA, "devA")
    assertTrue(l2.feed(0, 0) != null)
    h2.server.forwardV1Now()
    assertEquals(listOf("a1", "a2", "a3"), c.rows.keys.toList())
    val (n, r) = HubLog.append(s, "devA", listOf("a4"), "{}", "", "", "", clock)
    assertEquals(3L, (r as ir.vil3ntec.tohid.core.hub.HubAppend.Accepted).entry.seq)
    assertTrue(n.head == 3L)
  }

  /* ------------------------------ mDNS ------------------------------ */

  @Test fun mdnsResponderAnswersOnlyHubQueriesOnTheLan() {
    val udp = java.net.DatagramSocket(0, InetAddress.getLoopbackAddress()).use { it.localPort }
    val ann = HubAnnounce("Tohid Hub test", "tohid-hub-test", 47110, { "192.168.7.9" }, bindPort = udp, group = null,
      bindAddress = InetAddress.getLoopbackAddress(), sourceOk = { it.isLoopbackAddress })
    ann.start()
    try {
      val found = HubMdns.hubs(MdnsProbe.messages("127.0.0.1", 1_500, HubMdns.SERVICE, "127.0.0.1", udp, sourceOk = { true }))
      assertEquals(listOf(HubMdns.Found("192.168.7.9", 47110, "Tohid Hub test")), found)
      val before = ann.answered
      //  پرسشِ چاپگر بی پاسخ
      assertTrue(MdnsProbe.messages("127.0.0.1", 600, ir.vil3ntec.tohid.core.hw.Mdns.PDL, "127.0.0.1", udp, sourceOk = { true }).isEmpty())
      assertEquals(before, ann.answered)
    } finally {
      ann.stop()
    }
  }
}
