package ir.vil3ntec.tohid.core.i18n

/**
 *  مرکزِ مدیریت (`Management`): عددهای اصلی، هشدارها، صندوق، فروشنده‌ها، دستگاه‌ها، کارمندان، اشتراک،
 *  سابقهٔ اخیر و میان‌برها.
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ دستی هم ویرایش‌پذیر است. قاعده‌ها سرِ `I18n.kt`.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 *  ⛔ نامِ دستگاه، کارمند، فروشنده، پلن و یادداشتِ سابقه داده‌اند و ترجمه نمی‌شوند.
 */
@Suppress("unused")
object StrAdmin {

  val subtitle: String get() = t("همهٔ دکان در یک نگاه — هر کارت به صفحهٔ کاملش می‌رود", "The whole shop at a glance — each card opens its full page", "ټول دوکان په یوه کتنه — هر کارت خپلې بشپړې پاڼې ته ځي")

  // عددهای اصلی
  val monthSales: String get() = t("فروشِ این ماه", "This month's sales", "د دې میاشتې پلور")
  val monthLoss: String get() = t("ضررِ خالصِ ماه", "Net loss this month", "د میاشتې خالص تاوان")
  val monthProfit: String get() = t("سودِ خالصِ ماه", "Net profit this month", "د میاشتې خالصه ګټه")
  val afterExpenses: String get() = t("پس از مصارف", "After expenses", "له لګښتونو وروسته")
  val stockValue: String get() = t("ارزشِ انبار", "Stock value", "د ګدام ارزښت")
  fun itemLines(n: Any): String = t("$n قلم کالا", "$n ${pl(n, "item", "items")}", "$n ${pl(n, "توکی", "توکي")}")
  val owedByDebtors: String get() = t("طلب از قرض‌داران", "Owed by debtors", "له پوروړو طلب")
  fun people(n: Any): String = t("$n نفر", "$n ${pl(n, "person", "people")}", "$n تنه")
  fun suppliers(n: Any): String = t("$n تأمین‌کننده", "$n ${pl(n, "supplier", "suppliers")}", "$n ${pl(n, "عرضه‌کوونکی", "عرضه‌کوونکي")}")
  val expectedInDrawer: String get() = t("نقدِ مورد انتظار در دخل", "Expected cash in the drawer", "په دراز کې د تمې وړ نغدې")
  val blindCount: String get() = t("شمارشِ کور", "Blind count", "پټ شمېرل")
  fun openSince(at: Any): String = t("باز از $at", "Open since $at", "له $at راهیسې خلاص")
  val openFromHistory: String get() = t("از «تاریخچهٔ فروش» باز کنید", "Open it from “Sales history”", "له «د پلور تاریخچه» یې پرانیزئ")
  val stockAlerts: String get() = t("هشدارِ انبار", "Stock alerts", "د ګدام خبرتیاوې")
  fun outAndLow(out: Any, low: Any): String = t("$out تمام‌شده · $low کم", "$out sold out · $low low", "$out خلاص · $low لږ")

  // هشدارها
  val lowStock: String get() = t("موجودیِ کم", "Low stock", "لږ موجودي")
  val expired: String get() = t("منقضی", "Expired", "نېټه تېره")
  val nearExpiry: String get() = t("نزدیک به انقضا", "Expiring soon", "ژر ختمېږي")
  val debtorsOwing: String get() = t("قرض‌دارِ بدهکار", "Debtors who owe", "پوروړي چې پور لري")

  // صندوق
  val register: String get() = t("صندوق", "Register", "صندوق")
  val registerAndInvoices: String get() = t("صندوق و فاکتورها", "Register & invoices", "صندوق او بلونه")
  val registerClosedNote: String get() = t("صندوق بسته است.", "The register is closed.", "صندوق بند دی.")
  val openingFloat: String get() = t("اول صندوق", "Opening float", "د صندوق پیل")
  val expectedCash: String get() = t("مورد انتظار (نقد)", "Expected (cash)", "د تمې وړ (نغد)")
  val cashSales: String get() = t("فروشِ نقدی", "Cash sales", "نغد پلور")
  val debtorReceipts: String get() = t("رسیدِ قرض‌داران", "Debtor payments", "د پوروړو ورکړې")
  val returnsAndExpenses: String get() = t("مرجوعی و مصارف", "Returns & expenses", "بېرته ورکړې او لګښتونه")
  val cashInAndOut: String get() = t("ورود و خروجِ نقد", "Cash in & out", "د نغدو داخلول او ایستل")
  val expected: String get() = t("مورد انتظار", "Expected", "د تمې وړ")

  // فروشنده‌ها، دستگاه‌ها، کارمندان، اشتراک
  val sellersThisMonth: String get() = t("فروشنده‌ها (این ماه)", "Sellers (this month)", "پلورونکي (دا میاشت)")
  val noSales: String get() = t("فروشی ثبت نشده.", "No sales recorded.", "هېڅ پلور نه دی ثبت شوی.")
  val accountDevices: String get() = t("دستگاه‌های حساب", "Account devices", "د حساب وسایل")
  val noAccountLocal: String get() = t("بی حساب کار می‌کنید — دفتر فقط روی همین دستگاه است.", "You're working without an account — the ledger is only on this device.", "بې حسابه کار کوئ — دفتر یوازې په همدې وسیله کې دی.")
  val serverUnreachable: String get() = t("سرور در دسترس نیست.", "The server isn't reachable.", "سرور ته لاسرسی نشته.")
  val revokedSuffix: String get() = t(" (بسته)", " (revoked)", " (بند)")
  val signInForStaff: String get() = t("برای کارمندان، وارد حساب شوید.", "Sign in to see your staff.", "د کارکوونکو لپاره حساب ته ننوځئ.")
  val noStaffYet: String get() = t("هنوز کارمندی نیست.", "No staff yet.", "لا تر اوسه کارکوونکی نشته.")
  val roleOwner: String get() = t("صاحب", "Owner", "مالک")
  val roleSeller: String get() = t("فروشنده", "Seller", "پلورونکی")
  val noAccount: String get() = t("بی حساب.", "No account.", "بې حسابه.")
  val plan: String get() = t("پلن", "Plan", "پلان")
  val suspended: String get() = t("معلق", "Suspended", "ځنډول شوی")
  val daysLeft: String get() = t("روزِ مانده", "Days left", "پاتې ورځې")

  // سابقه و میان‌بُرها
  val recentActivity: String get() = t("سابقهٔ اخیر", "Recent activity", "وروستۍ کړنې")
  val nothingRecorded: String get() = t("هنوز چیزی ثبت نشده.", "Nothing recorded yet.", "لا تر اوسه هېڅ نه دي ثبت شوي.")
  val settingsAndBackup: String get() = t("تنظیمات و پشتیبان", "Settings & backup", "امستنې او بیک‌اپ")
}
