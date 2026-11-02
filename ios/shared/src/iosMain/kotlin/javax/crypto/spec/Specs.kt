package javax.crypto.spec

import javax.crypto.SecretKey

/** shimِ iOS ِ `SecretKeySpec`، `GCMParameterSpec`، `PBEKeySpec` — همان نام‌ها و برابریِ JDK */
class SecretKeySpec(key: ByteArray, offset: Int, len: Int, override val algorithm: String) : SecretKey {
  constructor(key: ByteArray, algorithm: String) : this(key, 0, key.size, algorithm)

  private val bytes: ByteArray = key.copyOfRange(offset, offset + len)
  override val format: String get() = "RAW"
  override val encoded: ByteArray get() = bytes.copyOf()

  override fun equals(other: Any?): Boolean =
    other is SecretKeySpec && other.algorithm.equals(algorithm, ignoreCase = true) && other.bytes.contentEquals(bytes)

  override fun hashCode(): Int = bytes.contentHashCode() xor algorithm.lowercase().hashCode()
}

class GCMParameterSpec(val tLen: Int, src: ByteArray, offset: Int, len: Int) {
  constructor(tLen: Int, src: ByteArray) : this(tLen, src, 0, src.size)

  private val nonce: ByteArray = src.copyOfRange(offset, offset + len)
  val iv: ByteArray get() = nonce.copyOf()
}

class PBEKeySpec(password: CharArray, salt: ByteArray, val iterationCount: Int, val keyLength: Int) {
  private val pw = password.copyOf()
  private val s = salt.copyOf()
  val password: CharArray get() = pw.copyOf()
  val salt: ByteArray get() = s.copyOf()
  fun clearPassword() = pw.fill('\u0000')
}
