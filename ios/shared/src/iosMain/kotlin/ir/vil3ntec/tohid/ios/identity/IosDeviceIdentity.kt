package ir.vil3ntec.tohid.ios.identity

import android.content.Context
import android.content.IosContext
import ir.vil3ntec.tohid.data.DiskWrite
import ir.vil3ntec.tohid.ios.keychain.Keychain
import ir.vil3ntec.tohid.ios.keychain.KeychainException
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import platform.Foundation.NSNumber
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.UIKit.UIApplication

/**
 *  لایهٔ نازکِ iOS ِ هویتِ دستگاه — همهٔ تصمیم در ‎commonMain/…/identity/DeviceIdentity.kt‎ (شرحِ قاعده همان‌جا).
 *
 *  • آینه: قلمِ کلیددانِ `device-identity` با `Keychain.put` = «پس از نخستین بازگشایی، فقط همین دستگاه»
 *    (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`): به آیفونِ دیگر نمی‌رود، نه با پشتیبان نه با انتقال.
 *  • پرونده: `Application Support/tohid-device/device-id` با `NSURLIsExcludedFromBackupKey` روی پوشه و پرونده — کنارِ
 *    `filesDir` (`Application Support/tohid`) نه درونش، تا هیچ پشتیبانِ خودِ برنامه هم آن را نبرد. گزارشِ همین دستگاه
 *    (`events.log`، ۵۰ سطرِ آخر) هم همان‌جاست.
 *  • قفل: `isProtectedDataAvailable` (فقط روی رشتهٔ اصلی؛ جای دیگر ⇒ «نمی‌دانیم») و هر خطای کلیددان/پرونده ⇒ هیچ تصمیمی.
 *
 *  صدازننده‌ها: `appDidLaunch`، `IosBoot.store` (پیش از دفتر و همگام‌سازی)، بازگشت به پیش‌زمینه و بازگشاییِ دستگاه
 *  (`IosLifecycle`). نتیجهٔ قطعی یک بار در هر فرآیند؛ «نمی‌دانیم» ⇒ دوباره در صدای بعد.
 */
internal object IosDeviceIdentity {
  private const val TAG = "DeviceIdentity"
  private const val KEEP_LOG = 50

  /** نتیجهٔ همین فرآیند (null ⇒ هنوز نسنجیده) */
  var verdict: Verdict? = null
    private set

  fun check(): Verdict {
    verdict?.takeIf { it !is Verdict.Undecided }?.let { return it }
    val v = try {
      DeviceIdentityGuard(Store).run()
    } catch (e: Throwable) {
      //  هر گامِ نوشتن دوباره‌پذیر است (ژورنال): بارِ بعد از همان‌جا
      Verdict.Undecided("خطا: ${e.message ?: e::class.simpleName}")
    }
    verdict = v
    android.util.Log.i(TAG, v.toString())
    return v
  }

  internal fun markerDir(): File = File(IosContext.filesDir.parentFile, DeviceIdentityKeys.MARKER_DIR)
  internal fun markerFile(): File = File(markerDir(), DeviceIdentityKeys.MARKER_FILE)
  private fun logFile(): File = File(markerDir(), "events.log")

  /** `NSURLIsExcludedFromBackupKey` ِ همین مسیر */
  internal fun excludedFromBackup(f: File): Boolean {
    val values = NSURL.fileURLWithPath(f.path).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)
    return (values?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue == true
  }

  private fun exclude(f: File) {
    val ok = NSURL.fileURLWithPath(f.path).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    if (!ok || !excludedFromBackup(f)) throw IOException("بیرون گذاشتن از پشتیبان نشد: ${f.name}")
  }

  internal object Store : DeviceIdentityGuard.Io {
    private fun prefs(suite: String) = IosContext.getSharedPreferences(suite, Context.MODE_PRIVATE)

    override fun protectedData(): Boolean =
      NSThread.isMainThread && runCatching { UIApplication.sharedApplication.isProtectedDataAvailable() }.getOrDefault(false)

    override fun get(suite: String, key: String): String? = prefs(suite).getString(key, null)

    override fun keys(suite: String): Set<String> = prefs(suite).all.keys.toSet()

    override fun edit(suite: String, changes: Map<String, String?>) {
      val e = prefs(suite).edit()
      for ((k, v) in changes) if (v == null) e.remove(k) else e.putString(k, v)
      e.commit()
    }

    override fun clear(suite: String) {
      prefs(suite).edit().clear().commit()
    }

    override fun keychain(): Probe = try {
      val uid = Keychain.read(DeviceIdentityKeys.KEYCHAIN_ACCOUNT)?.decodeToString()?.trim().orEmpty()
      if (uid.isEmpty()) Probe.Absent else Probe.Value(uid)
    } catch (e: KeychainException) {
      Probe.Unavailable("OSStatus ${e.status}")
    }

    override fun putKeychain(uid: String) = Keychain.put(DeviceIdentityKeys.KEYCHAIN_ACCOUNT, uid.encodeToByteArray())

    override fun marker(): Probe = try {
      val f = markerFile()
      val uid = if (f.exists()) f.readText().trim() else ""
      if (uid.isEmpty()) Probe.Absent else Probe.Value(uid)
    } catch (e: Throwable) {
      Probe.Unavailable(e.message ?: "خواندنِ پرونده")
    }

    override fun putMarker(uid: String) {
      val dir = markerDir()
      dir.mkdirs()
      exclude(dir)
      DiskWrite.text(markerFile(), uid)
      exclude(markerFile())
    }

    override fun newUid(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let(DeviceIdentity::hex)

    override fun log(line: String) {
      android.util.Log.i(TAG, line)
      runCatching {
        val f = logFile()
        markerDir().mkdirs()
        val kept = if (f.exists()) f.readText().lines().filter { it.isNotBlank() }.takeLast(KEEP_LOG - 1) else emptyList()
        val at = platform.Foundation.NSDate().description
        DiskWrite.text(f, (kept + "$at $line").joinToString("\n", postfix = "\n"))
      }
    }
  }
}
