package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.cost.CostMethod
import ir.vil3ntec.tohid.core.cost.CostPolicy
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportLedger
import ir.vil3ntec.tohid.core.report.ReportTables
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 *  «گزارشِ تازه» — کدام جدول برای کدام زبانه، بی Compose (آزمون: `ReportFreshModelTest`).
 *
 *  همهٔ عددها از `ReportCore`/`ReportTables`اند؛ این‌جا فقط انتخاب، برش و قالب است.
 *  سود همیشه با برچسبِ «تقریبی» می‌آید تا D-23 (v3 §۱۵.۴ بند ۴). روشِ بها (RPT-03) راهبردِ
 *  `CostPolicy` ِ core است: پیش‌فرض همان قیمتِ خریدِ روی قلم (رفتارِ پیشین)، میانگینِ موزون فقط پیش‌نمایش.
 */
object ReportFreshModel {
  /** شاگرد (`canSeeMoney = false`) فقط سنِ بدهی را می‌بیند — همان قاعدهٔ صفحهٔ امروز */
  fun tabs(money: Boolean): List<ReportBoard.TabDef> =
    if (money) ReportBoard.TABS else ReportBoard.TABS.filter { it.id == "debts" }

  /** ستون‌های جدولِ «سود و حاشیه» */
  private val PROFIT_KEYS = listOf("name", "netSales", "cogs", "grossProfit", "marginBp")

  /** ستون‌هایی که کارتِ هر ردیف (نمای کارتی) نشان می‌دهد */
  fun cardKeys(tableId: String): List<String> = when {
    tableId == "today" -> listOf("netSales", "invoices", "grossProfit", "avgTicket")
    tableId == "profit" -> listOf("netSales", "cogs", "grossProfit", "marginBp")
    tableId.startsWith("sales.") -> listOf("netSales", "invoices", "qty", "returns")
    tableId.startsWith("debts.") -> listOf("balance", "credit", "oldestDays")
    tableId == "fraud" -> listOf("discount", "returnCount", "voidCount", "lineDeleteCount", "noSaleCount")
    tableId == "dayend.methods" -> listOf("net", "expected", "counted", "variance", "outside")
    tableId == "dayend.shifts" -> listOf("status", "net", "expectedCash", "countedCash", "varianceCash")
    else -> emptyList()
  }

  /** فقط ستون‌های `keys` (به همان ترتیب)؛ ستونِ ناموجود نادیده */
  fun project(t: ReportTables.Table, id: String, title: String, keys: List<String>): ReportTables.Table {
    val idx = keys.mapNotNull { k -> t.columns.indexOfFirst { it.key == k }.takeIf { it >= 0 } }
    return ReportTables.Table(
      id, title, idx.map { t.columns[it] }, t.rows.map { r -> idx.map { r[it] } }, t.total?.let { r -> idx.map { r[it] } },
    )
  }

  /** روشِ بهای `CostPolicy` ⇒ `costing` ِ هستهٔ گزارش (همان شناسه‌ها؛ ناشناخته ⇒ رفتارِ امروز) */
  fun costingOf(m: CostMethod): ReportCore.Costing =
    ReportCore.Costing.entries.firstOrNull { it.id == m.id } ?: ReportCore.Costing.LINE

  /** نتیجهٔ یک زبانه: جدول‌ها و اینکه سود تقریبی است یا نه */
  data class View(val tables: List<ReportTables.Table>, val sales: ReportCore.SalesReport?, val debts: ReportCore.DebtReport?, val fraud: ReportCore.FraudReport?)

  fun view(
    l: ReportLedger, tab: String, span: String, dim: String, today: String, clock: ReportClock?,
    costing: ReportCore.Costing = ReportCore.Costing.LINE,
  ): View {
    val r = ReportBoard.range(span, today)
    val d = if (dim in ReportBoard.DIMS) dim else "product"
    return when (tab) {
      "today" -> View(ReportTables.today(ReportCore.today(l, today, clock, costing)), null, null, null)
      "sales" -> {
        val s = ReportCore.sales(l, r.from, r.to, clock, costing)
        View(ReportTables.sales(s).filter { it.id == "sales.$d" }, s, null, null)
      }
      "profit" -> {
        val s = ReportCore.sales(l, r.from, r.to, clock, costing)
        val t = ReportTables.sales(s).first { it.id == "sales.$d" }
        val title = CostPolicy.label("سود و حاشیه به تفکیک ${ReportBoard.DIM_LABELS.getValue(d)}", CostPolicy.parse(costing.id))
        View(listOf(project(t, "profit", title, PROFIT_KEYS)), s, null, null)
      }
      "debts" -> {
        val x = ReportCore.debts(l, today, clock = clock)
        View(ReportTables.debts(x), null, x, null)
      }
      "fraud" -> {
        val f = ReportCore.fraud(l, r.from, r.to, clock)
        View(ReportTables.fraud(f), null, null, f)
      }
      else -> View(emptyList(), null, null, null)
    }
  }

  /** «12.3٪» — یک رقمِ اعشار، بی علامتِ مثبت */
  fun percent(bp: Long): String {
    val a = if (bp < 0) -bp else bp
    val tenths = (a + 5) / 10
    val r = tenths % 10
    return (if (bp < 0 && tenths > 0) "-" else "") + (tenths / 10).toString() + (if (r != 0L) ".$r" else "") + "٪"
  }

  /**
   *  خانه برای صفحه: پول بی اعشار با جداکننده (همان `money()`)، مقدار تا سه رقم،
   *  درصد با یک رقم، تاریخ با `date` (خورشیدیِ برنامه). خالی ⇒ «—».
   */
  fun display(v: JsonPrimitive, kind: String, date: (String) -> String = { it }): String {
    if (v is JsonNull) return "—"
    val n = v.longOrNull
    return when (kind) {
      "money" -> if (n != null) ir.vil3ntec.tohid.money(n / 100.0) else v.content
      "qty" -> if (n != null) ir.vil3ntec.tohid.qty(n / 1000.0) else v.content
      "bp" -> if (n != null) percent(n) else v.content
      "flag" -> if (v.content == "true") "بله" else "نه"
      "date" -> date(v.content)
      else -> v.content
    }
  }

  /**
   *  برگهٔ خروجی (اکسل/چاپ/CSV) از جدول‌های همین زبانه: برای هر جدول عنوان، سرستون،
   *  ردیف‌ها و جمع، با یک ردیفِ خالی میانِ جدول‌ها. عدد همان `ReportTables.plain` است
   *  (رقمِ لاتین، پول با دو رقمِ اعشارِ واحدِ کوچک) تا اکسل حسابش کند.
   */
  fun exportRows(tables: List<ReportTables.Table>): List<List<String>> {
    val out = ArrayList<List<String>>()
    tables.forEachIndexed { i, t ->
      if (i > 0) out += listOf("")
      val p = ReportTables.plain(t)
      if (tables.size > 1) out += listOf(p.title)
      out += p.head
      out += p.rows
      p.total?.let { out += it }
    }
    return out
  }
}
