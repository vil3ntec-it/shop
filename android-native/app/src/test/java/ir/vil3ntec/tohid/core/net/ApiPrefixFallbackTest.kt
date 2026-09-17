package ir.vil3ntec.tohid.core.net

import ir.vil3ntec.tohid.core.config.ApiConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  سروری که `/api/v1` را نمی‌شناسد، ولی `/api` را بله.
 *
 *  ── چرا این آزمون هست ──────────────────────────────────────────────
 *  سه گزارشِ جدا از صاحب مخزن — حساب ساخته نمی‌شود، کدِ پیوستن ساخته
 *  نمی‌شود، صفحهٔ کارمندان می‌گوید این بخش روی سرور نیست — یک ریشه
 *  داشتند: در سرورهای پیش از شهریور، `/api` اول سوار می‌شد و درخواستِ
 *  `/api/v1/x` را هم همان می‌قاپید؛ داخلش چنین مسیری نبود و به لایهٔ
 *  دفترِ داده می‌رسید که توکن و دکان می‌خواهد. پاسخ: ۴۰۴ یا ۴۰۱، برای
 *  **هر** مسیری.
 *
 *  سرور درست شد، ولی سرورِ هر دکان‌دار همان روز به‌روز نمی‌شود. پس
 *  برنامه هم باید از پسش بربیاید — و این آزمون همان را ثابت می‌کند.
 */
class ApiPrefixFallbackTest {

  private lateinit var server: TestHttpServer

  @Before
  fun start() { server = TestHttpServer() }

  @After
  fun stop() = server.close()

