package ir.vil3ntec.tohid.core.stock

/**
 *  تحلیلِ موجودی — INV-15 (تحلیلِ ABC) و INV-16 (ارزشِ موجودی به بها و به قیمتِ فروش، و سودِ بالقوه).
 *
 *  خالص (commonMain، §۴۰.۳): نه android.*، نه java.*، نه ساعت، نه دیسک. ورودی را برنامه می‌سازد
 *  (کدام دوره، کدام بها) و این‌جا فقط حساب می‌شود؛ پس همین کد در گوشی، کامپیوتر و آیفون یکی است.
 *
 *  یکاها همان قاعدهٔ پروژه: پول به **واحدِ کوچک** (Long)، مقدار به **هزارمِ واحد** (Long)، درصد به
 *  **بیسیس‌پوینت** (۱۰۰ = ۱٪). هر عدد باید در بازهٔ دقیقِ ±۲^۵۳ بماند (`MAX_EXACT`، همان مرزِ Money)؛
 *  ردیفی که حاصلش بیرون بزند حساب نمی‌شود و در `outOfRange` می‌آید، نه این‌که بی‌صدا عددِ غلط بسازد.
 */
object StockAnalysis {
  /** همان `MAX_EXACT` ِ core/money: بزرگ‌ترین عددِ صحیحی که JS بی‌خطا نگه می‌دارد. */
  const val MAX_EXACT: Long = 9_007_199_254_740_991L
  private const val BP: Long = 10_000

  // ═════════════════════════════════ ABC (INV-15) ═════════════════════════════════

  enum class Abc { A, B, C }

  /** یک کالا و ارزشِ آن در دوره (معمولاً فروشِ خالص به واحدِ کوچک). */
  data class AbcInput(val id: String, val valueMinor: Long)

  data class AbcRow(
    val id: String,
    val valueMinor: Long,
    /** سهمِ همین کالا از کل (بیسیس‌پوینت، گردِ نیمه به بالا) */
    val shareBp: Long,
    /** سهمِ انباشته تا همین کالا، خودش هم حساب (بیسیس‌پوینت) */
    val cumulativeBp: Long,
    val cls: Abc,
  )

  data class AbcResult(
    /** پرارزش اول؛ برابر ⇒ شناسه به ترتیبِ الفبا (تا خروجی همیشه یکی باشد) */
    val rows: List<AbcRow>,
    val totalMinor: Long,
    val count: Map<Abc, Int>,
    val valueMinor: Map<Abc, Long>,
    /** سهمِ هر رده از کل (بیسیس‌پوینت) */
    val shareBp: Map<Abc, Long>,
  )

  /**
   *  ردهٔ ABC به روشِ «سهمِ انباشته» (پارتو): کالاها از پرارزش به کم‌ارزش چیده می‌شوند؛ کالایی
   *  A است که سهمِ انباشتهٔ **پیش از آن** زیرِ `aCutBp` باشد (پس کالایی که از مرز می‌گذرد هم A
   *  است)، B اگر زیرِ `bCutBp`، و بقیه C. پیش‌فرض ۸۰٪ / ۹۵٪.
   *
   *  - ارزشِ صفر یا منفی (بی‌فروش، یا برگشتِ بیش از فروش) ⇒ همیشه C با سهمِ صفر.
   *  - شناسهٔ تکراری ⇒ ارزش‌هایش جمع می‌شود.
   *  - کلِ صفر ⇒ همه C (هیچ کالایی «پرفروش» نیست).
   */
  fun abc(items: List<AbcInput>, aCutBp: Long = 8_000, bCutBp: Long = 9_500): AbcResult {
    require(aCutBp in 1..BP && bCutBp in aCutBp..BP) { "مرزِ ABC نامعتبر: $aCutBp / $bCutBp" }
    val merged = LinkedHashMap<String, Long>()
    for (it in items) {
      val v = it.valueMinor.coerceIn(0, MAX_EXACT)
      merged[it.id] = satAdd(merged[it.id] ?: 0L, v)
    }
    val sorted = merged.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
    var total = 0L
    for (e in sorted) total = satAdd(total, e.value)

    val rows = ArrayList<AbcRow>(sorted.size)
    var cum = 0L
    for (e in sorted) {
      val v = e.value
      val cls = when {
        v <= 0L || total <= 0L -> Abc.C
        below(cum, total, aCutBp) -> Abc.A
        below(cum, total, bCutBp) -> Abc.B
        else -> Abc.C
      }
      cum = satAdd(cum, v)
      rows += AbcRow(e.key, v, bpOf(v, total), bpOf(cum, total), cls)
    }
    val count = Abc.entries.associateWith { c -> rows.count { it.cls == c } }
    val value = Abc.entries.associateWith { c -> rows.filter { it.cls == c }.fold(0L) { s, r -> satAdd(s, r.valueMinor) } }
    return AbcResult(rows, total, count, value, value.mapValues { bpOf(it.value, total) })
  }

