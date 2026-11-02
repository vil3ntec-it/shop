package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSettingsHome
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.ui.screens.SettingsHelp
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/**
 *  «قابلیت‌ها» (`settings/features`، نسخهٔ دوم ۱۴۰۵/۰۸/۱۱): بخش‌های اختیاریِ کار هر کدام یک کارت — نشانهٔ رنگی، نام،
 *  یک خطِ ساده و **یک** دکمه («روشن کن» / «خاموش کن»). روشن کردن همان کلیدِ صفحهٔ خودِ قابلیت را می‌نویسد
 *  (`rememberFeatureStates`) و ردیفِ گزینه‌هایش در دستهٔ خودش پیدا می‌شود؛ خاموش کردن هیچ داده‌ای را پاک نمی‌کند.
 *  کارتی که کلیدِ ساده ندارد («هزینهٔ خودکار»، «حساب‌پیِ دکان») دکمهٔ «تنظیم کن» دارد که صفحه‌اش را باز می‌کند.
 */
@Composable
internal fun RouteScope.FeaturesScreen(onBack: (() -> Unit)?, onOptions: (String) -> Unit) {
  val states = rememberFeatureStates()
  SettingsPageFrame(title = StrSettingsHome.features, onBack = onBack) {
    item(key = "intro") { SettingsNote(StrSettingsHome.featuresIntro) }
    SettingsLayout.FEATURES.forEach { key ->
      item(key = key) {
        FeatureCard(
          key = key,
          on = key in states.on,
          toggleable = states.toggleable(key),
          onToggle = { value ->
            if (states.set(key, value)) {
              val name = SettingsLayout.labelOf(key)
              toasts.show(if (value) StrSettingsHome.turnedOn(name) else StrSettingsHome.turnedOff(name))
            }
          },
          onOptions = { onOptions(key) },
        )
      }
    }
  }
}

/** رنگِ نشانهٔ هر کارت — فقط برای جدا دیدنِ کارت‌ها، معنایی ندارد */
private fun featureHue(key: String): V3Hue = when (key) {
  SettingsLayout.GIFT_CARD -> V3Hue.PINK
  SettingsLayout.LAYAWAY -> V3Hue.AMBER
  SettingsLayout.LOYALTY -> V3Hue.VIOLET
  SettingsLayout.SAFE_BOOK -> V3Hue.TEAL
  SettingsLayout.SPECIAL_ORDER -> V3Hue.ORANGE
  SettingsLayout.QUOTE -> V3Hue.BLUE
  SettingsLayout.PRICE_LIST -> V3Hue.LIME
  SettingsLayout.AUTO_FEES -> V3Hue.CORAL
  SettingsLayout.DELIVER_LATER -> V3Hue.SKY
  SettingsLayout.TAX_QUARTER -> V3Hue.PURPLE
  else -> V3Hue.MINT
}

/** «گزینه‌ها در «فروش و پرداخت · پرداخت»» — جایی که ردیفِ این قابلیت پس از روشن شدن دیده می‌شود */
private fun featurePlace(key: String): String? {
  val group = SettingsLayout.groupOf(key) ?: return null
  return listOfNotNull(SettingsLayout.groupLabel(group), SettingsLayout.partOf(key)?.let(SettingsLayout::partLabel)).joinToString(" · ")
}

@Composable
private fun FeatureCard(key: String, on: Boolean, toggleable: Boolean, onToggle: (Boolean) -> Unit, onOptions: () -> Unit) {
  val c = PhoneV3.colors
  //  «حساب‌پیِ دکان» حالِ روشن/خاموش ندارد؛ بقیه دارند
  val stateful = key in SettingsLayout.FEATURE_GATED
  V3Card(Modifier.padding(top = 12.dp), padding = PaddingValues(16.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
      V3IconTile(SettingsPages.icon(key), hue = featureHue(key), size = 52.dp)
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(SettingsLayout.labelOf(key), style = V3Type.headline, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f, fill = false))
          if (stateful) V3StatusPill(if (on) V3Status.DELIVERED else V3Status.INACTIVE, if (on) StrShell.switchOn else StrSettings.off)
        }
        SettingsHelp.line(key)?.let { Text(it, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 2.dp)) }
        if (on) featurePlace(key)?.let { Text(StrSettingsHome.featureWhere(it), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          when {
            toggleable && on -> V3Button(StrSettingsHome.turnOff, { onToggle(false) }, kind = V3ButtonKind.SECONDARY)
            toggleable -> V3Button(StrSettingsHome.turnOn, { onToggle(true) })
            else -> V3Button(StrSettingsHome.setUp, onOptions, kind = if (on) V3ButtonKind.SECONDARY else V3ButtonKind.PRIMARY)
          }
          if (toggleable && on) V3TextButton(StrSettingsHome.options, onOptions)
        }
      }
    }
  }
}
