package ir.vil3ntec.tohid.desktop.hub

import ir.vil3ntec.tohid.core.hub.CloudCheck
import ir.vil3ntec.tohid.core.hub.HubBatch
import ir.vil3ntec.tohid.core.hub.HubCloud
import ir.vil3ntec.tohid.core.hub.HubLimits
import ir.vil3ntec.tohid.core.hub.HubQueue
import ir.vil3ntec.tohid.core.hub.HubReply
import ir.vil3ntec.tohid.platform.PinnedTls
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.nio.file.Files
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

/**
 *  SYN-09 — هابِ واقعی روی `127.0.0.1` با TLS ِ سنجاق‌شده و سرورِ ساختگی که قاعدهٔ سرورِ واقعی را دارد
 *  (`op_id` ِ تکراری ⇒ «duplicate»). فرستادن دستی است (`autoForward = false`) تا ترتیب قطعی باشد.
 */
class HubServerTest {

  private class FakeCloud : HubCloud {
    @Volatile var online = true
    /** چند push پیش از قطع شدن */
    @Volatile var allow = Int.MAX_VALUE
    val tokens = HashMap<String, String>()
    val rows = LinkedHashMap<String, Int>()
    val pushes = ArrayList<List<String>>()
    var verifies = 0

    @Synchronized override fun push(batch: HubBatch, token: String): HubReply {
      if (!online || pushes.size >= allow) return HubReply(0, null)
      if (tokens[token] != batch.deviceId) return HubReply(401, """{"error":"invalid_token"}""")
      pushes += batch.opIds
      return HubReply(200, """{"ok":true,"results":[${apply(batch.opIds).joinToString(",")}]}""")
    }

    @Synchronized override fun verify(token: String, deviceId: String, app: String): CloudCheck {
      verifies++
      if (!online) return CloudCheck.UNREACHABLE
      return if (tokens[token] == deviceId) CloudCheck.VALID else CloudCheck.INVALID
    }

    /** خودِ دستگاه، بی هاب، همان opها را می‌فرستد */
    @Synchronized fun direct(ids: List<String>): List<String> = apply(ids).map { it.substringAfter("\"status\":\"").substringBefore('"') }

    private fun apply(ids: List<String>) = ids.map { id ->
      val st = if (id in rows) "duplicate" else "applied".also { rows[id] = rows.size + 1 }
      """{"op_id":"$id","status":"$st","server_seq":${rows[id]}}"""
    }
  }

  private data class Res(val status: Int, val body: String)

  private class Hub(val server: HubServer, val pin: String, val dir: File) {
    val port get() = server.port
  }

  private val sid = "srv_0123456789abcdef0123"
  private val tokA = "tok-A-0123456789abcdef"
  private val tokB = "tok-B-0123456789abcdef"
  private val seal: (String) -> String = { "S1:" + Base64.getEncoder().encodeToString(it.toByteArray().reversedArray()) }
  private val open: (String) -> String? = { s ->
    if (s.startsWith("S1:")) String(Base64.getDecoder().decode(s.substring(3)).reversedArray()) else null
  }
  private val started = ArrayList<HubServer>()
  private val dirs = ArrayList<File>()

  @After fun cleanUp() {
    started.forEach { runCatching { it.stop() } }
    dirs.forEach { it.deleteRecursively() }
  }

  private fun newDir() = Files.createTempDirectory("tohid-hub-test").toFile().also { dirs += it }

  private fun cloud() = FakeCloud().apply { tokens[tokA] = "devA"; tokens[tokB] = "devB" }

