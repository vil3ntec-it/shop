package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.DebtReminder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CRM-06 — متن‌های فرمِ «متنِ یادآوریِ بدهی»: سه‌زبانه، و پیش‌نمایش با هر پنج جای‌نگهدارِ پر */
class ReminderTemplateTextTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  @Test
  fun `جای‌نگهدارها همان فهرستِ DebtReminder`() {
    assertEquals("{نام} {مبلغ} {دکان} {سررسید} {روز}", ReminderTemplateText.slots())
  }

  @Test
  fun `خطاها به زبانِ صفحه و جای‌نگهدارِ ناشناخته با نامش`() {
    I18n.source = { Lang.FA }
    assertEquals("متنِ یادآوری خالی است", ReminderTemplateText.problem(DebtReminder.Problem.Empty))
    assertEquals("جای‌نگهدارِ ناشناخته: {ناشناس}", ReminderTemplateText.problem(DebtReminder.Problem.Unknown(listOf("ناشناس"))))
    I18n.source = { Lang.EN }
    assertEquals("The reminder text is empty", ReminderTemplateText.problem(DebtReminder.Problem.Empty))
    assertEquals("The reminder text can have at most ${DebtReminder.MAX_TEMPLATE_LENGTH} characters", ReminderTemplateText.problem(DebtReminder.Problem.TooLong))
    assertTrue(ReminderTemplateText.problem(DebtReminder.Problem.Unknown(listOf("x"))).contains("{x}"))
  }

  @Test
  fun `پیش‌نمایش هیچ جای‌نگهداری را خالی یا دست‌نخورده نمی‌گذارد`() {
    val all = DebtReminder.PLACEHOLDERS.joinToString(" | ") { "{$it}" }
    val text = DebtReminder.message(ReminderTemplateText.sample(), all)
    assertFalse(text, text.contains("{"))
    assertEquals(DebtReminder.PLACEHOLDERS.size, text.split(" | ").count { it.isNotBlank() })
  }
}
