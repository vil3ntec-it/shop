package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 *  تابلوی گزارش — کارت‌ها و نشان‌های خانه (v3 §۱۹.۵ بند ۲، VISUAL-DIRECTION §۳ بند ۲).
 *
 *  خالص: نه Compose، نه دیسک، نه ساعتِ دستگاه. روی خروجیِ `ReportCore` می‌نشیند و
 *  فقط می‌گوید «کدام کارت، کدام عدد، کدام نشان، و کلیک به کدام فهرست». عددِ تازه‌ای
 *  نمی‌سازد؛ پس کارت و جمعِ جدولِ مقصد همیشه یکی‌اند. سود تا تصمیمِ D-23 «تقریبی»
 *  است (v3 §۱۵.۴ بند ۴).
 *
 *  دوقلوی `license/report-board.js`؛ هر دو `core/test-vectors/report-board-v1.json`
 *  را عیناً می‌گذرانند.
 */
object ReportBoard {
  const val VERSION = 1

  /** بازه‌های صفحهٔ گزارشِ تازه — همان معنای `ReportEngine.rangeOf` */
  val SPANS = listOf("today", "week", "month", "all")
  val SPAN_LABELS = mapOf("today" to "امروز", "week" to "۷ روز", "month" to "این ماه", "all" to "همه")

  data class TabDef(val id: String, val label: String, val ranged: Boolean)

  val TABS = listOf(
    TabDef("today", "امروز", false),
    TabDef("sales", "فروش", true),
    TabDef("profit", "سود و حاشیه", true),
    TabDef("debts", "سن بدهی", false),
    TabDef("fraud", "ضدتقلب", true),
  )
  val DIMS = listOf("product", "category", "user", "hour", "payment", "channel")
  val DIM_LABELS = mapOf(
    "product" to "کالا", "category" to "دسته", "user" to "کاربر", "hour" to "ساعت",
    "payment" to "روش پرداخت", "channel" to "کانال",
  )
  private const val VS = "نسبت به دیروز"

  /** مقصدِ کلیک: زبانهٔ گزارش، بازه و تفکیک */
  @Serializable
  data class Preset(val tab: String, val span: String, val dim: String)

  @Serializable
  data class Kpi(
    val id: String, val title: String, val value: Long, val kind: String,
    val delta: ReportCore.Delta?, val trend: String?, val good: Boolean?, val deltaText: String?,
    val period: String, val approximate: Boolean, val target: Preset,
  )

  /** `tone`: success | warning | danger | neutral */
  @Serializable
  data class Pill(val id: String, val text: String, val tone: String, val target: Preset)

  @Serializable
  data class Home(val date: String, val kpis: List<Kpi>, val pills: List<Pill>)

  @Serializable
  data class Range(val from: String, val to: String)

  /** بازه ⇐ از/تا؛ ناشناخته = امروز */
  fun range(span: String, today: String): Range = when (span) {
    "all" -> Range("0000-01-01", "9999-12-31")
    "week" -> Range(ReportDates.addDays(today, -6).ifEmpty { today }, today)
    "month" -> Range(today.take(7) + "-01", today)
    else -> Range(today, today)
  }

  /** بیسیس‌پوینت ⇐ «+12.5٪» (یک رقمِ اعشار، نیمه رو به بالا روی قدرِ مطلق) */
  fun pct(bp: Long?): String? {
    if (bp == null) return null
    val a = if (bp < 0) -bp else bp
    val tenths = (a + 5) / 10
    if (tenths == 0L) return "0٪"
    val r = tenths % 10
    return (if (bp > 0) "+" else "-") + (tenths / 10).toString() + (if (r != 0L) ".$r" else "") + "٪"
  }

  private fun trendOf(diff: Long) = if (diff > 0) "up" else if (diff < 0) "down" else "flat"

  fun deltaText(d: ReportCore.Delta?): String? = when {
    d == null -> null
    d.bp != null -> pct(d.bp) + " " + VS
    d.diff == 0L -> "بی تغییر $VS"
    else -> "دیروز صفر یا منفی بود"
  }

  private fun kpi(id: String, title: String, value: Long, kind: String, d: ReportCore.Delta?, period: String, approximate: Boolean, target: Preset) =
    Kpi(id, title, value, kind, d, d?.let { trendOf(it.diff) }, d?.let { it.diff > 0 }, deltaText(d), period, approximate, target)

