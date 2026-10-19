package ir.vil3ntec.tohid.ui.components.pos

/**
 *  خلاصهٔ نوارِ تیرهٔ سبدِ گوشی (phone-ui، تصویرِ مرجعِ ۵) — خالص، بی Compose.
 *
 *  نوار فقط وقتی سبد پُر است دیده می‌شود و سه چیز نشان می‌دهد: عکسِ گردِ تازه‌ترین
 *  قلم‌ها (یا حرفِ اولِ نامشان)، شمارِ قلم‌ها در دایرهٔ رنگی، و مبلغ. همان سه
 *  قاعده در `index.html` (`cartBarModel`) و ویترین (`store.js`) هم هست.
 *
 *   • «شمار» شمارِ **ردیف‌ها**ست، نه جمعِ مقدار — دو کیلو برنج یک قلم است
 *     (همان «۳ قلم»ِ نوارِ پیشینِ ویترین).
 *   • عکس‌ها از **تازه‌ترین** ردیف‌اند تا هر افزودن بی‌درنگ در نوار دیده شود؛
 *     بیش از `MAX_THUMBS` ⇐ «+N».
 *   • مبلغ همان جمعِ ردیف‌هاست که صدا زننده می‌دهد (`SalesEngine.cartTotal`) —
 *     این‌جا حسابِ تازه‌ای نیست.
 */
object CartSummary {
  const val MAX_THUMBS = 3

  /** یک ردیفِ سبد برای نوار و برگه */
  data class Line(val id: String, val name: String, val hasProduct: Boolean)

  data class Thumb(val id: String, val initials: String, val hasProduct: Boolean)

  data class Model(val count: Int, val total: Double, val thumbs: List<Thumb>, val more: Int) {
    val visible: Boolean get() = count > 0
  }

  fun of(lines: List<Line>, total: Double): Model {
    val recent = lines.takeLast(MAX_THUMBS).asReversed()
    return Model(
      count = lines.size,
      total = total,
      thumbs = recent.map { Thumb(it.id, initials(it.name), it.hasProduct) },
      more = (lines.size - recent.size).coerceAtLeast(0),
    )
  }

  /**
   *  حرفِ اولِ نام برای جای‌خالیِ عکس: نخستین حرف یا رقمِ واقعی، بی فاصله، نیم‌فاصله
   *  (ZWNJ) و نشانه؛ لاتین بزرگ. نامِ خالی ⇐ «؟».
   */
  fun initials(name: String): String {
    val c = name.firstOrNull { it.isLetterOrDigit() } ?: return "؟"
    return c.uppercaseChar().toString()
  }
}
