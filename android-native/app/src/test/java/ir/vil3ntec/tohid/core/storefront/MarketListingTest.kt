package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.core.storefront.MarketListing.Form
import ir.vil3ntec.tohid.core.storefront.MarketListing.Gate
import ir.vil3ntec.tohid.core.storefront.MarketListing.LocationEdit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 *  «نمایش در بازارچه» — بدنه و پاسخ همان است که `server/src/lib/market.js`
 *  (`listingView`، `saveListing`) و `routes/market-admin.js` می‌دهند و می‌خواهند.
 */
class MarketListingTest {
  private val M = MarketListing
  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  /** همان ۱۲ کلیدِ `CATEGORIES` ِ سرور، به همان ترتیب */
  private val serverKeys = listOf(
    "grocery", "bakery", "produce", "butcher", "dairy", "sweets",
    "beauty", "household", "stationery", "clothing", "electronics", "other",
  )

  /* ------------------------------ پاسخ ------------------------------ */

  @Test fun `پاسخِ کاملِ GET خوانده می‌شود و کلیدِ ناشناخته نمی‌شکند`() {
    val s = M.parseState(obj("""
      {"directory":true,"listing":{"listed":true,"category":"bakery","area":"کارتهٔ سه",
       "location":{"lat":34.52,"lng":69.18},"updatedAt":1760000000000,
       "categories":[{"key":"grocery","label":"سوپرمارکت و خواربار"},{"key":"bakery","label":"نانوایی"}],
       "somethingNew":1}}
    """))
    assertTrue(s.directory)
    assertTrue(s.listing.listed)
    assertEquals("bakery", s.listing.category)
    assertEquals("کارتهٔ سه", s.listing.area)
    assertEquals(34.52, s.listing.location!!.lat, 0.0)
    assertEquals(69.18, s.listing.location!!.lng, 0.0)
    assertEquals(1760000000000L, s.listing.updatedAt)
    assertEquals("نانوایی", M.labelOf("bakery", M.categoriesOf(s.listing)))
  }

  @Test fun `دکانی که هنوز ردیف ندارد — خاموش، بی موقعیت`() {
    //  `listingView(null)` ِ سرور
    val s = M.parseState(obj("""
      {"directory":false,"listing":{"listed":false,"category":"","area":"","location":null,"updatedAt":null,"categories":[]}}
    """))
    assertFalse(s.directory)
    assertFalse(s.listing.listed)
    assertEquals("", s.listing.category)
    assertNull(s.listing.location)
    assertNull(s.listing.updatedAt)
    //  فهرستی نیامد ⇒ همان ۱۲ دستهٔ ثابت
    assertEquals(serverKeys, M.categoriesOf(s.listing).map { it.key })
  }

  @Test fun `فهرستِ ثابتِ دسته‌ها همان ۱۲ کلیدِ سرور است، با برچسبِ فارسی`() {
    assertEquals(serverKeys, M.CATEGORIES.map { it.key })
    assertTrue(M.CATEGORIES.all { it.label.isNotBlank() && it.label.none { c -> c in 'a'..'z' } })
    assertEquals(12, M.CATEGORIES.map { it.label }.toSet().size)
  }

  @Test fun `فهرستِ سرور بر فهرستِ ثابت مقدم است و کلیدِ ناشناخته خامِ خودش را نشان می‌دهد`() {
    val l = MarketListingDto(categories = listOf(MarketCategoryDto("pharmacy", "دواخانه"), MarketCategoryDto("", "بی کلید")))
    val cats = M.categoriesOf(l)
    assertEquals(listOf("pharmacy"), cats.map { it.key })
    assertEquals("دواخانه", M.labelOf("pharmacy", cats))
    assertEquals("bakery", M.labelOf("bakery", cats))
    assertEquals("", M.labelOf("", cats))
  }

  @Test fun `پاسخِ PUT همان listing است؛ بی آن، پاسخِ نامعتبر`() {
    val l = M.parseSaved(obj("""{"listing":{"listed":true,"category":"dairy","area":"","location":null,"updatedAt":5,"categories":[]}}"""))
    assertTrue(l.listed)
    assertEquals("dairy", l.category)
    try {
      M.parseSaved(obj("""{"ok":true}"""))
      fail("بی listing نباید پذیرفته شود")
    } catch (e: ApiFailure) {
      assertEquals("invalid_response", e.code)
    }
  }

  /* ------------------------------ ورودی ------------------------------ */

