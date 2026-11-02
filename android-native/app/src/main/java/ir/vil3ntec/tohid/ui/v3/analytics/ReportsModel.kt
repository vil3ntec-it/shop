package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.StockMovement

/**
 *  زبانه‌های «گزارش‌ها» (`reports?tab=`). `money` ⇒ فقط با `ShopRole.canSeeMoney` (همان سدِ
 *  امروز: «سود و فروش» و «خرید» قیمتِ خریدِ دکان‌اند)، `ranged` ⇒ ردیفِ بازه دارد، `csv` همان
 *  بخشِ `ReportCsv.of` که خروجی و چاپِ امروزِ همین زبانه است.
 */
/** یک زبانهٔ «گزارش‌ها»: پایه ([ReportTab]) یا اختیاری ([ReportExtra]) */
interface ReportPage {
  val id: String
  val money: Boolean
  val ranged: Boolean
  val csv: String
}

enum class ReportTab(override val id: String, override val money: Boolean, override val ranged: Boolean, override val csv: String) : ReportPage {
  PROFIT("profit", money = true, ranged = true, csv = "sales"),
  PRODUCTS("products", money = false, ranged = false, csv = "products"),
  DEBTORS("debtors", money = false, ranged = false, csv = "debtors"),
  PURCHASES("purchases", money = true, ranged = true, csv = "purchases"),
  EXPIRY("expiry", money = false, ranged = false, csv = "expiry"),
  STOCK("stock", money = false, ranged = false, csv = "stock"),
}

/**
 *  زبانه‌های اختیاریِ گوشی (همان «گزارش‌ها» ِ گوشیِ پیشین)، هر کدام پشتِ تنظیمِ خودش — ضایعات (`ShrinkReportPref`)،
 *  سنِ بدهی و RFM (`ShopPolicy.crmReports`)، شماره‌های جاافتاده (`DocGapsPref`) و «پرسشِ ساده» (`SimpleAskPref`).
 *  `csv` همان بخشِ `ReportCsv.of`؛ «پرسشِ ساده» برگه ندارد.
 */
enum class ReportExtra(override val id: String, override val money: Boolean, override val ranged: Boolean, override val csv: String) : ReportPage {
  SHRINK("shrink", money = true, ranged = true, csv = "shrink"),
  AGING("aging", money = false, ranged = false, csv = "aging"),
  RFM("rfm", money = true, ranged = false, csv = "rfm"),
  GAPS("gaps", money = false, ranged = false, csv = "gaps"),
  ASK("ask", money = false, ranged = false, csv = ""),
}

/** بازهٔ انتخاب‌شده: گزینهٔ `ReportEngine.Range` و ماه/سالِ خورشیدیِ «ماه…»/«سال…» */
data class ReportPeriod(val range: ReportEngine.Range, val year: Int, val month: Int)

/** گردشِ یک کالا در دفترِ حرکت‌ها: جمعِ ورود، جمعِ خروج و شمارِ حرکت‌ها */
data class StockFlow(
  val productId: String,
  val product: Product?,
  val inQty: Double,
  val outQty: Double,
  val moves: Int,
  val lastAt: Long,
) {
  val net: Double get() = inQty - outQty
}

/** حالتِ خالصِ صفحهٔ گزارش — بی Compose، با آزمون (`ReportsModelTest`) */
object ReportsModel {
  /** ترتیبِ تراشه‌های بازه */
  val RANGES = listOf(
    ReportEngine.Range.TODAY, ReportEngine.Range.WEEK, ReportEngine.Range.MONTH,
    ReportEngine.Range.JMONTH, ReportEngine.Range.JYEAR, ReportEngine.Range.ALL,
  )

  fun tabs(canSeeMoney: Boolean): List<ReportTab> = ReportTab.entries.filter { canSeeMoney || !it.money }

  /** زبانه‌های پایه و سپس اختیاری‌های روشن (پولی فقط با `canSeeMoney`) */
  fun pages(canSeeMoney: Boolean, extras: Set<ReportExtra>): List<ReportPage> =
    tabs(canSeeMoney) + ReportExtra.entries.filter { it in extras && (canSeeMoney || !it.money) }

  /** زبانهٔ پیوند در میانِ همه؛ ناشناخته ⇒ همان `tabOf` */
  fun pageOf(id: String?, canSeeMoney: Boolean, extras: Set<ReportExtra>): ReportPage =
    pages(canSeeMoney, extras).firstOrNull { it.id == id } ?: tabOf(id, canSeeMoney)

  /** زبانهٔ پیوند؛ ناشناخته یا بسته برای این کاربر ⇒ نخستین زبانهٔ باز (همان پیش‌فرضِ امروز) */
  fun tabOf(id: String?, canSeeMoney: Boolean): ReportTab = tabs(canSeeMoney).let { all -> all.firstOrNull { it.id == id } ?: all.first() }

  fun rangeId(r: ReportEngine.Range): String = r.name.lowercase()

  /** `range=` ⇒ گزینه؛ ناشناخته ⇒ «این ماه» (پیش‌فرضِ امروز) */
  fun rangeOf(id: String?): ReportEngine.Range =
    ReportEngine.Range.entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: ReportEngine.Range.MONTH

  /** ماهِ خورشیدیِ قبلی/بعدی (`delta` ماه)، با گذر از سال */
  fun stepMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
    val index = year * 12 + (month - 1) + delta
    return Math.floorDiv(index, 12) to Math.floorMod(index, 12) + 1
  }

  /** پیکانِ برگه: ماه برای «ماه…»، سال برای «سال…» */
  fun step(p: ReportPeriod, delta: Int): ReportPeriod = when (p.range) {
    ReportEngine.Range.JMONTH -> stepMonth(p.year, p.month, delta).let { (y, m) -> p.copy(year = y, month = m) }
    ReportEngine.Range.JYEAR -> p.copy(year = p.year + delta)
    else -> p
  }

  fun bounds(p: ReportPeriod, today: String): Pair<String, String> = ReportEngine.rangeOf(p.range, today, p.year, p.month)

  fun isAllTime(from: String): Boolean = from == "0000-01-01"

  /** کسرِ میله نسبت به بزرگ‌ترین مقدار (۰ تا ۱)؛ منفی و بی‌مقدار ⇒ صفر */
  fun fraction(value: Double, max: Double): Float =
    if (max <= 0.0 || value <= 0.0) 0f else (value / max).toFloat().coerceIn(0f, 1f)

  /**
   *  گردشِ موجودی به تفکیکِ کالا از `ReportEngine.stockLedger`: ورود = جمعِ مقدارهای مثبت،
   *  خروج = جمعِ منفی‌ها (مثبت نوشته می‌شود). تازه‌ترین حرکت اول.
   */
  fun stockFlow(ledger: List<StockMovement>, products: List<Product>): List<StockFlow> {
    val byId = products.associateBy { it.id }
    return ledger.groupBy { it.productId }.map { (id, rows) ->
      StockFlow(
        productId = id,
        product = byId[id],
        inQty = rows.filter { it.qty > 0 }.sumOf { it.qty },
        outQty = rows.filter { it.qty < 0 }.sumOf { -it.qty },
        moves = rows.size,
        lastAt = rows.maxOf { it.createdAt },
      )
    }.sortedByDescending { it.lastAt }
  }
}
