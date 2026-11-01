package ir.vil3ntec.tohid.core.cost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RPT-03 و D-23: پیش‌فرض همان رفتارِ امروز، هیچ روشی مصوب نیست، شناسه‌ها همان `costing` ِ هسته */
class CostPolicyTest {

  @Test
  fun defaultIsTodaysPurchasePriceAndNothingIsDecided() {
    assertEquals(CostMethod.PURCHASE_PRICE, CostPolicy.DEFAULT)
    assertNull(CostPolicy.DECIDED)
    CostMethod.entries.forEach { assertFalse(CostPolicy.approved(it), it.id) }
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
    assertEquals(CostMethod.PURCHASE_PRICE, CostPolicy.parse(null))
    assertEquals(CostMethod.PURCHASE_PRICE, CostPolicy.parse(""))
    assertEquals(CostMethod.PURCHASE_PRICE, CostPolicy.parse("fifo"))
    assertEquals(CostMethod.PURCHASE_PRICE, CostPolicy.parse("MAVG"))
  }

  @Test
  fun approvedOnlyForTheDecidedMethod() {
    assertTrue(CostPolicy.approved(CostMethod.MOVING_AVERAGE, decided = CostMethod.MOVING_AVERAGE))
    assertFalse(CostPolicy.approved(CostMethod.PURCHASE_PRICE, decided = CostMethod.MOVING_AVERAGE))
    assertFalse(CostPolicy.approved(CostMethod.MOVING_AVERAGE, decided = null))
  }

  @Test
  fun labelSaysApproximateUntilDecidedAndNamesANonDefaultMethod() {
    assertEquals("سود ناخالص (تقریبی)", CostPolicy.label("سود ناخالص", CostMethod.PURCHASE_PRICE))
    assertEquals("سود ناخالص (تقریبی، میانگین موزون)", CostPolicy.label("سود ناخالص", CostMethod.MOVING_AVERAGE))
    assertEquals("سود ناخالص (میانگین موزون)", CostPolicy.label("سود ناخالص", CostMethod.MOVING_AVERAGE, decided = CostMethod.MOVING_AVERAGE))
    assertEquals("سود ناخالص", CostPolicy.label("سود ناخالص", CostMethod.PURCHASE_PRICE, decided = CostMethod.PURCHASE_PRICE))
  }

  @Test
  fun explanationNamesD23WhileUndecided() {
    assertTrue(CostPolicy.explain(CostMethod.PURCHASE_PRICE).contains("D-23"))
    assertTrue(CostPolicy.explain(CostMethod.MOVING_AVERAGE).startsWith(CostMethod.MOVING_AVERAGE.note))
    assertFalse(CostPolicy.explain(CostMethod.MOVING_AVERAGE, decided = CostMethod.MOVING_AVERAGE).contains("D-23"))
  }
}
