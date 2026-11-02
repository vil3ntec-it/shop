package java.nio

/** shimِ iOS ِ `ByteBuffer.wrap` — فقط برای رمزگشاییِ سخت‌گیرِ UTF-8 (‎charset/Charsets.kt‎) */
class ByteBuffer private constructor(internal val bytes: ByteArray) {
  fun array(): ByteArray = bytes
  fun remaining(): Int = bytes.size

  companion object {
    fun wrap(array: ByteArray): ByteBuffer = ByteBuffer(array)
  }
}

class CharBuffer internal constructor(private val text: String) : CharSequence by text {
  override fun toString(): String = text
}
