package ir.vil3ntec.tohid.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 *  LOC-08 — لایهٔ دری (`DariText`): فقط واژهٔ کامل و پسوندِ شناخته عوض می‌شود؛ هر چیزِ دیگر همان فارسی.
 */
class DariTextTest {

  private val zwnj = "‌"

  @Test
  fun wholeWordsAreReplaced() {
    assertEquals("مبایل", DariText.of("گوشی"))
    assertEquals("کمپیوتر و مبایل", DariText.of("کامپیوتر و گوشی"))
    assertEquals("گدام خالی است", DariText.of("انبار خالی است"))
    assertEquals("نرخ (فیصد)", DariText.of("نرخ (درصد)"))
    assertEquals("کمبود: 50", DariText.of("کسری: 50"))
    assertEquals("هیچ‌کس قرض‌دار نیست", DariText.of("هیچ‌کس بدهکار نیست"))
    assertEquals("«شاگردان» دکان", DariText.of("«کارمندان» فروشگاه"))
    assertEquals("برنامهٔ مبایل", DariText.of("برنامهٔ تلفنِ همراه"))
    assertEquals("سرک، ولایت، پوهنتون", DariText.of("خیابان، استان، دانشگاه"))
  }

  @Test
  fun aWordInsideAnotherWordIsNotTouched() {
    for (s in listOf("داستان", "درصدد", "خرگوشی", "انبارگردانی", "استانبول", "گوشه", "کارمندیار", "پیش${zwnj}انبار")) {
      assertEquals(s, DariText.of(s), s)
    }
  }

  @Test
  fun knownSuffixesFollowTheWord() {
    assertEquals("مبایلِ مشتری", DariText.of("گوشیِ مشتری"))
    assertEquals("مبایل${zwnj}ها", DariText.of("گوشی${zwnj}ها"))
    assertEquals("مبایلی دیگر", DariText.of("گوشی${zwnj}ای دیگر"))
    assertEquals("مبایلش", DariText.of("گوشی${zwnj}اش"))
    assertEquals("مصرفِ خودکار", DariText.of("هزینهٔ خودکار"))
    assertEquals("کرایهٔ ارسال: 20", DariText.of("هزینهٔ ارسال: 20"))
    assertEquals("مصارفِ ثبت‌شده", DariText.of("هزینه${zwnj}های ثبت‌شده"))
    assertEquals("همهٔ مصارف", DariText.of("همهٔ هزینه${zwnj}ها"))
    assertEquals("فیصدی", DariText.of("درصدی"))
    assertEquals("گدامِ پشتی", DariText.of("انبارِ پشتی"))
    assertEquals("گدام‌ها", DariText.of("انبار${zwnj}ها"))
    assertEquals("قرض‌داریم", DariText.of("بدهکاریم"))
    assertEquals("قرض‌داران", DariText.of("بدهکاران"))
    assertEquals("شاگردتان", DariText.of("کارمندتان"))
    assertEquals("کمبودِ شمارش", DariText.of("کسریِ شمارش"))
  }

  @Test
  fun unknownSuffixLeavesTheFarsiWord() {
    //  «ان» فقط برای نامِ کس («بدهکاران»)، نه «انباران»
    assertEquals("انباران", DariText.of("انباران"))
    assertEquals("هزینه${zwnj}هایش", DariText.of("هزینه${zwnj}هایش"))
  }

  @Test
  fun punctuationDigitsAndLatinAreBoundaries() {
    assertEquals("«گدام»: 3", DariText.of("«انبار»: 3"))
    assertEquals("۵ فیصد؟", DariText.of("۵ درصد؟"))
    assertEquals("۵فیصد", DariText.of("۵درصد"))
    assertEquals("(مبایل): A1", DariText.of("(گوشی): A1"))
    assertEquals("PC کمپیوتر", DariText.of("PC کامپیوتر"))
  }

  @Test
  fun keptNamesAndWholePhrases() {
    assertEquals("فروشگاه من — برنامهٔ مبایل", DariText.of("فروشگاه من — برنامهٔ گوشی"))
    assertEquals("مثلاً «دکان نور»", DariText.of("مثلاً «فروشگاه نور»"))
    assertEquals("تحویل و کرایه", DariText.of("تحویل و هزینه"))
    assertEquals("قرض‌دارانی که مانده دارند", DariText.of("قرض‌دارِ بدهکار"))
  }

  @Test
  fun textWithoutListedWordsIsReturnedAsIs() {
    for (s in listOf("ذخیره", "Save", "", "مانده: 1,250 افغانی", "خوندي کول", "شاگرد")) assertSame(s, DariText.of(s))
  }

  @Test
  fun everyDariFormIsStableAndListIsConservative() {
    for (w in DariText.WORDS) {
      assertEquals(w.dari, DariText.of(w.dari), "دریِ «${w.fa}» دوباره عوض می‌شود")
      assertTrue(w.dari.isNotBlank() && w.fa != w.dari)
    }
    for ((fa, dari) in DariText.PHRASES) assertTrue(dari.isNotBlank() && fa != dari)
    //  فهرست کوتاه می‌ماند؛ هر واژهٔ تازه با بازبینیِ صاحب‌کار
    assertTrue(DariText.WORDS.size <= 24)
  }

  private fun p(s: String) = DariText.protect(s)

  @Test
  fun userDataInsideAMessageIsNeverChanged() {
    val product = "گوشی سامسونگ انبار"
    assertEquals("مبایل «$product» به گدام رفت", DariText.of("گوشی «${p(product)}» به انبار رفت"))
    val customer = "فروشگاه کریم"
    assertEquals("$customer — 500 ؋ قرض‌داریم", DariText.of("${p(customer)} — ${p("500")} ؋ بدهکاریم"))
    //  همان دادهٔ بی نشانه در متنِ برنامه دری می‌شد؛ نشانه است که نگهش می‌دارد
    assertEquals("مبایل سامسونگ گدام", DariText.of(product))
    //  هیچ نشانه‌ای در خروجی نمی‌ماند
    val out = DariText.of("${p("گوشی")}${p("")} و ${p("انبار")}")
    assertEquals("گوشی و انبار", out)
    assertTrue(out.none { it == DariText.OPEN || it == DariText.CLOSE })
  }

  @Test
  fun aWordGluedToUserDataIsNotTouched() {
    //  «پیش‌» از داده + «انبار» ِ متن = یک واژه؛ «انبار» + «گردانی» ِ داده هم
    assertEquals("پیش‌انبار", DariText.of("${p("پیش‌")}انبار"))
    assertEquals("انبارگردانی", DariText.of("انبار${p("گردانی")}"))
    assertEquals("12 گدام", DariText.of("${p("12")} انبار"))
  }

  @Test
  fun stripRemovesOnlyTheMarkers() {
    assertSame("گوشی", DariText.strip("گوشی"))
    assertEquals("کالای گوشی", DariText.strip("کالای ${p("گوشی")}"))
  }
}