  /**
   *  کارت‌ها و نشان‌های خانه از سه گزارشِ هسته (`today`، `debts`، `fraud`ِ همان روز).
   *  `money = false` (شاگرد): سود و بهای خرید پنهان است، نه صفر.
   */
  fun home(t: ReportCore.TodayReport, debts: ReportCore.DebtReport, fraud: ReportCore.FraudReport, money: Boolean = true): Home {
    val d0 = t.today
    val vs = t.vsYesterday
    val kpis = mutableListOf(
      kpi("netSales", "فروش خالص امروز", d0.netSales, "money", vs["netSales"], "امروز", false, Preset("sales", "today", "product")),
      kpi("invoices", "فاکتورهای امروز", d0.invoices.toLong(), "count", vs["invoices"], "امروز", false, Preset("sales", "today", "user")),
    )
    if (money) {
      kpis += kpi(
        "grossProfit", if (d0.profitApproximate) "سود ناخالص امروز (تقریبی)" else "سود ناخالص امروز", d0.grossProfit,
        "money", vs["grossProfit"], "امروز", d0.profitApproximate, Preset("profit", "today", "product"),
      )
    }
    kpis += kpi("avgTicket", "میانگین فاکتور", d0.avgTicket, "money", vs["avgTicket"], "امروز", false, Preset("today", "today", ""))
    kpis += kpi("receivable", "طلب از مشتریان", debts.customers.totals.balance, "money", null, "تا امروز", false, Preset("debts", "all", ""))

    val pills = mutableListOf<Pill>()
    if (money) {
      pills += if (d0.profitApproximate) Pill("profit", "سود تقریبی", "warning", Preset("profit", "today", "product"))
      else Pill("profit", "سود قطعی", "success", Preset("profit", "today", "product"))
      if (d0.costEstimatedLines > 0) {
        pills += Pill("cost", "${d0.costEstimatedLines} قلم بی بهای خرید", "warning", Preset("profit", "today", "product"))
      }
    }
    val edge = debts.buckets.lastOrNull() ?: 0L
    val late = debts.customers.rows.count { (it.buckets.lastOrNull() ?: 0L) > 0 }
    pills += if (late > 0) Pill("overdue", "$late مشتری با قرض بیش از $edge روز", "danger", Preset("debts", "all", ""))
    else Pill("overdue", "قرض بیش از $edge روز نیست", "success", Preset("debts", "all", ""))
    val f = fraud.totals
    val events = f.returnCount + f.voidCount + f.lineDeleteCount + f.noSaleCount
    pills += if (events > 0) Pill("fraud", "$events رویداد حساس امروز", "warning", Preset("fraud", "today", ""))
    else Pill("fraud", "رویداد حساسی امروز نیست", "success", Preset("fraud", "today", ""))
    return Home(t.date, kpis, pills)
  }

  /** از دفتر تا تابلو، با همان هسته */
  fun build(
    l: ReportLedger, date: String, clock: ReportClock? = null, costing: ReportCore.Costing = ReportCore.Costing.LINE,
    costApproved: Boolean = false, money: Boolean = true,
  ): Home = home(
    ReportCore.today(l, date, clock, costing, 0, costApproved),
    ReportCore.debts(l, date, clock = clock),
    ReportCore.fraud(l, date, date, clock),
    money,
  )

  private fun cmpCell(a: JsonPrimitive, b: JsonPrimitive): Int {
    if (!a.isString && !b.isString) {
      val x = a.doubleOrNull
      val y = b.doubleOrNull
      if (x != null && y != null) return x.compareTo(y)
    }
    return a.content.compareTo(b.content)
  }

  /** ردیف‌های یک جدولِ `ReportTables` مرتب به ستونِ `col` (پایدار؛ خانهٔ خالی همیشه آخر) */
  fun sortRows(rows: List<List<JsonPrimitive>>, col: Int, desc: Boolean): List<List<JsonPrimitive>> =
    rows.withIndex().sortedWith { p, q ->
      val a = p.value.getOrNull(col) ?: JsonNull
      val b = q.value.getOrNull(col) ?: JsonNull
      val an = a is JsonNull
      val bn = b is JsonNull
      if (an || bn) {
        if (an == bn) p.index - q.index else if (an) 1 else -1
      } else {
        val c = cmpCell(a, b)
        (if (desc) -c else c).takeIf { it != 0 } ?: (p.index - q.index)
      }
    }.map { it.value }
}
