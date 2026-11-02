package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestKek
import ir.vil3ntec.tohid.ios.keychain.Keychain
import ir.vil3ntec.tohid.ios.keychain.KeychainException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 *  جایگزینِ iOS ِ `data/AtRestKeys.kt` (همان API؛ اندروید: Android Keystore، کامپیوتر: ‎AtRestKeysDesk‎).
 *
 *  KEK (کلیدِ پیچاندنِ کلیدِ دفتر) ۲۵۶ بیتِ تصادفیِ `SecRandomCopyBytes` است و در کلیددانِ iOS با
 *  «فقط همین دستگاه» می‌ماند (‎ios/keychain/Keychain.kt‎)؛ پیچاندن همان AES-GCM ِ اندروید است:
 *  `IV(12) ‖ رمزنوشته ‖ برچسب`. پس `AtRestKeyring` و پاکتِ دفتر (`AtRestEnvelope`) بی هیچ تغییری روی
 *  آیفون کار می‌کنند.
 *  ⛔ نبودِ کلید ⇐ KEY_MISSING (دفتر از سرور بازسازی می‌شود)، کلیددانِ قفل/ناپاسخ ⇐ KEY_UNAVAILABLE
 *  (دفتر دست نمی‌خورد، دوباره تلاش)؛ هرگز کلیدِ تازه روی کلیدِ موجود.
 */
object AtRestKeys {
  private const val ACCOUNT = "atrest-kek-v1"
  private const val PREFIX = "ios-keychain:"
  private const val IV = 12

  const val WHERE: String = "کلیددانِ iOS (Keychain)"

  /** کلید در کلیددانِ رمزشدهٔ سیستم است، ولی برای کار به حافظهٔ برنامه می‌آید (نه مثلِ Keystore ِ سخت‌افزاری) */
  const val HARDWARE_BACKED: Boolean = false

  fun note(): String =
    "کلید در $WHERE است، فقط روی همین آیفون. با پشتیبانِ iCloud یا انتقال به گوشیِ تازه نمی‌رود؛ " +
      "گوشیِ تازه دفتر را از سرور می‌گیرد."

  fun kek(@Suppress("UNUSED_PARAMETER") context: Context): AtRestKek = KeychainKek

  private object KeychainKek : AtRestKek {
    override val id: String get() = PREFIX + ACCOUNT

    private fun existing(): ByteArray? = Keychain.read(ACCOUNT)

    private fun create(): ByteArray {
      val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
      //  دو رشته با هم: اگر دیگری زودتر نوشت، همان را برمی‌داریم
      return if (Keychain.add(ACCOUNT, fresh)) fresh else existing() ?: throw KeychainException(0, "کلید نوشته نشد")
    }

    override fun wrap(dek: ByteArray): ByteArray = guard {
      val kek = existing() ?: create()
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"))
      c.iv!! + c.doFinal(dek)
    }

    override fun unwrap(blob: ByteArray, kekId: String): ByteArray = guard {
      if (!kekId.startsWith(PREFIX)) throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ اصلیِ دستگاه عوض شده ($kekId)")
      if (blob.size <= IV) throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ پیچیده بریده است")
      val kek = existing() ?: throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ کلیددان روی این دستگاه نیست")
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, blob, 0, IV))
      c.doFinal(blob, IV, blob.size - IV)
    }

    private inline fun <T> guard(block: () -> T): T = try {
      block()
    } catch (e: AtRestException) {
      throw e
    } catch (e: AEADBadTagException) {
      throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ کلیددان با کلیددانِ دفتر نمی‌خواند", e)
    } catch (e: Exception) {
      throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, "کلیددانِ iOS جواب نداد: ${e.message}", e)
    }
  }
}
