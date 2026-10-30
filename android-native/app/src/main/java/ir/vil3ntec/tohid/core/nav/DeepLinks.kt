package ir.vil3ntec.tohid.core.nav

/**
 *  پیوندهایی که برنامه را از بیرون باز می‌کنند — خالص و آزمون‌شده (`NavCoreTest`).
 *
 *  • `vill3n://app/<مسیر>?<پرسش>` ⇐ همان مسیرِ برنامه (اعلان‌ها، هشدارها،
 *    میان‌برها): `vill3n://app/settings/security`، `vill3n://app/products?filter=out`.
 *  • پیوندِ وب (QR و پیوندِ ویترینِ دکان) پیوندِ برنامهٔ مدیریت نیست و در مرورگر می‌ماند:
 *    خریدِ مشتری برنامهٔ جدای خودش را دارد (تصمیمِ ۱۴۰۵/۰۸/۰۸). خواندنِ همان پیوندها
 *    برای آن برنامه در `core/shopper/ShopperLinks` آماده و آزموده می‌ماند.
 *  • `vill3n://app/shopper/…` (حالتِ خریدِ پیش‌نویسِ پیشین) دیگر پیوندی نیست.
 */
data class DeepLink(val mode: AppMode, val path: String)

object DeepLinks {
  const val SCHEME = "vill3n"
  const val APP_HOST = "app"

  /** پیوندِ برنامه برای یک مسیر — همان رشته‌ای که اعلان‌ها و میان‌برها می‌سازند */
  fun appLink(path: String): String = "$SCHEME://$APP_HOST/" + RoutePaths.normalize(path)

  fun parse(raw: String?): DeepLink? {
    val url = raw?.trim().orEmpty()
    if (url.isEmpty() || "://" !in url) return null
    return when (url.substringBefore("://").lowercase()) {
      SCHEME -> app(url.substringAfter("://"))
      else -> null
    }
  }

  private fun app(rest: String): DeepLink? {
    if (!rest.startsWith("$APP_HOST/")) return null
    val path = RoutePaths.normalize(rest.removePrefix("$APP_HOST/").substringBefore('#'))
    if (path.isEmpty()) return null
    val mode = when (RoutePaths.base(path)) {
      "shopper" -> return null
      "courier" -> AppMode.COURIER
      else -> AppMode.SHOP
    }
    return DeepLink(mode, path)
  }
}
