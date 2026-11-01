package ir.vil3ntec.tohid.core.i18n

/**
 *  نوشته‌های گروهِ G6 گزارش‌ها و آمار (analytics.md) — صاحبش همان گروه است.
 *  هر ویژگی سه‌زبانه: `val x: String get() = t("…", "…", "…")`؛ قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrReports {

  // گزارش‌ها: سربرگ، زبانه‌ها و بازه
  val print: String get() = t("چاپ گزارش", "Print report", "راپور چاپ کړئ")
  val exportCsv: String get() = t("خروجی اکسل (CSV)", "Export to Excel (CSV)", "اکسل ته وېستل (CSV)")
  val csvSaved: String get() = t("فایلِ گزارش ذخیره شد", "Report file saved", "د راپور فایل خوندي شو")
  val csvFailed: String get() = t("فایل ساخته نشد", "The file wasn't created", "فایل جوړ نه شو")
  val csvFailedBody: String get() = t("جای ذخیره را عوض کنید و دوباره بزنید.", "Pick another location and try again.", "بل ځای وټاکئ او بیا هڅه وکړئ.")
  val nothingToExport: String get() = t("در این بخش داده‌ای برای خروجی نیست", "There's nothing to export in this section", "په دې برخه کې د وېستلو لپاره معلومات نشته")
  val tabProfit: String get() = t("سود و فروش", "Profit & sales", "ګټه او پلور")
  val tabProducts: String get() = t("محصولات", "Products", "توکي")
  val tabDebtors: String get() = t("قرض‌داران", "Debtors", "پوروړي")
  val tabPurchases: String get() = t("خرید", "Purchases", "پېرود")
  val tabExpiry: String get() = t("انقضا", "Expiry", "د نېټې تېرېدل")
  val tabStock: String get() = t("گردش موجودی", "Stock movement", "د موجودۍ خوځښت")
  val rangeToday: String get() = t("امروز", "Today", "نن")
  val rangeWeek: String get() = t("۷ روز", "7 days", "۷ ورځې")
  val rangeMonth: String get() = t("این ماه", "This month", "دا میاشت")
  val rangePickMonth: String get() = t("ماه…", "Month…", "میاشت…")
  val rangePickYear: String get() = t("سال…", "Year…", "کال…")
  val rangeAll: String get() = t("از آغاز", "All time", "له پیله")
  fun rangeSpan(from: Any, to: Any): String = t("$from تا $to", "$from – $to", "له $from تر $to")
  val allDates: String get() = t("همهٔ تاریخ‌ها", "All dates", "ټولې نېټې")
  val rightNow: String get() = t("وضعیتِ همین حالا", "As of right now", "همدا اوس حالت")
  val allMovements: String get() = t("همهٔ حرکت‌ها از آغاز", "Every movement since the start", "له پیله ټول خوځښتونه")
  val pickMonth: String get() = t("انتخابِ ماه", "Choose a month", "میاشت وټاکئ")
  val pickYear: String get() = t("انتخابِ سال", "Choose a year", "کال وټاکئ")
  val previous: String get() = t("قبلی", "Previous", "مخکینی")
  val next: String get() = t("بعدی", "Next", "راتلونکی")
  val show: String get() = t("نمایش", "Show", "ښودل")
  fun yearLabel(y: Any): String = t("سال $y", "Year $y", "کال $y")
  val moneyLocked: String get() = t("عددهای مالی برای شما بسته است", "Money figures are closed to you", "مالي شمېرې تاسو ته تړلې دي")
  val moneyLockedBody: String get() = t("صاحبِ دکان می‌تواند این دسترسی را بدهد.", "The shop owner can grant this access.", "د دوکان خاوند کولای شي دا لاسرسی درکړي.")

  // سود و فروش
  val netProfit: String get() = t("سود خالص", "Net profit", "خالصه ګټه")
  val netLoss: String get() = t("ضرر خالص", "Net loss", "خالص تاوان")
  val statement: String get() = t("صورتِ سود و زیان", "Profit & loss statement", "د ګټې او تاوان صورت")
  val itemsTotal: String get() = t("جمع اقلام", "Items total", "د توکو ټولګه")
  val discount: String get() = t("تخفیف", "Discount", "تخفیف")
  val netSales: String get() = t("فروش خالص", "Net sales", "خالص پلور")
  val cogs: String get() = t("بهای تمام‌شدهٔ کالا", "Cost of goods", "د توکو لګښت")
  val returns: String get() = t("مرجوعی", "Returns", "بېرته راغلي")
  val grossProfit: String get() = t("سود ناخالص", "Gross profit", "ناخالصه ګټه")
  val grossLoss: String get() = t("ضرر ناخالص", "Gross loss", "ناخالص تاوان")
  val expenses: String get() = t("مصارف", "Expenses", "لګښتونه")
  val bySeller: String get() = t("فروش هر کاربر", "Sales by user", "د هر کارن پلور")
  val profitFootnote: String get() = t(
    "سود هر قلم از قیمت خرید ثبت‌شده روی همان فاکتور حساب می‌شود، نه قیمت امروز.",
    "Each line's profit uses the purchase price recorded on that invoice, not today's price.",
    "د هر توکي ګټه د همغه بل پر ثبت شوې پېر بیه شمېرل کېږي، نه د نن بیه.",
  )
  val noSalesInRange: String get() = t("در این بازه فروشی ثبت نشده", "No sales in this period", "په دې موده کې پلور نه دی ثبت شوی")
  val newSale: String get() = t("فروشِ تازه", "New sale", "نوی پلور")

  // محصولات
  val inventoryValue: String get() = t("ارزش موجودی (به بهای خرید)", "Stock value (at cost)", "د موجودۍ ارزښت (په پېر بیه)")
  val soldOut: String get() = t("تمام‌شده", "Sold out", "خلاص شوي")
  val lowStock: String get() = t("موجودی کم", "Low stock", "لږ موجودي")
  val topSelling: String get() = t("پرفروش‌ترین", "Best sellers", "تر ټولو ډېر پلورل شوي")
  val slowest: String get() = t("کم‌فروش‌ترین", "Slowest sellers", "تر ټولو لږ پلورل شوي")
  val mostProfitable: String get() = t("پرسودترین", "Most profitable", "تر ټولو ګټور")
  val leastProfitable: String get() = t("کم‌سودترین", "Least profitable", "تر ټولو لږ ګټور")
  fun sold(q: Any): String = t("$q فروخته‌شده", "$q sold", "$q پلورل شوي")
  val needsAttention: String get() = t("نیاز به توجه", "Needs attention", "پاملرنې ته اړتیا")
  val noData: String get() = t("داده‌ای موجود نیست", "No data yet", "معلومات نشته")
  val noProducts: String get() = t("هنوز کالایی ثبت نشده", "No items yet", "لا هېڅ توکی نه دی ثبت شوی")
  val addProduct: String get() = t("افزودنِ کالا", "Add an item", "توکی زیات کړئ")
  fun rank(n: Any): String = t("رتبهٔ $n", "Rank $n", "$n درجه")

  // قرض‌داران
  val receivable: String get() = t("جمع طلب از مشتریان", "Owed by customers", "له پېرودونکو ټول طلب")
  fun creditVsPaid(given: Any, paid: Any): String =
    t("فروش نسیه $given — پرداخت‌شده $paid", "On credit $given — paid $paid", "پور پلور $given — ورکړل شوي $paid")
  val settled: String get() = t("تسویه شده", "Settled", "تصفیه شوی")
  fun prepaid(a: Any): String = t("$a ؋ پیش‌پرداخت", "$a ؋ in credit", "$a ؋ مخکې ورکړل شوي")
  val noDebtors: String get() = t("قرض‌داری ثبت نشده", "No debtors yet", "لا هېڅ پوروړی نه دی ثبت شوی")
  val addDebtor: String get() = t("ثبتِ قرض‌دار", "Add a debtor", "پوروړی ثبت کړئ")

  // خرید
  val purchasesTotal: String get() = t("جمع خرید", "Total purchases", "د پېرود ټولګه")
  val supplierDebt: String get() = t("بدهی به تأمین‌کننده", "Owed to suppliers", "عرضه‌کوونکو ته پور")
  val bySupplier: String get() = t("سهمِ هر تأمین‌کننده", "Share by supplier", "د هر عرضه‌کوونکي برخه")
  val purchasesInRange: String get() = t("خرید در این بازه", "Purchases in this period", "په دې موده کې پېرود")
  val purchaseRows: String get() = t("خریدهای بازه", "Purchases", "پېرودونه")
  val noPurchases: String get() = t("در این بازه خریدی ثبت نشده", "No purchases in this period", "په دې موده کې پېرود نه دی ثبت شوی")
  val newPurchase: String get() = t("ثبتِ خرید", "Record a purchase", "پېرود ثبت کړئ")
  val unknownProduct: String get() = t("(محصول حذف‌شده)", "(deleted item)", "(ړنګ شوی توکی)")

  // انقضا
  val expired: String get() = t("منقضی", "Expired", "نېټه تېره")
  fun withinDays(n: Any): String = t("تا $n روز", "Within $n ${pl(n, "day", "days")}", "تر $n ورځو")
  fun laterThan(n: Any): String = t("بیش از $n روز", "More than $n ${pl(n, "day", "days")}", "له $n ورځو زیات")
  fun batch(b: Any): String = t("سری $b", "Batch $b", "لړۍ $b")
  fun daysLeft(n: Any): String = t("$n روز", "$n ${pl(n, "day", "days")}", "$n ${pl(n, "ورځ", "ورځې")}")
  val noLots: String get() = t("محموله‌ای با تاریخِ انقضا در انبار نمانده", "No batches with an expiry date in stock", "په ګدام کې د ختمېدو نېټې لرونکې کڅوړه نشته")
  val stockEntry: String get() = t("ورودِ کالا", "Receive stock", "توکي راوړل")

  // گردش موجودی
  fun flowLine(inQty: Any, outQty: Any): String = t("ورود $inQty · خروج $outQty", "In $inQty · out $outQty", "راغلي $inQty · وتلي $outQty")
  fun moves(n: Any): String = t("$n حرکت", "$n ${pl(n, "movement", "movements")}", "$n خوځښتونه")
  val noMovements: String get() = t("هنوز حرکتی ثبت نشده", "No stock movement yet", "لا هېڅ خوځښت نه دی ثبت شوی")

  // آمار (صفحهٔ دومِ خانه)
  val statsTitle: String get() = t("آمار", "Statistics", "شمېرنې")
  val periodDay: String get() = t("روز", "Day", "ورځ")
  val periodWeek: String get() = t("هفته", "Week", "اونۍ")
  val periodMonth: String get() = t("ماه", "Month", "میاشت")
  val periodYear: String get() = t("سال", "Year", "کال")
  val legendSelected: String get() = t("فروشِ بازهٔ انتخاب‌شده", "Selected period's sales", "د ټاکلې مودې پلور")
  val legendOthers: String get() = t("بازه‌های دیگر", "Other periods", "نورې مودې")
  val tapHint: String get() = t("برای عددِ دقیق روی هر ستون بزنید.", "Tap a bar for its exact figure.", "د دقیقې شمېرې لپاره پر هر ستون ووهئ.")
  val seriesSales: String get() = t("فروش", "Sales", "پلور")
  val seriesSpend: String get() = t("مصارف و خرید", "Expenses & purchases", "لګښتونه او پېرود")
  val statsEmpty: String get() = t("در این بازه فروشی ثبت نشده", "No sales in these periods", "په دې مودو کې پلور نه دی ثبت شوی")
  val streakTitle: String get() = t("روزهای فروشِ این هفته", "Days with sales this week", "د دې اونۍ د پلور ورځې")
  fun streakCaption(sold: Any, past: Any): String =
    t("$sold روز از $past روزِ گذشتهٔ این هفته فروش داشتید", "Sales on $sold of the $past days so far this week", "د دې اونۍ له $past تېرو ورځو $sold ورځې پلور درلود")
  val streakNone: String get() = t("این هفته هنوز فروشی ثبت نشده", "No sales recorded this week yet", "پدې اونۍ کې لا پلور نه دی ثبت شوی")
  val soldThatDay: String get() = t("فروش داشت", "Had sales", "پلور یې درلود")
  val hoursTitle: String get() = t("ساعت‌های فروش", "Sales by hour", "د پلور ساعتونه")
  val peakHour: String get() = t("پُرفروش‌ترین ساعت", "Busiest hour", "تر ټولو ګڼ ساعت")
  fun peakLine(span: Any, count: Any, amount: Any): String =
    t("پُرفروش‌ترین: $span — $count · $amount", "Busiest: $span — $count · $amount", "تر ټولو ګڼ: $span — $count · $amount")
  /** روزهای هفته به شمارِ ISO (۱ دوشنبه … ۷ یکشنبه) — برچسبِ کوتاهِ نمودار */
  val weekdaysShort: List<String> get() = listOf(
    t("دوشنبه", "Mon", "دوشنبه"), t("سه‌شنبه", "Tue", "سه‌شنبه"), t("چهارشنبه", "Wed", "چهارشنبه"),
    t("پنجشنبه", "Thu", "پنجشنبه"), t("جمعه", "Fri", "جمعه"), t("شنبه", "Sat", "شنبه"), t("یکشنبه", "Sun", "یکشنبه"),
  )
  /** حرفِ اولِ روزها (سرِ تقویم) برای ستون‌های تنگِ نمودار — همان ترتیبِ ISO */
  val weekdaysInitial: List<String> get() = listOf(
    t("د", "Mon", "د"), t("س", "Tue", "س"), t("چ", "Wed", "چ"), t("پ", "Thu", "پ"), t("ج", "Fri", "ج"), t("ش", "Sat", "ش"), t("ی", "Sun", "ی"),
  )

  // سابقهٔ عملیات
  val auditTitle: String get() = t("سابقهٔ عملیات", "Activity log", "د کړنو تاریخچه")
  fun auditCount(n: Any): String = t("$n رویداد ثبت‌شده", "$n ${pl(n, "event", "events")} recorded", "$n پېښې ثبت شوې")
  val eventKind: String get() = t("نوعِ رویداد", "Event type", "د پېښې ډول")
  val deletion: String get() = t("حذف", "Deleted", "ړنګ شوی")
  val auditEmpty: String get() = t("هنوز رویدادی ثبت نشده است.", "Nothing has been recorded yet.", "لا هېڅ پېښه نه ده ثبت شوې.")
  val auditEmptyBody: String get() = t("هر فروش، خرید، پرداخت و تغییری این‌جا می‌ماند.", "Every sale, purchase, payment and change stays here.", "هر پلور، پېرود، ورکړه او بدلون دلته پاتې کېږي.")
  val auditFilteredEmpty: String get() = t("از این نوع رویدادی نیست", "No events of this type", "له دې ډوله پېښه نشته")
  val showAll: String get() = t("نمایشِ همه", "Show all", "ټول وښایاست")
  val auditDenied: String get() = t("این بخش فقط برای صاحب دکان و مدیر است", "This section is only for the shop owner and managers", "دا برخه یوازې د دوکان خاوند او مدیر لپاره ده")
  val eventDetail: String get() = t("جزئیاتِ رویداد", "Event details", "د پېښې جزییات")
  val recordGone: String get() = t("این مورد دیگر در دفتر نیست.", "This record is no longer in the ledger.", "دا مورد نور په دفتر کې نشته.")
  val when_: String get() = t("زمان", "Time", "وخت")
  val amount: String get() = t("مبلغ", "Amount", "اندازه")
  val note: String get() = t("شرح", "Description", "تشریح")
  val openRecord: String get() = t("باز کردنِ همان مورد", "Open the record", "همغه مورد پرانیزئ")

  /** نامِ هر نوعِ رویداد — همان AUDIT_TYPE_LABELS ِ وب */
  fun kind(type: String): String = when (type) {
    "sale" -> t("ثبت فروش", "Sale recorded", "پلور ثبت شو")
    "cancel_sale" -> t("لغو فروش", "Sale cancelled", "پلور لغوه شو")
    "return" -> t("مرجوعی", "Return", "بېرته راګرځول")
    "purchase" -> t("ثبت خرید", "Purchase recorded", "پېرود ثبت شو")
    "customer_payment" -> t("پرداخت مشتری", "Customer payment", "د پېرودونکي ورکړه")
    "supplier_payment" -> t("پرداخت تأمین‌کننده", "Supplier payment", "عرضه‌کوونکي ته ورکړه")
    "stock_adjustment" -> t("اصلاح موجودی", "Stock adjustment", "د موجودۍ سمون")
    "supplier_return" -> t("برگشت به تأمین‌کننده", "Return to supplier", "عرضه‌کوونکي ته بېرته")
    "price_change" -> t("تغییر قیمت", "Price change", "د بیې بدلون")
    "product_edit" -> t("ویرایش کالا", "Item edited", "توکی سم شو")
    "shift_open" -> t("باز کردن صندوق", "Drawer opened", "صندوق پرانیستل شو")
    "shift_close" -> t("بستن صندوق", "Drawer closed", "صندوق وتړل شو")
    "delete_product" -> t("حذف محصول", "Item deleted", "توکی ړنګ شو")
    "debtor_delete" -> t("حذف قرض‌دار", "Debtor deleted", "پوروړی ړنګ شو")
    "debt_tx_delete" -> t("حذف تراکنش بدهی", "Debt entry deleted", "د پور راکړه ورکړه ړنګه شوه")
    "expense_delete" -> t("حذف مصرف", "Expense deleted", "لګښت ړنګ شو")
    "delete_entry" -> t("حذفِ ورودِ کالا", "Stock entry deleted", "د توکو راوړنه ړنګه شوه")
    "data_export" -> t("خروجی کامل داده", "Full data export", "د ټولو معلوماتو وېستل")
    "debt_due_set" -> t("سررسیدِ قرض", "Debt due date", "د پور د ورکړې نېټه")
    "credit_over_limit" -> t("برد بیش از سقفِ اعتبار", "Over the credit limit", "له پور حد څخه زیات")
    "credit_limit_set" -> t("سقفِ اعتبار", "Credit limit", "د پور حد")
    //  نوعِ ناشناخته: همان کدِ رویداد، در هر سه زبان (و از t، تا آزمونِ پوشش آن را بشمارد)
    else -> t(type, type, type)
  }

  // مرکزِ مدیریت
  val adminCaption: String get() = t("همهٔ دکان در یک نگاه", "The whole shop at a glance", "ټول دوکان په یوه کتنه")
  val todaySales: String get() = t("فروشِ امروز", "Today's sales", "د نن پلور")
  val monthSales: String get() = t("فروشِ این ماه", "This month's sales", "د دې میاشتې پلور")
  val monthProfit: String get() = t("سودِ خالصِ ماه", "Month's net profit", "د میاشتې خالصه ګټه")
  val monthLoss: String get() = t("ضررِ خالصِ ماه", "Month's net loss", "د میاشتې خالص تاوان")
  val afterExpenses: String get() = t("پس از مصارف", "After expenses", "له لګښتونو وروسته")
  val stockValue: String get() = t("ارزشِ انبار", "Stock value", "د ګدام ارزښت")
  fun itemCount(n: Any): String = t("$n قلم کالا", "$n ${pl(n, "item", "items")}", "$n ${pl(n, "توکی", "توکي")}")
  val owedByDebtors: String get() = t("طلب از قرض‌داران", "Owed by debtors", "له پوروړو طلب")
  fun people(n: Any): String = t("$n نفر", "$n ${pl(n, "person", "people")}", "$n تنه")
  val owedToSuppliers: String get() = t("بدهی به تأمین‌کننده", "Owed to suppliers", "عرضه‌کوونکو ته پور")
  fun suppliers(n: Any): String = t("$n تأمین‌کننده", "$n ${pl(n, "supplier", "suppliers")}", "$n عرضه‌کوونکي")
  val expectedCash: String get() = t("نقدِ مورد انتظار در دخل", "Expected cash in drawer", "په صندوق کې تمه شوې نغدې")
  val drawerClosed: String get() = t("صندوق بسته", "Drawer closed", "صندوق تړلی")
  fun openSince(at: Any): String = t("باز از $at", "Open since $at", "له $at راهیسې پرانیستی")
  val openFromHistory: String get() = t("از «تاریخچهٔ فروش» باز کنید", "Open it from Sales history", "له «د پلور تاریخچه» یې پرانیزئ")
  val stockAlert: String get() = t("هشدارِ انبار", "Stock alerts", "د ګدام خبرتیاوې")
  fun outAndLow(out: Any, low: Any): String = t("$out تمام‌شده · $low کم", "$out sold out · $low low", "$out خلاص · $low لږ")
  val staff: String get() = t("کارمندان", "Staff", "کارکوونکي")
  val subscription: String get() = t("اشتراکِ من", "My subscription", "زما ګډون")
  val noConnection: String get() = t("اتصال نیست", "No connection", "اړیکه نشته")
  val signedOut: String get() = t("وارد حساب نشده‌اید", "Not signed in", "حساب ته نه یاست ننوتي")
  val loading: String get() = t("در حالِ خواندن…", "Loading…", "لوستل کېږي…")

  // گزارش‌های کامپیوتر
  val deskSales: String get() = t("فروش", "Sales", "پلور")
  val deskProfit: String get() = t("سود و زیان", "Profit & loss", "ګټه او تاوان")
  val deskExpenses: String get() = t("مصارف", "Expenses", "لګښتونه")
  val deskSellers: String get() = t("فروشِ هر کاربر", "Sales by user", "د هر کارن پلور")
  val deskProducts: String get() = t("کالاها", "Items", "توکي")
  val rangeJMonth: String get() = t("ماهِ خورشیدی", "Solar month", "لمریزه میاشت")
  val rangeJYear: String get() = t("سالِ خورشیدی", "Solar year", "لمریز کال")
  val rangeEverything: String get() = t("همه", "All", "ټول")
  val rangeCustom: String get() = t("از … تا …", "From … to …", "له … تر …")
  val from: String get() = t("از", "From", "له")
  val to: String get() = t("تا", "To", "تر")
  val printShort: String get() = t("چاپ", "Print", "چاپ")
  val excel: String get() = t("اکسل", "Excel", "اکسل")
  fun savedTo(path: Any): String = t("ذخیره شد: $path", "Saved: $path", "خوندي شو: $path")
  val invoices: String get() = t("فاکتورها", "Invoices", "بلونه")
  val paid: String get() = t("پرداخت‌شده", "Paid", "ورکړل شوي")
  val onSupplierAccount: String get() = t("به حسابِ تأمین‌کننده", "On supplier account", "د عرضه‌کوونکي په حساب")
  val topSupplier: String get() = t("بیشترین تأمین‌کننده", "Top supplier", "لوی عرضه‌کوونکی")
  val expensesTotal: String get() = t("جمعِ مصارف", "Total expenses", "د لګښتونو ټولګه")
  val topCategory: String get() = t("بیشترین دسته", "Top category", "لویه ډله")
  val noCategory: String get() = t("بی دسته", "Uncategorized", "بې ډلې")
  val items: String get() = t("کالاها", "Items", "توکي")
  val debtorsOwing: String get() = t("بدهکاران", "Owing", "پوروړي")
  val totalReceivable: String get() = t("طلبِ کل", "Total owed", "ټول طلب")
  val tableEmpty: String get() = t("در این بازه چیزی نیست.", "Nothing in this period.", "په دې موده کې هېڅ نشته.")
  val total: String get() = t("جمع", "Total", "ټولګه")
  val reportType: String get() = t("نوعِ گزارش", "Report", "د راپور ډول")
  val freshReport: String get() = t("گزارشِ تازه (آزمایشی)", "New reports (beta)", "نوي راپورونه (ازمایښتي)")

  // ── از main: صفحهٔ «گزارش‌ها» (از RPT-17: شماره‌های جاافتادهٔ اسناد) ──

  // تنظیمات ← شماره‌های جاافتاده (فقط کامپیوتر)
  val docGaps: String get() = t("شماره‌های جاافتادهٔ اسناد", "Missing document numbers", "د سندونو ورکې شمېرې")
  val docGapsOn: String get() = t("زبانهٔ «شماره‌های جاافتاده» در «گزارش‌ها»", "“Missing numbers” tab under “Reports”", "په «راپورونه» کې د «ورکې شمېرې» پاڼه")
  val docGapsNote: String get() = t(
    "یک زبانهٔ تازه در «گزارش‌ها»: برای هر سریِ فاکتورِ فروش و برای فاکتورهای خرید، شماره‌هایی که میانِ نخستین و آخرین سند نیستند. جای خالی‌ای که شمارنده پس از همگام‌سازی با دستگاهِ دیگر جلو پریده «پرشِ شمارنده» نام دارد و جاافتاده نیست؛ بقیه «جاافتاده»اند: سندی پاک شده، هنوز از دستگاهِ دیگری نرسیده یا دفتر از پشتیبانِ کهنه برگشته. شمارهٔ تکراری و فاکتورِ لغوشده (که شماره‌اش را نگه می‌دارد) هم شمرده می‌شوند. فقط خواندنی است و چیزی را عوض نمی‌کند. در صفحهٔ فعلیِ گزارش‌ها می‌آید، نه در «گزارشِ تازه». پس از تغییر، «گزارش‌ها» را دوباره باز کنید.",
    "A new tab under “Reports”: for each sales invoice series and for purchase invoices, the numbers missing between the first and the last document. A gap left when the counter moved ahead after syncing with another device is a “counter jump”, not a missing number; every other gap is “missing”: a document was deleted, has not arrived from another device yet, or the ledger was restored from an old backup. Duplicate numbers and cancelled invoices (which keep their number) are counted too. It is read-only and changes nothing. It appears on the current reports screen, not in “New report”. After changing this, reopen “Reports”.",
    "په «راپورونه» کې یوه نوې پاڼه: د پلور د بل هرې لړۍ او د پېرود بلونو لپاره، هغه شمېرې چې د لومړي او وروستي سند ترمنځ نشته. هغه تشه چې شمېرونکی له بلې وسیلې سره له همغږۍ وروسته مخکې ټوپ کړی «د شمېرونکي ټوپ» بلل کېږي او ورکه نه ده؛ نورې ټولې «ورکې» دي: کوم سند ړنګ شوی، لا له بلې وسیلې نه دی راغلی یا دفتر له زاړه بیک‌اپ څخه بېرته راغلی. تکراري شمېرې او لغوه شوي بلونه (چې خپله شمېره ساتي) هم شمېرل کېږي. یوازې د لوستلو دی او هېڅ نه بدلوي. په اوسني راپورونو پاڼه کې راځي، نه په «نوی راپور» کې. له بدلون وروسته «راپورونه» بیا پرانیزئ.",
  )

  // زبانه و کارت‌ها
  val gapsTab: String get() = t("شماره‌های جاافتاده", "Missing numbers", "ورکې شمېرې")
  val gapsNow: String get() = t("همهٔ اسنادِ شماره‌دارِ دفتر — بی بازهٔ تاریخ", "All numbered documents in the ledger — no date range", "د دفتر ټول شمېره‌لرونکي سندونه — بې نېټې")
  val kpiMissing: String get() = t("شمارهٔ جاافتاده", "Missing numbers", "ورکې شمېرې")
  val kpiJumped: String get() = t("پرشِ شمارنده", "Counter jumps", "د شمېرونکي ټوپونه")
  val kpiDuplicates: String get() = t("شمارهٔ تکراری", "Duplicate numbers", "تکراري شمېرې")
  val kpiCancelled: String get() = t("لغوشده (شماره دارد)", "Cancelled (numbered)", "لغوه شوي (شمېره لري)")

  // برگه (سرستون و خانه‌ها)
  val colKind: String get() = t("نوعِ سند", "Document type", "د سند ډول")
  val colSeries: String get() = t("سری", "Series", "لړۍ")
  val colFrom: String get() = t("از شمارهٔ", "From no.", "له شمېرې")
  val colTo: String get() = t("تا شمارهٔ", "To no.", "تر شمېرې")
  val colCount: String get() = t("تعداد", "Count", "شمېر")
  val colStatus: String get() = t("وضعیت", "Status", "حالت")
  val colBefore: String get() = t("روزِ سندِ پیش", "Date of document before", "د مخکیني سند نېټه")
  val colAfter: String get() = t("روزِ سندِ پس", "Date of document after", "د وروسته سند نېټه")
  val kindSale: String get() = t("فاکتورِ فروش", "Sales invoice", "د پلور بل")
  val kindPurchase: String get() = t("فاکتورِ خرید", "Purchase invoice", "د پېرود بل")
  val noSeries: String get() = t("بی سری", "No series", "بې لړۍ")
  val statusMissing: String get() = t("جاافتاده", "Missing", "ورکه")
  fun statusJump(series: String): String = t("پرشِ شمارنده (سریِ $series)", "Counter jump (series $series)", "د شمېرونکي ټوپ (لړۍ $series)")
  fun statusDuplicate(n: Int): String = t("تکراری: $n سند با یک شماره", "Duplicate: $n documents with one number", "تکراري: $n سندونه په یوه شمېره")
  fun moreGaps(n: Int): String = t("… و $n جای خالیِ دیگر (شمارها بالا کامل‌اند)", "… and $n more gaps (the totals above are complete)", "… او $n نورې تشې (پورته شمېرې بشپړې دي)")
  val sheetName: String get() = t("شماره‌های-جاافتاده", "missing-numbers", "ورکې-شمېرې")
}
