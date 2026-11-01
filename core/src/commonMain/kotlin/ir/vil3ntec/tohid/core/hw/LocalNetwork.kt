package ir.vil3ntec.tohid.core.hw

/**
 *  اجازهٔ دسترسی به شبکهٔ محلی (`ACCESS_LOCAL_NETWORK`) در اندروید ۱۷ — HW-13.
 *
 *  ── قاعدهٔ اندروید (developer.android.com/privacy-and-security/local-network-permission) ──
 *  • برنامه‌ای که **هدفش API 37 یا بیشتر** است و روی اندروید ۱۷ (API 37) به بالا اجرا می‌شود،
 *    بی اجازهٔ زمانِ اجرای `ACCESS_LOCAL_NETWORK` (گروهِ «دستگاه‌های نزدیک») به هیچ نشانیِ شبکهٔ
 *    محلی وصل نمی‌شود: TCP معمولاً **مهلت** می‌خورد و UDP خطای `EPERM` می‌دهد — هر API ِ شبکه، از
 *    سوکتِ خام تا OkHttp. چاپگرِ شبکه (درگاهِ ۹۱۰۰) دقیقاً همین است.
 *  • هدفِ ۳۶ یا کمتر: اجازه ضمنی است (با `INTERNET`) و اندروید می‌گوید **نه در مانیفست بیاید و نه
 *    پرسیده شود**. امروز هدفِ برنامه ۳۵ است ⇒ [needsPermission] همیشه `false` و رفتار همان امروز.
 *  • قفلِ ارتقا: آزمونِ برنامه (`LocalNetworkManifestTest`) هدف را از `build.gradle.kts` و مانیفست
 *    را می‌خواند؛ با هدفِ ۳۷ بی این اجازه در مانیفست (یا با اجازه و هدفِ کمتر) سرخ است.
 *
 *  ── «شبکهٔ محلی» یعنی چه (developer.android.com/privacy-and-security/local-network-definition) ──
 *  IPv4: ‎169.254/16‎ (link-local)، ‎100.64/10‎ (CGNAT)، ‎10/8‎، ‎172.16/12‎، ‎192.168/16‎؛ همهٔ
 *  multicast (‎224/4‎، ‎ff00::/8‎) و broadcast ‎(255.255.255.255)‎؛ IPv6 ِ link-local ‎(fe80::/10)‎ و
 *  ULA ‎(fc00::/7)‎؛ و هر نامِ ‎`.local`‎ (mDNS). loopback و نشانیِ اینترنتی محلی نیستند.
 *
 *  Kotlin خالص است — بی java.*، بی android.*، بی کتابخانه (نشانی رقم‌به‌رقم خوانده می‌شود).
 */
object LocalNetwork {

  /** نامِ کوتاهِ اجازه؛ نامِ کامل (با پیشوندِ بستهٔ اجازه‌های اندروید) در فایلِ مرزیِ برنامه است */
  const val PERMISSION_NAME = "ACCESS_LOCAL_NETWORK"

  /** نخستین API که اجازه را اجباری می‌کند (اندروید ۱۷) */
  const val ENFORCED_API = 37

  /** اجازه در مانیفست بیاید؟ فقط با هدفِ ۳۷ یا بیشتر (کمتر ⇒ اندروید می‌گوید نیاید) */
  fun declare(targetSdk: Int): Boolean = targetSdk >= ENFORCED_API

  /** اجازهٔ زمانِ اجرا لازم است؟ هم هدفِ برنامه و هم خودِ دستگاه باید ۳۷ یا بیشتر باشند */
  fun needsPermission(targetSdk: Int, deviceSdk: Int): Boolean = targetSdk >= ENFORCED_API && deviceSdk >= ENFORCED_API

  /**
   *  وصل شدن به [host] بی اجازه بسته است؟ — تا صفحه و صفِ چاپ به‌جای ده ثانیه مهلت، همان دم
   *  بگویند «اجازه داده نشده». اجازه لازم نیست یا داده شده، یا نشانی محلی نیست ⇒ `false`.
   *  نامی که نشانیِ عددی نیست (جز `.local`) را نمی‌شود پیش از DNS سنجید ⇒ `false` (سوکت خودش می‌گوید).
   */
  fun blocked(targetSdk: Int, deviceSdk: Int, granted: Boolean, host: String): Boolean =
    needsPermission(targetSdk, deviceSdk) && !granted && isLocal(host)

  /** [host] نشانیِ شبکهٔ محلی است؟ (تعریفِ بالا) */
  fun isLocal(host: String): Boolean {
    val h = host.trim().removePrefix("[").removeSuffix("]").trimEnd('.').lowercase()
    if (h.isEmpty()) return false
    ipv4(h)?.let { return localV4(it) }
    if (':' in h) return localV6(h.substringBefore('%'))
    return h.endsWith(".local") || h == "local"
  }

  /** چهار بخشِ ده‌دهی ⇒ عدد (صفرِ پیشین ده‌دهی خوانده می‌شود)؛ هر شکلِ دیگر (بخشِ خالی، بیش از ۲۵۵) ⇒ `null` */
  fun ipv4(h: String): Long? {
    val parts = h.split('.')
    if (parts.size != 4) return null
    var v = 0L
    for (p in parts) {
      if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
      val n = p.toInt()
      if (n > 255) return null
      v = v * 256 + n
    }
    return v
  }

  private fun inV4(ip: Long, base: String, bits: Int): Boolean {
    val b = ipv4(base) ?: return false
    val shift = 32 - bits
    return (ip shr shift) == (b shr shift)
  }

  private fun localV4(ip: Long): Boolean =
    inV4(ip, "10.0.0.0", 8) || inV4(ip, "172.16.0.0", 12) || inV4(ip, "192.168.0.0", 16) ||
      inV4(ip, "169.254.0.0", 16) || inV4(ip, "100.64.0.0", 10) || inV4(ip, "224.0.0.0", 4) ||
      ip == 0xFFFF_FFFFL

  /** IPv6: فقط پیشوندِ نخستین گروه سنجیده می‌شود — link-local، ULA و multicast */
  private fun localV6(h: String): Boolean {
    if (h.count { it == ':' } < 2) return false
    //  IPv4 ِ نگاشته (‎::ffff:192.168.1.5‎) همان IPv4 است
    if (h.startsWith("::ffff:")) return ipv4(h.removePrefix("::ffff:"))?.let(::localV4) ?: false
    val first = h.substringBefore(':')
    if (first.isEmpty() || first.length > 4 || !first.all { it in '0'..'9' || it in 'a'..'f' }) return false
    val g = first.toInt(16)
    return (g and 0xFFC0) == 0xFE80 || (g and 0xFE00) == 0xFC00 || (g and 0xFF00) == 0xFF00
  }
}
