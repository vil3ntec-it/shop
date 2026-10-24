package ir.vil3ntec.tohid.core.net

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** سرآیندهای `RawBody` (پشتیبان، عکس) هم از سدِ `PROTECTED_HEADERS` می‌گذرند */
class HttpEngineRawHeadersTest {

  private lateinit var server: TestHttpServer

  @Before
  fun start() { server = TestHttpServer() }

  @After
  fun stop() = server.close()

  @Test
  fun `بدنهٔ خام توکن و هویتِ برنامه را عوض نمی‌کند ولی برچسب‌های خودش می‌روند`() {
    var seen: TestHttpServer.Request? = null
    server.on("/api/v1/raw-test") { seen = it; 200 to """{"ok":true}""" }
    val engine = HttpEngine(
      baseUrl = { server.baseUrl },
      allowInsecure = true,
      identity = { mapOf("X-App" to "shop", "X-Device" to "device-1") },
    )
    val raw = RawBody(
      bytes = "file".toByteArray(),
      headers = mapOf(
        "Authorization" to "Bearer stolen",
        "x-device" to "device-2",
        "X-App" to "pump",
        "Cookie" to "sid=1",
        "X-Backup-Label" to "keep",
      ),
    )
    runBlocking { engine.send("POST", "/raw-test", token = "real", raw = raw) }

    val r = seen!!
    assertEquals("real", r.bearer)
    assertEquals("device-1", r.headers["x-device"])
    assertEquals("shop", r.headers["x-app"])
    assertNull(r.headers["cookie"])
    assertEquals("keep", r.headers["x-backup-label"])
  }
}
