package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.pay.DayClose
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportDates
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  «تاریخچه» ِ روز/ماه/سال (`SalesPeriods`): گروه‌بندی با مرزِ ماه و سالِ خورشیدی (و میلادی با تنظیمِ تقویم)، و برابریِ
 *  جمع‌ها با پایانِ روز (`DayEnd`) و Z ِ شیفت — عدد به عدد، روش به روش. کارایی با ده هزار فاکتور.
 */
class SalesPeriodsTest {
  private val clock = ReportClock.fixed(0, 0)
  private val h = 3_600_000L
  private var n = 0
  private val newId: () -> String = { "id${n++}" }

  private fun at(iso: String, hour: Int): Long = ReportDates.epochDay(iso)!! * ReportDates.DAY_MS + hour * h
  private fun sale(id: String, iso: String, hour: Int, afn: Double, vararg extra: (Sale) -> Sale): Sale =
    extra.fold(Sale(id = id, finalTotal = afn, total = afn, createdAt = at(iso, hour), date = iso)) { s, f -> f(s) }

  private fun <T> ok(o: PayFlow.Outcome<T>): T = when (o) {
    is PayFlow.Outcome.Ok -> o.value
    is PayFlow.Outcome.Bad -> throw AssertionError(o.problem.code)
  }

  private val zero = DayClose.Sums()
  private fun ledgerOf(r: DayClose.Report) = r.methods.associate { it.method to it.ledger }.filterValues { it != zero }
  private fun periodOf(t: SalesPeriods.Totals) = t.methods.filterValues { it != zero }

  /* ------------------------------ گروه‌بندی ------------------------------ */

  @Test
  fun `مرزِ سالِ خورشیدی — حوت و حمل دو ماه و دو سال‌اند و میلادی یک ماه`() {
    val lastHut = SolarHijri.fromSolar(1404, 12, SolarHijri.monthLength(1404, 12))!!
    val nowruz = SolarHijri.fromSolar(1405, 1, 1)!!
    assertEquals(nowruz, CivilDays.addDays(lastHut, 1))
    val d = ShopData(sales = listOf(sale("a", lastHut, 10, 100.0), sale("b", nowruz, 10, 250.0)))
    val days = SalesPeriods.days(d, clock, at(nowruz, 20))
    assertEquals(listOf(nowruz, lastHut), days.map { it.date })

    val months = SalesPeriods.periods(days, SalesPeriods.Unit.MONTH, gregorian = false)
    assertEquals(listOf("1405-01", "1404-12"), months.map { it.key })
    assertEquals(SolarHijri.fromSolar(1404, 12, 1) to lastHut, months[1].from to months[1].to)
    assertEquals(listOf(25_000L, 10_000L), months.map { it.totals.sales })
    val years = SalesPeriods.periods(days, SalesPeriods.Unit.YEAR, gregorian = false)
    assertEquals(listOf("1405", "1404"), years.map { it.key })
    assertEquals(SolarHijri.fromSolar(1404, 1, 1) to lastHut, years[1].from to years[1].to)
    assertEquals(listOf(listOf("b"), listOf("a")), years.map { p -> p.sales.map { it.id } })

    //  «تقویمِ میلادی»: همان دو روز در یک ماه و یک سالِ میلادی‌اند (مارچ ۲۰۲۶)
    val g = SalesPeriods.periods(days, SalesPeriods.Unit.MONTH, gregorian = true)
    assertEquals(listOf("2026-03"), g.map { it.key })
    assertEquals("2026-03-01" to "2026-03-31", g.single().from to g.single().to)
    assertEquals(35_000L, g.single().totals.sales)
  }

