package java.security.spec

open class InvalidKeySpecException(message: String? = null) : java.security.GeneralSecurityException(message)

interface KeySpec

class X509EncodedKeySpec(encoded: ByteArray) : KeySpec {
  private val bytes = encoded.copyOf()
  val encoded: ByteArray get() = bytes.copyOf()
}

class PKCS8EncodedKeySpec(encoded: ByteArray) : KeySpec {
  private val bytes = encoded.copyOf()
  val encoded: ByteArray get() = bytes.copyOf()
}

class ECGenParameterSpec(val name: String) : KeySpec
