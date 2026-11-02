package ir.vil3ntec.tohid.core.staff

import ir.vil3ntec.tohid.core.staff.StaffScope.SHOP_FLOOR
import ir.vil3ntec.tohid.core.staff.StaffScope.WAREHOUSE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** STF-06 — همان قاعدهٔ `server/test/staff-scope.test.js` */
class StaffScopeTest {

  private val all = listOf(SHOP_FLOOR, WAREHOUSE, "damaged", "in_transit")

  @Test
  fun emptyOrFullScopeMeansAllExactlyAsBefore() {
    assertEquals(emptyList(), StaffScope.normalize(emptyList()))
    assertEquals(emptyList(), StaffScope.normalize(listOf(WAREHOUSE, SHOP_FLOOR)))
    assertFalse(StaffScope.restricted(listOf(WAREHOUSE, SHOP_FLOOR, WAREHOUSE)))
    assertEquals(all, StaffScope.visible(all, emptyList()))
    assertEquals(all, StaffScope.visible(all, listOf(SHOP_FLOOR, WAREHOUSE)))
    assertTrue(StaffScope.allows(emptyList(), listOf("damaged")))
    assertTrue(StaffScope.allows(emptyList(), emptyList()))
  }

  @Test
  fun unknownPlacesAreDroppedAndOrderIsFixed() {
    assertEquals(listOf(WAREHOUSE), StaffScope.normalize(listOf("nowhere", WAREHOUSE, "damaged")))
    assertEquals(emptyList(), StaffScope.normalize(listOf("nowhere")))
  }

  @Test
  fun restrictedSeesAndWritesOnlyItsPlaces() {
    val mine = listOf(WAREHOUSE)
    assertTrue(StaffScope.restricted(mine))
    assertEquals(listOf(WAREHOUSE), StaffScope.visible(all, mine))
    assertTrue(StaffScope.allows(mine, listOf(WAREHOUSE)))
    assertTrue(StaffScope.allows(mine, listOf("in_transit", WAREHOUSE)))   //  دریافتِ «در مسیر» در انبار
    assertFalse(StaffScope.allows(mine, listOf(SHOP_FLOOR)))
    assertFalse(StaffScope.allows(mine, listOf(SHOP_FLOOR, WAREHOUSE)))    //  انتقالِ یک‌مرحله‌ای: هر دو سر
    assertFalse(StaffScope.allows(mine, listOf("damaged")))                 //  «خراب» مکان نیست
    assertFalse(StaffScope.allows(mine, emptyList()))
  }

  @Test
  fun ownerTogglesNeverLeaveAnEmptyScope() {
    assertTrue(StaffScope.isOn(emptyList(), SHOP_FLOOR))
    assertTrue(StaffScope.isOn(emptyList(), WAREHOUSE))
    assertEquals(listOf(WAREHOUSE), StaffScope.toggle(emptyList(), SHOP_FLOOR, on = false))
    assertFalse(StaffScope.isOn(listOf(WAREHOUSE), SHOP_FLOOR))
    assertNull(StaffScope.toggle(listOf(WAREHOUSE), WAREHOUSE, on = false))
    assertEquals(emptyList(), StaffScope.toggle(listOf(WAREHOUSE), SHOP_FLOOR, on = true))
    assertEquals(listOf(WAREHOUSE), StaffScope.toggle(listOf(WAREHOUSE), WAREHOUSE, on = true))
    assertNull(StaffScope.toggle(emptyList(), "damaged", on = false))
  }
}
