package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.hw.Ean13
import ir.vil3ntec.tohid.core.hw.WeightBarcode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** PRD-05: بارکدِ داخلی فقط ۲۰/۲۹، EAN-13 ِ درست، هرگز هم‌پیشوندِ قاعدهٔ ترازو، و یکتا میانِ کالاها. */
class InternalBarcodeTest {

  private fun ok(r: InternalBarcode.Result): String = assertIs<InternalBarcode.Result.Ok>(r).code

  @Test fun codesAreValidEan13WithOnlyTheTwoInternalPrefixes() {
    val seen = HashSet<String>()
    for (i in 0 until 500) {
      val code = ok(InternalBarcode.generate("01JPRODUCT$i", { false }))
      assertEquals(13, code.length)
      assertTrue(code.startsWith("20") || code.startsWith("29"), code)
      assertTrue(Ean13.isValid(code), code)
      assertTrue(InternalBarcode.isInternal(code))
      seen += code
    }
    //  ۵۰۰ کالا ⇒ ۵۰۰ کدِ جدا، و هر دو پیشوند به کار رفته
    assertEquals(500, seen.size)
    assertTrue(seen.any { it.startsWith("20") } && seen.any { it.startsWith("29") })
  }

  @Test fun sameProductGetsTheSameCodeOnEveryDevice() {
    val a = ok(InternalBarcode.generate("01JABC", { false }))
    assertEquals(a, ok(InternalBarcode.generate("01JABC", { false })))
    assertNotEquals(a, ok(InternalBarcode.generate("01JABD", { false })))
  }

  @Test fun aTakenCodeIsSkipped() {
    val first = ok(InternalBarcode.generate("01JABC", { false }))
    val second = ok(InternalBarcode.generate("01JABC", { it == first }))
    assertNotEquals(first, second)
    assertTrue(Ean13.isValid(second))
    assertIs<InternalBarcode.Result.Exhausted>(InternalBarcode.generate("01JABC", { true }, maxTries = 5))
  }

  @Test fun scaleRulesBlockTheirPrefixAndNothingIsMadeWhenBothAreTaken() {
    val w20 = WeightBarcode.Rule(prefix = "20")
    val w29 = WeightBarcode.Rule(prefix = "29")
    assertEquals(listOf("29"), InternalBarcode.freePrefixes(listOf(w20)))
    assertEquals(listOf("20"), InternalBarcode.freePrefixes(listOf(w29, WeightBarcode.Rule(prefix = "21"))))
    //  قاعدهٔ سه‌رقمیِ «201» هم ۲۰ را می‌بندد؛ قاعدهٔ یک‌رقمیِ «2» هر دو را
    assertEquals(listOf("29"), InternalBarcode.freePrefixes(listOf(WeightBarcode.Rule(prefix = "201", itemDigits = 4))))
    assertEquals(emptyList(), InternalBarcode.freePrefixes(listOf(WeightBarcode.Rule(prefix = "2", itemDigits = 6))))
    //  قاعدهٔ نامعتبر (ترازو آن را نمی‌خواند) چیزی را نمی‌بندد
    assertEquals(InternalBarcode.PREFIXES, InternalBarcode.freePrefixes(listOf(WeightBarcode.Rule(prefix = "20", itemDigits = 9))))
    //  قاعده‌های پیش‌فرضِ ترازو (۲۰ تا ۲۹) ⇒ هیچ
    assertIs<InternalBarcode.Result.NoFreePrefix>(InternalBarcode.generate("01JABC", { false }, WeightBarcode.standardRules()))

    for (i in 0 until 200) {
      val code = ok(InternalBarcode.generate("p$i", { false }, listOf(w20)))
      assertTrue(code.startsWith("29"), code)
      //  و صندوق آن را «وزنی» نمی‌خواند
      assertEquals(WeightBarcode.Result.NotScale, WeightBarcode.parse(code, listOf(w20)))
    }
  }

  @Test fun isInternalRejectsOtherShapes() {
    val code = ok(InternalBarcode.generate("01JABC", { false }))
    val wrongCheck = code.dropLast(1) + ((code.last() - '0' + 1) % 10)
    assertFalse(InternalBarcode.isInternal(wrongCheck))
    assertFalse(InternalBarcode.isInternal("6260000000017"))
    assertFalse(InternalBarcode.isInternal(code.drop(1)))
    assertFalse(InternalBarcode.isInternal("2" + "1" + code.drop(2).dropLast(1) + "0"))
  }
}
