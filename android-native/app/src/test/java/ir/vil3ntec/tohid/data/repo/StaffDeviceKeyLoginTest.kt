package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.ui.v3.onboarding.StaffJoinErrors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 *  ⛔ رازِ دستگاهِ شاگرد (سرور ۲.۱۴.۵، `server/src/lib/staff-device-keys.js`): ورودِ دوباره با کدِ شاگرد فقط با رازی
 *  که همین دستگاه در نخستین پیوستن گرفته. این‌جا سمتِ برنامه در برابرِ سرورِ کوچکِ واقعی:
 *  `keyVersion` همیشه می‌رود، رازِ نگه‌داشته همراهِ کد می‌رود، `deviceKey` ِ پاسخ در حافظهٔ امن می‌نشیند و پاسخِ بی راز
 *  آن را پاک نمی‌کند؛ نشستِ بازِ پیشین رازش را از `POST /auth/staff/device-key` می‌گیرد؛ و ردِ «دستگاهِ ناشناس»
 *  پیامِ «کدِ تازه از صاحبِ دکان» دارد.
 */
class StaffDeviceKeyLoginTest {

  private lateinit var server: TestHttpServer

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() = server.close()

  /** همان قراردادِ `TokenStorage` ِ واقعی: `clear()` ِ خروج رازِ دستگاه را نگه می‌دارد */
  private class Memory(var key: String? = null, signed: Boolean = false) : TokenStorage {
    override var accessToken: String? = if (signed) "open-session" else null
    override var refreshToken: String? = null
    override var accessExpiresAt: Long = 0
    override var staffDeviceKey: String?
      get() = key
      set(value) { key = value }
    override fun save(access: String?, refresh: String?, expiresAt: Long) {
      accessToken = access
      if (refresh != null) refreshToken = refresh
    }
    override fun clear() { accessToken = null; refreshToken = null }
  }

  private fun auth(tokens: TokenStorage) =
    AuthRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens), tokens)

  private val session = """"accessToken":"acc-1","refreshToken":"ref-1","accessExpiresAt":1,"user":{"id":"u-ali","name":"علی"}"""

  private fun device(req: TestHttpServer.Request): JsonObject =
    Json.parseToJsonElement(req.body).jsonObject["device"]!!.jsonObject

  @Test fun `پیوستنِ نخست keyVersion می‌فرستد و رازِ پاسخ را نگه می‌دارد`(): Unit = runBlocking {
    val seen = Collections.synchronizedList(ArrayList<TestHttpServer.Request>())
    server.on("/api/v1/auth/staff") { r -> seen += r; 201 to """{$session,"deviceKey":"k-from-server"}""" }
    val tokens = Memory()
    val r = auth(tokens).loginWithStaffCode("shg-aaaa-bbbb-cccc", "علی", "phone-1", "گوشی")
    assertTrue(r is ApiResult.Success)
    val dev = device(seen.single())
    assertEquals("phone-1", dev["uid"]!!.jsonPrimitive.content)
    assertEquals("1", dev["keyVersion"]!!.jsonPrimitive.content)
    assertNull("رازی نبود ⇒ کلیدِ key نمی‌رود", dev["key"])
    assertEquals("k-from-server", tokens.staffDeviceKey)
    //  خروج: نشست می‌رود، رازِ دستگاه می‌ماند
    tokens.clear()
    assertFalse(tokens.signedIn)
    assertEquals("k-from-server", tokens.staffDeviceKey)
  }

  @Test fun `ورودِ دوباره رازِ نگه‌داشته را همراهِ کد می‌فرستد و پاسخِ بی راز آن را نگه می‌دارد`(): Unit = runBlocking {
    val seen = Collections.synchronizedList(ArrayList<TestHttpServer.Request>())
    server.on("/api/v1/auth/staff") { r -> seen += r; 201 to """{$session}""" }
    val tokens = Memory(key = "k-kept")
    assertTrue(auth(tokens).loginWithStaffCode("SHG-AAAA-BBBB-CCCC", "", "phone-1", "گوشی") is ApiResult.Success)
    assertEquals("k-kept", device(seen.single())["key"]!!.jsonPrimitive.content)
    assertEquals("k-kept", tokens.staffDeviceKey)
  }

  @Test fun `دستگاهِ ناشناس ⇒ پیامِ کدِ تازه از صاحبِ دکان، نه شکستِ بی‌صدا`(): Unit = runBlocking {
    server.on("/api/v1/auth/staff") {
      403 to """{"error":{"code":"staff_device_unverified","message":"این دستگاه شناخته نشد. کدِ تازه از صاحبِ دکان بگیرید."}}"""
    }
    val tokens = Memory()
    val r = auth(tokens).loginWithStaffCode("SHG-AAAA-BBBB-CCCC", "", "phone-1", "گوشی")
    assertTrue(r is ApiResult.Failure)
    val failure = (r as ApiResult.Failure).error
    assertEquals("staff_device_unverified", failure.code)
    assertEquals(StrAuth.staffDeviceUnverified, StaffJoinErrors.message(failure))
    assertTrue(StrAuth.staffDeviceUnverified.contains("صاحبِ دکان"))
    assertFalse(tokens.signedIn)
    assertNull(tokens.staffDeviceKey)
  }

  @Test fun `نشستِ بازِ پیشین رازش را یک بار می‌گیرد؛ راز که هست هیچ پرسشی نیست`(): Unit = runBlocking {
    val seen = Collections.synchronizedList(ArrayList<TestHttpServer.Request>())
    server.on("/api/v1/auth/staff/device-key") { r -> seen += r; 201 to """{"deviceKey":"k-enrolled"}""" }
    val tokens = Memory(signed = true)
    val repo = auth(tokens)
    val first = repo.ensureStaffDeviceKey()
    assertEquals(true, (first as ApiResult.Success).value)
    assertEquals("k-enrolled", tokens.staffDeviceKey)
    assertEquals("Bearer open-session", seen.single().headers["authorization"])
    assertEquals(true, (repo.ensureStaffDeviceKey() as ApiResult.Success).value)
    assertEquals("راز که هست دوباره پرسیده نمی‌شود", 1, seen.size)
  }

  @Test fun `حسابِ غیرِ شاگرد یا رازِ موجود در سرور ⇒ هیچ چیز نوشته نمی‌شود`(): Unit = runBlocking {
    server.on("/api/v1/auth/staff/device-key") { 200 to """{"deviceKey":null,"reason":"not_staff_account"}""" }
    val tokens = Memory(signed = true)
    assertEquals(false, (auth(tokens).ensureStaffDeviceKey() as ApiResult.Success).value)
    assertNull(tokens.staffDeviceKey)
    //  بی نشست هیچ درخواستی
    val idle = Memory()
    assertEquals(false, (auth(idle).ensureStaffDeviceKey() as ApiResult.Success).value)
  }
}
