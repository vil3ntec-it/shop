package ir.vil3ntec.tohid.core.i18n

/**
 *  «پرسشِ ساده از گزارش‌ها» (RPT-16) — زبانهٔ «گزارش‌ها» و بخشِ تنظیماتِ همین دستگاه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/report/SimpleAsk` (commonMain) و `data/SimpleAskEngine`. فرمولِ هر پاسخ
 *  همان حسابِ `ReportEngine` است و تغییرِ یکی بی دیگری ممنوع.
 */
@Suppress("unused")
object StrSimpleAsk {
  val settingsTitle: String get() = t("پرسشِ ساده از گزارش‌ها", "Simple report questions", "د راپورونو ساده پوښتنې")
  val settingsNote: String get() = t(
    "زبانهٔ «پرسشِ ساده» در «گزارش‌ها»: پرسش‌های آماده (فروش، سود، مصارف، خرید، موجودی، قرض…) با بازه، کالا، دسته، مشتری یا فروشنده، یا یک جملهٔ کوتاه. پاسخ از همان گزارش‌هاست و فرمولش زیرِ هر پاسخ می‌آید. هوش مصنوعی در کار نیست و چیزی به اینترنت نمی‌رود.",
    "A «Simple question» tab in Reports: ready questions (sales, profit, expenses, purchases, stock, debt…) for a period, product, category, customer or seller, or a short sentence. Every answer comes from the same reports, with its formula under it. No AI is involved and nothing goes to the internet.",
    "په «راپورونو» کې د «ساده پوښتنې» برخه: چمتو پوښتنې (پلور، ګټه، لګښت، پېر، موجودي، پور…) د مودې، توکي، ډلې، پیرودونکي یا پلورونکي لپاره، یا یوه لنډه جمله. ځواب له همدې راپورونو دی او فورمول یې لاندې لیکل کېږي. مصنوعي ځیرکتیا نشته او هېڅ شی انټرنېټ ته نه ځي.",
  )
  val tab: String get() = t("پرسشِ ساده", "Simple question", "ساده پوښتنه")
  val intro: String get() = t(
    "یک پرسشِ آماده را بزنید یا کوتاه بنویسید، مثلاً «فروشِ امروز»، «سودِ ماهِ پیش» یا «موجودیِ برنج».",
    "Tap a ready question or type a short one, for example «sales today», «profit last month» or «stock rice».",
    "یوه چمتو پوښتنه وټاکئ یا لنډه یې ولیکئ، لکه «نن پلور»، «د تېرې میاشتې ګټه» یا «د وريجو موجودي».",
  )
  val noAi: String get() = t(
    "پاسخ‌ها با قاعده و از همان گزارش‌های این برنامه ساخته می‌شوند؛ هوش مصنوعی در کار نیست.",
    "Answers are built by fixed rules from this app's own reports; no AI is involved.",
    "ځوابونه د ټاکلو قاعدو له مخې د همدې برنامې له راپورونو جوړېږي؛ مصنوعي ځیرکتیا نشته.",
  )
  val input: String get() = t("پرسش", "Question", "پوښتنه")
  val ask: String get() = t("بپرس", "Ask", "وپوښتئ")
  val presets: String get() = t("پرسش‌های آماده", "Ready questions", "چمتو پوښتنې")
  val notUnderstood: String get() = t(
    "این پرسش را نفهمیدم. یکی از پرسش‌های آماده را بزنید.",
    "I did not understand this question. Tap one of the ready questions.",
    "دا پوښتنه مې ونه پوهېده. یوه چمتو پوښتنه وټاکئ.",
  )
  fun unknownWords(words: Any): String = t(
    "این واژه‌ها در این پرسش شناخته نشد: $words — یکی از پرسش‌های آماده را بزنید یا نام را از فهرست برگزینید.",
    "These words were not recognised in this question: $words — tap a ready question or pick the name from the list.",
    "دا کلمې پدې پوښتنه کې ونه پېژندل شوې: $words — یوه چمتو پوښتنه وټاکئ یا نوم له لېست وټاکئ.",
  )
  val ambiguous: String get() = t(
    "این نام به چند ردیف می‌خورد؛ از فهرست برگزینید.",
    "This name matches more than one entry; pick it from the list.",
    "دا نوم له څو قطارونو سره سمون لري؛ له لېست یې وټاکئ.",
  )
  val notAllowed: String get() = t(
    "نقشِ شما عددهای مالی را نمی‌بیند؛ این پرسش برایتان بسته است.",
    "Your role cannot see money figures; this question is closed to you.",
    "ستاسې رول مالي عددونه نه ویني؛ دا پوښتنه درته تړلې ده.",
  )
  fun missing(what: Any): String = t("$what را برگزینید.", "Choose the $what.", "$what وټاکئ.")
  fun unknown(what: Any): String = t(
    "این $what در دفتر نیست؛ از فهرست برگزینید.",
    "This $what is not in the books; pick one from the list.",
    "دا $what په دفتر کې نشته؛ له لېست یو وټاکئ.",
  )
  val badPeriod: String get() = t("ماه یا سالِ خورشیدیِ نادرست", "Invalid solar month or year", "ناسمه لمریزه میاشت یا کال")

