package ir.vil3ntec.tohid.core.pay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PAY-17 — کشوی اصلی و کشوی نام‌دار (`Drawers`): مالکِ هر ردیف با مهرِ شیفت، نه دستگاه و نه ساعت؛ ردیفِ بی مهر همیشه
 *  کشوی اصلی (یعنی بی «چند کشو» همان حسابِ امروز)؛ مهرِ شیفتِ بسته‌شده پول را گم نمی‌کند.
 */
class DrawersTest {

  private val named = Drawers.Named(mapOf("A" to null, "B" to 500L))

  @Test
  fun unstampedRowsAlwaysBelongToTheMainDrawer() {
    assertNull(Drawers.owner(null, 100, named))
    assertTrue(Drawers.counts(null, 100, "M", isNamed = false, named = named, from = 0, to = 1000))
    assertFalse(Drawers.counts(null, 2000, "M", isNamed = false, named = named, from = 0, to = 1000), "بیرون از پنجره")
    assertFalse(Drawers.counts(null, 100, "A", isNamed = true, named = named, from = 0, to = 1000))
  }

  @Test
  fun stampedRowsBelongToTheirShiftWhateverTheClock() {
    assertEquals("A", Drawers.owner("A", 100, named))
    //  دستگاهِ دیگر با ساعتِ عقب‌تر (شناور): باز هم همان شیفت — شمردن با مهر است
    assertTrue(Drawers.counts("A", -5_000, "A", isNamed = true, named = named, from = 0, to = 1000))
    assertFalse(Drawers.counts("A", 100, "M", isNamed = false, named = named, from = 0, to = 1000), "از کشوی اصلی کم می‌شود")
    assertFalse(Drawers.counts("A", 100, "B", isNamed = true, named = named, from = 0, to = 1000))
  }

  @Test
  fun aStampOfAClosedOrUnknownShiftFallsBackToTheMainDrawer() {
    assertEquals("B", Drawers.owner("B", 500, named), "تا لحظهٔ بستن")
    assertNull(Drawers.owner("B", 501, named), "پس از بستن ⇒ کشوی اصلی؛ Z ِ B ثابت")
    assertTrue(Drawers.counts("B", 501, "M", isNamed = false, named = named, from = 0, to = 1000))
    assertNull(Drawers.owner("X", 100, named), "شیفتِ ناشناس")
  }

  @Test
  fun aDeviceStampsOnlyAnOpenNamedShift() {
    assertEquals("A", Drawers.stamp("A", named))
    assertNull(Drawers.stamp("B", named), "بسته")
    assertNull(Drawers.stamp("X", named))
    assertNull(Drawers.stamp(null, named))
  }

  @Test
  fun labelsAreOneCleanLine() {
    assertEquals("کشوی ۲ — احمد", Drawers.label("  کشوی ۲   —\tاحمد \n"))
    assertNull(Drawers.label("   "))
    assertNull(Drawers.label(null))
    assertNull(Drawers.label("x".repeat(Drawers.LABEL_MAX + 1)))
    assertEquals("x".repeat(Drawers.LABEL_MAX), Drawers.label("x".repeat(Drawers.LABEL_MAX)))
  }
}