  @Test
  fun `مرزِ ماهِ خورشیدی و سالِ میلادی`() {
    //  میزانِ ۱۴۰۵ سی روز است: ۳۰ میزان و ۱ عقرب دو ماهِ جدا
    val lastMizan = SolarHijri.fromSolar(1405, 7, 30)!!
    val firstAqrab = SolarHijri.fromSolar(1405, 8, 1)!!
    assertEquals(firstAqrab, CivilDays.addDays(lastMizan, 1))
    //  ۳۱ دسامبر و ۱ جنوری: یک ماهِ خورشیدی (جدی ۱۴۰۴)، دو سالِ میلادی
    val d = ShopData(
      sales = listOf(
        sale("m1", lastMizan, 9, 10.0), sale("m2", firstAqrab, 9, 20.0),
        sale("g1", "2025-12-31", 9, 30.0), sale("g2", "2026-01-01", 9, 40.0),
      ),
    )
    val days = SalesPeriods.days(d, clock, at(firstAqrab, 23))
    val solar = SalesPeriods.periods(days, SalesPeriods.Unit.MONTH, gregorian = false)
    assertEquals(listOf("1405-08", "1405-07", "1404-10"), solar.map { it.key })
    assertEquals(listOf(listOf("m2"), listOf("m1"), listOf("g2", "g1")), solar.map { p -> p.sales.map { it.id } })
    assertEquals(SolarHijri.fromSolar(1405, 7, 1) to lastMizan, SalesPeriods.range("1405-07", SalesPeriods.Unit.MONTH, false))
    val gYears = SalesPeriods.periods(days, SalesPeriods.Unit.YEAR, gregorian = true)
    assertEquals(listOf("2026", "2025"), gYears.map { it.key })
    assertEquals("2025-01-01" to "2025-12-31", gYears[1].from to gYears[1].to)
    assertEquals(listOf("2026-10", "2026-01", "2025-12"), SalesPeriods.periods(days, SalesPeriods.Unit.MONTH, gregorian = true).map { it.key }.distinct())
  }

  @Test
  fun `روزِ فاکتور روزِ کاریِ ساعتِ گزارش‌هاست و امروز بی فاکتور جمعِ صفر دارد`() {
    //  پایانِ روزِ کاری ساعتِ ۲ بامداد: فروشِ ۱:۳۰ مالِ دیروز است، همان قاعدهٔ پایانِ روز
    val late = ReportClock.fixed(0, 120)
    val d = ShopData(sales = listOf(sale("x", "2026-11-03", 1, 50.0)))
    assertEquals("2026-11-02", SalesPeriods.dayOf(d.sales[0], late))
    val book = HistoryBook(SalesPeriods.days(d, late, at("2026-11-04", 12)), "2026-11-04", false, ir.vil3ntec.tohid.ui.v3.sales.history.RowLookup(d))
    val today = book.current(SalesPeriods.Unit.DAY)
    assertEquals("2026-11-04", today.key)
    assertTrue(today.totals.isEmpty && today.sales.isEmpty())
    assertEquals(listOf("2026-11-02"), book.older(SalesPeriods.Unit.DAY).map { it.key })
  }

  /* ------------------------------ برابری با X/Z ------------------------------ */

  private val date = "2026-10-29"
  private val openA = AuditEntry(id = "A", type = CashShift.OPEN, createdAt = at(date, 8), amount = 1000.0, by = "علی")

  /** یک شیفتِ کامل: نقد ۵۰۰، نقد ۳۰۰ + کارت ۲۰۰، نسیهٔ ۴۰۰، برگشتِ نقدِ ۵۰، رسیدِ ۱۰۰؛ لغوشده و اینترنتی هم */
  private fun dayLedger(): ShopData = ShopData(
    sales = listOf(
      sale("s1", date, 9, 500.0),
      sale("s2", date, 10, 500.0, { it.copy(payments = listOf(SalePayment("cash", "cash", 30000, "confirmed"), SalePayment("afpay_card", "card", 20000, "pending", "C1", "manual"))) }),
      sale("s3", date, 14, 400.0, { it.copy(debtGiven = 400.0, debtorId = "c1") }),
      sale("x1", date, 12, 900.0, { it.copy(status = "cancelled") }),
      sale("w1", date, 13, 700.0, { it.copy(series = ONLINE_SERIES) }),
      //  روزِ دیگر
      sale("s9", "2026-10-28", 22, 999.0),
    ),
    saleReturns = listOf(SaleReturn(id = "r1", saleId = "s1", amount = 50.0, createdAt = at(date, 11), date = date)),
    transactions = listOf(DebtTransaction(id = "t1", debtorId = "c1", type = "receive", amount = 100.0, createdAt = at(date, 15), date = date)),
    auditLog = listOf(openA),
  )