  // جای خالی‌ها
  val period: String get() = t("بازه", "period", "موده")
  val product: String get() = t("کالا", "product", "توکی")
  val category: String get() = t("دسته", "category", "ډله")
  val customer: String get() = t("مشتری", "customer", "پیرودونکی")
  val cashier: String get() = t("فروشنده", "seller", "پلورونکی")
  val search: String get() = t("جست‌وجوی نام", "Search name", "نوم ولټوئ")
  val today: String get() = t("امروز", "Today", "نن")
  val week: String get() = t("۷ روز", "7 days", "۷ ورځې")
  val month: String get() = t("این ماه", "This month", "دا میاشت")
  val solarMonth: String get() = t("ماهِ خورشیدی", "Solar month", "لمریزه میاشت")
  val solarYear: String get() = t("سالِ خورشیدی", "Solar year", "لمریز کال")
  val all: String get() = t("از آغاز", "All time", "له پیله")
  val prev: String get() = t("قبلی", "Previous", "مخکېنی")
  val next: String get() = t("بعدی", "Next", "راتلونکی")
  fun year(n: Any): String = t("سالِ $n", "Year $n", "$n کال")
  fun range(from: Any, to: Any): String = t("$from تا $to", "$from to $to", "له $from تر $to")
  val allDates: String get() = t("همهٔ تاریخ‌ها", "All dates", "ټولې نېټې")
  val now: String get() = t("وضعیتِ همین حالا", "As of now", "همدا اوس")

  // پرسش‌های آماده
  val qSales: String get() = t("فروش چقدر بود؟", "How much did we sell?", "پلور څومره و؟")
  val qProfit: String get() = t("سود چقدر شد؟", "What was the profit?", "ګټه څومره شوه؟")
  val qExpenses: String get() = t("مصارف چقدر بود؟", "How much were the expenses?", "لګښتونه څومره وو؟")
  val qPurchases: String get() = t("چقدر خرید کردیم؟", "How much did we buy?", "څومره مو پېر کړل؟")
  val qCashierSales: String get() = t("این فروشنده چقدر فروخت؟", "How much did this seller sell?", "دې پلورونکي څومره وپلورل؟")
  val qCategorySales: String get() = t("فروشِ این دسته چقدر بود؟", "What did this category sell?", "د دې ډلې پلور څومره و؟")
  val qProductSales: String get() = t("فروشِ این کالا چقدر بود؟", "What did this product sell?", "د دې توکي پلور څومره و؟")
  val qProductStock: String get() = t("از این کالا چقدر مانده؟", "How much of this product is left?", "له دې توکي څومره پاتې دي؟")
  val qLowStock: String get() = t("چه چیزهایی تمام یا کم است؟", "What is out or low?", "څه خلاص یا لږ دي؟")
  val qTopSelling: String get() = t("پرفروش‌ترین کالاها کدام‌اند؟", "Which products sell best?", "تر ټولو ډېر پلورل شوي توکي کوم دي؟")
  val qSlowSelling: String get() = t("کم‌فروش‌ترین کالاها کدام‌اند؟", "Which products sell least?", "تر ټولو لږ پلورل شوي توکي کوم دي؟")
  val qCustomerDebt: String get() = t("این مشتری چقدر قرض دارد؟", "How much does this customer owe?", "دا پیرودونکی څومره پور لري؟")
  val qDebtTotal: String get() = t("جمعِ طلب از مشتریان چقدر است؟", "How much do customers owe in total?", "پیرودونکي ټول څومره پور لري؟")

