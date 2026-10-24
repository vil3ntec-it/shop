package ir.vil3ntec.tohid.core.net

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 *  «رمز را دوباره بزنید» (بندِ ۲۱.۱) — `ReauthGate` تنها، و همراهِ `ApiClient`
 *  در برابرِ سرورِ آزمایشی که مثلِ `lib/reauth.js` جواب می‌دهد:
 *  ۴۰۳ `reauth_required` تا وقتی `POST /auth/reauth` درست زده نشده.
 */
class ReauthGateTest {

  /* ------------------------------ خودِ دروازه ------------------------------ */

  @Test
  fun `بی میزبان (کارِ پس‌زمینه) بی‌درنگ نه — همان خطای امروز`() = runBlocking {
    val gate = ReauthGate()
    assertFalse(gate.confirm())
    assertNull(gate.asking.value)
  }

  @Test
  fun `میزبان تأیید کرد ⇒ بله، و پرسش بسته می‌شود`() = runBlocking {
    val gate = ReauthGate()
    gate.attach()
    val answer = async { gate.confirm() }
    withTimeout(5_000) { gate.asking.filterNotNull().first() }.done(true)
    assertTrue(answer.await())
    assertNull(gate.asking.value)
  }

  @Test
  fun `انصراف ⇒ نه`() = runBlocking {
    val gate = ReauthGate()
    gate.attach()
    val answer = async { gate.confirm() }
    withTimeout(5_000) { gate.asking.filterNotNull().first() }.done(false)
    assertFalse(answer.await())
  }

  @Test
  fun `میزبان وسطِ پرسش رفت ⇒ نه، نه انتظارِ بی‌پایان`() = runBlocking {
    val gate = ReauthGate()
    val detach = gate.attach()
    val answer = async { gate.confirm() }
    withTimeout(5_000) { gate.asking.filterNotNull().first() }
    detach()
    detach()   // دوبار جدا شدن شمارش را خراب نمی‌کند
    assertFalse(withTimeout(5_000) { answer.await() })
    assertFalse(gate.confirm())
  }

  @Test
  fun `دو درخواستِ هم‌زمان ⇒ یک پرسش؛ دومی پس از تأییدِ اولی بی پرسش`() = runBlocking {
    val gate = ReauthGate()
    gate.attach()
    val first = async { gate.confirm() }
    val ask = withTimeout(5_000) { gate.asking.filterNotNull().first() }
    val second = async { gate.confirm() }
    yield()
    ask.done(true)
    assertTrue(first.await())
    assertTrue(second.await())
    assertNull("پرسشِ دوم نباید باز شود", gate.asking.value)
  }

  @Test
  fun `فقط reauth_required، و هرگز روی خودِ مسیرِ تأیید`() {
    val needs = ApiFailure.Forbidden("دوباره", ReauthGate.CODE)
    assertTrue(ReauthGate.applies(needs, ApiEndpoints.Me.ROOT))
    assertTrue(ReauthGate.applies(needs, ApiEndpoints.Auth.PASSWORD))
    assertFalse(ReauthGate.applies(needs, ApiEndpoints.Auth.REAUTH))
    assertFalse(ReauthGate.applies(needs, ApiEndpoints.Auth.REAUTH_CODE))
    assertFalse(ReauthGate.applies(ApiFailure.Forbidden("نه", "forbidden"), ApiEndpoints.Me.ROOT))
  }

  /* ------------------------- همراهِ ApiClient، روی سیم ------------------------- */

  private lateinit var server: TestHttpServer
  @Volatile private var fresh = false

  @Before
  fun start() {
    server = TestHttpServer()
    fresh = false
    server.on("/api/v1${ApiEndpoints.Auth.REAUTH}") { request ->
      if (request.body.contains("\"right\"")) { fresh = true; 200 to """{"ok":true}""" }
      else 401 to """{"error":{"code":"bad_credentials","message":"رمز درست نیست"}}"""
    }
  }

  @After
  fun stop() = server.close()

