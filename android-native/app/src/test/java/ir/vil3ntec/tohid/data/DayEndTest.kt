package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.DayClose
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportDates
import ir.vil3ntec.tohid.ui.screens.DayEndModel
import ir.vil3ntec.tohid.ui.screens.ReportFreshModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  RPT-04 — پایانِ روزِ چندشیفتی روی دفترِ واقعیِ برنامه: Z ِ ذخیره‌شده، X ِ شیفتِ باز، صندوقِ
 *  پیشین، پولِ بیرون از شیفت، و تطبیق با «فروش خالص» ِ هستهٔ گزارش (همان روز، همان ساعت).
 */
class DayEndTest {
  private val clock = ReportClock.fixed(0, 0)
  private val date = "2026-10-29"
  private val base = ReportDates.epochDay(date)!! * ReportDates.DAY_MS
  private val h = 3_600_000L
  private var n = 0
  private val newId: () -> String = { "id${n++}" }

  private fun <T> ok(o: PayFlow.Outcome<T>): T = when (o) {
    is PayFlow.Outcome.Ok -> o.value
    is PayFlow.Outcome.Bad -> throw AssertionError(o.problem.code)
  }

  private val openA = AuditEntry(id = "A", type = CashShift.OPEN, createdAt = base + 8 * h, amount = 1000.0, by = "علی")
  private val openB = AuditEntry(id = "B", type = CashShift.OPEN, createdAt = base + 13 * h, amount = 1750.0, by = "رضا")

  /** شیفتِ A بسته با Z: نقد ۵۰۰، نقد ۳۰۰ + کارت ۲۰۰، برگشتِ نقد ۵۰. شیفتِ B باز: نسیهٔ ۴۰۰ و رسیدِ ۱۰۰. */
  private fun ledger(): ShopData {
    var d = ShopData(
      sales = listOf(
        Sale(id = "s1", finalTotal = 500.0, createdAt = base + 9 * h),
        Sale(
          id = "s2", finalTotal = 500.0, createdAt = base + 10 * h,
          payments = listOf(SalePayment("cash", "cash", 30000, "confirmed"), SalePayment("afpay_card", "card", 20000, "pending", "C1", "manual")),
        ),
        //  روزِ پیش: در هیچ جمعی نیست
        Sale(id = "s9", finalTotal = 999.0, createdAt = base - 2 * h),
      ),
      saleReturns = listOf(SaleReturn(id = "r1", saleId = "s1", amount = 50.0, createdAt = base + 11 * h)),
      auditLog = listOf(openA),
    )
    val counts = mapOf("100000" to 1L, "50000" to 1L, "10000" to 2L, "5000" to 1L)
    val z = ok(PayFlow.closeRow(d, openA, PayFlow.Count(counts, methodCounts = mapOf("afpay_card" to 20000L), by = "علی"), base + 12 * h, newId))
    assertEquals(0L, z.report.variance!!["cash"])
    d = d.copy(
      auditLog = d.auditLog + z.row + openB,
      sales = d.sales + Sale(id = "s3", finalTotal = 400.0, debtGiven = 400.0, debtorId = "c1", createdAt = base + 14 * h),
      transactions = listOf(DebtTransaction(id = "t1", debtorId = "c1", type = "receive", amount = 100.0, createdAt = base + 15 * h)),
    )
    return d
  }

  private val now get() = base + 16 * h

  @Test
  fun `دو شیفت، Z و X، به تفکیکِ روش جمع می‌شوند و روز با دفتر می‌خواند`() {
    val r = DayEnd.report(ledger(), date, clock, now)
    assertEquals(listOf("A", "B"), r.shifts.map { it.id })
    assertEquals(listOf("closed", "open"), r.shifts.map { it.status })
    assertEquals("علی", r.shifts[0].openedBy)
    assertEquals(listOf("cash", "afpay_card", "account"), r.methods.map { it.method })
    val cash = r.methods[0]
    assertEquals(DayClose.Sums(80000, 5000, 10000), cash.shifts)
    assertEquals(cash.shifts, cash.ledger)
    //  A: ۱۰۰۰ + ۸۰۰ − ۵۰ = ۱۷۵۰؛ B (X): ۱۷۵۰ + ۱۰۰
    assertEquals(175000L + 185000L, cash.expected)
    assertEquals(175000L, cash.counted)
    assertEquals(0L, cash.variance)
    assertEquals(20000L, r.methods[1].counted)
    assertEquals(DayClose.Sums(40000), r.methods[2].shifts)
    assertTrue(r.reconciled)
    assertEquals(1, r.openShifts)
    assertEquals(DayClose.Status.OPEN, r.status)
  }

  @Test
  fun `فروش خالص ِ دفترِ روز همان فروش خالص ِ هستهٔ گزارش است`() {
    val d = ledger()
    val r = DayEnd.report(d, date, clock, now)
    val ledgerNet = r.methods.sumOf { it.ledger.sales - it.ledger.refunds }
    val core = ReportCore.today(ReportBridge.ledgerOf(d), date, clock).today
    assertEquals(core.netSales, ledgerNet)
    assertEquals(142000L - 7000L, ledgerNet)
  }

  @Test
  fun `فروشِ پیش از بازشدنِ صندوق بیرون از شیفت‌ها است، نه پنهان`() {
    val d = ledger().let { it.copy(sales = it.sales + Sale(id = "s0", finalTotal = 70.0, createdAt = base + 7 * h)) }
    val r = DayEnd.report(d, date, clock, now)
    assertFalse(r.reconciled)
    assertEquals(7000L, r.methods[0].outside)
    assertEquals(7000L, r.total.outside)
    assertEquals(DayClose.Status.MISMATCH, r.status)
    assertEquals(ReportCore.today(ReportBridge.ledgerOf(d), date, clock).today.netSales, r.methods.sumOf { it.ledger.sales - it.ledger.refunds })
  }

