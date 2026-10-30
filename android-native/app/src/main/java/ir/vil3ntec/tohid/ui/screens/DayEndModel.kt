package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.pay.DayClose
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportDates
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.data.DayEnd
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 *  زبانهٔ «پایانِ روز» ِ گزارشِ تازه (RPT-04) — بی Compose (آزمون: `DayEndModelTest`).
 *
 *  همهٔ عددها از `DayClose` ِ core و X/Z ِ هستهٔ شیفت‌اند؛ این‌جا فقط جدول و برچسب. جدول‌ها همان
 *  `ReportTables.Table` اند، پس اکسل، چاپ و نمای کارتی/جدولی همان راهِ زبانه‌های دیگر را می‌روند.
 *  زبانه فقط برای کسی است که پول را می‌بیند (`canSeeMoney`)، مثلِ سود و ضدتقلب.
 */
object DayEndModel {
  const val TAB_ID = "dayend"
  val TAB = ReportBoard.TabDef(TAB_ID, "پایان روز", false)

  /** زبانه‌های صفحه: همان زبانه‌های هسته، و «پایانِ روز» فقط با دیدنِ پول */
  fun tabs(base: List<ReportBoard.TabDef>, money: Boolean): List<ReportBoard.TabDef> = if (money) base + TAB else base

  /** امروز و شش روزِ پیش، تازه‌ترین اول */
  fun days(today: String): List<String> = (0..6).map { if (it == 0) today else ReportDates.addDays(today, -it) }.filter { it.isNotEmpty() }

  fun dayLabel(day: String, today: String, date: (String) -> String): String = when (day) {
    today -> "امروز"
    ReportDates.addDays(today, -1) -> "دیروز"
    else -> date(day)
  }

  /** نتیجهٔ یک روز؛ `error` فقط وقتی جمع از بازهٔ دقیق بیرون زد (هیچ عددِ گردشده‌ای نشان داده نمی‌شود) */
  data class Built(val report: DayClose.Report?, val error: String?, val tables: List<ReportTables.Table>)

  fun build(d: ShopData, day: String, clock: ReportClock, now: Long, offsetMinutes: Int): Built = try {
    val r = DayEnd.report(d, day, clock, now)
    Built(r, null, tables(r, offsetMinutes))
  } catch (e: ArithmeticException) {
    Built(null, "گزارشِ پایانِ روز ساخته نشد: جمع از بازهٔ دقیقِ عدد بیرون است.", emptyList())
  }

  /** «۰۸:۱۵» ِ دیواری با همان اختلافِ ساعتِ گزارش‌ها (رقمِ لاتین، مثلِ ستونِ «ساعت») */
  fun hhmm(ms: Long, offsetMinutes: Int): String {
    val m = (ms + offsetMinutes * 60_000L).mod(ReportDates.DAY_MS) / 60_000L
    return (m / 60).toString().padStart(2, '0') + ":" + (m % 60).toString().padStart(2, '0')
  }

  /** متن و رنگِ وضعیتِ روز (`success` | `warning` | `danger` | `neutral`) */
  fun status(r: DayClose.Report): Pair<String, String> = when (r.status) {
    DayClose.Status.EMPTY -> "این روز شیفت و فروشی ندارد" to "neutral"
    DayClose.Status.OVERLAP -> "شیفت‌های هم‌زمان؛ جمعِ شیفت‌ها فروش را دو بار شمرده است" to "danger"
    DayClose.Status.MISMATCH -> "با دفتر نمی‌خواند: پولِ بیرون از شیفت‌ها" to "danger"
    DayClose.Status.OPEN -> "${r.openShifts} شیفت هنوز باز است (X)" to "warning"
    DayClose.Status.REVIEW -> "بسته، با اختلاف یا رویدادِ نیازمندِ بررسی" to "warning"
    DayClose.Status.CLOSED -> "روز بسته است و با دفتر می‌خواند" to "success"
  }

  private fun p(s: String): JsonPrimitive = JsonPrimitive(s)
  private fun p(n: Long?): JsonPrimitive = if (n == null) JsonNull else JsonPrimitive(n)
  private fun col(key: String, label: String, kind: String) = ReportTables.Column(key, label, kind)

  private val METHOD_COLS = listOf(
    col("name", "روش", "text"), col("sales", "فروش", "money"), col("refunds", "برگشتی", "money"),
    col("receipts", "رسید", "money"), col("net", "خالص شیفت‌ها", "money"), col("expected", "مورد انتظار", "money"),
    col("counted", "شمرده", "money"), col("variance", "اختلاف", "money"), col("ledgerNet", "خالص دفتر", "money"),
    col("outside", "بیرون از شیفت‌ها", "money"),
  )

  private fun methodCells(name: String, x: DayClose.MethodLine): List<JsonPrimitive> = listOf(
    p(name), p(x.shifts.sales), p(x.shifts.refunds), p(x.shifts.receipts), p(x.shifts.net),
    p(x.expected), p(x.counted), p(x.variance), p(x.ledger.net), p(x.outside),
  )

  private val SHIFT_COLS = listOf(
    col("name", "شیفت", "text"), col("status", "وضعیت", "text"), col("float", "اول صندوق", "money"),
    col("net", "خالص", "money"), col("cashIn", "ورود نقد", "money"), col("cashOut", "خروج نقد", "money"),
    col("expectedCash", "نقد مورد انتظار", "money"), col("countedCash", "نقد شمرده", "money"),
    col("varianceCash", "اختلاف نقد", "money"), col("review", "نیازمند بررسی", "flag"),
  )

  fun shiftStatus(s: DayClose.ShiftLine): String = when (s.status) {
    "open" -> "باز (X)"
    "legacy" -> "بسته، صندوقِ پیشین"
    else -> "بسته (Z)"
  }

  private fun sumOrNull(v: List<Long?>): Long? = v.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

  fun tables(r: DayClose.Report, offsetMinutes: Int): List<ReportTables.Table> {
    val methods = ReportTables.Table(
      "dayend.methods", "پایان روز به تفکیک روش", METHOD_COLS,
      r.methods.map { methodCells(PayFlow.methodName(it.method), it) },
      methodCells("جمع", r.total),
    )
    val shifts = ReportTables.Table(
      "dayend.shifts", "شیفت‌های روز", SHIFT_COLS,
      r.shifts.map { s ->
        val span = hhmm(s.openedAt, offsetMinutes) + "–" + (s.closedAt?.let { hhmm(it, offsetMinutes) } ?: "…")
        val who = s.openedBy?.takeIf { it.isNotBlank() }
        listOf(
          p(if (who != null) "$who · $span" else span), p(shiftStatus(s)), p(s.float), p(s.net), p(s.cashIn), p(s.cashOut),
          p(s.expectedCash), p(s.countedCash), p(s.varianceCash), JsonPrimitive(s.needsReview || s.skipped > 0),
        )
      },
      listOf(
        p("جمع"), p(""), p(r.shifts.sumOf { it.float }), p(r.shifts.sumOf { it.net }), p(r.shifts.sumOf { it.cashIn }),
        p(r.shifts.sumOf { it.cashOut }), p(sumOrNull(r.shifts.map { it.expectedCash })), p(sumOrNull(r.shifts.map { it.countedCash })),
        p(sumOrNull(r.shifts.map { it.varianceCash })), JsonPrimitive(r.shifts.any { it.needsReview || it.skipped > 0 }),
      ),
    )
    return listOf(methods, shifts)
  }
}
