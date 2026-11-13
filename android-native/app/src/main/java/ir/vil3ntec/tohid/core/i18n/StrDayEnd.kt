package ir.vil3ntec.tohid.core.i18n

/**
 *  LOC-06 — زبانه‌های «پایانِ روز» (`DayEndModel`، RPT-04) و «ساعت و روز» (`HeatmapModel`، RPT-08) ِ گزارشِ تازه.
 *  تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳ (#۲۴): سرستون‌های خروجیِ اکسل **به زبانِ برنامه** — صفحه، اکسل، چاپ و PDF همان جدولِ
 *  `ReportTables` اند، پس هر چهار با همین جدول هم‌زبان‌اند. فارسی بایت‌به‌بایت همان متنِ پیشینِ دو مدل است، جز دو وضعیتِ «با دفتر
 *  می‌خواند/نمی‌خواند» که «برابر است/نیست» شد («نمی‌خواند» در دری نمی‌رود — `dari-text.test.js` ِ آینهٔ `DariCoverageTest`).
 *  هر ویژگی سه‌زبانه: `val x: String get() = t("…", "…", "…")`؛ قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrDayEnd {

  // ── «پایانِ روز» ─────────────────────────────────────────────
  val tab: String get() = t("پایان روز", "End of day", "د ورځې پای")
  val today: String get() = t("امروز", "Today", "نن")
  val yesterday: String get() = t("دیروز", "Yesterday", "پرون")
  val buildFailed: String get() = t(
    "گزارشِ پایانِ روز ساخته نشد: جمع از بازهٔ دقیقِ عدد بیرون است.",
    "The end-of-day report wasn't built: a total is outside the exact number range.",
    "د ورځې د پای راپور جوړ نه شو: ټولټال د دقیقو شمېرو له حد څخه وتلی دی.",
  )
  val statusEmpty: String get() = t("این روز شیفت و فروشی ندارد", "No shifts or sales on this day", "دا ورځ شفټ او پلور نه لري")
  val statusOverlap: String get() = t(
    "شیفت‌های هم‌زمان؛ جمعِ شیفت‌ها فروش را دو بار شمرده است",
    "Overlapping shifts; the shift totals count sales twice",
    "یو وخت شفټونه؛ د شفټونو ټولټال پلور دوه ځله شمېرلی دی",
  )
  val statusMismatch: String get() = t(
    "با دفتر برابر نیست: پولِ بیرون از شیفت‌ها",
    "Doesn't match the ledger: money outside the shifts",
    "له دفتر سره سمون نه خوري: له شفټونو بهر پیسې",
  )
  fun statusOpen(n: Int): String = t(
    "${asIs(n)} شیفت هنوز باز است (X)",
    "$n ${pl(n, "shift is", "shifts are")} still open (X)",
    "$n شفټ لا خلاص دی (X)",
  )
  val statusReview: String get() = t(
    "بسته، با اختلاف یا رویدادِ نیازمندِ بررسی",
    "Closed, with a variance or an event to review",
    "تړلې، له توپیر یا د کتنې وړ پېښې سره",
  )
  val statusClosed: String get() = t(
    "روز بسته است و با دفتر برابر است",
    "The day is closed and matches the ledger",
    "ورځ تړلې ده او له دفتر سره سمون خوري",
  )

  //  جدولِ روش‌ها
  val methodsTitle: String get() = t("پایان روز به تفکیک روش", "End of day by payment method", "د ورځې پای د تادیې د طریقې له مخې")
  val colMethod: String get() = t("روش", "Method", "طریقه")
  val colSales: String get() = t("فروش", "Sales", "پلور")
  val colRefunds: String get() = t("برگشتی", "Refunds", "بېرته ورکړې")
  val colReceipts: String get() = t("رسید", "Receipts", "رسیدونه")
  val colShiftsNet: String get() = t("خالص شیفت‌ها", "Shifts net", "د شفټونو خالص")
  val colExpected: String get() = t("مورد انتظار", "Expected", "تمه شوې")
  val colCounted: String get() = t("شمرده", "Counted", "شمېرل شوې")
  val colVariance: String get() = t("اختلاف", "Variance", "توپیر")
  val colLedgerNet: String get() = t("خالص دفتر", "Ledger net", "د دفتر خالص")
  val colOutside: String get() = t("بیرون از شیفت‌ها", "Outside shifts", "له شفټونو بهر")

  //  جدولِ شیفت‌ها
  val shiftsTitle: String get() = t("شیفت‌های روز", "Shifts of the day", "د ورځې شفټونه")
  val colShift: String get() = t("شیفت", "Shift", "شفټ")
  val colStatus: String get() = t("وضعیت", "Status", "حالت")
  val colFloat: String get() = t("اول صندوق", "Opening float", "د صندوق پیل")
  val colNet: String get() = t("خالص", "Net", "خالص")
  val colCashIn: String get() = t("ورود نقد", "Cash in", "نغدې راتګ")
  val colCashOut: String get() = t("خروج نقد", "Cash out", "نغدې وتنه")
  val colExpectedCash: String get() = t("نقد مورد انتظار", "Expected cash", "تمه شوې نغدې")
  val colCountedCash: String get() = t("نقد شمرده", "Counted cash", "شمېرل شوې نغدې")
  val colVarianceCash: String get() = t("اختلاف نقد", "Cash variance", "د نغدو توپیر")
  val colReview: String get() = t("نیازمند بررسی", "Needs review", "کتنې ته اړتیا")
  val shiftOpen: String get() = t("باز (X)", "Open (X)", "خلاص (X)")
  val shiftLegacy: String get() = t("بسته، صندوقِ پیشین", "Closed, previous till", "تړلې، پخوانی صندوق")
  val shiftClosed: String get() = t("بسته (Z)", "Closed (Z)", "تړلې (Z)")

  //  ریزِ تراکنش‌ها
  val kindRefund: String get() = t("برگشت", "Refund", "بېرته ورکړه")
  fun invoiceNo(no: String): String = t("فاکتور #${asIs(no)}", "Invoice #$no", "بل #$no")
  val invoice: String get() = t("فاکتور", "Invoice", "بل")
  fun refundOf(invoice: String): String = t("برگشتِ ${asIs(invoice)}", "Refund of $invoice", "د $invoice بېرته ورکړه")
  val debtReceived: String get() = t("دریافتِ قرض", "Debt payment received", "د پور ترلاسه کول")
  val giftCard: String get() = t("کارتِ هدیه", "Gift card", "ډالۍ کارت")
  val depositBack: String get() = t("پس دادنِ پیش‌پرداخت", "Deposit refund", "د مخکې ورکړې بېرته ورکول")
  fun detailsTitle(method: String): String = t("ریزِ تراکنش‌ها — ${asIs(method)}", "Transactions — $method", "راکړې ورکړې — $method")
  val colTime: String get() = t("ساعت", "Time", "وخت")
  val colKind: String get() = t("نوع", "Type", "ډول")
  val colDoc: String get() = t("سند", "Document", "سند")
  val colAmount: String get() = t("مبلغ", "Amount", "مبلغ")
  fun txCount(n: Int): String = t("${asIs(n)} تراکنش", "$n ${pl(n, "transaction", "transactions")}", "$n راکړې ورکړې")
  val total: String get() = t("جمع", "Total", "ټولټال")

  // ── «ساعت و روز» ─────────────────────────────────────────────
  val heatTab: String get() = t("ساعت و روز", "Hour & day", "ساعت او ورځ")
  val heatGridTitle: String get() = t("شمارِ فروش: روز × ساعت", "Sales count: day × hour", "د پلور شمېر: ورځ × ساعت")
  val colDay: String get() = t("روز", "Day", "ورځ")
  val colHour: String get() = t("ساعت", "Hour", "ساعت")
  val colSalesCount: String get() = t("شمارِ فروش", "Sales count", "د پلور شمېر")
  val colNetAmount: String get() = t("خالصِ مبلغ", "Net amount", "خالص مبلغ")
  val byHourTitle: String get() = t("به تفکیکِ ساعت", "By hour", "د ساعت له مخې")
  val byDayTitle: String get() = t("به تفکیکِ روزِ هفته", "By weekday", "د اونۍ د ورځې له مخې")
  val monday: String get() = t("دوشنبه", "Monday", "دوشنبه")
  val tuesday: String get() = t("سه‌شنبه", "Tuesday", "سه‌شنبه")
  val wednesday: String get() = t("چهارشنبه", "Wednesday", "چهارشنبه")
  val thursday: String get() = t("پنجشنبه", "Thursday", "پنجشنبه")
  val friday: String get() = t("جمعه", "Friday", "جمعه")
  val saturday: String get() = t("شنبه", "Saturday", "شنبه")
  val sunday: String get() = t("یکشنبه", "Sunday", "یکشنبه")
}
