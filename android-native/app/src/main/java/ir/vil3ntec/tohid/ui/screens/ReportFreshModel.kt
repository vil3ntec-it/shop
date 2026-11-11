package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.cost.CostMethod
import ir.vil3ntec.tohid.core.cost.CostPolicy
import ir.vil3ntec.tohid.core.i18n.StrAppData
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportLedger
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.core.i18n.StrReportPages as S
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 *  «گزارشِ تازه» — کدام جدول برای کدام زبانه، بی Compose (آزمون: `ReportFreshModelTest`).
 *
 *  همهٔ عددها از `ReportCore`/`ReportTables`اند؛ این‌جا فقط انتخاب، برش و قالب است.
 *  روشِ بها (RPT-03) راهبردِ `CostPolicy` ِ core است: پیش‌فرض و مصوب (D-23، ۱۴۰۵/۰۸/۱۳) میانگینِ موزون و
 *  تأییدش (`approvedOf`) به هسته می‌رود؛ قیمتِ خریدِ روی قلم انتخابی و «تقریبی».
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

  /** روشِ این `costing` مصوب است؟ همان `costApproved` ِ هسته — هرگز `true` ِ ثابت */
  fun approvedOf(c: ReportCore.Costing): Boolean = CostPolicy.approved(CostPolicy.parse(c.id))

  /** نتیجهٔ یک زبانه: جدول‌ها و اینکه سود تقریبی است یا نه */
  data class View(val tables: List<ReportTables.Table>, val sales: ReportCore.SalesReport?, val debts: ReportCore.DebtReport?, val fraud: ReportCore.FraudReport?)

  /* ---------------- RPT-13: − / + و «مقایسه با دورهٔ قبل» در «گزارشِ تازه» ِ کامپیوتر ---------------- */

  /** زبانه‌هایی که بازه‌شان با − / + جابه‌جا می‌شود: همان‌هایی که [view] یا «ساعت و روز» با بازهٔ صریح می‌سازند */
  val SHIFTABLE: Set<String> = setOf("sales", "profit", "fraud", HeatmapModel.TAB_ID)

  /** زبانه‌هایی که خطِ «نسبت به دورهٔ قبل» دارند (همان کلیدهای `ReportKeys.parts`) */
  val COMPARABLE: Set<String> = setOf("sales", "profit")

  /**
   *  بازهٔ «گزارشِ تازه» ⇒ نامِ بازهٔ `ReportKeys` (همان واحدِ `ReportPeriod`): «امروز» روز، «۷ روز» هفت روز، «این ماه» ماهِ
   *  میلادیِ کامل؛ سه‌ماههٔ مالی و «همه» ⇒ `ALL` (جابه‌جا نمی‌شود، «مقایسه» دورهٔ قبل ندارد).
   */
  fun keysSpan(span: String): String = when (span) {
    "today" -> "TODAY"
    "week" -> "WEEK"
    "month" -> "MONTH"
    else -> "ALL"
  }

  /** بازهٔ مؤثر: [shift] دوره از بازهٔ [span] (منفی = گذشته)؛ ناممکن (آینده، سه‌ماهه، «همه») ⇒ همان بازهٔ پایه */
  fun rangeOf(span: String, today: String, shift: Int): ReportBoard.Range {
    val base = ReportBoard.range(span, today)
    if (shift == 0) return base
    val (from, to) = ReportKeys.range(keysSpan(span), base.from, base.to, shift, today)
    return ReportBoard.Range(from, to)
  }

  /** [shift] دوره از بازهٔ [span] ممکن است؟ (نه آینده، نه سه‌ماهه/«همه») */
  fun canShift(span: String, today: String, shift: Int): Boolean {
    val base = ReportBoard.range(span, today)
    return ReportKeys.can(keysSpan(span), base.from, base.to, shift, today)
  }

  /**
   *  [lineSeller] POS-23 — «فروشندهٔ هر ردیف» (`LineSellerPref`): تفکیکِ «کاربر» به فروشندهٔ ردیف؛ `false` همان امروز.
   *  [range] RPT-13 — بازهٔ صریحِ − / + ([rangeOf])؛ `null` همان بازهٔ [span].
   */
  fun view(
    l: ReportLedger, tab: String, span: String, dim: String, today: String, clock: ReportClock?,
    costing: ReportCore.Costing = costingOf(CostPolicy.DEFAULT), lineSeller: Boolean = false, range: ReportBoard.Range? = null,
  ): View {
    val r = range ?: ReportBoard.range(span, today)
    val ok = approvedOf(costing)
    val d = if (dim in ReportBoard.DIMS) dim else "product"
    return when (tab) {
      "today" -> View(ReportTables.today(ReportCore.today(l, today, clock, costing, 0, ok)), null, null, null)
      "sales" -> {
        val s = ReportCore.sales(l, r.from, r.to, clock, costing, ok, lineSeller)
        View(ReportTables.sales(s).filter { it.id == "sales.$d" }, s, null, null)
      }
      "profit" -> {
        val s = ReportCore.sales(l, r.from, r.to, clock, costing, ok, lineSeller)
        val t = ReportTables.sales(s).first { it.id == "sales.$d" }
        val title = CostPolicy.label(StrAppData.profitBy(ReportBoard.DIM_LABELS.getValue(d)), CostPolicy.parse(costing.id))
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

  /* ---------------- RPT-13: ریزِ ردیفِ تفکیکِ فروش ---------------- */

  const val DRILL_ID = "sales.drill"

  /** ردیف‌های همان تفکیک، هم‌ترتیبِ جدولِ `sales.<dim>` و «سود و حاشیه» (`ReportTables.sales`) */
  fun dimRows(s: ReportCore.SalesReport, dim: String): List<ReportCore.Row> = when (dim) {
    "category" -> s.category
    "user" -> s.user
    "hour" -> s.hour
    "payment" -> s.payment
    "channel" -> s.channel
    else -> s.product
  }

  /**
   *  ردیفِ [index] از جدولِ تفکیکِ [dim] ⇒ جدولِ فاکتورهایش (روز، فاکتور، مقدار، سهمِ فروش) با جمعی برابرِ ستونِ
   *  «فروش» ِ همان ردیف؛ ردیفِ بی‌جا ⇒ null. [label] همان نوشتهٔ خانهٔ اول، [doc] نامِ فاکتور (`DayEndModel.docLabel`).
   */
  fun drill(
    l: ReportLedger, s: ReportCore.SalesReport, dim: String, index: Int, label: String, clock: ReportClock?, doc: (String) -> String,
  ): ReportTables.Table? = drill(l, s, dim, index, label, clock, lineSeller = false, doc = doc)

  /** همان [drill] با POS-23: [lineSeller] ⇒ ریزِ ردیفِ «کاربر» به فروشندهٔ هر ردیف (همان کلیدِ `ReportCore.sales`) */
  fun drill(
    l: ReportLedger, s: ReportCore.SalesReport, dim: String, index: Int, label: String, clock: ReportClock?,
    lineSeller: Boolean, doc: (String) -> String,
  ): ReportTables.Table? {
    val row = dimRows(s, dim).getOrNull(index) ?: return null
    val list = ReportCore.invoicesOf(l, s.from, s.to, dim, row.key, clock, lineSeller)
    return ReportTables.Table(
      DRILL_ID, S.drillInvoices(label.ifBlank { "—" }, list.size),
      listOf(
        ReportTables.Column("date", S.colDay, "date"), ReportTables.Column("doc", S.colInvoice, "text"),
        ReportTables.Column("qty", S.colQty, "qty"), ReportTables.Column("sales", S.colSales, "money"),
      ),
      list.map { listOf(JsonPrimitive(it.date), JsonPrimitive(doc(it.saleId)), JsonPrimitive(it.qty), JsonPrimitive(it.sales)) },
      listOf(JsonPrimitive(S.drillTotal), JsonPrimitive(""), JsonPrimitive(list.sumOf { it.qty }), JsonPrimitive(list.sumOf { it.sales })),
    )
  }

  /* ---------------- RPT-13: ریزِ ردیفِ «امروز»، «بدهی‌ها» و «تقلب» ---------------- */

  const val DAY_DRILL_ID = "today.drill"
  const val DEBT_DRILL_ID = "debts.drill"
  const val FRAUD_DRILL_ID = "fraud.drill"

  /** ستونِ «روز» ِ جدولِ «امروز» (`ReportTables.today`: روز، تاریخ، …) */
  private const val TODAY_DATE_COL = 1

  /**
   *  ردیفِ «امروز»/«دیروز»/«هفتهٔ پیش» ⇒ فاکتورهای همان روز (`ReportCore.dayInvoices`): جمعِ «فروش» = ستونِ «فروش» و
   *  شمار = «فاکتور» ِ همان ردیف. ردیف یا تاریخِ بی‌جا ⇒ null.
   */
  fun dayDrill(l: ReportLedger, row: List<JsonPrimitive>, clock: ReportClock?, doc: (String) -> String): ReportTables.Table? {
    val date = row.getOrNull(TODAY_DATE_COL)?.takeIf { it !is JsonNull }?.content ?: return null
    val list = ReportCore.dayInvoices(l, date, clock)
    return ReportTables.Table(
      DAY_DRILL_ID, S.drillInvoices(row.first().content, list.size),
      listOf(ReportTables.Column("doc", S.colInvoice, "text"), ReportTables.Column("qty", S.colQty, "qty"), ReportTables.Column("sales", S.colSales, "money")),
      list.map { listOf(JsonPrimitive(doc(it.saleId)), JsonPrimitive(it.qty), JsonPrimitive(it.sales)) },
      listOf(JsonPrimitive(S.drillTotal), JsonPrimitive(list.sumOf { it.qty }), JsonPrimitive(list.sumOf { it.sales })),
    )
  }

  /**
   *  ردیفِ [index] از «سنِ بدهی» ([supplier] ⇒ «طلبِ تأمین‌کنندگان») ⇒ ماندهٔ بازِ هر سندِ همان طرف به اولین‌وارد‌اولین‌پرداخت
   *  (`ReportCore.openDebt`)، با پیش‌پرداخت در ردیفِ جدا؛ «مانده» ِ پایانی = ستونِ «مانده» ِ همان ردیف. [doc] نامِ سند.
   */
  fun debtDrill(
    l: ReportLedger, r: ReportCore.DebtReport, supplier: Boolean, index: Int, label: String, clock: ReportClock?, doc: (String) -> String,
  ): ReportTables.Table? {
    val row = (if (supplier) r.suppliers else r.customers).rows.getOrNull(index) ?: return null
    val open = ReportCore.openDebt(l, r.asOf, supplier, row.id, clock)
    val lines = open.lots.map {
      listOf(JsonPrimitive(it.date), JsonPrimitive(doc(it.docId)), JsonPrimitive(it.left), if (it.ageDays >= 0) JsonPrimitive(it.ageDays) else JsonNull)
    } + if (open.credit != 0L) listOf(listOf(JsonNull, JsonPrimitive(S.prepaid), JsonPrimitive(-open.credit), JsonNull)) else emptyList()
    return ReportTables.Table(
      DEBT_DRILL_ID, S.drillOpenDocs(label.ifBlank { "—" }, open.lots.size),
      listOf(
        ReportTables.Column("date", S.colDay, "date"), ReportTables.Column("doc", S.colDoc, "text"),
        ReportTables.Column("left", S.colLeft, "money"), ReportTables.Column("age", S.colAge, "count"),
      ),
      lines,
      listOf(JsonPrimitive(S.drillTotal), JsonPrimitive(""), JsonPrimitive(open.lots.sumOf { it.left } - open.credit), JsonNull),
    )
  }

  /** نامِ نوعِ رویدادِ ضدتقلب (`ReportCore.FraudEvent.kind`) */
  fun fraudKind(kind: String): String = when (kind) {
    "discount" -> S.kindDiscount
    "approved" -> S.kindApproved
    "return" -> S.kindReturn
    "void" -> S.kindVoid
    "line_delete" -> S.kindLineDelete
    "no_sale" -> S.kindNoSale
    else -> kind
  }

  /**
   *  ردیفِ [index] از «ضدتقلب» ⇒ رویدادهای همان کاربر (`ReportCore.fraudEvents`، همان بازه): شمارِ هر نوع = ستونِ همان
   *  ردیف؛ کارِ دیگران که همین کاربر تأیید کرد با «تأییدِ کارِ …» در ستونِ «تأییدکننده».
   */
  fun fraudDrill(
    l: ReportLedger, r: ReportCore.FraudReport, index: Int, clock: ReportClock?, doc: (String) -> String,
  ): ReportTables.Table? {
    val row = r.rows.getOrNull(index) ?: return null
    val list = ReportCore.fraudEvents(l, r.from, r.to, row.key, clock)
    fun who(s: String) = s.ifEmpty { S.unknownUser }
    return ReportTables.Table(
      FRAUD_DRILL_ID, S.drillEvents(who(row.key), list.size),
      listOf(
        ReportTables.Column("date", S.colDay, "date"), ReportTables.Column("kind", S.colKind, "text"), ReportTables.Column("doc", S.colDoc, "text"),
        ReportTables.Column("amount", S.colAmount, "money"), ReportTables.Column("approver", S.colApprover, "text"),
      ),
      list.map { e ->
        listOf(
          JsonPrimitive(e.date), JsonPrimitive(fraudKind(e.kind)), JsonPrimitive(if (e.docId.isEmpty()) "—" else doc(e.docId)), JsonPrimitive(e.amount),
          JsonPrimitive(if (e.byOther) S.approvedWorkOf(who(e.user)) else e.approvedBy),
        )
      },
      null,
    )
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
      "flag" -> if (v.content == "true") S.yes else S.no
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
