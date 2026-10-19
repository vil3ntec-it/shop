package ir.vil3ntec.tohid.core.storefront

/**
 *  توضیح و مشخصاتِ عمومیِ کالا در ویترین (WEB-08) — همان قاعدهٔ سرور
 *  (`specsOf` در `server/src/lib/storefront.js`): حداکثر ۲۰ مشخصه، نام تا ۴۰
 *  و مقدار تا ۱۲۰ نویسه. یادداشتِ داخلیِ دفتر هرگز به ویترین نمی‌رود.
 */
object StorefrontSpecs {
  const val MAX = 20
  const val MAX_DESCRIPTION = 2000

  /** «وزن: ۱ کیلو» در هر خط (یا «؛») ⇒ فهرست؛ خطا ⇒ پیام */
  fun parse(raw: String): Pair<List<Pair<String, String>>?, String?> {
    val items = raw.split('\n', ';', '؛').map { it.trim() }.filter { it.isNotEmpty() }
    if (items.size > MAX) return null to "حداکثر ۲۰ مشخصه"
    val out = ArrayList<Pair<String, String>>(items.size)
    for (it in items) {
      val i = it.indexOfFirst { c -> c == ':' || c == '：' }
      val value = if (i > 0) it.substring(i + 1).trim() else ""
      if (i < 1 || value.isEmpty()) return null to "«$it» را مثلِ «وزن: ۱ کیلو» بنویسید"
      out += it.substring(0, i).trim().take(40) to value.take(120)
    }
    return out to null
  }

  fun format(specs: List<Pair<String, String>>): String = specs.joinToString("\n") { "${it.first}: ${it.second}" }
}
