package ir.vil3ntec.tohid.core.i18n

/**
 *  صندوقِ شیفت (`ShiftV2` — `ShiftPanelV2`، پشتِ پرچمِ `ShopPolicy.payV2`): باز کردن، ورود/خروجِ نقد،
 *  کشو بی فروش، گزارشِ X/Z و بستن با شمارشِ اسکناس.
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ دستی هم ویرایش‌پذیر است. قاعده‌ها سرِ `I18n.kt`.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 *  ⛔ یادداشتِ سابقهٔ صندوق (`notes`) و «تأیید» ِ ذخیره‌شده داده‌اند و ترجمه نمی‌شوند.
 */
@Suppress("unused")
object StrShift {

  // کارتِ صندوق
  val registerClosed: String get() = t("صندوق بسته است", "The register is closed", "صندوق بند دی")
  fun registerOpenSince(at: Any): String = t("صندوق باز — از $at", "Register open — since $at", "صندوق خلاص — له $at راهیسې")
  fun expectedInDrawer(a: Any): String = t("نقدِ مورد انتظار در دخل: $a", "Expected cash in the drawer: $a", "په دراز کې د تمې وړ نغدې: $a")
  val blindNote: String get() = t(
    "شمارشِ کور: مبلغِ مورد انتظار پس از بستن به مدیر نشان داده می‌شود",
    "Blind count: the expected amount is shown to a manager after closing",
    "پټ شمېرل: د تمې وړ مبلغ له بندولو وروسته مدیر ته ښودل کېږي",
  )
  fun skippedOuts(n: Any): String = t(
    "$n خروجِ دفتر بیش از نقدِ دخل — بررسیِ مدیر",
    "$n ledger ${pl(n, "withdrawal exceeds", "withdrawals exceed")} the cash in the drawer — manager review",
    "$n د دفتر ${pl(n, "ایستنه", "ایستنې")} د دراز له نغدو زیاتې — د مدیر کتنه",
  )
  val history: String get() = t("سابقه", "History", "تاریخچه")
  val cashInOut: String get() = t("ورود/خروجِ نقد", "Cash in/out", "د نغدو داخلول/ایستل")
  val noSale: String get() = t("کشو بی فروش", "No-sale drawer", "بې پلوره دراز")
  val reportFailed: String get() = t("گزارش ساخته نشد", "The report couldn't be built", "راپور جوړ نه شو")
  val xReport: String get() = t("گزارش X", "X report", "X راپور")
  val zReport: String get() = t("گزارش Z", "Z report", "Z راپور")
  val closeRegister: String get() = t("بستن صندوق", "Close register", "صندوق بندول")
  val openRegister: String get() = t("باز کردن", "Open", "پرانیستل")
  val saveFailed: String get() = t("ذخیره نشد", "Not saved", "خوندي نه شو")

  // چاپِ گزارش (PAY-15)
  val noReceiptPrinter: String get() = t(
    "چاپگرِ فاکتور تعریف نشده — تنظیمات › دستگاه‌ها",
    "No receipt printer is set up — Settings › Devices",
    "د بل پرنټر نه دی ټاکل شوی — امستنې › وسایل",
  )
  fun reportNotPrinted(why: Any): String = t("گزارش کشیده نشد: $why", "The report couldn't be laid out: $why", "راپور جوړ نه شو: $why")
  val reportPrinted: String get() = t("گزارش چاپ شد", "Report printed", "راپور چاپ شو")
  val print: String get() = t("چاپ", "Print", "چاپ")

  // باز کردن
  val openRegisterTitle: String get() = t("باز کردنِ صندوق", "Open the register", "د صندوق پرانیستل")
  val openingCash: String get() = t("پولِ اولِ صندوق", "Opening cash", "د صندوق لومړنۍ پیسې")
  val registerOpened: String get() = t("صندوق باز شد", "Register opened", "صندوق پرانیستل شو")
  val openIt: String get() = t("باز کن", "Open", "یې پرانیزئ")

