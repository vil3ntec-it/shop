package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsIndexModel
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

private val THEMES = listOf(ThemeChoice.LIGHT, ThemeChoice.DARK, ThemeChoice.SYSTEM)

/**
 *  `settings/general/appearance`: روشن / تیره / خودکار (همان `onTheme` ِ پوسته،
 *  همان لحظه) و «انیمیشن روشن» (`Motion.set`: `full` همیشه، `auto` مثل تنظیم گوشی).
 */
@Composable
internal fun RouteScope.AppearanceScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  SettingsPageFrame(title = SettingsLayout.labelOf("ظاهر"), onBack = onBack) {
    item(key = "theme") {
      SettingsBlock {
        V3Segmented(
          options = THEMES.map(SettingsIndexModel::themeLabel),
          selected = THEMES.indexOf(theme).coerceAtLeast(0),
          onSelect = { onTheme(THEMES[it]) },
        )
      }
      SettingsNote(StrSettings.themeNote)
    }
    item(key = "motion") {
      val full = Motion.choice == "full"
      V3Section(null) {
        V3ToggleRow(
          StrSettings.animationsOn,
          checked = full,
          onCheckedChange = { Motion.set(context, if (it) "full" else "auto") },
          subtitle = if (full) null else StrSettings.likePhoneSetting,
          icon = Icons.Outlined.Animation,
        )
      }
      SettingsNote(StrSettings.reduceMotionNote)
    }
  }
}
