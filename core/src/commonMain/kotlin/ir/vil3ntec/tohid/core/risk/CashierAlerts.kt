package ir.vil3ntec.tohid.core.risk

/**
 *  RSK-10 — هشدارِ رفتارِ غیرعادیِ صندوقدار: لغوِ فاکتور، تخفیف و باز کردنِ کشو بی فروش «بیش از حدِ معمول».
 *
 *  «معمول» = نرخِ **بقیهٔ کاربرانِ همین دکان در همین بازه**: هر رویداد به‌ازای هر فاکتورِ فروخته. کاربری هشدار می‌گیرد
 *  که هر دو شرط را داشته باشد:
 *   ۱) دستِ کم [Policy.minCount] بار همان رویداد (یکی‌دو بارِ اتفاقی هشدار نیست)؛
 *   ۲) نرخش دستِ کم [Policy.factorPct] درصدِ نرخِ بقیه باشد. شمارِ بقیه کمِ کم ۱ گرفته می‌شود، تا «بقیه هیچ‌بار»
 *      با چند فاکتورِ انگشت‌شمارِ همکار هشدار نسازد.
 *  کاربری که خودش فاکتوری نفروخته مخرجش ۱ است (کشو بی فروشِ کسی که نمی‌فروشد خودش نشانه است).
 *  بی فاکتورِ همکار (دکانِ یک‌نفره) «معمولی» نیست و هشداری ساخته نمی‌شود.
 *  کاربرِ ناشناخته (کلیدِ خالی، دادهٔ کهنهٔ بی نام) در «بقیه» شمرده می‌شود ولی هرگز هشدار نمی‌گیرد.
 *
 *  خالص و فقط عددِ صحیح (بی Double)، در commonMain تا گوشی، کامپیوتر و آیفون یک حساب داشته باشند. شمارِ رویداد تا
 *  [MAX_EVENTS] و فاکتور تا [MAX_INVOICES] بریده می‌شود و ضریب تا ۱۰۰۰٪، تا هر ضرب زیرِ ۲^۵۳ بماند و دوقلوی JS
 *  پول‌به‌پول همین عدد را بدهد.
 *  ⛔ هم‌زادِ `cashierAlerts` در `license/report-board.js`؛ هر دو `core/test-vectors/cashier-alerts-v1.json` را می‌گذرانند.
 *  فقط هشدار است: هیچ کاری را نمی‌بندد و چیزی را عوض نمی‌کند.
 */
object CashierAlerts {
  const val VERSION = 1
  const val MAX_EVENTS: Long = 100_000
  const val MAX_INVOICES: Long = 10_000_000

  enum class Kind(val id: String) { VOID("void"), DISCOUNT("discount"), NO_SALE("no_sale") }

  /** شمارِ یک کاربر در بازه — همان ستون‌های `FraudRow` ِ هستهٔ گزارش */
  data class Counts(val key: String, val invoices: Long, val voids: Long, val discounts: Long, val noSales: Long) {
    fun of(k: Kind): Long = when (k) {
      Kind.VOID -> voids
      Kind.DISCOUNT -> discounts
      Kind.NO_SALE -> noSales
    }
  }

  data class Policy(val minCount: Long = 3, val factorPct: Long = 200)

  /**
   *  `count` در `invoices` فاکتورِ همین کاربر، در برابرِ `peerCount` در `peerInvoices` فاکتورِ بقیه.
   *  `ratioPct` = نرخِ کاربر به درصدِ نرخِ بقیه (شمارِ بقیه کمِ کم ۱)، گردشده به پایین.
   */
  data class Alert(
    val key: String, val kind: Kind, val count: Long, val invoices: Long,
    val peerCount: Long, val peerInvoices: Long, val ratioPct: Long,
  )

  private fun events(n: Long) = n.coerceIn(0L, MAX_EVENTS)
  private fun sold(n: Long) = n.coerceIn(0L, MAX_INVOICES)

  /** هشدارها به ترتیبِ نوع (لغو، تخفیف، کشو)، سپس نسبتِ بیشتر، سپس شمارِ بیشتر، سپس کلید */
  fun alerts(rows: List<Counts>, policy: Policy = Policy()): List<Alert> {
    val min = policy.minCount.coerceIn(1L, MAX_EVENTS)
    val factor = policy.factorPct.coerceIn(100L, 1_000L)
    val out = mutableListOf<Alert>()
    for (k in Kind.entries) {
      for ((i, u) in rows.withIndex()) {
        val c = events(u.of(k))
        if (u.key.isEmpty() || c < min) continue
        var peerCount = 0L
        var peerInvoices = 0L
        for ((j, p) in rows.withIndex()) {
          if (j == i) continue
          peerCount = events(peerCount + events(p.of(k)))
          peerInvoices = sold(peerInvoices + sold(p.invoices))
        }
        if (peerInvoices == 0L) continue
        val mine = sold(u.invoices)
        val den = maxOf(1L, mine)
        val base = maxOf(1L, peerCount)
        //  c/den ≥ (factor/100)·(base/peerInvoices) — ضربِ متقاطع، بی تقسیم
        if (c * peerInvoices * 100L < factor * base * den) continue
        out += Alert(u.key, k, c, mine, peerCount, peerInvoices, (c * peerInvoices * 100L) / (den * base))
      }
    }
    return out.sortedWith(compareBy<Alert>({ it.kind.ordinal }, { -it.ratioPct }, { -it.count }, { it.key }))
  }
}
