package ir.vil3ntec.tohid.desktop.offline

import com.sun.net.httpserver.HttpServer
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncV1Client
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.util.zip.GZIPInputStream

/**
 *  SYN-04 — push ِ v1 ِ کاتلین (`SyncV1Client.PushGzip`): «فشرده بفرست» ِ سرور (`gzip_push` ِ pull ِ با op، پرچمِ دکانِ
 *  `sync.gzip_push`) ⇒ بدنهٔ بالای ۱KB با `Content-Encoding: gzip`، همان بایت‌های JSON پس از باز شدن؛ pull ِ بی op
 *  یادِ پیشین را نگه می‌دارد؛ ۴۱۵ یا ۴۰۰ ِ `bad_json` ⇒ همان دم ساده و از آن پس ساده؛ خطای دیگر ⇒ بی فرستادنِ دوباره.
 *  سرورِ کوچکِ JDK (`HttpServer`) جای سرورِ مخزن؛ رفتارِ سرورِ واقعی را `S26Load2GDesktopTest` (SYN-04) می‌سنجد.
 */
class SyncV1PushGzipDesktopTest {

  private class Seen(val path: String, val encoding: String, val text: String)

  private val seen = java.util.Collections.synchronizedList(ArrayList<Seen>())
  @Volatile private var pushReply: (encoding: String) -> Pair<Int, String> = { 200 to """{"ok":true,"results":[],"cursor":7}""" }
  @Volatile private var pullBody = ""

  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
    createContext("/") { ex ->
      val enc = ex.requestHeaders.getFirst("Content-Encoding").orEmpty()
      val raw = ex.requestBody.readBytes()
      val text = if (enc == "gzip") GZIPInputStream(raw.inputStream()).readBytes().decodeToString() else raw.decodeToString()
      val path = ex.requestURI.path
      seen += Seen(path, enc, text)
      val (code, body) = when {
        path.endsWith("/sync/v1/pull") -> 200 to pullBody
        path.endsWith("/sync/v1/push") -> pushReply(enc)
        else -> 404 to """{"error":"not_found"}"""
      }
      val bytes = body.toByteArray()
      ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
      ex.sendResponseHeaders(code, bytes.size.toLong())
      ex.responseBody.use { it.write(bytes) }
    }
    start()
  }

  @After fun stop() = server.stop(0)

  private val api = ApiClient(
    HttpEngine(baseUrl = { "http://127.0.0.1:${server.address.port}" }, allowInsecure = true, online = { true },
      identity = { mapOf("X-App" to "shop", "X-Device" to "dev-gz") }),
    MemTokens("acc", "ref"),
  )

  private var memo = false
  private val gzip = SyncV1Client.PushGzip({ memo }, { memo = it })
  private val client = SyncV1Client(api, { null }, gzip)

  private fun op(i: Int, note: String = "x".repeat(40)) = SyncOp(
    opId = "01JTEST${i.toString().padStart(19, '0')}", ts = 1_700_000_000_000L + i, table = "sales", rowId = "s$i",
    type = SyncOp.INSERT, fields = JsonObject(mapOf("note" to JsonPrimitive(note), "total" to JsonPrimitive(i))), row = null,
  )

  private fun pull(ops: Int, flag: Boolean?) {
    val list = (1..ops).joinToString(",") { """{"op_id":"r$it","ts":1,"table":"sales","row_id":"r$it","type":"insert","fields":{"total":1},"server_seq":$it,"device_id":"other"}""" }
    pullBody = """{"ok":true,"ops":[$list],"cursor":$ops,"has_more":false${if (flag == null) "" else ""","gzip_push":$flag"""}}"""
  }

  private fun pushes() = seen.filter { it.path.endsWith("/sync/v1/push") }

  @Test fun `بی gzip_push همیشه ساده؛ پس از pull ِ با op فشرده و همان JSON`() = runBlocking {
    val big = (1..60).map { op(it) }
    client.push("dev-gz", big, big.size)
    assertEquals("پیش از هر pull ساده", "", pushes().last().encoding)
    pull(1, true)
    client.pull("dev-gz", 0)
    assertTrue("pull ِ با op ⇒ یاد", memo)
    pull(0, null)
    client.pull("dev-gz", 1)
    assertTrue("pull ِ بی op یادِ پیشین را نگه می‌دارد", memo)
    client.push("dev-gz", big, big.size)
    val p = pushes().last()
    assertEquals("بالای ۱KB فشرده", "gzip", p.encoding)
    assertEquals("همان JSON ِ ساده پس از باز شدن", pushes().first().text, p.text)
    client.push("dev-gz", listOf(op(1, "k")), 1)
    assertEquals("زیرِ ۱KB ساده", "", pushes().last().encoding)
    pull(1, false)
    client.pull("dev-gz", 1)
    client.push("dev-gz", big, big.size)
    assertEquals("پرچم خاموش شد ⇒ ساده", "", pushes().last().encoding)
  }

  @Test fun `۴۱۵ یا ۴۰۰ ِ bad_json ِ فشرده ⇒ همان دم ساده و از آن پس ساده؛ خطای دیگر بی فرستادنِ دوباره`() = runBlocking {
    memo = true
    val big = (1..60).map { op(it) }
    for ((code, err) in listOf(415 to "unsupported_encoding", 400 to "bad_json")) {
      seen.clear()
      gzip.refused = false
      pushReply = { enc -> if (enc == "gzip") code to """{"error":{"code":"$err","message":"x"}}""" else 200 to """{"ok":true,"results":[],"cursor":7}""" }
      client.push("dev-gz", big, big.size)
      assertEquals("$code: فشرده، رد، همان دم ساده", listOf("gzip", ""), pushes().map { it.encoding })
      client.push("dev-gz", big, big.size)
      assertEquals("$code: از آن پس ساده", listOf("gzip", "", ""), pushes().map { it.encoding })
    }
    seen.clear()
    gzip.refused = false
    pushReply = { 500 to """{"error":{"code":"internal","message":"x"}}""" }
    try { client.push("dev-gz", big, big.size); fail("۵۰۰ باید برسد") } catch (_: ApiFailure) {}
    assertEquals("۵۰۰ ⇒ یک درخواست، نه دوباره ساده", listOf("gzip"), pushes().map { it.encoding })
    pushReply = { 426 to """{"error":{"code":"upgrade_required","message":"x"}}""" }
    try { client.push("dev-gz", big, big.size); fail("۴۲۶") } catch (_: SyncV1Client.UpgradeRequired) {}
  }
}
