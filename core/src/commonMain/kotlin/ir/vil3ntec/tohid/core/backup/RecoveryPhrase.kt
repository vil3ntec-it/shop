package ir.vil3ntec.tohid.core.backup

import ir.vil3ntec.tohid.core.platform.secureRandomBytes
import ir.vil3ntec.tohid.core.platform.sha256

/*
 *  OPS-02 / §۱۰.۳ — «عبارتِ بازیابیِ فروشگاه»: کلیدِ پشتیبان‌های دستگاه، **نه** کلیدِ دستگاه.
 *
 *  پشتیبانی که با کلیدِ Keystore/DPAPI رمز شود با نصبِ دوبارهٔ ویندوز یا پاک شدنِ دادهٔ اندروید
 *  دیگر باز نمی‌شود. پس پشتیبانِ دستگاه با کلیدی رمز می‌شود که از این عبارت ساخته می‌شود؛ عبارت
 *  یک بار به صاحب دکان نشان داده و چاپ می‌شود و جای دیگری نمی‌ماند.
 *
 *  ── شکل ──────────────────────────────────────────────────────────────
 *  ۲۵ نویسهٔ Crockford Base32 در پنج دستهٔ پنج‌تایی (`K7QM2-…`): ۲۴ نویسهٔ تصادفی (۱۲۰ بیت از
 *  CSPRNG ِ سیستم) + یک نویسهٔ وارسی (SHA-256)، تا غلطِ تایپی پیش از ساختنِ کلید (که چند ثانیه
 *  طول می‌کشد) گفته شود. خواندن با هر نوع نوشتن: کوچک/بزرگ، فاصله و خط‌تیره، رقمِ فارسی/عربی،
 *  و O⇐0، I و L⇐1 (قاعدهٔ Crockford).
 *
 *  ── شناسهٔ کلید در پاکت ──────────────────────────────────────────────
 *  پشتیبانِ رمزدار همان پاکتِ `AtRestEnvelope` است (MAGIC، نسخه، KID، nonce، AES-256-GCM)؛ KID =
 *  `rp1.<تکرار>.<نمک>` (نمکِ ۱۶ بایتی، base64url بی `=`). پس پارامترهای ساختِ کلید در خودِ فایل
 *  و زیرِ AAD اند: دستگاهِ تازه فقط عبارت لازم دارد. [ITERATIONS_MIN]..[ITERATIONS_MAX] ⇒ فایلِ
 *  دست‌کاری‌شده نمی‌تواند برنامه را با میلیاردها تکرار قفل کند.
 *
 *  فقط منطق؛ ساختنِ کلید (PBKDF2-HMAC-SHA256) و پاکت در
 *  `android-native/…/core/atrest/platform/PhraseBackup.kt`.
 */
object RecoveryPhrase {

  const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
  const val BODY = 24
  const val LENGTH = BODY + 1
  const val GROUP = 5

  const val KID_PREFIX = "rp1."
  const val SALT_BYTES = 16
  /** OWASP ۲۰۲۳ برای PBKDF2-HMAC-SHA256؛ یک بار هنگامِ ساختنِ عبارت و یک بار هنگامِ بازیابی */
  const val ITERATIONS = 600_000
  const val ITERATIONS_MIN = 100_000
  const val ITERATIONS_MAX = 5_000_000

  private val KID = Regex("^rp1\\.(\\d{6,7})\\.([A-Za-z0-9_-]{22})$")
  private const val B64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

  /** عبارتِ تازه، در شکلِ نمایش (`XXXXX-XXXXX-…`) */
  fun generate(random: (Int) -> ByteArray = ::secureRandomBytes): String {
    //  ۱۵ بایت = ۱۲۰ بیت = دقیقاً ۲۴ نویسهٔ پنج‌بیتی
    val bytes = random(15)
    require(bytes.size == 15)
    val body = StringBuilder(BODY)
    var acc = 0
    var bits = 0
    for (b in bytes) {
      acc = (acc shl 8) or (b.toInt() and 0xFF)
      bits += 8
      while (bits >= 5) {
        bits -= 5
        body.append(ALPHABET[(acc shr bits) and 31])
      }
      acc = acc and ((1 shl bits) - 1)
    }
    return format(body.toString() + check(body.toString()))
  }

