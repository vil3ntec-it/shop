package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrExpenses
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.DebtReminder
import ir.vil3ntec.tohid.ui.theme.Shop

// i18n: سه‌زبانه — نوشته‌ها در `StrShell`/`StrExpenses`؛ قالب و جای‌نگهدارهایش ({نام}…) داده‌اند.

/**
 *  CRM-06 — ویرایشِ «متنِ یادآوریِ بدهی» (بخشِ «گزارش‌های مشتری» ِ تنظیماتِ کامپیوتر).
 *
 *  همان سنجش و ساختِ متنِ `DebtReminder` (`check`، `message`)؛ این‌جا فقط فرم، پیش‌نمایش با یک مشتریِ
 *  نمونه و دو دکمه. ذخیره و خواندن با صدازننده است (`DebtReminderPref`)، پس این پرونده `Context` ندارد.
 *
 *  @param saved قالبِ فعلیِ دستگاه (نبودن = پیش‌فرض)
 *  @param onSave `null` ⇒ ذخیره شد؛ وگرنه همان خطا
 */
@Composable
fun DebtReminderTemplateEditor(
  saved: String,
  onSave: (String) -> DebtReminder.Problem?,
  onReset: () -> Unit,
  onMessage: (String) -> Unit,
) {
  var text by remember(saved) { mutableStateOf(saved) }
  val problem = remember(text) { DebtReminder.check(text) }
  val preview = remember(text, problem) {
    if (problem != null) "" else DebtReminder.message(ReminderTemplateText.sample(), text)
  }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(StrShell.reminderTemplate, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
    Text(
      StrShell.reminderTemplateNote(ReminderTemplateText.slots()),
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    OutlinedTextField(
      value = text,
      onValueChange = { text = it.take(DebtReminder.MAX_TEMPLATE_LENGTH + 1) },
      minLines = 3,
      isError = problem != null,
      supportingText = if (problem == null) null else {
        { Text(ReminderTemplateText.problem(problem)) }
      },
      modifier = Modifier.fillMaxWidth(),
    )
    if (preview.isNotEmpty()) {
      Text(StrShell.reminderPreview(preview), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextButton(
        enabled = problem == null && text.trim() != saved,
        onClick = {
          val p = onSave(text)
          onMessage(if (p == null) StrShell.reminderSaved else ReminderTemplateText.problem(p))
        },
      ) { Text(StrExpenses.save) }
      TextButton(
        enabled = saved != DebtReminder.DEFAULT_TEMPLATE || text != DebtReminder.DEFAULT_TEMPLATE,
        onClick = { onReset(); text = DebtReminder.DEFAULT_TEMPLATE },
      ) { Text(StrShell.reminderDefault) }
    }
  }
}

/** متن‌های فرم — جدا تا آزمون بی Compose بسنجدشان */
object ReminderTemplateText {
  /** «{نام} {مبلغ} …» — همان فهرستِ `DebtReminder.PLACEHOLDERS` */
  fun slots(): String = DebtReminder.PLACEHOLDERS.joinToString(" ") { "{$it}" }

  fun problem(p: DebtReminder.Problem): String = when (p) {
    DebtReminder.Problem.Empty -> StrShell.reminderEmpty
    DebtReminder.Problem.TooLong -> StrShell.reminderTooLong(DebtReminder.MAX_TEMPLATE_LENGTH)
    is DebtReminder.Problem.Unknown -> StrShell.reminderUnknown(p.slots.joinToString(" ") { "{$it}" })
  }

  /** مشتریِ نمونهٔ پیش‌نمایش: هر پنج جای‌نگهدار مقدار دارد */
  fun sample(): DebtReminder.Vars =
    DebtReminder.Vars(StrShell.reminderSampleName, 1250.0, StrShell.reminderSampleStore, SAMPLE_DUE, 45)

  /** سررسیدِ نمونه — فقط برای پیش‌نمایش، به خورشیدی نشان داده می‌شود */
  const val SAMPLE_DUE = "2026-11-15"
}
