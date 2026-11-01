package ir.vil3ntec.tohid.ui.courier

import ir.vil3ntec.tohid.core.courier.CourierChecks
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.i18n.StrCourier
import ir.vil3ntec.tohid.core.i18n.StrPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  پیکِ نسخهٔ ۳ (DLV-04/07/17/18): همان شاخه‌های رابطِ امروز — کیسه پیش از «گرفتم»، موقعیت پیش از «تحویل شد»،
 *  امتیاز فقط با پرچم و یک بار — و حالتِ مشترکِ هر دو رابط (`CourierChecksState.kt`).
 */
class CourierChecksV3Test {
  private val plain = MissionCard("a1", 1043, "assigned")
  private val bagged = plain.copy(bagScanBags = 2)
  private val pinned = MissionCard("a2", 1044, "arrived", lat = 34.5, lng = 69.2, nearPinMaxM = 300)

  @Test
  fun `گرفتم با شمار کیسه اول برگه اسکن و بی آن همان دم صف`() {
    assertEquals(CheckAsk.Bags(bagged), checkFor(CourierAction(bagged, "picked_up")))
    assertNull(checkFor(CourierAction(plain, "picked_up")))
    //  برگهٔ اسکن خودش با برچسب‌ها ثبت می‌کند ⇒ دوباره برگه باز نمی‌شود
    assertNull(checkFor(CourierAction(bagged, "picked_up", bags = listOf("VLNB-1043-1-2", "VLNB-1043-2-2"))))
    assertNull(checkFor(CourierAction(bagged, "moving")))
  }

  @Test
  fun `تحویل شد با فاصله مجاز اول موقعیت و با موقعیت همان دم صف`() {
    val deliver = CourierAction(pinned, "delivered", code = "123456", cash = 5000)
    assertEquals(CheckAsk.Locate(deliver), checkFor(deliver))
    val here = CourierChecks.Point(34.5, 69.2)
    assertNull(checkFor(deliver.copy(position = here)))
    assertNull(checkFor(CourierAction(pinned.copy(nearPinMaxM = null), "delivered")))
    assertNull(checkFor(CourierAction(pinned, "failed", reason = "no_answer")))
  }

  @Test
  fun `دکمه امتیاز فقط با پرچم و فقط تحویل شده و یک بار`() {
    val done = MissionCard("a3", 1045, "delivered")
    assertFalse(CourierExtras().canRate(done))
    assertTrue(CourierExtras(ratingsEnabled = true).canRate(done))
    assertFalse(CourierExtras(ratingsEnabled = true, rated = setOf("a3")).canRate(done))
    assertFalse(CourierExtras(ratingsEnabled = true).canRate(done.copy(status = "returned")))
  }

  @Test
  fun `اسکن کیسه سفارش دیگر را رد و کیسه های پیشین را نگه می دارد`() {
    val s = BagScanState(1043, 2)
    s.edit("VLNB-1043-1-2")
    s.submit()
    assertEquals("", s.input)
    assertNull(s.note)
    assertFalse(s.ready)
    s.edit("VLNB-9999-2-2")
    s.submit()
    assertEquals(StrCourier.wrongOrder, s.note)
    assertEquals(setOf(1), s.scanned)
    s.edit("VLNB-1043-2-3")
    s.submit()
    assertEquals(StrCourier.mismatch, s.note)
    s.edit("hello")
    s.submit()
    assertEquals(StrPick.notLabel, s.note)
    s.edit("VLNB-1043-2-2")
    s.submit()
    assertTrue(s.ready)
    assertEquals(listOf("VLNB-1043-1-2", "VLNB-1043-2-2"), s.labels())
  }

  @Test
  fun `نزدیک پین همان نقطه و دور یا بی موقعیت پیام روشن`() {
    val s = NearPinState()
    assertTrue(s.busy)
    assertNull(s.settle(null, pinned))
    assertFalse(s.busy)
    assertEquals(StrCourier.noLocation, s.message)
    val near = CourierChecks.Point(34.5005, 69.2)
    assertEquals(near, s.settle(near, pinned))
    val far = CourierChecks.Point(34.6, 69.2)
    assertNull(s.settle(far, pinned))
    val d = CourierChecks.nearPin(far, CourierChecks.Point(34.5, 69.2), 300).distanceM ?: 0L
    assertEquals(StrCourier.tooFar(d, 300), s.message)
  }

  @Test
  fun `مسیر بی موقعیت هم ساخته می شود و راهنما پله به پله`() {
    val s = RouteState()
    assertEquals(StrCourier.locating, s.hint)
    s.found(null)
    assertEquals(StrCourier.routeNoStart, s.hint)
    s.found(CourierChecks.Point(34.5, 69.2))
    assertEquals(StrCourier.routeHint, s.hint)
    val route = s.plan(listOf(pinned.copy(status = "moving"), plain.copy(id = "a9")))
    assertEquals(1, route.stops.size)
    assertEquals(1, route.unpinned.size)
  }

  @Test
  fun `امتیاز ستاره می خواهد و حداکثر چهار برچسب به ترتیب فهرست`() {
    val s = RatingState()
    assertFalse(s.ok)
    s.stars = 4
    assertTrue(s.ok)
    s.allowed.reversed().forEach { s.toggle(it) }
    assertEquals(4, s.tags.size)
    assertEquals(s.allowed.filter { it in s.tags }, s.chosen())
    s.toggle(s.chosen().first())
    assertEquals(3, s.tags.size)
    assertTrue(s.ok)
  }
}
