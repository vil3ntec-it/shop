package ir.vil3ntec.tohid.ios.backup

import android.content.IosContext
import java.io.File
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

/**
 *  `NSURLIsExcludedFromBackupKey` ِ یک مسیر — یک کمک برای هر دو: پوشه و پروندهٔ نشانِ هویت (`tohid-device/`، #1012،
 *  ‎ios/identity/IosDeviceIdentity.kt‎) و پوشه‌های [NoBackupDirs]. ویژگی مالِ همان قلم است؛ روی پوشه درونش را هم بیرون می‌گذارد.
 */
internal object BackupExclusion {
  fun isExcluded(f: File): Boolean {
    val values = NSURL.fileURLWithPath(f.path).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)
    return (values?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue == true
  }

  /** گذاشتن و دوباره خواندن؛ true فقط وقتی خوانده شد */
  fun exclude(f: File): Boolean =
    NSURL.fileURLWithPath(f.path).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null) && isExcluded(f)
}

/**
 *  لایهٔ نازکِ iOS ِ [NoBackupDirs] (شرحِ اشکال و قاعده همان‌جا).
 *
 *  صدازننده‌ها: `Context.filesDir` ِ shim ([firstUse]: نخستین بارِ هر فرآیند، پیش از آنکه کدِ مشترک — `ShadowStore.dirOf`،
 *  `V3StateDir` — مسیری زیرِ آن بسازد؛ برنامه، کارِ پس‌زمینه و آزمونِ شبیه‌ساز همه از همین می‌گذرند)، `appDidLaunch` و بازگشت
 *  به پیش‌زمینه ([ensure]: دوباره فقط اگر بارِ پیش نشد). هیچ‌کدام پرتاب نمی‌کند؛ نشد ⇒ گزارش و بارِ بعد.
 */
internal object IosNoBackupDirs {
  private const val TAG = "NoBackupDirs"
  private const val NOT_YET = 0
  private const val DONE = 1
  private const val RETRY = 2

  private val state = kotlin.concurrent.AtomicInt(NOT_YET)

  /** همه بیرون‌اند (آزمونِ شبیه‌ساز) */
  val done: Boolean get() = state.value == DONE

  /** `Context.filesDir`: یک بار در هر فرآیند */
  fun firstUse(filesDir: File) {
    if (state.compareAndSet(NOT_YET, RETRY)) run(filesDir)
  }

  /** `appDidLaunch` و بازگشت به پیش‌زمینه: دوباره اگر بارِ پیش نشد */
  fun ensure() {
    val dir = IosContext.filesDir
    if (state.value != DONE) run(dir)
  }

  private fun run(filesDir: File) {
    val failed = NoBackupDirs.apply(Disk(filesDir))
    state.value = if (failed.isEmpty()) DONE else RETRY
    if (failed.isNotEmpty()) android.util.Log.w(TAG, "بیرون گذاشتن از پشتیبان نشد: ${failed.joinToString()}")
  }

  /** پوشه‌های `filesDir/<نام>` روی دیسکِ واقعی */
  internal class Disk(private val filesDir: File) : NoBackupDirs.Io {
    fun dir(name: String) = File(filesDir, name)
    override fun mkdir(name: String): Boolean = dir(name).let { it.isDirectory || it.mkdirs() }
    override fun excluded(name: String): Boolean = BackupExclusion.isExcluded(dir(name))
    override fun exclude(name: String): Boolean = BackupExclusion.exclude(dir(name))
  }
}
