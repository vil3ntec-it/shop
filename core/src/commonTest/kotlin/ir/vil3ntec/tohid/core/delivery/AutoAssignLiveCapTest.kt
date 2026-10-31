package ir.vil3ntec.tohid.core.delivery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  سپردنِ خودکار با سقفِ کارِ هم‌زمانِ هر پیک (DLV-03، سرور: `max_live` ⇒ ۴۰۹ `courier_busy`).
 *  سقفِ مؤثر = کمترینِ سقفِ همین نوبت و سقفِ خودِ پیک؛ هر دو تهی یعنی بی‌سقف (رفتارِ پیشین).
 */
class AutoAssignLiveCapTest {

  private fun o(no: Long) = AutoAssign.Candidate("O$no", no, expectedCollectMinor = 0, createdAt = no)

  private fun c(id: String, live: Int = 0, cap: Int? = null) =
    CourierRow(id = id, name = id, active = true, cashCapMinor = 1_000_000, liveCount = live, maxLive = cap)

  @Test
  fun effectiveCapIsTheSmallerOfRunAndCourier() {
    assertNull(AutoAssign.liveCapOf(c("A"), null))
    assertEquals(3, AutoAssign.liveCapOf(c("A", cap = 3), null))
    assertEquals(2, AutoAssign.liveCapOf(c("A", cap = 3), 2))
    assertEquals(1, AutoAssign.liveCapOf(c("A", cap = 1), 4))
  }

  @Test
  fun courierAtOwnCapGetsNothingAndTheRestGoToOthers() {
    val p = AutoAssign.plan(listOf(o(1), o(2), o(3)), listOf(c("A", live = 1, cap = 1), c("B", cap = 2)))
    assertEquals(listOf("B", "B"), p.picks.map { it.courierId })
    assertEquals(listOf("O3"), p.unplaced.map { it.orderId })
    assertEquals("همهٔ پیک‌ها به سقفِ کارِ هم‌زمانِ خودشان رسیده‌اند", p.unplaced.single().reason)
    assertEquals(setOf("A", "B"), p.blocked.map { it.courierId }.toSet())
    assertTrue(p.blocked.first { it.courierId == "A" }.reason.contains("سقفِ کارِ هم‌زمان: 1"))
  }

  @Test
  fun withoutAnyCapNothingChanges() {
    val p = AutoAssign.plan(listOf(o(1), o(2), o(3)), listOf(c("A", live = 5), c("B", live = 6)))
    assertEquals(3, p.picks.size)
    assertTrue(p.unplaced.isEmpty())
    assertTrue(p.blocked.isEmpty())
  }

  @Test
  fun overriddenCourierAboveCapIsReportedWithItsRealCount() {
    val p = AutoAssign.plan(listOf(o(1)), listOf(c("A", live = 4, cap = 2)))
    assertEquals(listOf("O1"), p.unplaced.map { it.orderId })
    assertEquals("4 مأموریتِ هم‌زمان دارد (سقفِ کارِ هم‌زمان: 2)", p.blocked.single().reason)
  }
}
