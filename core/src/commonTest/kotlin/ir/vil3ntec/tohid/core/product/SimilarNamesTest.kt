package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.SimilarNames.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PRD-19: «نامِ مشابه» هنگامِ ثبت و واردسازی — فقط هشدار؛ نامِ عیناً یکسان کارِ `duplicateName` است. */
class SimilarNamesTest {

  @Test fun sameLettersWithDifferentSpacingOrDiacritics() {
    assertEquals(Reason.SAME_LETTERS, SimilarNames.reason("رب گوجه", "رب‌گوجه"))
    assertEquals(Reason.SAME_LETTERS, SimilarNames.reason("شیرِ پاکتی", "شیر پاکتی"))
    assertEquals(Reason.SAME_LETTERS, SimilarNames.reason("آب معدنی", "اب معدنی"))
  }

  @Test fun exactlyTheSameNameIsNotASimilarityWarning() {
    //  «ي» ِ عربی و فاصلهٔ اضافه همان کلیدِ «یکسان» ِ موتور است ⇒ موتور خودش رد می‌کند
    assertNull(SimilarNames.reason("چای سبز", "چاي  سبز "))
    assertNull(SimilarNames.reason("Tea", "tea"))
  }

  @Test fun wordOrder() {
    assertEquals(Reason.WORD_ORDER, SimilarNames.reason("چای سبز احمد", "احمد چای سبز"))
  }

  @Test fun smallTypoWithinTheSearchBudget() {
    assertEquals(Reason.SPELLING, SimilarNames.reason("شامپو گل", "شامپو گلل"))
    assertEquals(Reason.SPELLING, SimilarNames.reason("بیسکویت کره‌ای", "بیسکوییت کره‌ای"))
    //  کوتاه (≤۳ نویسه) هیچ غلطی بخشیده نمی‌شود
    assertNull(SimilarNames.reason("نان", "نام"))
    assertNull(SimilarNames.reason("بیسکویت", "ماکارونی"))
  }

  @Test fun differentNumbersAreDifferentProductsNeverATypo() {
    assertNull(SimilarNames.reason("روغن ۱ لیتری", "روغن ۵ لیتری"))
    assertNull(SimilarNames.reason("روغن 1 لیتری", "لیتری روغن 5"))
    //  ولی رقمِ فارسی و لاتینِ یکسان همان است
    assertEquals(Reason.SAME_LETTERS, SimilarNames.reason("روغن ۱ لیتری", "روغن 1لیتری"))
  }

  @Test fun indexFindsStrongestFirstSkipsSelfAndExactDuplicates() {
    val names = listOf("چای سبز", "سبز چای", "چای‌سبز", "چای سبزز", "قند", "چای سبز")
    val idx = SimilarNames.Index(names)
    val m = idx.find("چای سبز", except = 0, limit = 5)
    assertEquals(listOf(2 to Reason.SAME_LETTERS, 1 to Reason.WORD_ORDER, 3 to Reason.SPELLING), m.map { it.index to it.reason })
    //  ردیفِ ۵ عیناً همان نام است ⇒ هشدارِ «مشابه» نیست (رد شدنش کارِ موتور است)
    assertTrue(m.none { it.index == 5 })
    assertEquals(1, idx.find("چای سبز", except = 0, limit = 1).size)
    assertEquals(emptyList(), idx.find("   "))
  }

  @Test fun indexOverALargeCatalogFindsOnlyTheRealNeighbour() {
    val names = (0 until 20_000).map { "کالای شماره $it" } + "پودر لباس‌شویی سیمرغ"
    val idx = SimilarNames.Index(names)
    val m = idx.find("پودر لباسشویی سیمرغ")
    assertEquals(listOf(20_000 to Reason.SAME_LETTERS), m.map { it.index to it.reason })
    //  رقمِ متفاوت ⇒ هیچ‌کدام از بیست هزار «کالای شماره N» مشابهِ دیگری نیست
    assertEquals(emptyList(), idx.find("کالای شماره 123456"))
  }
}
