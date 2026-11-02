package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  زمانِ تقریبیِ تحویلِ ویترین (WEB-04) — پاسخ و بدنه همان است که `server/src/routes/storefront-eta.js`
 *  و `lib/storefront-eta.js` (`ownerView`، `saveSettings`) می‌دهند و می‌پذیرند.
 */
class StorefrontEtaApiTest {
  private val A = StorefrontEtaApi
  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val Z1 = "01JZONE0000000000000000001"
  private val Z2 = "01JZONE0000000000000000002"

  private val onJson = """
    {"enabled":true,
     "settings":{"show":true,"prepMinutes":12,"travelMinutes":25,"parallel":3,"zoneTravel":{"$Z1":30},"version":4,"updatedAt":1760000000000},
     "zones":[{"id":"$Z1","name":"کارته‌سه","active":true},{"id":"$Z2","name":"شهرِ نو","active":false}],
     "signals":{"queue":2,"prepMinutes":14,"samples":5,"prepFromHistory":true,"busyExtraMinutes":10,
       "couriers":{"couriers":2,"cap":3,"live":1,"waiting":0}},
     "preview":{"available":true,"approximate":true,"delivery":{"minMinutes":40,"maxMinutes":60},"pickup":null,
       "zones":[{"id":"$Z1","minMinutes":45,"maxMinutes":65}],"updatedAt":1760000000000},
     "defaults":{"show":true,"prepMinutes":15,"travelMinutes":20,"parallel":2},
     "permissions":{"manage":true},"somethingNew":1}
  """

  @Test fun `پاسخِ صاحب با پرچمِ روشن خوانده می‌شود و کلیدِ ناشناخته نمی‌شکند`() {
    val v = A.parse(obj(onJson))
    assertTrue(v.enabled)
    assertEquals(12, v.settings.prepMinutes)
    assertEquals(25, v.settings.travelMinutes)
    assertEquals(3, v.settings.parallel)
    assertEquals(mapOf(Z1 to 30), v.settings.zoneTravel)
    assertEquals(4L, v.settings.version)
    assertEquals(listOf(Z1, Z2), v.zones.map { it.id })
    assertFalse(v.zones[1].active)
    assertEquals(2, v.signals.queue)
    assertEquals(3, v.signals.couriers.cap)
    assertEquals(EtaRangeDto(40, 60), v.preview!!.delivery)
    assertNull(v.preview!!.pickup)
    assertTrue(A.visible(v))
  }

  @Test fun `پرچمِ خاموش یا نقشِ بی تنظیم یا بی پاسخ یعنی بخش پنهان است`() {
    assertFalse(A.visible(A.parse(obj("""{"enabled":false}"""))))
    assertFalse(A.visible(A.parse(obj(onJson.replace(""""manage":true""", """"manage":false""")))))
    assertFalse(A.visible(null))
  }

  @Test fun `کادرها از پاسخ پر می‌شوند و ناحیهٔ بی دقیقه خالی است`() {
    val f = A.formOf(A.parse(obj(onJson)))
    assertEquals("12", f.prep)
    assertEquals("25", f.travel)
    assertEquals("3", f.parallel)
    assertEquals(mapOf(Z1 to "30", Z2 to ""), f.zones)
  }

  @Test fun `مرزهای کادر همان مرزهای سرور است`() {
    val ok = StorefrontEtaApi.Form(prep = "1", travel = "0", parallel = "1")
    assertTrue(A.check(ok, emptyList(), 0).errors.isEmpty())
    assertTrue(A.check(ok.copy(prep = "240", travel = "240", parallel = "20"), emptyList(), 0).errors.isEmpty())
    assertEquals(setOf("prep"), A.check(ok.copy(prep = "0"), emptyList(), 0).errors.keys)
    assertEquals(setOf("prep"), A.check(ok.copy(prep = "241"), emptyList(), 0).errors.keys)
    assertEquals(setOf("travel"), A.check(ok.copy(travel = "241"), emptyList(), 0).errors.keys)
    assertEquals(setOf("travel"), A.check(ok.copy(travel = "-1"), emptyList(), 0).errors.keys)
    assertEquals(setOf("parallel"), A.check(ok.copy(parallel = "21"), emptyList(), 0).errors.keys)
    assertEquals(setOf("parallel"), A.check(ok.copy(parallel = "0"), emptyList(), 0).errors.keys)
    assertEquals(setOf("prep"), A.check(ok.copy(prep = "12.5"), emptyList(), 0).errors.keys)
    assertEquals(setOf("prep"), A.check(ok.copy(prep = ""), emptyList(), 0).errors.keys)
    val z = ok.copy(zones = mapOf(Z1 to "241"))
    assertEquals(setOf("zone:$Z1"), A.check(z, listOf(Z1), 0).errors.keys)
    assertNull(A.check(z, listOf(Z1), 0).body)
    assertTrue(A.check(ok.copy(zones = mapOf(Z1 to "240")), listOf(Z1), 0).errors.isEmpty())
  }

