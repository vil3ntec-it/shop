package android.provider

import android.content.ContentResolver
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/*
 *  shimِ iOS ِ `android.provider.Settings` — فقط «مقیاسِ انیمیشن» که `Motion` می‌خواند: «کاهشِ
 *  حرکت» ِ دسترس‌پذیریِ iOS همان «انیمیشن خاموش» ِ اندروید است (۰)؛ وگرنه پیش‌فرضِ فراخوان.
 */
object Settings {
  object Global {
    const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"
    const val TRANSITION_ANIMATION_SCALE = "transition_animation_scale"
    const val WINDOW_ANIMATION_SCALE = "window_animation_scale"

    fun getFloat(cr: ContentResolver, name: String, def: Float): Float =
      if (name.endsWith("_scale") && UIAccessibilityIsReduceMotionEnabled()) 0f else def
  }
}
