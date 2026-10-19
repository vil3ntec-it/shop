package ir.vil3ntec.tohid.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestKek
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 *  کلیدِ اصلیِ رمزِ دفتر روی اندروید — در Android Keystore (SEC-01).
 *
 *  کلیدِ AES-256 که هرگز از Keystore بیرون نمی‌آید؛ فقط کلیدِ دادهٔ دفتر
 *  (`AtRestKeyring`) را می‌پیچد و باز می‌کند — یک بار در هر اجرای برنامه.
 *
 *  ⚠️ عمداً **بدونِ** «اثر انگشت/قفلِ صفحه لازم است»: نسخهٔ خودکار و
 *  همگام‌سازی در پس‌زمینه دفتر را می‌خوانند، و گوشیِ بی‌قفلِ صفحه هم باید
 *  بتواند رمز را روشن کند.
 *
 *  ⚠️ Keystore در پشتیبانِ خودکارِ اندروید و انتقال به گوشیِ تازه نمی‌رود؛
 *  دفترِ رمزدارِ منتقل‌شده آن‌جا باز نمی‌شود و از سرور/پشتیبان برمی‌گردد
 *  (`ShopStore.unreadable`). همین در صفحهٔ امنیت گفته شده.
 *
 *  جایگزینِ کامپیوتری: `desktop/…/data/AtRestKeysDesk.kt`.
 */
object AtRestKeys {

  private const val ALIAS = "tohid_atrest_kek_v1"
  private const val PROVIDER = "AndroidKeyStore"

  /** کلید کجاست — برای صفحهٔ امنیت */
  const val WHERE: String = "Android Keystore"

  /** آیا کلید بیرون از فایل‌های برنامه نگه داشته می‌شود؟ */
  const val HARDWARE_BACKED: Boolean = true

  fun kek(@Suppress("UNUSED_PARAMETER") context: Context): AtRestKek = Keystore

  private object Keystore : AtRestKek {
    override val id: String = "android-keystore:$ALIAS"

    private fun existing(): SecretKey? {
      val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
      return (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun create(): SecretKey =
      KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
        init(
          KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build(),
        )
      }.generateKey()

    override fun wrap(dek: ByteArray): ByteArray = guard {
      val key = existing() ?: create()
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.ENCRYPT_MODE, key)
      c.iv + c.doFinal(dek)
    }

    override fun unwrap(blob: ByteArray): ByteArray = guard {
      if (blob.size <= IV) throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ پیچیده بریده است")
      val key = existing()
        ?: throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ Keystore روی این دستگاه نیست")
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, IV))
      c.doFinal(blob, IV, blob.size - IV)
    }

    /**
     *  رده‌بندیِ شکست: برچسبِ نخورده یعنی این کلیدِ پیچیده مالِ این KEK
     *  نیست (برای همیشه)؛ هر خطای دیگرِ Keystore گذرا شمرده می‌شود و هرگز
     *  کلیدِ تازه نمی‌سازد.
     */
    private inline fun <T> guard(block: () -> T): T = try {
      block()
    } catch (e: AtRestException) {
      throw e
    } catch (e: AEADBadTagException) {
      throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ Keystore با کلیددان نمی‌خواند", e)
    } catch (e: Exception) {
      throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, "Keystore جواب نداد: ${e.javaClass.simpleName}", e)
    }

    private const val IV = 12
  }
}
