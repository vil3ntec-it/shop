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
 *  کلیدِ نوار، بی صفحه‌کلید و بی دکمهٔ صفحه). گوشی همیشه لمسی است، مثلِ امروز. فقط وقتی این دستگاه هرگز
 *  انتخاب نکرده و صفحهٔ لمسی دارد (`TouchScreenProbe`) کلیدِ نوار دیده می‌شود و صندوق تا زدنش همان موشی است
 *  (`TouchMode.choice`، §۵.۳ «تشخیص صفحه لمسی»)؛ هر انتخاب برای همین دستگاه ذخیره می‌شود.
 */
object PosTouchPrefs {
  /** تنظیم: کلیدِ «حالتِ لمسی» در نوارِ صندوق دیده شود */
  val on = mutableStateOf(false)

  /** خودِ کلید؛ نبوده ⇒ همان تنظیمِ ذخیره‌شده، تا روشن کردنِ تنظیم همان دم صندوق را لمسی کند (`TouchMode.choice`) */
  val active = mutableStateOf(true)

  private var loaded = false
  private const val PREFS = "tohid-ui"
  private const val ON = "pos_touch"
  private const val ACTIVE = "pos_touch_active"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    val c = choice(context.getSharedPreferences(PREFS, 0), TouchScreenProbe.detected)
    on.value = c.on
    active.value = c.active
  }

  /** انتخابِ ذخیره‌شده (نبوده ⇒ null) و صفحهٔ لمسیِ تشخیص‌داده ⇐ همان قاعدهٔ هسته */
  internal fun choice(p: android.content.SharedPreferences, detected: Boolean): TouchMode.Choice = TouchMode.choice(
    storedOn = if (p.contains(ON)) p.getBoolean(ON, false) else null,
    storedActive = if (p.contains(ACTIVE)) p.getBoolean(ACTIVE, true) else null,
    detected = detected,
  )

  fun setOn(context: android.content.Context, value: Boolean) {
    val p = context.getSharedPreferences(PREFS, 0)
    p.edit().putBoolean(ON, value).apply()
    val c = choice(p, TouchScreenProbe.detected)
    on.value = c.on
    active.value = c.active
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
