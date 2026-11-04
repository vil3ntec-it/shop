package ir.vil3ntec.tohid.core.i18n

/**
 *  گزارشِ تازه (`ui/screens/ReportFresh.kt`): کلیدِ آزمایشی، خلاصهٔ فروش و سود، روشِ بها، طلب و بدهی، ضدتقلب، تشخیصِ ناهنجاری، جدول‌ها
 *  و «امروز و سنِ بدهی» ِ گوشی. این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند (LOC-08 دری، دورِ ۳). فارسی
 *  کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته. نامِ پرونده‌های خروجی در صفحه
 *  «// داده:» است. ستون‌های `DayEndModel` (هم صفحه و هم خروجی) منتظرِ تصمیمِ صاحب‌کارند و این‌جا نیستند. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrReportPages {
  // ── `ReportFresh.kt` ──

  // کلید و سرِ صفحه
  val trialThisDevice: String get() = t("آزمایشی · فقط روی همین دستگاه", "Experimental · this device only", "ازمایښتي · یوازې همدا وسیله")
  val sourceNote: String get() = t(
    "از هستهٔ مشترکِ گزارش (همان عددهای وب). سود تا تعیینِ روشِ بهای تمام‌شده تقریبی است.",
    "From the shared report core (the same numbers as the web). Profit is approximate until the costing method is decided.",
    "د راپور له ګډ هستې (هماغه شمېرې لکه وېب). ګټه تر هغه نږدې ده چې د تمامې بیې طریقه وټاکل شي.",
  )
  fun printKey(key: Any?): String = t("چاپ  ${asIs(key)}", "Print  $key", "چاپ  $key")
  fun excelKey(key: Any?): String = t("اکسل  ${asIs(key)}", "Excel  $key", "اکسل  $key")
  val noReport: String get() = t("گزارشی برای نمایش نیست.", "No report to show.", "د ښودلو لپاره راپور نشته.")
  val salesDrillNote: String get() = t(
    "جمعِ «فروش» ِ این فاکتورها همان ستونِ «فروش» ِ ردیف است؛ برگشتی جداست.",
    "The “Sales” total of these invoices is the row's “Sales” column; returns are separate.",
    "د دې بلونو د «پلور» ټولټال د قطار د «پلور» ستون دی؛ بېرته راغلي جلا دي.",
  )

  // خلاصه
  val avgTicket: String get() = t("میانگین فاکتور", "Average invoice", "منځنی بل")
  val cogs: String get() = t("بهای تمام‌شده", "Cost of goods", "د توکو تمامه بیه")
  val margin: String get() = t("حاشیه", "Margin", "حاشیه")
  val costMethod: String get() = t("روش بها", "Costing method", "د بیې طریقه")
  val profitApprox: String get() = t("سود تقریبی", "Approximate profit", "نږدې ګټه")
  val profitFinal: String get() = t("سود قطعی", "Final profit", "قطعي ګټه")
  fun noCostLines(n: Any?): String = t(
    "${asIs(n)} قلم بی بهای خرید است.",
    "$n ${pl(n ?: 0, "line has", "lines have")} no cost price.",
    "$n قلمونه پېر بیه نه لري.",
  )
  val untilToday: String get() = t("تا امروز", "Until today", "تر نن")
  val owingCustomers: String get() = t("مشتریانِ بدهکار", "Customers who owe", "پوروړي پېرودونکي")
  fun creditOver(days: Any?): String = t("قرضِ بیش از ${asIs(days)} روز", "Credit over $days days", "له $days ورځو زیات پور")
  val owedToSuppliers: String get() = t("بدهی به تأمین‌کنندگان", "Owed to suppliers", "عرضه‌کوونکو ته پور")
  val invoiceVoids: String get() = t("لغوِ فاکتور", "Invoice voids", "د بل لغوې")
  val needApproval: String get() = t("نیازمندِ تأیید", "Needed approval", "تایید ته اړتیا")

  // ناهنجاری و جدول
  val nothingToReview: String get() = t("موردی نیازمندِ بررسی نیست", "Nothing needs review", "د کتنې لپاره څه نشته")
  fun needReview(n: Any?): String = t(
    "${asIs(n)} مورد نیازمندِ بررسی",
    "$n ${pl(n ?: 0, "item needs", "items need")} review",
    "$n مواردو ته کتنه اړینه ده",
  )
  val anomalyNote: String get() = t(
    "تشخیصِ ناهنجاری (قاعده و آمار، روی دفترِ همین دستگاه): صندوقدار، اختلافِ صندوق، کمِ موجودی، زیرِ بها، ساعت و روزهای بیرون از معمول. فقط نشان می‌دهد.",
    "Anomaly detection (rules and statistics, on this device's ledger): cashier, till difference, missing stock, below cost, unusual hours and days. Only shows.",
    "د غیرعادي حالتونو موندنه (قاعده او احصایه، د همدې وسیلې پر دفتر): صندوقدار، د دخل توپیر، کمه موجودي، تر بیې لاندې، غیرعادي ساعتونه او ورځې. یوازې ښيي.",
  )
  fun andMore(n: Any?): String = t("و ${asIs(n)} موردِ دیگر", "and $n more", "او $n نور")
  fun sortBy(column: Any?): String = t("مرتب‌سازی با ${asIs(column)}", "Sort by $column", "د $column له مخې ترتیب")
  fun detailOf(row: Any?): String = t("ریزِ ${asIs(row)}", "Details of $row", "د $row جزئیات")

  // امروز و سنِ بدهی
  val todayAging: String get() = t("امروز و سنِ بدهی", "Today and debt age", "نن او د پور عمر")
  fun dayRange(lo: Any?, hi: Any?): String = t("${asIs(lo)}–${asIs(hi)} روز", "$lo–$hi days", "$lo–$hi ورځې")
  fun daysPlus(lo: Any?): String = t("${asIs(lo)}+ روز", "$lo+ days", "$lo+ ورځې")
  val customerAging: String get() = t("سنِ بدهیِ مشتری‌ها", "Customers' debt age", "د پېرودونکو د پور عمر")
  val noCustomerOwes: String get() = t("هیچ مشتری‌ای قرض ندارد.", "No customer owes anything.", "هېڅ پېرودونکی پور نه لري.")
  val onTime: String get() = t("در مهلت", "On time", "په وخت")
  fun oldestDays(n: Any?): String = t("قدیمی‌ترین: ${asIs(n)} روز", "Oldest: $n ${pl(n ?: 0, "day", "days")}", "تر ټولو زوړ: $n ورځې")
}