  @Test fun `رقمِ فارسی پذیرفته و عددِ لاتین فرستاده می‌شود`() {
    val b = A.check(StorefrontEtaApi.Form(show = false, prep = "۱۵", travel = "۲۰", parallel = "۲", zones = mapOf(Z1 to "۳۵")), listOf(Z1), 0).body!!
    assertEquals(15, b["prepMinutes"]!!.jsonPrimitive.int)
    assertEquals(20, b["travelMinutes"]!!.jsonPrimitive.int)
    assertEquals(35, b["zoneTravel"]!!.jsonObject[Z1]!!.jsonPrimitive.int)
    assertFalse(b["show"]!!.jsonPrimitive.boolean)
  }

  @Test fun `بدنهٔ ذخیره نسخهٔ پیشین را دارد و ناحیهٔ خالی و بی فهرست فرستاده نمی‌شود`() {
    val f = StorefrontEtaApi.Form(prep = "10", travel = "20", parallel = "2", zones = mapOf(Z1 to "", Z2 to "45", "01JZONEUNKNOWN000000000000" to "9"))
    val b = A.check(f, listOf(Z1, Z2), 7).body!!
    assertEquals(setOf("show", "prepMinutes", "travelMinutes", "parallel", "zoneTravel", "expectedVersion"), b.keys)
    assertEquals(7L, b["expectedVersion"]!!.jsonPrimitive.long)
    assertEquals(setOf(Z2), b["zoneTravel"]!!.jsonObject.keys)
    //  ناحیه‌های ارسال خاموش ⇒ فهرستِ خالی ⇒ ذخیرهٔ پیشینِ سرور دست نمی‌خورد
    assertFalse("zoneTravel" in A.check(f, emptyList(), 7).body!!.keys)
  }

  @Test fun `پیش‌نمایش همان بازهٔ مشتری یا دلیلِ بی عدد را نشان می‌دهد`() {
    val v = A.parse(obj(onJson))
    assertEquals(listOf("ارسال: ۴۰ تا ۶۰ دقیقه", "کارته‌سه: ۴۵ تا ۶۵ دقیقه"), A.previewLines(v.preview, v.zones))
    assertEquals(listOf("ویترین هنوز ساخته نشده؛ پیش‌نمایشی نیست"), A.previewLines(null, v.zones))
    val paused = EtaPreviewDto(available = false, reason = "paused")
    assertEquals(listOf("مشتری الان عددی نمی‌بیند: پذیرشِ سفارش موقتاً متوقف است"), A.previewLines(paused, v.zones))
    assertTrue(A.signalLine(v.signals).contains("از سفارش‌های اخیر"))
  }

  /* --------------------- در برابرِ سرورِ واقعیِ HTTP --------------------- */

  private fun api(server: TestHttpServer): ApiClient {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = Long.MAX_VALUE
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens)
  }

  @Test fun `بارگذاری و ذخیره همان مسیر و روش و بدنه را می‌فرستد`() {
    val server = TestHttpServer()
    try {
      val sent = mutableListOf<TestHttpServer.Request>()
      server.on("/api/v1/shop/storefront/eta") { r ->
        sent += r
        if (r.method == "GET") 200 to onJson
        else 200 to """{"settings":{"show":false,"prepMinutes":10,"travelMinutes":20,"parallel":2,"zoneTravel":{},"version":5,"updatedAt":2}}"""
      }
      val v = runBlocking { A.load(api(server)) }.valueOrNull()
      assertNotNull(v)
      val body = A.check(A.formOf(v!!).copy(show = false, prep = "10"), v.zones.map { it.id }, v.settings.version).body!!
      val s = runBlocking { A.save(api(server), body) }.valueOrNull()!!
      assertEquals(5L, s.version)
      assertEquals("GET", sent[0].method)
      assertEquals("PUT", sent[1].method)
      val put = obj(sent[1].body)
      assertEquals(10, put["prepMinutes"]!!.jsonPrimitive.int)
      assertEquals(4L, put["expectedVersion"]!!.jsonPrimitive.long)
    } finally {
      server.close()
    }
  }

  @Test fun `خطای سرور فارسی نشان داده می‌شود`() {
    val server = TestHttpServer()
    try {
      server.on("/api/v1/shop/storefront/eta") { r ->
        if (r.bearer.isNotEmpty() && obj(r.body)["expectedVersion"]?.jsonPrimitive?.long == 1L)
          409 to """{"error":{"code":"version_conflict","message":"تنظیم‌ها همین حالا جای دیگری عوض شد"}}"""
        else 403 to """{"error":{"code":"subscription_required","message":"این قابلیت نیازمندِ اشتراک است"}}"""
      }
      val stale = runBlocking { A.save(api(server), A.body(true, 10, 20, 2, null, 1)) } as ApiResult.Failure
      assertEquals("تنظیم در این میان جای دیگری عوض شد؛ دوباره باز کنید", A.errorText(stale.error.code, stale.error.userMessage))
      val paid = runBlocking { A.save(api(server), A.body(true, 10, 20, 2, null, 0)) } as ApiResult.Failure
      assertEquals("این قابلیت نیازمندِ اشتراک است", A.errorText(paid.error.code, paid.error.userMessage))
      assertEquals("زمانِ تقریبیِ تحویل برای این دکان روشن نیست", A.errorText("storefront_eta_disabled", "x"))
    } finally {
      server.close()
    }
  }
}
