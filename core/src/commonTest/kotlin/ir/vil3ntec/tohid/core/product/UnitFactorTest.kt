package ir.vil3ntec.tohid.core.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  PRD-07 — ضریبِ واحدِ فروشِ دوم یک معنا دارد: هزارمِ گرد (`UnitSale.factorMilli` = `unit_factor_milli` ِ سرور = اندازهٔ
 *  برگهٔ ردیف)، تبدیل با عددِ صحیح (`toBaseQty`)، و ضریبِ تازه فقط اگر دقیقاً به هزارم نوشته شود (`exactMilli`).
 */
class UnitFactorTest {

  @Test
  fun factorMilli_isTheServersRoundedMilli() {
    assertEquals(1L, UnitSale.factorMilli(0.001))
    assertEquals(7L, UnitSale.factorMilli(0.007))
    assertEquals(500L, UnitSale.factorMilli(0.5))
    assertEquals(24_000L, UnitSale.factorMilli(24.0))
    assertEquals(1_000_000L, UnitSale.factorMilli(1000.0))
    assertEquals(42L, UnitSale.factorMilli(1.0 / 24))
    for (bad in listOf(0.0, -1.0, 0.0004, Double.NaN, Double.POSITIVE_INFINITY, 2e6)) assertNull(UnitSale.factorMilli(bad), "$bad")
  }

  @Test
  fun exactMilli_onlyFactorsWrittenToTheMilli() {
    assertEquals(1L, UnitSale.exactMilli(0.001))
    assertEquals(7L, UnitSale.exactMilli(0.007))
    assertEquals(42L, UnitSale.exactMilli(0.042))
    assertEquals(2_500L, UnitSale.exactMilli(2.5))
    assertEquals(12_000L, UnitSale.exactMilli(12.0))
    //  «۱ دانه = ۱/۲۴ کارتن» یا نیم‌گرمی از کیلو در دفترِ هزارمی دقیق نمی‌ماند
    for (bad in listOf(1.0 / 24, 0.0417, 0.0005, 1.0 / 3, 0.0, Double.NaN)) assertNull(UnitSale.exactMilli(bad), "$bad")
  }

  @Test
  fun toBaseQty_integerMilli_halfUp() {
    assertEquals(0.25, UnitSale.toBaseQty(250.0, 0.001))
    assertEquals(0.001, UnitSale.toBaseQty(1.0, 0.001))
    assertEquals(0.007, UnitSale.toBaseQty(1.0, 0.007))
    assertEquals(0.333, UnitSale.toBaseQty(333.0, 0.001))
    assertEquals(48.0, UnitSale.toBaseQty(2.0, 24.0))
    //  نیم‌گرم ⇒ ۰٫۰۰۰۵ کیلو ⇒ نیمه به بالا، همان `mulDivRound` ِ سرور
    assertEquals(0.001, UnitSale.toBaseQty(0.5, 0.001))
    //  نیمهٔ هزارم با ضریبِ دقیق: ضربِ ممیزِ شناور ۱۳٫۴۹۹… و ۳٫۴۹۹… می‌دید (۰٫۰۱۳ و ۰٫۰۰۳)؛ عددِ صحیح ۱۳٫۵ و ۳٫۵ ⇒ بالا
    assertEquals(0.014, UnitSale.toBaseQty(1.5, 0.009))
    assertEquals(0.004, UnitSale.toBaseQty(0.7, 0.005))
    //  ضریبِ کهنهٔ ناجور (۱/۲۴، ۱/۱۲): همان ضربِ پیشین، نزدیک‌ترین هزارم به همین فروش
    assertEquals(0.042, UnitSale.toBaseQty(1.0, 1.0 / 24))
    assertEquals(1.0, UnitSale.toBaseQty(24.0, 1.0 / 24))
    assertEquals(0.5, UnitSale.toBaseQty(6.0, 1.0 / 12))
    assertNull(UnitSale.toBaseQty(1.0, 0.0))
    assertNull(UnitSale.toBaseQty(Double.NaN, 0.001))
  }

  @Test
  fun tillUnitsUseTheSameMilli() {
    val units = UnitSale.units("کیلو", "گرم", 0.001, emptyList())
    assertEquals(listOf(1000L, 1L), units.map { it.sizeMilli })
    assertEquals(42L, UnitSale.units("کارتن", "دانه", 1.0 / 24, emptyList())[1].sizeMilli)
    //  ضریبِ ۱ یا نادرست ⇒ فقط پایه
    assertEquals(1, UnitSale.units("دانه", "عدد", 1.0, emptyList()).size)
    assertEquals(1, UnitSale.units("دانه", "عدد", Double.NaN, emptyList()).size)
  }
}
