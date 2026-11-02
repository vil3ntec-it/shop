package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri

/**
 *  گزارشِ مالیاتِ سه‌ماهه با یادآورِ سررسید (RPT-10، LOC-02، سناریوی S49) — منطقِ خالص (commonMain).
 *
 *  • سه‌ماهه از ماهِ آغازِ سالِ مالیِ قابلِ تنظیم (`startMonth`، پیش‌فرض ۱ = حمل، همان
 *    `SolarHijri.fiscalQuarterRange`)؛ سه‌ماهه‌ای که از حوت می‌گذرد به سالِ خورشیدیِ بعد می‌رود.
 *    سالِ مالی با سالِ خورشیدیِ ماهِ آغازش نام می‌گیرد.
 *  • پایه‌ها فقط از ردیف‌های واقعی ([Doc]) و به پول (۰٫۰۱ افغانی): فروش، برگشتِ فروش و خرید. نرخ از
 *    تنظیمات (`rateBp`، ده‌هزارم؛ ۰ = هنوز نرخی ثبت نشده) — D-11: نرخ را حسابدار تأیید می‌کند و هیچ
 *    انطباقِ قانونی ادعا نمی‌شود (D-25). سند مالیاتِ جدا روی فاکتور ندارد، پس «مالیاتِ فروش» همان
 *    نرخ × فروشِ دوره است و «مالیاتِ برگشت» نرخ × برگشت.
 *  • سررسید: روزِ `dueDay` ِ ماهی که `dueMonths` ماه پس از ماهِ آخرِ سه‌ماهه است (پیش‌فرض ۱۵ ِ ماهِ
 *    بعد: سرطان–سنبله ⇒ ۱۵ میزان)؛ روزِ بلندتر از ماه ⇒ روزِ آخرِ همان ماه.
 *  • یادآور فقط درونِ برنامه: از `remindDays` روز پیش تا خودِ روزِ سررسید؛ پس از آن هیچ.
 *
 *  آزمون: `QuarterTaxTest` (commonTest).
 */
object QuarterTax {

  const val MAX_RATE_BP = 10_000
  const val MAX_REMIND_DAYS = 60
  const val MAX_DUE_MONTHS = 3

  data class Config(
    /** ماهِ آغازِ سالِ مالی (۱ = حمل … ۱۲ = حوت) */
    val startMonth: Int = 1,
    /** نرخ به ده‌هزارم (۱۰۰ = ۱٪)؛ ۰ = نرخی ثبت نشده */
    val rateBp: Int = 0,
    /** روزِ سررسید در ماهِ سررسید */
    val dueDay: Int = 15,
    /** سررسید چند ماه پس از ماهِ آخرِ سه‌ماهه (۰ = همان ماه) */
    val dueMonths: Int = 1,
    /** یادآور از چند روز پیش از سررسید */
    val remindDays: Int = 10,
  ) {
    /** هر عددِ بیرون از بازه به نزدیک‌ترین مرز — تنظیمِ خراب گزارش را نمی‌شکند */
    fun clamped(): Config = Config(
      startMonth = startMonth.coerceIn(1, 12),
      rateBp = rateBp.coerceIn(0, MAX_RATE_BP),
      dueDay = dueDay.coerceIn(1, 31),
      dueMonths = dueMonths.coerceIn(0, MAX_DUE_MONTHS),
      remindDays = remindDays.coerceIn(0, MAX_REMIND_DAYS),
    )
  }

  /** سه‌ماههٔ `q` (۱ تا ۴) از سالِ مالیِ `fiscalYear` */
  data class Quarter(val fiscalYear: Int, val q: Int)

  /** ماهِ شمارهٔ `offset` (از صفر) از آغازِ سالِ مالیِ `fy` ⇒ (سالِ خورشیدی، ماه) */
  private fun monthAt(fy: Int, startMonth: Int, offset: Int): Pair<Int, Int> {
    val idx = startMonth - 1 + offset
    return Pair(fy + idx / 12, idx % 12 + 1)
  }

