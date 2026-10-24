package ir.vil3ntec.tohid.core.nav

import java.net.URLDecoder
import java.net.URLEncoder

/**
 *  مسیرِ نوع‌دارِ برنامه — یک رشتهٔ خوانا مثلِ `settings/security`،
 *  `product/42` یا `products/new?barcode=626001`.
 *
 *  الگو بخش‌های ثابت و `{نام}` دارد (`product/{id}`). یک مسیر با الگو جور است
 *  اگر شمارِ بخش‌ها یکی باشد، بخش‌های ثابت عیناً برابر باشند و هر `{نام}` یک بخشِ
 *  ناخالی بگیرد. پارامترهای پرسش (`?k=v`) هم در `params` می‌نشینند.
 *
 *  پرسشِ ثابت در خودِ الگو (`scan?kind=qr`) شرط است: مسیر باید همان مقدار را داشته
 *  باشد، و هر جفتِ ثابت یک بخشِ ثابت شمرده می‌شود (پس `scan?kind=qr` از `scan` دقیق‌تر است).
 */
class RoutePattern(val pattern: String) {
  val segments: List<String> = split(pattern)

  /** پرسشِ ثابتِ الگو (`scan?kind=qr` ⇐ kind=qr) */
  val required: Map<String, String> = if ('?' in pattern) RoutePaths.parse(pattern).second else emptyMap()

  init {
    require(segments.isNotEmpty()) { "الگوی خالی" }
  }

  /** بخشِ اولِ ثابت — «پایه»ی مسیر (`settings` برای `settings/{page}`) */
  val base: String get() = segments.first()

  /** شمارِ بخش‌های ثابت: الگوی دقیق‌تر بر الگوی کلی‌تر پیشی می‌گیرد */
  val literals: Int get() = segments.count { !isParam(it) } + required.size

  fun match(path: String): RouteMatch? {
    val (parts, query) = RoutePaths.parse(path)
    if (parts.size != segments.size) return null
    if (required.any { (k, v) -> query[k] != v }) return null
    val params = LinkedHashMap<String, String>()
    segments.forEachIndexed { i, seg ->
      val part = parts[i]
      if (isParam(seg)) {
        if (part.isEmpty()) return null
        params[seg.substring(1, seg.length - 1)] = part
      } else if (seg != part) {
        return null
      }
    }
    query.forEach { (k, v) -> params.putIfAbsent(k, v) }
    return RouteMatch(pattern, RoutePaths.normalize(path), params)
  }

  /** ساختنِ مسیر از الگو — `build("id" to "42")` ⇐ `product/42` */
  fun build(vararg args: Pair<String, String>): String {
    val map = args.toMap()
    val path = segments.joinToString("/") { seg ->
      if (isParam(seg)) RoutePaths.encode(map[seg.substring(1, seg.length - 1)] ?: error("پارامترِ «$seg» داده نشد")) else seg
    }
    return if (required.isEmpty()) path else RoutePaths.withQuery(path, *required.map { (k, v) -> k to v }.toTypedArray())
  }

  override fun toString(): String = pattern

  private companion object {
    fun isParam(seg: String) = seg.length > 2 && seg.startsWith("{") && seg.endsWith("}")
    fun split(p: String) = p.substringBefore('?').split('/').filter { it.isNotEmpty() }
  }
}

/** نتیجهٔ جور شدنِ یک مسیر با الگو */
data class RouteMatch(val pattern: String, val path: String, val params: Map<String, String>) {
  operator fun get(key: String): String? = params[key]
}

object RoutePaths {
  /** بخش‌ها (رمزگشایی‌شده) و پارامترهای پرسش */
  fun parse(path: String): Pair<List<String>, Map<String, String>> {
    val clean = path.trim().trim('/')
    val head = clean.substringBefore('?')
    val parts = head.split('/').filter { it.isNotEmpty() }.map(::decode)
    val query = if ('?' in clean) {
      clean.substringAfter('?').split('&').filter { it.isNotEmpty() }.associate {
        decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
      }
    } else emptyMap()
    return parts to query
  }

  /** شکلِ یکتای مسیر: بی `/` در دو سر */
  fun normalize(path: String): String = path.trim().trim('/')

  /** پایهٔ یک مسیر: `settings/security` ⇐ `settings` */
  fun base(path: String): String = parse(path).first.firstOrNull().orEmpty()

  /** افزودنِ پارامترِ پرسش: `withQuery("products/new", "barcode" to "6260")` */
  fun withQuery(path: String, vararg args: Pair<String, String?>): String {
    val q = args.filter { !it.second.isNullOrEmpty() }.joinToString("&") { (k, v) -> encode(k) + "=" + encode(v!!) }
    if (q.isEmpty()) return normalize(path)
    return normalize(path) + (if ('?' in path) "&" else "?") + q
  }

  fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
  fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
