package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.print.EasySetupPref
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  HW-12 — بخشِ تنظیماتِ «راه‌اندازیِ آسانِ دستگاه‌ها» (همین دستگاه، پیش‌فرض خاموش). عنوان در فهرستِ
 *  `DeskSettingsLegacy` هم هست. کارِ اصلی در «دستگاه‌ها» است (`EasySetupAndroid.kt`).
 */

const val EASY_SETUP_TITLE = "راه‌اندازیِ آسانِ چاپگر و بارکدخوان (آزمایشی)"

/** توضیحِ بخش — یکی برای رابطِ امروز و صفحهٔ نسخهٔ ۳ (`settings/devices/easy-setup`) */
const val EASY_SETUP_NOTE =
  "در «دستگاه‌ها»: پیدا کردنِ خودکارِ چاپگرِ شبکه (فقط زیرشبکهٔ همین وای‌فای/LAN، درگاهِ ۹۱۰۰) و بلوتوثِ نزدیک، " +
  "چاپِ شناسایی، برگهٔ بارکدِ آزمون و «آزمونِ بارکدخوان» که پیشوند و پسوندِ بارکدخوان را خودش پیدا می‌کند."

@Composable
fun EasySetupSettingsWired(context: android.content.Context) {
  LaunchedEffect(Unit) { EasySetupPref.load(context) }
  val on = EasySetupPref.on.value
  SettingsSection(
    icon = Icons.Filled.QrCodeScanner,
    title = EASY_SETUP_TITLE,
    subtitle = if (on) StrShell.switchOn else StrSettings.off,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(
      EASY_SETUP_NOTE,
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
    )
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { EasySetupPref.set(context, it == "on") },
    )
  }
}