  @Test
  fun `جمعِ روز همان «دفتر» ِ پایانِ روز و همان Z ِ شیفت است`() {
    var d = dayLedger()
    //  ۱۰۰۰ + ۸۰۰ + ۱۰۰ − ۵۰ = ۱۸۵۰ ⇒ بستن بی اختلاف
    val counts = mapOf("100000" to 1L, "50000" to 1L, "10000" to 3L, "5000" to 1L)
    val z = ok(PayFlow.closeRow(d, openA, PayFlow.Count(counts, methodCounts = mapOf("afpay_card" to 20000L), by = "علی"), at(date, 20), newId))
    d = d.copy(auditLog = d.auditLog + z.row)
    val now = at(date, 21)

    val day = SalesPeriods.days(d, clock, now).single { it.date == date }
    val r = DayEnd.report(d, date, clock, now)
    assertEquals(DayClose.Status.CLOSED, r.status)
    //  روش به روش: همان دفترِ پایانِ روز…
    assertEquals(ledgerOf(r), periodOf(day.totals))
    assertEquals(r.total.ledger, day.totals.total)
    //  …و همان Z ِ ذخیره‌شده
    val zMethods = z.report.methods.mapValues { (_, m) -> DayClose.Sums(m.sales, m.refunds, m.receipts) }.filterValues { it != zero }
    assertEquals(zMethods, periodOf(day.totals))
    assertEquals(140_000L, day.totals.sales)
    assertEquals(80_000L, day.totals.cash)
    assertEquals(40_000L, day.totals.credit)
    assertEquals(20_000L, day.totals.other)
    assertEquals(5_000L, day.totals.refunds)
    assertEquals(135_000L, day.totals.net)
    assertEquals(10_000L, day.totals.receipts)
    assertEquals(r.total.ledger.net, day.totals.net + day.totals.receipts)
    //  لغوشده و اینترنتی شمرده می‌شوند ولی در جمع نیستند — همان X/Z
    assertEquals(3, day.totals.invoices)
    assertEquals(1 to 90_000L, day.totals.cancelled to day.totals.cancelledMinor)
    assertEquals(1 to 70_000L, day.totals.online to day.totals.onlineMinor)
    assertEquals(1, day.totals.returns)
    assertEquals(listOf("s3", "w1", "x1", "s2", "s1"), day.sales.map { it.id })
  }

  @Test
  fun `ماه جمعِ پایانِ روزهای خودش است`() {
    val days = (0 until 5).map { CivilDays.addDays(SolarHijri.fromSolar(1405, 7, 28)!!, it)!! }
    val d = ShopData(
      sales = days.flatMapIndexed { i, iso ->
        listOf(sale("c$i", iso, 9, 100.0 + i), sale("k$i", iso, 11, 40.0, { it.copy(debtGiven = 40.0, debtorId = "c1") }))
      },
      saleReturns = listOf(SaleReturn(id = "r", saleId = "c3", amount = 20.0, createdAt = at(days[3], 12), date = days[3])),
    )
    val now = at(days.last(), 23)
    val months = SalesPeriods.periods(SalesPeriods.days(d, clock, now), SalesPeriods.Unit.MONTH, gregorian = false)
    assertEquals(listOf("1405-08", "1405-07"), months.map { it.key })
    for (m in months) {
      val inside = days.filter { it in m.from..m.to }
      val sum = inside.map { DayEnd.report(d, it, clock, now).total.ledger }.fold(zero) { a, x -> a + x }
      assertEquals(m.key, sum, m.totals.total)
      assertEquals(inside.size * 2, m.totals.invoices)
    }
  }

  @Test
  fun `اصلاحِ روشِ پرداختِ دیررس همان پنجرهٔ پایانِ روز را دارد`() {
    val d0 = ShopData(sales = listOf(sale("s1", date, 9, 1000.0), sale("s2", date, 10, 300.0)))
    //  پنج روز پس از فروش ۴۰۰ از نقد به HesabPay — پایانِ روزِ همان روز (پنجرهٔ سه‌روزه) آن را نمی‌شمارد
    val fixAt = at(date, 9) + 5 * 24 * h
    val req = PayFixFlow.Request("s1", "cash", "hesabpay", 40_000, "m1", "allow", null, "مشتری با HesabPay داد", "HP-1")
    val row = (PayFixFlow.build(d0, "pf1", req, CivilDays.addDays(date, 5)!!, fixAt) as PayFixFlow.Built.Ok).row
    val d = d0.copy(auditLog = listOf(row))
    val now = fixAt + h
    val day = SalesPeriods.days(d, clock, now).single { it.date == date }
    assertEquals(ledgerOf(DayEnd.report(d, date, clock, now)), periodOf(day.totals))
    assertEquals(130_000L, day.totals.cash)
  }

  @Test
  fun `فروشِ کشوی نام‌دار هم در تاریخچه است`() {
    val named = AuditEntry(id = "N", type = CashShift.OPEN, createdAt = at(date, 8), amount = 0.0, drawer = "کشوی ۲")
    val d = ShopData(
      sales = listOf(sale("s1", date, 9, 100.0), sale("s2", date, 10, 60.0, { it.copy(shiftId = "N") })),
      auditLog = listOf(named),
    )
    val day = SalesPeriods.days(d, clock, at(date, 20)).single()
    assertEquals(16_000L, day.totals.sales)
    assertEquals(2, day.totals.invoices)
  }

