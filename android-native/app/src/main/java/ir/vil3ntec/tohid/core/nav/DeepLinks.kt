package ir.vil3ntec.tohid.core.nav

import ir.vil3ntec.tohid.core.shopper.ShopperLinks

/**
 *  پیوندهایی که برنامه را از بیرون باز می‌کنند — خالص و آزمون‌شده (`NavCoreTest`).
 *
 *  • `vill3n://app/<مسیر>?<پرسش>` ⇐ همان مسیرِ برنامه (اعلان‌ها، هشدارها،
 *    میان‌برها): `vill3n://app/settings/security`، `vill3n://app/products?filter=out`.
 *  • پیوندِ وب (QR و پیوندِ دکان) را هستهٔ خرید می‌خواند: `ShopperLinks.parse(uri, hosts)`
 *    (`https://<slug>.vill3n.top/`، `https://<میزبان>/store/<slug>`، `#/p/…`، `#/o/…`، `#/cart`)
 *    ⇐ حالتِ «خرید» روی همان دکان. `hosts` میزبانِ سرورِ همین ساخت است. نشانی‌های دیگرِ
 *    سایت (بازارچه، پنل، API) پیوندِ برنامه نیستند و در مرورگر می‌مانند.
 */
data class DeepLink(val mode: AppMode, val path: String)

object DeepLinks {
  const val SCHEME = "vill3n"
  const val APP_HOST = "app"
  const val DOMAIN = ShopperLinks.DOMAIN

  /** پیوندِ برنامه برای یک مسیر — همان رشته‌ای که اعلان‌ها و میان‌برها می‌سازند */
  fun appLink(path: String): String = "$SCHEME://$APP_HOST/" + RoutePaths.normalize(path)

  fun parse(raw: String?, hosts: Set<String> = emptySet()): DeepLink? {
    val url = raw?.trim().orEmpty()
    if (url.isEmpty() || "://" !in url) return null
    return when (url.substringBefore("://").lowercase()) {
      SCHEME -> app(url.substringAfter("://"))
      "https", "http" -> ShopperLinks.parse(url, hosts)?.let { DeepLink(AppMode.SHOPPER, it) }
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
}
