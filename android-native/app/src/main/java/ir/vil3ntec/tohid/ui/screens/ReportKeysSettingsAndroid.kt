package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.ReportKeysPref
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  RPT-13 — بخشِ «کلیدهای دوره در گزارش‌ها» در تنظیماتِ کامپیوتر، با خواندن و نوشتنِ `ReportKeysPref`
 *  (مرزِ پلتفرم، تا `Settings` یک خط بخواهد). عنوانش در فهرستِ `DeskSettings` ِ `AppRoot` هم هست.
 */
@Composable
fun ReportKeysSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(ReportKeysPref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.Keyboard,
    title = StrHome.reportKeys,
    subtitle = if (on) StrHome.reportKeysOn else StrSettings.off,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrHome.reportKeysNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = {
        on = it == "on"
        ReportKeysPref.setOn(context, on)
      },
    )
  }
}
