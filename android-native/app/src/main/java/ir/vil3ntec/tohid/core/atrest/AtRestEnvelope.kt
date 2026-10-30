package ir.vil3ntec.tohid.core.atrest

import java.io.File
import java.io.FileInputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 *  پاکتِ رمزِ یک فایل روی دیسک — SEC-01 (رمزگذاریِ دادهٔ محلی)، فاز ۱.
 *
 *  ── قالب (نسخهٔ ۱) ────────────────────────────────────────────────────
 *     MAGIC (۸ بایت: 89 54 45 4E 43 0D 0A 1A — «‎\x89TENC\r\n\x1a‎»)
 *     VERSION (۱ بایت = ۱)
 *     KID_LEN (۱ بایت) + KID (اسکی؛ شناسهٔ کلیدِ داده)
 *     NONCE (۱۲ بایت)
 *     CIPHERTEXT ‖ TAG (AES-256-GCM، برچسبِ ۱۲۸ بیتی)
 *  AAD = همهٔ سرآیند (MAGIC تا NONCE): دست بردن در شناسهٔ کلید یا نسخه هم
 *  مثلِ دست بردن در خودِ داده رد می‌شود.
 *
 *  ── چرا این سرآیند ────────────────────────────────────────────────────
 *  بایتِ نخستِ `0x89` در UTF-8 هرگز آغازِ یک متن نیست، پس هیچ JSONی (دفتر،
 *  بایگانی، نسخهٔ خودکار) با پاکتِ رمز اشتباه گرفته نمی‌شود. خواننده‌ها
 *  **همیشه** هر دو را می‌خوانند (`isSealed`)؛ یعنی هر لحظه از کوچ — و هر
 *  ترکیبی از فایل‌های رمزدار و ساده — بی‌خطا باز می‌شود.
 *
 *  ⛔ هیچ چیزِ این‌جا به اندروید وابسته نیست (فقط `javax.crypto`) تا روی JVM
 *  آزموده شود و کامپیوتر هم همین را بکار ببرد.
 */
object AtRestEnvelope {

  val MAGIC: ByteArray = byteArrayOf(0x89.toByte(), 0x54, 0x45, 0x4E, 0x43, 0x0D, 0x0A, 0x1A)
  const val VERSION: Int = 1
  private const val NONCE_LEN = 12
  private const val TAG_BITS = 128
  private const val TAG_LEN = TAG_BITS / 8
  private const val TRANSFORM = "AES/GCM/NoPadding"

  /** آیا این بایت‌ها پاکتِ رمزند (فقط سرآیند دیده می‌شود)؟ */
  fun isSealed(bytes: ByteArray): Boolean {
    if (bytes.size < MAGIC.size) return false
    for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return false
    return true
  }

  /** آیا این فایل پاکتِ رمز است؟ فقط ۸ بایتِ نخست خوانده می‌شود. */
  fun isSealed(file: File): Boolean {
    if (!file.isFile || file.length() < MAGIC.size) return false
    val head = ByteArray(MAGIC.size)
    val n = runCatching { FileInputStream(file).use { readFully(it, head) } }.getOrDefault(0)
    return n == MAGIC.size && isSealed(head)
  }

  /** شناسهٔ کلیدِ یک پاکت، یا `null` اگر سرآیند خراب است */
  fun keyIdOf(sealed: ByteArray): String? = runCatching { parse(sealed).kid }.getOrNull()

  fun seal(plain: ByteArray, kid: String, key: SecretKey, random: SecureRandom): ByteArray {
    val kidBytes = kid.toByteArray(Charsets.US_ASCII)
    require(kidBytes.isNotEmpty() && kidBytes.size <= 255) { "شناسهٔ کلید نامعتبر است" }
    val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
    val headerLen = MAGIC.size + 2 + kidBytes.size + NONCE_LEN
    val cipher = Cipher.getInstance(TRANSFORM)
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
    /*
     *  کارایی (بازبینیِ #331): یک بافرِ از پیش‌اندازه‌گرفته — سرآیند و رمزنوشته مستقیم در
     *  همان. پیش‌تر `doFinal` یک کپیِ کامل و `header + …` کپیِ کاملِ دوم می‌ساخت؛ برای
     *  دفترِ چند مگابایتی روی گوشیِ کهنه یعنی دو برابر حافظه و زمانِ GC در هر ذخیره.
     */
    val out = ByteArray(headerLen + cipher.getOutputSize(plain.size))
    var at = 0
    MAGIC.copyInto(out, at); at += MAGIC.size
    out[at++] = VERSION.toByte()
    out[at++] = kidBytes.size.toByte()
    kidBytes.copyInto(out, at); at += kidBytes.size
    nonce.copyInto(out, at)
    cipher.updateAAD(out, 0, headerLen)
    val n = cipher.doFinal(plain, 0, plain.size, out, headerLen)
    return if (headerLen + n == out.size) out else out.copyOf(headerLen + n)
  }

