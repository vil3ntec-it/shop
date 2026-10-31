package ir.vil3ntec.tohid.core.platform

/*
 *  تنها مرزِ سکو در commonMain: تصادفیِ امن، SHA-256، ساعت و قفل.
 *  JVM (اندروید و کامپیوتر) همان API ِ JDK ِ پیشین است؛ iOS از Security،
 *  CommonCrypto و Foundation. هیچ‌جا رمزنگاریِ دست‌نویس نیست.
 */

/** `size` بایتِ تصادفیِ امن (CSPRNG ِ سیستم) */
internal expect fun secureRandomBytes(size: Int): ByteArray

/** SHA-256 ِ داده (۳۲ بایت) */
internal expect fun sha256(data: ByteArray): ByteArray

/** میلی‌ثانیهٔ یونیکس از ساعتِ دیواریِ دستگاه */
internal expect fun currentTimeMillis(): Long

/** یک قفلِ سراسری برای حالتِ مشترکِ `Ulid` (جای `@Synchronized` که روی Native کاری نمی‌کند) */
internal expect fun <T> locked(block: () -> T): T
