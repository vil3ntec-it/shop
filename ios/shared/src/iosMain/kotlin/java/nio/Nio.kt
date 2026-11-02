package java.nio

/** shimِ iOS ِ `ByteBuffer` ِ فقط-خواندنی (`wrap`، `get`، `limit`) — رمزگشاییِ UTF-8 و تحلیلِ روشناییِ تصویرِ دوربین */
class ByteBuffer private constructor(internal val bytes: ByteArray) {
  private var position = 0
  fun array(): ByteArray = bytes
  fun remaining(): Int = bytes.size - position
  fun limit(): Int = bytes.size
  fun capacity(): Int = bytes.size
  fun position(): Int = position
  fun get(index: Int): Byte = bytes[index]
  fun get(): Byte = bytes[position++]
  fun hasRemaining(): Boolean = position < bytes.size

  companion object {
    fun wrap(array: ByteArray): ByteBuffer = ByteBuffer(array)
  }
}

class CharBuffer internal constructor(private val text: String) : CharSequence by text {
  override fun toString(): String = text
}
