package ir.vil3ntec.tohid.sync

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 *  کدِ اشتراکِ آفلاین — بسته به یک کامپیوتر، یک بار.
 *
 *  خواستهٔ صاحب سامانه: «کد افلاین اشتراک باشه که کسی اگه نت هم نداشت بتونه
 *  از برنامه اشتراک بگیره؛ برای هر کامپیوتر یک ایدی… و روی همون کد فقط کار
 *  کنه و یک بار بیشتر هم استفاده نشه.»
 *
 *  ⛔ شکلِ دودویی مو‌به‌مو همان `server/src/lib/shop-offline-codes.js` است
 *  (۸۶ بایت، دامنهٔ امضای `TSOC1\0`). یک بایت عوض ⇒ همهٔ کدها باطل.
 *
 *  امضا با همان کلیدِ مجوز سنجیده می‌شود؛ بی کلیدِ خصوصیِ سرور هیچ کدی
 *  ساختنی نیست. کامپیوتر و حساب داخلِ بخشِ امضاشده‌اند، پس کدِ یک
 *  کامپیوتر روی کامپیوترِ دیگر نمی‌نشیند.
 */
object OfflineCode {

  private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
  private const val BODY = 22
  private const val TOTAL = BODY + 64
  private val DOMAIN = "TSOC1\u0000".toByteArray(Charsets.ISO_8859_1)
  private const val DAY = 86_400_000L
  //  روز از ۱۹۷۰ تا ۲۰۲۴/۰۱/۰۱ — ‎java.time‎ روی اندروید ۷ نیست
  private const val EPOCH_DAY = 19_723L
  private const val PERMANENT = 0xFFFF

  data class Parsed(
    val plan: String,          // sub | perm
    val machine: ByteArray,
    val account: ByteArray,
    val serial: String,
    val issuedAt: Long,
    val endsAt: Long,          // ۰ = دائمی
    val permanent: Boolean,
    val body: ByteArray,
    val sig: ByteArray,
  ) {
    val planTitle: String get() = if (permanent) "اشتراکِ دائمی (کدِ آفلاین)" else "اشتراک (کدِ آفلاین)"
  }

  sealed interface Verdict {
    data class Ok(val code: Parsed) : Verdict
    /** format | signature | computer | account | expired | no_key */
    data class Bad(val reason: String) : Verdict
  }

  /* ── شناسهٔ کامپیوتر ─────────────────────────────────────────────── */

  /** اثرِ انگشتِ سیستم ⇒ «XXXX-XXXX-XXXX-XXXX» (۱۵ نویسه + یک نویسهٔ سنجش) */
  fun computerId(fingerprint: String): String {
    val m = machineOf(fingerprint)
    val data = b32encode(m).substring(0, 15)
    val check = checkOf(data.map { charValue(it) })
    return (data + ALPHABET[check]).chunked(4).joinToString("-")
  }

  private fun machineOf(fingerprint: String): ByteArray {
    val h = sha256("tohid-shop|offline-pc|v1|$fingerprint".toByteArray(Charsets.UTF_8))
    val m = h.copyOfRange(0, 10)
    m[9] = (m[9].toInt() and 0xE0).toByte()
    return m
  }

  /** شناسهٔ کاربر ⇒ ۴ بایتِ «حساب» در کد — خالی ⇒ صفر */
  fun accountTag(userId: String): ByteArray =
    if (userId.isBlank()) ByteArray(4)
    else sha256("tohid-shop|offline-acct|v1|$userId".toByteArray(Charsets.UTF_8)).copyOfRange(0, 4)

  /* ── خودِ کد ──────────────────────────────────────────────────────── */

  fun parse(code: String): Parsed? {
    val buf = b32decode(code, TOTAL) ?: return null
    if (buf[0].toInt() != 1) return null
    val plan = when (buf[1].toInt()) { 1 -> "sub"; 3 -> "perm"; else -> return null }
    val issuedDay = u16(buf, 18)
    val endDay = u16(buf, 20)
    return Parsed(
      plan = plan,
      machine = buf.copyOfRange(2, 10),
      account = buf.copyOfRange(10, 14),
      serial = buf.copyOfRange(14, 18).joinToString("") { "%02x".format(it.toInt() and 0xFF) },
      issuedAt = (EPOCH_DAY + issuedDay) * DAY,
      endsAt = if (endDay == PERMANENT) 0 else (EPOCH_DAY + endDay) * DAY,
      permanent = endDay == PERMANENT,
      body = buf.copyOfRange(0, BODY),
      sig = buf.copyOfRange(BODY, TOTAL),
    )
  }

