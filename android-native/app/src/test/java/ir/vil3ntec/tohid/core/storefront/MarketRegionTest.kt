package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.net.ApiJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S109 — ولایت و ولسوالیِ دکان در «نمایش در بازارچه»: پاسخ و بدنه همان `listingView`/`saveListing` ِ سرور
 *  (`lib/af-provinces.js`). فرمِ بی ولایت (رابطِ پیشین، صفِ آفلاین) کلید را نمی‌فرستد تا ولایتِ ذخیره پاک نشود.
 */
class MarketRegionTest {

  private val state = """
    {"directory":true,"listing":{"listed":true,"category":"grocery","area":"کارتهٔ سه","province":"HER","provinceName":"هرات",
     "district":"انجیل","provinces":[{"code":"HER","name":"هرات"},{"code":"KAB","name":"کابل"},{"code":"","name":"بی‌کد"}]}}
  """.trimIndent()

  @Test fun `S109 پاسخِ سرور ⇒ فرم با ولایت و ولسوالی و فهرستِ ولایت‌ها`() {
    val s = MarketListing.parseState(Json.parseToJsonElement(state))
    val f = MarketListing.formOf(s.listing)
    assertEquals("HER", f.province)
    assertEquals("انجیل", f.district)
    val provinces = MarketListing.provincesOf(s.listing)
    assertEquals(listOf("HER", "KAB"), provinces.map { it.code })
    assertEquals("هرات", MarketListing.provinceLabel("HER", provinces))
    assertEquals("", MarketListing.provinceLabel(null, provinces))
  }

  @Test fun `S109 بدنه ولایت و ولسوالیِ یکدست را می‌فرستد و فرمِ بی ولایت کلید را نمی‌فرستد`() {
    val body = MarketListing.body(MarketListing.Form(true, "grocery", "کارته", province = "KAB", district = " ده‌سبز  "))
    assertEquals("KAB", body["province"]?.jsonPrimitive?.content)
    assertEquals("ده سبز", body["district"]?.jsonPrimitive?.content)
    val legacy = MarketListing.body(MarketListing.Form(true, "grocery", "کارته"))
    assertFalse(legacy.containsKey("province"))
    assertFalse(legacy.containsKey("district"))
  }

  @Test fun `S109 ولسوالیِ بلند پیش از فرستادن خطا دارد و سرورِ پیش از S109 بخشِ ولایت ندارد`() {
    val errors = MarketListing.errors(MarketListing.Form(true, "grocery", "", province = "HER", district = "ب".repeat(61)), MarketListing.CATEGORIES)
    assertNotNull(errors["district"])
    val old = MarketListing.parseState(Json.parseToJsonElement("""{"directory":true,"listing":{"listed":false}}"""))
    assertTrue(MarketListing.provincesOf(old.listing).isEmpty())
    assertEquals("", MarketListing.formOf(old.listing).province)
    //  همان رمزگشای برنامه (ApiJson) کلیدِ ناشناسِ آینده را نادیده می‌گیرد
    assertEquals("HER", ApiJson.decode<MarketListingDto>(Json.parseToJsonElement(state).jsonObject["listing"]!!).province)
  }
}
