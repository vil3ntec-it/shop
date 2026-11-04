package ir.vil3ntec.tohid.core.cost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  RPT-03 و D-23 (گرفته‌شده ۱۴۰۵/۰۸/۱۳، OWNER-DECISIONS: الف، میانگینِ موزون): پیش‌فرض و مصوب همان میانگینِ موزون؛
 *  قیمتِ روی قلم انتخابیِ همین دستگاه و «تقریبی»؛ شناسه‌ها همان `costing` ِ هسته.
 */
class CostPolicyTest {

  @Test
  fun defaultAndDecidedAreTheMovingAverage() {
    assertEquals(CostMethod.MOVING_AVERAGE, CostPolicy.DEFAULT)
    assertEquals(CostMethod.MOVING_AVERAGE, CostPolicy.DECIDED)
    assertTrue(CostPolicy.approved(CostMethod.MOVING_AVERAGE))
    assertFalse(CostPolicy.approved(CostMethod.PURCHASE_PRICE))
  }

  @Test
  fun idsAreTheReportCoreCostingIds() {
    assertEquals(listOf("line", "mavg"), CostMethod.entries.map { it.id })
  }

  @Test
  fun storedValueFallsBackToTheDefault() {
    assertEquals(CostMethod.MOVING_AVERAGE, CostPolicy.parse("mavg"))
    assertEquals(CostMethod.MOVING_AVERAGE, CostPolicy.parse(" mavg "))
    assertEquals(CostMethod.PURCHASE_PRICE, CostPolicy.parse("line"))
    assertEquals(CostMethod.PURCHASE_PRICE, CostPolicy.parse(" line "))
    assertEquals(CostMethod.MOVING_AVERAGE, CostPolicy.parse(null))
    assertEquals(CostMethod.MOVING_AVERAGE, CostPolicy.parse(""))
    assertEquals(CostMethod.MOVING_AVERAGE, CostPolicy.parse("fifo"))
    assertEquals(CostMethod.MOVING_AVERAGE, CostPolicy.parse("LINE"))
  }

  @Test
  fun approvedOnlyForTheDecidedMethod() {
    assertTrue(CostPolicy.approved(CostMethod.MOVING_AVERAGE, decided = CostMethod.MOVING_AVERAGE))
    assertFalse(CostPolicy.approved(CostMethod.PURCHASE_PRICE, decided = CostMethod.MOVING_AVERAGE))
    assertFalse(CostPolicy.approved(CostMethod.MOVING_AVERAGE, decided = null))
  }

  @Test
  fun labelIsPlainForTheDecidedMethodAndApproximateForTheOther() {
    assertEquals("سود ناخالص", CostPolicy.label("سود ناخالص", CostMethod.MOVING_AVERAGE))
    assertEquals("سود ناخالص (تقریبی، قیمت خرید روی قلم)", CostPolicy.label("سود ناخالص", CostMethod.PURCHASE_PRICE))
    assertEquals("سود ناخالص (تقریبی)", CostPolicy.label("سود ناخالص", CostMethod.MOVING_AVERAGE, decided = null))
    //  روشِ مصوب دیگر «پیش‌نمایش» نیست
    CostMethod.entries.forEach { assertFalse(it.label.contains("پیش‌نمایش"), it.id) }
  }

  @Test
  fun explanationNamesTheDecidedMethodForTheOther() {
    assertEquals(CostMethod.MOVING_AVERAGE.note, CostPolicy.explain(CostMethod.MOVING_AVERAGE))
    val line = CostPolicy.explain(CostMethod.PURCHASE_PRICE)
    assertTrue(line.startsWith(CostMethod.PURCHASE_PRICE.note))
    assertTrue(line.contains("D-23") && line.contains(CostMethod.MOVING_AVERAGE.label) && line.contains("تقریبی"), line)
    assertTrue(CostPolicy.explain(CostMethod.MOVING_AVERAGE, decided = null).contains("هنوز تصمیم نشده"))
  }
}
