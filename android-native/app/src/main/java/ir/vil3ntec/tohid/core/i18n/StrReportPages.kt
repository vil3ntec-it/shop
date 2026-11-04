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

  // ریزِ ردیف (RPT-13: `ReportFreshModel.drill`/`dayDrill`/`debtDrill`/`fraudDrill`) — فقط پنجرهٔ ریز، نه پروندهٔ خروجی
  val colDay: String get() = t("روز", "Day", "ورځ")
  val colInvoice: String get() = t("فاکتور", "Invoice", "بل")
  val colQty: String get() = t("مقدار", "Quantity", "مقدار")
  val colSales: String get() = t("فروش", "Sales", "پلور")
  val colDoc: String get() = t("سند", "Document", "سند")
  val colLeft: String get() = t("مانده", "Balance", "پاتې")
  val colAge: String get() = t("سن (روز)", "Age (days)", "عمر (ورځې)")
  val colKind: String get() = t("نوع", "Type", "ډول")
  val colAmount: String get() = t("مبلغ", "Amount", "مبلغ")
  val colApprover: String get() = t("تأییدکننده", "Approver", "تاییدوونکی")
  val drillTotal: String get() = t("جمع", "Total", "ټول")
  val prepaid: String get() = t("پیش‌پرداخت", "Prepaid", "مخکې ورکړه")
  val unknownUser: String get() = t("نامعلوم", "Unknown", "نامعلوم")
  val yes: String get() = t("بله", "Yes", "هو")
  val no: String get() = t("نه", "No", "نه")
  fun drillInvoices(label: Any?, n: Any?): String = t(
    "ریزِ «${asIs(label)}» — ${asIs(n)} فاکتور", "Details of “$label” — $n ${pl(n ?: 0, "invoice", "invoices")}", "د «$label» جزئیات — $n بلونه",
  )
  fun drillOpenDocs(label: Any?, n: Any?): String = t(
    "ریزِ «${asIs(label)}» — ${asIs(n)} سندِ باز", "Details of “$label” — $n open ${pl(n ?: 0, "document", "documents")}", "د «$label» جزئیات — $n خلاص سندونه",
  )
  fun drillEvents(label: Any?, n: Any?): String = t(
    "ریزِ «${asIs(label)}» — ${asIs(n)} رویداد", "Details of “$label” — $n ${pl(n ?: 0, "event", "events")}", "د «$label» جزئیات — $n پېښې",
  )
  val kindDiscount: String get() = t("تخفیف", "Discount", "تخفیف")
  val kindApproved: String get() = t("فاکتورِ تأییدشده", "Approved invoice", "تایید شوی بل")
  val kindReturn: String get() = t("برگشتی", "Return", "بېرته راغلی")
  val kindVoid: String get() = t("لغو", "Void", "لغوه")
  val kindLineDelete: String get() = t("حذف ردیف", "Line deleted", "د کرښې لرې کول")
  val kindNoSale: String get() = t("باز کردن کشو بی فروش", "Drawer opened without a sale", "بې پلور د دخل پرانیستل")
  fun approvedWorkOf(user: Any?): String = t("تأییدِ کارِ «${asIs(user)}»", "Approved work of “$user”", "د «$user» د کار تایید")
  val docDebt: String get() = t("قرض", "Credit", "پور")
  val docPurchase: String get() = t("خرید", "Purchase", "پېرود")
  val dayDrillNote: String get() = t(
    "جمعِ «فروش» ِ این فاکتورها همان ستونِ «فروش» ِ همین روز است؛ برگشتی جداست.",
    "The “Sales” total of these invoices is this day's “Sales” column; returns are separate.",
    "د دې بلونو د «پلور» ټولټال د همدې ورځې د «پلور» ستون دی؛ بېرته راغلي جلا دي.",
  )
  val debtDrillNote: String get() = t(
    "سندهای باز به ترتیبِ «اولین قرض، اولین پرداخت»؛ جمع همان «مانده» ِ همین ردیف است.",
    "Open documents in “first debt, first paid” order; the total is this row's “Balance”.",
    "خلاص سندونه د «لومړی پور، لومړی تادیه» په ترتیب؛ ټولټال د همدې قطار «پاتې» دی.",
  )
  val fraudDrillNote: String get() = t(
    "شمارِ هر نوع همان ستونِ همین ردیف است؛ «تأییدِ کارِ …» کارِ دیگری است که این کاربر تأیید کرد.",
    "The count of each type is this row's column; “Approved work of …” is someone else's action that this user approved.",
    "د هر ډول شمېر د همدې قطار ستون دی؛ «د … د کار تایید» د بل چا کار دی چې دې کارن تایید کړ.",
  )
}