  // عددها
  val vNet: String get() = t("فروشِ خالص", "Net sales", "خالص پلور")
  val vInvoices: String get() = t("فاکتورها", "Invoices", "بلونه")
  val vDiscount: String get() = t("تخفیف", "Discount", "تخفیف")
  val vReturns: String get() = t("برگشتی (جدا، کم نشده)", "Returns (separate, not subtracted)", "بېرته راګرځېدلي (جلا، نه کمېږي)")
  val vCogs: String get() = t("بهای کالای فروخته‌شده", "Cost of goods sold", "د پلورل شویو توکو بیه")
  val vGrossProfit: String get() = t("سودِ ناخالص", "Gross profit", "ناخالصه ګټه")
  val vExpenses: String get() = t("مصارف", "Expenses", "لګښتونه")
  val vNetProfit: String get() = t("سودِ خالص", "Net profit", "خالصه ګټه")
  val vPurchaseTotal: String get() = t("جمعِ خرید", "Total purchases", "ټول پېر")
  val vPurchasePaid: String get() = t("پرداخت‌شده", "Paid", "ورکړل شوی")
  val vPurchaseDebt: String get() = t("به حسابِ تأمین‌کننده", "Owed to suppliers", "د برابروونکي پر حساب")
  val vPurchases: String get() = t("شمارِ خریدها", "Number of purchases", "د پېرونو شمېر")
  val vCategoryNet: String get() = t("فروشِ کالاهای دسته", "Category product sales", "د ډلې د توکو پلور")
  val vCategoryProducts: String get() = t("کالاهای فروخته‌شده", "Products sold", "پلورل شوي توکي")
  val vProductNet: String get() = t("فروشِ کالا", "Product sales", "د توکي پلور")
  val vOnHand: String get() = t("موجودی", "On hand", "موجودي")
  val vOut: String get() = t("تمام‌شده", "Out of stock", "خلاص شوي")
  val vLow: String get() = t("موجودی کم", "Low stock", "لږه موجودي")
  val vSold: String get() = t("فروخته‌شده", "Sold", "پلورل شوي")
  val vDebtRemaining: String get() = t("مانده", "Balance", "پاتې")
  val vDebtGiven: String get() = t("فروشِ نسیه و قرض", "Credit sales and loans", "نسیه پلور او پور")
  val vDebtReceived: String get() = t("پولِ گرفته‌شده", "Received", "اخیستل شوې پیسې")
  val vDebtTotal: String get() = t("جمع طلب از مشتریان", "Total owed by customers", "له پیرودونکو ټول پور")
  val vDebtors: String get() = t("بدهکاران", "Debtors", "پوروړي")
  val stOut: String get() = t("تمام‌شده", "Out of stock", "خلاص شوی")
  val stLow: String get() = t("موجودی کم", "Low stock", "لږه موجودي")
  val stOk: String get() = t("موجود", "In stock", "شته")
  val settled: String get() = t("تسویه شده", "Settled", "تصفیه شوی")
  fun credit(amount: Any): String = t("$amount پیش‌پرداخت", "$amount in credit", "$amount مخکې ورکړل شوي")
  val none: String get() = t("چیزی نیست", "Nothing here", "هېڅ نشته")
  fun more(n: Any): String = t("و $n تای دیگر", "and $n more", "او $n نور")

