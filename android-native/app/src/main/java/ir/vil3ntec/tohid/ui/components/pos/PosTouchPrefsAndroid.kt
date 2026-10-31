package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.pos.TouchMode
import ir.vil3ntec.tohid.ui.Platform

/**
 *  POS-31 — حالتِ لمسیِ صندوقِ کامپیوتر روی همین دستگاه (`tohid-ui:pos_touch` و
 *  `tohid-ui:pos_touch_active`)؛ فایلِ مرزیِ پلتفرم (`SharedPreferences`). قاعده در هسته
 *  (`core/…/pos/TouchMode.kt`).
 *
 *  پیش‌فرض **خاموش**: `on = false` ⇒ صندوقِ کامپیوتر بیت‌به‌بیت همان موشیِ امروز (هدفِ ۳۶، بی
 *  کلیدِ نوار، بی صفحه‌کلید و بی دکمهٔ صفحه). گوشی همیشه لمسی است، مثلِ امروز.
 */
object PosTouchPrefs {
  /** تنظیم: کلیدِ «حالتِ لمسی» در نوارِ صندوق دیده شود */
  val on = mutableStateOf(false)

  /** خودِ کلید؛ نبوده ⇒ روشن، تا روشن کردنِ تنظیم همان دم صندوق را لمسی کند */
  val active = mutableStateOf(true)

  private var loaded = false
  private const val PREFS = "tohid-ui"
  private const val ON = "pos_touch"
  private const val ACTIVE = "pos_touch_active"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    val p = context.getSharedPreferences(PREFS, 0)
    on.value = p.getBoolean(ON, false)
    active.value = p.getBoolean(ACTIVE, true)
  }

  fun setOn(context: android.content.Context, value: Boolean) {
    on.value = value
    context.getSharedPreferences(PREFS, 0).edit().putBoolean(ON, value).apply()
  }

  fun setActive(context: android.content.Context, value: Boolean) {
    active.value = value
    context.getSharedPreferences(PREFS, 0).edit().putBoolean(ACTIVE, value).apply()
  }

  /** صندوقِ همین لحظه لمسی است؟ (گوشی همیشه) — بخش‌های بی‌Context هم همین را می‌خوانند */
  fun touch(): Boolean = TouchMode.touch(Platform.DESKTOP, on.value, active.value)

  /** صفحه‌کلیدِ عددی و دکمهٔ صفحه — فقط کامپیوترِ لمسی */
  fun extras(): Boolean = TouchMode.touchExtras(Platform.DESKTOP, on.value, active.value)

  /** کلیدِ نوارِ صندوق دیده شود */
  fun showToggle(): Boolean = TouchMode.showToggle(Platform.DESKTOP, on.value)
}

/** بخشِ تنظیمات با خواندن و نوشتنِ همین تنظیم — مرزِ پلتفرم، تا `Settings` فقط یک خط بخواهد */
@androidx.compose.runtime.Composable
fun PosTouchSettingsWired(context: android.content.Context) {
  PosTouchPrefs.load(context)
  PosTouchSettings(PosTouchPrefs.on.value) { PosTouchPrefs.setOn(context, it) }
}
