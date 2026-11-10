package ir.vil3ntec.tohid.core.sale

import ir.vil3ntec.tohid.core.promo.Loyalty

/*
 *  مالیاتِ فاکتور — POS-37، §۶.۱ («مالیات قابل تنظیم برای ردیف یا فاکتور. پیش‌فرض خاموش.»).
 *
 *  D-11: نرخ و قاعده را دکان با حسابدارش می‌گذارد؛ این‌جا هیچ نرخِ پیش‌فرضی نیست. بی تنظیم (`null`) ⇒ مالیات ۰ و
 *  فاکتور بیت‌به‌بیت همان امروز. مالیات «روی» مبلغ است (نه درونِ قیمت): نهایی = جمع − تخفیف + مالیات (+ گردِ نقدیِ PAY-09).
 *
 *  • **فاکتور** ([Mode.INVOICE]): یک نرخ روی (جمعِ اقلام − تخفیفِ فاکتور)، یک بار گرد.
 *  • **ردیف** ([Mode.LINE]): هر ردیف نرخِ کالای خودش ([Config.rates]؛ نبود ⇒ نرخِ پایه، `0` ⇒ معاف) روی سهمِ خودش از
 *    (جمع − تخفیف)، و هر ردیف جدا گرد. سهمِ تخفیفِ هر ردیف همان «بیشترین باقی‌مانده» ِ `Loyalty.split` است (به پول؛ برابر
 *    ⇒ ردیفِ جلوتر)، پس جمعِ سهم‌ها دقیقاً همان تخفیف است.
 *  گرد: نیمه رو به بالا به افغانیِ کامل ([STEP])، مثلِ مبلغِ ردیف (`LinePricing.grossMinor`). نرخ به صدمِ درصد
 *  (۱۰٪ = ۱۰۰۰، سقف [FULL_BP]). همهٔ مبلغ‌ها به پول (یک‌صدمِ افغانی)، عددِ صحیح و بی Double.
 *
 *  ⛔ هم‌زادِ `license/sale-tax.js` (سرور و وب)؛ هر دو با `core/test-vectors/sale-tax-v1.json`
 *  (`SaleTaxVectorsTest`، `server/test/sale-tax.test.js`). یکی را بی دیگری و بی بردار عوض نکنید.
 */
object SaleTax {

  const val FULL_BP = 10_000
  const val STEP = 100L
  const val MAX_RATES = 200
  const val MAX_EXACT = 9_007_199_254_740_991L

  enum class Mode(val wire: String) {
    INVOICE("invoice"), LINE("line");

    companion object {
      fun of(wire: String?): Mode? = entries.firstOrNull { it.wire == wire }
    }
  }

  /** [rates]: شناسهٔ کالا ⇒ نرخ (فقط در حالتِ ردیف) */
  data class Config(val mode: Mode, val rateBp: Int, val rates: Map<String, Int> = emptyMap())

  /** ردیفِ سبد: کالا (`null` برای قلمِ آزاد و هزینهٔ خودکار) و مبلغِ ردیف به پول */
  data class Line(val productId: String?, val grossMinor: Long)

  /** [lineTaxMinor] فقط در حالتِ ردیف (به ترتیبِ ردیف‌ها)؛ حالتِ فاکتور ⇒ خالی */
  data class Result(val taxMinor: Long, val lineTaxMinor: List<Long>)

  private val NONE = Result(0L, emptyList())

  private fun rateOk(bp: Int): Boolean = bp in 0..FULL_BP

  /**
   *  تنظیمِ درست یا `null` (= خاموش): نرخِ بیرون از ۰…۱۰۰٪ و کلیدِ خالی کنار می‌روند؛ بیش از [MAX_RATES] نرخِ کالا ⇒
   *  همان نخستین‌ها (به ترتیبِ کلید)؛ بی هیچ نرخِ مثبت ⇒ `null`.
   */
  fun clean(c: Config?): Config? {
    if (c == null || !rateOk(c.rateBp)) return null
    val rates = if (c.mode != Mode.LINE) emptyMap()
    else c.rates.entries.filter { it.key.isNotBlank() && rateOk(it.value) }.sortedBy { it.key }.take(MAX_RATES)
      .associate { it.key to it.value }
    val any = c.rateBp > 0 || rates.values.any { it > 0 }
    return if (any) Config(c.mode, c.rateBp, rates) else null
  }