  @Test
  fun `Z پس از بستن ثابت است و فروشِ دیررسیده در پنجره‌اش جدا نشان داده می‌شود`() {
    val d = ledger().let { it.copy(sales = it.sales + Sale(id = "s4", finalTotal = 60.0, createdAt = base + 11 * h + 30 * 60_000L)) }
    val r = DayEnd.report(d, date, clock, now)
    assertEquals(80000L, r.methods[0].shifts.sales)
    assertEquals(86000L, r.methods[0].ledger.sales)
    assertEquals(6000L, r.methods[0].outside)
    assertEquals(DayClose.Status.MISMATCH, r.status)
  }

  @Test
  fun `صندوقِ پیشین بی Z با بازپخشِ دفتر و نقدِ شمرده‌شدهٔ ردیفِ بستن`() {
    val d0 = ShopData(
      sales = listOf(Sale(id = "s1", finalTotal = 500.0, createdAt = base + 9 * h)),
      auditLog = listOf(openA),
    )
    val closed = (CashShift.close(d0, 1490.0, "", "علی", date, base + 12 * h, newId) as CashShift.Result.Ok).data
    val r = DayEnd.report(closed, date, clock, now)
    val a = r.shifts.single()
    assertEquals("legacy", a.status)
    assertEquals(150000L, a.expectedCash)
    assertEquals(149000L, a.countedCash)
    assertEquals(-1000L, a.varianceCash)
    assertTrue(a.needsReview)
    assertTrue(r.reconciled)
    assertEquals(DayClose.Status.REVIEW, r.status)
  }

  @Test
  fun `شیفتِ روزِ دیگر و تاریخِ نامعتبر چیزی نمی‌آورند`() {
    val next = DayEnd.report(ledger(), "2026-10-30", clock, now)
    assertTrue(next.shifts.isEmpty())
    assertEquals(DayClose.Status.EMPTY, next.status)
    val bad = DayEnd.report(ledger(), "2026-02-30", clock, now)
    assertEquals(DayClose.Status.EMPTY, bad.status)
    //  روزِ پیش فقط فروشِ s9 را دارد، بی شیفت
    val prev = DayEnd.report(ledger(), "2026-10-28", clock, now)
    assertEquals(99900L, prev.methods[0].outside)
    assertEquals(DayClose.Status.MISMATCH, prev.status)
  }

  @Test
  fun `جدول‌های زبانه همان عددهای گزارش‌اند و ستون‌هایشان کامل است`() {
    val r = DayEnd.report(ledger(), date, clock, now)
    val t = DayEndModel.tables(r, 0)
    assertEquals(listOf("dayend.methods", "dayend.shifts"), t.map { it.id })
    t.forEach { tab ->
      tab.rows.forEach { assertEquals(tab.columns.size, it.size) }
      assertEquals(tab.columns.size, tab.total!!.size)
      val keys = tab.columns.map { it.key }
      assertTrue(ReportFreshModel.cardKeys(tab.id).isNotEmpty())
      assertTrue(keys.containsAll(ReportFreshModel.cardKeys(tab.id)))
    }
    val methods = t[0]
    assertEquals("نقد", methods.rows[0][0].content)
    val net = methods.columns.indexOfFirst { it.key == "net" }
    assertEquals(r.total.shifts.net, methods.total!![net].content.toLong())
    val shifts = t[1]
    assertEquals("علی · 08:00–12:00", shifts.rows[0][0].content)
    assertEquals("رضا · 13:00–…", shifts.rows[1][0].content)
    assertEquals(listOf("بسته (Z)", "باز (X)"), shifts.rows.map { it[1].content })
    val counted = shifts.columns.indexOfFirst { it.key == "countedCash" }
    assertNull(shifts.rows[1][counted].takeIf { it !is kotlinx.serialization.json.JsonNull })
    assertEquals("175000", shifts.total!![counted].content)
    //  خروجی (اکسل/چاپ) همان جدول‌ها
    assertEquals(shifts.rows.size + 2, ReportFreshModel.exportRows(listOf(shifts)).size)
  }

  @Test
  fun `زبانه فقط با دیدنِ پول و روزها از امروز به عقب`() {
    val core = ReportFreshModel.tabs(true)
    assertEquals(core.map { it.id } + "dayend", DayEndModel.tabs(core, true).map { it.id })
    assertEquals(listOf("debts"), DayEndModel.tabs(ReportFreshModel.tabs(false), false).map { it.id })
    val days = DayEndModel.days(date)
    assertEquals(7, days.size)
    assertEquals(listOf(date, "2026-10-28"), days.take(2))
    assertEquals("امروز", DayEndModel.dayLabel(date, date) { it })
    assertEquals("دیروز", DayEndModel.dayLabel("2026-10-28", date) { it })
    assertEquals("2026-10-27", DayEndModel.dayLabel("2026-10-27", date) { it })
    //  کابل +۴:۳۰
    assertEquals("12:35", DayEndModel.hhmm(at(8, 5), 270))
    assertEquals("23:59", DayEndModel.hhmm(-60_000L, 0))
  }

  private fun at(hour: Int, minute: Int) = base + hour * h + minute * 60_000L
}
