package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long

/**
 *  RPT-11 — گزارش ⇐ جدول، لایهٔ مشترکِ Excel/PDF/چاپ. خانه‌ها خام‌اند (عددِ صحیح
 *  با `kind`)؛ قالب‌بندیِ محلی (رقمِ فارسی، جداکنندهٔ هزارگان) کارِ لایهٔ نمایش است.
 *  `plain` متنِ ساده با رقمِ لاتین می‌دهد (CSV و آزمون). دوقلوی `tables`/`plain`ِ JS.
 *
 *  kind: text | date | count | qty (هزارم) | money (واحدِ کوچک) | bp (بیسیس‌پوینت) | flag (بله/نه)
 */
object ReportTables {
  @Serializable
  data class Column(val key: String, val label: String, val kind: String)

  @Serializable
  data class Table(
    val id: String, val title: String, val columns: List<Column>,
    val rows: List<List<JsonPrimitive>>, val total: List<JsonPrimitive>?,
  )

  @Serializable
  data class Plain(val id: String, val title: String, val head: List<String>, val rows: List<List<String>>, val total: List<String>?)

  private class C(val key: String, val label: String, val kind: String)

  private fun cols(list: List<C>) = list.map { Column(it.key, it.label, it.kind) }
  private fun p(s: String): JsonPrimitive = JsonPrimitive(s)
  private fun p(n: Long?): JsonPrimitive = if (n == null) JsonNull else JsonPrimitive(n)

  private val DIM_TITLES = mapOf(
    "product" to ("فروش به تفکیک کالا" to "کالا"), "category" to ("فروش به تفکیک دسته" to "دسته"),
    "user" to ("فروش به تفکیک کاربر" to "کاربر"), "hour" to ("فروش به تفکیک ساعت" to "ساعت"),
    "payment" to ("فروش به تفکیک روش پرداخت" to "روش پرداخت"), "channel" to ("فروش به تفکیک کانال" to "کانال"),
  )
  private val UNKNOWN = mapOf(
    "product" to "قلم آزاد", "category" to "بی‌دسته", "user" to "نامعلوم",
    "hour" to "نامعلوم", "payment" to "نامعلوم", "channel" to "نامعلوم",
  )
  private val KNOWN = mapOf(
    //  PAY (v3 A4): مقدارهای تازهٔ `paymentMethod` (`PayFlow.legacyMethod`) — همان `KNOWN`ِ report-core.js
    "payment" to mapOf(
      "cash" to "نقد", "credit" to "نسیه", "card" to "کارت", "wallet" to "کیف پول", "transfer" to "حواله",
      "store_credit" to "اعتبار فروشگاه", "gift_card" to "کارت هدیه", "other" to "سایر", "mixed" to "ترکیبی",
      "online" to "پرداختِ آنلاین", "cod" to "نقد هنگامِ تحویل",
    ),
    "channel" to mapOf("pos" to "حضوری", "online" to "اینترنتی"),
  )
  private val SALES_COLS = listOf(
    C("invoices", "فاکتور", "count"), C("qty", "مقدار", "qty"), C("gross", "جمع پیش از تخفیف", "money"),
    C("discount", "تخفیف", "money"), C("sales", "فروش", "money"), C("returns", "برگشتی", "money"),
    C("netSales", "فروش خالص", "money"), C("cogs", "بهای تمام‌شده", "money"),
    C("grossProfit", "سود ناخالص", "money"), C("marginBp", "حاشیه", "bp"),
  )

  private fun label(dim: String, key: String): String = when {
    key == "" -> UNKNOWN.getValue(dim)
    dim == "hour" -> "$key:00"
    else -> KNOWN[dim]?.get(key) ?: key
  }

  private fun salesCells(invoices: Int, qty: Long, gross: Long, discount: Long, sales: Long, returns: Long,
    netSales: Long, cogs: Long, grossProfit: Long, marginBp: Long?): List<JsonPrimitive> =
    listOf(p(invoices.toLong()), p(qty), p(gross), p(discount), p(sales), p(returns), p(netSales), p(cogs), p(grossProfit), p(marginBp))

