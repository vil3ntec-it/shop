package android.content.pm

/** shimِ iOS — بسته‌ای بی گواهیِ امضای APK (‎PackageManager.getPackageInfo‎) */
class PackageInfo internal constructor(val packageName: String) {
  val signingInfo: SigningInfo? get() = null
  val signatures: Array<Signature>? get() = null
  val versionName: String? get() = ir.vil3ntec.tohid.BuildConfig.VERSION_NAME
}

class SigningInfo internal constructor() {
  fun hasMultipleSigners(): Boolean = false
  val apkContentsSigners: Array<Signature> get() = emptyArray()
  val signingCertificateHistory: Array<Signature> get() = emptyArray()
}

class Signature(private val bytes: ByteArray) {
  fun toByteArray(): ByteArray = bytes.copyOf()
}
