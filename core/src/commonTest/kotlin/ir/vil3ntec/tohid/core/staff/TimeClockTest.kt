package ir.vil3ntec.tohid.core.staff

import ir.vil3ntec.tohid.core.staff.TimeClock.Issue
import ir.vil3ntec.tohid.core.staff.TimeClock.Kind
import ir.vil3ntec.tohid.core.staff.TimeClock.Punch
import ir.vil3ntec.tohid.core.staff.TimeClock.Rejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** STF-07 — ورود و خروجِ کارمند */
class TimeClockTest {

  /** 2026-11-01 ساعتِ ۰۰:۰۰ ِ کابل (UTC+4:30) = 2026-10-31T19:30Z */
  private val day0 = 1_793_475_000_000L
  private fun at(h: Int, m: Int = 0) = day0 + (h * 60L + m) * 60_000L
  private fun p(id: String, staff: String, kind: Kind, ms: Long) = Punch(id, staff, kind, ms, "dev-1")
  private val kabul = 270

  @Test
  fun nextAlternatesAndCheckRejectsDoubleTapsAndBackdating() {
    val list = mutableListOf<Punch>()
    assertEquals(Kind.IN, TimeClock.next(list, "a"))
    val in1 = p("1", "a", Kind.IN, at(8))
    assertNull(TimeClock.check(list, in1)); list += in1
    assertEquals(Kind.OUT, TimeClock.next(list, "a"))
    assertEquals(Kind.IN, TimeClock.next(list, "b"), "کارمندِ دیگر جداست")
    assertEquals(Rejection.SAME_KIND, TimeClock.check(list, p("2", "a", Kind.IN, at(8, 1))))
    assertEquals(Rejection.DUPLICATE, TimeClock.check(list, p("1", "a", Kind.OUT, at(9))))
    assertEquals(Rejection.BEFORE_LAST, TimeClock.check(list, p("3", "a", Kind.OUT, at(7, 59))), "عقب کشیدنِ ساعت")
    assertEquals(Rejection.BAD_INPUT, TimeClock.check(list, p("", "a", Kind.OUT, at(9))))
    assertEquals(Rejection.BAD_INPUT, TimeClock.check(list, p("4", " ", Kind.OUT, at(9))))
    assertNull(TimeClock.check(list, p("5", "a", Kind.OUT, at(16, 30))))
  }

  @Test
  fun healthyShiftsSumPerBusinessDayAndDuplicatesCountOnce() {
    val punches = listOf(
      p("1", "a", Kind.IN, at(8)), p("2", "a", Kind.OUT, at(12, 30)),
      p("3", "a", Kind.IN, at(13, 15)), p("4", "a", Kind.OUT, at(17, 0, )),
      p("2", "a", Kind.OUT, at(12, 30)),       // همان زدن با همگام‌سازیِ دوباره
    )
    val spans = TimeClock.spans(punches, at(20), kabul)
    assertEquals(listOf(270L, 225L), spans.map { it.minutes })
    assertEquals(listOf(null, null), spans.map { it.issue })
    assertEquals(setOf("2026-11-01"), spans.map { it.businessDate }.toSet())
    assertEquals(listOf(TimeClock.DayTotal("a", "2026-11-01", 495, 0)), TimeClock.totals(spans))
  }

  @Test
  fun nightShiftBelongsToTheDayItStarted() {
    val spans = TimeClock.spans(listOf(p("1", "a", Kind.IN, at(22)), p("2", "a", Kind.OUT, at(26))), at(30), kabul)
    assertEquals(1, spans.size)
    assertEquals("2026-11-01", spans[0].businessDate)
    assertEquals(240L, spans[0].minutes)
    //  دکانی که روزش ۲ بامداد شروع می‌شود: ورودِ ۱:۳۰ هنوز روزِ پیش است
    val late = TimeClock.spans(listOf(p("1", "a", Kind.IN, at(25, 30)), p("2", "a", Kind.OUT, at(27))), at(30), kabul, dayStartMinutes = 120)
    assertEquals("2026-11-01", late[0].businessDate)
  }

