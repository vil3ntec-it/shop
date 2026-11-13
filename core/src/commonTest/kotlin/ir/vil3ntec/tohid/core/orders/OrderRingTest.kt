package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NOTIF-06 / ORD-02 / S36 (کامپیوتر): زنگِ سفارشِ تازه تا «دیدم» — همان قاعده‌های `OrderRing` */
class OrderRingTest {

  private val t0 = 1_800_000_000_000L
  private fun n(id: String, order: String = "o$id", no: Long = 7) = OrderNotice(id, order, no)

  @Test
  fun newNoticeRingsAtOnceThenRepeatsUntilSeen() {
    val first = OrderRing.onPending(OrderRing.State(), listOf(n("a")), t0)
    assertEquals(listOf("a"), first.arrived.map { it.id })
    assertTrue(first.ring, "رسید ⇒ همان دم زنگ")
    //  پرسشِ بعدی پیش از فاصلهٔ تکرار ⇒ بی زنگ و بی «تازه»
    val soon = OrderRing.onPending(first.state, listOf(n("a")), t0 + OrderRing.POLL_MS)
    assertFalse(soon.ring)
    assertEquals(emptyList(), soon.arrived)
    //  پس از فاصلهٔ تکرار ⇒ دوباره زنگ
    val again = OrderRing.onPending(soon.state, listOf(n("a")), t0 + OrderRing.RING_EVERY_MS)
    assertTrue(again.ring, "تا «دیدم» تکرار")
    //  «دیدم» ⇒ خاموش، حتی اگر سرور هنوز همان را برگرداند (رسید هنوز نرسیده یا نقش اجازهٔ بستن ندارد)
    val seen = OrderRing.seen(again.state, null)
    assertEquals(emptyList(), seen.ringing)
    val after = OrderRing.onPending(seen, listOf(n("a")), t0 + 10 * OrderRing.RING_EVERY_MS)
    assertFalse(after.ring)
    assertEquals(emptyList(), after.state.ringing)
  }

  @Test
  fun serverDroppingTheNoticeStopsTheRingAndForgetsIt() {
    var s = OrderRing.onPending(OrderRing.State(), listOf(n("a"), n("b")), t0).state
    s = OrderRing.seen(s, listOf("a"))
    assertEquals(listOf("b"), s.ringing.map { it.id })
    //  سفارشِ b را گوشی پذیرفت ⇒ از `/pending` رفت ⇒ خاموش؛ «دیدهٔ» a هم با رفتنش فراموش می‌شود
    val gone = OrderRing.onPending(s, emptyList(), t0 + OrderRing.RING_EVERY_MS)
    assertFalse(gone.ring)
    assertEquals(OrderRing.State(lastRingAt = gone.state.lastRingAt), gone.state)
  }

  @Test
  fun anotherArrivalWhileRingingRingsAtOnce() {
    val s = OrderRing.onPending(OrderRing.State(), listOf(n("a")), t0).state
    val b = OrderRing.onPending(s, listOf(n("b"), n("a")), t0 + 1_000)
    assertTrue(b.ring)
    assertEquals(listOf("b"), b.arrived.map { it.id })
    assertEquals(listOf("b", "a"), b.state.ringing.map { it.id }, "ترتیبِ سرور: تازه‌ترین اول")
  }

  @Test
  fun offlineKeepsRingingKnownNoticesButFindsNothingNew() {
    val s = OrderRing.onPending(OrderRing.State(), listOf(n("a")), t0).state
    assertFalse(OrderRing.onOffline(s, t0 + 1_000).ring)
    val later = OrderRing.onOffline(s, t0 + OrderRing.RING_EVERY_MS)
    assertTrue(later.ring, "بی اینترنت ⇒ سفارشِ دیده‌نشده هنوز زنگ می‌زند")
    assertEquals(emptyList(), later.arrived)
    assertFalse(OrderRing.onOffline(OrderRing.State(), t0).ring, "هیچ سفارشِ شناخته ⇒ هیچ صدا")
  }

  @Test
  fun malformedAndDuplicateIdsAreIgnoredAndListIsCapped() {
    val bad = listOf(n("a b"), n("ok", order = "x/y"), n("c"), n("c"))
    val step = OrderRing.onPending(OrderRing.State(), bad, t0)
    assertEquals(listOf("c"), step.state.ringing.map { it.id })
    val many = (1..OrderRing.MAX_RINGING + 5).map { n("n$it") }
    assertEquals(OrderRing.MAX_RINGING, OrderRing.onPending(OrderRing.State(), many, t0).state.ringing.size)
  }

  @Test
  fun clickTargetIsTheOnlyOrderOrTheList() {
    assertNull(OrderRing.target(OrderRing.State()))
    val one = OrderRing.onPending(OrderRing.State(), listOf(n("a", order = "ord1")), t0).state
    assertEquals("ord1", OrderRing.target(one))
    val two = OrderRing.onPending(one, listOf(n("b"), n("a", order = "ord1")), t0).state
    assertNull(OrderRing.target(two), "چند سفارش ⇒ فهرستِ سفارش‌ها")
  }
}
