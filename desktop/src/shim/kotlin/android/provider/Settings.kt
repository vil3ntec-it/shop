package android.provider

import android.content.ContentResolver

object Settings {
  const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"
  const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
  object Global {
    const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"
    fun getFloat(cr: ContentResolver, name: String, def: Float): Float = def
  }
  object Secure {
    const val ANDROID_ID = "android_id"
    fun getString(cr: ContentResolver, name: String): String? = null
  }
}
