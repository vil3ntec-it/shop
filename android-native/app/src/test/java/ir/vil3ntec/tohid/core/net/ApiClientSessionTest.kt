package ir.vil3ntec.tohid.core.net

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 *  جریانِ نشست، در برابرِ یک سرورِ واقعی.
 *
 *  سرورِ کوچکی روی همین ماشین بالا می‌آید و همان‌طور جواب می‌دهد که
 *  سرورِ واقعی می‌دهد: `401` با `{"error":{...}}` وقتی توکن پیر است،
 *  و `accessToken` تازه روی `/auth/refresh`.
 *
 *  ── چیزی که اینجا سنجیده می‌شود ────────────────────────────────────
 *  توکنِ دسترسی روی سرور یک ساعت عمر دارد. برنامه توکنِ تازه‌سازی را
 *  ذخیره می‌کرد ولی هیچ‌وقت صدایش نمی‌زد، پس یک ساعت بعد از ورود
 *  همگام‌سازی بی‌صدا می‌مرد. این تست‌ها همان را می‌بندند.
 *  ──────────────────────────────────────────────────────────────────
 */
class ApiClientSessionTest {

  private lateinit var server: HttpServer
  private lateinit var base: String

  /** شمارشِ درخواست‌ها، تا معلوم شود چند بار و به کجا زده شده */
  private val hits = mutableMapOf<String, AtomicInteger>()

  private fun count(path: String): Int = hits[path]?.get() ?: 0

