package android.content.pm

/** shimِ iOS — `targetSdkVersion = 0`: هیچ قاعدهٔ «اندرویدِ تازه» (مثلاً اجازهٔ شبکهٔ محلی) بر آیفون نمی‌افتد */
class ApplicationInfo internal constructor() {
  val targetSdkVersion: Int get() = 0
  val flags: Int get() = 0
  val packageName: String get() = "ir.vil3ntec.tohid"

  companion object {
    const val FLAG_DEBUGGABLE = 2
    internal val ios = ApplicationInfo()
  }
}

/** shimِ iOS — نوعِ سرویسِ پیش‌رو (فقط نام؛ ‎android/app/Service.kt‎) */
object ServiceInfo {
  const val FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE = 0x10
  const val FOREGROUND_SERVICE_TYPE_DATA_SYNC = 0x1
}