  /** ماه‌های سه‌ماهه به ترتیب ⇒ (سالِ خورشیدی، ماه) */
  fun months(fy: Int, q: Int, startMonth: Int = 1): List<Pair<Int, Int>> =
    if (q !in 1..4 || startMonth !in 1..12) emptyList() else (0..2).map { monthAt(fy, startMonth, (q - 1) * 3 + it) }

  /** (از، تا) ِ میلادیِ سه‌ماهه؛ سه‌ماهه، ماهِ آغاز یا سالِ نادرست ⇒ null */
  fun range(fy: Int, q: Int, startMonth: Int = 1): Pair<String, String>? {
    val m = months(fy, q, startMonth).takeIf { it.size == 3 } ?: return null
    val (y1, m1) = m[0]
    val (y3, m3) = m[2]
    if (y1 !in SolarHijri.MIN_YEAR..SolarHijri.MAX_YEAR || y3 !in SolarHijri.MIN_YEAR..SolarHijri.MAX_YEAR) return null
    val first = SolarHijri.fromSolar(y1, m1, 1) ?: return null
    val last = SolarHijri.fromSolar(y3, m3, SolarHijri.monthLength(y3, m3)) ?: return null
    return Pair(first, last)
  }

  /** سه‌ماههٔ مالیِ یک روزِ میلادی؛ تاریخِ نادرست ⇒ null */
  fun quarterOf(iso: String, startMonth: Int = 1): Quarter? {
    if (startMonth !in 1..12) return null
    val j = SolarHijri.toSolar(iso) ?: return null
    val offset = (j.month - startMonth + 12) % 12
    val fy = if (j.month >= startMonth) j.year else j.year - 1
    return Quarter(fy, offset / 3 + 1)
  }

  fun previous(x: Quarter): Quarter = if (x.q > 1) Quarter(x.fiscalYear, x.q - 1) else Quarter(x.fiscalYear - 1, 4)

  fun next(x: Quarter): Quarter = if (x.q < 4) Quarter(x.fiscalYear, x.q + 1) else Quarter(x.fiscalYear + 1, 1)

  /** روزِ سررسیدِ سه‌ماهه (`YYYY-MM-DD`)؛ نادرست ⇒ null */
  fun dueDate(x: Quarter, cfg: Config): String? {
    if (x.q !in 1..4) return null
    val c = cfg.clamped()
    val (y, m) = monthAt(x.fiscalYear, c.startMonth, (x.q - 1) * 3 + 2 + c.dueMonths)
    if (y !in SolarHijri.MIN_YEAR..SolarHijri.MAX_YEAR) return null
    return SolarHijri.fromSolar(y, m, minOf(c.dueDay, SolarHijri.monthLength(y, m)))
  }

  /** یادآورِ سررسید: سه‌ماهه، روزِ سررسید و روزهای مانده (۰ = امروز) */
  data class Due(val quarter: Quarter, val dueIso: String, val daysLeft: Int)

  /**
   *  یادآورِ امروز یا null: نزدیک‌ترین سررسیدی که از امروز تا `remindDays` روزِ دیگر است. سررسید
   *  حداکثر سه ماه پس از پایانِ سه‌ماهه است، پس سه‌ماههٔ جاری و دو سه‌ماههٔ پیشین بس است.
   */
  fun reminder(today: String, cfg: Config): Due? {
    val c = cfg.clamped()
    val now = CivilDays.jdnOfIso(today) ?: return null
    val cur = quarterOf(today, c.startMonth) ?: return null
    val candidates = listOf(previous(previous(cur)), previous(cur), cur)
    return candidates.mapNotNull { x ->
      val due = dueDate(x, c) ?: return@mapNotNull null
      val left = (CivilDays.jdnOfIso(due) ?: return@mapNotNull null) - now
      if (left in 0..c.remindDays) Due(x, due, left) else null
    }.minByOrNull { it.daysLeft }
  }

  /* ------------------------------ جمع‌ها ------------------------------ */

  enum class Kind { SALE, RETURN, PURCHASE }