  private val reauthRequired =
    403 to """{"error":{"code":"reauth_required","message":"رمز را دوباره بزنید"}}"""

  private fun client(gate: ReauthGate = ReauthGate()): ApiClient {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = "r"
      override var accessExpiresAt: Long = 0
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens, reauth = gate)
  }

  private val phone = buildJsonObject { put("phone", JsonPrimitive("09121234567")) }

  @Test
  fun `۴۰۳ reauth_required ⇒ پنجره، رمزِ درست، و همان درخواست یک بار دوباره`() = runBlocking {
    server.on("/api/v1${ApiEndpoints.Me.ROOT}") { if (fresh) 200 to """{"user":{"id":"u1"}}""" else reauthRequired }
    val gate = ReauthGate()
    gate.attach()
    val api = client(gate)

    val call = async { api.put(ApiEndpoints.Me.ROOT, phone) }
    val ask = withTimeout(5_000) { gate.asking.filterNotNull().first() }
    //  رمزِ غلط: خطا در پنجره می‌ماند و پرسش باز است — همان کاری که `ReauthHost` می‌کند
    runCatching { api.post(ApiEndpoints.Auth.REAUTH, buildJsonObject { put("password", JsonPrimitive("wrong")) }) }
      .onSuccess { fail("رمزِ غلط نباید بگیرد") }
      .onFailure { assertEquals("bad_credentials", (it as ApiFailure).code) }
    api.post(ApiEndpoints.Auth.REAUTH, buildJsonObject { put("password", JsonPrimitive("right")) })
    ask.done(true)

    assertEquals("u1", (call.await()["user"] as kotlinx.serialization.json.JsonObject)["id"]?.let { (it as JsonPrimitive).content })
    assertEquals(2, server.hits("/api/v1${ApiEndpoints.Me.ROOT}"))
    assertEquals(2, server.hits("/api/v1${ApiEndpoints.Auth.REAUTH}"))
  }

  @Test
  fun `انصراف ⇒ خطای اصلیِ سرور، بی تکرار`() = runBlocking {
    server.on("/api/v1${ApiEndpoints.Me.ROOT}") { reauthRequired }
    val gate = ReauthGate()
    gate.attach()
    val api = client(gate)

    val call = async { runCatching { api.put(ApiEndpoints.Me.ROOT, phone) } }
    withTimeout(5_000) { gate.asking.filterNotNull().first() }.done(false)
    val failure = call.await().exceptionOrNull() as ApiFailure
    assertEquals(ReauthGate.CODE, failure.code)
    assertEquals("رمز را دوباره بزنید", failure.userMessage)
    assertEquals(1, server.hits("/api/v1${ApiEndpoints.Me.ROOT}"))
  }

  @Test
  fun `بی میزبان ⇒ همان خطا، بی پرسش و بی تکرار`() = runBlocking {
    server.on("/api/v1${ApiEndpoints.Me.ROOT}") { reauthRequired }
    val failure = runCatching { client().put(ApiEndpoints.Me.ROOT, phone) }.exceptionOrNull() as ApiFailure
    assertEquals(ReauthGate.CODE, failure.code)
    assertEquals(1, server.hits("/api/v1${ApiEndpoints.Me.ROOT}"))
  }

  @Test
  fun `تکرار هم رد شد ⇒ فقط یک بار تکرار، بی حلقه`() = runBlocking {
    server.on("/api/v1${ApiEndpoints.Me.ROOT}") { reauthRequired }
    val gate = ReauthGate()
    gate.attach()
    val api = client(gate)

    val call = async { runCatching { api.put(ApiEndpoints.Me.ROOT, phone) } }
    withTimeout(5_000) { gate.asking.filterNotNull().first() }.done(true)
    val failure = withTimeout(5_000) { call.await() }.exceptionOrNull() as ApiFailure
    assertEquals(ReauthGate.CODE, failure.code)
    assertEquals(2, server.hits("/api/v1${ApiEndpoints.Me.ROOT}"))
  }
}
