package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 *  کلیدِ انیمیشن — همان «انیمیشن روشن / مثل تنظیم گوشی» صفحهٔ تنظیماتِ وب.
 *
 *  اندروید یک تنظیمِ سراسری دارد که کاربر می‌تواند برای ذخیرهٔ باتری یا
 *  کم‌کردنِ حرکت، همهٔ انیمیشن‌ها را خاموش کند. تا وقتی کاربر خودش
 *  «انیمیشن روشن» را انتخاب نکرده، ما هم به همان تنظیم احترام می‌گذاریم؛
 *  اگر انتخاب کرد، انیمیشن‌ها همیشه اجرا می‌شوند.
 *
 *  حالت در یک `mutableStateOf` نگه داشته می‌شود تا لحظه‌ای که کاربر کلید
 *  را می‌زند، کلِ صفحه بدون بازکردنِ دوباره عوض شود.
 */
object Motion {

  private const val PREFS = "tohid"
  private const val KEY = "motion"

  /** `full` یعنی همیشه روشن، `auto` یعنی مثل تنظیم گوشی */
  var choice by mutableStateOf("full")
    private set

  /** آیا همین حالا باید انیمیشن اجرا شود */
  var enabled by mutableStateOf(true)
    private set

  fun load(context: Context) {
    // پیش‌فرض روشن است، چون کاربر انیمیشن‌ها را خواسته — مثل نسخهٔ وب
    choice = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "full") ?: "full"
    apply(context)
  }

  fun set(context: Context, value: String) {
    choice = value
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    apply(context)
  }

  private fun apply(context: Context) {
    enabled = choice == "full" || !systemReducesMotion(context)
  }

  /** گوشی گفته حرکت کم شود یا انیمیشن‌ها خاموش‌اند؟ */
  private fun systemReducesMotion(context: Context): Boolean = runCatching {
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    scale == 0f
  }.getOrDefault(false)
}
