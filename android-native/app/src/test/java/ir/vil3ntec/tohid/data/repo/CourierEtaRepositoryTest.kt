package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 *  DLV-06 — «تقریباً کی می‌رسم» ِ پیک: `etaEnabled` ِ `/courier/me` (سرورِ کهنه بی کلید ⇒ پنهان) و
 *  `POST /api/courier/missions/:id/eta {minutes}` ⇒ `etaAt` ِ سرور؛ ردِ سرور خطاست.
 */
class CourierEtaRepositoryTest {

  private lateinit var server: TestHttpServer

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() = server.close()

  private fun repo(): CourierRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "courier"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = Long.MAX_VALUE
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return CourierRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  private val courier = """"courier":{"id":"C1","name":"کریم","active":true,"cashCapMinor":0}"""

  @Test fun `کلیدِ etaEnabled از me؛ سرورِ کهنه بی کلید یعنی پنهان`(): Unit = runBlocking {
    server.on("/api/v1/courier/me") { 200 to """{$courier,"etaEnabled":true}""" }
    assertTrue((repo().me() as ApiResult.Success).value.etaEnabled)
    server.on("/api/v1/courier/me") { 200 to """{$courier}""" }
    assertFalse((repo().me() as ApiResult.Success).value.etaEnabled)
  }

  @Test fun `دقیقه‌ها به همان درِ مأموریت می‌روند و etaAt برمی‌گردد`(): Unit = runBlocking {
    val seen = Collections.synchronizedList(ArrayList<TestHttpServer.Request>())
    server.on("/api/v1/courier/missions/A1/eta") { r -> seen += r; 200 to """{"etaAt":1700000900000}""" }
    val r = repo().eta("A1", 15)
    assertEquals(1700000900000L, (r as ApiResult.Success).value)
    val req = seen.single()
    assertEquals("POST", req.method)
    assertEquals(15, Json.parseToJsonElement(req.body).jsonObject["minutes"]!!.jsonPrimitive.int)
  }

  @Test fun `ردِ سرور (نه در راه) خطاست`(): Unit = runBlocking {
    server.on("/api/v1/courier/missions/A1/eta") { 409 to """{"error":{"code":"not_on_the_way","message":"فقط در راه"}}""" }
    assertTrue(repo().eta("A1", 15) is ApiResult.Failure)
  }
}
