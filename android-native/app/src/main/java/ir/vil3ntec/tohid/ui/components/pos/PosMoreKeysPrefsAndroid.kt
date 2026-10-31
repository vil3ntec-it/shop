package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import ir.vil3ntec.tohid.core.pos.PosMoreKeys
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import ir.vil3ntec.tohid.ui.screens.isPrimaryModifier

/**
 *  POS-41 — روشن/خاموشِ «کلیدهای بیشترِ صندوق» روی همین دستگاه (`tohid-ui:pos_more_keys`)؛ فایلِ
 *  مرزیِ پلتفرم (`SharedPreferences`). پیش‌فرض **خاموش**: همان کلیدها و برچسب‌های پیشین.
 *  منطق در `core/pos/PosMoreKeys`.
 */
object PosMoreKeysPrefs {
  val on = mutableStateOf(false)

  /** بارگذاری و خواندن در یک گام — صفحهٔ صندوق یک خط بخواهد */
  fun isOn(context: android.content.Context): Boolean { load(context); return on.value }

  /** Shift+F5 در صندوق — `PosMoreKeys.screen` روی همین کلید */
  fun screen(on: Boolean, e: KeyEvent): PosMoreKeys.Act? = PosMoreKeys.screen(on, e.moreKey(), e.moreMods())

  /** Esc ِ کادرِ اسکن — `PosMoreKeys.field` روی همین کلید */
  fun field(on: Boolean, e: KeyEvent, queryEmpty: Boolean, cartEmpty: Boolean): PosMoreKeys.Act? =
    PosMoreKeys.field(on, e.moreKey(), e.moreMods(), queryEmpty, cartEmpty)

  private var loaded = false
  private const val KEY = "pos_more_keys"

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

/** بخشِ تنظیمات با خواندن و نوشتنِ همین تنظیم — مرزِ پلتفرم، تا `Settings` فقط یک خط بخواهد */
@androidx.compose.runtime.Composable
fun PosMoreKeysSettingsWired(context: android.content.Context) {
  PosMoreKeysPrefs.load(context)
  PosMoreKeysSettings(PosMoreKeysPrefs.on.value) { PosMoreKeysPrefs.set(context, it) }
}

/** کلیدِ Compose ⇒ نامِ کلیدِ `PosMoreKeys` (فیزیکی، مستقل از چیدمان) */
private fun KeyEvent.moreKey(): PosMoreKeys.Key = when (key) {
  Key.F5 -> PosMoreKeys.Key.F5
  Key.Escape -> PosMoreKeys.Key.ESCAPE
  else -> PosMoreKeys.Key.OTHER
}

/** پیرایه‌ها؛ Ctrl یا Cmd همان `isPrimaryModifier` */
private fun KeyEvent.moreMods(): PosMoreKeys.Mods = PosMoreKeys.Mods(isShiftPressed, isPrimaryModifier, isAltPressed)
