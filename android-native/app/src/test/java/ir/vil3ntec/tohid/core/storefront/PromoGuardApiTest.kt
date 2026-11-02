package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.core.storefront.PromoGuardApi.Gate
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  تخفیفِ اولین خرید (MKT-02) و سدِ سوءاستفاده (RSK-09) — پاسخ و بدنه همان است که
 *  `server/src/lib/storefront-promo-guard.js` (`ownerView`، `parseSettings`) می‌دهد و می‌پذیرد.
 */
class PromoGuardApiTest {
  private val A = PromoGuardApi
  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val viewJson = """
    {"settings":{"firstOrder":{"enabled":true,"kind":"percent","bp":1250,"amountMinor":0,"maxMinor":20000,"minSubtotalMinor":50000,"stack":"exclusive"},
                 "limits":{"codePerPhone":1,"codePerDevice":2,"maxDiscountMinor":100000}},
     "recent":[{"kind":"first_order","promoId":"","code":"","phone":"0700111222","device":"ab12cd34","discountMinor":12500,"at":1760000000000,"orderNo":17},
               {"kind":"code","promoId":"p1","code":"EID","phone":"0700333444","device":"","discountMinor":5000,"at":1760000000001,"orderNo":null}],
     "suspicious":{"threshold":3,
       "devices":[{"device":"ab12cd34","phones":["0700111222","0700333444","0700555666"],"count":3,"lastAt":1760000000000}],
       "phones":[{"phone":"0700777888","devices":["11112222","33334444","55556666","77778888"],"count":4,"lastAt":1760000000000}]},
     "somethingNew":true}
  """

  @Test fun `پاسخِ صاحب خوانده می‌شود و کلیدِ ناشناخته نمی‌شکند`() {
    val v = A.parse(obj(viewJson))
    assertTrue(v.settings.firstOrder.enabled)
    assertEquals(1250, v.settings.firstOrder.bp)
    assertEquals(20000L, v.settings.firstOrder.maxMinor)
    assertEquals(A.EXCLUSIVE, v.settings.firstOrder.stack)
    assertEquals(2, v.settings.limits.codePerDevice)
    assertEquals(2, v.recent.size)
    assertNull(v.recent[1].orderNo)
    assertEquals(3, v.suspicious.threshold)
    assertEquals(4, v.suspicious.phones[0].count)
  }

  @Test fun `کادرها از تنظیم پر می‌شوند و صفر یعنی کادرِ خالی`() {
    val f = A.formOf(A.parse(obj(viewJson)).settings)
    assertEquals("12.5", f.percent)
    assertEquals("", f.amount)
    assertEquals("200", f.max)
    assertEquals("500", f.minSubtotal)
    assertEquals("1", f.codePerPhone)
    assertEquals("1000", f.maxDiscount)
    assertEquals("", A.formOf(PromoGuardSettingsDto()).codePerPhone)
  }

  @Test fun `درصد تا صد با دو رقمِ اعشار همان مرزِ سرور است`() {
    assertEquals(1000, A.parseBp("۱۰"))
    assertEquals(1250, A.parseBp("12.5"))
    assertEquals(1205, A.parseBp("12.05"))
    assertEquals(10000, A.parseBp("100"))
    assertEquals(0, A.parseBp(""))
    assertNull(A.parseBp("100.01"))
    assertNull(A.parseBp("1.234"))
    assertNull(A.parseBp("-5"))
  }

  @Test fun `مبلغ افغانیِ درست تا یک میلیون و شمارِ کد تا هزار`() {
    assertEquals(5000L, A.parseAfghani("50"))
    assertEquals(0L, A.parseAfghani(""))
    assertEquals(100_000_000L, A.parseAfghani("1000000"))
    assertNull(A.parseAfghani("1000001"))
    assertNull(A.parseAfghani("50.5"))
    assertEquals(1000, A.parseCount("1000"))
    assertNull(A.parseCount("1001"))
    assertEquals(0, A.parseCount(" "))
  }

  @Test fun `اولین خریدِ روشن بی مقدار و کادرِ نادرست پیش از فرستادن گرفته می‌شود`() {
    assertEquals(setOf("percent"), A.check(PromoGuardApi.Form(enabled = true, kind = A.PERCENT, percent = "")).errors.keys)
    assertEquals(setOf("amount"), A.check(PromoGuardApi.Form(enabled = true, kind = A.AMOUNT, amount = "0")).errors.keys)
    assertTrue(A.check(PromoGuardApi.Form(enabled = false)).errors.isEmpty())
    val bad = A.check(PromoGuardApi.Form(enabled = true, percent = "10", max = "1.5", codePerPhone = "2000", codePerDevice = "x", maxDiscount = "2000000"))
    assertEquals(setOf("max", "codePerPhone", "codePerDevice", "maxDiscount"), bad.errors.keys)
    assertNull(bad.settings)
  }