  // ═══════════════════════════ ارزش و سودِ بالقوه (INV-16) ═══════════════════════════

  /**
   *  یک کالا: موجودیِ امروز، بهای یک واحد (هر روشی که برنامه دارد: آخرین قیمتِ خرید، یا بهای
   *  میانگین وقتی موتورِ تازهٔ موجودی روشن است) و قیمتِ فروشِ یک واحد. بها/قیمتِ ≤ ۰ یعنی «ثبت نشده».
   */
  data class ValueLine(val id: String, val onHandMilli: Long, val unitCostMinor: Long, val unitPriceMinor: Long)

  /** ارزشِ یک کالای موجود (۰ یعنی بها/قیمت ثبت نشده) */
  data class LineValue(val id: String, val costMinor: Long, val retailMinor: Long)

  data class Valuation(
    /** Σ موجودیِ مثبت × بها، فقط کالاهایی که بها دارند */
    val costMinor: Long,
    /** Σ موجودیِ مثبت × قیمتِ فروش، فقط کالاهایی که قیمت دارند */
    val retailMinor: Long,
    /** Σ (ارزشِ فروش − ارزشِ بها) فقط کالاهایی که **هر دو** را دارند — تا سود بزرگ‌تر از واقع نشود */
    val potentialProfitMinor: Long,
    /** سودِ بالقوه ÷ ارزشِ فروشِ همان کالاها؛ null اگر مخرج صفر */
    val marginBp: Long?,
    /** شمارِ کالاهای با موجودیِ مثبت */
    val inStock: Int,
    /** موجود ولی بی بها ⇒ ارزش و سود ناقص است */
    val missingCost: List<String>,
    /** موجود ولی بی قیمتِ فروش */
    val missingPrice: List<String>,
    /** قیمتِ فروش زیرِ بها ⇒ سودِ این کالا منفی است */
    val belowCost: List<String>,
    /** موجودیِ منفی (فروشِ بیش از ورود) — در هیچ جمعی نمی‌آید؛ وظیفهٔ بررسی است */
    val negative: List<String>,
    /** حاصل بیرون از بازهٔ دقیق ⇒ حساب نشد */
    val outOfRange: List<String>,
    /** ارزشِ هر کالای موجودی که حساب شد، به همان ترتیبِ ورودی (برای جدول؛ جمعش همان بالاست) */
    val lines: List<LineValue>,
  ) {
    /** عدد کامل است؟ (هیچ کالای موجودی بی بها یا بی قیمت نیست) */
    val complete: Boolean get() = missingCost.isEmpty() && missingPrice.isEmpty() && outOfRange.isEmpty()
  }

