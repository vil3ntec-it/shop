package ir.vil3ntec.tohid.migrate

/**
 *  کلیدهای گزارشِ تطبیق و مقیاسِ هرکدام (عددِ تازه = عددِ قدیم × مقیاس).
 *
 *   rows/<جدول>                 تعدادِ ردیف                       ×۱
 *   sales/count                 تعدادِ فاکتورِ فعال                ×۱
 *   sales/{gross,discount,net,returnAmount,expenses}  پول        ×۱۰۰
 *   sales/{cogs,grossProfit,netProfit}  مقدار×بها               ×۱۰۰٬۰۰۰
 *   pay/<نوع>                   جمعِ پرداخت‌ها                     ×۱۰۰
 *   customer/<شناسه>            ماندهٔ هر مشتری                    ×۱۰۰
 *   supplier/<شناسه>            ماندهٔ هر تأمین‌کننده              ×۱۰۰
 *   stock/<شناسه>               موجودیِ هر کالا                   ×۱٬۰۰۰
 *   month/<سال-ماه>/sales       فروشِ خالصِ ماهِ خورشیدی           ×۱۰۰
 *   month/<سال-ماه>/{grossProfit,netProfit}  سودِ ماه           ×۱۰۰٬۰۰۰
 */
object Keys {
  fun scaleOf(key: String): Long = when {
    key.startsWith("rows/") || key == "sales/count" -> 1L
    key.startsWith("stock/") -> Scale.QTY
    key.endsWith("Profit") || key == "sales/cogs" -> Scale.COST
    else -> Scale.MONEY
  }
}

enum class Verdict {
  /** برابر، به دقتِ مدلِ تازه */
  EQUAL,
  /** فقط اعشارِ اضافیِ Double در ورودی (بیش از ۲ رقم برای پول، ۳ رقم برای مقدار) */
  DOUBLE,
  /** دقیقاً برابرِ اثرِ ناهنجاری‌های فهرست‌شده (و شاید اعشار) */
  EXPLAINED,
  /** اختلافِ بی‌دلیل — مهاجرت نباید جلو برود */
  UNEXPLAINED,
}

data class Row(
  val key: String,
  val scale: Long,
  /** عددِ قدیم، به مقیاس و گردشده */
  val old: Long?,
  /** عددِ قدیم همان‌طور که برنامه حساب می‌کند */
  val oldRaw: Double?,
  val new: Long?,
  val verdict: Verdict,
  val reasons: List<String>,
)

object Reconcile {

  /**
   *  @param old      محاسبهٔ قدیم روی دفترِ واقعی
   *  @param rounded  همان محاسبهٔ قدیم روی دفتری که ورودی‌هایش به دقتِ مدلِ تازه گرد شده
   *  @param new      محاسبهٔ مدلِ تازه
   */
  fun compare(
    old: Map<String, Double>,
    rounded: Map<String, Double>,
    new: Map<String, Long>,
    anomalies: List<Anomaly>,
  ): List<Row> {
    val effect = HashMap<String, Long>()
    val causes = HashMap<String, MutableSet<String>>()
    anomalies.forEach { a ->
      a.deltas.forEach { (k, v) ->
        effect.merge(k, v, Long::plus)
        causes.getOrPut(k) { sortedSetOf() } += a.code
      }
    }
    val keys = LinkedHashSet<String>().apply { addAll(old.keys); addAll(new.keys) }
    return keys.map { key ->
      val scale = Keys.scaleOf(key)
      val o = old[key]
      val n = new[key]
      val hint = causes[key].orEmpty().toList()
      if (o == null || n == null) {
        val side = if (o == null) "ONLY_IN_NEW" else "ONLY_IN_OLD"
        return@map Row(key, scale, o?.let { Scale.ofDouble(it, scale) }, o, n, Verdict.UNEXPLAINED, listOf(side) + hint)
      }
      val oS = Scale.ofDouble(o, scale)
      val rS = Scale.ofDouble(rounded[key] ?: o, scale)
      val verdict = when {
        oS == n -> Verdict.EQUAL
        rS == n -> Verdict.DOUBLE
        rS + (effect[key] ?: 0L) == n -> Verdict.EXPLAINED
        else -> Verdict.UNEXPLAINED
      }
      val reasons = when (verdict) {
        Verdict.EQUAL -> emptyList()
        Verdict.DOUBLE -> listOf("DOUBLE_DECIMALS")
        Verdict.EXPLAINED -> (if (rS != oS) listOf("DOUBLE_DECIMALS") else emptyList()) + hint
        Verdict.UNEXPLAINED -> hint
      }
      Row(key, scale, oS, o, n, verdict, reasons)
    }
  }
}