  // فرمول و منبع
  val formula: String get() = t("فرمول و منبع", "Formula and source", "فورمول او سرچینه")
  val fSales: String get() = t(
    "فروشِ خالص = جمعِ «مبلغِ نهایی» ِ فاکتورهای لغونشدهٔ این بازه (پس از تخفیف). برگشتی در روزِ خودش جدا نشان داده می‌شود و از این عدد کم نمی‌شود. منبع: «گزارش‌ها ⇐ سود و فروش».",
    "Net sales = sum of the final amount of non-cancelled invoices in this period (after discount). Returns are shown separately on their own day and are not subtracted. Source: Reports ⇐ Profit and sales.",
    "خالص پلور = پدې موده کې د نه لغوه شویو بلونو د وروستي مبلغ ټولګه (له تخفیف وروسته). بېرته راګرځېدنه په خپله ورځ جلا ښودل کېږي او له دې عدد نه نه کمېږي. سرچینه: «راپورونه ⇐ ګټه او پلور».",
  )
  val fProfit: String get() = t(
    "سودِ ناخالص = فروشِ خالص − بهای کالای فروخته‌شده (قیمتِ خریدِ ثبت‌شده روی هر قلم) − اثرِ برگشتی‌های این بازه؛ سودِ خالص = سودِ ناخالص − مصارف. منبع: «گزارش‌ها ⇐ سود و فروش».",
    "Gross profit = net sales − cost of goods sold (purchase price recorded on each line) − the effect of this period's returns; net profit = gross profit − expenses. Source: Reports ⇐ Profit and sales.",
    "ناخالصه ګټه = خالص پلور − د پلورل شویو توکو بیه (په هر قلم ثبت شوې د پېر بیه) − پدې موده کې د بېرته راګرځېدنې اغېز؛ خالصه ګټه = ناخالصه ګټه − لګښتونه. سرچینه: «راپورونه ⇐ ګټه او پلور».",
  )
  val fExpenses: String get() = t(
    "جمعِ مصارفِ ثبت‌شده با تاریخِ همین بازه. منبع: «گزارش‌ها ⇐ سود و فروش» (مصارف).",
    "Sum of the expenses recorded with a date in this period. Source: Reports ⇐ Profit and sales (expenses).",
    "پدې موده کې د ثبت شویو لګښتونو ټولګه. سرچینه: «راپورونه ⇐ ګټه او پلور» (لګښتونه).",
  )
  val fPurchases: String get() = t(
    "جمعِ خریدهای این بازه، پرداخت‌شده و ماندهٔ به حسابِ تأمین‌کننده. منبع: «گزارش‌ها ⇐ خرید».",
    "Total of this period's purchases, the paid part and what is still owed to suppliers. Source: Reports ⇐ Purchases.",
    "پدې موده کې د پېرونو ټولګه، ورکړل شوې برخه او د برابروونکي پر حساب پاتې. سرچینه: «راپورونه ⇐ پېر».",
  )
  val fCashierSales: String get() = t(
    "فاکتورهای لغونشدهٔ این بازه که نامِ همین فروشنده رویشان است (بی نام ⇒ «نامعلوم»)؛ جمعِ مبلغِ نهایی و تخفیف. منبع: «گزارش‌ها ⇐ فروشِ هر کاربر».",
    "Non-cancelled invoices in this period that carry this seller's name (no name ⇒ «unknown»); sum of final amount and discount. Source: Reports ⇐ Sales per user.",
    "پدې موده کې هغه نه لغوه شوي بلونه چې د همدې پلورونکي نوم پرې دی (بې نومه ⇒ «نامعلوم»)؛ د وروستي مبلغ او تخفیف ټولګه. سرچینه: «راپورونه ⇐ د هر کارن پلور».",
  )
  val fCategorySales: String get() = t(
    "برای هر کالای این دسته: (مقدارِ فروخته − برگشتی) × قیمتِ واحدِ همان قلم، فاکتورِ لغوشده بیرون. تخفیفِ کلِ فاکتور پخش نمی‌شود، پس جمع با «فروشِ خالص» یکی نیست. منبع: «گزارش‌ها ⇐ تحلیلِ موجودی».",
    "For each product in this category: (quantity sold − returned) × that line's unit price, cancelled invoices excluded. Whole-invoice discounts are not spread, so the sum differs from net sales. Source: Reports ⇐ Stock analysis.",
    "د دې ډلې د هر توکي لپاره: (پلورل شوې اندازه − بېرته راغلې) × د همغه قلم د یوه واحد بیه، لغوه شوي بلونه بهر. د ټول بل تخفیف نه وېشل کېږي، نو ټولګه له خالص پلور سره یو شان نه ده. سرچینه: «راپورونه ⇐ د موجودي شننه».",
  )
  val fProductSales: String get() = t(
    "(مقدارِ فروخته − برگشتی) × قیمتِ واحدِ هر قلمِ این کالا در این بازه، فاکتورِ لغوشده بیرون. تخفیفِ کلِ فاکتور پخش نمی‌شود. منبع: «گزارش‌ها ⇐ تحلیلِ موجودی».",
    "(Quantity sold − returned) × unit price of each line of this product in this period, cancelled invoices excluded. Whole-invoice discounts are not spread. Source: Reports ⇐ Stock analysis.",
    "پدې موده کې د دې توکي د هر قلم (پلورل شوې اندازه − بېرته راغلې) × د یوه واحد بیه، لغوه شوي بلونه بهر. د ټول بل تخفیف نه وېشل کېږي. سرچینه: «راپورونه ⇐ د موجودي شننه».",
  )
  val fProductStock: String get() = t(
    "موجودیِ همین حالا، همان عددی که «کالاها» نشان می‌دهد؛ «کم» یعنی به حداقلِ تعریف‌شدهٔ کالا رسیده یا پایین‌تر است. منبع: «گزارش‌ها ⇐ محصولات».",
    "Stock right now, the same number the Products list shows; «low» means at or below the product's minimum. Source: Reports ⇐ Products.",
    "همدا اوس موجودي، هماغه عدد چې «توکي» یې ښيي؛ «لږ» یعنې د توکي لږترلږه حد ته رسېدلې یا ترې ښکته ده. سرچینه: «راپورونه ⇐ توکي».",
  )
  val fLowStock: String get() = t(
    "کالاهای تمام‌شده و کم‌موجود (به حداقلِ کالا رسیده) همین حالا. منبع: «گزارش‌ها ⇐ محصولات ⇐ نیاز به توجه».",
    "Products that are out or low (at the product's minimum) right now. Source: Reports ⇐ Products ⇐ Needs attention.",
    "همدا اوس خلاص شوي او لږ موجود توکي (د توکي لږترلږه حد ته رسېدلي). سرچینه: «راپورونه ⇐ توکي ⇐ پاملرنې ته اړتیا».",
  )
  val fTopSelling: String get() = t(
    "پنج کالا با بیشترین مقدارِ فروخته از آغاز تا حالا، فاکتورِ لغوشده بیرون. منبع: «گزارش‌ها ⇐ محصولات ⇐ پرفروش‌ترین».",
    "The five products with the largest quantity sold since the start, cancelled invoices excluded. Source: Reports ⇐ Products ⇐ Best selling.",
    "هغه پنځه توکي چې له پیله تر اوسه تر ټولو ډېره اندازه یې پلورل شوې، لغوه شوي بلونه بهر. سرچینه: «راپورونه ⇐ توکي ⇐ تر ټولو ډېر پلورل شوي».",
  )
  val fSlowSelling: String get() = t(
    "پنج کالا با کمترین مقدارِ فروخته از آغاز تا حالا، فقط میانِ کالاهایی که فروش داشته‌اند. منبع: «گزارش‌ها ⇐ محصولات ⇐ کم‌فروش‌ترین».",
    "The five products with the smallest quantity sold since the start, among products that have sold at all. Source: Reports ⇐ Products ⇐ Least selling.",
    "هغه پنځه توکي چې له پیله تر اوسه تر ټولو لږه اندازه یې پلورل شوې، یوازې د پلورل شویو توکو له منځه. سرچینه: «راپورونه ⇐ توکي ⇐ تر ټولو لږ پلورل شوي».",
  )
  val fCustomerDebt: String get() = t(
    "مانده = جمعِ فروشِ نسیه و قرضِ داده‌شده − جمعِ پولِ گرفته‌شده از این مشتری. منبع: «گزارش‌ها ⇐ قرض‌داران».",
    "Balance = credit sales and loans given − money received from this customer. Source: Reports ⇐ Debtors.",
    "پاتې = نسیه پلور او ورکړل شوی پور − له دې پیرودونکي اخیستل شوې پیسې. سرچینه: «راپورونه ⇐ پوروړي».",
  )
  val fDebtTotal: String get() = t(
    "جمعِ مانده‌های مثبتِ همهٔ قرض‌داران (پیش‌پرداختِ کسی از طلبِ دیگری کم نمی‌شود)؛ بدهکار یعنی ماندهٔ بیش از نیم افغانی. منبع: «گزارش‌ها ⇐ قرض‌داران».",
    "Sum of every debtor's positive balance (one customer's credit does not reduce another's debt); a debtor owes more than half an afghani. Source: Reports ⇐ Debtors.",
    "د ټولو پوروړو د مثبتو پاتې پیسو ټولګه (د یوه پیرودونکي مخکې ورکړې د بل له پوره نه کمېږي)؛ پوروړی یعنې تر نیمې افغانۍ ډېر پاتې. سرچینه: «راپورونه ⇐ پوروړي».",
  )
}
