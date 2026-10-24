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
    private const val PREFIX = "android-keystore:"

    /** KEKِ امروز: تازه‌ترین نام (`ALIAS`، یا `ALIAS_r<زمان>` پس از جایگزینی) */
    override val id: String get() = PREFIX + current()

    private fun keystore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun current(): String =
      keystore().aliases().toList().filter { it == ALIAS || it.startsWith("${ALIAS}_r") }
        .maxByOrNull { it.removePrefix(ALIAS).removePrefix("_r").toLongOrNull() ?: 0L } ?: ALIAS

    private fun existing(alias: String): SecretKey? =
      (keystore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun create(alias: String): SecretKey =
      KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
        init(
          KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
      val alias = current()
      try {
        seal(existing(alias) ?: create(alias), dek)
      } catch (e: Exception) {
        if (!permanent(e)) throw e
        /*
         *  ⛔ هیچ کلیدی پاک نمی‌شود (بازبینیِ #331): کلیدِ همیشه‌ازکارافتاده سرِ جایش می‌ماند —
         *  اگر معلوم شود گذرا بود، کلیددان‌های کنارگذاشته هنوز با آن باز می‌شوند — و کلیدِ تازه
         *  با **نامِ تازه** ساخته می‌شود. `wrap` فقط هنگامِ ساختنِ کلیددانِ تازه صدا زده می‌شود.
         */
        seal(create("${ALIAS}_r${System.currentTimeMillis()}"), dek)
      }
    }

    override fun unwrap(blob: ByteArray, kekId: String): ByteArray = guard {
      if (!kekId.startsWith(PREFIX)) throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ اصلیِ دستگاه عوض شده ($kekId)")
      if (blob.size <= IV) throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ پیچیده بریده است")
      val key = existing(kekId.removePrefix(PREFIX))
        ?: throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ Keystore روی این دستگاه نیست")
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, IV))
      c.doFinal(blob, IV, blob.size - IV)
    }

    /**
     *  رده‌بندیِ شکست: برچسبِ نخورده یعنی این کلیدِ پیچیده مالِ این KEK نیست؛
     *  `KeyPermanentlyInvalidatedException` و «Invalid key blob» یعنی KEK برای همیشه از کار
     *  افتاده (`AtRestKeyFailure`) — `KEY_MISSING`؛ کلیددان با این حال فقط پس از تکرار در اجرای
     *  دوم `Lost` می‌شود. هر خطای دیگرِ Keystore (از جمله `UnrecoverableKeyException`) گذرا
     *  شمرده می‌شود و هرگز کلیدِ تازه نمی‌سازد.
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
