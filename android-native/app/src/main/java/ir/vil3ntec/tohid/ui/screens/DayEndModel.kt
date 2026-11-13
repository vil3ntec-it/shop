package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.StrDayEnd as S
import ir.vil3ntec.tohid.core.pay.DayClose
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportDates
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.data.DayEnd
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 *  زبانهٔ «پایانِ روز» ِ گزارشِ تازه (RPT-04) — بی Compose (آزمون: `DayEndModelTest`).
 *
 *  همهٔ عددها از `DayClose` ِ core و X/Z ِ هستهٔ شیفت‌اند؛ این‌جا فقط جدول و برچسب. جدول‌ها همان
 *  `ReportTables.Table` اند، پس اکسل، چاپ و نمای کارتی/جدولی همان راهِ زبانه‌های دیگر را می‌روند.
 *  زبانه فقط برای کسی است که پول را می‌بیند (`canSeeMoney`)، مثلِ سود و ضدتقلب.
 *  LOC-06 (تصمیمِ ۱۴۰۵/۰۸/۲۳ #۲۴): همهٔ برچسب‌ها و سرستون‌ها از `StrDayEnd`، پس صفحه و خروجیِ اکسل/چاپ/PDF به زبانِ برنامه‌اند.
 */
object DayEndModel {
  const val TAB_ID = "dayend"
  val TAB: ReportBoard.TabDef get() = ReportBoard.TabDef(TAB_ID, S.tab, false)

  /** زبانه‌های صفحه: همان زبانه‌های هسته، و «پایانِ روز» فقط با دیدنِ پول */
  fun tabs(base: List<ReportBoard.TabDef>, money: Boolean): List<ReportBoard.TabDef> = if (money) base + TAB else base

  /** امروز و شش روزِ پیش، تازه‌ترین اول */
  fun days(today: String): List<String> = (0..6).map { if (it == 0) today else ReportDates.addDays(today, -it) }.filter { it.isNotEmpty() }

  fun dayLabel(day: String, today: String, date: (String) -> String): String = when (day) {
    today -> S.today
    ReportDates.addDays(today, -1) -> S.yesterday
    else -> date(day)
  }

  /** نتیجهٔ یک روز؛ `error` فقط وقتی جمع از بازهٔ دقیق بیرون زد (هیچ عددِ گردشده‌ای نشان داده نمی‌شود) */
  data class Built(val report: DayClose.Report?, val error: String?, val tables: List<ReportTables.Table>)

  fun build(d: ShopData, day: String, clock: ReportClock, now: Long, offsetMinutes: Int): Built = try {
    val r = DayEnd.report(d, day, clock, now)
    Built(r, null, tables(r, offsetMinutes))
  } catch (e: ArithmeticException) {
    Built(null, S.buildFailed, emptyList())
  }

  /** «۰۸:۱۵» ِ دیواری با همان اختلافِ ساعتِ گزارش‌ها (رقمِ لاتین، مثلِ ستونِ «ساعت») */
  fun hhmm(ms: Long, offsetMinutes: Int): String {
    val m = (ms + offsetMinutes * 60_000L).mod(ReportDates.DAY_MS) / 60_000L
    return (m / 60).toString().padStart(2, '0') + ":" + (m % 60).toString().padStart(2, '0')
  }

  /** متن و رنگِ وضعیتِ روز (`success` | `warning` | `danger` | `neutral`) */
  fun status(r: DayClose.Report): Pair<String, String> = when (r.status) {
    DayClose.Status.EMPTY -> S.statusEmpty to "neutral"
    DayClose.Status.OVERLAP -> S.statusOverlap to "danger"
    DayClose.Status.MISMATCH -> S.statusMismatch to "danger"
    DayClose.Status.OPEN -> S.statusOpen(r.openShifts) to "warning"
    DayClose.Status.REVIEW -> S.statusReview to "warning"
    DayClose.Status.CLOSED -> S.statusClosed to "success"
  }

  private fun p(s: String): JsonPrimitive = JsonPrimitive(s)
  private fun p(n: Long?): JsonPrimitive = if (n == null) JsonNull else JsonPrimitive(n)
  private fun col(key: String, label: String, kind: String) = ReportTables.Column(key, label, kind)

  //  getter، نه مقدارِ ثابت: سرستون با هر بار ساختنِ جدول به زبانِ همان لحظهٔ برنامه (LOC-06)
  private val METHOD_COLS: List<ReportTables.Column> get() = listOf(
    col("name", S.colMethod, "text"), col("sales", S.colSales, "money"), col("refunds", S.colRefunds, "money"),
    col("receipts", S.colReceipts, "money"), col("net", S.colShiftsNet, "money"), col("expected", S.colExpected, "money"),
    col("counted", S.colCounted, "money"), col("variance", S.colVariance, "money"), col("ledgerNet", S.colLedgerNet, "money"),
    col("outside", S.colOutside, "money"),
  )

  private fun methodCells(name: String, x: DayClose.MethodLine): List<JsonPrimitive> = listOf(
    p(name), p(x.shifts.sales), p(x.shifts.refunds), p(x.shifts.receipts), p(x.shifts.net),
    p(x.expected), p(x.counted), p(x.variance), p(x.ledger.net), p(x.outside),
  )

  private val SHIFT_COLS: List<ReportTables.Column> get() = listOf(
    col("name", S.colShift, "text"), col("status", S.colStatus, "text"), col("float", S.colFloat, "money"),
    col("net", S.colNet, "money"), col("cashIn", S.colCashIn, "money"), col("cashOut", S.colCashOut, "money"),
    col("expectedCash", S.colExpectedCash, "money"), col("countedCash", S.colCountedCash, "money"),
    col("varianceCash", S.colVarianceCash, "money"), col("review", S.colReview, "flag"),
  )

  fun shiftStatus(s: DayClose.ShiftLine): String = when (s.status) {
    "open" -> S.shiftOpen
    "legacy" -> S.shiftLegacy
    else -> S.shiftClosed
  }

  //  ── RPT-04 / S48: «ریزِ تراکنش‌ها» ی یک ردیفِ روش (کلیک یا Enter روی ردیفِ «نقد») ──

  const val METHODS_ID = "dayend.methods"
  const val DETAIL_ID = "dayend.detail"

  /** نامِ ردیفِ جدولِ روش‌ها ⇐ شناسهٔ روش؛ ردیفِ «جمع» یا نامِ ناشناخته ⇒ null (ریز ندارد) */
  fun methodOf(r: DayClose.Report?, rowName: String): String? =
    r?.methods?.firstOrNull { PayFlow.methodName(it.method) == rowName }?.method

  private fun kindLabel(type: String): String = when (type) {
    "sale" -> S.colSales
    "refund" -> S.kindRefund
    else -> S.colReceipts
  }

  /** سندِ هر رویدادِ دفتر از شناسه‌اش (`sale:`، `ret:`، `rcv:`، `gci:`، `sor:`/`lyr:`) — بی نامِ مشتری */
  fun docLabel(d: ShopData, id: String): String {
    val ref = id.substringAfter(':', "")
    fun inv(saleId: String?): String = d.sales.find { it.id == saleId }?.invoiceLabel()?.let { S.invoiceNo(it) } ?: S.invoice
    return when (id.substringBefore(':')) {
      "sale" -> inv(ref)
      "ret" -> S.refundOf(inv(d.saleReturns.find { it.id == ref }?.saleId))
      "rcv" -> S.debtReceived
      "gci" -> S.giftCard
      "sor", "lyr" -> S.depositBack
      else -> ""
    }
  }

  private val DETAIL_COLS: List<ReportTables.Column> get() = listOf(
    col("time", S.colTime, "text"), col("kind", S.colKind, "text"), col("doc", S.colDoc, "text"), col("amount", S.colAmount, "money"),
  )

  /** جدولِ ریزِ روشِ [method] در روزِ [day]؛ جمعش همان «خالص دفتر» ِ همان ردیف است (`DayEnd.lines`) */
  fun details(d: ShopData, day: String, method: String, clock: ReportClock, offsetMinutes: Int): ReportTables.Table {
    val lines = DayEnd.lines(d, day, method, clock)
    return ReportTables.Table(
      DETAIL_ID, S.detailsTitle(PayFlow.methodName(method)), DETAIL_COLS,
      lines.map { listOf(p(hhmm(it.at, offsetMinutes)), p(kindLabel(it.type)), p(docLabel(d, it.id)), p(it.amount)) },
      listOf(p(S.total), p(""), p(S.txCount(lines.size)), p(lines.sumOf { it.amount })),
    )
  }

  private fun sumOrNull(v: List<Long?>): Long? = v.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

  fun tables(r: DayClose.Report, offsetMinutes: Int): List<ReportTables.Table> {
    val methods = ReportTables.Table(
      METHODS_ID, S.methodsTitle, METHOD_COLS,
      r.methods.map { methodCells(PayFlow.methodName(it.method), it) },
      methodCells(S.total, r.total),
    )
    val shifts = ReportTables.Table(
      "dayend.shifts", S.shiftsTitle, SHIFT_COLS,
      r.shifts.map { s ->
        val span = hhmm(s.openedAt, offsetMinutes) + "–" + (s.closedAt?.let { hhmm(it, offsetMinutes) } ?: "…")
        val who = s.openedBy?.takeIf { it.isNotBlank() }
        listOf(
          p(if (who != null) "$who · $span" else span), p(shiftStatus(s)), p(s.float), p(s.net), p(s.cashIn), p(s.cashOut),
          p(s.expectedCash), p(s.countedCash), p(s.varianceCash), JsonPrimitive(s.needsReview || s.skipped > 0),
        )
      },
      listOf(
        p(S.total), p(""), p(r.shifts.sumOf { it.float }), p(r.shifts.sumOf { it.net }), p(r.shifts.sumOf { it.cashIn }),
        p(r.shifts.sumOf { it.cashOut }), p(sumOrNull(r.shifts.map { it.expectedCash })), p(sumOrNull(r.shifts.map { it.countedCash })),
        p(sumOrNull(r.shifts.map { it.varianceCash })), JsonPrimitive(r.shifts.any { it.needsReview || it.skipped > 0 }),
      ),
    )
    return listOf(methods, shifts)
  }
}