  private fun handle(path: String, body: (HttpExchange) -> Pair<Int, String>) {
    hits[path] = AtomicInteger(0)
    server.createContext(path) { exchange ->
      hits[path]?.incrementAndGet()
      val (status, text) = body(exchange)
      val bytes = text.toByteArray(Charsets.UTF_8)
      exchange.responseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
  }

  private fun bearerOf(exchange: HttpExchange): String =
    exchange.requestHeaders.getFirst("Authorization").orEmpty().removePrefix("Bearer ")

  @Before
  fun start() {
    server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.start()
    base = "http://127.0.0.1:${server.address.port}"
  }

  @After
  fun stop() = server.stop(0)

  /** لایه‌ها را همان‌طور می‌بندد که برنامه می‌بندد */
  private fun clientWith(tokens: TokenStorage, onLost: () -> Unit = {}): ApiClient {
    //  `allowInsecure` روشن است چون سرورِ آزمایشی روی `http` است — همان
    //  چیزی که در ساختِ آزمایشی هم مجاز است و در نسخهٔ منتشرشده نه
    val engine = HttpEngine(baseUrl = { base }, allowInsecure = true)
    return ApiClient(engine, tokens, onSessionLost = onLost)
  }

  private fun tokens(access: String?, refresh: String?) = object : TokenStorage {
    override var accessToken: String? = access
    override var refreshToken: String? = refresh
    override var accessExpiresAt: Long = 0
    override fun save(access: String?, refresh: String?, expiresAt: Long) {
      accessToken = access
      if (refresh != null) refreshToken = refresh
      if (expiresAt > 0) accessExpiresAt = expiresAt
    }
    override fun clear() { accessToken = null; refreshToken = null; accessExpiresAt = 0 }
  }

  /* ------------------------------ تست‌ها ------------------------------ */

  @Test
  fun `توکنِ پیر بی‌سروصدا تازه می‌شود و درخواست دوباره می‌رود`() {
    handle("/api/v1/auth/refresh") { 200 to """{"accessToken":"tazeh","accessExpiresAt":123}""" }
    handle("/api/v1/shop/me") { exchange ->
      if (bearerOf(exchange) == "tazeh") 200 to """{"shop":{"id":"shp_1","name":"دکان من"},"role":"owner"}"""
      else 401 to """{"error":{"code":"invalid_token","message":"نشست منقضی شده است"}}"""
    }

    val store = tokens(access = "pir", refresh = "refresh-90-rouzeh")
    val body = runBlocking { clientWith(store).get(ApiEndpoints.Shop.ME) }

    assertEquals("shp_1", (body["shop"] as kotlinx.serialization.json.JsonObject)["id"]?.let {
      (it as JsonPrimitive).content
    })
    //  یک بار با توکنِ پیر، یک بار با تازه
    assertEquals(2, count("/api/v1/shop/me"))
    assertEquals(1, count("/api/v1/auth/refresh"))
    //  توکنِ تازه ذخیره شده و کاربر چیزی ندیده
    assertEquals("tazeh", store.accessToken)
    assertEquals("refresh-90-rouzeh", store.refreshToken)
    assertEquals(123L, store.accessExpiresAt)
  }

  @Test
  fun `اگر تازه‌سازی هم نگرفت، نشست پاک می‌شود و کاربر خبردار`() {
    handle("/api/v1/auth/refresh") { 401 to """{"error":{"code":"invalid_token"}}""" }
    handle("/api/v1/shop/me") { 401 to """{"error":{"code":"invalid_token"}}""" }

    val store = tokens(access = "pir", refresh = "morde")
    var told = false
    val failure = runCatching {
      runBlocking { clientWith(store) { told = true }.get(ApiEndpoints.Shop.ME) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.SessionExpired)
    assertTrue("برنامه باید خبردار شود تا کاربر را به صفحهٔ ورود ببرد", told)
    assertNull(store.accessToken)
    assertNull(store.refreshToken)
  }

  @Test
  fun `قطعیِ گذرا نشست را پاک نمی‌کند`() {
    //  اگر «نت نیست» را «نشست تمام شد» حساب کنیم، کاربری که در مسیرِ
    //  بی‌آنتن است، بی‌دلیل از حسابش بیرون می‌افتد.
    handle("/api/v1/shop/me") { 401 to """{"error":{"code":"invalid_token"}}""" }
    //  مسیرِ تازه‌سازی اصلاً وجود ندارد → سرور در دسترس هست ولی…
    handle("/api/v1/auth/refresh") { 503 to """{"error":{"code":"unavailable"}}""" }

    val store = tokens(access = "pir", refresh = "salem")
    var told = false
    val failure = runCatching {
      runBlocking { clientWith(store) { told = true }.get(ApiEndpoints.Shop.ME) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.ServerFault)
    assertFalse("خطای گذرا نباید حساب را پاک کند", told)
    assertEquals("pir", store.accessToken)
    assertEquals("salem", store.refreshToken)
  }

  @Test
  fun `بدونِ توکنِ تازه‌سازی، همان یک بار تمام است`() {
    handle("/api/v1/shop/me") { 401 to """{"error":{"code":"invalid_token"}}""" }
    handle("/api/v1/auth/refresh") { 200 to """{"accessToken":"x"}""" }

    val store = tokens(access = "pir", refresh = null)
    val failure = runCatching {
      runBlocking { clientWith(store).get(ApiEndpoints.Shop.ME) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.SessionExpired)
    assertEquals("تازه‌سازیِ بی‌توکن نباید حتی امتحان شود", 0, count("/api/v1/auth/refresh"))
  }

  @Test
  fun `پیامِ فارسیِ سرور به کاربر می‌رسد`() {
    //  همان باگی که هر خطای سرور را به یک استثنای انگلیسیِ داخلی تبدیل
    //  می‌کرد
    handle("/api/v1/shop/staff-code") {
      403 to """{"error":{"code":"feature_locked","message":"اشتراک شما چند کاربر را ندارد"}}"""
    }

    val failure = runCatching {
      runBlocking { clientWith(tokens("salem", "r")).post(ApiEndpoints.Shop.STAFF_CODE) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.Forbidden)
    assertEquals("اشتراک شما چند کاربر را ندارد", (failure as ApiFailure).userMessage)
    assertEquals("feature_locked", failure.code)
  }

  @Test
  fun `خواندن دوباره تلاش می‌شود ولی نوشتن نه`() {
    handle("/api/v1/health") { 503 to """{"error":{"code":"unavailable"}}""" }
    handle("/api/v1/shop/sync/push") { 503 to """{"error":{"code":"unavailable"}}""" }

    val client = clientWith(tokens("salem", "r"))

    runCatching { runBlocking { client.getPublic(ApiEndpoints.HEALTH) } }
    //  یک بارِ اول + دو تلاشِ دوباره
    assertEquals(1 + ApiConfigRetries, count("/api/v1/health"))

    runCatching { runBlocking { client.post(ApiEndpoints.Sync.PUSH) } }
    //  فرستادنِ فروش هیچ‌وقت دوباره فرستاده نمی‌شود: دو بار ثبت شدنِ یک
    //  فاکتور از یک بار نرسیدنش بدتر است
    assertEquals(1, count("/api/v1/shop/sync/push"))
  }

  @Test
  fun `درخواستِ حساب‌دار بدونِ توکن اصلاً فرستاده نمی‌شود`() {
    handle("/api/v1/shop/me") { 200 to "{}" }

    val failure = runCatching {
      runBlocking { clientWith(tokens(null, null)).get(ApiEndpoints.Shop.ME) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.SessionExpired)
    assertEquals(0, count("/api/v1/shop/me"))
  }

  @Test
  fun `نشانیِ تنظیم‌نشده، خطای روشن می‌دهد نه سقوط`() {
    val engine = HttpEngine(baseUrl = { "" }, allowInsecure = true)
    val failure = runCatching {
      runBlocking { ApiClient(engine, tokens("t", "r")).getPublic(ApiEndpoints.HEALTH) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.NotConfigured)
  }

  @Test
  fun `وقتی دستگاه نت ندارد، اصلاً به شبکه زده نمی‌شود`() {
    handle("/api/v1/health") { 200 to "{}" }
    val engine = HttpEngine(baseUrl = { base }, allowInsecure = true, online = { false })

    val failure = runCatching {
      runBlocking { ApiClient(engine, tokens("t", "r")).getPublic(ApiEndpoints.HEALTH) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.Offline)
    assertEquals(0, count("/api/v1/health"))
  }

  @Test
  fun `پاسخِ خالی خطا نیست`() {
    handle("/api/v1/me/devices/dev_1") { 200 to "" }
    val body = runBlocking { clientWith(tokens("t", "r")).delete(ApiEndpoints.Me.device("dev_1")) }
    assertTrue(body.isEmpty())
  }

  @Test
  fun `پاسخی که JSON نیست، خطای خوانا می‌دهد`() {
    //  دروازه‌ای که وسط راه HTML برمی‌گرداند
    handle("/api/v1/health") { 200 to "<html>502 Bad Gateway</html>" }
    val failure = runCatching {
      runBlocking { clientWith(tokens("t", "r")).getPublic(ApiEndpoints.HEALTH) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.InvalidResponse)
    assertTrue((failure as ApiFailure).userMessage.isNotBlank())
  }

  private companion object {
    /** همان سقفی که `ApiConfig` دارد؛ اینجا صریح نوشته تا تست خودش گویا باشد */
    const val ApiConfigRetries = 2
  }
}
