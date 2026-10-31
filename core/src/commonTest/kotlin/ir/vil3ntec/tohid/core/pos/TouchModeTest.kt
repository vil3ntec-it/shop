package ir.vil3ntec.tohid.core.pos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  حالتِ لمسیِ کامل در کامپیوتر — POS-31 (§۵.۳).
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class TouchModeTest {

  @Test
  fun settingOffKeepsDesktopMouseLikeToday() {
    //  تنظیمِ خاموش ⇒ کامپیوتر موشی، بی کلید و بی صفحه‌کلید — هر چه کلید بگوید
    assertFalse(TouchMode.touch(desktop = true, on = false, active = true))
    assertFalse(TouchMode.touch(desktop = true, on = false, active = false))
    assertFalse(TouchMode.showToggle(desktop = true, on = false))
    assertFalse(TouchMode.touchExtras(desktop = true, on = false, active = true))
  }

  @Test
  fun phoneIsAlwaysTouchAndGetsNoDesktopExtras() {
    for (on in listOf(true, false)) for (active in listOf(true, false)) {
      assertTrue(TouchMode.touch(desktop = false, on = on, active = active))
      assertFalse(TouchMode.touchExtras(desktop = false, on = on, active = active))
      assertFalse(TouchMode.showToggle(desktop = false, on = on))
    }
  }

  @Test
  fun settingOnShowsToggleAndToggleDecides() {
    assertTrue(TouchMode.showToggle(desktop = true, on = true))
    assertTrue(TouchMode.touch(desktop = true, on = true, active = true))
    assertTrue(TouchMode.touchExtras(desktop = true, on = true, active = true))
    assertFalse(TouchMode.touch(desktop = true, on = true, active = false))
    assertFalse(TouchMode.touchExtras(desktop = true, on = true, active = false))
  }

  @Test
  fun touchTargetMatchesSpec() {
    assertEquals(48, TouchMode.TOUCH_TARGET_DP)
    assertEquals(8, TouchMode.DANGER_GAP_DP)
  }

  @Test
  fun pageMovesByVisibleRowsAndStaysInRange() {
    assertEquals(10, TouchMode.page(first = 0, visible = 10, total = 35, forward = true))
    assertEquals(30, TouchMode.page(first = 20, visible = 10, total = 35, forward = true))
    assertEquals(34, TouchMode.page(first = 30, visible = 10, total = 35, forward = true))
    assertEquals(10, TouchMode.page(first = 20, visible = 10, total = 35, forward = false))
    assertEquals(0, TouchMode.page(first = 4, visible = 10, total = 35, forward = false))
    //  پیدا نبودنِ ردیف (پیش از نخستین چیدمان) ⇒ دست‌کم یک ردیف
    assertEquals(1, TouchMode.page(first = 0, visible = 0, total = 5, forward = true))
    assertEquals(0, TouchMode.page(first = 0, visible = 10, total = 0, forward = true))
    //  نخستینِ کهنه پس از کوتاه شدنِ فهرست
    assertEquals(0, TouchMode.page(first = 40, visible = 10, total = 3, forward = false))
    assertEquals(2, TouchMode.page(first = 40, visible = 10, total = 3, forward = true))
  }

  @Test
  fun canPageAtEdges() {
    assertFalse(TouchMode.canPage(first = 0, visible = 10, total = 35, forward = false))
    assertTrue(TouchMode.canPage(first = 0, visible = 10, total = 35, forward = true))
    assertTrue(TouchMode.canPage(first = 20, visible = 10, total = 35, forward = true))
    assertFalse(TouchMode.canPage(first = 25, visible = 10, total = 35, forward = true))
    assertTrue(TouchMode.canPage(first = 25, visible = 10, total = 35, forward = false))
    assertFalse(TouchMode.canPage(first = 0, visible = 0, total = 0, forward = true))
  }
}
