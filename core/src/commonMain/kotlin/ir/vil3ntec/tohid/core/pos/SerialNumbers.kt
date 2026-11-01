package ir.vil3ntec.tohid.core.pos

/*
 *  کالای سریال‌دار — POS-28، §۶.۱: «ثبت سریال هنگام فروش. پایان بدون سریال کامل ممکن نیست.»
 *
 *  ردیفِ کالای سریال‌دار به ازای هر دانه یک سریال می‌خواهد؛ تا شمارِ سریال‌ها با مقدار برابر نشود، فاکتور
 *  بسته نمی‌شود. سریال در همان فاکتور تکراری نیست و سریالی که پیش‌تر فروخته شده (و برنگشته) دوباره فروخته
 *  نمی‌شود. مقدارِ اعشاری برای کالای سریال‌دار معنا ندارد.
 *
 *  کادر: هر سریال یک خط (اسکنر هم هر بار Enter می‌زند)، یا با «،» / «,» / «;» جدا. فاصلهٔ دو سر بیرون،
 *  رقمِ فارسی/عربی لاتین؛ حروف دست نمی‌خورند.
 *  منطقِ خالص (commonMain)؛ آزمون: `SerialNumbersTest`.
 */
object SerialNumbers {

  const val MAX_LEN = 64
  const val MAX_PER_LINE = 500

  sealed interface Problem {
    data class Missing(val have: Int, val need: Int) : Problem
    data class TooMany(val have: Int, val need: Int) : Problem
    data class Duplicate(val serial: String) : Problem
    data class AlreadySold(val serial: String) : Problem
    data object Fractional : Problem
  }

  private fun latin(c: Char): Char = when (c) {
    in '۰'..'۹' -> '0' + (c - '۰')
    in '٠'..'٩' -> '0' + (c - '٠')
    else -> c
  }

  /** کادر ⇒ سریال‌ها به ترتیب؛ تهی بیرون، هر کدام حداکثر [MAX_LEN]، روی هم حداکثر [MAX_PER_LINE] */
  fun parse(text: String): List<String> =
    text.split('\n', '\r', ',', '،', ';', '؛').map { s -> s.trim().map(::latin).joinToString("").take(MAX_LEN) }
      .filter { it.isNotEmpty() }.take(MAX_PER_LINE)

  fun format(serials: List<String>): String = serials.joinToString("\n")

  /**
   *  ایرادِ سریال‌های یک ردیف با مقدارِ [qtyMilli] (هزارم)؛ `null` ⇒ ردیف کامل است.
   *  [sold]: سریال‌هایی که در فاکتورهای دیگر فروخته شده‌اند و برنگشته‌اند.
   */
  fun problem(serials: List<String>, qtyMilli: Long, sold: Set<String>): Problem? {
    if (qtyMilli % 1000 != 0L) return Problem.Fractional
    val need = (qtyMilli / 1000).toInt()
    val seen = HashSet<String>()
    serials.firstOrNull { !seen.add(it) }?.let { return Problem.Duplicate(it) }
    serials.firstOrNull { it in sold }?.let { return Problem.AlreadySold(it) }
    return when {
      serials.size < need -> Problem.Missing(serials.size, need)
      serials.size > need -> Problem.TooMany(serials.size, need)
      else -> null
    }
  }
}
