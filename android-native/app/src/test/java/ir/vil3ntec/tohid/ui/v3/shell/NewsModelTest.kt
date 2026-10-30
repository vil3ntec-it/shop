package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.data.repo.EventsRepository.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsModelTest {
  private val now = 1_800_000_000_000L
  private val day = 24 * 60 * 60_000L
  private fun ev(id: String, at: Long, user: String = "u2", kind: String = "sale", name: String = "احمد") =
    Event(id, kind, if (kind == "sale") "فروش تازه" else "چای تمام شد", "1,200 افغانی — 3 قلم", user, name, at)

  /** روزِ ساختگی: هر ۲۴ ساعت یک روز */
  private val dayOf: (Long) -> String = { "d" + (it / day) }

  @Test
  fun `زمانِ نسبی`() {
    assertEquals("همین حالا", NewsModel.since(now - 20_000, now))
    assertEquals("5 دقیقه پیش", NewsModel.since(now - 5 * 60_000, now))
    assertEquals("2 ساعت پیش", NewsModel.since(now - 2 * 3_600_000, now))
    assertEquals("3 روز پیش", NewsModel.since(now - 3 * day, now))
  }

  @Test
  fun `جملهٔ فروش با نامِ عضو و بقیه همان سرخطِ سرور`() {
    assertEquals("احمد فروشی ثبت کرد", NewsModel.sentence(ev("1", now)))
    assertEquals("چای تمام شد", NewsModel.sentence(ev("2", now, kind = "stock_out")))
    assertTrue(NewsModel.detail(ev("2", now, kind = "stock_out"), now).startsWith("احمد · "))
  }

  @Test
  fun `روزبه‌روز، تازه‌ترین بالا، بی خبرِ خودم، با دیده‌نشده`() {
    val events = listOf(ev("a", now - 2 * day), ev("b", now - 60_000), ev("me", now, user = "u1"), ev("c", now - day))
    val days = NewsModel.days(events, me = "u1", now = now, seenAt = now - day - 1, dayOf = dayOf) { "D:$it" }
    assertEquals(listOf("امروز", "دیروز", "D:" + dayOf(now - 2 * day)), days.map { it.label })
    assertEquals(listOf("b"), days[0].rows.map { it.key })
    assertTrue(days[0].rows.single().unseen)
    assertTrue(days[1].rows.single().unseen)
    assertFalse(days[2].rows.single().unseen)
    assertEquals("ا", days[0].rows.single().letter)
  }

  @Test
  fun `دیده‌نشده فقط از دیگران`() {
    assertFalse(NewsModel.hasUnseen(listOf(ev("me", now, user = "u1")), me = "u1", seenAt = 0))
    assertTrue(NewsModel.hasUnseen(listOf(ev("x", now)), me = "u1", seenAt = now - 1))
    assertFalse(NewsModel.hasUnseen(listOf(ev("x", now)), me = "u1", seenAt = now))
  }
}