  /**
   *  ارزشِ ردیف = گردِ نیمه‌به‌بالای (مقدارِ هزارم × قیمتِ واحد ÷ ۱۰۰۰)، به واحدِ کوچک و **بی** پلهٔ
   *  گردکردنِ نقدی (این ارزشِ دفتری است، نه پولی که دست به دست شود).
   */
  fun valuation(lines: List<ValueLine>): Valuation {
    var cost = 0L
    var retail = 0L
    var profit = 0L
    var bothRetail = 0L
    var inStock = 0
    val missingCost = ArrayList<String>()
    val missingPrice = ArrayList<String>()
    val belowCost = ArrayList<String>()
    val negative = ArrayList<String>()
    val outOfRange = ArrayList<String>()
    val values = ArrayList<LineValue>()
    for (l in lines) {
      if (l.onHandMilli < 0) { negative += l.id; continue }
      if (l.onHandMilli == 0L) continue
      inStock++
      val hasCost = l.unitCostMinor > 0
      val hasPrice = l.unitPriceMinor > 0
      val c = if (hasCost) lineValue(l.onHandMilli, l.unitCostMinor) else 0L
      val r = if (hasPrice) lineValue(l.onHandMilli, l.unitPriceMinor) else 0L
      if (c < 0 || r < 0) { outOfRange += l.id; continue }
      if (!hasCost) missingCost += l.id
      if (!hasPrice) missingPrice += l.id
      values += LineValue(l.id, c, r)
      cost = satAdd(cost, c)
      retail = satAdd(retail, r)
      if (hasCost && hasPrice) {
        profit = satAdd(profit, r - c)
        bothRetail = satAdd(bothRetail, r)
        if (l.unitPriceMinor < l.unitCostMinor) belowCost += l.id
      }
    }
    val margin = if (bothRetail > 0) bpOf(profit, bothRetail) else null
    return Valuation(cost, retail, profit, margin, inStock, missingCost, missingPrice, belowCost, negative, outOfRange, values)
  }

  // ═════════════════════════════════════ کمکی ═════════════════════════════════════

  /** مقدار × قیمت ÷ ۱۰۰۰ گردِ نیمه به بالا؛ بیرون از بازهٔ دقیق ⇒ −۱ (نشانهٔ «حساب نشد») */
  internal fun lineValue(qtyMilli: Long, unitMinor: Long): Long {
    if (qtyMilli !in 0..MAX_EXACT || unitMinor !in 0..MAX_EXACT) return -1
    if (qtyMilli == 0L || unitMinor == 0L) return 0
    if (qtyMilli > MAX_EXACT / unitMinor) return -1
    return (qtyMilli * unitMinor + 500) / 1000
  }

  /** جمعِ بی‌سرریز برای عددهای نامنفی، سقف `MAX_EXACT` (جمعِ گزارش هرگز دور نمی‌زند) */
  internal fun satAdd(a: Long, b: Long): Long {
    val s = a + b
    return when {
      s > MAX_EXACT -> MAX_EXACT
      s < -MAX_EXACT -> -MAX_EXACT
      else -> s
    }
  }

  /**
   *  `part / whole` به بیسیس‌پوینت، گردِ نیمه رو به +∞ (معنای `Math.round`، برای صورتِ منفی هم)؛
   *  `whole ≤ ۰` ⇒ ۰. بیرون از بازهٔ ضربِ Long با Double (فقط برای عددهایی که در دکان پیش نمی‌آیند).
   */
  internal fun bpOf(part: Long, whole: Long): Long {
    if (whole <= 0L || part == 0L) return 0
    val mag = if (part < 0) -part else part
    return if (mag <= (Long.MAX_VALUE - whole) / BP) (part * BP + whole / 2).floorDiv(whole)
    else kotlin.math.floor(part.toDouble() * BP / whole.toDouble() + 0.5).toLong()
  }

  /** `cum / total < cutBp / ۱۰۰۰۰` بی تقسیم (دقیق تا جایی که ضرب جا شود) */
  internal fun below(cum: Long, total: Long, cutBp: Long): Boolean =
    if (cum <= Long.MAX_VALUE / BP && total <= Long.MAX_VALUE / BP) cum * BP < cutBp * total
    else cum.toDouble() * BP < cutBp.toDouble() * total.toDouble()
}
