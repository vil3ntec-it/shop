package ir.vil3ntec.tohid.core.shopper

import kotlinx.serialization.Serializable

/*
 *  برنامهٔ خریدِ مشتری، مرحلهٔ ۲ — پیوندِ عمیق و «سفارشِ تکرارشونده» (MKT-08، روی همین دستگاه).
 *
 *  پیوندِ عمیق: همان نشانی‌های ویترینِ وب (`/store/<slug>`، `/store/<slug>/p/<id>`، `#/p/<id>`، `#/o/<توکن>`،
 *  زیردامنهٔ `<slug>.vill3n.top` ِ D-06)، QR ِ دکان (`/store/go/<کد>`؛ MKT-10/11 — slug را سرور می‌گوید) و طرحِ
 *  `tohidshop://` ِ خودِ برنامه. هیچ پیوندی بی سنجش چیزی نمی‌نویسد؛ توکنِ پیگیری را سرور می‌سنجد.
 *
 *  MKT-08: مشتری برای سفارشی که از همین دستگاه ثبت کرده «هر ۷/۱۴/۳۰ روز» می‌گذارد. موعد که رسید، برنامه (هنگامِ
 *  باز شدن) کارتِ «وقتِ سفارشِ دوباره» نشان می‌دهد و یک لمس سبد را از «خریدِ دوباره» ِ سرور (`GET …/reorder`، قیمت و
 *  موجودیِ امروز) پُر می‌کند؛ ثبت همان `POST /checkout` با کلیدِ یکتایی و تأییدِ خودِ مشتری است. ⛔ هیچ سفارشی بی
 *  تأییدِ مشتری و در پس‌زمینه ثبت نمی‌شود (پرداخت، موجودی و قیمتِ روز با سرور؛ یادآوریِ بیرون از برنامه Push ِ O-01 است).
 */

sealed class ShopperLink {
  data class Shop(val slug: String, val productId: String? = null, val campaign: String? = null) : ShopperLink()
  data class Order(val slug: String, val token: String) : ShopperLink()
  /** QR/پیوندِ کمپین: slug را `GET /store/go/<کد>` ِ سرور می‌گوید ([ShopperApi.campaign]) */
  data class Campaign(val code: String) : ShopperLink()
}

object ShopperLinks {
  const val SCHEME = "tohidshop"
  /** دامنهٔ ویترین‌ها (D-06) — زیردامنهٔ `<slug>.vill3n.top` همان `/store/<slug>` است */
  const val ROOT_DOMAIN = "vill3n.top"
  /** زیردامنه‌هایی که ویترین نیستند (همان رزروِ #244) */
  private val RESERVED = setOf("www", "api", "app", "admin", "panel", "mail", "store", "market", "pump", "shop", "static", "cdn")
  private val SLUG = Regex("^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$")
  private val TOKEN = Regex("^[A-Za-z0-9._~-]{8,300}$")
  /** همان `PRODUCT_RE` ِ سرور */
  private val ID = Regex("^[A-Za-z0-9_.:-]{1,80}$")
  private val CODE = Regex("^[A-Za-z0-9_-]{3,64}$")

  fun parse(url: String): ShopperLink? {
    val raw = url.trim()
    if (raw.isEmpty() || raw.length > 2048) return null
    val schemeEnd = raw.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = raw.substring(0, schemeEnd).lowercase()
    val rest = raw.substring(schemeEnd + 3)
    val hostEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
    val host = rest.substring(0, hostEnd).substringAfter('@').substringBefore(':').lowercase()
    val after = rest.substring(hostEnd)
    val fragment = after.substringAfter('#', "")
    val beforeFrag = after.substringBefore('#')
    val path = beforeFrag.substringBefore('?')
    val query = beforeFrag.substringAfter('?', "")
    val segs = path.split('/').filter { it.isNotEmpty() }.map { decode(it) }
    return when (scheme) {
      //  tohidshop://store/<slug>[/p/<id>|/o/<توکن>] — «store» جای میزبان است
      SCHEME -> fromSegments(listOf(host) + segs, fragment, query)
      "https", "http" -> when {
        host == ROOT_DOMAIN -> fromSegments(segs, fragment, query)
        host.endsWith(".$ROOT_DOMAIN") -> {
          val sub = host.removeSuffix(".$ROOT_DOMAIN")
          when {
            sub in RESERVED || '.' in sub -> fromSegments(segs, fragment, query)
            SLUG.matches(sub) -> tail(sub, segs, fragment, query)
            else -> null
          }
        }
        else -> null
      }
      else -> null
    }
  }

