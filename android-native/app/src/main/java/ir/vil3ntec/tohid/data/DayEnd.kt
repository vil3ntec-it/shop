package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.DayClose
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.core.pay.ShiftCore
import ir.vil3ntec.tohid.core.pay.ShiftReport
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportDates

/**
 *  پایانِ روز (RPT-04) — پلِ دفترِ برنامه به `DayClose` ِ core. فقط می‌خواند؛ هیچ ردیفی نمی‌نویسد.
 *
 *  هر شیفت (`CashShift.list`) همان عددی را می‌دهد که صفحهٔ صندوق نشان می‌دهد:
 *  • بسته با Z ِ ذخیره‌شده (pay v2): خودِ Z — ثابت، حتی اگر رویدادی بعداً برسد؛
 *  • بسته با صندوقِ پیشین (بی Z): X ِ بازپخشِ دفتر تا لحظهٔ بستن + نقدِ شمرده‌شدهٔ همان ردیف
 *    (`AuditEntry.amount`) ⇒ اختلافِ نقد؛ «پیش از v2» نشان می‌خورد؛
 *  • باز: X ِ همین لحظه.
 *  رویدادهای دفتر همان `PayFlow.ledgerEvents` اند (همان قاعده‌های دخل: فاکتورِ اینترنتی، امانتِ
 *  پیک و رسیدِ لغو بیرون)، پس تفاوتِ دفتر و شیفت‌ها فقط پولِ بیرون از شیفت است.
 *
 *  جست‌وجو محدود است: فقط شیفت‌ها و رویدادهای یک پنجرهٔ چهارروزه دورِ همان روز (هر اختلافِ ساعت
 *  تا ±۱۴ ساعت و هر شروعِ روزِ کاری درونش است)، و بعد روزِ دقیق با همان ساعتِ گزارش‌ها.
 */
object DayEnd {
  /** شناسه‌ای که هیچ `refId` ِ سابقه‌ای نیست: ورود/خروجِ نقدِ شیفت‌ها در رویدادهای روز نمی‌آید */
  private const val DAY_REF = "\u0000day-end"

  /** پنجرهٔ نامزدها [از، تا) به میلی‌ثانیه؛ تاریخِ نامعتبر ⇒ null */
  fun window(date: String): Pair<Long, Long>? {
    val day = ReportDates.epochDay(date) ?: return null
    return (day - 1) * ReportDates.DAY_MS to (day + 3) * ReportDates.DAY_MS
  }

  private fun of(r: ShiftReport, closedAt: Long?, legacy: Boolean, skipped: Int): DayClose.Shift = DayClose.Shift(
    id = r.shiftId, openedAt = r.openedAt, openedBy = r.openedBy, closedAt = closedAt, float = r.float,
    methods = r.methods.entries.associateTo(LinkedHashMap()) { (k, m) -> k to DayClose.Sums(m.sales, m.refunds, m.receipts) },
    cashIn = r.moves.cashIn, cashOut = r.moves.cashOut,
    expected = r.expected.orEmpty(), counted = r.counted, variance = r.variance,
    needsReview = r.needsReview == true, legacy = legacy, skipped = skipped, noSale = r.events.noSale,
  )

  /** شیفتی که هسته نتوانست بخواند: بی عدد، «نیازمند بررسی» — پنهان نمی‌شود */
  private fun unreadable(s: CashShift.Shift) = DayClose.Shift(
    id = s.open.id, openedAt = s.open.createdAt, openedBy = s.open.by, closedAt = s.close?.createdAt,
    float = PayFlow.minor(s.open.amount), needsReview = true, skipped = 1,
  )

  fun shiftOf(d: ShopData, s: CashShift.Shift, now: Long): DayClose.Shift {
    val close = s.close
    if (close == null) {
      return when (val r = PayFlow.report(d, s.open, null, now)) {
        is PayFlow.Outcome.Ok -> of(r.value.report, null, false, r.value.skipped.size)
        is PayFlow.Outcome.Bad -> unreadable(s)
      }
    }
    val stored = close.z?.let { PayFlow.reportOf(it) }
    if (stored != null) return of(stored, close.createdAt, false, 0)
    //  صندوقِ پیشین: بازپخش تا لحظهٔ بستن، شمرده = مبلغِ ردیفِ بستن
    val at = maxOf(close.createdAt, s.open.createdAt)
    val rep = when (val r = PayFlow.replay(d, s.open, at)) {
      is PayFlow.Outcome.Ok -> r.value
      is PayFlow.Outcome.Bad -> return unreadable(s)
    }
    val x = when (val r = ShiftCore.xReport(rep.shift, at)) {
      is PayResult.Ok -> r.value
      is PayResult.Err -> return unreadable(s)
    }
    val counted = PayFlow.minor(close.amount)
    val expected = x.expected?.get(PayFlow.CASH) ?: 0L
    val variance = counted - expected
    return of(x, close.createdAt, true, rep.skipped.size).copy(
      counted = mapOf(PayFlow.CASH to counted),
      variance = mapOf(PayFlow.CASH to variance),
      needsReview = variance != 0L,
    )
  }

  /** رویدادهای پولیِ دفتر در پنجره، به شکلِ core */
  fun events(d: ShopData, from: Long, to: Long): List<DayClose.Event> =
    PayFlow.ledgerEvents(d, AuditEntry(id = DAY_REF, createdAt = from), to - 1).mapNotNull { e ->
      when (e.type) {
        "sale", "refund" -> DayClose.Event(e.type, e.at, e.rows.orEmpty().map { it.method to it.amount })
        "receipt" -> DayClose.Event("receipt", e.at, listOf((e.method ?: PayFlow.CASH) to (e.amount ?: 0L)))
        else -> null
      }
    }

  /**
   *  پایانِ روزِ `date` (`yyyy-MM-dd`ِ روزِ کاری) با ساعتِ گزارش‌ها. جمعِ بیرون از بازهٔ دقیق
   *  [ArithmeticException] می‌دهد (صفحه پیامش را نشان می‌دهد، عددِ گردشده نه).
   */
  fun report(d: ShopData, date: String, clock: ReportClock, now: Long): DayClose.Report {
    val dayOf: (Long) -> String = { clock.date(it) }
    val w = window(date) ?: return DayClose.of(date, emptyList(), emptyList(), dayOf, now)
    val shifts = CashShift.list(d)
      .filter { it.open.createdAt >= w.first && it.open.createdAt < w.second && dayOf(it.open.createdAt) == date }
      .map { shiftOf(d, it, now) }
    return DayClose.of(date, shifts, events(d, w.first, w.second), dayOf, now)
  }
}
