package android.provider

import android.content.ContentResolver
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/*
 *  shimِ iOS ِ `android.provider.Settings` — فقط «مقیاسِ انیمیشن» که `Motion` می‌خواند: «کاهشِ
 *  حرکت» ِ دسترس‌پذیریِ iOS همان «انیمیشن خاموش» ِ اندروید است (۰)؛ وگرنه پیش‌فرضِ فراخوان.
 */
object Settings {
  const val ACTION_SETTINGS = "android.settings.SETTINGS"
  const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
  const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"
  const val ACTION_NFC_SETTINGS = "android.settings.NFC_SETTINGS"
  const val ACTION_WIRELESS_SETTINGS = "android.settings.WIRELESS_SETTINGS"
  const val ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
  const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"

  object Secure {
    const val ANDROID_ID = "android_id"
    /** شناسهٔ دستگاهِ اندروید در iOS نیست ⇐ null (کدِ مشترک به شناسهٔ خودش برمی‌گردد) */
    fun getString(cr: ContentResolver, name: String): String? = null
  }

  object Global {
    const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"
    const val TRANSITION_ANIMATION_SCALE = "transition_animation_scale"
    const val WINDOW_ANIMATION_SCALE = "window_animation_scale"

    fun getFloat(cr: ContentResolver, name: String, def: Float): Float =
      if (name.endsWith("_scale") && UIAccessibilityIsReduceMotionEnabled()) 0f else def
  }
}
