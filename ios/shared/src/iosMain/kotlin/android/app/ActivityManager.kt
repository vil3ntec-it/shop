package android.app

/** shimِ iOS — `Context.getSystemService(ActivityManager::class.java)` در iOS null است؛ فقط برای نوع */
class ActivityManager {
  val isLowRamDevice: Boolean get() = false
}
