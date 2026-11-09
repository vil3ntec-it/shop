package ir.vil3ntec.tohid.customer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 *  نگه‌دارِ رمزشدهٔ نشستِ حسابِ مشتری: AES-256-GCM با کلیدِ Android Keystore (کلید هرگز از Keystore بیرون نمی‌آید)؛
 *  متنِ رمزشده در SharedPreferences ِ جدای همین برنامه (`allowBackup=false`). هر خطا ⇒ هیچ (نشست فقط در همین اجرا).
 */
class SecretsAndroid(context: Context) : ShopperKeyValue {
  private val prefs = context.applicationContext.getSharedPreferences("shopper.secret", Context.MODE_PRIVATE)

  override fun get(key: String): String? = try {
    prefs.getString(key, null)?.let { open(it) }
  } catch (_: Exception) {
    null
  }

  override fun put(key: String, value: String?) {
    val sealed = try {
      value?.let { seal(it) }
    } catch (_: Exception) {
      null
    }
    prefs.edit().apply { if (sealed == null) remove(key) else putString(key, sealed) }.apply()
  }

  private fun key(): SecretKey {
    val ks = KeyStore.getInstance(STORE).apply { load(null) }
    (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
    gen.init(
      KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build(),
    )
    return gen.generateKey()
  }

  private fun seal(plain: String): String {
    val c = Cipher.getInstance(MODE)
    c.init(Cipher.ENCRYPT_MODE, key())
    val out = c.iv + c.doFinal(plain.encodeToByteArray())
    return Base64.encodeToString(out, Base64.NO_WRAP)
  }

  private fun open(text: String): String? {
    val b = Base64.decode(text, Base64.NO_WRAP)
    if (b.size <= IV) return null
    val c = Cipher.getInstance(MODE)
    c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, IV))
    return c.doFinal(b, IV, b.size - IV).decodeToString()
  }

  private companion object {
    const val STORE = "AndroidKeyStore"
    const val ALIAS = "shopper.session"
    const val MODE = "AES/GCM/NoPadding"
    const val IV = 12
  }
}
