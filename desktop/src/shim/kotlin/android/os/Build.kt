package android.os

import java.net.InetAddress

/**
 *  `Build` — نسخهٔ اندروید را «تازه‌ترین» می‌گوید تا راه‌های قدیمی گرفته
 *  نشوند، و نامِ دستگاه نامِ همین کامپیوتر است (در فهرستِ دستگاه‌های
 *  حساب همین دیده می‌شود).
 */
object Build {
  @JvmField val MODEL: String = run {
    val os = System.getProperty("os.name").orEmpty().trim()
    val host = runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
      ?: System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME")
    listOfNotNull("کامپیوتر", os.ifBlank { null }, host?.ifBlank { null }).joinToString(" · ")
  }
  @JvmField val MANUFACTURER: String = System.getProperty("os.name").orEmpty()
  @JvmField val BRAND: String = "desktop"
  @JvmField val DEVICE: String = "desktop"
  @JvmField val PRODUCT: String = "desktop"

  object VERSION {
    @JvmField val SDK_INT: Int = 35
    @JvmField val RELEASE: String = System.getProperty("os.version").orEmpty()
  }

  object VERSION_CODES {
    const val M = 23; const val N = 24; const val O = 26; const val P = 28; const val Q = 29
    const val R = 30; const val S = 31; const val TIRAMISU = 33; const val UPSIDE_DOWN_CAKE = 34
  }
}
