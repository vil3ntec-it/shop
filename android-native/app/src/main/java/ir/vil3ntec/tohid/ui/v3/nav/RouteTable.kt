package ir.vil3ntec.tohid.ui.v3.nav

import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.RouteMatch
import ir.vil3ntec.tohid.core.nav.RoutePaths

/** مسیری که پیدا شد: ماژول، مسیرِ ثبت‌شده و پارامترها */
class Resolved(val module: AppModule, val route: V3Route, val match: RouteMatch) {
  val args: RouteArgs get() = RouteArgs(match.path, match.params)

  /** این کاربر (نقش، پرچمِ سرور، پرچمِ ماژول، حالتِ برنامه) اجازه دارد؟ */
  fun allowed(access: Access): Boolean =
    access.mode in module.modes &&
      access.allows(module.needs + route.needs, null) &&
      (module.flag == null || module.flag in access.flags) &&
      (route.flag == null || route.flag in access.flags)
}

/**
 *  جدولِ همهٔ مسیرهای ثبت‌شده — خالص (هیچ محتوایی اجرا نمی‌شود)، پس آزمون‌پذیر.
 *
 *  • `canonical`: نامِ قدیمی (`warehouse`، `dashboard`، `nav-editor`…) ⇐ مسیرِ امروز،
 *    با نگه داشتنِ پرسش (`warehouse?x=1` ⇐ `stock?x=1`).
 *  • `resolve`: الگوی دقیق‌تر (بخشِ ثابتِ بیشتر) پیشی می‌گیرد: `settings/security`
 *    پیش از `settings/{group}`.
 *  • `chain`: زنجیرهٔ پدرها برای پیوندِ سرد (`products/42` ⇐ [`products`، `products/42`]).
 */
class RouteTable(val modules: List<AppModule>) {
  private val entries: List<Pair<AppModule, V3Route>> =
    modules.flatMap { m -> m.routes.map { m to it } }.sortedByDescending { it.second.matcher.literals }

  private val aliases: Map<String, String> = buildMap {
    modules.forEach { m -> m.routes.forEach { r -> r.aliases.forEach { a -> put(RoutePaths.normalize(a), r.pattern) } } }
  }

  fun canonical(path: String): String {
    val clean = RoutePaths.normalize(path)
    val head = clean.substringBefore('?')
    val target = aliases[head] ?: return clean
    return if ('?' in clean) target + "?" + clean.substringAfter('?') else target
  }

  fun resolve(path: String): Resolved? {
    val p = canonical(path)
    for ((m, r) in entries) {
      val match = r.matcher.match(p) ?: continue
      return Resolved(m, r, match)
    }
    return null
  }

  /** همهٔ مسیرهای یک حالت (برای آزمون و فهرستِ فرمان) */
  fun routes(mode: AppMode): List<V3Route> = modules.filter { mode in it.modes }.flatMap { it.routes }

  /** زنجیرهٔ پدرها تا ریشه، خودِ مسیر در آخر; حلقه یا پدرِ ناموجود ⇒ همان‌جا قطع */
  fun chain(path: String): List<String> {
    val out = ArrayDeque<String>()
    var cur: String? = canonical(path)
    val seen = HashSet<String>()
    while (cur != null && seen.add(cur)) {
      out.addFirst(cur)
      val r = resolve(cur) ?: break
      cur = r.route.parentOf(r.match)?.let(::canonical)
    }
    return out.toList()
  }
}
