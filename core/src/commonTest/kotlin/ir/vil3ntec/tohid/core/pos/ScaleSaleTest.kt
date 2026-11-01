package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.hw.ScaleReading
import ir.vil3ntec.tohid.core.hw.WeightBarcode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 *  کالای وزنی در صندوق — POS-29، سناریوی S04: برچسبِ وزنی و قیمتیِ ترازو و وزنِ خوانده از ترازو.
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class ScaleSaleTest {

  private val rice = ScaleSale.Item("rice", "برنج", "کیلو", true, 12_000)
  private val saffron = ScaleSale.Item("saf", "زعفران", "گرم", true, 900)
  private val soap = ScaleSale.Item("soap", "صابون", "عدد", true, 5_000)
  private val nuts = ScaleSale.Item("nuts", "پسته", "کیلو", true, 300_000)
  private val free = ScaleSale.Item("free", "سبزی", "کیلو", true, 0)
  private val old = ScaleSale.Item("old", "کهنه", "کیلو", false, 10_000)
  private val items = listOf(rice, saffron, soap, nuts, free, old).associateBy { it.id }
  private val index = mapOf(
    "00150" to "rice", "2100777" to "saf", "00999" to "soap", "00321" to "nuts", "00400" to "free", "00500" to "old",
    "00778" to "saf",
    "2000000000008" to "plain",
  )
  private val weight = listOf(WeightBarcode.Rule(prefix = "21"), WeightBarcode.Rule(prefix = "20"))
  private val price = WeightBarcode.Rule(prefix = "22", kind = WeightBarcode.Kind.PRICE, decimals = 0)
  private val rules = weight + price

  private fun label(code: String) = ScaleSale.label(rules, code, index::get, items::get)
  private fun rejected(code: String): ScaleSale.Why = assertIs<ScaleSale.Label.Rejected>(label(code)).why

  @Test
  fun s04WeightLabelIsOneAndAQuarterKiloOfRice() {
    val m = assertIs<ScaleSale.Label.Weighed>(label("2100150012507"))
    assertEquals("rice", m.item.id)
    assertEquals(1.25, m.quantity, 0.0)
    assertEquals(1.25, m.kg, 0.0)
    assertNull(m.labelMinor)
  }

  @Test
  fun wrongCheckDigitIsRejectedNotUnknown() {
    val r = assertIs<ScaleSale.Label.Rejected>(label("2100150012508"))
    assertEquals(ScaleSale.Why.BAD_CHECK, r.why)
    assertEquals("2100150012508", r.arg)
  }

  @Test
  fun gramItemGetsExactGramsAndPrefixedPluIsFound() {
    val m = assertIs<ScaleSale.Label.Weighed>(label(WeightBarcode.build(weight[0], "00777", 25)))
    assertEquals("saf", m.item.id)
    assertEquals(25.0, m.quantity, 0.0)
    assertEquals(0.025, m.kg, 1e-12)
  }

  @Test
  fun offRegisteredOrOrdinaryBarcodeTakesTheUsualPath() {
    assertNull(ScaleSale.label(emptyList(), "2100150012507", index::get, items::get))
    assertNull(label("2000000000008"))
    assertNull(label("6260100120014"))
    assertNull(label("2300150012504"))
  }

  @Test
  fun weightLabelRejections() {
    assertEquals(ScaleSale.Why.NOT_WEIGHED, rejected(WeightBarcode.build(weight[0], "00999", 1000)))
    assertEquals(ScaleSale.Why.ZERO, rejected(WeightBarcode.build(weight[0], "00150", 0)))
    assertEquals(ScaleSale.Why.INACTIVE, rejected(WeightBarcode.build(weight[0], "00500", 500)))
    val unknown = assertIs<ScaleSale.Label.Rejected>(label(WeightBarcode.build(weight[0], "00888", 500)))
    assertEquals(ScaleSale.Why.UNKNOWN_CODE, unknown.why)
    assertEquals("00888", unknown.arg)
  }

  @Test
  fun priceLabelBecomesTheWeightThatChargesExactlyTheLabel() {
    //  ۱۵۰ افغانی برنجِ کیلویی ۱۲۰ ⇒ ۱٫۲۵۰ کیلو؛ ۱۲۰ × ۱٫۲۵ = ۱۵۰
    val m = assertIs<ScaleSale.Label.Weighed>(label(WeightBarcode.build(price, "00150", 150)))
    assertEquals(1.25, m.quantity, 0.0)
    assertEquals(15_000L, m.labelMinor)
    //  ۱۰۰ افغانی ⇒ ۰٫۸۳۳ کیلو (گرد به گرم)؛ ۱۲۰ × ۰٫۸۳۳ = ۹۹٫۹۶ ⇒ همان ۱۰۰
    assertEquals(0.833, assertIs<ScaleSale.Label.Weighed>(label(WeightBarcode.build(price, "00150", 100))).quantity, 0.0)
  }

  @Test
  fun priceLabelOnGramItemRoundsToWholeGrams() {
    //  زعفرانِ گرمی ۹ افغانی، برچسبِ ۲۲۵ ⇒ ۲۵ گرم
    val m = assertIs<ScaleSale.Label.Weighed>(label(WeightBarcode.build(price, "00778", 225)))
    assertEquals("saf", m.item.id)
    assertEquals(25.0, m.quantity, 0.0)
    assertEquals(0.025, m.kg, 1e-12)
    assertEquals(22_500L, m.labelMinor)
  }

  @Test
  fun priceLabelThatCannotBeChargedExactlyIsRejected() {
    //  پستهٔ کیلویی ۳۰۰۰: ۱۰۰۰ افغانی ⇒ ۰٫۳۳۳ کیلو ⇒ ۹۹۹ — مشتری چیزِ دیگری می‌پردازد ⇒ رد
    assertEquals(ScaleSale.Why.PRICE_MISMATCH, rejected(WeightBarcode.build(price, "00321", 1000)))
    //  ۱ افغانی پسته کمتر از یک گرم است
    assertEquals(ScaleSale.Why.PRICE_MISMATCH, rejected(WeightBarcode.build(price, "00321", 1)))
    //  ۱۲۵٫۵۰ افغانی: صندوق کسرِ افغانی نمی‌گیرد
    val cents = price.copy(decimals = 2)
    assertEquals(ScaleSale.Why.PRICE_MISMATCH, ScaleSale.label(listOf(cents), WeightBarcode.build(cents, "00150", 12550), index::get, items::get)
      .let { assertIs<ScaleSale.Label.Rejected>(it).why })
    //  ۱۲۶٫۰۰ با دو رقمِ اعشار همان ۱۲۶ افغانی است: ۱۲۰ × ۱٫۰۵ = ۱۲۶
    assertEquals(1.05, assertIs<ScaleSale.Label.Weighed>(
      ScaleSale.label(listOf(cents), WeightBarcode.build(cents, "00150", 12600), index::get, items::get)).quantity, 0.0)
  }

  @Test
  fun priceLabelRejections() {
    assertEquals(ScaleSale.Why.NO_PRICE, rejected(WeightBarcode.build(price, "00400", 50)))
    assertEquals(ScaleSale.Why.NOT_WEIGHED, rejected(WeightBarcode.build(price, "00999", 50)))
    assertEquals(ScaleSale.Why.ZERO, rejected(WeightBarcode.build(price, "00150", 0)))
    assertEquals(ScaleSale.Why.INACTIVE, rejected(WeightBarcode.build(price, "00500", 50)))
  }

  private fun reading(kg: Double?, motion: Boolean = false, over: Boolean = false, under: Boolean = false, error: Boolean = false) =
    ScaleReading(kg, "KG", motion, kg == 0.0, over, under, error)

  @Test
  fun stableScaleWeightBecomesLineQuantity() {
    assertEquals(ScaleSale.Weigh.Ok(1.25, 1.25), ScaleSale.fromScale(reading(1.2504), "کیلو"))
    assertEquals(ScaleSale.Weigh.Ok(0.751, 0.751), ScaleSale.fromScale(reading(0.7505), "کیلوگرم"))
    assertEquals(ScaleSale.Weigh.Ok(1250.0, 1.25), ScaleSale.fromScale(reading(1.2504), "گرم"))
  }

  @Test
  fun unusableScaleReadingNeverReachesTheCart() {
    assertEquals(ScaleSale.Weigh.Rejected(ScaleSale.ReadWhy.NOT_WEIGHED), ScaleSale.fromScale(reading(1.0), "عدد"))
    assertEquals(ScaleSale.Weigh.Rejected(ScaleSale.ReadWhy.MOTION), ScaleSale.fromScale(reading(1.0, motion = true), "کیلو"))
    assertEquals(ScaleSale.Weigh.Rejected(ScaleSale.ReadWhy.OVER), ScaleSale.fromScale(reading(1.0, over = true), "کیلو"))
    assertEquals(ScaleSale.Weigh.Rejected(ScaleSale.ReadWhy.UNDER), ScaleSale.fromScale(reading(-0.1, under = true), "کیلو"))
    assertEquals(ScaleSale.Weigh.Rejected(ScaleSale.ReadWhy.ERROR), ScaleSale.fromScale(reading(1.0, error = true), "کیلو"))
    assertEquals(ScaleSale.Weigh.Rejected(ScaleSale.ReadWhy.NO_WEIGHT), ScaleSale.fromScale(reading(0.0), "کیلو"))
    assertEquals(ScaleSale.Weigh.Rejected(ScaleSale.ReadWhy.NO_WEIGHT), ScaleSale.fromScale(reading(null), "کیلو"))
    assertEquals(ScaleSale.Weigh.Rejected(ScaleSale.ReadWhy.NO_WEIGHT), ScaleSale.fromScale(reading(0.0004), "کیلو"))
  }

  @Test
  fun configurableLengthsAlwaysMakeValidRulesAndReadTheirOwnSample() {
    for (kind in WeightBarcode.Kind.entries) for (n in ScaleSale.ITEM_DIGITS) for (dec in ScaleSale.decimalsFor(kind)) {
      val rs = ScaleSale.rules(kind, n, dec)
      assertEquals((20..29).map { it.toString() }, rs.map { it.prefix })
      assertEquals(true, rs.all { it.valid && it.itemDigits == n && it.valueDigits == 10 - n && it.decimals == dec && it.kind == kind })
      val code = ScaleSale.sample(rs)!!
      val hit = assertIs<WeightBarcode.Result.Hit>(WeightBarcode.parse(code, rs))
      assertEquals("150".padStart(n, '0'), hit.itemCode)
      if (kind == WeightBarcode.Kind.PRICE) assertEquals(true, hit.value == 150.0 || hit.value == 15.0, "$n/$dec: ${hit.value}")
      else assertEquals(1.25, hit.value, 1e-9)
    }
    //  S04 با پیش‌فرض همان برچسبِ سند است
    assertEquals("2100150012507", ScaleSale.sample(ScaleSale.rules(WeightBarcode.Kind.WEIGHT, 5, 3)))
  }

  @Test
  fun outOfRangeLengthsFallBackToDefaultsNotBrokenRules() {
    val rs = ScaleSale.rules(WeightBarcode.Kind.WEIGHT, 9, 7)
    assertEquals(true, rs.all { it.valid && it.itemDigits == 5 && it.decimals == 3 })
    assertEquals(true, ScaleSale.rules(WeightBarcode.Kind.PRICE, 3, 3).all { it.itemDigits == 5 && it.decimals == 0 })
    assertNull(ScaleSale.sample(emptyList()))
  }

  @Test
  fun sixDigitPluWithTenGramWeightFindsTheItem() {
    val rs = ScaleSale.rules(WeightBarcode.Kind.WEIGHT, 6, 2)
    //  کدِ ۰۰۰۱۵۰، ۱۲۵ ده‌گرم = ۱٫۲۵ کیلو؛ کالا با همان کدِ شش‌رقمی ثبت شده
    val idx = mapOf("000150" to "rice")
    val m = assertIs<ScaleSale.Label.Weighed>(ScaleSale.label(rs, WeightBarcode.build(rs[1], "150", 125), idx::get, items::get))
    assertEquals(1.25, m.quantity, 1e-9)
  }
}
