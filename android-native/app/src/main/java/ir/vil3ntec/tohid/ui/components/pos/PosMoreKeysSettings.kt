package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  بخشِ تنظیماتِ «کلیدهای بیشترِ صندوق» (POS-41، `PosMoreKeys`) — فقط کامپیوتر. خودش نمی‌خواند و
 *  نمی‌نویسد: مقدار و نوشتن از صدازننده (`PosMoreKeysPrefs`). پیش‌فرض خاموش.
 */
@Composable
fun PosMoreKeysSettings(on: Boolean, onChange: (Boolean) -> Unit) {
  SettingsSection(
    icon = Icons.Filled.Keyboard,
    title = StrPos.moreKeys,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrPos.moreKeysNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { onChange(it == "on") },
    )
  }
}
