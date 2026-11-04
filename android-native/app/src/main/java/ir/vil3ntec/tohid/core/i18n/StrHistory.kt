package ir.vil3ntec.tohid.core.i18n

/**
 *  «تاریخچه» ِ تازه (روز / ماه / سال) و چاپِ گزارشِ هر دوره روی چاپگرِ فاکتور.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `data/SalesPeriods` و `ui/v3/sales/history/PeriodSlip`. برچسب‌های کاغذ همین
 *  جدول‌اند (مثلِ گزارشِ X/Z در `StrShift`)، پس کاغذ همان زبانِ برنامه را دارد.
 */
@Suppress("unused")
object StrHistory {
  // ── روز، ماه، سال ──
  val day: String get() = t("روز", "Day", "ورځ")
  val month: String get() = t("ماه", "Month", "میاشت")
  val year: String get() = t("سال", "Year", "کال")
  val thisMonth: String get() = t("این ماه", "This month", "دا میاشت")
  val thisYear: String get() = t("امسال", "This year", "سږکال")
  val earlier: String get() = t("پیش‌تر", "Earlier", "مخکې")
  val noDate: String get() = t("بی تاریخ", "No date", "بې نېټې")

  // ── جمع‌ها ──
  fun invoices(n: Any): String = t("${asIs(n)} فاکتور", "$n ${pl(n, "invoice", "invoices")}", "$n بلونه")
  fun online(n: Any): String = t("${asIs(n)} اینترنتی", "$n online", "$n آنلاین")
  fun cancelled(n: Any): String = t("${asIs(n)} لغوشده", "$n cancelled", "$n لغوه شوي")
  fun returnsAmount(amount: Any): String = t("برگشت ${asIs(amount)}", "Returns $amount", "بېرته ورکړه $amount")
  val cash: String get() = t("نقد", "Cash", "نغد")
  val credit: String get() = t("نسیه", "Credit", "پور")
  val other: String get() = t("دیگر", "Other", "نور")
  val returns: String get() = t("برگشت", "Returns", "بېرته ورکړه")
  val net: String get() = t("خالص", "Net", "خالص")
  val receipts: String get() = t("دریافتِ بدهی", "Debt payments", "د پور ترلاسه")
  val notInTotals: String get() = t("بیرون از جمع، مثلِ گزارشِ صندوق", "Not in totals, same as the register report", "له ټولګې بهر، لکه د صندوق راپور")
  val countsOnly: String get() = t("مبلغِ جمع‌ها را صاحب و مدیر می‌بینند", "Owners and managers see the totals", "ټولګې یوازې خاوند او مدیر ویني")

  // ── فهرست ──
  fun more(n: Any): String = t("نمایشِ ${asIs(n)} فاکتورِ دیگر", "Show $n more", "$n نور بلونه وښایه")
  fun shownOf(shown: Any, all: Any): String = t("${asIs(shown)} از ${asIs(all)} فاکتور", "$shown of $all invoices", "$shown له $all بلونو")
  val emptyToday: String get() = t("امروز هنوز فاکتوری ثبت نشده است", "No invoices yet today", "نن لا هېڅ بل نه دی ثبت شوی")
  val emptyPeriod: String get() = t("در این دوره فاکتوری نیست", "No invoices in this period", "په دې موده کې بل نشته")
  val table: String get() = t("جدولِ فاکتورها", "Invoice table", "د بلونو جدول")

  // ── فیلتر ──
  val filter: String get() = t("فیلتر", "Filter", "فلټر")
  val payment: String get() = t("پرداخت", "Payment", "ورکړه")
  val where: String get() = t("کجا", "Where", "چېرته")
  val inStore: String get() = t("در دکان", "In store", "په دوکان کې")
  val showCancelled: String get() = t("فقط لغوشده‌ها", "Cancelled only", "یوازې لغوه شوي")
  val clearFilters: String get() = t("پاک کردنِ فیلترها", "Clear filters", "فلټرونه پاک کړه")
  val filterNote: String get() = t(
    "جمعِ هر روز و ماه همیشه همهٔ فاکتورهای آن است؛ فیلتر فقط فهرست را کوتاه می‌کند.",
    "Each day and month always totals all its invoices; filters only shorten the list.",
    "د هرې ورځې او میاشتې ټولګه تل ټول بلونه دي؛ فلټر یوازې لېست لنډوي.",
  )

