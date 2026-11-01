package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ORD-18 (هشدارِ زمان‌بندی‌شده در برنامه) و ORD-03 (قطعِ اتصال) — همان قاعده‌های `OrderAlerts` */
class OrderAlertsTest {

  private val now = 1_800_000_000_000L
  private fun a(id: String, inMin: Int) = OrderAlerts.Start(id, 7, "accepted", now + inMin * 60_000L, now - 60_000)

  @Test
  fun soundOncePerOrderAndMemoryFollowsServer() {
    val first = listOf(a("o1", 20), a("o2", 25))
    assertEquals(listOf("o1", "o2"), OrderAlerts.fresh(first, emptySet()).map { it.id })
    var announced = OrderAlerts.keep(emptySet(), first, OrderAlerts.fresh(first, emptySet()).map { it.id })
    assertEquals(emptyList(), OrderAlerts.fresh(first, announced), "همان فهرست ⇒ صدای دوم نه")
    //  o1 شروع شد (از فهرستِ سرور رفت)، o3 تازه رسید
    val next = listOf(a("o2", 24), a("o3", 29))
    assertEquals(listOf("o3"), OrderAlerts.fresh(next, announced).map { it.id })
    announced = OrderAlerts.keep(announced, next, listOf("o3"))
    assertEquals(setOf("o2", "o3"), announced, "رفته‌ها فراموش می‌شوند")
    assertEquals(emptySet(), OrderAlerts.keep(announced, emptyList()))
  }

  @Test
  fun dismissedHiddenAndAtMostThree() {
    val list = listOf(a("o1", 5), a("o2", 10), a("o3", 15), a("o4", 20))
    assertEquals(listOf("o1", "o2", "o3"), OrderAlerts.visible(list, emptySet()).map { it.id })
    assertEquals(listOf("o1", "o3", "o4"), OrderAlerts.visible(list, setOf("o2")).map { it.id })
    assertEquals(emptyList(), OrderAlerts.visible(list, list.map { it.id }.toSet()))
  }

  @Test
  fun minutesLeftWithServerClock() {
    assertEquals(20, OrderAlerts.minutesLeft(now + 20 * 60_000, now))
    assertEquals(21, OrderAlerts.minutesLeft(now + 20 * 60_000 + 1, now))
    assertEquals(1, OrderAlerts.minutesLeft(now + 1, now))
    assertEquals(0, OrderAlerts.minutesLeft(now, now))
    assertEquals(0, OrderAlerts.minutesLeft(now - 5 * 60_000, now), "دیرشده ⇒ وقتش رسیده")
  }

  @Test
  fun netDownOnlyWhenServerUnreachable() {
    val up = OrderAlerts.ok(now)
    assertFalse(up.down)
    assertEquals(up, OrderAlerts.failed(up, retryable = false, at = now + 1), "۴xx قطع نیست")
    val down = OrderAlerts.failed(up, retryable = true, at = now + 3 * 60_000 + 5)
    assertTrue(down.down)
    assertEquals(3, OrderAlerts.minutesSinceOk(down))
    assertNull(OrderAlerts.minutesSinceOk(OrderAlerts.failed(OrderAlerts.Net(), true, now)), "هرگز وصل نبوده")
    assertFalse(OrderAlerts.ok(now + 4 * 60_000).down, "نخستین پاسخِ درست ⇒ وصل")
  }
}
