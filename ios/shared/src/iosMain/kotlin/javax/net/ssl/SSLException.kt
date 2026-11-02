package javax.net.ssl

/** shimِ iOS — همان ردهٔ خطای JDK که `ApiFailure` می‌شناسد */
open class SSLException(message: String? = null, cause: Throwable? = null) : java.io.IOException(message, cause)
