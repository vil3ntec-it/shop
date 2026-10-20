package ir.vil3ntec.tohid.core.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سپردنِ خودکار (DLV-03، DLV-11): هرگز پیکی که با سفارش از سقفِ نقد
 *  می‌گذرد، هرگز پیکِ غیرفعال؛ کم‌کارترین اول؛ دلیلِ فارسی برای هر «نه».
 */
class AutoAssignTest {

  private fun c(id: String, held: Long = 0, live: Long = 0, count: Int = 0, cap: Long = 1_000_000, active: Boolean = true) =
    CourierRow(id = id, name = "پیک‌$id", active = active, cashCapMinor = cap, cashHeldMinor = held, liveExpectedMinor = live, liveCount = count)

  private fun o(no: Long, cash: Long, at: Long = no) = AutoAssign.Candidate("O$no", no, cash, at)

  @Test fun `فقط نقدِ هنگامِ تحویلِ پرداخت‌نشده گرفتنی است`() {
    assertEquals(1_240_000, AutoAssign.expectedOf("cod", "unpaid", 1_240_000))
    assertEquals(0, AutoAssign.expectedOf("cod", "paid", 1_240_000))
    assertEquals(0, AutoAssign.expectedOf("online", "unpaid", 1_240_000))
  }

  @Test fun `کم‌کارترین پیک، و بار پس از هر سپردن حساب می‌شود`() {
    val p = AutoAssign.plan(listOf(o(1, 100_000), o(2, 100_000), o(3, 100_000)), listOf(c("A", count = 1), c("B")))
    assertEquals(listOf("B", "A", "B"), p.picks.map { it.courierId })
    assertTrue(p.unplaced.isEmpty())
  }

  @Test fun `قدیمی‌ترین سفارش اول`() {
    val p = AutoAssign.plan(listOf(o(9, 0, at = 50), o(4, 0, at = 10)), listOf(c("A")))
    assertEquals(listOf(4L, 9L), p.picks.map { it.orderNo })
  }

  @Test fun `سقفِ نقد — پیکِ پُر انتخاب نمی‌شود و دلیلش گفته می‌شود`() {
    //  A جای ۲۰۰٬۰۰۰ دارد، B جای ۹۰۰٬۰۰۰
    val p = AutoAssign.plan(listOf(o(1, 500_000)), listOf(c("A", held = 800_000), c("B", held = 100_000, count = 3)))
    assertEquals("B", p.picks.single().courierId)
  }

  @Test fun `به سقف رسید ⇒ سپردنِ خودکار برای او می‌ایستد`() {
    //  A: ۶۰۰٬۰۰۰ درگیر؛ با سفارشِ ۴۰۰٬۰۰۰ درست به سقف می‌رسد و دیگر خودکار نمی‌گیرد
    val p = AutoAssign.plan(listOf(o(1, 400_000), o(2, 100_000)), listOf(c("A", held = 600_000)))
    assertEquals(listOf(1L), p.picks.map { it.orderNo })
    val u = p.unplaced.single()
    assertEquals(2L, u.orderNo)
    assertTrue(u.reason, u.reason.contains("جا نمی‌شود"))
    val b = p.blocked.single()
    assertEquals("A", b.courierId)
    assertTrue(b.reason, b.reason.contains("به سقفِ نقد رسیده"))
  }

  @Test fun `سفارشِ پیش‌پرداخت به پیکِ به‌سقف‌رسیده می‌رود ولی نه به پیکِ بالای سقف`() {
    val atCap = AutoAssign.plan(listOf(o(1, 0)), listOf(c("A", held = 1_000_000)))
    assertEquals("A", atCap.picks.single().courierId)
    val over = AutoAssign.plan(listOf(o(1, 0)), listOf(c("A", held = 1_100_000)))
    assertTrue(over.picks.isEmpty())
    assertTrue(over.blocked.single().reason.contains("گذشته"))
  }

  @Test fun `پیکِ غیرفعال هرگز؛ بی پیکِ فعال دلیلِ روشن`() {
    val p = AutoAssign.plan(listOf(o(1, 0)), listOf(c("A", active = false)))
    assertTrue(p.picks.isEmpty())
    assertEquals("هیچ پیکِ فعالی نیست", p.unplaced.single().reason)
    assertEquals("غیرفعال است", p.blocked.single().reason)
  }

  @Test fun `سقفِ کارِ هم‌زمان`() {
    val p = AutoAssign.plan(listOf(o(1, 0), o(2, 0), o(3, 0)), listOf(c("A", count = 1), c("B")), maxLive = 2)
    assertEquals(listOf("B", "A", "B"), p.picks.map { it.courierId })
    assertTrue(p.unplaced.isEmpty())
    assertEquals(listOf("A", "B"), p.blocked.map { it.courierId })
    val q = AutoAssign.plan(listOf(o(1, 0), o(2, 0)), listOf(c("A", count = 2)), maxLive = 2)
    assertTrue(q.picks.isEmpty())
    assertTrue(q.unplaced.first().reason.contains("سقفِ کارِ هم‌زمان"))
    assertTrue(q.blocked.single().reason.contains("هم‌زمان"))
  }
}
