package ir.vil3ntec.tohid.core.net

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

  private lateinit var server: TestHttpServer

  private fun count(path: String): Int = server.hits("/api/v1$path")

  private fun handle(path: String, body: (TestHttpServer.Request) -> Pair<Int, String>) =
    server.on("/api/v1$path", body)

  @Before
  fun start() { server = TestHttpServer() }

  @After
  fun stop() = server.close()

  /** لایه‌ها را همان‌طور می‌بندد که برنامه می‌بندد */
  private fun clientWith(tokens: TokenStorage, onLost: () -> Unit = {}): ApiClient {
    //  `allowInsecure` روشن است چون سرورِ آزمایشی روی `http` است — همان
    //  چیزی که در ساختِ آزمایشی هم مجاز است و در نسخهٔ منتشرشده نه
    val engine = HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true)
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
    handle(ApiEndpoints.Auth.REFRESH) { 200 to """{"accessToken":"tazeh","accessExpiresAt":123}""" }
    handle(ApiEndpoints.Shop.ME) { request ->
      if (request.bearer == "tazeh") 200 to """{"shop":{"id":"shp_1","name":"دکان من"},"role":"owner"}"""
      else 401 to """{"error":{"code":"invalid_token","message":"نشست منقضی شده است"}}"""
    }

    val store = tokens(access = "pir", refresh = "refresh-90-rouzeh")
    val body = runBlocking { clientWith(store).get(ApiEndpoints.Shop.ME) }

    assertEquals("shp_1", (body["shop"] as kotlinx.serialization.json.JsonObject)["id"]?.let {
      (it as JsonPrimitive).content
    })
    //  یک بار با توکنِ پیر، یک بار با تازه
    assertEquals(2, count(ApiEndpoints.Shop.ME))
    assertEquals(1, count(ApiEndpoints.Auth.REFRESH))
    //  توکنِ تازه ذخیره شده و کاربر چیزی ندیده
    assertEquals("tazeh", store.accessToken)
    assertEquals("refresh-90-rouzeh", store.refreshToken)
    assertEquals(123L, store.accessExpiresAt)
  }

  @Test
  fun `اگر تازه‌سازی هم نگرفت، نشست پاک می‌شود و کاربر خبردار`() {
    handle(ApiEndpoints.Auth.REFRESH) { 401 to """{"error":{"code":"invalid_token"}}""" }
    handle(ApiEndpoints.Shop.ME) { 401 to """{"error":{"code":"invalid_token"}}""" }

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
    handle(ApiEndpoints.Shop.ME) { 401 to """{"error":{"code":"invalid_token"}}""" }
    //  مسیرِ تازه‌سازی اصلاً وجود ندارد → سرور در دسترس هست ولی…
    handle(ApiEndpoints.Auth.REFRESH) { 503 to """{"error":{"code":"unavailable"}}""" }

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

  /*
   *  ── تازه‌سازیِ چرخشی (بندِ ۲۹.۲ و ۲۳.۴ سندِ v3: «بستن و باز کردن یا قطع و
   *  وصلِ اینترنت نباید کاربر را بی‌دلیل خارج کند») ───────────────────────
   *  سرورِ واقعی در هر `/auth/refresh` توکنِ تازه‌سازیِ **تازه** می‌دهد و
   *  قبلی را می‌بندد؛ اگر قبلی پس از سی ثانیه برگردد «دزدی» حساب می‌شود و
   *  کلِ نشستِ آن دستگاه بسته می‌شود (`refresh_reused`). برنامه تا امروز
   *  توکنِ تازه را دور می‌ریخت، پس دومین تازه‌سازی — یعنی حدودِ دو ساعت
   *  کار، یا فردای همان روز — کاربر را بیرون می‌انداخت.
   */
  @Test
  fun `توکنِ تازه‌سازیِ چرخیده ذخیره می‌شود و دومین تازه‌سازی هم بیرون نمی‌اندازد`() {
    //  سرورِ چرخشی، همان‌طور که `lib/tokens.rotateRefresh` رفتار می‌کند
    var live = "r0"
    var generation = 0
    handle(ApiEndpoints.Auth.REFRESH) { request ->
      if (request.body.contains("\"$live\"")) {
        generation++
        live = "r$generation"
        200 to """{"accessToken":"a$generation","accessExpiresAt":${1000 + generation},"refreshToken":"$live"}"""
      } else {
        401 to """{"error":{"code":"refresh_reused","message":"این نشست جای دیگری هم به کار رفته و بسته شد"}}"""
      }
    }
    //  هر توکنِ دسترسی فقط یک بار پذیرفته می‌شود — یعنی هر درخواستِ بعدی
    //  دوباره به تازه‌سازی می‌خورد، مثلِ گذشتنِ یک ساعت
    val used = mutableSetOf<String>()
    handle(ApiEndpoints.Shop.ME) { request ->
      if (request.bearer.startsWith("a") && used.add(request.bearer)) 200 to """{"shop":{"id":"shp_1"}}"""
      else 401 to """{"error":{"code":"invalid_token"}}"""
    }

    val store = tokens(access = "pir", refresh = "r0")
    var told = false
    val client = clientWith(store) { told = true }
    runBlocking { client.get(ApiEndpoints.Shop.ME) }
    assertEquals("توکنِ چرخیده باید جای قبلی بنشیند", "r1", store.refreshToken)
    runBlocking { client.get(ApiEndpoints.Shop.ME) }
    runBlocking { client.get(ApiEndpoints.Shop.ME) }

    assertFalse("سه تازه‌سازیِ پیاپی نباید کاربر را بیرون بیندازد", told)
    assertEquals("r3", store.refreshToken)
    assertEquals("a3", store.accessToken)
    assertEquals(1003L, store.accessExpiresAt)
  }

  @Test
  fun `نام‌های قراردادِ ورود هم خوانده می‌شوند`() {
    //  `/auth/refresh` هر دو شکل را می‌فرستد؛ اگر روزی فقط شکلِ قرارداد ماند،
    //  توکنِ چرخیده نباید گم شود
    handle(ApiEndpoints.Auth.REFRESH) { 200 to """{"access_token":"tazeh","refresh_token":"r-tazeh","access_expires_in":3600}""" }
    handle(ApiEndpoints.Shop.ME) { request ->
      if (request.bearer == "tazeh") 200 to "{}" else 401 to """{"error":{"code":"invalid_token"}}"""
    }
    val store = tokens(access = "pir", refresh = "r-pir")
    runBlocking { clientWith(store).get(ApiEndpoints.Shop.ME) }
    assertEquals("tazeh", store.accessToken)
    assertEquals("r-tazeh", store.refreshToken)
    assertTrue("مهلت از `access_expires_in` ساخته شود", store.accessExpiresAt > System.currentTimeMillis())
  }

  @Test
  fun `سقفِ نرخ روی تازه‌سازی نشست را پاک نمی‌کند`() {
    handle(ApiEndpoints.Shop.ME) { 401 to """{"error":{"code":"invalid_token"}}""" }
    handle(ApiEndpoints.Auth.REFRESH) { 429 to """{"error":{"code":"rate_limited","message":"درخواست‌ها زیاد بود"}}""" }

    val store = tokens(access = "pir", refresh = "salem")
    var told = false
    val failure = runCatching {
      runBlocking { clientWith(store) { told = true }.get(ApiEndpoints.Shop.ME) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.RateLimited)
    assertFalse("۴۲۹ یعنی «الان نه»، نه «نشست مرد»", told)
    assertEquals("salem", store.refreshToken)
    assertEquals("pir", store.accessToken)
  }

  @Test
  fun `پاسخِ غیرِ JSON روی تازه‌سازی (پورتالِ وای‌فای) نشست را پاک نمی‌کند`() {
    handle(ApiEndpoints.Shop.ME) { 401 to """{"error":{"code":"invalid_token"}}""" }
    //  وای‌فایِ هتل یا صفحهٔ «بسته تمام شد»ِ اپراتور: ۲۰۰ با HTML
    handle(ApiEndpoints.Auth.REFRESH) { 200 to "<html>لطفاً وارد شوید</html>" }

    val store = tokens(access = "pir", refresh = "salem")
    var told = false
    val failure = runCatching {
      runBlocking { clientWith(store) { told = true }.get(ApiEndpoints.Shop.ME) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.InvalidResponse)
    assertFalse(told)
    assertEquals("salem", store.refreshToken)
  }

  @Test
  fun `بدونِ توکنِ تازه‌سازی، همان یک بار تمام است`() {
    handle(ApiEndpoints.Shop.ME) { 401 to """{"error":{"code":"invalid_token"}}""" }
    handle(ApiEndpoints.Auth.REFRESH) { 200 to """{"accessToken":"x"}""" }

    val store = tokens(access = "pir", refresh = null)
    val failure = runCatching {
      runBlocking { clientWith(store).get(ApiEndpoints.Shop.ME) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.SessionExpired)
    assertEquals("تازه‌سازیِ بی‌توکن نباید حتی امتحان شود", 0, count(ApiEndpoints.Auth.REFRESH))
  }

  @Test
  fun `پیامِ فارسیِ سرور به کاربر می‌رسد`() {
    //  همان باگی که هر خطای سرور را به یک استثنای انگلیسیِ داخلی تبدیل
    //  می‌کرد
    handle(ApiEndpoints.Shop.STAFF_CODE) {
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
    handle(ApiEndpoints.HEALTH) { 503 to """{"error":{"code":"unavailable"}}""" }
    handle(ApiEndpoints.Sync.PUSH) { 503 to """{"error":{"code":"unavailable"}}""" }

    val client = clientWith(tokens("salem", "r"))

    runCatching { runBlocking { client.getPublic(ApiEndpoints.HEALTH) } }
    //  یک بارِ اول + دو تلاشِ دوباره
    assertEquals(1 + ApiConfigRetries, count(ApiEndpoints.HEALTH))

    runCatching { runBlocking { client.post(ApiEndpoints.Sync.PUSH) } }
    //  فرستادنِ فروش هیچ‌وقت دوباره فرستاده نمی‌شود: دو بار ثبت شدنِ یک
    //  فاکتور از یک بار نرسیدنش بدتر است
    assertEquals(1, count(ApiEndpoints.Sync.PUSH))
  }

  @Test
  fun `درخواستِ حساب‌دار بدونِ توکن اصلاً فرستاده نمی‌شود`() {
    handle(ApiEndpoints.Shop.ME) { 200 to "{}" }

    val failure = runCatching {
      runBlocking { clientWith(tokens(null, null)).get(ApiEndpoints.Shop.ME) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.SessionExpired)
    assertEquals(0, count(ApiEndpoints.Shop.ME))
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
    handle(ApiEndpoints.HEALTH) { 200 to "{}" }
    val engine = HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true, online = { false })

    val failure = runCatching {
      runBlocking { ApiClient(engine, tokens("t", "r")).getPublic(ApiEndpoints.HEALTH) }
    }.exceptionOrNull()

    assertTrue(failure is ApiFailure.Offline)
    assertEquals(0, count(ApiEndpoints.HEALTH))
  }

  @Test
  fun `پاسخِ خالی خطا نیست`() {
    handle(ApiEndpoints.Me.device("dev_1")) { 200 to "" }
    val body = runBlocking { clientWith(tokens("t", "r")).delete(ApiEndpoints.Me.device("dev_1")) }
    assertTrue(body.isEmpty())
  }

  @Test
  fun `پاسخی که JSON نیست، خطای خوانا می‌دهد`() {
    //  دروازه‌ای که وسط راه HTML برمی‌گرداند
    handle(ApiEndpoints.HEALTH) { 200 to "<html>502 Bad Gateway</html>" }
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
