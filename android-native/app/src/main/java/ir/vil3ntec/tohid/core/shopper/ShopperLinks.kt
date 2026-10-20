package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import java.net.URI
import java.net.URLDecoder

/** جایی که یک پیوند یا کیو‌آر ِ دکان باید باز شود */
sealed class ShopperLink {
  data class Shop(val slug: String, val productId: String? = null, val openCart: Boolean = false) : ShopperLink()

  data class Order(val slug: String, val token: String) : ShopperLink()
}

/** مسیرهای حالتِ «خرید» در مسیریابِ برنامه */
object ShopperRoutes {
  const val HOME = "shopper/home"
  const val SHOP = "shopper/shop/{slug}"
  const val ORDERS = "shopper/orders"
  const val ORDER = "shopper/order/{slug}/{token}"
  const val CART = "shopper/cart/{slug}"
  const val CHECKOUT = "shopper/checkout/{slug}"
  const val SCAN = "shopper/scan"

  fun shop(slug: String): String = "shopper/shop/$slug"
  fun order(slug: String, token: String): String = "shopper/order/$slug/$token"
  fun cart(slug: String): String = "shopper/cart/$slug"
  fun checkout(slug: String): String = "shopper/checkout/$slug"

  /** مسیرِ پیوند؛ کالا و سبد پارامترِ همان صفحهٔ دکان‌اند */
  fun of(link: ShopperLink): String = when (link) {
    is ShopperLink.Order -> order(link.slug, link.token)
    is ShopperLink.Shop -> when {
      link.productId != null -> shop(link.slug) + "?product=" + java.net.URLEncoder.encode(link.productId, "UTF-8")
      link.openCart -> cart(link.slug)
      else -> shop(link.slug)
    }
  }
}

/**
 *  خواندنِ پیوندِ دکان — از کیو‌آر ِ پشتِ پیشخوان، پیام یا مرورگر:
 *
 *    https://{slug}.vill3n.top[/][#/p/{کالا}|#/o/{سفارش}|#/cart]
 *    https://{میزبان}/store/{slug}[/][#/…]
 *
 *  ⛔ فقط میزبان‌های شناخته (دامنهٔ ویترین و سرورِ همین برنامه): پیوندِ سایتِ
 *  دیگری که مسیرش شبیه است، دکانِ ما حساب نمی‌شود. نشانیِ ویترین با همان
 *  قاعدهٔ سرور سنجیده می‌شود (`StorefrontOwner.SLUG_RE`، رزروها، بی «--»).
 */
object ShopperLinks {
  /** دامنهٔ ویترین‌ها (D-06؛ `STOREFRONT_DOMAIN` ِ سرور) */
  const val DOMAIN = "vill3n.top"

  private val PRODUCT_RE = Regex("^[A-Za-z0-9_.:-]{1,80}$")
  private val TOKEN_RE = Regex("^[0-9A-Z]{26}\\.[A-Za-z0-9_-]{32}$")
  private val STORE_PATH = Regex("^/store/([^/]+)/?$")

  /**
   *  @param domains دامنه‌هایی که زیردامنه‌شان نشانیِ ویترین است
   *  @param hosts میزبان‌های دیگری که `/store/{slug}` شان پذیرفته است (سرورِ تنظیم‌شده)
   */
  fun parse(raw: String, domains: Set<String> = setOf(DOMAIN), hosts: Set<String> = emptySet()): ShopperLink? {
    val text = raw.trim()
    if (text.isEmpty() || text.length > 2048) return null
    val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(text)) text else "https://$text"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("https", "http")) return null
    val host = uri.host?.lowercase()?.removeSuffix(".")?.removePrefix("www.") ?: return null
    val doms = domains.map { it.lowercase().trim('.') }.filter { it.isNotEmpty() }

    val slug = doms.firstNotNullOfOrNull { d -> host.removeSuffix(".$d").takeIf { host.endsWith(".$d") && "." !in it } }
      ?: run {
        val known = host in doms || host in hosts.map { it.lowercase().removePrefix("www.") }
        if (!known) return null
        STORE_PATH.find(uri.rawPath.orEmpty())?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") }
      }
      ?: return null
    val clean = slug.lowercase()
    if (!validSlug(clean)) return null

    val fragment = uri.rawFragment.orEmpty()
    val (kind, value) = fragment.removePrefix("/").split('/', limit = 2).let { it[0] to it.getOrNull(1).orEmpty() }
    val decoded = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault("")
    return when {
      kind == "o" && TOKEN_RE.matches(decoded) -> ShopperLink.Order(clean, decoded)
      kind == "p" && PRODUCT_RE.matches(decoded) -> ShopperLink.Shop(clean, productId = decoded)
      kind == "cart" -> ShopperLink.Shop(clean, openCart = true)
      else -> ShopperLink.Shop(clean)
    }
  }

  fun validSlug(slug: String): Boolean =
    StorefrontOwner.SLUG_RE.matches(slug) && !slug.contains("--") && slug !in StorefrontOwner.RESERVED
}
