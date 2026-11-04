package ir.vil3ntec.tohid.ui.v3.sales.history

import ir.vil3ntec.tohid.core.hw.PrinterProfile
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrShift
import ir.vil3ntec.tohid.core.receipt.ReceiptBlock
import ir.vil3ntec.tohid.core.receipt.ReceiptDoc
import ir.vil3ntec.tohid.core.receipt.ReceiptFormat
import ir.vil3ntec.tohid.core.receipt.ReceiptMetrics
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportDates
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ONLINE_SERIES
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SaleReturn
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.data.ShopData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** کاغذِ «چاپ» ِ تاریخچه: خلاصه و همهٔ فاکتورها روی ۵۸ و ۸۰ میلی‌متر، تکه‌ها و شمارشِ کور */
class PeriodSlipTest {

  private val zone = SaleTime.zoneOffset

  @Before fun fa() {
    I18n.source = { Lang.FA }
    SaleTime.zoneOffset = { 0 }
  }

  @After fun reset() {
    I18n.source = { Lang.FA }
    SaleTime.zoneOffset = zone
  }

  private val fmt = object : ReceiptFormat {
    override fun money(afn: Double) = amount(afn) + " ؋"
    override fun amount(afn: Double) = Math.round(afn).toString()
    override fun qty(q: Double) = if (q == Math.floor(q)) q.toLong().toString() else q.toString()
    override fun digits(text: String) = text
    override fun phone(text: String) = text
    override fun date(iso: String) = iso
  }

  private val date = "2026-10-29"
  private val h = 3_600_000L
  private fun at(hour: Int) = ReportDates.epochDay(date)!! * ReportDates.DAY_MS + hour * h

  private fun ledger(): ShopData = ShopData(
    sales = listOf(
      Sale(id = "s1", invoiceNumber = 101, finalTotal = 500.0, createdAt = at(9), date = date),
      Sale(id = "s2", invoiceNumber = 102, finalTotal = 400.0, debtGiven = 400.0, debtorId = "c1", createdAt = at(10), date = date),
      Sale(id = "s3", invoiceNumber = 103, finalTotal = 900.0, status = "cancelled", createdAt = at(11), date = date),
      Sale(id = "w1", invoiceNumber = 7, series = ONLINE_SERIES, finalTotal = 700.0, createdAt = at(12), date = date),
    ),
    saleItems = listOf(
      SaleItem("i1", "s1", "p1", quantity = 2.0, unitPrice = 200.0, totalPrice = 400.0),
      SaleItem("i2", "s1", "", name = "کیسه", quantity = 4.0, unitPrice = 25.0, totalPrice = 100.0),
      SaleItem("i3", "s2", "p2", quantity = 1.0, unitPrice = 400.0, totalPrice = 400.0),
      SaleItem("i4", "s3", "p1", quantity = 4.5, unitPrice = 200.0, totalPrice = 900.0),
      SaleItem("i5", "w1", "p2", quantity = 1.0, unitPrice = 700.0, totalPrice = 700.0),
    ),
    products = listOf(Product(id = "p1", name = "برنج سیلا"), Product(id = "p2", name = "روغن")),
    debtors = listOf(Debtor(id = "c1", name = "احمد")),
    saleReturns = listOf(SaleReturn(id = "r1", saleId = "s1", amount = 50.0, createdAt = at(13), date = date)),
  )

  private val clock = ReportClock.fixed(0, 0)
  private fun book(d: ShopData = ledger()) = HistoryBook.build(d, clock, at(20), gregorian = false)
  private val head = PeriodSlip.Head("دکانِ نمونه", "امروز · 7 عقرب 1405", null, "1405/08/07 · 20:00")

  private fun texts(doc: ReceiptDoc): List<String> = doc.blocks.mapNotNull {
    when (it) {
      is ReceiptBlock.Text -> it.text
      is ReceiptBlock.Row -> it.label + " = " + it.value
      is ReceiptBlock.Item -> it.name + " | " + it.detail + " = " + it.total
      else -> null
    }
  }

  @Test
  fun `خلاصه — جمعِ هر روش، برگشت، خالص و لغوشده و اینترنتیِ بیرون از جمع، روی ۵۸ و ۸۰`() {
    val day = book().current(SalesPeriods.Unit.DAY)
    for (w in listOf(PrinterProfile.WIDTH_58MM, PrinterProfile.WIDTH_80MM)) {
      val doc = PeriodSlip.summary(day, head, w, fmt, money = true)
      assertEquals(w, doc.widthDots)
      assertEquals(ReceiptMetrics.of(w), doc.metrics)
      val t = texts(doc)
      assertEquals("دکانِ نمونه", t[0])
      assertTrue(t.contains(StrHistory.slipSummary))
      assertTrue(t.contains("امروز · 7 عقرب 1405"))
      assertTrue(t.contains(StrHistory.invoiceCount + " = 2"))
      assertTrue(t.contains(StrShift.methodSales(PayFlow.methodName("cash")) + " = 500"))
      assertTrue(t.contains(StrShift.methodSales(PayFlow.methodName("account")) + " = 400"))
      assertTrue(t.contains(StrHistory.salesTotal + " = 900"))
      assertTrue(t.contains(StrHistory.returns + " (1) = −50"))
      assertTrue(t.contains(StrHistory.net + " = 850"))
      assertTrue(t.contains(StrHistory.cancelledInvoices + " = 1 · 900"))
      assertTrue(t.contains(StrHistory.onlineOrders + " = 1 · 700"))
      //  هیچ قلمی در خلاصه
      assertFalse(t.any { it.contains("برنج") })
    }
  }

