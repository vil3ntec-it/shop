package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.RouteMatch
import ir.vil3ntec.tohid.core.nav.RoutePattern

/** گونهٔ مسیر (NAV-MAP) */
enum class RouteKind {
  /** می‌تواند در نوارِ پایین بنشیند؛ ریشهٔ پشته؛ بی دکمهٔ برگشت */
  TAB,
  /** رفتن به عمق؛ برگشتِ سربرگ */
  PAGE,
  /** برگه روی صفحهٔ پیشین (یا `parent` در پیوندِ سرد)؛ برگشت/پس‌زمینه/کشیدن می‌بندد */
  SHEET,
  /** تمام‌صفحه پیش از برنامه (قفل، ورود، ساختِ دکان) — هرگز در پشته نیست */
  GATE,
}

/** سربرگِ صفحه را چه کسی می‌کشد */
enum class Chrome {
  /** خودِ صفحه `V3Scaffold` دارد (صفحه‌های بازسازی‌شده) */
  OWN,
  /** پوسته صفحهٔ امروز را در `V3Scaffold(title)` می‌پیچد (تا بازسازی‌اش) */
  SHELL,
}

/**
 *  یک مسیرِ نوع‌دار: الگو (`stock/{id}`)، گونه، عنوان، پدر (برای ساختنِ پشته در
 *  پیوندِ سرد: `products/42` ⇐ `products` ⇐ `home`)، نام‌های قدیمی (`warehouse`)،
 *  نیازها و پرچمِ ماژول، و محتوا. پرسش (`?filter=low`) جزوِ الگو نیست و در
 *  `RouteArgs` خوانده می‌شود.
 *
 *  `parent` خودش یک مسیر است و می‌تواند پارامترهای همین مسیر را بگیرد
 *  (`stock/{id}/adjust` ⇐ `stock/{id}`).
 */
class V3Route(
  val pattern: String,
  val kind: RouteKind = RouteKind.PAGE,
  val title: () -> String = { "" },
  val parent: String? = null,
  val aliases: List<String> = emptyList(),
  val needs: Set<Need> = emptySet(),
  val flag: String? = null,
  val chrome: Chrome = Chrome.SHELL,
  val content: @Composable RouteScope.() -> Unit,
) {
  val matcher = RoutePattern(pattern)

  /** پدرِ همین مسیر با پارامترهای جاگذاری‌شده، یا null */
  fun parentOf(match: RouteMatch): String? = parent?.let { p ->
    var out = p
    match.params.forEach { (k, v) -> out = out.replace("{$k}", v) }
    out.takeIf { '{' !in it }
  }
}

/** پارامترهای مسیر و پرسش — `args["id"]`، `args.query("filter")` */
class RouteArgs(val path: String, private val params: Map<String, String>) {
  operator fun get(key: String): String? = params[key]
  fun query(key: String): String? = params[key]
  fun int(key: String): Int? = params[key]?.toIntOrNull()
  fun flag(key: String): Boolean = params[key].let { it == "1" || it == "true" }
  val all: Map<String, String> get() = params
}
