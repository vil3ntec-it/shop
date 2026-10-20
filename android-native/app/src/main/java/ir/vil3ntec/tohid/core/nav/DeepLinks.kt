package ir.vil3ntec.tohid.core.nav

/**
 *  پیوندهایی که برنامه را از بیرون باز می‌کنند — خالص و آزمون‌شده (`DeepLinksTest`).
 *
 *  • `vill3n://app/<مسیر>?<پرسش>` ⇐ همان مسیرِ برنامه (اعلان‌ها، هشدارها،
 *    میان‌برها): `vill3n://app/settings/security`، `vill3n://app/products?filter=out`.
 *  • ویترینِ دکان، با همان شکلی که سرور امروز می‌سازد (D-06): زیردامنهٔ
 *    `https://<slug>.vill3n.top/`، مسیرِ `https://<هر میزبانِ vill3n.top>/store/<slug>`،
 *    و نشانیِ کوتاهِ `https://vill3n.top/<slug>` (QRِ دکان) ⇐ حالتِ «خرید» روی همان
 *    دکان: `shopper/shop/<slug>`. پیگیریِ سفارشِ ویترین (`…/store/<slug>#/o/<توکن>`)
 *    ⇐ `shopper/order/<توکن>`.
 *  قاعدهٔ نامِ دکان و نام‌های رزرو عیناً همان `server/src/lib/storefront.js` است تا
 *  پیوندی که سرور نمی‌سازد، این‌جا هم دکان حساب نشود؛ نشانی‌های دیگرِ سایت
 *  (بازارچه، پنل، API) پیوندِ برنامه نیستند و در مرورگر می‌مانند.
 */
data class DeepLink(val mode: AppMode, val path: String)

object DeepLinks {
  const val SCHEME = "vill3n"
  const val APP_HOST = "app"
  const val DOMAIN = "vill3n.top"

  /** `SLUG_RE`ِ سرور */
  private val SLUG = Regex("^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$")
  /** `RESERVED`ِ سرور + بخش‌های اولِ مسیرِ خودِ سایت */
  val RESERVED: Set<String> = setOf(
    "api", "www", "admin", "panel", "portal", "sdk", "shop", "store", "stores", "beta", "app", "apps",
    "pump", "mail", "smtp", "imap", "pop", "ftp", "ns1", "ns2", "cdn", "static", "assets", "help",
    "support", "status", "docs", "blog", "dev", "test", "staging", "login", "auth", "account",
    "billing", "pay", "payment", "download", "downloads", "home", "vill3n", "tohid", "official",
    "market", "kit", "courier",
  )
  private val TOKEN = Regex("^[A-Za-z0-9_.-]{1,160}$")

  /** پیوندِ برنامه برای یک مسیر — همان رشته‌ای که اعلان‌ها و میان‌برها می‌سازند */
  fun appLink(path: String): String = "$SCHEME://$APP_HOST/" + RoutePaths.normalize(path)

  fun parse(raw: String?): DeepLink? {
    val url = raw?.trim().orEmpty()
    if (url.isEmpty() || "://" !in url) return null
    val scheme = url.substringBefore("://").lowercase()
    val rest = url.substringAfter("://")
    return when (scheme) {
      SCHEME -> app(rest)
      "https", "http" -> site(rest)
      else -> null
    }
  }

  private fun app(rest: String): DeepLink? {
    if (!rest.startsWith("$APP_HOST/")) return null
    val path = RoutePaths.normalize(rest.removePrefix("$APP_HOST/").substringBefore('#'))
    if (path.isEmpty()) return null
    val mode = when (RoutePaths.base(path)) {
      "shopper" -> AppMode.SHOPPER
      "courier" -> AppMode.COURIER
      else -> AppMode.SHOP
    }
    return DeepLink(mode, path)
  }

  private fun site(rest: String): DeepLink? {
    val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':').lowercase()
    val afterHost = rest.substring(rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it })
    val fragment = afterHost.substringAfter('#', "")
    val parts = afterHost.substringBefore('#').substringBefore('?').split('/').filter { it.isNotEmpty() }.map(RoutePaths::decode)
    val sub = host.removeSuffix(".$DOMAIN").takeIf { host.endsWith(".$DOMAIN") }
    val slug: String? = when {
      host != DOMAIN && sub == null -> return null
      parts.size >= 2 && parts[0] == "store" -> parts[1]
      sub != null && sub !in RESERVED && '.' !in sub && parts.isEmpty() -> sub
      sub == null && parts.size == 1 -> parts[0]
      else -> null
    }?.lowercase()?.takeIf { SLUG.matches(it) && it !in RESERVED }
    if (slug == null) return null
    val order = fragment.removePrefix("/").takeIf { it.startsWith("o/") }?.removePrefix("o/")?.takeIf { TOKEN.matches(it) }
    return if (order != null) DeepLink(AppMode.SHOPPER, "shopper/order/" + RoutePaths.encode(order))
    else DeepLink(AppMode.SHOPPER, "shopper/shop/$slug")
  }
}