  @Test
  fun `شمارشِ کور — خلاصه بی هیچ مبلغی`() {
    val doc = PeriodSlip.summary(book().current(SalesPeriods.Unit.DAY), head, PrinterProfile.WIDTH_58MM, fmt, money = false)
    val rows = doc.blocks.filterIsInstance<ReceiptBlock.Row>()
    assertEquals(listOf("2", "1", "1", "1"), rows.map { it.value })
    assertTrue(texts(doc).contains(StrHistory.countsOnly))
    assertFalse(texts(doc).any { it.contains("500") || it.contains("850") })
  }

  @Test
  fun `همهٔ فاکتورها — هشتاد میلی‌متر یک سطر برای هر قلم، پنجاه‌وهشت دو سطر`() {
    val b = book()
    val day = b.current(SalesPeriods.Unit.DAY)
    val src = PeriodSlip.source(b.look)

    val wide = PeriodSlip.sections(day, head, src, PrinterProfile.WIDTH_80MM, fmt, money = true)
    //  به ترتیبِ زمان: ۱۰۱، ۱۰۲، ۱۰۳ (لغوشده) و سفارشِ اینترنتی
    assertEquals(4, wide.invoices.size)
    val first = wide.invoices[0]
    assertEquals("#101 · 9:00", (first[0] as ReceiptBlock.Text).text)
    assertEquals(ReceiptBlock.Row("برنج سیلا × 2", "400", ir.vil3ntec.tohid.core.receipt.TextStyle.SMALL), first[1])
    assertEquals(ReceiptBlock.Row("کیسه × 4", "100", ir.vil3ntec.tohid.core.receipt.TextStyle.SMALL), first[2])
    assertTrue(first.none { it is ReceiptBlock.Item })
    assertEquals("#102 · 10:00 · احمد", (wide.invoices[1][0] as ReceiptBlock.Text).text)
    assertEquals(StrHistory.cancelledMark, (wide.invoices[2][1] as ReceiptBlock.Text).text)
    assertEquals(StrHistory.onlineMark, (wide.invoices[3][1] as ReceiptBlock.Text).text)

    val narrow = PeriodSlip.sections(day, head, src, PrinterProfile.WIDTH_58MM, fmt, money = true)
    val n1 = narrow.invoices[0]
    assertEquals(ReceiptBlock.Item("برنج سیلا", "2 × 200", "400"), n1[1])
    assertEquals(ReceiptBlock.Item("کیسه", "4 × 25", "100"), n1[2])
    assertTrue(narrow.invoices[2].any { it is ReceiptBlock.Item && it.name == "برنج سیلا" && it.detail == "4.5 × 200" })

    //  پایانِ کاغذ همان جمع‌های خلاصه
    val foot = narrow.foot.filterIsInstance<ReceiptBlock.Row>().map { it.label + " = " + it.value }
    assertTrue(foot.contains(StrHistory.net + " = 850"))
    assertTrue(narrow.foot.last() == ReceiptBlock.Text(StrHistory.endOfList, ir.vil3ntec.tohid.core.receipt.TextStyle.SMALL))

    for (w in listOf(PrinterProfile.WIDTH_58MM, PrinterProfile.WIDTH_80MM)) {
      val docs = PeriodSlip.parts(PeriodSlip.sections(day, head, src, w, fmt, true), w)
      assertEquals(1, docs.size)
      assertTrue(docs.single().blocks.containsAll(narrow.head.filterIsInstance<ReceiptBlock.Text>()))
      assertTrue(docs.single().widthDots == w)
    }
  }

  @Test
  fun `کاغذِ بلند تکه‌تکه — سرخط با نخستین و پایان با واپسین، و کاغذ با شمارِ فاکتور بلندتر`() {
    val many = ShopData(
      sales = (0 until 95).map { Sale(id = "s$it", invoiceNumber = it + 1, finalTotal = 100.0, createdAt = at(8) + it * 60_000L, date = date) },
      saleItems = (0 until 95).flatMap { i -> (0 until 3).map { SaleItem("i$i-$it", "s$i", "", name = "قلم $it", quantity = 1.0, unitPrice = 100.0 / 3, totalPrice = 100.0 / 3) } },
    )
    val b = book(many)
    val day = b.current(SalesPeriods.Unit.DAY)
    val sections = PeriodSlip.sections(day, head, PeriodSlip.source(b.look), PrinterProfile.WIDTH_58MM, fmt, true)
    val docs = PeriodSlip.parts(sections, PrinterProfile.WIDTH_58MM)
    assertTrue("تکه‌ها ${docs.size}", docs.size >= 5)
    assertEquals(sections.head, docs.first().blocks.take(sections.head.size))
    assertEquals(sections.foot, docs.last().blocks.takeLast(sections.foot.size))
    docs.drop(1).forEach { assertFalse(it.blocks.containsAll(sections.head)) }
    docs.dropLast(1).forEach { d -> assertFalse(d.blocks.any { it == ReceiptBlock.Text(StrHistory.endOfList, ir.vil3ntec.tohid.core.receipt.TextStyle.SMALL) }) }
    //  هیچ فاکتوری گم یا دوتایی نشد
    val heads = docs.flatMap { d -> d.blocks.filterIsInstance<ReceiptBlock.Text>().filter { it.text.startsWith("#") } }
    assertEquals(95, heads.size)
    assertEquals(heads.distinct(), heads)

    val one = PeriodSlip.paperCm(listOf(PeriodSlip.summary(day, head, PrinterProfile.WIDTH_58MM, fmt, true)))
    val all = PeriodSlip.paperCm(docs)
    assertTrue("$one / $all", one in 5..30)
    assertTrue("$one / $all", all > one * 10)
  }

