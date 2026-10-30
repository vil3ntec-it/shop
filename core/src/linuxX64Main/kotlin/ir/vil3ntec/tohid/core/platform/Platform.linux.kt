package ir.vil3ntec.tohid.core.platform

/*
 *  linuxX64 فقط نگهبانِ کامپایل است (`-Pnative=true`، کارِ `core-native` در
 *  `.github/workflows/ios-spike.yml`): هیچ برنامه‌ای روی آن ساخته یا اجرا نمی‌شود.
 *  این actualها فقط برای این‌اند که commonMain روی Kotlin/Native ِ بی JDK کامپایل شود؛
 *  صدا زدنشان یعنی کسی این هدف را جدی گرفته و باید پیاده‌سازیِ واقعی بنویسد.
 */

private fun guardOnly(): Nothing =
  throw UnsupportedOperationException("linuxX64 فقط نگهبانِ کامپایلِ commonMain است")

internal actual fun secureRandomBytes(size: Int): ByteArray = guardOnly()

internal actual fun sha256(data: ByteArray): ByteArray = guardOnly()

internal actual fun currentTimeMillis(): Long = guardOnly()

internal actual fun <T> locked(block: () -> T): T = guardOnly()