  /** یک ردیفِ واقعی: فاکتورِ فروشِ فعال، ردیفِ برگشتِ فروش یا ردیفِ خرید — مبلغ به پول */
  data class Doc(val kind: Kind, val date: String, val amountMinor: Long)

  data class Summary(
    val from: String,
    val to: String,
    val rateBp: Int,
    val salesCount: Int,
    val salesMinor: Long,
    val returnsCount: Int,
    val returnsMinor: Long,
    /** درآمدِ ناخالصِ خالص = فروش − برگشت */
    val netMinor: Long,
    val salesTaxMinor: Long,
    val returnsTaxMinor: Long,
    /** مالیاتِ خالص = مالیاتِ فروش − مالیاتِ برگشت */
    val netTaxMinor: Long,
    val purchasesCount: Int,
    val purchasesMinor: Long,
  )

  /** مبلغ × نرخ (ده‌هزارم) با گردِ نیمه به دورتر از صفر؛ بی سرریز برای هر مبلغِ Long ِ واقعی */
  fun taxOf(amountMinor: Long, rateBp: Int): Long {
    val bp = rateBp.coerceIn(0, MAX_RATE_BP).toLong()
    if (amountMinor < 0) return -taxOf(-amountMinor, rateBp)
    return (amountMinor / 10_000) * bp + ((amountMinor % 10_000) * bp + 5_000) / 10_000
  }

  /** ردیف‌های بازهٔ [from]..[to] (تاریخِ خودِ ردیف) ⇒ جمع‌ها */
  fun summarize(docs: List<Doc>, from: String, to: String, rateBp: Int): Summary {
    val inRange = docs.filter { it.date in from..to }
    val sales = inRange.filter { it.kind == Kind.SALE }
    val returns = inRange.filter { it.kind == Kind.RETURN }
    val purchases = inRange.filter { it.kind == Kind.PURCHASE }
    val salesMinor = sales.sumOf { it.amountMinor }
    val returnsMinor = returns.sumOf { it.amountMinor }
    val rate = rateBp.coerceIn(0, MAX_RATE_BP)
    val salesTax = taxOf(salesMinor, rate)
    val returnsTax = taxOf(returnsMinor, rate)
    return Summary(
      from = from, to = to, rateBp = rate,
      salesCount = sales.size, salesMinor = salesMinor,
      returnsCount = returns.size, returnsMinor = returnsMinor,
      netMinor = salesMinor - returnsMinor,
      salesTaxMinor = salesTax, returnsTaxMinor = returnsTax, netTaxMinor = salesTax - returnsTax,
      purchasesCount = purchases.size, purchasesMinor = purchases.sumOf { it.amountMinor },
    )
  }

  /* ------------------------------ نرخ ------------------------------ */

  /** «2.5» یا «۲٫۵» (درصد) ⇒ ۲۵۰ ده‌هزارم؛ بیش از دو رقمِ اعشار، منفی، بیش از ۱۰۰ یا نامفهوم ⇒ null */
  fun parseRate(text: String): Int? {
    val s = buildString {
      for (ch in text.trim()) append(
        when (ch) {
          in '۰'..'۹' -> '0' + (ch - '۰')
          in '٠'..'٩' -> '0' + (ch - '٠')
          '٫', '،', ',' -> '.'
          else -> ch
        },
      )
    }.removeSuffix("%").removeSuffix("٪").trim()
    if (s.isEmpty()) return null
    val parts = s.split('.')
    if (parts.size > 2 || parts.any { p -> p.any { it !in '0'..'9' } }) return null
    val whole = parts[0].ifEmpty { "0" }
    val frac = parts.getOrElse(1) { "" }
    if (frac.length > 2 || whole.length > 3) return null
    val bp = whole.toInt() * 100 + frac.padEnd(2, '0').toInt()
    return bp.takeIf { it in 0..MAX_RATE_BP }
  }

  /** ده‌هزارم ⇒ «2.5» (بی صفرِ اضافه) */
  fun rateText(bp: Int): String {
    val whole = bp / 100
    val frac = bp % 100
    return when {
      frac == 0 -> "$whole"
      frac % 10 == 0 -> "$whole.${frac / 10}"
      else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
  }
}