  private fun fromSegments(segs: List<String>, fragment: String, query: String): ShopperLink? {
    if (segs.size < 2 || segs[0] != "store") return null
    if (segs[1] == "go") return segs.getOrNull(2)?.takeIf { CODE.matches(it) }?.let { ShopperLink.Campaign(it) }
    val slug = segs[1].lowercase().takeIf { SLUG.matches(it) } ?: return null
    return tail(slug, segs.drop(2), fragment, query)
  }

  /** پس از slug: `/p/<id>` یا `/o/<توکن>` در مسیر، یا همان در `#/p/…`/`#/o/…` ِ وب؛ `?c=<کد>` ِ کمپین */
  private fun tail(slug: String, segs: List<String>, fragment: String, query: String): ShopperLink {
    val frag = fragment.removePrefix("/").split('/').filter { it.isNotEmpty() }.map { decode(it) }
    val parts = if (segs.size >= 2) segs else frag
    val campaign = query.split('&').firstOrNull { it.startsWith("c=") }?.removePrefix("c=")?.let { decode(it) }?.takeIf { CODE.matches(it) }
    return when {
      parts.size >= 2 && parts[0] == "o" && TOKEN.matches(parts[1]) -> ShopperLink.Order(slug, parts[1])
      parts.size >= 2 && parts[0] == "p" && ID.matches(parts[1]) -> ShopperLink.Shop(slug, parts[1], campaign)
      else -> ShopperLink.Shop(slug, null, campaign)
    }
  }

  /** پیوندِ برنامه برای همان دکان/سفارش (هم‌رسانی از برنامه) */
  fun appLink(link: ShopperLink): String = when (link) {
    is ShopperLink.Shop -> "$SCHEME://store/${link.slug}" + (link.productId?.let { "/p/" + ShopperUrl.encode(it) } ?: "")
    is ShopperLink.Order -> "$SCHEME://store/${link.slug}/o/${link.token}"
    is ShopperLink.Campaign -> "$SCHEME://store/go/${link.code}"
  }

  /** کدگشاییِ درصدیِ UTF-8 (بی `java.net`)؛ نادرست ⇒ همان متن */
  private fun decode(s: String): String {
    if ('%' !in s) return s
    val out = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
      val c = s[i]
      if (c == '%' && i + 2 < s.length) {
        val v = s.substring(i + 1, i + 3).toIntOrNull(16) ?: return s
        out += v.toByte()
        i += 3
      } else {
        c.toString().encodeToByteArray().forEach { out += it }
        i++
      }
    }
    return out.toByteArray().decodeToString()
  }
}

/** پاسخِ `GET /store/go/<کد>` با `Accept: application/json` */
@Serializable
data class CampaignTarget(val slug: String, val campaign: String? = null)

/** یک قلمِ «خریدِ دوباره» ِ سرور (WEB-12): قیمت و موجودیِ امروز؛ `available = false` ⇒ به سبد نمی‌رود */
@Serializable
data class ReorderLine(
  val productId: String,
  val name: String = "",
  val unit: String = "",
  val qtyMilli: Long = 0,
  val previousPriceMinor: Long = 0,
  val priceMinor: Long? = null,
  val maxQtyMilli: Long? = null,
  val available: Boolean = false,
  val reason: String = "",
)