  @Test fun `ناحیه — تا ۶۰ نویسه پذیرفته، بیشتر خطا — همان شمارشِ سرور`() {
    assertNull(M.areaError(""))
    assertNull(M.areaError("ا".repeat(60)))
    assertNotNull(M.areaError("ا".repeat(61)))
    //  فاصله و نویسهٔ کنترلی پیش از شمارش می‌روند (`stripControl(str(v))`)
    assertNull(M.areaError("  " + "ب".repeat(60) + "\n\t "))
    assertNull(M.areaError("ب".repeat(30) + "\u0007".repeat(40) + "ب".repeat(30)))
    //  یک ایموجی دو واحدِ UTF-16 است، همان `slice` ِ JS
    assertNull(M.areaError("😀".repeat(30)))
    assertNotNull(M.areaError("😀".repeat(30) + "ا"))
  }

  @Test fun `پاک‌سازیِ ناحیه همان stripControl و trim ِ سرور است`() {
    assertEquals("کارتهٔ سه", M.cleanArea("  کارتهٔ\u0000 سه \n"))
    assertEquals("ab", M.cleanArea("a\u007Fb"))
    assertEquals("😀x", M.cleanArea("😀\u001Fx"))
  }

  @Test fun `دسته — خالی فقط وقتی در فهرست نیست؛ کلیدِ بیرون از فهرست خطا`() {
    val cats = M.CATEGORIES
    assertNull(M.categoryError("", listed = false, cats))
    assertNotNull(M.categoryError("", listed = true, cats))
    assertNull(M.categoryError("bakery", listed = true, cats))
    assertNotNull(M.categoryError("pharmacy", listed = false, cats))
    assertNotNull(M.categoryError("Bakery", listed = true, cats))
  }

  @Test fun `خطاهای فرم با کلیدِ همان کادر`() {
    assertTrue(M.errors(Form(listed = true, category = "grocery", area = "مکرویان"), M.CATEGORIES).isEmpty())
    val e = M.errors(Form(listed = true, category = "", area = "x".repeat(61)), M.CATEGORIES)
    assertEquals(setOf("category", "area"), e.keys)
  }

  @Test fun `موقعیت فقط درشت — یک‌صدمِ درجه، نیمه رو به بالا مثلِ Math_round`() {
    assertEquals(34.53, M.coarse(34.5251), 1e-9)
    assertEquals(69.18, M.coarse(69.1849), 1e-9)
    assertEquals(-12.34, M.coarse(-12.3449), 1e-9)
    //  Math.round(-0.5) در JS صفر است، نه -1
    assertEquals(0.0, M.coarse(-0.005), 1e-9)
    val c = M.coarseOf(34.555555, 69.123456)!!
    assertEquals(34.56, c.lat, 1e-9)
    assertEquals(69.12, c.lng, 1e-9)
    assertNull(M.coarseOf(Double.NaN, 69.0))
    assertNull(M.coarseOf(91.0, 69.0))
    assertNull(M.coarseOf(34.0, -180.5))
    assertNull(M.coarseOf(34.0, Double.POSITIVE_INFINITY))
  }

  /* ------------------------------ بدنه ------------------------------ */

  @Test fun `بدنهٔ PUT — فقط کلیدهای سرور؛ «دست نزن» موقعیت را نمی‌فرستد`() {
    val b = M.body(Form(listed = true, category = " bakery ", area = "  کارتهٔ سه\n"))
    assertEquals(setOf("listed", "category", "area"), b.keys)
    assertEquals("true", b["listed"].toString())
    assertEquals("bakery", b["category"]!!.jsonPrimitive.content)
    assertEquals("کارتهٔ سه", b["area"]!!.jsonPrimitive.content)
  }

  @Test fun `بدنهٔ PUT — برداشتنِ موقعیت null می‌فرستد و گذاشتنش همیشه درشت است`() {
    val clear = M.body(Form(false, "", "", LocationEdit.Clear))
    assertEquals(JsonNull, clear["location"])
    assertEquals("false", clear["listed"].toString())

    //  حتی اگر نقطهٔ دقیق به فرم برسد، دقیق از دستگاه بیرون نمی‌رود
    val set = M.body(Form(true, "other", "", LocationEdit.Set(MarketLocationDto(34.567891, 69.123456))))
    val loc = set["location"]!!.jsonObject
    assertEquals(setOf("lat", "lng"), loc.keys)
    assertEquals("34.57", loc["lat"]!!.jsonPrimitive.content)
    assertEquals("69.12", loc["lng"]!!.jsonPrimitive.content)
  }

