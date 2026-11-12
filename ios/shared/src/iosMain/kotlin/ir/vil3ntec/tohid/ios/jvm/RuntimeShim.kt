package ir.vil3ntec.tohid.ios.jvm

import platform.Foundation.NSProcessInfo

/**
 *  `java.lang.Runtime` ِ JDK، فقط آنچه کدِ مشترک صدا می‌زند: سنجشِ حافظهٔ آزاد پیش از رمز/بازیابیِ پشتیبان (`BackupSeal`).
 *  iOS سقفِ heap ندارد؛ سیستم برنامه را نزدیکِ نیمی از حافظهٔ دستگاه می‌بندد ⇒ «بیشینه» یک‌چهارمِ حافظهٔ دستگاه (محتاطانه)
 *  و «در کار» صفر — همان فرمولِ `maxMemory - (totalMemory - freeMemory)` همین را می‌دهد.
 */
class Runtime private constructor() {
  fun maxMemory(): Long = (NSProcessInfo.processInfo.physicalMemory / 4uL).toLong()
  fun totalMemory(): Long = 0L
  fun freeMemory(): Long = 0L

  companion object {
    private val one = Runtime()
    fun getRuntime(): Runtime = one
  }
}