@Serializable
data class ReorderPage(val orderNo: Long = 0, val lines: List<ReorderLine> = emptyList())

/** برنامهٔ تکرارِ یک سفارش روی همین دستگاه (MKT-08) */
@Serializable
data class RepeatPlan(
  val slug: String,
  val token: String,
  val orderNo: Long,
  val storeTitle: String = "",
  val everyDays: Int,
  val nextAt: Long,
  val createdAt: Long = 0,
)

object RepeatPlans {
  const val MAX = 10
  const val DAY_MS = 24 * 60 * 60 * 1000L
  /** هفتگی، دو هفته یک بار، ماهانه (۳۰ روز) */
  val CHOICES = listOf(7, 14, 30)

  fun of(list: List<RepeatPlan>, token: String): RepeatPlan? = list.firstOrNull { it.token == token }

  /** یکی برای هر سفارش؛ نخستین موعد یک دوره پس از اکنون؛ سقفِ ۱۰ (کهنه‌ترین می‌رود) */
  fun set(list: List<RepeatPlan>, o: SavedOrder, everyDays: Int, now: Long): List<RepeatPlan> {
    require(everyDays in CHOICES) { "دوره" }
    val p = RepeatPlan(o.slug, o.token, o.orderNo, o.storeTitle, everyDays, now + everyDays * DAY_MS, now)
    return (list.filter { it.token != o.token } + p).sortedBy { it.createdAt }.takeLast(MAX)
  }

  fun remove(list: List<RepeatPlan>, token: String): List<RepeatPlan> = list.filter { it.token != token }

  fun due(list: List<RepeatPlan>, now: Long): List<RepeatPlan> = list.filter { it.nextAt <= now }.sortedBy { it.nextAt }

  /** سبد پُر شد (یا «این بار نه») ⇒ موعدِ بعدی یک دوره پس از اکنون — هرگز پشتِ سرِ هم دو موعدِ عقب‌افتاده */
  fun advance(list: List<RepeatPlan>, token: String, now: Long): List<RepeatPlan> =
    list.map { if (it.token == token) it.copy(nextAt = now + it.everyDays * DAY_MS) else it }

  /** «فردا یادم بینداز» */
  fun snooze(list: List<RepeatPlan>, token: String, now: Long): List<RepeatPlan> =
    list.map { if (it.token == token) it.copy(nextAt = now + DAY_MS) else it }

  /** دکان نشانی‌اش را عوض کرد (S3) */
  fun moved(list: List<RepeatPlan>, from: String, to: String): List<RepeatPlan> = list.map { if (it.slug == from) it.copy(slug = to) else it }

  /**
   *  سبدِ همان دکان از «خریدِ دوباره» ِ سرور: فقط قلم‌های فروختنی با **قیمتِ امروز** و سقفِ امروز؛ قلمِ همان کالا
   *  که از پیش در سبد است جایگزین می‌شود. @return (سبد، شمارِ قلم‌هایی که دیگر فروختنی نیستند)
   */
  fun cartFrom(cart: Cart, page: ReorderPage, at: Long): Pair<Cart, Int> {
    val ok = page.lines.filter { it.available && it.priceMinor != null && it.qtyMilli > 0 }
    var lines = cart.lines
    for (l in ok) {
      val cap = l.maxQtyMilli?.takeIf { it > 0 } ?: CartRules.MAX_QTY_MILLI
      val line = CartLine(l.productId, l.name, l.unit, l.priceMinor!!, minOf(l.qtyMilli, cap), l.maxQtyMilli)
      lines = if (lines.any { it.productId == l.productId }) lines.map { if (it.productId == l.productId) line else it }
        else if (lines.size < CartRules.MAX_LINES) lines + line else lines
    }
    return Cart(cart.slug, lines, at) to (page.lines.size - ok.size)
  }
}
