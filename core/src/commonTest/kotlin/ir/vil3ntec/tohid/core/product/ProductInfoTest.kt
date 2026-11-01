package ir.vil3ntec.tohid.core.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  PRD-02 (نامِ پشتو و انگلیسی، مترادف، واژهٔ جست‌وجو) و PRD-26 (محلِ قفسه) — شکلِ تمیزِ کادرها و
 *  جورِ جست‌وجو. پیش از این کالا فقط یک `name` داشت: «potato» یا «کچالو» کالای «سیب‌زمینی» را پیدا
 *  نمی‌کرد و جای کالا در دکان هیچ‌جا ثبت نمی‌شد.
 */
class ProductInfoTest {

  @Test
  fun termsSplitOnCommasAndNewlinesButKeepTwoWordTerms() {
    assertEquals(
      listOf("کچالو", "سیب زمینی", "potato", "آلو"),
      ProductTerms.parse(" کچالو، سیب   زمینی ; potato\nآلو ،، "),
    )
    assertEquals(emptyList(), ProductTerms.parse(""))
    assertEquals(emptyList(), ProductTerms.parse(" ، ; \n "))
  }

  @Test
  fun duplicateTermsAreKeptOnceByCompareKey() {
    //  «ي/ی» ِ عربی، نیم‌فاصله و بزرگیِ لاتین همان واژه‌اند — نخستین می‌ماند، متنش دست نمی‌خورد
    assertEquals(listOf("چای‌سبز", "Coke"), ProductTerms.parse("چای‌سبز، چاي سبز، Coke، coke، COKE"))
  }

  @Test
  fun termsAreCappedInCountAndLength() {
    val many = (1..30).joinToString("، ") { "w$it" }
    val got = ProductTerms.parse(many)
    assertEquals(ProductTerms.MAX_TERMS, got.size)
    assertEquals("w1", got.first())
    assertEquals(ProductTerms.MAX_TERM, ProductTerms.parse("x".repeat(200)).single().length)
  }

  @Test
  fun formatRoundTripsWithParse() {
    val terms = ProductTerms.parse("کچالو، سیب زمینی، potato")
    assertEquals("کچالو، سیب زمینی، potato", ProductTerms.format(terms))
    assertEquals(terms, ProductTerms.parse(ProductTerms.format(terms)))
  }

  @Test
  fun aliasesAndKeywordsMatchWithNormalization() {
    val info = ProductInfo.of("کچالو", "Potato", "آلو، alu", "سبزی; تازه", null, null, null)
    assertEquals(listOf("کچالو", "Potato", "آلو", "alu"), info.aliases)
    assertTrue(info.matches("potato"))
    assertTrue(info.matches("POTA"))
    assertTrue(info.matches("كچالو"))           // ک ِ عربی
    assertTrue(info.matches("تازه"))
    assertTrue(info.matches("alu"))
    assertFalse(info.matches("برنج"))
    //  پرس‌وجوی خالی چیزی را «جور» نمی‌کند — جورِ «همه» کارِ صدازننده است
    assertFalse(info.matches(""))
    assertFalse(info.matches("  "))
    //  تکهٔ پایانِ یک واژه و آغازِ واژهٔ بعد جورِ ساختگی نمی‌سازد
    assertFalse(ProductInfo(keywords = listOf("سبز", "یتون")).matches("سبزیتون"))
  }

  @Test
  fun emptyFormIsEmptyInfo() {
    val info = ProductInfo.of("", " ", "، ،", "", "  ", null, "")
    assertTrue(info.isEmpty)
    assertEquals(ProductInfo(), info)
    assertTrue(ProductInfo().aliases.isEmpty())
  }

  @Test
  fun shelfLocationKeepsFilledPartsInOrder() {
    val loc = ShelfLocation.of(" ۳ ", "B", "")
    assertFalse(loc.isEmpty)
    assertEquals(
      listOf(ShelfLocation.Part.AISLE to "۳", ShelfLocation.Part.SHELF to "B"),
      loc.parts(),
    )
    assertEquals(listOf(ShelfLocation.Part.LEVEL to "بالا"), ShelfLocation.of(null, null, "بالا").parts())
    assertTrue(ShelfLocation.of(null, " ", "‌").isEmpty)
    assertEquals(ShelfLocation.MAX, ShelfLocation.of("x".repeat(40), null, null).aisle.length)
    assertEquals("راهرو دوم", ShelfLocation.of("  راهرو   دوم ", null, null).aisle)
  }

  @Test
  fun namesAreTrimmedAndCapped() {
    assertEquals("Coca Cola 1L", ProductTerms.cleanName("  Coca   Cola 1L "))
    assertEquals(ProductTerms.MAX_NAME, ProductTerms.cleanName("y".repeat(500)).length)
  }
}
