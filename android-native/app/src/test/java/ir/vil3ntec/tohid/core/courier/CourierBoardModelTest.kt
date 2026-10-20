package ir.vil3ntec.tohid.core.courier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  خانه، پیگیری، زنگ و «نقدِ من»ِ حالتِ پیک — فقط انتخاب و چیدنِ دادهٔ واقعی.
 */
class CourierBoardModelTest {

  private fun card(id: String, status: String, no: Long = 1, updated: Long = 0, landmark: String = "", recall: Boolean = false, timeline: List<MissionStep> = emptyList()) =
    MissionCard(id = id, orderNo = no, status = status, updatedAt = updated, landmark = landmark, recallRequested = recall, timeline = timeline)

  @Test fun `مأموریتِ جاری — نزدیک‌ترین به مشتری، و میانِ هم‌وضعیت‌ها ترتیبِ سرور`() {
    val live = listOf(card("a", "requested"), card("b", "assigned"), card("c", "moving"), card("d", "moving"))
    assertEquals("c", CourierBoardModel.current(live)?.id)
    assertEquals("b", CourierBoardModel.current(listOf(card("a", "requested"), card("b", "assigned")))?.id)
    assertEquals("x", CourierBoardModel.current(listOf(card("x", "arrived"), card("y", "moving")))?.id)
    assertNull(CourierBoardModel.current(emptyList()))
  }

  @Test fun `کارهای اخیر — زنده و پایان‌یافته، تازه‌ترین اول، بی تکرار`() {
    val live = listOf(card("a", "moving", updated = 30), card("b", "assigned", updated = 10))
    val done = listOf(card("c", "delivered", updated = 20), card("a", "delivered", updated = 5))
    assertEquals(listOf("a", "c", "b"), CourierBoardModel.recent(live, done).map { it.id })
    assertEquals(2, CourierBoardModel.recent(live, done, limit = 2).size)
  }

  @Test fun `جست‌وجو — شماره از آغاز و با رقمِ فارسی؛ متن در نشانه`() {
    val cards = listOf(card("a", "moving", no = 1043, landmark = "کنارِ داروخانهٔ شفا"), card("b", "moving", no = 2104))
    assertEquals(listOf("a"), CourierBoardModel.search(cards, "104").map { it.id })
    assertEquals(listOf("a"), CourierBoardModel.search(cards, "۱۰۴۳").map { it.id })
    assertEquals(listOf("a"), CourierBoardModel.search(cards, "#1043").map { it.id })
    assertEquals(listOf("a"), CourierBoardModel.search(cards, "داروخانه").map { it.id })
    assertTrue(CourierBoardModel.search(cards, "  ").isEmpty())
    assertTrue(CourierBoardModel.search(cards, "999").isEmpty())
  }

  @Test fun `زنگ فقط چیزهای واقعی را می‌شمارد`() {
    val live = listOf(card("a", "requested"), card("b", "moving", recall = true), card("c", "requested"))
    val rejected = RejectedEvent(CourierEvent("cev-xxxxxxxx", "b", "arrived", 1, 1), "bad_transition", "")
    val b = CourierBoardModel.bell(live, QueueState(rejected = listOf(rejected)))
    assertEquals(2, b.newMissions)
    assertEquals(1, b.recalls)
    assertEquals(1, b.rejectedEvents)
    assertEquals(4, b.total)
    assertEquals(0, CourierBoardModel.bell(emptyList(), QueueState()).total)
  }

  @Test fun `پیگیری — رویدادهای سرور + کارِ در صف، تازه‌ترین بالا و پلهٔ جاری روشن`() {
    val tl = listOf(
      MissionStep("created", "requested", 100, by = "shop"),
      MissionStep("status", "assigned", 200, by = "courier"),
      MissionStep("status", "picked_up", 300, by = "courier"),
    )
    val q = CourierQueue.enqueue(QueueState(), "cev-test-moving-1", "a", "moving", 400, from = "picked_up").first
    //  کارتِ نمایش وضعیتِ پیش‌بینی‌شده را دارد (همان `MissionView.forDisplay`)
    val shown = MissionView.forDisplay(card("a", "picked_up", timeline = tl), q)
    val rows = CourierBoardModel.track(shown, q)
    assertEquals(listOf("moving", "picked_up", "assigned", "requested"), rows.map { it.to })
    assertTrue(rows[0].pending)
    assertTrue(rows[0].current)
    assertFalse(rows[1].current)
    assertEquals(listOf(400L, 300L, 200L, 100L), rows.map { it.at })
    //  بی صف: آخرینِ سرور جاری است
    val plain = CourierBoardModel.track(card("a", "picked_up", timeline = tl), QueueState())
    assertTrue(plain[0].current)
    assertFalse(plain.any { it.pending })
    //  رویدادِ بی تغییرِ وضعیت (عکس) جاری نمی‌شود
    val withPhoto = CourierBoardModel.track(
      card("a", "arrived", timeline = tl + MissionStep("status", "arrived", 500) + MissionStep("photo", "arrived", 600)),
      QueueState(),
    )
    assertEquals("photo", withPhoto[0].kind)
    assertFalse(withPhoto[0].current)
    assertTrue(withPhoto[1].current)
  }

  @Test fun `روز و هفتهٔ گوشی — آغازِ روز به وقتِ محلی و هفته از شنبه`() {
    val kabul = (4 * 60 + 30) * 60_000L
    //  ۲۰۲۶-۱۰-۲۰ ساعتِ ۰۲:۰۰ به وقتِ UTC = ۰۶:۳۰ کابل (سه‌شنبه)
    val now = 1_792_454_400_000L + 2 * 3_600_000L
    val day = CourierBoardModel.dayStart(now, kabul)
    assertEquals(1_792_454_400_000L - kabul, day)
    val week = CourierBoardModel.weekStart(now, kabul)
    //  سه‌شنبه ⇐ شنبه: سه روز پیش
    assertEquals(day - 3 * 86_400_000L, week)
    //  خودِ شنبه آغازِ هفته است
    assertEquals(week, CourierBoardModel.weekStart(week + 1_000L, kabul))
  }

  @Test fun `پُریِ سقف میانِ صفر و یک؛ بی سقف صفر`() {
    assertEquals(0.65f, CourierBoardModel.capUse(CourierSummary(cashInHandMinor = 98_000, liveExpectedMinor = 552_000, cashCapMinor = 1_000_000)), 0.0001f)
    assertEquals(1f, CourierBoardModel.capUse(CourierSummary(cashInHandMinor = 2_000_000, cashCapMinor = 1_000_000)), 0f)
    assertEquals(0f, CourierBoardModel.capUse(CourierSummary(cashInHandMinor = 5)), 0f)
  }
}