  private fun hub(dir: File, cloud: FakeCloud, burst: Int = 60, perMinute: Int = 120, limits: HubLimits = HubLimits()): Hub {
    val tls = HubTls.loadOrCreate(dir, seal, open)
    val s = HubServer(
      dir, tls, sid, cloud, seal, open, autoForward = false, limits = limits, requestsPerMinute = perMinute, requestBurst = burst,
    )
    s.start(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    started += s
    return Hub(s, tls.pin, dir)
  }

  private fun body(device: String, vararg ids: String) =
    """{"device_id":"$device","protocol":3,"ops":[""" +
      ids.joinToString(",") { """{"op_id":"$it","table":"sales","row_id":"r-$it","kind":"insert","fields":{"totalMinor":100}}""" } +
      """],"queued":0}"""

  private fun call(
    h: Hub, method: String, path: String, token: String?, device: String?,
    body: String? = null, serverId: String? = sid, pin: String = h.pin,
  ): Res {
    val c = URL("https://127.0.0.1:${h.port}$path").openConnection(Proxy.NO_PROXY) as HttpsURLConnection
    PinnedTls.of(pin).applyTo(c)
    c.requestMethod = method
    c.connectTimeout = 5_000
    c.readTimeout = 10_000
    token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
    device?.let { c.setRequestProperty("X-Device", it) }
    serverId?.let { c.setRequestProperty("X-Server-Id", it) }
    c.setRequestProperty("X-App", "shop")
    c.setRequestProperty("X-App-Version", "2.14.5")
    if (body != null) {
      c.doOutput = true
      c.setRequestProperty("Content-Type", "application/json")
      c.outputStream.use { it.write(body.toByteArray()) }
    }
    val st = c.responseCode
    val text = (if (st >= 400) c.errorStream else c.inputStream)?.use { it.readBytes().decodeToString() }.orEmpty()
    return Res(st, text)
  }

  private fun hello(h: Hub, token: String?, device: String?) = call(h, "GET", "/hub/v1/hello", token, device)
  private fun push(h: Hub, token: String, device: String, vararg ids: String) =
    call(h, "POST", "/hub/v1/push", token, device, body(device, *ids))

  /* ------------------------------ امنیت ------------------------------ */

  @Test fun `هاب فقط با گواهیِ سنجاق‌شده و توکنِ سنجیده جواب می‌دهد`() {
    val c = cloud()
    val h = hub(newDir(), c)
    try {
      call(h, "GET", "/hub/v1/hello", tokA, "devA", pin = "00".repeat(32))
      fail("گواهیِ ناهم‌خوان باید دست‌دادن را بشکند")
    } catch (_: SSLException) {
    }
    assertEquals("توکن پیش از سنجاق نباید به هاب برسد", 0, c.verifies)
    assertEquals(401, hello(h, null, "devA").status)
    assertTrue(hello(h, null, "devA").body.contains("hub_auth_required"))
    assertEquals(401, hello(h, "tok-X-0123456789abcdef", "devA").status)
    val ok = hello(h, tokA, "devA")
    assertEquals(200, ok.status)
    assertTrue(ok.body.contains("\"server_id\":\"$sid\""))
    assertTrue(ok.body.contains("\"hub\":1"))
    //  همان توکن با شناسهٔ دیگر
    assertEquals(403, hello(h, tokA, "devB").status)
    assertEquals(404, call(h, "GET", "/api/v1/sync/v3/pull", tokA, "devA").status)
  }

  @Test fun `بی اینترنت توکنِ ناشناس رد و توکنِ سنجیده پذیرفته است`() {
    val c = cloud()
    val h = hub(newDir(), c)
    assertEquals(200, hello(h, tokA, "devA").status)
    c.online = false
    assertEquals(200, push(h, tokA, "devA", "a1").status)
    val r = hello(h, tokB, "devB")
    assertEquals(401, r.status)
    assertTrue(r.body.contains("hub_unverified"))
  }

  @Test fun `درخواستِ زیاد ۴۲۹ و بدنهٔ بزرگ ۴۱۳ و سرورِ دیگر ۴۰۹ می‌گیرد`() {
    val c = cloud()
    val h = hub(newDir(), c, burst = 5, perMinute = 1, limits = HubLimits(maxBodyBytes = 2_048))
    assertEquals(200, hello(h, tokA, "devA").status)
    assertEquals(409, call(h, "POST", "/hub/v1/push", tokA, "devA", body("devA", "a1"), serverId = "srv_fedcba9876543210fedc").status)
    val big = """{"device_id":"devA","pad":"${"x".repeat(3_000)}","ops":[{"op_id":"a1"}]}"""
    assertEquals(413, call(h, "POST", "/hub/v1/push", tokA, "devA", big).status)
    assertEquals(400, call(h, "POST", "/hub/v1/push", tokA, "devA", body("devB", "a1")).status)
    assertEquals(400, call(h, "POST", "/hub/v1/push", tokA, "devA", "not json").status)
    val limited = hello(h, tokA, "devA")
    assertEquals(429, limited.status)
    assertTrue(limited.body.contains("hub_rate_limited"))
    assertTrue(h.server.snapshot().batches.isEmpty())
  }

  @Test fun `فقط نشانی‌های شبکهٔ دکان پذیرفته‌اند`() {
    for (a in listOf("127.0.0.1", "10.0.0.7", "172.16.5.4", "192.168.1.20", "169.254.3.3", "::1", "fe80::1", "fd12::1")) {
      assertTrue(a, HubServer.lanPeer(InetAddress.getByName(a)))
    }
    for (a in listOf("8.8.8.8", "172.32.0.1", "100.64.0.1", "2001:db8::1", "2a00:1450::1")) {
      assertFalse(a, HubServer.lanPeer(InetAddress.getByName(a)))
    }
  }

  @Test fun `کلید و صف و توکن باز روی دیسک نیستند و سنجاق پس از راه‌اندازیِ دوباره همان است`() {
    val c = cloud()
    val dir = newDir()
    val h = hub(dir, c)
    assertEquals(200, push(h, tokA, "devA", "opSecretA1").status)
    h.server.stop()
    val raw = dir.walkTopDown().filter { it.isFile }.map { it.readBytes().toString(Charsets.ISO_8859_1) }.joinToString("\n")
    assertFalse(raw.contains(tokA))
    assertFalse(raw.contains("opSecretA1"))
    assertTrue(File(dir, HubTls.KEY_FILE).readText().startsWith("S1:"))
    assertEquals(h.pin, HubTls.loadOrCreate(dir, seal, open).pin)
    //  کلیدِ دست‌کاری‌شده ⇒ جفتِ تازه، نه اعتمادِ بی‌صدا
    File(dir, HubTls.KEY_FILE).writeText("S1:AAAA")
    assertNotEquals(h.pin, HubTls.loadOrCreate(dir, seal, open).pin)
  }

  /* ------------------------------ یکتایی و ترتیب ------------------------------ */

  @Test fun `push تکراری یک بار در صف و یک ردیف در سرور می‌سازد`() {
    val c = cloud()
    val h = hub(newDir(), c)
    assertEquals(200, hello(h, tokA, "devA").status)
    c.online = false
    val first = push(h, tokA, "devA", "a1", "a2")
    assertEquals(200, first.status)
    assertTrue(first.body.contains("\"accepted\":true"))
    assertTrue(first.body.contains("hub_queued"))
    val again = push(h, tokA, "devA", "a1", "a2")
    assertTrue(again.body.contains("\"accepted\":false"))
    assertEquals(1, h.server.snapshot().batches.size)
    assertTrue(h.server.forwardNow().retry)
    c.online = true
    assertEquals(1, h.server.forwardNow().forwarded)
    assertEquals(listOf("a1", "a2"), c.rows.keys.toList())
    //  دستگاه همان دسته را دوباره به هاب و سپس مستقیم به سرور می‌دهد
    assertTrue(push(h, tokA, "devA", "a1", "a2").body.contains("hub_settled"))
    assertEquals(0, h.server.forwardNow().forwarded)
    assertEquals(listOf("duplicate", "duplicate"), c.direct(listOf("a1", "a2")))
    assertEquals(2, c.rows.size)
    assertEquals(1, c.pushes.size)
  }

  @Test fun `ترتیبِ دسته‌های هر دستگاه تا سرور می‌ماند`() {
    val c = cloud()
    val h = hub(newDir(), c)
    c.online = true
    assertEquals(200, hello(h, tokA, "devA").status)
    assertEquals(200, hello(h, tokB, "devB").status)
    c.online = false
    push(h, tokA, "devA", "a1")
    push(h, tokB, "devB", "b1")
    push(h, tokA, "devA", "a2", "a3")
    push(h, tokA, "devA", "a4")
    push(h, tokB, "devB", "b2")
    c.online = true
    assertEquals(5, h.server.forwardNow().forwarded)
    assertEquals(listOf(listOf("a1"), listOf("b1"), listOf("a2", "a3"), listOf("a4"), listOf("b2")), c.pushes)
    assertEquals(listOf("a1", "b1", "a2", "a3", "a4", "b2"), c.rows.keys.toList())
  }

  /* ------------------------------ خاموش و روشن ------------------------------ */

  @Test fun `هابِ خاموش و روشن‌شده صف را از دیسک از همان‌جا ادامه می‌دهد`() {
    val c = cloud()
    val dir = newDir()
    val h = hub(dir, c)
    assertEquals(200, hello(h, tokA, "devA").status)
    c.online = false
    push(h, tokA, "devA", "a1")
    push(h, tokA, "devA", "a2")
    push(h, tokA, "devA", "a3")
    c.online = true
    c.allow = 1
    val p1 = h.server.forwardNow()
    assertEquals(1, p1.forwarded)
    assertTrue(p1.retry)
    h.server.stop()

    val h2 = hub(dir, c)
    assertEquals(listOf("a2", "a3"), h2.server.snapshot().batches.flatMap { it.opIds })
    c.allow = Int.MAX_VALUE
    //  توکن فقط در حافظه بود ⇒ تا دستگاه برنگردد چیزی نمی‌رود
    assertEquals(setOf("devA"), h2.server.forwardNow().waiting)
    c.online = false
    assertEquals("توکنِ سنجیده بی اینترنت پذیرفته است", 200, hello(h2, tokA, "devA").status)
    c.online = true
    assertEquals(2, h2.server.forwardNow().forwarded)
    assertEquals(listOf("a1", "a2", "a3"), c.rows.keys.toList())
    assertEquals(listOf(listOf("a1"), listOf("a2"), listOf("a3")), c.pushes)
  }

  @Test fun `سقوط میانِ پاسخِ سرور و ذخیره دوباره‌فرستادن است نه ردیفِ دوم`() {
    val c = cloud()
    val dir = newDir()
    val h = hub(dir, c)
    assertEquals(200, hello(h, tokA, "devA").status)
    c.online = false
    push(h, tokA, "devA", "a1", "a2")
    push(h, tokA, "devA", "a3")
    val before = File(dir, HubServer.QUEUE_FILE).readBytes()
    c.online = true
    assertEquals(2, h.server.forwardNow().forwarded)
    h.server.stop()
    //  همان حالِ دیسک که اگر برنامه درست پس از پاسخِ سرور و پیش از ذخیره می‌افتاد
    File(dir, HubServer.QUEUE_FILE).writeBytes(before)
    val h2 = hub(dir, c)
    assertEquals(2, h2.server.snapshot().batches.size)
    assertEquals(200, hello(h2, tokA, "devA").status)
    assertEquals(2, h2.server.forwardNow().forwarded)
    assertEquals(listOf("a1", "a2", "a3"), c.rows.keys.toList())
    assertEquals(4, c.pushes.size)
    assertTrue(h2.server.snapshot().batches.isEmpty())
  }

  @Test fun `پاسخِ ۴۰۱ ِ سرور توکن را از حافظه و فهرست برمی‌دارد`() {
    val c = cloud()
    val h = hub(newDir(), c)
    assertEquals(200, hello(h, tokA, "devA").status)
    c.online = false
    push(h, tokA, "devA", "a1")
    c.online = true
    c.tokens.remove(tokA)
    val p = h.server.forwardNow()
    assertEquals(setOf("devA"), p.authFailed)
    assertEquals(1, h.server.snapshot().batches.size)
    assertEquals(setOf("devA"), h.server.forwardNow().waiting)
    val r = hello(h, tokA, "devA")
    assertEquals(401, r.status)
    assertTrue(r.body.contains("hub_invalid_token"))
    assertTrue(HubQueue.decode(open(File(h.dir, HubServer.QUEUE_FILE).readText())) != null)
  }
}
