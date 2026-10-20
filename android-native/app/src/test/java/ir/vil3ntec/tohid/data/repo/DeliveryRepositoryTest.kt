package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 *  میزِ تحویل در برابرِ یک سرورِ واقعیِ کوچک: سپردن و گذار با
 *  `Idempotency-Key` می‌روند (همان که `keyOf`ِ `routes/delivery.js` می‌خواند)
 *  و پاسخِ گم‌شده با همان کلید دوباره فرستاده می‌شود — پس سپردنی که در
 *  سرور ثبت شده «ناموفق» دیده نمی‌شود و دومی هم ساخته نمی‌شود.
 */
class DeliveryRepositoryTest {

  private lateinit var server: TestHttpServer

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() = server.close()

  private fun repo(): DeliveryRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = 0
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return DeliveryRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  private val mission = """{"id":"A1","orderId":"O1","orderNo":7,"mode":"shop_courier","courierId":"C1","status":"requested","live":true}"""

  @Test fun `سپردن با سرآیندِ Idempotency-Key و بدنهٔ JSON`(): Unit = runBlocking {
    val seen = Collections.synchronizedList(ArrayList<TestHttpServer.Request>())
    server.on("/api/v1/shop/delivery/orders/O1/assign") { r -> seen += r; 201 to """{"assignment":$mission}""" }
    val body = DeliveryWire.assignJson("shop_courier", courierId = "C1", fareMinor = 20_000)
    val r = repo().assign("O1", body, "dsk-key-0001")
    assertTrue(r is ApiResult.Success)
    val req = seen.single()
    assertEquals("POST", req.method)
    assertEquals("dsk-key-0001", req.headers["idempotency-key"])
    assertTrue(req.headers["content-type"].orEmpty().startsWith("application/json"))
    val sent = Json.parseToJsonElement(req.body).jsonObject
    assertEquals("shop_courier", sent["mode"]!!.jsonPrimitive.content)
    assertEquals("C1", sent["courierId"]!!.jsonPrimitive.content)
    assertEquals(false, (r as ApiResult.Success).value.replayed)
  }

  @Test fun `پاسخِ گم‌شده با همان کلید دوباره می‌رود و «ثبت‌شده» برمی‌گردد`(): Unit = runBlocking {
    val keys = Collections.synchronizedList(ArrayList<String>())
    server.on("/api/v1/shop/delivery/orders/O1/assign") { r ->
      keys += r.headers["idempotency-key"].orEmpty()
      //  اولی در سرور ثبت شد ولی پاسخش نرسید (۵۰۳)؛ دومی همان کلید ⇒ تکرارِ همان
      if (keys.size == 1) 503 to """{"error":{"code":"unavailable","message":"x"}}"""
      else 200 to """{"assignment":$mission,"replayed":true}"""
    }
    val r = repo().assign("O1", DeliveryWire.assignJson("pickup"), "dsk-key-0002")
    assertTrue("مهلت/۵۰۳ پس از ثبت «ناموفق» دیده نمی‌شود", r is ApiResult.Success)
    assertEquals(listOf("dsk-key-0002", "dsk-key-0002"), keys.toList())
    val a = (r as ApiResult.Success).value
    assertEquals(true, a.replayed)
    assertNull("تکرار کد را دوباره نمی‌دهد", a.code)
  }

  @Test fun `گذارِ کارمند و فراخوانی هم کلید دارند`(): Unit = runBlocking {
    val keys = Collections.synchronizedList(ArrayList<String>())
    server.on("/api/v1/shop/delivery/assignments/A1/status") { r -> keys += r.headers["idempotency-key"].orEmpty(); 200 to """{"assignment":$mission,"status":"applied"}""" }
    server.on("/api/v1/shop/delivery/assignments/A1/recall") { r -> keys += r.headers["idempotency-key"].orEmpty(); 200 to """{"assignment":$mission}""" }
    val repo = repo()
    assertTrue(repo.status("A1", DeliveryWire.statusJson(to = "withdrawn", reason = "reassign"), "dsk-key-0003") is ApiResult.Success)
    assertTrue(repo.recall("A1", "customer_request", "", "dsk-key-0004") is ApiResult.Success)
    assertEquals(listOf("dsk-key-0003", "dsk-key-0004"), keys.toList())
  }

  @Test fun `کلیدِ فرم — همان بدنه همان کلید، بدنهٔ دیگر کلیدِ تازه`() {
    var n = 0
    val once = DeliveryRepository.OnceKey { "dsk-test-${++n}" }
    val a = DeliveryWire.assignJson("shop_courier", courierId = "C1")
    val k1 = once.of(a)
    assertEquals(k1, once.of(DeliveryWire.assignJson("shop_courier", courierId = "C1")))
    val k2 = once.of(DeliveryWire.assignJson("shop_courier", courierId = "C2"))
    assertNotEquals(k1, k2)
    assertTrue(Regex("^[A-Za-z0-9_.:-]{8,128}$").matches(DeliveryRepository.newKey()))
  }
}
