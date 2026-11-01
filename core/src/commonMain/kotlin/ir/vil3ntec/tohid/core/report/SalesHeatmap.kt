package ir.vil3ntec.tohid.core.report

/**
 *  RPT-08 — نقشهٔ حرارتیِ «ساعت در برابر روزِ هفته» (۷ × ۲۴) از رویدادهای فروش.
 *
 *  هر رویداد زمانِ مطلق (ms) و مبلغ (یک‌صدم، Long) دارد؛ ساعتِ دیواری با جابه‌جاییِ
 *  منطقهٔ زمانیِ دکان (`offsetMinutes`، کابل = ۲۷۰) ساخته می‌شود و روزِ هفته با همان
 *  قاعدهٔ «روزِ کاری که از نیمه‌شب می‌گذرد» (`dayStartMinutes`، همان `ShopDay` و
 *  `BusinessDay.businessDate`): فروشِ ۱:۳۰ ِ دکانی که روزش ساعتِ ۲ تمام می‌شود ردیفِ
 *  **دیروز** و ستونِ ساعتِ ۱ را می‌گیرد — یک شبِ کاری دو ردیف نمی‌شود.
 *
 *  ردیف‌ها از نخستین روزِ هفتهٔ دکان (`weekStart` به شمارِ ISO، پیش‌فرض ۶ = شنبه)؛
 *  ستون‌ها ساعتِ ۰ تا ۲۳. هر خانه شمارِ فروش (`count`، فقط `isSale`) و جمعِ خالصِ مبلغ
 *  (`totalMinor`؛ برگشتی با مبلغِ منفی در خانهٔ **خودش**، نه خانهٔ فاکتور — همان قاعدهٔ
 *  گزارش در RULES) را دارد. شدتِ رنگ (`level`) با عددِ صحیح از بیشترین شمار ساخته
 *  می‌شود. پول هرگز Double نیست.
 *
 *  منطقِ خالص، بی `android.*`/`java.*` (§۴۰.۳)؛ رابطِ کاربریِ گزارش جداست.
 */
data class HeatSale(val atMs: Long, val amountMinor: Long, val isSale: Boolean = true)

data class HeatCell(val count: Int, val totalMinor: Long) {
  companion object { val EMPTY = HeatCell(0, 0L) }
}

class SalesHeatmap internal constructor(
  /** روزِ هفتهٔ هر ردیف به شمارِ ISO (۱ دوشنبه … ۶ شنبه، ۷ یکشنبه)، از `weekStart` */
  val weekdays: List<Int>,
  /** ۷ ردیف × ۲۴ ستون */
  val cells: List<List<HeatCell>>,
) {
  fun cell(row: Int, hour: Int): HeatCell = cells.getOrNull(row)?.getOrNull(hour) ?: HeatCell.EMPTY

  /** بیشترین شمار در یک خانه؛ ۰ یعنی بی هیچ فروش */
  val maxCount: Int = cells.maxOf { r -> r.maxOf { it.count } }

  /** پرفروش‌ترین خانه (ردیف، ساعت)؛ برابری ⇒ نخستین در ترتیبِ ردیف و ساعت؛ بی فروش ⇒ null */
  val peak: Pair<Int, Int>? = if (maxCount == 0) null else {
    val r = cells.indexOfFirst { row -> row.any { it.count == maxCount } }
    r to cells[r].indexOfFirst { it.count == maxCount }
  }

  /** جمعِ هر ردیف (روز) و هر ستون (ساعت) */
  val rowTotals: List<HeatCell> = cells.map { sum(it) }
  val hourTotals: List<HeatCell> = (0 until HOURS).map { h -> sum(cells.map { it[h] }) }
  val total: HeatCell = sum(rowTotals)

  /**
   *  شدتِ خانه از ۰ تا `levels`: ۰ فقط برای خانهٔ بی فروش؛ وگرنه ⌈count × levels / max⌉
   *  (کم‌فروش‌ترین خانهٔ پُر هم دست‌کم ۱ است و فقط بیشینه به `levels` می‌رسد).
   */
  fun level(row: Int, hour: Int, levels: Int = 4): Int {
    val c = cell(row, hour).count
    if (c <= 0 || maxCount <= 0 || levels <= 0) return 0
    return ((c.toLong() * levels + maxCount - 1) / maxCount).toInt()
  }

  companion object {
    const val DAYS: Int = 7
    const val HOURS: Int = 24
    private const val MINUTE_MS: Long = 60_000L
    private const val DAY_MS: Long = 86_400_000L
    /** بیشترین جابه‌جاییِ منطقهٔ زمانیِ زمین (±۱۴ ساعت) */
    private const val MAX_OFFSET_MIN: Int = 14 * 60

    private fun sum(list: List<HeatCell>): HeatCell =
      HeatCell(list.sumOf { it.count }, list.fold(0L) { s, c -> s + c.totalMinor })

    /**
     *  نقشه از رویدادها. `offsetMinutes` بیرون از ±۱۴ ساعت ⇒ ۰؛ `dayStartMinutes` بیرون از
     *  شبانه‌روز ⇒ ۰ (نیمه‌شب)؛ `weekStart` بیرون از ۱..۷ ⇒ ۶ (شنبه).
     */
    fun build(
      sales: List<HeatSale>,
      offsetMinutes: Int,
      dayStartMinutes: Int = 0,
      weekStart: Int = 6,
    ): SalesHeatmap {
      val off = if (offsetMinutes in -MAX_OFFSET_MIN..MAX_OFFSET_MIN) offsetMinutes else 0
      val start = if (dayStartMinutes in 0 until 24 * 60) dayStartMinutes else 0
      val ws = if (weekStart in 1..7) weekStart else 6
      val counts = Array(DAYS) { IntArray(HOURS) }
      val totals = Array(DAYS) { LongArray(HOURS) }
      for (s in sales) {
        val local = s.atMs + off * MINUTE_MS
        var day = floorDiv(local, DAY_MS)
        val minute = (floorMod(local, DAY_MS) / MINUTE_MS).toInt()
        if (minute < start) day -= 1
        val row = ((weekdayOf(day) - ws) % DAYS + DAYS) % DAYS
        val hour = minute / 60
        if (s.isSale) counts[row][hour] += 1
        totals[row][hour] += s.amountMinor
      }
      return SalesHeatmap(
        weekdays = (0 until DAYS).map { (ws - 1 + it) % DAYS + 1 },
        cells = (0 until DAYS).map { r -> (0 until HOURS).map { h -> HeatCell(counts[r][h], totals[r][h]) } },
      )
    }

    /** روزِ هفتهٔ ISO ِ «روزِ چندم از ۱۹۷۰-۰۱-۰۱» (آن روز پنجشنبه = ۴ بود) */
    internal fun weekdayOf(epochDay: Long): Int = (floorMod(epochDay + 3, DAYS.toLong())).toInt() + 1

    private fun floorDiv(a: Long, b: Long): Long {
      val q = a / b
      return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
    }

    private fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b
  }
}
