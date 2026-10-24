package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/**
 *  `settings/general/language`: زبانِ نوشته‌های برنامه (فارسی / English / پښتو —
 *  همان لحظه، فقط روی همین دستگاه) و نامِ ماه‌های خورشیدی (دری یا پشتو، LOC-01).
 */
@Composable
internal fun RouteScope.LanguageScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val lang = LocaleChoice.lang
  val months = LocaleChoice.months
  SettingsPageFrame(title = SettingsLayout.labelOf("زبان"), onBack = onBack) {
    item(key = "lang") {
      V3Section(StrSettings.appLanguage, footer = StrSettings.languageNote) {
        Lang.entries.forEachIndexed { i, l ->
          if (i > 0) V3Divider(start = 22.dp)
          V3ListRow(
            l.native,
            tile = false,
            chevron = false,
            trailing = if (l == lang) ({ Check() }) else null,
            onClick = { if (l != lang) { Haptics.confirm(context); LocaleChoice.setLang(context, l) } },
          )
        }
      }
    }
    item(key = "months") {
      V3Section(StrSettings.monthNames) {
        listOf(LocaleText.MONTHS_DARI to StrSettings.monthsDari, LocaleText.MONTHS_PASHTO to StrSettings.monthsPashto).forEachIndexed { i, (id, label) ->
          if (i > 0) V3Divider(start = 22.dp)
          V3ListRow(
            label,
            tile = false,
            chevron = false,
            trailing = if (id == months) ({ Check() }) else null,
            onClick = { LocaleChoice.setMonths(context, id) },
          )
        }
      }
      SettingsNote(StrSettings.monthsToday(SolarHijri.formatLong(todayIso(), months, LocaleFormat.DIGITS_LATN)))
    }
  }
}

@Composable
private fun Check() {
  Icon(Icons.Outlined.Check, contentDescription = StrSettings.chosen, tint = PhoneV3Roles.brandText(PhoneV3.colors), modifier = Modifier.size(20.dp))
}
