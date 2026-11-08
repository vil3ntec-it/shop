package ir.vil3ntec.tohid.core.delivery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  سپردنِ خودکار با محدودهٔ خدمتِ هر پیک (S83، §۱۹.۱۲؛ سرور: `areas` ⇒ ۴۰۹ `courier_out_of_area`).
 *  پیکِ بیرون از محدوده هرگز خودکار نمی‌گیرد و سفارش «جا نشد» با دلیل در صفِ دستی می‌ماند؛
 *  محدودهٔ خالی یا سفارشِ بی ناحیه همان رفتارِ پیشین است.
 */
class AutoAssignAreaTest {

  private fun o(no: Long, area: String = "", expected: Long = 0) =
    AutoAssign.Candidate("O$no", no, expectedCollectMinor = expected, createdAt = no, area = area)

  private fun c(id: String, vararg areas: String, live: Int = 0, active: Boolean = true) =
    CourierRow(id = id, name = id, active = active, cashCapMinor = 1_000_000, liveCount = live, areas = areas.toList())

  @Test
  fun outOfAreaCourierIsNeverPickedEvenWhenLeastLoaded() {
    val p = AutoAssign.plan(listOf(o(1, "کارته")), listOf(c("A", "خیرخانه"), c("B", live = 4)))
    assertEquals(listOf("B"), p.picks.map { it.courierId })
    assertTrue(p.unplaced.isEmpty())
  }

  @Test
  fun inAreaCourierIsPickedAcrossSpellings() {
    val p = AutoAssign.plan(listOf(o(1, "خیر خانه"), o(2, "کارته 4")), listOf(c("A", "خیر‌خانه", "کارتهٔ ۴")))
    assertEquals(listOf("A", "A"), p.picks.map { it.courierId })
  }

  @Test
  fun noCourierCoversTheAreaSoTheOrderStaysForManualAssign() {
    val p = AutoAssign.plan(listOf(o(1, "کارته"), o(2, "خیرخانه")), listOf(c("A", "خیرخانه"), c("B", "شهرنو")))
    assertEquals(listOf("O2" to "A"), p.picks.map { it.orderId to it.courierId })
    val left = p.unplaced.single()
    assertEquals("O1", left.orderId)
    assertEquals("ناحیهٔ «کارته» در محدودهٔ خدمتِ هیچ پیکِ فعالی نیست — دستی بسپارید", left.reason)
    assertTrue(p.blocked.isEmpty(), "محدوده دلیلِ «خودکار نمی‌گیرد» ِ پیک نیست؛ فقط همان سفارش")
  }

  @Test
  fun inactiveInAreaCourierDoesNotCount() {
    val p = AutoAssign.plan(listOf(o(1, "کارته")), listOf(c("A", "کارته", active = false), c("B", "خیرخانه")))
    assertTrue(p.picks.isEmpty())
    assertEquals("ناحیهٔ «کارته» در محدودهٔ خدمتِ هیچ پیکِ فعالی نیست — دستی بسپارید", p.unplaced.single().reason)
  }

  @Test
  fun withoutAreasOrOrderAreaNothingChanges() {
    val couriers = listOf(c("A", live = 2), c("B", live = 1))
    val before = AutoAssign.plan(listOf(o(1), o(2), o(3)), couriers)
    val withArea = AutoAssign.plan(listOf(o(1, "کارته"), o(2, "خیرخانه"), o(3, "شهرنو")), couriers)
    assertEquals(before.picks.map { it.courierId }, withArea.picks.map { it.courierId })
    val limited = listOf(c("A", "خیرخانه", live = 2), c("B", "شهرنو", live = 1))
    val unknown = AutoAssign.plan(listOf(o(1), o(2), o(3)), limited)
    assertEquals(before.picks.map { it.courierId }, unknown.picks.map { it.courierId }, "سفارشِ بی ناحیه بیرون از محدوده نیست")
  }

  @Test
  fun cashReasonStillWinsAmongInAreaCouriers() {
    val tight = CourierRow(id = "A", name = "A", active = true, cashCapMinor = 100, areas = listOf("کارته"))
    val p = AutoAssign.plan(listOf(o(1, "کارته", expected = 500)), listOf(tight, c("B", "خیرخانه")))
    assertTrue(p.picks.isEmpty())
    assertTrue(p.unplaced.single().reason.startsWith("نقدِ این سفارش"), p.unplaced.single().reason)
  }

  @Test
  fun orderAreaPrefersStorefrontZoneAndToggleKeepsOneSpelling() {
    assertEquals("کارتهٔ ۴", CourierArea.orderArea(" کارتهٔ ۴ ", "پشتِ بازار"))
    assertEquals("خیرخانه", CourierArea.orderArea("", "  خیرخانه "))
    assertEquals(listOf("خیر‌خانه"), CourierArea.toggle(emptyList(), "خیر‌خانه"))
    assertEquals(listOf("خیر‌خانه"), CourierArea.toggle(listOf("خیر‌خانه"), "خیر خانه").let { CourierArea.toggle(it, "خیر‌خانه") })
    assertEquals(emptyList<String>(), CourierArea.toggle(listOf("خیر‌خانه"), "خیر خانه"))
    assertTrue(CourierArea.has(listOf("كابل"), "کابل"))
    assertFalse(CourierArea.has(listOf("کابل"), "  "))
    assertEquals(CourierArea.MAX_AREAS, CourierArea.clean((1..40).map { "n$it" }).size)
    assertTrue(CourierArea.clean(listOf("ا".repeat(81), "‌", "  ")).isEmpty())
  }
}
