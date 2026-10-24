package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.plusDays
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3TextField

/**
 *  کادرِ سررسیدِ برد: خورشیدی نوشته می‌شود و `yyyy-MM-dd` نگه داشته می‌شود؛ خالی یعنی سررسید
 *  ندارد. زیرش تراشه‌های «یک هفته · دو هفته · یک ماه · بی سررسید». نوشتهٔ ناخوانا خطای درجا دارد
 *  و مقدار را عوض نمی‌کند.
 */
@Composable
internal fun DueDateEditor(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
  //  کادرِ تاریخِ کیت فقط رقم و «-» می‌گیرد ⇒ «۱۴۰۵-۰۸-۱۰»
  fun shown(iso: String) = if (iso.isBlank()) "" else formatDate(iso).replace('/', '-')
  var text by remember { mutableStateOf(shown(value)) }
  LaunchedEffect(value) {
    if (DebtorTxModel.parseDue(text) != value) text = shown(value)
  }
  val today = todayIso()
  val sample = remember(today) { shown(plusDays(today, DebtorTxModel.DUE_PRESETS.first())) }
  val broken = DebtorTxModel.parseDue(text) == null
  Column(modifier) {
    V3TextField(
      value = text,
      onValueChange = { raw ->
        text = raw
        DebtorTxModel.parseDue(raw)?.let(onValueChange)
      },
      label = StrMoney.dueField,
      kind = V3FieldKind.DATE,
      placeholder = sample,
      helper = StrMoney.dueHelper,
      error = if (broken) StrMoney.dueUnreadable(sample) else null,
    )
    Spacer(Modifier.height(8.dp))
    V3ChipRow(contentPadding = PaddingValues(0.dp)) {
      DebtorTxModel.DUE_PRESETS.zip(listOf(StrMoney.dueWeek, StrMoney.dueTwoWeeks, StrMoney.dueMonth)).forEach { (days, label) ->
        val iso = plusDays(today, days)
        V3Chip(label, selected = value == iso, onClick = { onValueChange(iso) })
      }
      V3Chip(StrMoney.dueNone, selected = value.isBlank(), onClick = { onValueChange("") })
    }
  }
}
