package ir.vil3ntec.tohid.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestKeyFailure
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
 *  پس وقتی رمز در کار است دفترِ رمزدار، کلیددان و حالِ همگام‌سازی هم با هم
 *  نمی‌روند (`TohidBackupAgent`) و گوشیِ تازه دفتر را از سرور می‌گیرد. دفترِ
 *  رمزداری که به هر راهِ دیگری بی کلید رسیده باشد «بازسازی از سرور» دارد
 *  (`ShopStore.rebuildFromServer`). همین در صفحهٔ امنیت گفته شده.
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

  /** جای کلید و آنچه از آن برمی‌آید — یک جمله برای صفحهٔ امنیت */
  fun note(): String =
    "کلید در $WHERE است و از برنامه بیرون نمی‌آید. با پشتیبانِ خودکار یا انتقالِ اندروید به گوشیِ تازه نمی‌رود؛ " +
      "برای همین وقتی رمز روشن است، دفتر و حالِ همگام‌سازی هم در آن پشتیبان نمی‌روند و گوشیِ تازه دفتر را از سرور می‌گیرد."

  fun kek(@Suppress("UNUSED_PARAMETER") context: Context): AtRestKek = Keystore

  private object Keystore : AtRestKek {
    override val id: String = "android-keystore:$ALIAS"

    private fun keystore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existing(): SecretKey? =
      (keystore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

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

    private fun seal(key: SecretKey, dek: ByteArray): ByteArray {
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.ENCRYPT_MODE, key)
      return c.iv + c.doFinal(dek)
    }

    override fun wrap(dek: ByteArray): ByteArray = guard {
      try {
        seal(existing() ?: create(), dek)
      } catch (e: Exception) {
        if (!permanent(e)) throw e
        /*
         *  ⛔ `wrap` فقط هنگامِ ساختنِ کلیددانِ **تازه** صدا زده می‌شود (کلیددانِ قبلی کنار
         *  رفته است). KEKی که برای همیشه از کار افتاده دیگر هیچ چیزی را باز نمی‌کند؛ اگر
         *  همین‌جا نمی‌ماند، رمز تا ابد «در دسترس نیست» می‌ماند. جایش کلیدِ تازه می‌نشیند.
         */
        runCatching { keystore().deleteEntry(ALIAS) }
        seal(create(), dek)
      }
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
     *  رده‌بندیِ شکست: برچسبِ نخورده یعنی این کلیدِ پیچیده مالِ این KEK نیست؛
     *  `KeyPermanentlyInvalidatedException`، `UnrecoverableKeyException` و «invalid key blob»
     *  یعنی KEK برای همیشه از کار افتاده (بازبینیِ #331) — هر سه `KEY_MISSING`، تا صفحه راهِ
     *  «بازسازی از سرور» را پیشنهاد کند. هر خطای دیگرِ Keystore گذرا شمرده می‌شود و هرگز
     *  کلیدِ تازه نمی‌سازد.
     */
    private fun permanent(e: Throwable): Boolean =
      e is KeyPermanentlyInvalidatedException || AtRestKeyFailure.isPermanent(e)

    private inline fun <T> guard(block: () -> T): T = try {
      block()
    } catch (e: AtRestException) {
      throw e
    } catch (e: AEADBadTagException) {
      throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ Keystore با کلیددان نمی‌خواند", e)
    } catch (e: Exception) {
      if (permanent(e)) {
        throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ Keystore برای همیشه از کار افتاده: ${e.javaClass.simpleName}", e)
      }
      throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, "Keystore جواب نداد: ${e.javaClass.simpleName}", e)
    }

    private const val IV = 12
  }
}
