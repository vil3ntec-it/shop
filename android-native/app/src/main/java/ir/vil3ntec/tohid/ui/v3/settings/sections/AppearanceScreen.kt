package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ShopTheme
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.ThemeStore
import ir.vil3ntec.tohid.ui.theme.shopColors
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsIndexModel
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

private val THEMES = listOf(ThemeChoice.LIGHT, ThemeChoice.DARK, ThemeChoice.SYSTEM)

/**
 *  `settings/general/appearance`: روشن / تیره / خودکار (همان `onTheme` ِ پوسته،
 *  همان لحظه)، ردیفِ «تم» (THEME-V2: آسمان / اقیانوس / غروب — `ThemeStore`، فقط همین دستگاه، گذرِ
 *  ۲۵۰ms بی راه‌اندازیِ دوباره) و «انیمیشن روشن» (`Motion.set`: `full` همیشه، `auto` مثل تنظیم گوشی).
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
    item(key = "palette") {
      //  پیش‌نمایشِ هر کارت همان گرادیانِ کادرِ بزرگِ آن تم در حالتِ جاری (روز/شب)
      val dark = PhoneV3.colors.source.isDark
      val options = remember(dark) {
        ShopTheme.entries.map { t -> ThemeOption(t.id, { themeName(t) }, Brush.linearGradient(shopColors(t, dark).heroGradient)) }
      }
      SettingsBlock {
        Text(StrSettings.themeRow, style = V3Type.headline, color = PhoneV3.colors.ink)
        Spacer(Modifier.height(12.dp))
        ThemePaletteRow(options, selected = ThemeStore.theme.id, onPick = { ThemeStore.set(context, ShopTheme.of(it)) })
      }
      SettingsNote(StrSettings.themeOnDevice)
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

/** نامِ هر تم به زبانِ صفحه */
private fun themeName(t: ShopTheme): String = when (t) {
  ShopTheme.SKY -> StrSettings.themeSky
  ShopTheme.OCEAN -> StrSettings.themeOcean
  ShopTheme.DUSK -> StrSettings.themeSunset
}
