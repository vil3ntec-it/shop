package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrTaxQuarter
import ir.vil3ntec.tohid.core.report.QuarterTax
import ir.vil3ntec.tohid.data.TaxQuarterPref
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  یادآورِ سررسید روی خانه (رابطِ امروز): فقط وقتی «گزارشِ مالیاتِ سه‌ماهه» روشن است و امروز در پنجرهٔ
 *  `remindDays` ِ پیش از سررسید است (`QuarterTax.reminder`)؛ لمس ⇒ تنظیمات. خاموش ⇒ هیچ چیز کشیده نمی‌شود.
 */
@Composable
fun TaxDueBanner(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val on = remember { TaxQuarterPref.on(context) }
  if (!on) return
  val today = ir.vil3ntec.tohid.todayIso()
  val due = remember(today) { QuarterTax.reminder(today, TaxQuarterPref.config(context)) } ?: return
  Row(
    modifier
      .fillMaxWidth()
      .background(Shop.colors.warning.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
      .clickable { onOpen("settings") }
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.Receipt, contentDescription = null, tint = Shop.colors.warning)
    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
      Text(
        if (due.daysLeft == 0) StrTaxQuarter.reminderToday else StrTaxQuarter.reminderTitle(due.daysLeft),
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text,
      )
      Text(StrTaxQuarter.due(ir.vil3ntec.tohid.formatDate(due.dueIso)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Text(StrTaxQuarter.open, style = MaterialTheme.typography.labelMedium, color = Shop.colors.primary)
  }
}