  fun sales(r: ReportCore.SalesReport): List<Table> {
    val t = r.totals
    val noQty = SALES_COLS.filter { it.key != "qty" }
    val head = listOf(C("from", "از", "date"), C("to", "تا", "date"), C("returnCount", "برگشتی (بار)", "count")) +
      noQty + C("avgTicket", "میانگین فاکتور", "money") + C("profitApproximate", "سود تقریبی", "flag")
    val summary = listOf(p(r.from), p(r.to), p(t.returnCount.toLong())) +
      salesCells(t.invoices, 0, t.gross, t.discount, t.sales, t.returns, t.netSales, t.cogs, t.grossProfit, t.marginBp)
        .filterIndexed { i, _ -> i != 1 } + p(t.avgTicket) + JsonPrimitive(t.profitApproximate)
    val out = mutableListOf(Table("sales.totals", "خلاصهٔ فروش", cols(head), listOf(summary), null))
    val byDim = mapOf("product" to r.product, "category" to r.category, "user" to r.user,
      "hour" to r.hour, "payment" to r.payment, "channel" to r.channel)
    for (dim in ReportCore.DIMS) {
      val list = byDim.getValue(dim)
      val qty = list.sumOf { it.qty }; val gross = list.sumOf { it.gross }; val discount = list.sumOf { it.discount }
      val sales = list.sumOf { it.sales }; val returns = list.sumOf { it.returns }; val net = list.sumOf { it.netSales }
      val cogs = list.sumOf { it.cogs }; val gp = list.sumOf { it.grossProfit }
      val (title, who) = DIM_TITLES.getValue(dim)
      out += Table(
        "sales.$dim", title, cols(listOf(C("name", who, "text")) + SALES_COLS),
        list.map { x ->
          val name = if (dim == "product" && x.key != "") (if (x.name != "") x.name else x.key) else label(dim, x.key)
          listOf(p(name)) + salesCells(x.invoices, x.qty, x.gross, x.discount, x.sales, x.returns, x.netSales, x.cogs, x.grossProfit, x.marginBp)
        },
        listOf(p("جمع")) + salesCells(t.invoices, qty, gross, discount, sales, returns, net, cogs, gp, ReportCore.bp(gp, net)),
      )
    }
    return out
  }

  private val TODAY_COLS = listOf(
    C("invoices", "فاکتور", "count"), C("sales", "فروش", "money"), C("returns", "برگشتی", "money"),
    C("netSales", "فروش خالص", "money"), C("grossProfit", "سود ناخالص", "money"), C("marginBp", "حاشیه", "bp"),
    C("avgTicket", "میانگین فاکتور", "money"), C("profitApproximate", "سود تقریبی", "flag"),
  )

  fun today(r: ReportCore.TodayReport): List<Table> {
    fun row(label: String, d: ReportCore.Day) = listOf(p(label), p(d.date), p(d.invoices.toLong()), p(d.sales), p(d.returns),
      p(d.netSales), p(d.grossProfit), p(d.marginBp), p(d.avgTicket), JsonPrimitive(d.profitApproximate))
    return listOf(Table(
      "today", "داشبورد امروز", cols(listOf(C("day", "روز", "text"), C("date", "تاریخ", "date")) + TODAY_COLS),
      listOf(row("امروز", r.today), row("دیروز", r.yesterday), row("همین روز هفتهٔ پیش", r.lastWeek)), null,
    ))
  }

  private fun bucketLabels(b: List<Long>): List<String> {
    val out = ArrayList<String>()
    var lo = 0L
    for (hi in b) { out += "$lo–$hi روز"; lo = hi + 1 }
    out += "$lo+ روز"
    return out
  }

