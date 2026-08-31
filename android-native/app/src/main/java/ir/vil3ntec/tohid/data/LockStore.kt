package ir.vil3ntec.tohid.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 *  قفلِ خودِ برنامه.
 *
 *  تا امروز هر کسی که گوشی را برمی‌داشت، کلِ حساب‌های دکان جلویش باز
 *  بود: طلبِ مشتری‌ها، سود، قیمتِ خرید. برای دکانی که گوشی‌اش روی پیشخوان
 *  می‌ماند، این کمبودِ کوچکی نیست.
 *
 *  رمز خودش ذخیره نمی‌شود؛ فقط اثرش. با نمکِ تصادفی و ۵۰ هزار دور
 *  چرخاندن، تا حدسِ رمزِ چهار رقمی از روی فایل هم کارِ ساده‌ای نباشد.
 *
 *  چرا اثر انگشت نه: `BiometricPrompt` اکتیویتی از نوعِ `FragmentActivity`
 *  می‌خواهد و اکتیویتیِ این برنامه آن نیست. عوض کردنش کارِ کمی نیست و
 *  بدونِ دستگاهِ واقعی هم قابلِ آزمودن نیست؛ رمز هم همان کار را می‌کند و
 *  روی هر گوشی‌ای هست. جای اثر انگشت باز است.
 */
class LockStore(context: Context) {

  private val prefs = context.getSharedPreferences("tohid-lock", Context.MODE_PRIVATE)

  /** قفل روشن است؟ */
  val enabled: Boolean get() = prefs.getString(HASH, null) != null

  /**
   *  گذاشتنِ رمز.
   *
   *  رمزِ خالی یعنی برداشتنِ قفل.
   */
  fun set(pin: String) {
    if (pin.isBlank()) {
      prefs.edit().remove(HASH).remove(SALT).remove(LEN).apply()
      return
    }
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    prefs.edit()
      .putString(SALT, salt.toHex())
      .putString(HASH, hash(pin, salt))
      .putInt(LEN, pin.length)
      .apply()
  }

  /**
   *  چند رقم است.
   *
   *  رمزِ تازه شش‌رقمی است، ولی کسی که از نسخهٔ قبل می‌آید رمزِ
   *  **چهار**رقمی دارد. اگر صفحهٔ قفل بی‌قید و شرط شش رقم می‌خواست،
   *  آن آدم هیچ‌وقت نمی‌توانست وارد شود — رمزش درست بود و صفحه
   *  هیچ‌وقت نمی‌پرسیدش. پس طول کنارِ خودِ رمز نوشته می‌شود و
   *  رمزهای قدیمی که این عدد را ندارند، چهار رقمی خوانده می‌شوند.
   */
  val length: Int
    get() = if (!enabled) NEW_PIN_LEN else prefs.getInt(LEN, LEGACY_PIN_LEN)

  /** رمزِ ذخیره‌شده از نسخهٔ قبل مانده و هنوز شش‌رقمی نشده */
  val isLegacyLength: Boolean get() = enabled && length != NEW_PIN_LEN

  fun matches(pin: String): Boolean {
    val stored = prefs.getString(HASH, null) ?: return true
    val salt = prefs.getString(SALT, null)?.fromHex() ?: return false
    // مقایسهٔ ثابت‌زمان — تفاوتِ زمانِ پاسخ هم خودش یک سرنخ است
    val given = hash(pin, salt)
    if (given.length != stored.length) return false
    var diff = 0
    for (i in stored.indices) diff = diff or (stored[i].code xor given[i].code)
    return diff == 0
  }

  private fun hash(pin: String, salt: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    var bytes = salt + pin.toByteArray(Charsets.UTF_8)
    repeat(50_000) { bytes = digest.digest(bytes) }
    return bytes.toHex()
  }

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

  private fun String.fromHex(): ByteArray =
    ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

  companion object {
    private const val HASH = "pin_hash"
    private const val SALT = "pin_salt"
    private const val LEN = "pin_len"

    /** رمزِ تازه شش رقم است */
    const val NEW_PIN_LEN = 6

    /** نسخه‌های قبل چهار رقم می‌گرفتند */
    const val LEGACY_PIN_LEN = 4
  }
}