  /* ------------------------------ حالت ------------------------------ */

  @Test fun `حالتِ بخش از خطای GET`() {
    assertEquals(Gate.MARKET_OFF, M.gateOf(404, "market_not_found"))
    assertEquals(Gate.MARKET_OFF, M.gateOf(404, "not_found"))
    assertEquals(Gate.STOREFRONT_OFF, M.gateOf(403, "storefront_disabled"))
    assertEquals(Gate.NO_PERMISSION, M.gateOf(403, "forbidden"))
    assertEquals(Gate.ERROR, M.gateOf(0, "offline"))
    assertEquals(Gate.ERROR, M.gateOf(500, "server_error"))
  }

  @Test fun `هشدار وقتی «در فهرست» هست ولی ویترین دیده نمی‌شود`() {
    assertNull(M.hiddenReason(listed = false, storefrontEnabled = false, storefrontExists = false))
    assertNull(M.hiddenReason(listed = true, storefrontEnabled = true, storefrontExists = true))
    assertNotNull(M.hiddenReason(listed = true, storefrontEnabled = false, storefrontExists = true))
    assertNotNull(M.hiddenReason(listed = true, storefrontEnabled = false, storefrontExists = false))
  }

  @Test fun `نمایشِ موقعیت با رقمِ فارسی`() {
    assertEquals("گذاشته نشده", M.locationText(null))
    assertEquals("حدودِ ۳۴.۵۲، ۶۹.۱۸", M.locationText(MarketLocationDto(34.52, 69.18)))
  }

  /* --------------------- مخزن، در برابرِ سرورِ واقعیِ HTTP --------------------- */

  private fun client(server: TestHttpServer): MarketListingRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = Long.MAX_VALUE
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return MarketListingRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  @Test fun `مخزن — GET و PUT روی همان مسیر، با همان بدنه`() {
    val server = TestHttpServer()
    try {
      var sent: TestHttpServer.Request? = null
      server.on("/api/v1/shop/market") { r ->
        if (r.method == "PUT") {
          sent = r
          200 to """{"listing":{"listed":true,"category":"bakery","area":"کارتهٔ سه","location":{"lat":34.52,"lng":69.18},"updatedAt":9,"categories":[]}}"""
        } else 200 to """{"directory":true,"listing":{"listed":false,"category":"","area":"","location":null,"updatedAt":null,"categories":[{"key":"bakery","label":"نانوایی"}]}}"""
      }
      val repo = client(server)

      val got = runBlocking { repo.load() }
      assertTrue(got is ApiResult.Success)
      assertTrue(got.valueOrNull()!!.directory)
      assertEquals(listOf("bakery"), M.categoriesOf(got.valueOrNull()!!.listing).map { it.key })

      val saved = runBlocking {
        repo.save(Form(true, "bakery", " کارتهٔ سه ", LocationEdit.Set(MarketLocationDto(34.5249, 69.1751))))
      }
      assertEquals("bakery", saved.valueOrNull()!!.category)
      val body = obj(sent!!.body)
      assertEquals("PUT", sent!!.method)
      assertEquals("t", sent!!.bearer)
      assertEquals("کارتهٔ سه", body["area"]!!.jsonPrimitive.content)
      assertEquals("34.52", body["location"]!!.jsonObject["lat"]!!.jsonPrimitive.content)
      assertEquals("69.18", body["location"]!!.jsonObject["lng"]!!.jsonPrimitive.content)
    } finally {
      server.close()
    }
  }

  @Test fun `مخزن — بازارچهٔ نبوده (۴۰۴) و ویترینِ خاموش (۴۰۳) به حالتِ درست می‌رسند`() {
    val server = TestHttpServer()
    try {
      server.on("/api/v1/shop/market") { 404 to """{"error":{"code":"market_not_found","message":"بازارچه هنوز باز نیست"}}""" }
      val off = runBlocking { client(server).load() } as ApiResult.Failure
      assertEquals(Gate.MARKET_OFF, M.gateOf(off.error.status, off.error.code))

      server.on("/api/v1/shop/market") { 403 to """{"error":{"code":"storefront_disabled","message":"ویترین برای این دکان روشن نیست"}}""" }
      val sf = runBlocking { client(server).load() } as ApiResult.Failure
      assertEquals(Gate.STOREFRONT_OFF, M.gateOf(sf.error.status, sf.error.code))
      assertEquals("ویترین برای این دکان روشن نیست", sf.error.userMessage)
    } finally {
      server.close()
    }
  }
}
