package ir.vil3ntec.tohid.ui.v3.sales.history

import ir.vil3ntec.tohid.core.hw.PrintBatch
import ir.vil3ntec.tohid.core.hw.PrinterProfile
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrShift
import ir.vil3ntec.tohid.core.receipt.PaperLength
import ir.vil3ntec.tohid.core.receipt.ReceiptBlock
import ir.vil3ntec.tohid.core.receipt.ReceiptDoc
import ir.vil3ntec.tohid.core.receipt.ReceiptFormat
import ir.vil3ntec.tohid.core.receipt.ReceiptMetrics
import ir.vil3ntec.tohid.core.receipt.TextStyle
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesPeriods

/**
 *  کاغذِ «چاپ» ِ یک روز، ماه یا سالِ تاریخچه — خالص (بلوک‌های `ReceiptDoc`؛ کشیدن با `ReceiptRaster` ِ همان فاکتور).
 *
 *  • **خلاصه**: شمارِ فاکتور، فروشِ هر روشِ پرداخت، برگشت، خالص و دریافتِ بدهی — عددها همان
 *    `SalesPeriods.Totals` (یعنی همان دفترِ X/Z و پایانِ روز)؛ لغوشده و سفارشِ اینترنتی جدا و «بیرون از جمع».
 *  • **همهٔ فاکتورها** (پیش‌فرض، خواستِ صاحب‌کار): هر فاکتور با اقلامش پشتِ سرِ هم، لغوشده و اینترنتی نشان‌دار؛
 *    پایانش همان جمع‌های خلاصه.
 *    ۸۰ میلی‌متر: هر قلم یک سطر («نام × مقدار ــ مبلغ»)؛ ۵۸ میلی‌متر: نام در یک سطر و «مقدار × فی ــ مبلغ» زیرش.
 *    کاغذِ بلند تکه‌تکه به صف می‌رود ([parts] با `PrintBatch.plan`) تا «لغو» در میانه ممکن باشد.
 *  • بی حقِ دیدنِ «مورد انتظار» (شمارشِ کور، PAY-13) هیچ جمعِ پولی چاپ نمی‌شود — فقط شمارها، مثلِ X/Z ِ کور.
 */
object PeriodSlip {

  /** هر تکهٔ چاپ تا حدودِ ۲۵ سانت و ۲۰ فاکتور — بس کوتاه که «لغو» زود برسد، بس بلند که صف شلوغ نشود */
  const val PART_DOTS = 2_000
  const val PART_INVOICES = 20

  /** سرخط و زمانِ چاپ؛ [range] فقط ماه و سال («۱۴۰۵/۰۸/۰۱ — ۱۴۰۵/۰۸/۳۰») */
  data class Head(val shop: String, val title: String, val range: String?, val printedAt: String)

  /** بخش‌های «همهٔ فاکتورها»: سرخط، یک فهرست بلوک برای هر فاکتور، و پایان */
  class Sections(val head: List<ReceiptBlock>, val invoices: List<List<ReceiptBlock>>, val foot: List<ReceiptBlock>)

  /** برای هر فاکتور: اقلامِ نام‌دار، نامِ مشتری و برچسبِ پرداخت — از `RowLookup` و `ReceiptModel` ِ صفحه */
  class Source(
    val items: (Sale) -> List<Line>,
    val customer: (Sale) -> String?,
    val payLabel: (Sale) -> String,
    val methodName: (String) -> String = PayFlow::methodName,
  )

  data class Line(val name: String, val qty: Double, val unitPrice: Double, val total: Double)

  /** همان نام‌ها و برچسب‌های صفحه، از نمایه‌های یک‌بارهٔ تاریخچه */
  fun source(look: RowLookup): Source = Source(
    items = { s -> look.items[s.id].orEmpty().map { Line(look.itemName(it), it.quantity, it.unitPrice, it.totalPrice) } },
    customer = { s -> s.debtorId?.let(look.debtors::get) },
    payLabel = ReceiptModel::payLabel,
  )