  // ورود/خروجِ نقد و کشو بی فروش
  val noSaleTitle: String get() = t("باز کردنِ کشو بی فروش", "Open the drawer without a sale", "بې پلوره د دراز پرانیستل")
  val outOfDrawer: String get() = t("خروج از دخل", "Out of the drawer", "له دراز ایستل")
  val intoDrawer: String get() = t("ورود به دخل", "Into the drawer", "دراز ته داخلول")
  val reason: String get() = t("دلیل", "Reason", "لامل")
  val moveDeposit: String get() = t("واریز به دخل", "Deposit to the drawer", "دراز ته جمع")
  val moveOwnerWithdrawal: String get() = t("برداشتِ مالک", "Owner withdrawal", "د مالک ایستنه")
  val movePettyExpense: String get() = t("پرداختِ کوچک", "Petty expense", "وړوکی لګښت")
  val moveOther: String get() = t("دیگر", "Other", "نور")
  val amountAfn: String get() = t("مبلغ (افغانی)", "Amount (AFN)", "مبلغ (افغانۍ)")
  val noSaleReason: String get() = t("دلیلِ باز کردنِ کشو", "Reason for opening the drawer", "د دراز د پرانیستلو لامل")
  val noteForOther: String get() = t("توضیح (برای «دیگر» لازم)", "Note (required for “Other”)", "توضیح (د «نور» لپاره اړین)")
  val noSaleNeedsApproval: String get() = t(
    "باز کردنِ کشو بی فروش تأییدِ صاحب یا مدیر می‌خواهد؛ با حسابِ صاحب یا مدیر وارد شوید.",
    "Opening the drawer without a sale needs the owner's or a manager's approval; sign in with an owner or manager account.",
    "بې پلوره د دراز پرانیستل د مالک یا مدیر تایید غواړي؛ د مالک یا مدیر په حساب ننوځئ.",
  )
  fun noSaleApprover(name: Any): String = t(
    "تأییدکننده: $name — کشوِ برقی به چاپگرِ پیش‌فرض وصل نیست؛ این‌جا فقط با دلیل و تأیید ثبت می‌شود.",
    "Approver: $name — no electric drawer is connected to the default printer; this only records it with a reason and approval.",
    "تاییدوونکی: $name — برېښنايي دراز له اصلي پرنټر سره نه دی نښلول شوی؛ دلته یوازې له لامل او تایید سره ثبتېږي.",
  )
  fun noSaleApproverDrawer(name: Any, printer: Any): String = t(
    "تأییدکننده: $name — کشوی چاپگرِ «$printer» باز می‌شود و با دلیل در سابقهٔ همین صندوق می‌ماند.",
    "Approver: $name — the drawer of printer “$printer” opens and is kept with the reason in this register's history.",
    "تاییدوونکی: $name — د «$printer» پرنټر دراز پرانیستل کېږي او له لامل سره د همدې صندوق په تاریخچه کې پاتې کېږي.",
  )
  val thisAccount: String get() = t("همین حساب", "this account", "همدا حساب")
  val noSaleRecorded: String get() = t("باز کردنِ کشو ثبت شد", "Drawer opening recorded", "د دراز پرانیستل ثبت شول")
  val recorded: String get() = t("ثبت شد", "Recorded", "ثبت شو")
  val record: String get() = t("ثبت", "Record", "ثبت")

  // بستن با شمارشِ اسکناس
  val closeCountTitle: String get() = t("بستنِ صندوق — شمارشِ اسکناس", "Close the register — count the banknotes", "د صندوق بندول — د نوټونو شمېرل")
  val blindCountNote: String get() = t(
    "شمارشِ کور: اسکناس‌ها را بشمارید؛ مبلغِ مورد انتظار پس از بستن به مدیر نشان داده می‌شود.",
    "Blind count: count the banknotes; the expected amount is shown to a manager after closing.",
    "پټ شمېرل: نوټونه وشمېرئ؛ د تمې وړ مبلغ له بندولو وروسته مدیر ته ښودل کېږي.",
  )
  fun expectedCash(a: Any): String = t("نقدِ مورد انتظار: $a", "Expected cash: $a", "د تمې وړ نغدې: $a")
  val countedCash: String get() = t("نقدِ شمرده", "Cash counted", "شمېرل شوې نغدې")
  val methodCountsNote: String get() = t(
    "شمارشِ هر روش — جمعِ رسیدهای دستگاهِ کارت یا کیف پول؛ خالی یعنی «شمرده نشد»",
    "Count per method — the total of the card terminal or wallet receipts; empty means “not counted”",
    "د هرې لارې شمېرل — د کارت ماشین یا بټوې د رسیدونو ټولګه؛ خالي یعنې «ونه شمېرل شو»",
  )
  fun expectedSuffix(a: Any): String = t(" — مورد انتظار $a", " — expected $a", " — د تمې وړ $a")
  val noteForManager: String get() = t("یادداشت برای مدیر (اختیاری)", "Note for the manager (optional)", "مدیر ته یادښت (اختیاري)")
  fun badMethodCount(m: Any): String = t("شمرده‌شدهٔ «$m» درست نیست", "The count for “$m” isn't valid", "د «$m» شمېرل سم نه دي")
  val varianceReasonLabel: String get() = t("دلیلِ اختلاف (بالای ۵۰ افغانی لازم است)", "Reason for the difference (required above 50 AFN)", "د توپیر لامل (له 50 افغانیو پورته اړین دی)")
  val varianceReasonRequired: String get() = t("اختلافِ شمارش از آستانهٔ ۵۰ افغانی بیشتر است؛ دلیل بنویسید", "The count is off by more than 50 AFN; write a reason", "د شمېرلو توپیر له 50 افغانیو زیات دی؛ لامل ولیکئ")
  val registerClosedNow: String get() = t("صندوق بسته شد", "Register closed", "صندوق بند شو")
  val closeIt: String get() = t("ببند", "Close", "یې بند کړئ")