  /** round(پول × نرخ ÷ ۱۰۰۰۰) نیمه رو به بالا به [STEP] — دقیق و بی سرریز */
  fun ofMinor(baseMinor: Long, rateBp: Int): Long {
    if (baseMinor <= 0 || rateBp <= 0) return 0L
    val halves = Loyalty.mulDiv(baseMinor, rateBp.toLong(), FULL_BP * STEP / 2)
    return (halves + 1) / 2 * STEP
  }

  /**
   *  مالیاتِ این سبد. [discountMinor] تخفیفِ فاکتور (به سقفِ جمع)؛ ردیفِ منفی صفر شمرده می‌شود.
   *  تنظیمِ خاموش یا سبدِ بی مبلغ ⇒ صفر. جمع یا نهاییِ بیرون از ۲^۵۳−۱ ⇒ `null` (خطا، نه عددِ گردشده).
   */
  fun compute(lines: List<Line>, discountMinor: Long, config: Config?): Result? {
    val c = clean(config) ?: return NONE
    val gross = lines.map { maxOf(0L, it.grossMinor) }
    var whole = 0L
    for (g in gross) {
      if (g > MAX_EXACT || whole > MAX_EXACT - g) return null
      whole += g
    }
    if (whole == 0L) return if (c.mode == Mode.LINE) Result(0L, gross.map { 0L }) else NONE
    val d = discountMinor.coerceIn(0L, whole)
    val r = if (c.mode == Mode.INVOICE) Result(ofMinor(whole - d, c.rateBp), emptyList())
    else {
      val shares = Loyalty.split(gross, d) ?: return null
      val each = lines.indices.map { i ->
        val rate = lines[i].productId?.takeIf { it.isNotBlank() }?.let { c.rates[it] } ?: c.rateBp
        ofMinor(gross[i] - shares[i], rate)
      }
      Result(each.sum(), each)
    }
    //  نهایی (جمع − تخفیف + مالیات) هم باید در بازهٔ دقیق بماند
    return if (r.taxMinor > MAX_EXACT - (whole - d)) null else r
  }

  enum class Problem { BAD_LINE, BAD_RATE, UNKNOWN_ITEM, TOO_MANY }

  data class Parsed(val rates: Map<String, Int>, val problems: List<Pair<String, Problem>>)

  /** «۱۰»، «۱۰٫۵٪» ⇒ صدمِ درصد؛ نادرست یا بیرون از ۰…۱۰۰ ⇒ `null` */
  fun rateOf(text: String): Int? {
    val t = text.trim().trimEnd('%', '٪').trim()
    val v = ir.vil3ntec.tohid.core.product.DecimalText.scaled(t, 2) ?: return null
    return if (v in 0..FULL_BP.toLong()) v.toInt() else null
  }

  /**
   *  کادرِ نرخِ کالاها (حالتِ ردیف): هر خط «کالا، کالا = نرخ» (کالا با نام یا بارکد؛ نرخ به درصد، ۰ = معاف).
   *  [resolve]: نام یا بارکد ⇒ شناسه. خطِ نادرست جدا گفته می‌شود و در نرخ‌ها نمی‌آید.
   */
  fun parseRates(text: String, resolve: (String) -> String?): Parsed {
    val out = LinkedHashMap<String, Int>()
    val bad = ArrayList<Pair<String, Problem>>()
    for (raw in text.split('\n', '\r')) {
      val line = raw.trim()
      if (line.isEmpty()) continue
      val eq = line.lastIndexOf('=')
      if (eq < 0) { bad += line to Problem.BAD_LINE; continue }
      val items = line.substring(0, eq).split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }
      val rate = rateOf(line.substring(eq + 1))
      val ids = items.map(resolve)
      when {
        items.isEmpty() -> bad += line to Problem.BAD_LINE
        rate == null -> bad += line to Problem.BAD_RATE
        ids.any { it == null } -> bad += line to Problem.UNKNOWN_ITEM
        out.size + ids.filterNotNull().distinct().count { it !in out } > MAX_RATES -> bad += line to Problem.TOO_MANY
        else -> ids.filterNotNull().forEach { out[it] = rate }
      }
    }
    return Parsed(out, bad)
  }

  /** نرخ‌ها ⇒ کادر؛ کالاهای هم‌نرخ در یک خط. [nameOf] نامِ کالا، [percent] نرخِ خوانا */
  fun formatRates(rates: Map<String, Int>, nameOf: (String) -> String, percent: (Int) -> String): String =
    rates.entries.groupBy({ it.value }, { it.key }).entries.sortedBy { it.key }
      .joinToString("\n") { (bp, ids) -> ids.joinToString("، ", transform = nameOf) + " = " + percent(bp) }
}
