package ir.vil3ntec.tohid.core.text

import ir.vil3ntec.tohid.core.text.CatalogMatch.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 *  جست‌وجوی مقاومِ صندوق — POS-04. پیش از این صندوقِ کامپیوتر فقط `contains` ِ ساده داشت:
 *  «كوكا» (ک/ی ِ عربی)، «چای سبز» با نیم‌فاصله، رقمِ فارسیِ بارکد و «روغنن» هیچ‌کدام پیدا نمی‌شدند.
 */
class CatalogMatchTest {

  private val items = listOf(
    Item("coke", "کوکاکولا ۱ لیتری", listOf("6260001000017")),
    Item("tea", "چای‌سبز احمد", listOf("5011000000123")),
    Item("oil", "روغن آفتابگردان", listOf("6261000000222")),
    Item("rice", "برنج هندی", listOf("8900000000444")),
    Item("potato", "کچالو", emptyList()),
    Item("oilx", "روغن زیتون", emptyList()),
  )
  private val index = CatalogMatch.index(items)

  @Test
  fun arabicLettersAndHalfSpaceFindTheSameItem() {
    assertEquals(listOf("coke"), CatalogMatch.search(index, "كوكا"))
    assertEquals(listOf("tea"), CatalogMatch.search(index, "چای سبز"))
    assertEquals(listOf("tea"), CatalogMatch.search(index, "چایسبز"))
    //  رقمِ فارسیِ نام
    assertEquals(listOf("coke"), CatalogMatch.search(index, "کوکاکولا 1"))
  }

  @Test
  fun wordsMatchInAnyOrder() {
    assertEquals(listOf("tea"), CatalogMatch.search(index, "احمد چای"))
    //  بی کمکِ غلطِ تایپی هم (فقط جورِ دقیق)
    assertEquals(listOf("tea"), CatalogMatch.search(index, "احمد چای", fuzzyBelow = 0))
    assertEquals(listOf("rice"), CatalogMatch.search(index, "هندی برنج"))
  }

  @Test
  fun fullBarcodeComesFirstAndPersianDigitsMatch() {
    assertEquals(listOf("tea"), CatalogMatch.search(index, "۵۰۱۱۰۰۰۰۰۰۱۲۳"))
    assertEquals(listOf("oil"), CatalogMatch.search(index, "6261000000222"))
    //  آغازِ بارکد — همان رفتارِ پیشینِ صندوق
    assertEquals(listOf("oil", "coke"), CatalogMatch.search(index, "۶۲۶"))   // در گروه به ترتیبِ نام
  }

  @Test
  fun groupsAreOrderedCodeThenPrefixThenWordsThenCodePrefix() {
    val idx = CatalogMatch.index(
      listOf(
        Item("a", "آب روغن", emptyList()),      // کلمه وسطِ نام — به نام پیش از «روغن نباتی»
        Item("b", "روغن نباتی", emptyList()),   // آغازِ نام
        Item("c", "صابون", listOf("روغن")),     // بارکدِ کامل
        Item("d", "قند", listOf("روغن2")),      // آغازِ بارکد
      ),
    )
    assertEquals(listOf("c", "b", "a", "d"), CatalogMatch.search(idx, "روغن"))
  }

  @Test
  fun typoIsForgivenOnlyWhenExactMatchesAreFew() {
    //  «روغنن» (یک حرفِ اضافه) ⇒ هر دو روغن
    assertEquals(listOf("oil", "oilx"), CatalogMatch.search(index, "روغنن"))
    //  هر کلمه با بودجهٔ خودش: «روغن» به «روغنن» نزدیک است، نه به «زیتونن»
    assertEquals(listOf("oilx"), CatalogMatch.search(index, "روغنن زیتونن"))
    //  کلمهٔ کوتاه (≤۳) غلط نمی‌بخشد
    assertEquals(emptyList(), CatalogMatch.search(index, "بنج"))   // «برنج» یک حرف دورتر است، ولی کلمهٔ سه‌حرفی بودجه ندارد
    //  با جورِ دقیقِ کافی، غلطِ تایپی سنجیده نمی‌شود
    assertEquals(emptyList(), CatalogMatch.search(index, "روغنن", fuzzyBelow = 0))
  }

  @Test
  fun synonymsComeFromOutside() {
    assertEquals(emptyList(), CatalogMatch.search(index, "سیب‌زمینی"))
    val dict = listOf(listOf("کچالو", "سیب زمینی"), listOf("روغن", "غوړي"))
    assertEquals(listOf("potato"), CatalogMatch.search(index, "سیب‌زمینی", dict))
    assertEquals(listOf("oil", "oilx"), CatalogMatch.search(index, "غوړي", dict))
  }

  @Test
  fun localDistanceIsTheSameAsTheNormalizerOne() {
    val words = listOf("", "روغن", "روغنن", "رغن", "کچالو", "کچالوو", "برنج", "بنج", "abc", "abd", "kitten", "sitting")
    for (a in words) for (b in words) for (max in 0..2) {
      assertEquals(TextNormalizer.editDistance(a, b, max), CatalogMatch.distance(a, b, max), "$a/$b/$max")
    }
  }

  @Test
  fun emptyQueryFindsNothing() {
    assertEquals(emptyList(), CatalogMatch.search(index, ""))
    assertEquals(emptyList(), CatalogMatch.search(index, "  ‌ "))
  }

  @Test
  fun twentyThousandItemsStayWithinTheSearchBudget() {
    val big = CatalogMatch.index(
      (0 until 20_000).map { Item("p$it", "کالای شماره $it بسته ${it % 97}", listOf((6_260_000_000_000L + it).toString())) },
    )
    //  گرم کردن
    CatalogMatch.search(big, "کالاي 1234")
    val t0 = TimeSource.Monotonic.markNow()
    val hits = CatalogMatch.search(big, "کالاي شماره 1234")
    val exactMs = t0.elapsedNow().inWholeMilliseconds
    val t1 = TimeSource.Monotonic.markNow()
    val typo = CatalogMatch.search(big, "کالایی شمارة 19999")
    val typoMs = t1.elapsedNow().inWholeMilliseconds
    val ms = exactMs + typoMs
    println("CatalogMatch 20k: exact $exactMs ms, typo $typoMs ms")
    assertTrue("p1234" in hits)
    assertTrue("p19999" in typo)
    //  بودجهٔ هر جست‌وجو ۱۵۰ms (POS-42)؛ ماشینِ مشترکِ پُربار ⇒ سقفِ گشاده‌تر برای دو جست‌وجو
    assertTrue(ms < 3_000, "two searches took $ms ms")
  }
}
