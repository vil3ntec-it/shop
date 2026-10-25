package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.data.Sale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** کادرِ «فروشِ امروز»: مقایسه با همین ساعتِ دیروز و خطِ ۷ روزه (HOME-V2 §۲٫۲) */
class HomeTrendTest {
  private val today = "2026-10-24"
  private val now = 1_800_000_000_000L
  private val day = 24L * 60 * 60 * 1000
  private val hour = 60L * 60 * 1000

  private fun sale(id: String, date: String, total: Double, at: Long = 0L, status: String = "completed") =
    Sale(id = id, finalTotal = total, total = total, date = date, createdAt = at, status = status)

  @Test
  fun `مقایسه با همین ساعتِ دیروز است نه کلِ دیروز`() {
    val v = HomeTrend.of(
      listOf(
        sale("t1", today, 700.0, now - hour),
        sale("t2", today, 500.0, now - 2 * hour),
        sale("y1", "2026-10-23", 600.0, now - day - hour),        // پیش از همین ساعت ⇒ هست
        sale("y2", "2026-10-23", 400.0),                          // ساعتِ نامعلوم ⇒ هست
        sale("y3", "2026-10-23", 900.0, now - day + hour),        // پس از همین ساعت ⇒ نیست
      ),
      today, now,
    )
    assertEquals(1200.0, v.today, 0.001)
    assertEquals(1000.0, v.yesterday, 0.001)
    assertEquals(20, v.pct)
    assertEquals(HomeTrend.Direction.UP, v.direction)
  }

  @Test
  fun `لغوشده حساب نمی‌شود و کاهش منفی است`() {
    val v = HomeTrend.of(
      listOf(
        sale("t1", today, 250.0, now - hour),
        sale("t2", today, 5000.0, now - hour, status = "cancelled"),
        sale("y1", "2026-10-23", 1000.0, now - day - hour),
      ),
      today, now,
    )
    assertEquals(-75, v.pct)
    assertEquals(HomeTrend.Direction.DOWN, v.direction)
  }

  @Test
  fun `دیروز بی فروش ⇒ مقایسه پنهان`() {
    val v = HomeTrend.of(listOf(sale("t1", today, 300.0, now - hour)), today, now)
    assertNull(v.pct)
    assertNull(v.direction)
  }

  @Test
  fun `خط هفت روزِ تا امروز است، کهنه‌ترین اول`() {
    val v = HomeTrend.of(
      listOf(
        sale("t", today, 10.0, now - hour),
        sale("y", "2026-10-23", 20.0, now - day + hour),          // کلِ دیروز در خط
        sale("w", "2026-10-18", 30.0),                            // روزِ اولِ پنجره
        sale("old", "2026-10-17", 99.0),                          // بیرون از پنجره
      ),
      today, now,
    )
    assertEquals(HomeTrend.DAYS, v.series.size)
    assertEquals(listOf(30.0, 0.0, 0.0, 0.0, 0.0, 20.0, 10.0), v.series)
    assertTrue(v.hasSeries)
  }

  @Test
  fun `دکانِ تازه — همه صفر و بی خط`() {
    val v = HomeTrend.of(emptyList(), today, now)
    assertEquals(0.0, v.today, 0.0)
    assertFalse(v.hasSeries)
    assertNull(v.pct)
  }

  @Test
  fun `درصد گرد می‌شود و بی‌تغییر صفر است`() {
    assertEquals(0, HomeTrend.percent(100.0, 100.0))
    assertEquals(13, HomeTrend.percent(112.5, 100.0))
    assertNull(HomeTrend.percent(5.0, 0.0))
  }
}
