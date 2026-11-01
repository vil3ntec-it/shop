package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.ui.theme.Shop

/** POS-39 — کلیدِ «دریافت از مشتری در صندوق (F7)»؛ حالت و ذخیره با `TillReceivePref` در `Settings` */
@Composable
internal fun TillReceiveSettings(on: Boolean, onChange: (Boolean) -> Unit) {
  SettingsSection(
    icon = Icons.Filled.PointOfSale,
    title = StrSettings.tillReceiveTitle,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrSettings.tillReceiveNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { onChange(it == "on") },
    )
  }
}
