package ir.vil3ntec.tohid.core.i18n

/**
 *  صفحه‌های داده‌ایِ **کامپیوتر** (`ui/desk/DeskData.kt`، `claude/e-desk-d3b`): تاریخچهٔ فروش و فاکتورها، مشتری‌ها و
 *  قرض‌داران، خرید و تأمین‌کننده، مصارف، رسیدها و گزارش‌ها. هر نامِ بخش، ستون و دکمه‌ای که پیش‌تر بود همان نوشتهٔ
 *  `Str*` ِ پیشین است؛ این‌جا فقط نوشته‌های تازهٔ کامپیوتر. دری از لایهٔ `DariText`.
 */
@Suppress("unused")
object StrDeskData {
  //  ── مشترک ──
  val colWhen: String get() = t("زمان", "When", "وخت")
  val versusPrev: String get() = t("نسبت به دورهٔ پیش", "vs. previous period", "د تېرې مودې په پرتله")
  fun shareOfSales(p: Any): String = t("${asIs(p)}٪ از فروش", "$p% of sales", "د پلور $p٪")
  val noPrevSales: String get() = t("دورهٔ پیش فروشی نداشت", "No sales in the previous period", "په تېره موده کې پلور نه و")
  fun results(n: Any): String = t("${asIs(n)} نتیجه در همهٔ دوره‌ها", "$n ${pl(n, "result", "results")} across all periods", "$n پایلې په ټولو مودو کې")
  fun rowsShown(n: Any): String = t("${asIs(n)} ردیف", "$n ${pl(n, "row", "rows")}", "$n کرښې")
  val loading: String get() = t("در حالِ آماده کردن…", "Preparing…", "چمتو کېږي…")

  //  ── تاریخچهٔ فروش و فاکتورها ──
  val kpiSales: String get() = t("فروش", "Sales", "پلور")
  val pickInvoice: String get() = t(
    "یک فاکتور را بزنید تا اقلام و کارهایش این‌جا باز شود.",
    "Select an invoice to see its items and actions here.",
    "یو بل وټاکئ چې توکي او کړنې یې دلته پرانیستل شي.",
  )
  fun invoiceTitle(no: Any): String = t("فاکتورِ ${asIs(no)}", "Invoice $no", "بل $no")
  val partlyReturned: String get() = t("برگشتِ بخشی", "Partly returned", "یوه برخه بېرته")
  val fullyReturned: String get() = t("همه برگشت شد", "Fully returned", "ټول بېرته شول")
  val printPeriod: String get() = t("چاپِ همین دوره", "Print this period", "د همدې مودې چاپ")
  val printList: String get() = t("چاپِ فهرست", "Print the list", "د لړلیک چاپ")
  val moreForInvoice: String get() = t("کارهای دیگرِ فاکتور", "More invoice actions", "د بل نورې کړنې")
  val returnsHead: String get() = t("رسیدهای برگشت", "Return receipts", "د بېرته راګرځولو رسیدونه")
  fun returnsCount(n: Any): String = t("${asIs(n)} بار برگشت", "$n ${pl(n, "return", "returns")}", "$n ځله بېرته")
  val drawerOpen: String get() = t("صندوق باز است", "Drawer open", "صندوق خلاص دی")

  //  ── مشتری‌ها و قرض‌داران ──
  val pickCustomer: String get() = t(
    "یک مشتری را بزنید تا صورت‌حساب و فاکتورهایش این‌جا باز شود.",
    "Select a customer to open their statement and invoices here.",
    "یو پیرودونکی وټاکئ چې صورت‌حساب او بلونه یې دلته پرانیستل شي.",
  )
  val show: String get() = t("نمایش", "Show", "ښودل")
  val sortLabel: String get() = t("ترتیب", "Sort", "ترتیب")

  //  ── خرید و تأمین‌کننده ──
  val pickSupplier: String get() = t(
    "یک تأمین‌کننده را بزنید تا صورت‌حساب و پرداخت‌هایش این‌جا باز شود.",
    "Select a supplier to open their statement and payments here.",
    "یو عرضه‌کوونکی وټاکئ چې صورت‌حساب او ورکړې یې دلته پرانیستل شي.",
  )

  //  ── مصارف و رسیدها ──
  val versusPrevMonth: String get() = t("نسبت به ماهِ پیش", "vs. last month", "د تېرې میاشتې په پرتله")
  val noPrevExpenses: String get() = t("ماهِ پیش مصرفی نبود", "No expenses last month", "تېره میاشت لګښت نه و")
  val rowsLabel: String get() = t("شمارِ ردیف‌ها", "Entries", "د کرښو شمېر")
  val colTitle: String get() = t("عنوان", "Title", "سرلیک")
  val thisMonthTotal: String get() = t("جمعِ این ماه", "This month", "د دې میاشتې ټول")

  //  ── گزارش‌ها ──
  val dayLabel: String get() = t("روز", "Day", "ورځ")
  val breakdown: String get() = t("تفکیک", "Breakdown", "وېش")
  val salesByHour: String get() = t("فروشِ ساعت‌به‌ساعت", "Sales by hour", "د ساعت په ساعت پلور")
  val salesByMonth: String get() = t("فروشِ ماهانه", "Monthly sales", "میاشتنی پلور")
}
