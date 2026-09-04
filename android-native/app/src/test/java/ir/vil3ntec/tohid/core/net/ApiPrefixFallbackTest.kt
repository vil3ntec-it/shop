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
