//  هستهٔ خرید (`core/shopper/…`) — **بی مسیر و بی صفحه** در برنامهٔ مدیریت.
//  تصمیمِ صاحب محصول (۱۴۰۵/۰۸/۰۸): خریدِ مشتری برنامهٔ جدای خودش را دارد؛ این منطقِ
//  خالص (پیوند، سبد، پرداخت، سفارش‌ها، API) و آزمون‌هایش برای همان برنامه نگه داشته
//  شده و هیچ جای برنامهٔ مدیریت (پوسته، `DeepLinks`، `V3Modules`) آن را صدا نمی‌زند.
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
object ShopperPaths {
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
 *  خواندنِ پیوندِ دکان — از کیو‌آر ِ پشتِ پیشخوان، پیام، مرورگر یا خودِ برنامه:
 *
 *    https://{slug}.vill3n.top[/][#/p/{کالا}|#/o/{سفارش}|#/cart]
 *    https://vill3n.top/store/{slug}[/][#/…]   (و همین روی سرورِ تنظیم‌شده)
 *    https://vill3n.top/{slug}
 *    vill3n://app/{slug} · vill3n://app/shopper/shop/{slug}[?product=…] · …/order/{slug}/{token} · …/cart/{slug}
 *
 *  تنها درِ بیرونی `parse` است: پیوند ⇒ مسیرِ برنامه، یا null. مسیریابِ برنامه
 *  همین را صدا می‌زند و خودش چیزی از شکلِ نشانی‌ها نمی‌داند.
 *
 *  ⛔ فقط میزبان‌های شناخته (دامنهٔ ویترین و سرورِ همین برنامه): پیوندِ سایتِ
 *  دیگری که مسیرش شبیه است، دکانِ ما حساب نمی‌شود. نشانیِ ویترین با همان
 *  قاعدهٔ سرور سنجیده می‌شود (`StorefrontOwner.SLUG_RE`، رزروها، بی «--»).
 */
object ShopperLinks {
  /** دامنهٔ ویترین‌ها (D-06؛ `STOREFRONT_DOMAIN` ِ سرور) */
  const val DOMAIN = "vill3n.top"
  const val APP_SCHEME = "vill3n"
  const val APP_HOST = "app"

  private val PRODUCT_RE = Regex("^[A-Za-z0-9_.:-]{1,80}$")
  private val TOKEN_RE = Regex("^[0-9A-Z]{26}\\.[A-Za-z0-9_-]{32}$")

  /** مسیرهای خودِ سایت روی دامنهٔ اصلی — نشانیِ دکان نیستند */
  private val SITE_PATHS = setOf("market", "store", "kit")

  /**
   *  پیوند ⇒ مسیرِ برنامه (`shopper/shop/{slug}`، `shopper/order/{slug}/{token}`،
   *  `shopper/cart/{slug}`، `shopper/shop/{slug}?product=…`) — یا null.
   *  @param hosts میزبان‌های دیگری که پیوندشان پذیرفته است (سرورِ تنظیم‌شدهٔ برنامه)
   */
  fun parse(uri: String, hosts: Set<String> = emptySet()): String? = link(uri, hosts)?.let(ShopperPaths::of)

  fun validSlug(slug: String): Boolean =
    StorefrontOwner.SLUG_RE.matches(slug) && !slug.contains("--") && slug !in StorefrontOwner.RESERVED

  internal fun link(raw: String, hosts: Set<String> = emptySet()): ShopperLink? {
    val text = raw.trim()
    if (text.isEmpty() || text.length > 2048) return null
    val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(text)) text else "https://$text"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    return when (uri.scheme?.lowercase()) {
      APP_SCHEME -> appLink(uri)
      "https", "http" -> webLink(uri, hosts)
      else -> null
    }
  }

  private fun webLink(uri: URI, hosts: Set<String>): ShopperLink? {
    val host = uri.host?.lowercase()?.removeSuffix(".")?.removePrefix("www.") ?: return null
    val segments = segmentsOf(uri.rawPath)
    val slug = host.removeSuffix(".$DOMAIN").takeIf { host.endsWith(".$DOMAIN") && "." !in it }
      ?: run {
        val known = host == DOMAIN || host in hosts.map { it.lowercase().removePrefix("www.") }
        when {
          !known -> null
          segments.size == 2 && segments[0] == "store" -> segments[1]
          segments.size == 1 && host == DOMAIN && segments[0] !in SITE_PATHS -> segments[0]
          else -> null
        }
      }
      ?: return null
    val clean = slug.lowercase().takeIf(::validSlug) ?: return null
    val fragment = segmentsOf(uri.rawFragment)
    val value = fragment.getOrNull(1).orEmpty()
    return when (fragment.firstOrNull()) {
      "o" -> if (TOKEN_RE.matches(value)) ShopperLink.Order(clean, value) else ShopperLink.Shop(clean)
      "p" -> ShopperLink.Shop(clean, productId = value.takeIf { PRODUCT_RE.matches(it) })
      "cart" -> ShopperLink.Shop(clean, openCart = true)
      else -> ShopperLink.Shop(clean)
    }
  }

  /** `vill3n://app/…` — نشانیِ دکان یا یکی از مسیرهای خودِ حالتِ «خرید» */
  private fun appLink(uri: URI): ShopperLink? {
    if (uri.host?.lowercase() != APP_HOST) return null
    val s = segmentsOf(uri.rawPath).let { if (it.firstOrNull() == "shopper") it.drop(1) else it }
    val slug = (if (s.size == 1) s[0] else s.getOrNull(1))?.lowercase()?.takeIf(::validSlug) ?: return null
    val product = queryOf(uri.rawQuery)["product"]?.takeIf { PRODUCT_RE.matches(it) }
    return when {
      s.size == 1 -> ShopperLink.Shop(slug)
      s.size == 2 && s[0] == "shop" -> ShopperLink.Shop(slug, productId = product)
      s.size == 2 && s[0] == "cart" -> ShopperLink.Shop(slug, openCart = true)
      s.size == 3 && s[0] == "order" && TOKEN_RE.matches(s[2]) -> ShopperLink.Order(slug, s[2])
      else -> null
    }
  }

  private fun segmentsOf(raw: String?): List<String> =
    raw.orEmpty().split('/').filter { it.isNotEmpty() }
      .map { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault("") }

  private fun queryOf(raw: String?): Map<String, String> =
    raw.orEmpty().split('&').filter { '=' in it }.associate { pair ->
      val dec = { x: String -> runCatching { URLDecoder.decode(x, "UTF-8") }.getOrDefault("") }
      dec(pair.substringBefore('=')) to dec(pair.substringAfter('='))
    }
}
