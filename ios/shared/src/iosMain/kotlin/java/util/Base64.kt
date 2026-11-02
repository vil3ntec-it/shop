package java.util

import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.encoding.Base64 as KBase64

/*
 *  shimِ iOS ِ `java.util.Base64` روی `kotlin.io.encoding.Base64` — همان رفتارِ JDK: رمزگذارِ پایه با «=» و
 *  بی شکستِ سطر، `withoutPadding()` بی «=»، رمزگشای پایه/URL «=» را اختیاری می‌پذیرد و نویسهٔ بیگانه
 *  ⇐ IllegalArgumentException؛ MIME نویسه‌های بیرون از الفبا را نادیده می‌گیرد.
 */
@OptIn(ExperimentalEncodingApi::class)
object Base64 {
  class Encoder internal constructor(private val k: KBase64) {
    fun encode(src: ByteArray): ByteArray = k.encodeToByteArray(src)
    fun encodeToString(src: ByteArray): String = k.encode(src)
    fun withoutPadding(): Encoder = Encoder(k.withPadding(KBase64.PaddingOption.ABSENT))
  }

  class Decoder internal constructor(private val k: KBase64) {
    fun decode(src: String): ByteArray = try { k.decode(src) } catch (e: IllegalArgumentException) { throw IllegalArgumentException(e.message) }
    fun decode(src: ByteArray): ByteArray = try { k.decode(src) } catch (e: IllegalArgumentException) { throw IllegalArgumentException(e.message) }
  }

  fun getEncoder(): Encoder = Encoder(KBase64.Default)
  fun getUrlEncoder(): Encoder = Encoder(KBase64.UrlSafe)
  fun getMimeEncoder(): Encoder = Encoder(KBase64.Mime)
  fun getDecoder(): Decoder = Decoder(KBase64.Default.withPadding(KBase64.PaddingOption.PRESENT_OPTIONAL))
  fun getUrlDecoder(): Decoder = Decoder(KBase64.UrlSafe.withPadding(KBase64.PaddingOption.PRESENT_OPTIONAL))
  fun getMimeDecoder(): Decoder = Decoder(KBase64.Mime.withPadding(KBase64.PaddingOption.PRESENT_OPTIONAL))
}
