package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.i18n.StrWhy
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  POS-22 — روشن/خاموشِ «چرا این قیمت؟» روی همین دستگاه (`tohid-ui:price_why`)؛ فایلِ مرزیِ پلتفرم
 *  (`SharedPreferences`). پیش‌فرض **خاموش**: کادرِ ویرایشِ ردیف همان پیشین است.
 */
object PriceWhyPrefs {
  val on = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "price_why"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    on.value = context.getSharedPreferences("tohid-ui", 0).getBoolean(KEY, false)
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(KEY, value).apply()
  }
}

/** همین تنظیم برای صفحه‌ها — خواندن این‌جا، تا صفحه `LocalContext` ِ تازه نگیرد */
@Composable
fun priceWhyOn(): Boolean {
  PriceWhyPrefs.load(LocalContext.current)
  return PriceWhyPrefs.on.value
}

/** بخشِ تنظیمات با خواندن و نوشتنِ همین تنظیم — تا `Settings` یک خط بخواهد */
@Composable
fun PriceWhySettingsWired(context: android.content.Context) {
  PriceWhyPrefs.load(context)
  val on = PriceWhyPrefs.on.value
  SettingsSection(
    icon = Icons.Filled.Info,
    title = StrWhy.section,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrWhy.note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { PriceWhyPrefs.set(context, it == "on") },
    )
  }
}