  private fun head(h: Head, kind: String): List<ReceiptBlock> = buildList {
    if (h.shop.isNotBlank()) add(ReceiptBlock.Text(h.shop.trim(), TextStyle.TITLE))
    add(ReceiptBlock.Text(kind, TextStyle.BODY))
    add(ReceiptBlock.Text(h.title, TextStyle.TOTAL))
    h.range?.let { add(ReceiptBlock.Text(it, TextStyle.SMALL)) }
    add(ReceiptBlock.Text(StrHistory.slipPrintedAt(h.printedAt), TextStyle.SMALL))
    add(ReceiptBlock.Rule(dashed = false))
  }

  /** جمع‌های دوره — هم برگهٔ خلاصه، هم پایانِ «همهٔ فاکتورها» */
  fun totals(t: SalesPeriods.Totals, fmt: ReceiptFormat, money: Boolean, methodName: (String) -> String = PayFlow::methodName): List<ReceiptBlock> = buildList {
    fun amount(minor: Long) = fmt.amount(PayFlow.major(minor))
    fun count(n: Int) = fmt.digits(n.toString())
    add(ReceiptBlock.Row(StrHistory.invoiceCount, count(t.invoices)))
    if (!money) {
      add(ReceiptBlock.Row(StrHistory.returnCount, count(t.returns)))
      if (t.cancelled > 0) add(ReceiptBlock.Row(StrHistory.cancelledInvoices, count(t.cancelled), TextStyle.SMALL))
      if (t.online > 0) add(ReceiptBlock.Row(StrHistory.onlineOrders, count(t.online), TextStyle.SMALL))
      add(ReceiptBlock.Text(StrHistory.countsOnly, TextStyle.SMALL))
      return@buildList
    }
    t.methods.forEach { (m, s) -> if (s.sales != 0L) add(ReceiptBlock.Row(StrShift.methodSales(methodName(m)), amount(s.sales), TextStyle.SMALL)) }
    add(ReceiptBlock.Row(StrHistory.salesTotal, amount(t.sales)))
    add(ReceiptBlock.Row(StrHistory.returns + " (" + count(t.returns) + ")", if (t.refunds == 0L) amount(0) else "−" + amount(t.refunds)))
    add(ReceiptBlock.Rule(dashed = false))
    add(ReceiptBlock.Row(StrHistory.net, amount(t.net), TextStyle.TOTAL))
    if (t.receipts != 0L) add(ReceiptBlock.Row(StrHistory.receipts, amount(t.receipts), TextStyle.SMALL))
    if (t.cancelled > 0 || t.online > 0) add(ReceiptBlock.Rule())
    if (t.cancelled > 0) add(ReceiptBlock.Row(StrHistory.cancelledInvoices, count(t.cancelled) + " · " + amount(t.cancelledMinor), TextStyle.SMALL))
    if (t.online > 0) add(ReceiptBlock.Row(StrHistory.onlineOrders, count(t.online) + " · " + amount(t.onlineMinor), TextStyle.SMALL))
  }

  /** برگهٔ خلاصه — یک کار، با برش */
  fun summary(p: SalesPeriods.Period, h: Head, widthDots: Int, fmt: ReceiptFormat, money: Boolean, methodName: (String) -> String = PayFlow::methodName): ReceiptDoc {
    val w = PrinterProfile.paper(widthDots)
    return ReceiptDoc(w, ReceiptMetrics.of(w), head(h, StrHistory.slipSummary) + totals(p.totals, fmt, money, methodName))
  }

  /**
   *  «#شماره» ِ سرِ هر فاکتور در سطرِ راست‌به‌چپ. شمارهٔ حرف‌دار (سفارشِ اینترنتی «W-71»، سریِ دستگاه «A1-5») حرفِ لاتینِ
   *  قوی دارد: بی نشانه «#» به آن سرِ سطر می‌رفت و ساعتِ پس از آن هم چپ‌به‌راست می‌شد («W-71 · 11:30#»). LRM پیش از «#»
   *  و RLM پس از شماره آن را یک تکهٔ چپ‌به‌راست می‌کنند و بقیهٔ سطر راست‌به‌چپ می‌ماند. ایزوله (LRI/PDI) نه: وزیرمتن
   *  نویسه‌اش را ندارد و Java2D ِ پیش از JDK 21 (کامپیوتر با ۱۷ ساخته می‌شود) آن را نادیدنی نمی‌گیرد ⇒ جعبه روی کاغذ؛
   *  LRM/RLM در خودِ قلم هست (پهنای صفر) و هر دو سکو نادیدنی‌اش می‌کشند. کاغذ تصویر است (`ReceiptPainter`، نه متنِ
   *  ESC/POS). شمارهٔ فقط‌رقم و زبانِ چپ‌به‌راست درست بودند و دست نمی‌خورند.
   */
  fun numberTag(number: String, rtl: Boolean = I18n.lang.rtl): String =
    if (rtl && !number.all { it.isDigit() }) "\u200E#$number\u200F" else "#$number"

