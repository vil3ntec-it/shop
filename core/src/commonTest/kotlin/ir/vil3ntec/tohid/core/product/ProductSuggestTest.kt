package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.ProductSuggest.Dropped
import ir.vil3ntec.tohid.core.product.ProductSuggest.Known
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AI-06: پیشنهادِ دسته از نام و نام از متن — فقط قاعده و دفترِ همین دکان؛ هیچ قیمت، بارکد، وزن یا برندی ساخته نمی‌شود. */
class ProductSuggestTest {

  private val shop = listOf(
    Known("شیر پاستوریزه ۱ لیتر", "لبنیات"),
    Known("شیر کم‌چرب ۱ لیتر", "لبنیات"),
    Known("ماست موسیر", "لبنیات"),
    Known("پنیر سفید", "لبنیات"),
    Known("شیر کاکائو ۲۰۰ میلی", "نوشیدنی"),
    Known("آب معدنی ۱.۵ لیتر", "نوشیدنی"),
    Known("نوشابه کوکا ۱ لیتر", "نوشیدنی"),
    Known("برنج سیلا ۵ کیلو", "برنج و حبوبات"),
    Known("برنج دودی", "برنج و حبوبات"),
    Known("نخود", "برنج و حبوبات"),
    Known("صابون گل", "شوینده"),
    Known("کالای بی‌دسته", ""),
  )
  private val index = ProductSuggest.Index(shop, categories = listOf("لبنیات", "نوشیدنی", "برنج و حبوبات", "شوینده", "روغن"))

  @Test fun categoryFromSharedWordsWithReason() {
    val picks = index.categories("برنج هندی ۱۰ کیلو")
    assertEquals(listOf("برنج و حبوبات"), picks.map { it.category })
    //  «برنج» هم در دو کالا و هم در نامِ خودِ دسته ⇐ دلیل: نامِ دسته
    assertEquals("برنج", picks[0].word)
    assertEquals(2, picks[0].count)
    assertTrue(picks[0].byName)
  }

  @Test fun commonWordIsWeighedByHowManyCategoriesHaveIt() {
    //  «شیر» در ۲ کالای لبنیات و ۱ نوشیدنی ⇒ لبنیات پیش؛ «کاکائو» فقط نوشیدنی ⇒ برابر و شمارِ کالا داور
    assertEquals(listOf("لبنیات", "نوشیدنی"), index.categories("شیر محلی").map { it.category })
    val choco = index.categories("شیر کاکائو بزرگ")
    assertEquals(setOf("لبنیات", "نوشیدنی"), choco.map { it.category }.toSet())
    assertEquals("نوشیدنی", choco.first().category) // ۱/۲ + ۱ > ۲/۲
    assertEquals(choco, index.categories("شیر کاکائو بزرگ")) // پایدار
  }

  @Test fun onlyExistingCategoriesAndNeverANewOne() {
    assertTrue(index.categories("روغن آفتابگردان").all { it.category == "روغن" }) // دستهٔ بی کالا فقط با نامِ خودش
    assertEquals(listOf("روغن"), index.categories("روغن آفتابگردان").map { it.category })
    assertTrue(index.categories("چیزی که نیست").isEmpty())
    assertTrue(index.categories("").isEmpty())
    //  کالای بی‌دسته در نمایه نیست
    assertTrue(index.categories("کالای بی‌دسته").none { it.category.isBlank() })
  }

  @Test fun digitsUnitsAndJoinersDoNotDriveTheCategory() {
    //  «لیتر» در هر دو دسته است، «۱» عدد: هیچ‌کدام نشانه نیست
    assertTrue(index.categories("۱ لیتر").isEmpty())
    assertTrue(index.categories("2 kg و با").isEmpty())
    assertEquals(listOf("پنیر", "سفید"), ProductSuggest.words("پنیر  سفید ۵۰۰ گرم"))
  }

  @Test fun normalisedLettersAndDigitsStillMatch() {
    //  «ي/ك» ِ عربی و نیم‌فاصله همان کلیدِ جست‌وجو
    assertEquals("لبنیات", index.categories("پنير خامه‌اي").first().category)
    assertEquals("شوینده", index.categories("صابون مايع").first().category)
    assertEquals("نوشیدنی", index.categories("كوكا زيرو").first().category)
  }

  @Test fun atMostThreePicksAndALimitOfZeroIsEmpty() {
    val many = ProductSuggest.Index((1..6).map { Known("چای نوع$it", "دسته$it") })
    assertEquals(ProductSuggest.MAX_PICKS, many.categories("چای").size)
    assertEquals(listOf("دسته1", "دسته2", "دسته3"), many.categories("چای").map { it.category }) // برابر ⇒ نامِ دسته
    assertTrue(many.categories("چای", limit = 0).isEmpty())
    assertTrue(ProductSuggest.Index(emptyList()).isEmpty)
  }

  // ── نام از متن ──────────────────────────────────────────────