  private fun engine() = HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true)

  @Test
  fun `مسیرِ نسخه‌دار که نبود، بی‌نسخه امتحان می‌شود`() {
    server.on("/api${ApiEndpoints.CONFIG}") { 200 to """{"otpEnabled":true}""" }
    val engine = engine()

    val body = kotlinx.coroutines.runBlocking {
      engine.send("GET", ApiEndpoints.CONFIG, token = null)
    }
    assertEquals("true", body["otpEnabled"].toString())
    assertEquals("از این پس همان راهِ کارآمد", ApiConfig.API_PREFIX_PLAIN, engine.activePrefix)
  }

  @Test
  fun `وقتی یک بار پیدا شد، دیگر مسیرِ نسخه‌دار امتحان نمی‌شود`() {
    server.on("/api${ApiEndpoints.CONFIG}") { 200 to """{"otpEnabled":true}""" }
    server.on("/api${ApiEndpoints.HEALTH}") { 200 to """{"ok":true}""" }
    val engine = engine()

    kotlinx.coroutines.runBlocking {
      engine.send("GET", ApiEndpoints.CONFIG, token = null)
      engine.send("GET", ApiEndpoints.HEALTH, token = null)
    }
    //  درخواستِ دوم مستقیم سراغِ همان راه رفته: هیچ ۴۰۴ اضافه‌ای
    assertEquals(1, server.hits("/api${ApiEndpoints.HEALTH}"))
    assertEquals(ApiConfig.API_PREFIX_PLAIN, engine.activePrefix)
  }

  @Test
  fun `سرورِ درست، همان مسیرِ نسخه‌دار می‌ماند`() {
    server.on("/api/v1${ApiEndpoints.CONFIG}") { 200 to """{"otpEnabled":true}""" }
    val engine = engine()

    kotlinx.coroutines.runBlocking { engine.send("GET", ApiEndpoints.CONFIG, token = null) }
    assertEquals(ApiConfig.API_PREFIX, engine.activePrefix)
    assertEquals(1, server.hits("/api/v1${ApiEndpoints.CONFIG}"))
  }

  @Test
  fun `هیچ‌کدام که نبود، خطای مسیرِ اصلی گفته می‌شود`() {
    val engine = engine()
    val failure = runCatching {
      kotlinx.coroutines.runBlocking { engine.send("GET", "/چیزی-که-نیست", token = null) }
    }.exceptionOrNull()
    assertTrue("باید NotFound باشد", failure is ApiFailure.NotFound)
    //  و پیشوند دست‌نخورده می‌ماند؛ چیزی کشف نشده که پین شود
    assertEquals(ApiConfig.API_PREFIX, engine.activePrefix)
  }

  /**
   *  رمزِ غلط، یک بار فرستاده می‌شود — نه دو بار.
   *
   *  ── چه چیزی را نگه می‌دارد ────────────────────────────────────────
   *  «رمز اشتباه است» هم یک ۴۰۱ بی‌توکن است و تا دیروز درست می‌افتاد
   *  داخلِ آزمونِ پیشوند: هر ورودِ ناموفق دو بار به سرور می‌رفت. یعنی
   *  سقفِ نرخِ سرور نصف می‌شد و شمارندهٔ قفلِ حساب دو برابر می‌شمرد —
   *  کاربر بعد از چهار اشتباه قفل می‌شد، نه هشت تا.
   *
   *  اگر روزی کسی شرطِ `once` را دوباره باز کند، همین‌جا قرمز می‌شود.
   */
  @Test
  fun `رمزِ غلط دوباره فرستاده نمی‌شود`() {
    server.on("/api/v1${ApiEndpoints.Auth.LOGIN}") {
      401 to """{"error":{"code":"bad_credentials","message":"ایمیل/شماره یا رمز درست نیست"}}"""
    }
    server.on("/api${ApiEndpoints.Auth.LOGIN}") {
      401 to """{"error":{"code":"bad_credentials","message":"ایمیل/شماره یا رمز درست نیست"}}"""
    }
    val engine = engine()

    val failure = runCatching {
      kotlinx.coroutines.runBlocking {
        engine.send(
          "POST",
          ApiEndpoints.Auth.LOGIN,
          body = kotlinx.serialization.json.buildJsonObject {
            put("identifier", kotlinx.serialization.json.JsonPrimitive("a@b.co"))
            put("password", kotlinx.serialization.json.JsonPrimitive("غلط"))
          },
          token = null,
          idempotent = false,
        )
      }
    }.exceptionOrNull()

    assertTrue("رمزِ غلط یعنی Unauthorized", failure is ApiFailure.Unauthorized)
    assertEquals("bad_credentials", (failure as ApiFailure).code)
    assertEquals("یک بار، نه بیشتر", 1, server.hits("/api/v1${ApiEndpoints.Auth.LOGIN}"))
    assertEquals("راهِ دوم اصلاً امتحان نشده", 0, server.hits("/api${ApiEndpoints.Auth.LOGIN}"))
    //  و پیشوند هم جابه‌جا نشده
    assertEquals(ApiConfig.API_PREFIX, engine.activePrefix)
  }

  /**
   *  ولی ۴۰۱ـی که از «توکن لازم است» می‌آید، هنوز آزمون را راه می‌اندازد.
   *
   *  همان اشکالِ سرورهای قدیمی که کلِ این سازوکار برایش هست: مسیرِ
   *  `/api/v1/…` پیدا نمی‌شد و به لایهٔ توکن‌خواه می‌رسید.
   */
  @Test
  fun `۴۰۱ِ مسیرِ گمشده هنوز راهِ دوم را امتحان می‌کند`() {
    server.on("/api/v1${ApiEndpoints.Auth.LOGIN}") {
      401 to """{"error":{"code":"unauthorized","message":"احراز هویت لازم است"}}"""
    }
    server.on("/api${ApiEndpoints.Auth.LOGIN}") { 200 to """{"accessToken":"tk"}""" }
    val engine = engine()

    val body = kotlinx.coroutines.runBlocking {
      engine.send(
        "POST",
        ApiEndpoints.Auth.LOGIN,
        body = kotlinx.serialization.json.buildJsonObject {
          put("identifier", kotlinx.serialization.json.JsonPrimitive("a@b.co"))
          put("password", kotlinx.serialization.json.JsonPrimitive("درست"))
        },
        token = null,
        idempotent = false,
      )
    }
    assertTrue(body.containsKey("accessToken"))
    assertEquals(ApiConfig.API_PREFIX_PLAIN, engine.activePrefix)
  }

  @Test
  fun `ساختنِ حساب هم از همین راه می‌رود`() {
    //  همان چیزی که کار نمی‌کرد: POST، نه GET
    server.on("/api${ApiEndpoints.Auth.REGISTER}") {
      201 to """{"accessToken":"tk","user":{"id":"usr_1","name":"هارون"}}"""
    }
    val engine = engine()
    val body = kotlinx.coroutines.runBlocking {
      engine.send(
        "POST",
        ApiEndpoints.Auth.REGISTER,
        body = kotlinx.serialization.json.buildJsonObject {
          put("email", kotlinx.serialization.json.JsonPrimitive("a@b.co"))
        },
        token = null,
        idempotent = false,
      )
    }
    assertTrue(body.containsKey("accessToken"))
  }
}