  /** یک فاکتور: «#۱۲۴۵ · ساعت · مشتری»، نشانِ لغو/اینترنتی، اقلام (به پهنای کاغذ) و جمعِ همان فاکتور */
  fun invoice(s: Sale, src: Source, widthDots: Int, fmt: ReceiptFormat, withDate: Boolean): List<ReceiptBlock> = buildList {
    val wide = PrinterProfile.paper(widthDots) >= PrinterProfile.WIDTH_80MM
    val day = if (withDate && s.date.isNotBlank()) fmt.date(s.date) else ""
    val head = listOf(numberTag(ReceiptModel.number(s)), day, SaleTime.clock(s.createdAt), src.customer(s).orEmpty())
      .filter { it.isNotBlank() }.joinToString(" · ")
    add(ReceiptBlock.Text(fmt.digits(head), TextStyle.BODY, center = false))
    when {
      s.status == "cancelled" -> add(ReceiptBlock.Text(StrHistory.cancelledMark, TextStyle.SMALL, center = false))
      CashShift.isOnline(s) -> add(ReceiptBlock.Text(StrHistory.onlineMark, TextStyle.SMALL, center = false))
    }
    for (l in src.items(s)) {
      add(
        if (wide) ReceiptBlock.Row(l.name + " × " + fmt.qty(l.qty), fmt.amount(l.total), TextStyle.SMALL)
        else ReceiptBlock.Item(l.name, fmt.qty(l.qty) + " × " + fmt.amount(l.unitPrice), fmt.amount(l.total))
      )
    }
    add(ReceiptBlock.Row(StrHistory.total + " · " + src.payLabel(s), fmt.amount(s.finalTotal)))
    add(ReceiptBlock.Rule())
  }

  fun sections(p: SalesPeriods.Period, h: Head, src: Source, widthDots: Int, fmt: ReceiptFormat, money: Boolean): Sections {
    //  کاغذ به ترتیبِ زمان (کهنه‌ترین اول) — مثلِ دفترِ کاغذی؛ صفحه تازه‌ترین را اول نشان می‌دهد
    val withDate = p.unit != SalesPeriods.Unit.DAY
    val invoices = p.sales.asReversed().map { invoice(it, src, widthDots, fmt, withDate) }
    val foot = buildList {
      add(ReceiptBlock.Gap(ReceiptMetrics.of(widthDots).gap))
      addAll(totals(p.totals, fmt, money, src.methodName))
      add(ReceiptBlock.Text(StrHistory.endOfList, TextStyle.SMALL))
    }
    return Sections(head(h, StrHistory.slipInvoices), invoices, foot)
  }

  /** «همهٔ فاکتورها» ⇒ چند سند پیاپی؛ سرخط با نخستین تکه و پایان با واپسین */
  fun parts(s: Sections, widthDots: Int): List<ReceiptDoc> {
    val w = PrinterProfile.paper(widthDots)
    val m = ReceiptMetrics.of(w)
    val plan = PrintBatch.plan(s.invoices.map { PaperLength.dots(it, w, m) }, PART_DOTS, PART_INVOICES)
    if (plan.isEmpty()) return listOf(ReceiptDoc(w, m, s.head + s.foot))
    return plan.mapIndexed { i, range ->
      val body = range.flatMap { s.invoices[it] }
      ReceiptDoc(w, m, (if (i == 0) s.head else emptyList()) + body + (if (i == plan.lastIndex) s.foot else emptyList()))
    }
  }

  /** کاغذِ تخمینی (سانتی‌متر) برای برگهٔ تأیید — تکه‌های میانی بی برش و بی سطرِ خالی‌اند */
  fun paperCm(docs: List<ReceiptDoc>, feedLines: Int = 3): Int =
    PaperLength.cm(docs.mapIndexed { i, d -> PaperLength.dots(d, if (i == docs.lastIndex) feedLines else 0) }.sum())
}
