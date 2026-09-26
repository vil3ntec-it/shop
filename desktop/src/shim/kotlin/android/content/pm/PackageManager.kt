package android.content.pm

import android.content.Intent

open class PackageManager {
  fun getLaunchIntentForPackage(name: String): Intent? = Intent(Intent.ACTION_MAIN)
  fun queryIntentActivities(intent: Intent, flags: Int): List<Any> = emptyList()
  fun hasSystemFeature(name: String): Boolean = false

  companion object {
    const val PERMISSION_GRANTED = 0
    const val PERMISSION_DENIED = -1
    const val MATCH_DEFAULT_ONLY = 0x00010000
    const val GET_SIGNATURES = 0x40
    const val GET_SIGNING_CERTIFICATES = 0x08000000
  }
}
