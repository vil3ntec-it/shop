package java.io

/** shimِ iOS — خطای ورودی/خروجی؛ لایهٔ شبکهٔ iOS (گامِ ۲) خطای NSURLSession را به همین‌ها برمی‌گرداند */
open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