  @Test fun priceAndBarcodeAreRemovedAndNeverReturnedAsValues() {
    val p = ProductSuggest.paste(
      """
      روغن آفتابگردان ۵ لیتر 650 افغانی
      شیر کاله ۱ لیتر، قیمت: ۸۵ ؋ 6260012345678
      ؋۱۲۰ نوشابه کوکا
      برنج سیلا ۵ کیلو $12
      چای سبز قیمت۲۰۰
      """.trimIndent(),
    )
    assertEquals(listOf("روغن آفتابگردان ۵ لیتر", "شیر کاله ۱ لیتر", "نوشابه کوکا", "برنج سیلا ۵ کیلو", "چای سبز"), p.lines.map { it.name })
    assertEquals(setOf(Dropped.PRICE), p.lines[0].dropped)
    assertEquals(setOf(Dropped.PRICE, Dropped.BARCODE), p.lines[1].dropped)
    //  هیچ رقمِ قیمت یا بارکد در نام نمانده
    assertTrue(p.lines.none { l -> listOf("650", "۸۵", "6260012345678", "۱۲۰", "12", "۲۰۰").any { it in l.name } })
  }

  @Test fun barcodeIsEightToFourteenDigitsInAnyScript() {
    //  EAN-8 و ITF-14 در مرز؛ ۷ رقم و ۱۵ رقم بارکد نیست و (بی واحدِ پول) دست نمی‌خورد
    val p = ProductSuggest.paste("کره 96385074\nکنسرو ۱۲۳۴۵۶۷۸۹۰۱۲۳۴\nشیر 1234567\nکارت 123456789012345")
    assertEquals(listOf("کره", "کنسرو", "شیر 1234567", "کارت 123456789012345"), p.lines.map { it.name })
    assertEquals(listOf(setOf(Dropped.BARCODE), setOf(Dropped.BARCODE), emptySet(), emptySet()), p.lines.map { it.dropped })
  }

  @Test fun quantityAndBrandOfTheTextStayAsTyped() {
    //  مقدار و برندِ خودِ متن ساختگی نیست؛ «افغانی» ِ بی عدد صفت است، نه قیمت
    val p = ProductSuggest.paste("چای افغانی ۵۰۰ گرم\nروغن فامیلا 1,5 لیتر")
    assertEquals(listOf("چای افغانی ۵۰۰ گرم", "روغن فامیلا 1,5 لیتر"), p.lines.map { it.name })
    assertTrue(p.lines.all { it.dropped.isEmpty() })
  }

  @Test fun onlyTheFirstSegmentOfEachLineIsTheName() {
    val p = ProductSuggest.paste("برنج سیلا ۵ کیلو، دانه دراز و خوشبو\nصابون گل - ۱۲ عدد\nماست | چکیده\nپنیر; سفید")
    assertEquals(listOf("برنج سیلا ۵ کیلو", "صابون گل", "ماست", "پنیر"), p.lines.map { it.name })
    //  جداکننده با برداشتنِ قیمت گم نمی‌شود
    assertEquals("روغن ۵ لیتر", ProductSuggest.paste("روغن ۵ لیتر 650 افغانی، مناسبِ سرخ کردن").lines.single().name)
    assertEquals("شیر", ProductSuggest.paste("شیر (650 افغانی)").lines.single().name)
  }

  @Test fun listBulletsAreStrippedButLeadingQuantitiesAreNot() {
    val p = ProductSuggest.paste("۱. شیر\n2) ماست\n- پنیر\n• کره\n12.5 کیلو آرد")
    assertEquals(listOf("شیر", "ماست", "پنیر", "کره", "12.5 کیلو آرد"), p.lines.map { it.name })
  }

  @Test fun duplicatesExistingNamesAndEmptyLines() {
    val p = ProductSuggest.paste("ماست موسیر\n\n  \nچای\nچاي \n650 افغانی\n6260012345678", existing = listOf("ماست  موسير"))
    assertEquals(listOf("ماست موسیر", "چای"), p.lines.map { it.name })
    assertTrue(p.lines[0].exists) // همان کلیدِ «یکسان» ِ موتور
    assertFalse(p.lines[1].exists)
    assertEquals(listOf("چای"), p.fresh.map { it.name })
    assertEquals(2, p.skipped) // فقط قیمت، فقط بارکد
    assertFalse(p.truncated)
  }

  @Test fun pashtoLettersAreKeptAndLongInputIsBounded() {
    assertEquals("ډوډۍ ښه", ProductSuggest.paste("ډوډۍ ښه").lines.single().name)
    assertEquals("چاي", ProductSuggest.paste("چاي").lines.single().name) // حرف عوض نمی‌شود
    val long = ProductSuggest.paste("ب".repeat(500))
    assertEquals(ProductSuggest.MAX_NAME, long.lines.single().name.length)
    val many = ProductSuggest.paste((1..150).joinToString("\n") { "کالا$it" })
    assertEquals(ProductSuggest.MAX_LINES, many.lines.size)
    assertTrue(many.truncated)
  }
}