  // گزارشِ X / Z و سابقه
  val zReportTitle: String get() = t("گزارش Z (پایانِ شیفت)", "Z report (end of shift)", "Z راپور (د شیفت پای)")
  val xReportTitle: String get() = t("گزارش X (میانِ شیفت)", "X report (mid-shift)", "X راپور (د شیفت په منځ کې)")
  val registerHistory: String get() = t("سابقهٔ صندوق", "Register history", "د صندوق تاریخچه")
  val noShiftsYet: String get() = t("هنوز صندوقی باز نشده.", "No register has been opened yet.", "لا تر اوسه هېڅ صندوق نه دی پرانیستل شوی.")
  val stillOpen: String get() = t("هنوز باز است", "Still open", "لا خلاص دی")
  val needsReview: String get() = t("اختلاف — نیازمند بررسیِ مدیر", "Difference — needs manager review", "توپیر — د مدیر کتنې ته اړتیا لري")
  val opened: String get() = t("باز شد", "Opened", "پرانیستل شو")
  val closed: String get() = t("بسته شد", "Closed", "بند شو")
  val reportTime: String get() = t("زمانِ گزارش", "Report time", "د راپور وخت")
  val user: String get() = t("کاربر", "User", "کارن")
  val openingFloat: String get() = t("اولِ صندوق", "Opening float", "د صندوق پیل")
  fun methodSales(m: Any): String = t("$m — فروش", "$m — sales", "$m — پلور")
  fun methodReceipts(m: Any): String = t("$m — رسیدِ قرض‌داران", "$m — debtor payments", "$m — د پوروړو ورکړې")
  fun methodRefunds(m: Any): String = t("$m — مرجوعی", "$m — returns", "$m — بېرته ورکړې")
  fun times(n: Any): String = t("$n بار", "$n ${pl(n, "time", "times")}", "$n ځله")
  fun countedOf(m: Any): String = t("شمرده — $m", "Counted — $m", "شمېرل شوي — $m")
  //  نمای کور (`ShiftSlip.forViewer`): فقط شمار، بی روش
  val sales: String get() = t("فروش", "Sales", "پلور")
  val receipts: String get() = t("رسیدِ قرض‌داران", "Debtor payments", "د پوروړو ورکړې")
  val refunds: String get() = t("مرجوعی", "Returns", "بېرته ورکړې")
  fun expectedOf(m: Any): String = t("مورد انتظار — $m", "Expected — $m", "د تمې وړ — $m")
  fun varianceOf(m: Any): String = t("اختلاف — $m", "Difference — $m", "توپیر — $m")
  val notCounted: String get() = t("شمرده نشد", "Not counted", "ونه شمېرل شو")
  val noDifference: String get() = t("بی اختلاف", "No difference", "بې توپیره")
  fun over(a: Any): String = t("اضافه $a", "Over $a", "زیات $a")
  fun shortBy(a: Any): String = t("کسری $a", "Short $a", "کم $a")
  val varianceReason: String get() = t("دلیلِ اختلاف", "Reason for the difference", "د توپیر لامل")
  val notAutoFixed: String get() = t(
    "اختلاف خودکار اصلاح نمی‌شود — برای بررسیِ مدیر ثبت شد",
    "Differences aren't corrected automatically — recorded for manager review",
    "توپیر په خپله نه سمېږي — د مدیر کتنې ته ثبت شو",
  )
}