  /** نویسهٔ وارسی: SHA-256 ِ بدنه، بایتِ اول پیمانهٔ ۳۲ */
  fun check(body: String): Char = ALPHABET[(sha256(body.encodeToByteArray())[0].toInt() and 0xFF) % 32]

  /**
   *  نوشتهٔ کاربر ⇒ شکلِ استاندارد (۲۵ نویسه، بی خط‌تیره)؛ نادرست (طول، نویسهٔ بیگانه، وارسی) ⇒ null.
   */
  fun normalize(input: String): String? {
    val out = StringBuilder(LENGTH)
    for (raw in input) {
      val c = when (raw) {
        in '\u06F0'..'\u06F9' -> '0' + (raw - '\u06F0')
        in '\u0660'..'\u0669' -> '0' + (raw - '\u0660')
        else -> raw.uppercaseChar()
      }
      when {
        c == ' ' || c == '-' || c == '\u200C' || c == '\u00A0' || c == '\t' || c == '\n' -> continue
        c == 'O' -> out.append('0')
        c == 'I' || c == 'L' -> out.append('1')
        c in ALPHABET -> out.append(c)
        else -> return null
      }
      if (out.length > LENGTH) return null
    }
    if (out.length != LENGTH) return null
    val s = out.toString()
    return if (check(s.substring(0, BODY)) == s[BODY]) s else null
  }

  /** شکلِ استاندارد ⇒ پنج دستهٔ پنج‌تایی برای نمایش و چاپ */
  fun format(canonical: String): String = canonical.chunked(GROUP).joinToString("-")

  /** KID ِ پاکت برای این نمک و تکرار */
  fun kidOf(salt: ByteArray, iterations: Int = ITERATIONS): String {
    require(salt.size == SALT_BYTES) { "نمک ۱۶ بایت" }
    require(iterations in ITERATIONS_MIN..ITERATIONS_MAX) { "تکرار بیرون از بازه" }
    return "$KID_PREFIX$iterations.${b64url(salt)}"
  }

  /** پارامترهای KID ِ عبارت؛ KID ِ دیگر (کلیدِ دستگاه) یا ناجور یا بیرون از بازه ⇒ null */
  data class KdfParams(val iterations: Int, val salt: ByteArray)

  fun parseKid(kid: String): KdfParams? {
    val m = KID.matchEntire(kid) ?: return null
    val iterations = m.groupValues[1].toIntOrNull() ?: return null
    if (iterations !in ITERATIONS_MIN..ITERATIONS_MAX) return null
    val salt = unb64url(m.groupValues[2]) ?: return null
    return if (salt.size == SALT_BYTES) KdfParams(iterations, salt) else null
  }

  private fun b64url(b: ByteArray): String {
    val sb = StringBuilder()
    var i = 0
    while (i < b.size) {
      val n = (b[i].toInt() and 0xFF shl 16) or
        ((if (i + 1 < b.size) b[i + 1].toInt() and 0xFF else 0) shl 8) or
        (if (i + 2 < b.size) b[i + 2].toInt() and 0xFF else 0)
      val chars = minOf(4, ((b.size - i) * 8 + 5) / 6)
      for (k in 0 until chars) sb.append(B64URL[(n shr (18 - 6 * k)) and 63])
      i += 3
    }
    return sb.toString()
  }

  private fun unb64url(s: String): ByteArray? {
    val out = ArrayList<Byte>()
    var acc = 0
    var bits = 0
    for (c in s) {
      val v = B64URL.indexOf(c)
      if (v < 0) return null
      acc = (acc shl 6) or v
      bits += 6
      if (bits >= 8) {
        bits -= 8
        out += ((acc shr bits) and 0xFF).toByte()
      }
      acc = acc and ((1 shl bits) - 1)
    }
    return out.toByteArray()
  }
}