  fun debts(r: ReportCore.DebtReport): List<Table> {
    val labels = bucketLabels(r.buckets)
    fun one(id: String, title: String, who: String, part: ReportCore.Ageing): Table {
      val head = listOf(C("name", who, "text"), C("balance", "مانده", "money"), C("credit", "پیش‌پرداخت", "money")) +
        labels.mapIndexed { i, l -> C("b$i", l, "money") } + C("oldestDays", "قدیمی‌ترین (روز)", "count")
      return Table(
        id, title, cols(head),
        part.rows.map { x ->
          listOf(p(x.name.ifEmpty { x.id.ifEmpty { "نامعلوم" } }), p(x.balance), p(x.credit)) + x.buckets.map { p(it) } +
            p(x.oldestDays?.takeIf { it >= 0 })
        },
        listOf(p("جمع"), p(part.totals.balance), p(part.totals.credit)) + part.totals.buckets.map { p(it) } + p(null as Long?),
      )
    }
    return listOf(
      one("debts.customers", "سن بدهی مشتری‌ها", "مشتری", r.customers),
      one("debts.suppliers", "طلب تأمین‌کنندگان", "تأمین‌کننده", r.suppliers),
    )
  }

  private val FRAUD_COLS = listOf(
    C("invoices", "فاکتور", "count"), C("gross", "جمع پیش از تخفیف", "money"),
    C("discountCount", "تخفیف (بار)", "count"), C("discount", "تخفیف", "money"), C("discountBp", "نسبت تخفیف", "bp"),
    C("returnCount", "برگشتی (بار)", "count"), C("returns", "برگشتی", "money"), C("voidCount", "لغو (بار)", "count"),
    C("voids", "لغو", "money"), C("lineDeleteCount", "حذف ردیف (بار)", "count"), C("lineDeletes", "حذف ردیف", "money"),
    C("noSaleCount", "باز کردن کشو بی فروش", "count"), C("overrideCount", "نیازمند تأیید (بار)", "count"),
    C("approvalCount", "تأیید داده (بار)", "count"),
  )

  fun fraud(r: ReportCore.FraudReport): List<Table> {
    fun cells(x: ReportCore.FraudRow) = listOf(
      p(x.invoices.toLong()), p(x.gross), p(x.discountCount.toLong()), p(x.discount), p(x.discountBp),
      p(x.returnCount.toLong()), p(x.returns), p(x.voidCount.toLong()), p(x.voids), p(x.lineDeleteCount.toLong()),
      p(x.lineDeletes), p(x.noSaleCount.toLong()), p(x.overrideCount.toLong()), p(x.approvalCount.toLong()),
    )
    return listOf(Table(
      "fraud", "ضدتقلب به تفکیک کاربر", cols(listOf(C("name", "کاربر", "text")) + FRAUD_COLS),
      r.rows.map { x -> listOf(p(x.key.ifEmpty { "نامعلوم" })) + cells(x) },
      listOf(p("جمع")) + cells(r.totals),
    ))
  }

  /** یک خانه ⇐ متنِ ساده با رقمِ لاتین: money «1234.50»، qty «1.5»، bp «12.34%»، null «» */
  fun cellText(v: JsonPrimitive, kind: String): String {
    if (v is JsonNull) return ""
    return when (kind) {
      "flag" -> if (v.content == "true") "بله" else "نه"
      "money", "bp" -> {
        val n = v.long
        val a = if (n < 0) -n else n
        (if (n < 0) "-" else "") + (a / 100).toString() + "." + (a % 100).toString().padStart(2, '0') + (if (kind == "bp") "%" else "")
      }
      "qty" -> {
        val n = v.long
        val b = if (n < 0) -n else n
        val frac = (b % 1000).toString().padStart(3, '0').trimEnd('0')
        (if (n < 0) "-" else "") + (b / 1000).toString() + (if (frac.isNotEmpty()) ".$frac" else "")
      }
      else -> v.content
    }
  }

  fun plain(t: Table): Plain {
    fun line(cells: List<JsonPrimitive>) = cells.mapIndexed { i, v -> cellText(v, t.columns[i].kind) }
    return Plain(t.id, t.title, t.columns.map { it.label }, t.rows.map(::line), t.total?.let(::line))
  }
}