  @Test
  fun `ماه و سال تاریخِ هر فاکتور را دارند`() {
    val b = book()
    val month = b.current(SalesPeriods.Unit.MONTH)
    val s = PeriodSlip.sections(month, head, PeriodSlip.source(b.look), PrinterProfile.WIDTH_80MM, fmt, true)
    assertEquals("#101 · $date · 9:00", (s.invoices[0][0] as ReceiptBlock.Text).text)
  }

  /** سطر همان‌طور که در پاراگرافِ راست‌به‌چپ (یا چپ‌به‌راست) دیده می‌شود: از چپ به راست، بی LRM/RLM ِ نادیدنی */
  private fun shown(text: String, rtl: Boolean = true): String {
    val bidi = java.text.Bidi(text, if (rtl) java.text.Bidi.DIRECTION_RIGHT_TO_LEFT else java.text.Bidi.DIRECTION_LEFT_TO_RIGHT)
    val levels = ByteArray(text.length) { bidi.getLevelAt(it).toByte() }
    val chars = Array<Any>(text.length) { text[it] }
    java.text.Bidi.reorderVisually(levels, 0, chars, 0, text.length)
    return chars.joinToString("").filterNot { it == '\u200E' || it == '\u200F' }
  }

  @Test
  fun `شمارهٔ حرف‌دارِ سفارشِ اینترنتی در کاغذِ راست‌به‌چپ — نشانِ شماره کنارِ خودش و ساعت پس از آن`() {
    val b = book()
    val src = PeriodSlip.source(b.look)
    val day = PeriodSlip.sections(b.current(SalesPeriods.Unit.DAY), head, src, PrinterProfile.WIDTH_80MM, fmt, money = true)
    val line = (day.invoices[3][0] as ReceiptBlock.Text).text
    assertEquals("\u200E#W-7\u200F · 12:00", line)
    //  چیدنِ دوجهته (همان الگوریتمِ Java2D ِ کامپیوتر و اندروید): از راست «#W-7» و سپس ساعت
    assertEquals("12:00 · #W-7", shown(line))
    //  بی نشانه همان خطای گزارش‌شده بود: «#» آن سرِ سطر و ساعت پیش از شماره
    assertEquals("W-7 · 12:00#", shown("#W-7 · 12:00"))
    //  ماه و سال: تاریخ میانِ شماره و ساعت، همان ترتیب
    val month = PeriodSlip.sections(b.current(SalesPeriods.Unit.MONTH), head, src, PrinterProfile.WIDTH_58MM, fmt, money = true)
    val monthLine = month.invoices.map { (it[0] as ReceiptBlock.Text).text }.single { it.contains("W-7") }
    assertEquals("12:00 · $date · #W-7", shown(monthLine))
    //  شمارهٔ فقط‌رقم دست نمی‌خورد و از پیش درست است
    assertEquals("#101", PeriodSlip.numberTag("101"))
    assertEquals("10:00 · #102", shown((day.invoices[1][0] as ReceiptBlock.Text).text.substringBefore(" · احمد")))
    //  زبانِ چپ‌به‌راست بی نشانه (RLM آن‌جا ساعت را جابه‌جا می‌کرد)
    I18n.source = { Lang.EN }
    assertEquals("#W-7", PeriodSlip.numberTag("W-7"))
    assertEquals("#W-7 · 12:00", shown("#W-7 · 12:00", rtl = false))
  }

  @Test
  fun `دورهٔ بی فاکتور یک سند با سرخط و جمعِ صفر`() {
    val empty = SalesPeriods.Period("2026-11-01", SalesPeriods.Unit.DAY, "2026-11-01", "2026-11-01", SalesPeriods.Totals(), emptyList())
    val b = book()
    val docs = PeriodSlip.parts(PeriodSlip.sections(empty, head, PeriodSlip.source(b.look), PrinterProfile.WIDTH_58MM, fmt, true), PrinterProfile.WIDTH_58MM)
    assertEquals(1, docs.size)
    assertTrue(texts(docs.single()).contains(StrHistory.invoiceCount + " = 0"))
  }
}
