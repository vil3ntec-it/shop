package ir.vil3ntec.tohid.core.atrest.platform

import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.backup.RecoveryPhrase
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
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
