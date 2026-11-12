package ir.vil3ntec.tohid.core.atrest.platform

import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.backup.RecoveryPhrase
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 *  OPS-02 / §۱۰.۳ — پشتیبانِ دستگاه با «عبارتِ بازیابیِ فروشگاه»، نه با کلیدِ دستگاه.
 *
 *  همان پاکتِ `AtRestEnvelope` (AES-256-GCM، سرآیند زیرِ AAD) با KID ِ `rp1.<تکرار>.<نمک>`
 *  (`core/backup/RecoveryPhrase`)؛ کلید = PBKDF2-HMAC-SHA256(عبارتِ استاندارد، نمک، تکرار، ۲۵۶ بیت).
 *  پس خواننده‌های امروز (`isSealed`) فایل را رمزدار می‌شناسند و دستگاهِ تازه فقط عبارت لازم دارد.
 *
 *  ساختنِ کلید کُند است (عمداً، در برابرِ حدس زدن): [deriveKey] یک بار هنگامِ ساختنِ عبارت و یک
 *  بار هنگامِ بازیابی؛ پشتیبان‌های خودکار با کلیدِ ساخته‌شده ([seal] با `key`) رمز می‌شوند.
 *  فقط `javax.crypto` ⇒ اندروید و کامپیوتر هر دو؛ آزمون روی JVM.
 */
object PhraseBackup {

  /** کلیدِ AES از عبارتِ استاندارد (۲۵ نویسه، `RecoveryPhrase.normalize`) */
  fun deriveKey(canonical: String, salt: ByteArray, iterations: Int): SecretKey {
    require(canonical.length == RecoveryPhrase.LENGTH) { "عبارتِ استاندارد لازم است" }
    require(iterations in RecoveryPhrase.ITERATIONS_MIN..RecoveryPhrase.ITERATIONS_MAX) { "تکرار بیرون از بازه" }
    val spec = PBEKeySpec(canonical.toCharArray(), salt, iterations, 256)
    try {
      val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
      return SecretKeySpec(raw, "AES")
    } finally {
      spec.clearPassword()
    }
  }

  /** کلید و KID ِ تازه برای یک عبارت (نمکِ تازه) — هنگامِ ساختنِ عبارت */
  fun newKey(canonical: String, random: SecureRandom, iterations: Int = RecoveryPhrase.ITERATIONS): Pair<String, SecretKey> {
    val salt = ByteArray(RecoveryPhrase.SALT_BYTES).also { random.nextBytes(it) }
    return RecoveryPhrase.kidOf(salt, iterations) to deriveKey(canonical, salt, iterations)
  }

  /** رمز کردنِ یک پشتیبان با کلیدِ ساخته‌شده از عبارت */
  fun seal(plain: ByteArray, kid: String, key: SecretKey, random: SecureRandom): ByteArray {
    require(RecoveryPhrase.parseKid(kid) != null) { "KID ِ عبارتِ بازیابی نیست" }
    return AtRestEnvelope.seal(plain, kid, key, random)
  }

  /**
   *  همان پاکتِ [seal]، جریانی (پشتیبانِ دستی/پوشه، `BackupSeal`): سرآیند همین حالا در [out] نوشته می‌شود و هر چه در
   *  جریانِ برگشتی نوشته شود رمز می‌شود؛ `close()` برچسب را می‌نویسد و [out] را می‌بندد (هر خطا بالا می‌رود). بایت‌به‌بایت
   *  همان خروجیِ [seal] با همان [random] (`BackupSealDrillTest`) — پس همان قراردادِ `AtRestEnvelope` و `phrase-backup.js`؛
   *  پشتیبانِ کامل با عکس‌ها بی کپیِ دوم در حافظه نوشته می‌شود (روی اندروید Conscrypt شاید رمزنوشته را تا `close` نگه دارد).
   *  باز کردن همان [open] است.
   */
  fun sealing(out: OutputStream, kid: String, key: SecretKey, random: SecureRandom): OutputStream {
    require(RecoveryPhrase.parseKid(kid) != null) { "KID ِ عبارتِ بازیابی نیست" }
    val kidBytes = kid.toByteArray(Charsets.US_ASCII)
    //  همان قالبِ `AtRestEnvelope.seal`: MAGIC ‖ نسخه ‖ طولِ KID ‖ KID ‖ nonce ِ ۱۲ — همه زیرِ AAD؛ برچسبِ ۱۲۸ بیتی
    val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
    val header = AtRestEnvelope.MAGIC + byteArrayOf(AtRestEnvelope.VERSION.toByte(), kidBytes.size.toByte()) + kidBytes + nonce
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
    cipher.updateAAD(header)
    out.write(header)
    return Sealing(out, cipher)
  }

  private const val NONCE_LEN = 12
  private const val TAG_BITS = 128

  /**
   *  ⛔ نه `javax.crypto.CipherOutputStream`: آن خطای `flush` ِ پس از برچسب را نادیده می‌گیرد ⇒ دیسکِ پُر پاکتِ بی‌برچسب را
   *  «نوشته‌شده» نشان می‌داد. این‌جا هر خطای نوشتن و `doFinal` بالا می‌رود و مقصد در هر حال بسته می‌شود.
   */
  private class Sealing(private val out: OutputStream, private val cipher: Cipher) : OutputStream() {
    private var closed = false
    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
    override fun write(b: ByteArray, off: Int, len: Int) {
      check(!closed) { "جریانِ رمز بسته است" }
      if (len == 0) return
      cipher.update(b, off, len)?.let { if (it.isNotEmpty()) out.write(it) }
    }
    override fun flush() = out.flush()
    override fun close() {
      if (closed) return
      closed = true
      try {
        out.write(cipher.doFinal())
        out.flush()
      } finally {
        out.close()
      }
    }
  }

  /**
   *  باز کردن با عبارتی که کاربر نوشته (هر شکلی: کوچک/بزرگ، خط‌تیره، رقمِ فارسی).
   *  @throws AtRestException `KEY_MISSING`: عبارت نادرست است یا فایل با کلیدِ دستگاه رمز شده؛
   *    `AUTH_FAILED`: عبارتِ دیگر یا فایلِ دست‌خورده؛ `MALFORMED`: پاکتِ رمز نیست.
   */
  fun open(sealed: ByteArray, typedPhrase: String): ByteArray {
    val canonical = RecoveryPhrase.normalize(typedPhrase)
      ?: throw AtRestException(AtRestException.Kind.KEY_MISSING, "عبارتِ بازیابی درست نوشته نشده است")
    return AtRestEnvelope.open(sealed) { kid ->
      RecoveryPhrase.parseKid(kid)?.let { deriveKey(canonical, it.salt, it.iterations) }
    }
  }
}
