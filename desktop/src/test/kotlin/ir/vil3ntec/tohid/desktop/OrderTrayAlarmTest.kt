package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.orders.OrderNotice
import ir.vil3ntec.tohid.core.orders.OrderRing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  NOTIF-06 (§۳۰.۵): برنامهٔ کامپیوتر در سینی ⇒ سفارشِ تازه اعلانِ سیستم و صدا می‌سازد، صدا تا «دیدم» تکرار می‌شود و
 *  کلیک همان سفارش را باز می‌کند؛ پرچم/نقشِ خاموش ⇒ هیچ؛ بی اینترنت ⇒ بی خطا. راهِ برنامه جعلی است ([Fake]).
 */
class OrderTrayAlarmTest {

  private class Fake : OrderTrayAlarm.Port {
    var clock = 1_800_000_000_000L
    var gate: Boolean? = true
    var list: List<OrderNotice>? = emptyList()
    var boom = false
    val shownBefore = HashSet<String>()
    val announced = ArrayList<String>()
    var chimes = 0
    val acked = ArrayList<String>()
    var flushes = 0
    val opened = ArrayList<String?>()
    override fun now() = clock
    override suspend fun on() = gate
    override suspend fun pending(): List<OrderNotice>? = if (boom) error("شبکه نیست") else list
    override fun firstShown(arrived: List<OrderNotice>) = arrived.filter { shownBefore.add(it.id) }
    override fun announce(arrived: List<OrderNotice>, ringing: Int) { announced += arrived.map { it.id } }
    override fun chime() { chimes++ }
    override fun ack(notices: List<OrderNotice>) { acked += notices.map { it.id } }
    override suspend fun flush() { flushes++ }
    override fun open(orderId: String?) { opened += orderId }
  }

  private fun n(id: String) = OrderNotice(id, "o$id", 12)

  @Test fun `سفارشِ تازه در سینی تا دیدم زنگ می‌زند`() = runBlocking {
    val f = Fake().apply { list = listOf(n("a")) }
    val alarm = OrderTrayAlarm(f)
    alarm.round()
    assertEquals(listOf("a"), f.announced)
    assertEquals(1, f.chimes)
    assertTrue(alarm.state.value.active)
    f.clock += OrderRing.POLL_MS; alarm.round()
    assertEquals("پیش از فاصلهٔ تکرار صدای دوم نه", 1, f.chimes)
    f.clock += OrderRing.POLL_MS; alarm.round()
    assertEquals("تا «دیدم» تکرار", 2, f.chimes)
    assertEquals("اعلانِ سیستم یک بار برای هر سفارش", listOf("a"), f.announced)
    assertTrue(alarm.seen(open = true))
    assertEquals(listOf("a"), f.acked)
    assertEquals(listOf<String?>("oa"), f.opened)
    assertFalse(alarm.state.value.active)
    //  سرور هنوز همان را برمی‌گرداند (رسید در راه است) ⇒ دیگر صدا نه
    f.clock += 10 * OrderRing.RING_EVERY_MS; alarm.round()
    assertEquals(2, f.chimes)
    assertTrue("صفِ رسید هر دور فرستاده می‌شود", f.flushes >= 4)
  }

  @Test fun `پرچم یا نقشِ خاموش هیچ زنگی ندارد`() = runBlocking {
    val f = Fake().apply { list = listOf(n("a")); gate = false }
    val alarm = OrderTrayAlarm(f)
    alarm.round()
    assertEquals(0, f.chimes)
    assertEquals(emptyList<String>(), f.announced)
    assertEquals(0, f.flushes)
    f.gate = true; alarm.round()
    assertEquals(1, f.chimes)
    f.gate = false; f.clock += OrderRing.RING_EVERY_MS; alarm.round()
    assertEquals(1, f.chimes)
    assertFalse(alarm.state.value.active)
    assertFalse("بی زنگ کلیکِ سینی کاری با سفارش ندارد", alarm.seen(open = true))
    assertEquals(emptyList<String?>(), f.opened)
  }

  @Test fun `بی اینترنت بی خطا زنگ می‌زند`() = runBlocking {
    //  از آغاز بی اینترنت: «روشن است؟» ناپیدا ⇒ خاموش، بی خطا
    val f = Fake().apply { gate = null; list = listOf(n("a")) }
    val alarm = OrderTrayAlarm(f)
    alarm.round()
    assertEquals(0, f.chimes)
    f.gate = true; alarm.round()
    assertEquals(1, f.chimes)
    //  شبکه رفت: «روشن است؟» ناپیدا ⇒ همان پیشین؛ `/pending` خطا ⇒ همان زنگ سرِ وقتش
    f.gate = null; f.boom = true
    f.clock += OrderRing.POLL_MS; alarm.round()
    assertEquals(1, f.chimes)
    f.clock += OrderRing.POLL_MS; alarm.round()
    assertEquals(2, f.chimes)
    assertTrue(alarm.state.value.active)
    assertTrue(alarm.seen(open = false))
    assertEquals("«دیدم» ِ سینی بی باز کردن", emptyList<String?>(), f.opened)
    assertEquals(listOf("a"), f.acked)
  }

  @Test fun `اعلانِ دیده‌شده بالن نمی‌شود و چند سفارش فهرست است`() = runBlocking {
    val f = Fake().apply { list = listOf(n("b"), n("a")); shownBefore += "a" }
    val alarm = OrderTrayAlarm(f)
    alarm.round()
    assertEquals(listOf("b"), f.announced)
    assertEquals(1, f.chimes)
    assertTrue(alarm.seen(open = true))
    assertEquals(listOf<String?>(null), f.opened)
    assertEquals(listOf("b", "a"), f.acked)
  }
}
