package ir.vil3ntec.tohid.core.i18n

/**
 *  گاوصندوق (PAY-18) در «تاریخچهٔ فروش» ِ گوشی و کامپیوتر (`SafePanel`) و تنظیماتِ همین دستگاه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/safe/SafeCore` (commonMain) و `data/SafeBook`. یادداشتِ سابقهٔ صندوق داده است و
 *  فارسی می‌ماند (`SafeBook.transfer`). ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrSafe {
  val settingsTitle: String get() = t("گاوصندوق", "Safe", "تجوري")
  val settingsNote: String get() = t(
    "کارتِ «گاوصندوق» در تاریخچهٔ فروش: شمارش، ورود و خروج با دلیل، تحویل به بانک با شمارهٔ کیسه، و انتقال میانِ دخل و گاوصندوق. هر ردیف فقط افزوده می‌شود و اختلافِ شمارش هرگز خودکار یکی نمی‌شود.",
    "A \"Safe\" card in sales history: counts, money in and out with a reason, bank drops with a bag number, and transfers between the drawer and the safe. Rows are only ever added, and a count difference is never silently evened out.",
    "د پلور په تاریخچه کې د «تجوري» کارت: شمېرنه، له دلیل سره راتګ او وتنه، بانک ته سپارل د کڅوړې له شمېرې سره، او د دخل او تجوري ترمنځ لېږد. هر کرښه یوازې زیاتېږي او د شمېرنې توپیر هېڅکله پخپله نه برابرېږي.",
  )
  val title: String get() = t("گاوصندوق", "Safe", "تجوري")
  fun balance(amount: Any): String = t("مانده: $amount", "Balance: $amount", "پاتې: $amount")
  val notCounted: String get() = t("هنوز شمرده نشده", "Not counted yet", "لا نه دی شمېرل شوی")
  fun lastCount(time: Any): String = t("آخرین شمارش: $time", "Last count: $time", "وروستۍ شمېرنه: $time")
  val negative: String get() = t("مانده زیرِ صفر — نیازمندِ شمارش", "Balance below zero — needs a count", "پاتې له صفر لاندې — شمېرنې ته اړتیا")
  fun needsReview(n: Any): String = t("$n شمارش نیازمندِ بررسی", "$n count(s) need review", "$n شمېرنې کتنې ته اړتیا لري")
  fun invalid(n: Any): String = t("$n ردیفِ نادرست کنار رفت", "$n invalid row(s) set aside", "$n ناسمې کرښې یوې خوا شوې")
  val transferNeedsShift: String get() = t("انتقال با دخل فقط وقتی صندوق باز است", "Drawer transfers need an open register", "له دخل سره لېږد یوازې کله چې صندوق پرانیستی وي")
  val staffNote: String get() = t("شما فقط از دخل به گاوصندوق می‌گذارید", "You can only move cash from the drawer into the safe", "تاسو یوازې له دخل څخه تجوري ته اچولی شئ")

  // دکمه‌ها و نوع‌ها
  val history: String get() = t("تاریخچه", "History", "تاریخچه")
  val count: String get() = t("شمارش", "Count", "شمېرنه")
  val moneyIn: String get() = t("ورود", "Money in", "راتګ")
  val moneyOut: String get() = t("خروج", "Money out", "وتنه")
  val toBank: String get() = t("تحویل به بانک", "Bank drop", "بانک ته سپارل")
  val fromDrawer: String get() = t("از دخل به گاوصندوق", "Drawer to safe", "له دخل څخه تجوري ته")
  val toDrawer: String get() = t("از گاوصندوق به دخل", "Safe to drawer", "له تجوري څخه دخل ته")
  val kindCount: String get() = t("شمارش", "Count", "شمېرنه")
  val kindIn: String get() = t("ورود", "In", "راتګ")
  val kindOut: String get() = t("خروج", "Out", "وتنه")
  val kindFromDrawer: String get() = t("از دخل", "From drawer", "له دخل")
  val kindToDrawer: String get() = t("به دخل", "To drawer", "دخل ته")
  val kindToBank: String get() = t("به بانک", "To bank", "بانک ته")

  // دلیل‌ها (همان واژه‌های `SafeCore.REASONS`)
  val reasonDeposit: String get() = t("واریز", "Deposit", "زېرمه")
  val reasonOwnerWithdrawal: String get() = t("برداشتِ مالک", "Owner withdrawal", "د مالک اخیستل")
  val reasonPettyExpense: String get() = t("پرداختِ کوچک", "Petty expense", "وړوکی لګښت")
  val reasonOther: String get() = t("دیگر", "Other", "نور")

  // پنجره
  val countTitle: String get() = t("شمارشِ گاوصندوق", "Count the safe", "د تجوري شمېرنه")
  val amount: String get() = t("مبلغ (افغانی)", "Amount (AFN)", "اندازه (افغانۍ)")
  val counted: String get() = t("مبلغِ شمرده (افغانی)", "Counted amount (AFN)", "شمېرل شوې اندازه (افغانۍ)")
  fun bookBalance(amount: Any): String = t("ماندهٔ دفتر: $amount", "Book balance: $amount", "د دفتر پاتې: $amount")
  fun over(amount: Any): String = t("اضافه: $amount", "Over: $amount", "زیات: $amount")
  fun short(amount: Any): String = t("کسری: $amount", "Short: $amount", "کمښت: $amount")
  val reason: String get() = t("دلیل", "Reason", "دلیل")
  val varianceReason: String get() = t("دلیلِ اختلاف (بیش از ۵۰ افغانی لازم)", "Reason for the difference (needed above 50 AFN)", "د توپیر دلیل (له ۵۰ افغانیو پورته اړین)")
  val noteForOther: String get() = t("یادداشت (برای «دیگر» لازم)", "Note (needed for \"Other\")", "یادښت (د «نور» لپاره اړین)")
  val bagNo: String get() = t("شمارهٔ کیسه", "Bag number", "د کڅوړې شمېره")
  val bankNote: String get() = t("بانک یا یادداشت (اختیاری)", "Bank or note (optional)", "بانک یا یادښت (اختیاري)")
  val transferNote: String get() = t("همین مبلغ هم در سابقهٔ صندوق می‌آید.", "The same amount is also recorded in the register history.", "همدا اندازه د صندوق په تاریخچه کې هم ثبتېږي.")
  val record: String get() = t("ثبت", "Record", "ثبت")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  val close: String get() = t("بستن", "Close", "بندول")

  // تاریخچه
  val historyTitle: String get() = t("تاریخچهٔ گاوصندوق", "Safe history", "د تجوري تاریخچه")
  val empty: String get() = t("هنوز ردیفی نیست", "No entries yet", "لا کومه کرښه نشته")
  fun after(amount: Any): String = t("مانده پس از آن: $amount", "Balance after: $amount", "وروسته پاتې: $amount")
  fun bag(no: Any): String = t("کیسه: $no", "Bag: $no", "کڅوړه: $no")
  val startCount: String get() = t("شمارشِ آغاز", "Opening count", "پیلنۍ شمېرنه")
  val late: String get() = t("ردیفی دیر رسید — نیازمندِ بررسی", "A row arrived late — needs review", "یوه کرښه ناوخته راغله — کتنې ته اړتیا")
  fun more(n: Any): String = t("و $n ردیفِ کهنه‌تر", "and $n older row(s)", "او $n زړې کرښې")

  // نتیجه
  val saved: String get() = t("ثبت شد", "Recorded", "ثبت شو")
  val already: String get() = t("همین عملیات پیش‌تر ثبت شده بود", "This was already recorded", "دا مخکې ثبت شوی و")
  val notSaved: String get() = t("ثبت نشد، دوباره امتحان کنید", "Not saved, try again", "ثبت نه شو، بیا هڅه وکړئ")
  val amountRequired: String get() = t("مبلغ را بنویسید", "Enter the amount", "اندازه ولیکئ")

  // خطاها (کدهای `SafeCore.Code` و صندوق)
  fun varianceNeedsReason(over: Boolean, amount: Any, threshold: Any): String = if (over) t(
    "اختلافِ شمارش اضافه $amount است — بیش از $threshold دلیل می‌خواهد",
    "The count is over by $amount — more than $threshold needs a reason",
    "شمېرنه $amount زیاته ده — له $threshold زیات دلیل غواړي",
  ) else t(
    "اختلافِ شمارش کسری $amount است — بیش از $threshold دلیل می‌خواهد",
    "The count is short by $amount — more than $threshold needs a reason",
    "شمېرنه $amount کمه ده — له $threshold زیات دلیل غواړي",
  )
  val reasonRequired: String get() = t("دلیل را بنویسید", "Enter a reason", "دلیل ولیکئ")
  val insufficientFunds: String get() = t("این مبلغ بیش از ماندهٔ گاوصندوق است", "This is more than the safe holds", "دا له تجوري له پاتې څخه زیات دی")
  val insufficientCash: String get() = t("این مبلغ بیش از نقدِ دخل است", "This is more than the cash in the drawer", "دا د دخل له نغدو زیات دی")
  val invalidAmount: String get() = t("مبلغ درست نیست", "The amount is not valid", "اندازه سمه نه ده")
  val noteRequired: String get() = t("برای «دیگر» یادداشت بنویسید", "Write a note for \"Other\"", "د «نور» لپاره یادښت ولیکئ")
  val bagRequired: String get() = t("شمارهٔ کیسه را بنویسید", "Enter the bag number", "د کڅوړې شمېره ولیکئ")
  val badReason: String get() = t("دلیل درست نیست", "The reason is not valid", "دلیل سم نه دی")
  val tooLong: String get() = t("متن بیش از اندازه بلند است", "The text is too long", "متن ډېر اوږد دی")
  val idConflict: String get() = t("این عملیات با محتوای دیگری ثبت شده بود", "This was recorded with different details", "دا له بل منځپانګې سره ثبت شوی و")
  val badTime: String get() = t("زمانِ دستگاه درست نیست", "The device time is wrong", "د وسیلې وخت سم نه دی")
  val shiftClosed: String get() = t("صندوق باز نیست؛ اول صندوق را باز کنید", "The register is closed; open it first", "صندوق تړلی دی؛ لومړی یې پرانیزئ")
}