  /* ------------------------------ کارایی ------------------------------ */

  @Test
  fun `ده هزار فاکتور در یک سال و اندی — ساخته و گروه‌بندی در زمانِ معقول`() {
    val start = "2025-09-01"
    val sales = ArrayList<Sale>(10_000)
    val items = ArrayList<SaleItem>(20_000)
    for (i in 0 until 10_000) {
      val iso = CivilDays.addDays(start, i / 25)!!
      val s = sale("s$i", iso, 8 + i % 12, 100.0 + i % 50, {
        when {
          i % 40 == 0 -> it.copy(status = "cancelled")
          i % 33 == 0 -> it.copy(series = ONLINE_SERIES)
          i % 7 == 0 -> it.copy(debtGiven = it.finalTotal, debtorId = "c${i % 30}")
          else -> it
        }
      })
      sales += s
      items += SaleItem("i$i-a", s.id, "p${i % 200}", quantity = 1.0, unitPrice = 60.0, totalPrice = 60.0)
      items += SaleItem("i$i-b", s.id, "p${(i + 7) % 200}", quantity = 2.0, unitPrice = 20.0, totalPrice = 40.0)
    }
    val d = ShopData(
      sales = sales, saleItems = items,
      products = (0 until 200).map { Product(id = "p$it", name = "کالا $it") },
      saleReturns = (0 until 300).map { SaleReturn(id = "r$it", saleId = "s${it * 31}", amount = 10.0, createdAt = sales[it * 31].createdAt + h) },
      transactions = (0 until 300).map { DebtTransaction(id = "t$it", debtorId = "c${it % 30}", type = "receive", amount = 25.0, createdAt = sales[it * 29].createdAt + h) },
    )
    val now = sales.maxOf { it.createdAt } + h
    HistoryBook.build(d, clock, now, gregorian = false) // گرم شدنِ JIT
    val t0 = System.nanoTime()
    val book = HistoryBook.build(d, clock, now, gregorian = false)
    val ms = (System.nanoTime() - t0) / 1_000_000
    println("history: 10k invoices ⇒ ${book.days.size} days, ${book.periods(SalesPeriods.Unit.MONTH).size} months in $ms ms")
    assertTrue("ساختنِ تاریخچه $ms ms", ms < 2_000)
    assertEquals(400, book.days.size)
    val all = book.periods(SalesPeriods.Unit.YEAR).fold(SalesPeriods.Totals()) { a, p -> a + p.totals }
    assertEquals(10_000, all.invoices + all.cancelled + all.online)
    assertEquals(book.days.sumOf { it.totals.sales }, all.sales)
    assertTrue("برگشت‌ها ${all.returns}", all.returns in 250..300)
    //  جمعِ یک روزِ تصادفی همان پایانِ روز است
    val probe = book.days[123]
    assertEquals(ledgerOf(DayEnd.report(d, probe.date, clock, now)), periodOf(probe.totals))
    //  کاغذِ «همهٔ فاکتورها» ِ بزرگ‌ترین سال (بی کشیدن): بلوک‌ها، تخمینِ کاغذ و تکه‌ها
    val year = book.periods(SalesPeriods.Unit.YEAR).maxBy { it.sales.size }
    val fmt = object : ir.vil3ntec.tohid.core.receipt.ReceiptFormat {
      override fun money(afn: Double) = amount(afn)
      override fun amount(afn: Double) = Math.round(afn).toString()
      override fun qty(q: Double) = q.toString()
      override fun digits(text: String) = text
      override fun phone(text: String) = text
      override fun date(iso: String) = iso
    }
    val head = ir.vil3ntec.tohid.ui.v3.sales.history.PeriodSlip.Head("دکان", "1405", null, "—")
    val t1 = System.nanoTime()
    val slip = ir.vil3ntec.tohid.ui.v3.sales.history.PeriodSlip
    val docs = slip.parts(slip.sections(year, head, slip.source(book.look), 384, fmt, money = true), 384)
    val cm = slip.paperCm(docs)
    val slipMs = (System.nanoTime() - t1) / 1_000_000
    println("history: year slip ${year.sales.size} invoices ⇒ ${docs.size} parts, ~$cm cm in $slipMs ms")
    assertTrue("کاغذِ سال $slipMs ms", slipMs < 2_000)
    assertEquals(year.sales.size, docs.sumOf { d -> d.blocks.count { it is ir.vil3ntec.tohid.core.receipt.ReceiptBlock.Text && it.text.startsWith("#") } })
  }
}
