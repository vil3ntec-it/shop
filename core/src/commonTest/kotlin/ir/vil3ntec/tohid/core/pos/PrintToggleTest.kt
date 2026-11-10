package ir.vil3ntec.tohid.core.pos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  POS-41 — Alt+P ِ پنلِ پرداخت: چاپِ همین فاکتور روشن یا خاموش، بی دست زدن به تنظیمِ «چاپِ خودکار».
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class PrintToggleTest {

  @Test
  fun untouchedFollowsTheDeviceSettingLikeToday() {
    assertTrue(PrintToggle.auto(null, autoPrint = true))
    assertFalse(PrintToggle.auto(null, autoPrint = false))
  }

  @Test
  fun onePressFlipsOnlyThisInvoice() {
    //  چاپِ خودکار روشن ⇒ «این یکی نه»؛ خاموش ⇒ «این یکی چاپ»
    assertEquals(false, PrintToggle.toggle(null, autoPrint = true))
    assertFalse(PrintToggle.auto(PrintToggle.toggle(null, autoPrint = true), autoPrint = true))
    assertEquals(true, PrintToggle.toggle(null, autoPrint = false))
    assertTrue(PrintToggle.auto(PrintToggle.toggle(null, autoPrint = false), autoPrint = false))
  }

  @Test
  fun secondPressReturnsToTheSettingNotAStickyChoice() {
    for (setting in listOf(true, false)) {
      val once = PrintToggle.toggle(null, setting)
      assertNull(PrintToggle.toggle(once, setting), "setting=$setting")
      assertEquals(setting, PrintToggle.auto(PrintToggle.toggle(once, setting), setting))
    }
    //  مقدارِ کهنه‌ای که با تنظیم برابر است هم با یک زدن وارونه می‌شود
    assertEquals(false, PrintToggle.toggle(true, autoPrint = true))
    assertEquals(true, PrintToggle.toggle(false, autoPrint = false))
  }
}
