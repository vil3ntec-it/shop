package ir.vil3ntec.tohid.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PRD-08 — مقدارِ اعشاری روی JVM و آیفون (Kotlin/Native) با همان عددها؛ کلِ بردارِ `qty-exact-v1.json` را
 *  `jvmTest/…/QtyExactVectorsTest` می‌سنجد. این‌جا S04، نیمهٔ دقیق، دروازهٔ مقدارِ درست و عددِ نامعتبر.
 */
class QtyExactTest {

  @Test fun s04_scaleLabelAndManualWeight() {
    assertEquals(1250L, QtyExact.milli(1.25))
    assertEquals(37500L, QtyExact.lineMinor(300.0, 1.25, on = true))
    assertEquals(9000L, QtyExact.lineMinor(120.0, 0.75, on = true))
    assertEquals(QtyExact.lineMinor(300.0, 1.25, on = false), QtyExact.lineMinor(300.0, 1.25, on = true))
  }

  @Test fun exactHalf_roundsUpLikeTheServer() {
    //  ۱٫۰۱۵ × ۱۰۰ = ۱۰۱٫۵: Double ِ امروز ۱۰۱٫۴۹۹… ⇒ ۱۰۱؛ قاعدهٔ ۳.۴ (و سفارشِ آنلاین) ۱۰۲
    assertEquals(10100L, QtyExact.legacyMinor(100.0, 1.015))
    assertEquals(10200L, QtyExact.lineMinor(100.0, 1.015, on = true))
    assertEquals(10100L, QtyExact.lineMinor(100.0, 1.015, on = false))
    assertEquals(1500L, QtyExact.lineMinor(25.0, 0.58, on = true))
    assertEquals(Rounding.lineTotalMinor(1015, 10000, 100), QtyExact.lineMinorOrNull(100.0, 1.015))
  }

  @Test fun wholeQuantity_staysTheEngineOfToday() {
    //  ۵۰ × ۰٫۲۹ = ۱۴٫۵: قاعدهٔ دقیق ۱۵ می‌داد، ولی کالای بی اعشار بایت‌به‌بایت همان امروز است
    assertNull(QtyExact.lineMinorOrNull(0.29, 50.0))
    assertEquals(1400L, QtyExact.lineMinor(0.29, 50.0, on = true))
    assertEquals(1500L, QtyExact.exactMinor(29, 50_000))
    assertFalse(QtyExact.fractional(3000))
    assertTrue(QtyExact.fractional(-1250))
  }

  @Test fun badNumbers_fallBackWithoutThrowing() {
    assertNull(QtyExact.milli(Double.NaN))
    assertNull(QtyExact.milli(Double.POSITIVE_INFINITY))
    assertNull(QtyExact.milli(1e300))
    assertNull(QtyExact.lineMinorOrNull(Double.NaN, 1.25))
    assertNull(QtyExact.lineMinorOrNull(949062.66, 94906.266), "بیرون از بازهٔ دقیق ⇒ همان حسابِ پیشین")
    assertNull(QtyExact.exactMinor(94906266, 94906266))
    assertTrue(QtyExact.clean(Double.NaN).isNaN())
  }

  @Test fun cartSumsAndRoomAreExactToTheMilli() {
    assertEquals(0.3, QtyExact.plus(0.1 + 0.1, 0.1))
    assertEquals(0.3, QtyExact.clean(0.1 + 0.2))
    assertEquals(0.2, QtyExact.room(0.1 + 0.2, 0.1))
    assertEquals(0.0, QtyExact.room(1.0, 1.5))
    assertEquals(3.0, QtyExact.plus(2.0, 1.0))
  }

  @Test fun quantityText_atMostThreeDecimals() {
    assertFalse(QtyExact.tooPrecise("1.250"))
    assertFalse(QtyExact.tooPrecise("1.2500"))
    assertTrue(QtyExact.tooPrecise("1.2505"))
    assertTrue(QtyExact.tooPrecise("۱٫۲۳۴۵"))
    assertFalse(QtyExact.tooPrecise("12"))
    assertFalse(QtyExact.tooPrecise(""))
  }
}
