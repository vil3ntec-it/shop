package ir.vil3ntec.tohid.core.i18n

/**
 *  پوسته و داشبوردِ **کامپیوتر** (`ui/desk`، `claude/e-desk-d1`): منوی کناری، «همهٔ بخش‌ها»، داشبوردِ خانه و نوارِ وضعیت.
 *  هر نامِ بخش همان نوشتهٔ `Str`/`StrNav` ِ پیشین است؛ این‌جا فقط نوشته‌های تازهٔ کامپیوتر. دری از لایهٔ `DariText`.
 */
@Suppress("unused")
object StrDesk {
  //  ── منوی کناری ──
  val groupOnline: String get() = t("فروشِ آنلاین", "Online sales", "آنلاین پلور")
  val hideMenu: String get() = t("پنهان کردنِ منو", "Hide menu", "مېنو پټ کړئ")
  val showMenu: String get() = t("نشان دادنِ منو", "Show menu", "مېنو وښایاست")
  val mainNav: String get() = t("منوی بخش‌ها", "Sections menu", "د برخو مېنو")

  //  ── «همهٔ بخش‌ها» ──
  val allSections: String get() = t("همهٔ بخش‌ها", "All sections", "ټولې برخې")
  val allSectionsLead: String get() = t(
    "هر بخشِ برنامه یک‌جا؛ هر کارت صفحهٔ خودش را باز می‌کند.",
    "Every part of the app in one place; each card opens its own page.",
    "د اپ هره برخه په یوه ځای کې؛ هر کارت خپله پاڼه پرانیزي.",
  )
  val homeSub: String get() = t("خلاصهٔ امروز: فروش، سود، صندوق و قرض", "Today at a glance: sales, profit, cash and debts", "د نن لنډیز: پلور، ګټه، صندوق او پور")
  val saleSub: String get() = t("فروشِ تازه با بارکد، سبد و پرداخت", "New sale with barcode, cart and payment", "نوی پلور د بارکوډ، ټوکرۍ او تادیې سره")
  val debtorsSub: String get() = t("قرض و دریافتِ هر مشتری و دفترِ حسابش", "Each customer's debt, payments and account book", "د هر پیرودونکي پور، تادیې او حساب")
  val productsSub: String get() = t("فهرستِ کالاها، قیمت، بارکد و دسته", "Item list, prices, barcodes and categories", "د توکو لړلیک، بیې، بارکوډ او ډلې")
  val fulfilSub: String get() = t("جمع‌آوری و بسته‌بندیِ سفارش‌های پذیرفته", "Pick and pack accepted orders", "د منل شوو فرمایشونو راټولول او بسته کول")
  val settingsSub: String get() = t("دکان، پرداخت، دستگاه‌ها، پشتیبان و ظاهر", "Shop, payments, devices, backup and appearance", "دوکان، تادیه، وسایل، بیک‌اپ او بڼه")
  val updatesSub: String get() = t("نسخهٔ نصب‌شده و گرفتنِ نسخهٔ تازه", "Installed version and getting a new one", "نصب شوې نسخه او د نوې نسخې اخیستل")
  val whatsNewSub: String get() = t("تغییرهای هر نسخه", "Changes in each version", "د هرې نسخې بدلونونه")
  val offNote: String get() = t("برای این دکان خاموش است", "Off for this shop", "د دې دوکان لپاره بند دی")

