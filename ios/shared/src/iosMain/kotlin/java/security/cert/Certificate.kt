package java.security.cert

import java.security.GeneralSecurityException

/*
 *  shimِ iOS ِ گواهیِ JDK — فقط آنچه سنجاقِ گواهیِ «سرورِ خودِ دکان» (`PinnedTls` در
 *  `platform/ServerSwitchAndroid.kt`) می‌خواند: DER ِ گواهی (`encoded`). گواهی‌ها را `javax.net.ssl.IosTls`
 *  از زنجیرهٔ `SecTrust` ِ دست‌دادنِ NSURLSession می‌سازد.
 */
abstract class Certificate protected constructor(val type: String) {
  abstract val encoded: ByteArray

  override fun equals(other: Any?): Boolean = other is Certificate && other.encoded.contentEquals(encoded)
  override fun hashCode(): Int = encoded.contentHashCode()
}

abstract class X509Certificate protected constructor() : Certificate("X.509")

open class CertificateException(message: String? = null, cause: Throwable? = null) : GeneralSecurityException(message, cause)
