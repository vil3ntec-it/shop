package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  برگهٔ «ماه…» / «سال…»: ‹ قبلی | ماهِ خورشیدی | بعدی › و «نمایش». نامِ ماه با پیکان
 *  به همان سو می‌لغزد (`V3PageSwitch`)؛ «نمایش» برگه را با حرکت می‌بندد و سپس بازه را می‌گذارد.
 */
@Composable
internal fun PeriodSheet(start: ReportPeriod, onDismiss: () -> Unit, onApply: (ReportPeriod) -> Unit) {
  val c = PhoneV3.colors
  val context = LocalContext.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  var p by remember { mutableStateOf(start) }
  val monthly = p.range == ReportEngine.Range.JMONTH
  val months = I18n.solarMonths(I18n.lang, LocaleChoice.months)
  fun label(x: ReportPeriod) = if (x.range == ReportEngine.Range.JMONTH) "${months.getOrElse(x.month - 1) { "" }} ${x.year}" else StrReports.yearLabel(x.year)
  fun step(delta: Int) { p = ReportsModel.step(p, delta); Haptics.confirm(context) }

  V3Sheet(onDismiss = onDismiss, title = if (monthly) StrReports.pickMonth else StrReports.pickYear) { closer ->
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      V3IconButton(if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft, StrReports.previous) { step(-1) }
      V3PageSwitch(p, { it.year * 12 + it.month }, Modifier.weight(1f)) { x ->
        Text(label(x), style = V3Type.title, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
      }
      V3IconButton(if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, StrReports.next) { step(1) }
    }
    Spacer(Modifier.height(20.dp))
    V3Button(StrReports.show, { closer.close { onApply(p) } }, Modifier.fillMaxWidth(), haptic = true)
  }
}