  // ── چاپ ──
  val print: String get() = t("چاپ", "Print", "چاپ")
  fun printTitle(period: Any): String = t("چاپِ ${asIs(period)}", "Print $period", "$period چاپ")
  val summary: String get() = t("خلاصه", "Summary", "لنډیز")
  val summaryHint: String get() = t(
    "جمع به تفکیکِ روشِ پرداخت، شمارِ فاکتور، برگشت و خالص",
    "Totals by payment method, invoice count, returns and net",
    "د ورکړې د لارې په توګه ټولګه، د بلونو شمېر، بېرته ورکړه او خالص",
  )
  val allInvoices: String get() = t("همهٔ فاکتورها", "All invoices", "ټول بلونه")
  val allInvoicesHint: String get() = t(
    "هر فاکتور با اقلامش، پشتِ سرِ هم؛ لغوشده‌ها نشان‌دار و بیرون از جمع",
    "Every invoice with its items, one after another; cancelled ones marked and left out of totals",
    "هر بل له توکو سره، یو په بل پسې؛ لغوه شوي نښه شوي او له ټولګې بهر",
  )
  fun paperLine(invoices: Any, cm: Any): String =
    t("${asIs(invoices)} فاکتور · حدودِ ${asIs(cm)} سانتی‌متر کاغذ", "$invoices invoices · about $cm cm of paper", "$invoices بلونه · شاوخوا $cm سانتي متره کاغذ")
  fun printerLine(name: Any, mm: Any): String = t("چاپگر: ${asIs(name)} · ${asIs(mm)} میلی‌متر", "Printer: $name · $mm mm", "چاپګر: $name · $mm ملي متره")
  val longPaper: String get() = t(
    "بیش از سه متر کاغذ می‌برد؛ پیش از چاپ رولِ کاغذ را ببینید.",
    "This needs more than three metres of paper; check the roll first.",
    "له درې مترو ډېر کاغذ غواړي؛ مخکې له چاپه د کاغذ رول وګورئ.",
  )
  val noPrinter: String get() = t(
    "چاپگرِ فاکتور ثبت نشده است. در «دستگاه‌ها» یکی بیفزایید.",
    "No receipt printer is set up. Add one in Devices.",
    "د بل چاپګر نه دی ثبت شوی. په «وسایل» کې یو ور زیات کړئ.",
  )
  fun printIgnoresFilter(n: Any): String = t(
    "فیلترِ فهرست در چاپ نیست: همهٔ ${asIs(n)} فاکتورِ این دوره چاپ می‌شود",
    "List filters don't apply: all $n ${pl(n, "invoice", "invoices")} of this period are printed",
    "د لېست فلټر په چاپ نه لګېږي: د دې مودې ټول $n بلونه چاپېږي",
  )
  val printSummary: String get() = t("چاپِ خلاصه", "Print summary", "لنډیز چاپ کړه")
  fun printInvoices(n: Any): String = t("چاپِ ${asIs(n)} فاکتور", "Print $n ${pl(n, "invoice", "invoices")}", "$n بلونه چاپ کړه")
  fun printing(done: Any, total: Any): String = t("در حالِ چاپ: ${asIs(done)} از ${asIs(total)} تکه", "Printing: $done of $total parts", "چاپ کېږي: $done له $total برخو")
  val waitingPrinter: String get() = t(
    "منتظرِ چاپگر — در صفِ چاپ می‌ماند و دوباره تلاش می‌شود",
    "Waiting for the printer — it stays in the print queue and retries",
    "چاپګر ته انتظار — د چاپ په کتار کې پاتې کېږي او بیا هڅه کېږي",
  )
  val cancelPrint: String get() = t("لغوِ چاپ", "Cancel printing", "چاپ لغوه کړه")
  val cancelNote: String get() = t(
    "تکه‌ای که همین حالا چاپ می‌شود تمام می‌شود؛ بقیه چاپ نمی‌شود.",
    "The part printing right now finishes; the rest is not printed.",
    "هغه برخه چې اوس چاپېږي بشپړېږي؛ پاتې نه چاپېږي.",
  )
  val printed: String get() = t("چاپ شد", "Printed", "چاپ شو")
  fun printCancelled(done: Any, total: Any): String =
    t("چاپ لغو شد — ${asIs(done)} از ${asIs(total)} تکه چاپ شد", "Printing cancelled — $done of $total parts printed", "چاپ لغوه شو — $done له $total برخو چاپ شوې")
  fun printFailed(reason: Any): String = t("چاپ نشد: ${asIs(reason)}", "Not printed: $reason", "چاپ نه شو: $reason")
  val busy: String get() = t("چاپِ دیگری از تاریخچه در جریان است", "Another history print is running", "د تاریخچې بل چاپ روان دی")

  // ── کاغذ ──
  val slipSummary: String get() = t("خلاصهٔ فروش", "Sales summary", "د پلور لنډیز")
  val slipInvoices: String get() = t("فاکتورهای فروش", "Sales invoices", "د پلور بلونه")
  fun slipPrintedAt(time: Any): String = t("چاپ: ${asIs(time)}", "Printed: $time", "چاپ: $time")
  val invoiceCount: String get() = t("شمارِ فاکتور", "Invoices", "د بلونو شمېر")
  val returnCount: String get() = t("شمارِ برگشت", "Returns", "د بېرته ورکړې شمېر")
  val salesTotal: String get() = t("جمعِ فروش", "Total sales", "ټول پلور")
  val onlineOrders: String get() = t("سفارشِ اینترنتی (پولِ دخل نیست)", "Online orders (not drawer money)", "آنلاین فرمایشونه (د دخل پیسې نه)")
  val cancelledInvoices: String get() = t("لغوشده (در جمع نیست)", "Cancelled (not in totals)", "لغوه شوي (په ټولګه کې نه)")
  val cancelledMark: String get() = t("لغوشده — در جمع نیست", "Cancelled — not in totals", "لغوه شوی — په ټولګه کې نه")
  val onlineMark: String get() = t("سفارشِ اینترنتی — در جمعِ دخل نیست", "Online order — not in drawer totals", "آنلاین فرمایش — د دخل په ټولګه کې نه")
  val total: String get() = t("جمع", "Total", "ټول")
  val endOfList: String get() = t("پایانِ فهرست", "End of list", "د لېست پای")
}
