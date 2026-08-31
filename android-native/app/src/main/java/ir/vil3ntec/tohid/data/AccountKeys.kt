package ir.vil3ntec.tohid.data

import android.content.Context
import java.security.SecureRandom

/**
 *  کلیدهای حساب — دقیقاً همان دو کلیدی که نسخهٔ وب می‌سازد.
 *
 *   ۱) **کلید حساب** (`TSH-…`) — شناسهٔ یکتای هر حساب. فروشنده با همین
 *      می‌فهمد اشتراک را روی کدام حساب فعال کند.
 *   ۲) **کد شاگرد** (`SHG-…`) — صاحب دکان این را به شاگردهایش می‌دهد تا
 *      در صفحهٔ ورود بزنند و روی همان دکان بیایند.
 *
 *  الفبا، طولِ دسته‌ها و بخشِ زمانیِ ابتدای کلید مو‌به‌مو با
 *  `license/license-client.js` یکی است؛ کلیدی که در گوشی ساخته می‌شود
 *  باید در سایت هم شناخته شود.
 *
 *  چرا تکراری نمی‌شود: هر کلید از بایت‌های `SecureRandom` می‌آید و شش
 *  کاراکترِ اولش از زمانِ ساخت است — دو دستگاهی که هم‌زمان کلید نمی‌سازند،
 *  هرگز به هم نمی‌رسند.
 */
object AccountKeys {

  private const val PREFS = "tohid"
  private const val API_KEY = "tohid-account-key-v1"
  private const val STAFF_KEY = "tohid-staff-code-v1"

  // بدون I/O/0/1 تا موقع خواندن و گفتن اشتباه نشود
  private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

  val API_RE = Regex("^TSH-[A-Z0-9]{5}(-[A-Z0-9]{5}){4}$")
  val STAFF_RE = Regex("^SHG-[A-Z0-9]{5}(-[A-Z0-9]{5}){2}$")

  private val random = SecureRandom()

  private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun randomChars(n: Int): String {
    val bytes = ByteArray(n)
    random.nextBytes(bytes)
    return buildString { bytes.forEach { append(ALPHABET[(it.toInt() and 0xFF) % ALPHABET.length]) } }
  }

  /** زمانِ ساخت، فشرده در شش کاراکتر */
  private fun timeChunk(): String {
    var t = System.currentTimeMillis()
    var out = ""
    repeat(6) {
      out = ALPHABET[(t % ALPHABET.length).toInt()] + out
      t /= ALPHABET.length
    }
    return out
  }

  private fun group(text: String, size: Int): String =
    text.chunked(size).joinToString("-")

  /** کلید حساب — ۲۵ کاراکتر (۶ زمانی + ۱۹ تصادفی) در پنج دستهٔ پنج‌تایی */
  fun newApiKey(): String = "TSH-" + group(timeChunk() + randomChars(19), 5)

  /** کد شاگرد — ۱۵ کاراکتر (۶ زمانی + ۹ تصادفی) در سه دستهٔ پنج‌تایی */
  fun newStaffCode(): String = "SHG-" + group(timeChunk() + randomChars(9), 5)

  fun apiKey(context: Context): String {
    val stored = prefs(context).getString(API_KEY, "") ?: ""
    if (API_RE.matches(stored)) return stored
    val key = newApiKey()
    prefs(context).edit().putString(API_KEY, key).apply()
    return key
  }

  /** کد شاگردِ ذخیره‌شده، یا اگر نبود یکی تازه */
  fun staffCode(context: Context): String {
    val stored = prefs(context).getString(STAFF_KEY, "") ?: ""
    if (STAFF_RE.matches(stored)) return stored
    return rotateStaffCode(context)
  }

  /**
   *  هر دو کلید را پاک می‌کند — وقتی حسابِ دیگری روی همین گوشی وارد شود.
   *
   *  این کلیدها به حساب بسته‌اند، نه به دستگاه. اگر می‌ماندند، نفرِ تازه
   *  کلیدِ حساب و کدِ شاگردِ نفرِ قبلی را در تنظیمات می‌دید. دفعهٔ بعد که
   *  لازم شوند، برای خودش ساخته می‌شوند.
   */
  fun forget(context: Context) {
    prefs(context).edit().remove(API_KEY).remove(STAFF_KEY).apply()
  }

  /** کد تازه می‌سازد — وقتی صاحب دکان بخواهد کد قبلی دیگر کار نکند */
  fun rotateStaffCode(context: Context): String {
    val code = newStaffCode()
    prefs(context).edit().putString(STAFF_KEY, code).apply()
    return code
  }
}
