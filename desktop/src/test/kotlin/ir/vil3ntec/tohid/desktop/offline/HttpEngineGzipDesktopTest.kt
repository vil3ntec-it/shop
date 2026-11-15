package ir.vil3ntec.tohid.desktop.offline

import com.sun.net.httpserver.HttpServer
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.HttpEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.zip.GZIPOutputStream

/**
 *  SYN-04 — پاسخِ فشرده برای کامپیوتر: `HttpURLConnection` ِ JDK خودش `Accept-Encoding: gzip` نمی‌فرستد (OkHttp ِ اندروید
 *  می‌فرستد و باز می‌کند)، پس pull ِ v1 با پرچمِ `sync.gzip_pull`، Snapshot ِ v1 و pull/Snapshot ِ v3 روی کامپیوتر همیشه
 *  ساده می‌آمدند (S26 روی 2G: pull ≈ ۹ برابرِ gzip). `HttpEngine` حالا سرآیند را خودش می‌گذارد و پاسخِ
 *  `Content-Encoding: gzip` را باز می‌کند؛ سرورِ بی gzip همان پاسخِ ساده. سرورِ کوچکِ JDK جای سرورِ مخزن.
 */
class HttpEngineGzipDesktopTest {

  @Volatile private var lastAccept = ""
  @Volatile private var mode = "gzip"

  private fun gz(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
    createContext("/") { ex ->
      lastAccept = ex.requestHeaders.getFirst("Accept-Encoding").orEmpty()
      val json = """{"ok":true,"ops":[${(1..300).joinToString(",") { """{"op_id":"o$it","note":"فروشِ نقد"}""" }}]}""".toByteArray()
      val wantsGzip = lastAccept.contains("gzip")
      ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
      when {
        mode == "empty" -> { ex.responseHeaders.add("Content-Encoding", "gzip"); ex.sendResponseHeaders(204, -1); ex.close() }
        mode == "error" && wantsGzip -> {
          val b = gz("""{"error":{"code":"rate_limited","message":"بعداً"}}""".toByteArray())
          ex.responseHeaders.add("Content-Encoding", "gzip"); ex.sendResponseHeaders(429, b.size.toLong()); ex.responseBody.use { it.write(b) }
        }
        mode == "gzip" && wantsGzip -> {
          val b = gz(json)
          ex.responseHeaders.add("Content-Encoding", "gzip"); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) }
        }
        else -> { ex.sendResponseHeaders(200, json.size.toLong()); ex.responseBody.use { it.write(json) } }
      }
    }
    start()
  }

  @After fun stop() = server.stop(0)

  private val api = ApiClient(
    HttpEngine(baseUrl = { "http://127.0.0.1:${server.address.port}" }, allowInsecure = true, online = { true },
      identity = { mapOf("X-App" to "shop", "X-Device" to "dev-gz") }),
    MemTokens("acc", "ref"),
  )

  @Test fun `کامپیوتر gzip می‌خواهد و پاسخِ فشرده را باز می‌کند؛ پاسخِ ساده و خالی همان پیش`() = runBlocking {
    val a = api.get("/sync/v1/pull?device_id=d&since=0")
    assertTrue("سرآیندِ Accept-Encoding: «$lastAccept»", lastAccept.contains("gzip"))
    assertEquals(300, (a["ops"] as JsonArray).size)
    assertEquals("فروشِ نقد", ((a["ops"] as JsonArray)[299] as kotlinx.serialization.json.JsonObject)["note"]!!.let { (it as JsonPrimitive).content })
    mode = "plain"
    assertEquals(300, (api.get("/sync/v1/snapshot")["ops"] as JsonArray).size)
    mode = "empty"
    assertTrue(api.post("/x").isEmpty())
    mode = "error"
    try { api.get("/y"); fail("۴۲۹") } catch (f: ApiFailure) { assertEquals("rate_limited", f.code) }
  }
}