  @Test
  fun anomaliesAreShownNotCounted() {
    val punches = listOf(
      p("0", "a", Kind.OUT, at(7)),                       // خروجِ بی ورود
      p("1", "a", Kind.IN, at(8)), p("2", "a", Kind.IN, at(9)),   // ورودِ بی خروج، سپس ورودِ تازه
      p("3", "a", Kind.OUT, at(12)),
      p("4", "b", Kind.IN, at(1)), p("5", "b", Kind.OUT, at(18)), // ۱۷ ساعت > سقف
      p("6", "c", Kind.IN, at(10)),                       // هنوز سرِ کار
    )
    val spans = TimeClock.spans(punches, at(14), kabul)
    assertEquals(
      listOf(Issue.MISSING_IN, Issue.MISSING_OUT, null, Issue.TOO_LONG, Issue.OPEN),
      spans.map { it.issue },
    )
    assertEquals(listOf(0L, 0L, 180L, 0L, 0L), spans.map { it.minutes })
    assertEquals(1020L, spans[3].rawMinutes, "مدتِ خام برای مدیر دیده می‌شود")
    assertEquals(240L, spans[4].rawMinutes, "نوبتِ باز تا همین حالا")
    val totals = TimeClock.totals(spans)
    assertEquals(TimeClock.DayTotal("a", "2026-11-01", 180, 2), totals.first { it.staffId == "a" })
    assertEquals(0L, totals.first { it.staffId == "b" }.minutes)
    //  سقفِ تنظیم‌شده
    assertEquals(null, TimeClock.spans(punches.filter { it.staffId == "b" }, at(20), kabul, maxMinutes = 18 * 60)[0].issue)
  }

  @Test
  fun orderIsDeterministicRegardlessOfInputOrder() {
    val a = listOf(p("2", "a", Kind.OUT, at(12)), p("1", "a", Kind.IN, at(8)), p("9", "b", Kind.IN, at(9)), p("8", "b", Kind.OUT, at(10)))
    assertEquals(TimeClock.spans(a, at(20), kabul), TimeClock.spans(a.reversed(), at(20), kabul))
    assertEquals(listOf("a", "b"), TimeClock.spans(a, at(20), kabul).map { it.staffId })
  }
  @Test
  fun periodTotalsSumHealthyMinutesWorkedDaysAndIssuesInsideTheRange() {
    fun d(n: Int, h: Int, m: Int = 0) = at(h, m) + n * 86_400_000L
    val punches = listOf(
      p("1", "a", Kind.IN, d(-1, 9)), p("2", "a", Kind.OUT, d(-1, 17)),          // بیرون از بازه
      p("3", "a", Kind.IN, d(0, 8)), p("4", "a", Kind.OUT, d(0, 12)),
      p("5", "a", Kind.IN, d(0, 13)), p("6", "a", Kind.OUT, d(0, 15)),
      p("7", "a", Kind.IN, d(2, 22)), p("8", "a", Kind.OUT, d(3, 1)),            // شبِ آخر ⇒ مالِ روزِ ورود
      p("9", "b", Kind.IN, d(1, 8)), p("10", "b", Kind.IN, d(1, 9)), p("11", "b", Kind.OUT, d(1, 10)),
      p("12", "b", Kind.IN, d(2, 18)),                                            // هنوز باز
    )
    val spans = TimeClock.spans(punches, d(2, 20), kabul)
    val r = TimeClock.periodTotals(spans, "2026-11-01", "2026-11-03")
    assertEquals(listOf("a", "b"), r.map { it.staffId })
    assertEquals(TimeClock.PeriodTotal("a", 4 * 60 + 2 * 60 + 3 * 60, 2, 0), r[0])
    //  ورودِ دوباره بی خروج ⇒ یک ناجور؛ نوبتِ باز ناجور نیست و دقیقه هم ندارد
    assertEquals(TimeClock.PeriodTotal("b", 60, 1, 1), r[1])
    assertEquals(emptyList(), TimeClock.periodTotals(spans, "2026-11-05", "2026-11-30"))
    assertEquals(listOf(TimeClock.PeriodTotal("a", 8 * 60, 1, 0)), TimeClock.periodTotals(spans, "2026-10-31", "2026-10-31"))
  }
}