  /**
   *  سنجشِ کامل، بی اینترنت.
   *
   *  @param accountId حسابِ واردشده؛ خالی ⇒ فقط کدی که به هیچ حسابی بسته نیست
   *    یا هر کد (وقتی هنوز حسابی نیست، بعداً سرور می‌سنجد)
   */
  fun verify(code: String, publicKeySpki: String?, fingerprint: String, accountId: String, now: Long): Verdict {
    val p = parse(code) ?: return Verdict.Bad("format")
    if (publicKeySpki.isNullOrBlank()) return Verdict.Bad("no_key")
    val ok = runCatching {
      val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(License.decodeBase64(publicKeySpki.trim())))
      Signature.getInstance("SHA256withECDSA").run {
        initVerify(key)
        update(DOMAIN + p.body)
        verify(License.rawToDer(p.sig))
      }
    }.getOrDefault(false)
    if (!ok) return Verdict.Bad("signature")
    if (!machineOf(fingerprint).copyOfRange(0, 8).contentEquals(p.machine)) return Verdict.Bad("computer")
    val bound = p.account.any { it.toInt() != 0 }
    if (bound && accountId.isNotBlank() && !accountTag(accountId).contentEquals(p.account)) return Verdict.Bad("account")
    if (!p.permanent && now >= p.endsAt) return Verdict.Bad("expired")
    return Verdict.Ok(p)
  }

  /** پیامِ فارسیِ هر دلیل */
  fun reasonText(reason: String): String = when (reason) {
    "format" -> "کد کامل یا درست نیست — همهٔ ۱۳۸ نویسه را بچسبانید"
    "signature" -> "این کد از سرورِ «فروشگاه من» نیست یا دست خورده است"
    "computer" -> "این کد برای کامپیوترِ دیگری ساخته شده است"
    "account" -> "این کد برای حسابِ دیگری ساخته شده است"
    "expired" -> "مهلتِ این کد تمام شده است"
    "used" -> "این کد یک بار در این برنامه استفاده شده است"
    "no_key" -> "کلیدِ مجوز در این نسخه نیست؛ یک بار با اینترنت وارد شوید"
    else -> "کد پذیرفته نشد"
  }

  /* ── base32ِ کراکفورد ─────────────────────────────────────────────── */

  private fun b32encode(buf: ByteArray): String {
    val sb = StringBuilder()
    var bits = 0
    var value = 0
    for (b in buf) {
      value = (value shl 8) or (b.toInt() and 0xFF); bits += 8
      while (bits >= 5) { sb.append(ALPHABET[(value ushr (bits - 5)) and 31]); bits -= 5 }
      value = value and ((1 shl bits) - 1)
    }
    if (bits > 0) sb.append(ALPHABET[(value shl (5 - bits)) and 31])
    return sb.toString()
  }

  private fun charValue(ch: Char): Int {
    val c = ch.uppercaseChar()
    if (c == 'O') return 0
    if (c == 'I' || c == 'L') return 1
    return ALPHABET.indexOf(c)
  }

  /** فقط حرف و رقم؛ رقمِ فارسی و عربی هم */
  private fun clean(raw: String): String = buildString {
    for (ch in raw) {
      val fa = "۰۱۲۳۴۵۶۷۸۹".indexOf(ch)
      val ar = "٠١٢٣٤٥٦٧٨٩".indexOf(ch)
      when {
        fa >= 0 -> append(('0' + fa))
        ar >= 0 -> append(('0' + ar))
        ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z' -> append(ch)
      }
    }
  }

  private fun b32decode(str: String, byteLen: Int): ByteArray? {
    val s = clean(str)
    if (s.length != (byteLen * 8 + 4) / 5) return null
    val out = ByteArray(byteLen)
    var bits = 0
    var value = 0
    var i = 0
    for (ch in s) {
      val v = charValue(ch)
      if (v < 0) return null
      value = (value shl 5) or v; bits += 5
      if (bits >= 8) {
        if (i < byteLen) out[i++] = ((value ushr (bits - 8)) and 0xFF).toByte()
        bits -= 8
        value = value and ((1 shl bits) - 1)
      }
    }
    if (i != byteLen || value != 0) return null
    return out
  }

  private fun checkOf(values: List<Int>): Int {
    var sum = 0
    values.forEachIndexed { i, v -> sum += (i + 1) * v }
    return sum % 29
  }

  private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

  private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

  /** همان کد بی خط‌تیره و بزرگ — برای مقایسه و «یک بار» */
  fun normalized(code: String): String = clean(code).uppercase()
}
