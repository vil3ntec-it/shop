package ir.vil3ntec.tohid.core.courier

import ir.vil3ntec.tohid.core.delivery.DeliveryRating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  منطقِ صفحه‌های پیک (DLV-04/07/17/18) — همان قاعده‌های سرور، بی دستگاه:
 *  مسیر از کارت‌ها (همان `routeOf`)، برچسبِ کیسه، دکمهٔ امتیاز و متنِ میانگین.
 */
class CourierUiLogicTest {

  private fun card(id: String, status: String, lat: Double? = null) =
    MissionCard(id = id, orderNo = id.length.toLong(), status = status, lat = lat, lng = if (lat == null) null else 69.17)

  @Test
  fun routeFromCardsKeepsOnlyAcceptedLiveStopsAndPutsUnpinnedLast() {
    val cards = listOf(
      card("far", "assigned", 34.527), card("near", "moving", 34.509), card("bare", "picked_up"),
      card("mid", "arrived", 34.518), card("new", "requested", 34.501), card("failed", "failed", 34.502),
    )
    val r = RoutePlan.forCards(CourierChecks.Point(34.5, 69.17), cards)
    assertEquals(listOf("near", "mid", "far"), r.stops.map { it.card.id })
    assertEquals(listOf(1, 2, 3), r.stops.map { it.seq })
    assertEquals(listOf(1001L, 1001L, 1001L), r.stops.map { it.legM })
    assertEquals(listOf("bare"), r.unpinned.map { it.id })
    assertEquals(3003L, r.totalM)
    assertEquals(0, RoutePlan.forCards(null, listOf(card("x", "requested", 34.5))).stops.size)
  }

  @Test
  fun kmText() {
    assertEquals("1.0", RoutePlan.km(1001))
    assertEquals("0.9", RoutePlan.km(950))
    assertEquals("12.3", RoutePlan.km(12_345))
  }

  @Test
  fun bagLabelsAndMissing() {
    assertEquals("VLNB-12-2-3", CourierChecks.labelOf(12, 2, 3))
    val canonical = (1..3).map { CourierChecks.labelOf(12, it, 3) }
    assertTrue(CourierChecks.bagsCheck(canonical, 12, 3).ok, "برچسبِ ساخته‌شده همان است که سرور می‌پذیرد")
    assertEquals(listOf(1, 3), CourierChecks.missing(setOf(2), 3))
    assertEquals(emptyList<Int>(), CourierChecks.missing(setOf(1, 2), 2))
  }

  @Test
  fun rateButtonAndAverageText() {
    assertTrue(DeliveryRating.courierCanRate("delivered", "A", emptySet()))
    assertFalse(DeliveryRating.courierCanRate("delivered", "A", setOf("A")), "یک بار")
    for (s in listOf("failed", "returned", "moving", "")) assertFalse(DeliveryRating.courierCanRate(s, "A", emptySet()), s)
    assertEquals("4.3", DeliveryRating.avgText(43))
    assertEquals("5.0", DeliveryRating.avgText(50))
    assertEquals("—", DeliveryRating.avgText(null))
  }
}
