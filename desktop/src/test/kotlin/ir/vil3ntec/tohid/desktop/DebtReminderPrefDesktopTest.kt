package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.DebtReminder
import ir.vil3ntec.tohid.data.DebtReminderPref
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 *  CRM-06 — متنِ یادآوریِ همین دستگاه (`tohid-policy` ‹`debt_reminder_template`›) روی پیکربندیِ واقعیِ
 *  کامپیوتر: نبودن = همان جملهٔ امروز؛ فقط متنِ درست ذخیره می‌شود؛ پیش‌فرض و «برگشت» کلید را برمی‌دارند.
 */
class DebtReminderPrefDesktopTest {
  @Before fun before() { DebtReminderPref.reset(DesktopContext) }
  @After fun after() { DebtReminderPref.reset(DesktopContext) }

  @Test
  fun `نبودن همان جملهٔ امروز، درست ذخیره و نادرست رد`() {
    assertEquals(DebtReminder.DEFAULT_TEMPLATE, DebtReminderPref.template(DesktopContext))

    assertNull(DebtReminderPref.set(DesktopContext, "  {نام} جان، {مبلغ} افغانی مانده  "))
    assertEquals("{نام} جان، {مبلغ} افغانی مانده", DebtReminderPref.template(DesktopContext))

    assertEquals(DebtReminder.Problem.Unknown(listOf("ناشناس")), DebtReminderPref.set(DesktopContext, "{ناشناس}"))
    assertEquals(DebtReminder.Problem.Empty, DebtReminderPref.set(DesktopContext, "   "))
    assertEquals("{نام} جان، {مبلغ} افغانی مانده", DebtReminderPref.template(DesktopContext))

    DebtReminderPref.reset(DesktopContext)
    assertEquals(DebtReminder.DEFAULT_TEMPLATE, DebtReminderPref.template(DesktopContext))
  }

  @Test
  fun `ذخیرهٔ همان پیش‌فرض یعنی بی کلید`() {
    assertNull(DebtReminderPref.set(DesktopContext, "{نام}"))
    assertNull(DebtReminderPref.set(DesktopContext, DebtReminder.DEFAULT_TEMPLATE))
    assertEquals(DebtReminder.DEFAULT_TEMPLATE, DebtReminderPref.template(DesktopContext))
    val raw = DesktopContext.applicationContext.getSharedPreferences("tohid-policy", 0)
    assertEquals(false, raw.contains("debt_reminder_template"))
  }
}