  //  ── داشبورد ──
  val cashNow: String get() = t("نقدِ صندوق", "Cash in drawer", "په صندوق کې نغدې")
  val versusYesterdayNow: String get() = t("نسبت به همین ساعتِ دیروز", "vs. this time yesterday", "د پرون د همدې وخت په پرتله")
  val versusYesterday: String get() = t("نسبت به دیروز", "vs. yesterday", "د پرون په پرتله")
  val noYesterday: String get() = t("دیروز تا این ساعت فروشی نبود", "No sales by this time yesterday", "پرون تر دې وخته پلور نه و")
  val noYesterdayProfit: String get() = t("دیروز سودی ثبت نشد", "No profit recorded yesterday", "پرون ګټه نه وه ثبت شوې")
  fun invoicesToday(n: Any): String = t("${asIs(n)} فاکتور امروز", "$n ${pl(n, "invoice", "invoices")} today", "نن $n ${pl(n, "بل", "بلونه")}")
  val shiftOpen: String get() = t("صندوق باز است", "Register is open", "صندوق خلاص دی")
  val shiftClosed: String get() = t("صندوق بسته — گردشِ نقدِ امروز", "Register closed — today's cash flow", "صندوق تړلی — د نن نغدې")
  fun overduePeople(n: Any): String = t("${asIs(n)} نفر بیش از 30 روز", "$n ${pl(n, "person", "people")} over 30 days", "$n تنه له 30 ورځو زیات")
  fun debtorsPeople(n: Any): String = t("${asIs(n)} قرض‌دار", "$n ${pl(n, "debtor", "debtors")}", "$n پوروړي")
  val salesChart: String get() = t("فروشِ روزانه", "Daily sales", "ورځنی پلور")
  val days7: String get() = t("7 روز", "7 days", "7 ورځې")
  val days30: String get() = t("30 روز", "30 days", "30 ورځې")
  fun rangeTotal(a: Any): String = t("جمعِ بازه: ${asIs(a)} ؋", "Period total: $a ؋", "د مودې ټول: $a ؋")
  fun bestDayLine(a: Any): String = t("بهترین روز: ${asIs(a)} ؋", "Best day: $a ؋", "غوره ورځ: $a ؋")
  val topProducts: String get() = t("پرفروش‌ترین کالاها", "Top-selling items", "تر ټولو پلورل شوي توکي")
  val thisWeek: String get() = t("این هفته", "This week", "دا اونۍ")
  val noTopToday: String get() = t("امروز هنوز کالایی فروخته نشده", "No items sold today yet", "نن لا توکی نه دی پلورل شوی")
  val noTopWeek: String get() = t("این هفته کالایی فروخته نشده", "No items sold this week", "دا اونۍ توکی نه دی پلورل شوی")
  fun soldQty(q: Any): String = t("${asIs(q)} فروخته", "$q sold", "$q پلورل شوي")
  val todayTable: String get() = t("تراکنش‌های امروز", "Today's transactions", "د نن راکړې ورکړې")
  val colTime: String get() = t("ساعت", "Time", "وخت")
  val colInvoice: String get() = t("فاکتور", "Invoice", "بل")
  val colCustomer: String get() = t("مشتری / طرف", "Customer / party", "پیرودونکی / لوری")
  val colItems: String get() = t("اقلام", "Items", "توکي")
  val colAmount: String get() = t("مبلغ", "Amount", "مبلغ")
  val colPayment: String get() = t("پرداخت", "Payment", "تادیه")
  val colStatus: String get() = t("وضعیت", "Status", "حالت")
  val payCard: String get() = t("کارت", "Card", "کارت")
  val payMixed: String get() = t("چندگانه", "Split", "ګډ")
  val purchase: String get() = t("خرید", "Purchase", "پېرود")
  val noTxToday: String get() = t("امروز هنوز تراکنشی نیست", "No transactions yet today", "نن لا راکړه ورکړه نشته")
  val noTxTodayBody: String get() = t(
    "نخستین فروشِ امروز را بزنید؛ همین‌جا با ساعت و مبلغ دیده می‌شود.",
    "Ring up today's first sale; it shows here with its time and amount.",
    "د نن لومړی پلور ثبت کړئ؛ دلته به د وخت او مبلغ سره ښکاره شي.",
  )
  val quickActions: String get() = t("کارهای سریع", "Quick actions", "چټک کارونه")
  val searchAll: String get() = t("جست‌وجو در همه‌جا", "Search everywhere", "په ټولو ځایونو کې لټون")
  val newDebtor: String get() = t("قرض‌دارِ تازه", "New debtor", "نوی پوروړی")
  val stockIn: String get() = t("ورودِ کالا", "Stock in", "د توکو داخلول")
  val newExpense: String get() = t("مصرفِ تازه", "New expense", "نوی لګښت")
  fun margin(p: Any): String = t("حاشیهٔ سود ${asIs(p)}٪ از فروش", "Margin $p% of sales", "د پلور $p٪ ګټه")
  val noSalesNoMargin: String get() = t("امروز هنوز فروشی نیست", "No sales yet today", "نن لا پلور نشته")
  fun chartTip(day: Any, amount: Any, n: Any): String = t(
    "${asIs(day)} · ${asIs(amount)} ؋ · ${asIs(n)} فاکتور",
    "$day · $amount ؋ · $n ${pl(n, "invoice", "invoices")}",
    "$day · $amount ؋ · $n ${pl(n, "بل", "بلونه")}",
  )
  fun percent(n: Any): String = t("${asIs(n)}٪", "$n%", "$n٪")
  fun deltaA11y(pct: Int, versus: String): String = when {
    pct > 0 -> t("${asIs(pct)} درصد بیشتر ${asIs(versus)}", "$pct% higher $versus", "$pct سلنه زیات $versus")
    pct < 0 -> t("${asIs(-pct)} درصد کمتر ${asIs(versus)}", "${-pct}% lower $versus", "${-pct} سلنه کم $versus")
    else -> t("بی تغییر ${asIs(versus)}", "No change $versus", "بې بدلونه $versus")
  }

  //  ── نوارِ وضعیت ──
  val statusBar: String get() = t("نوارِ وضعیت", "Status bar", "د حالت پټه")
}
