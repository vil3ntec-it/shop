package ir.vil3ntec.tohid.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** CRM-15 — پوشاندنِ شمارهٔ مشتری برای نقشِ بی مجوزِ `customers.contact` */
class ContactMaskTest {

  @Test fun everyDigitButTheLastTwoIsHidden() {
    assertEquals("••••••••56", ContactMask.phone("0799123456"))
    //  نشانه‌ها و فاصله می‌مانند؛ رقمِ فارسی هم رقم است
    assertEquals("+•• ••• ••• •56", ContactMask.phone("+93 799 123 456"))
    assertEquals("••••••••۵۶", ContactMask.phone("۰۷۹۹۱۲۳۴۵۶"))
    //  جهت‌نماهای متنِ نمایشی دست نمی‌خورند
    assertEquals("⁦••••••••56⁩", ContactMask.phone("⁦0799123456⁩"))
  }

  @Test fun noDigitsOrShortNumbersStaySafe() {
    assertEquals("", ContactMask.phone(""))
    assertEquals("—", ContactMask.phone("—"))
    assertEquals("12", ContactMask.phone("12"))
    assertEquals("••3", ContactMask.phone("123", keep = 1))
    assertFalse(ContactMask.phone("0799123456").contains("0799"))
  }

  @Test fun maskedNumberIsRecognised() {
    assertTrue(ContactMask.isMasked(ContactMask.phone("+93791200003")))
    assertTrue(ContactMask.isMasked("+•••••••••03"))
    assertFalse(ContactMask.isMasked("+93791200003"))
    assertFalse(ContactMask.isMasked(""))
  }

  @Test fun visibleKeepsTheNumber() {
    assertEquals("0799123456", ContactMask.show("0799123456", visible = true))
    assertEquals("••••••••56", ContactMask.show("0799123456", visible = false))
  }
}