  /**
   *  باز کردنِ پاکت.
   *
   *  @param keyFor کلیدِ همین شناسه، یا `null` اگر روی این دستگاه نیست
   *  @throws AtRestException با `kind` روشن — هیچ‌وقت «دادهٔ خالی» برنمی‌گردد
   */
  fun open(sealed: ByteArray, keyFor: (String) -> SecretKey?): ByteArray {
    val p = parse(sealed)
    val key = keyFor(p.kid) ?: throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ «${p.kid}» روی این دستگاه نیست")
    return try {
      val cipher = Cipher.getInstance(TRANSFORM)
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, p.nonceAt, NONCE_LEN))
      cipher.updateAAD(sealed, 0, p.bodyAt)
      cipher.doFinal(sealed, p.bodyAt, sealed.size - p.bodyAt)
    } catch (e: AEADBadTagException) {
      throw AtRestException(AtRestException.Kind.AUTH_FAILED, "فایلِ رمزدار دست‌خورده یا خراب است", e)
    } catch (e: GeneralSecurityException) {
      throw AtRestException(AtRestException.Kind.AUTH_FAILED, e.message ?: "باز نشد", e)
    }
  }

  private class Parsed(val kid: String, val nonceAt: Int, val bodyAt: Int)

  private fun parse(b: ByteArray): Parsed {
    if (!isSealed(b)) throw AtRestException(AtRestException.Kind.MALFORMED, "پاکتِ رمز نیست")
    if (b.size < MAGIC.size + 2) throw AtRestException(AtRestException.Kind.MALFORMED, "سرآیندِ ناقص")
    val ver = b[MAGIC.size].toInt() and 0xFF
    if (ver != VERSION) throw AtRestException(AtRestException.Kind.UNSUPPORTED, "نسخهٔ پاکت $ver شناخته نیست")
    val kidLen = b[MAGIC.size + 1].toInt() and 0xFF
    val kidAt = MAGIC.size + 2
    val nonceAt = kidAt + kidLen
    val bodyAt = nonceAt + NONCE_LEN
    if (kidLen == 0 || b.size < bodyAt + TAG_LEN) throw AtRestException(AtRestException.Kind.MALFORMED, "پاکتِ بریده")
    return Parsed(String(b, kidAt, kidLen, Charsets.US_ASCII), nonceAt, bodyAt)
  }

  private fun readFully(s: java.io.InputStream, buf: ByteArray): Int {
    var total = 0
    while (total < buf.size) {
      val r = s.read(buf, total, buf.size - total)
      if (r < 0) break
      total += r
    }
    return total
  }
}

/**
 *  شکستِ رمز — همیشه با نوعِ روشن، تا صفحه راستش را بگوید و هیچ نوشتنی
 *  فایلِ خوانده‌نشده را با دفترِ خالی رونویسی نکند.
 */
class AtRestException(val kind: Kind, message: String, cause: Throwable? = null) : java.io.IOException(message, cause) {
  enum class Kind {
    /** کلیدِ این فایل روی این دستگاه نیست (نصبِ تازه، انتقالِ اندروید، پاک شدنِ Keystore) */
    KEY_MISSING,

    /** کلید هست ولی همین حالا در دسترس نیست (خطای گذرای Keystore) — دوباره تلاش شود */
    KEY_UNAVAILABLE,

    /** برچسبِ GCM نخورد: کلیدِ اشتباه یا فایلِ دست‌خورده/خراب */
    AUTH_FAILED,

    /** سرآیندِ بریده یا بی‌معنی */
    MALFORMED,

    /** نسخهٔ تازه‌ترِ قالب — برنامه کهنه است */
    UNSUPPORTED,

    /** خطای دیسک (جا نیست، اجازه نیست) — فایلِ اصلی دست‌نخورده است */
    IO,
  }
}