  @Test fun `بدنهٔ ذخیره فقط مقدارِ همان نوع را دارد`() {
    val p = A.check(PromoGuardApi.Form(enabled = true, kind = A.PERCENT, percent = "10", max = "200", stack = A.EXCLUSIVE, codePerPhone = "1")).settings!!
    val pb = A.body(p)
    val fo = pb["firstOrder"]!!.jsonObject
    assertEquals(setOf("enabled", "kind", "bp", "maxMinor", "minSubtotalMinor", "stack"), fo.keys)
    assertEquals(1000, fo["bp"]!!.jsonPrimitive.int)
    assertEquals(20000L, fo["maxMinor"]!!.jsonPrimitive.long)
    assertEquals("exclusive", fo["stack"]!!.jsonPrimitive.content)
    assertEquals(1, pb["limits"]!!.jsonObject["codePerPhone"]!!.jsonPrimitive.int)
    val a = A.body(A.check(PromoGuardApi.Form(enabled = true, kind = A.AMOUNT, amount = "۷۵", percent = "999")).settings!!)
    val fa = a["firstOrder"]!!.jsonObject
    assertEquals(7500L, fa["amountMinor"]!!.jsonPrimitive.long)
    assertFalse("bp" in fa.keys)
    assertEquals(setOf("codePerPhone", "codePerDevice", "maxDiscountMinor"), a["limits"]!!.jsonObject.keys)
  }

  @Test fun `فهرستِ مشکوک دستگاه با چند شماره و شماره با چند دستگاه را نشان می‌دهد`() {
    val s = A.parse(obj(viewJson)).suspicious
    val rows = A.suspectRows(s)
    assertEquals(2, rows.size)
    assertEquals("یک دستگاه (ab12cd34) با ۳ شماره", rows[0].title)
    assertEquals("0700111222، 0700333444، 0700555666", rows[0].detail)
    assertEquals("شمارهٔ 0700777888 روی ۴ دستگاه", rows[1].title)
    assertTrue(A.suspectSummary(s).startsWith("۲ موردِ مشکوک"))
    val none = SuspiciousDto()
    assertTrue(A.suspectRows(none).isEmpty())
    assertTrue(A.suspectSummary(none).startsWith("موردِ مشکوکی نیست"))
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

  @Test fun `پرچمِ خاموش یا نقشِ دیگر یعنی بخش پنهان است`() {
    val server = TestHttpServer()
    try {
      server.on("/api/v1/shop/storefront/promo-guard") { 403 to """{"error":{"code":"promo_guard_disabled","message":"سدِ تخفیفِ ویترین برای این دکان روشن نیست"}}""" }
      val off = runBlocking { A.load(api(server)) } as ApiResult.Failure
      assertEquals(Gate.OFF, A.gateOf(off.error.status))
      assertEquals("سدِ تخفیفِ ویترین برای این دکان روشن نیست", A.errorText(off.error.code, off.error.userMessage))
      assertEquals(Gate.OFF, A.gateOf(404))
      assertEquals(Gate.ERROR, A.gateOf(500))
    } finally {
      server.close()
    }
  }

  @Test fun `ذخیره با روشِ جایگزینی و خطای فارسیِ سرور`() {
    val server = TestHttpServer()
    try {
      val sent = mutableListOf<TestHttpServer.Request>()
      server.on("/api/v1/shop/storefront/promo-guard") { r ->
        sent += r
        if (obj(r.body)["limits"]!!.jsonObject["codePerPhone"]!!.jsonPrimitive.int == 9)
          400 to """{"error":{"code":"bad_promo_guard","message":"سقفِ کد برای هر شماره معتبر نیست"}}"""
        else 200 to """{"settings":${obj(r.body)},"updatedAt":5}"""
      }
      val next = A.check(PromoGuardApi.Form(enabled = true, percent = "10", codePerDevice = "1")).settings!!
      val saved = runBlocking { A.save(api(server), next) }.valueOrNull()!!
      assertEquals(next, saved)
      assertEquals("PUT", sent[0].method)
      assertTrue(obj(sent[0].body)["firstOrder"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
      val bad = runBlocking { A.save(api(server), next.copy(limits = next.limits.copy(codePerPhone = 9))) } as ApiResult.Failure
      assertEquals("سقفِ کد برای هر شماره معتبر نیست", A.errorText(bad.error.code, bad.error.userMessage))
    } finally {
      server.close()
    }
  }
}
