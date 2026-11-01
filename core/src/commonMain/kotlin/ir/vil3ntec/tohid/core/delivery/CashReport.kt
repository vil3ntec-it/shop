package ir.vil3ntec.tohid.core.delivery

/**
 *  RPT-15 — گزارشِ نقدِ پیک‌ها و تسویه در یک بازه (`GET /shop/delivery/cash-report`).
 *
 *  هر ردیف یک پیک: نقدی که در بازه هنگامِ تحویل گرفت و بخشی از آن که هنوز تسویه نشده،
 *  سندهای تسویهٔ همان بازه (بدهیِ تحویل، شمرده، کسری، اضافه، کرایه) و «در دست» ِ همین لحظه.
 *  عددها همه از سرورند (زمانِ سرور)؛ این‌جا فقط سنجشِ هم‌خوانی و برچسب. خالص (commonMain).
 */
data class CashReportRow(
  val courierId: String = "",
  val name: String = "",
  val deliveries: Int = 0,
  val expectedCashMinor: Long = 0,
  val collectedCashMinor: Long = 0,
  val unsettledMinor: Long = 0,
  val settlements: Int = 0,
  val handoverDueMinor: Long = 0,
  val receivedMinor: Long = 0,
  val shortageMinor: Long = 0,
  val excessMinor: Long = 0,
  val fareTotalMinor: Long = 0,
  val inHandMinor: Long = 0,
)

data class CashReport(val from: Long, val to: Long, val couriers: List<CashReportRow>, val totals: CashReportRow)

object CashReportMath {
  private fun columns(r: CashReportRow): List<Long> = listOf(
    r.deliveries.toLong(), r.expectedCashMinor, r.collectedCashMinor, r.unsettledMinor, r.settlements.toLong(),
    r.handoverDueMinor, r.receivedMinor, r.shortageMinor, r.excessMinor, r.fareTotalMinor, r.inHandMinor,
  )

  /** جمعِ هر ستونِ ردیف‌ها همان جمعِ کلِ سرور است؟ اگر نه، صفحه عدد را «ناهم‌خوان» نشان می‌دهد */
  fun consistent(r: CashReport): Boolean {
    val sum = LongArray(columns(r.totals).size)
    r.couriers.forEach { row -> columns(row).forEachIndexed { i, v -> sum[i] += v } }
    return sum.toList() == columns(r.totals)
  }

  /** نقدِ گرفته‌شدهٔ بازه که تسویه هم شده (گرفته − تسویه‌نشده) */
  fun settledOfCollected(r: CashReportRow): Long = r.collectedCashMinor - r.unsettledMinor

  /** اختلافِ مشتری در بازه (گرفته − موردِ انتظار)؛ منفی ⇐ مشتری کمتر داد */
  fun customerDiff(r: CashReportRow): Long = r.collectedCashMinor - r.expectedCashMinor

  /** نیازمندِ نگاهِ مدیر: کسری/اضافهٔ تسویه، اختلافِ مشتری، یا نقدِ تسویه‌نشده */
  fun attention(r: CashReportRow): Boolean =
    r.shortageMinor > 0 || r.excessMinor > 0 || customerDiff(r) != 0L || r.unsettledMinor > 0
}
