package ir.vil3ntec.tohid.core.i18n

/**
 *  PUR-11 (D-15 ب) — خرید به ارزِ دیگر: کادرِ «ارز و نرخ» ِ فاکتورِ خرید، حسابِ ارزیِ تأمین‌کننده، پرداخت به ارز یا افغانی و
 *  خطِ «اختلافِ نرخ». قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/purchase/PurchaseFx` و `data/SupplierFx`. کدِ ارز (`USD`)، عدد و خطِ
 *  نرخ («1 USD = 70.25 AFN») از بیرون می‌آیند و همان می‌مانند.
 */
@Suppress("unused")
object StrPurchaseFx {
  /** مبلغ، کد و خطِ نرخِ لاتین چپ‌به‌راستِ جدا (LRI…PDI) تا در جملهٔ راست‌به‌چپ «۱۳۵ USD» از هم نپاشد */
  private fun ltr(s: String): String = "\u2066$s\u2069"

  // ── فاکتورِ خرید ──
  val section: String get() = t("ارز و نرخ", "Currency and rate", "اسعار او نرخ")
  fun rateLabel(per: String, code: String): String =
    t("نرخ — چند افغانی برای ${asIs(ltr("$per $code"))}", "Rate — AFN for $per $code", "نرخ — د ${ltr("$per $code")} لپاره څو افغانۍ")
  val per: String get() = t("نرخ برای", "Rate per", "نرخ د")
  val rateBad: String get() = t("نرخ درست نیست — عددِ بزرگ‌تر از صفر بنویسید", "The rate is not valid — enter a number above zero", "نرخ سم نه دی — له صفر لوی عدد ولیکئ")
  val rateNote: String get() = t(
    "یک نرخ برای همهٔ این فاکتور. قیمت‌ها به همین ارز نوشته می‌شوند؛ بهای کالا در انبار و گزارش‌ها معادلِ افغانی با همین نرخ است.",
    "One rate for the whole invoice. Prices are entered in this currency; the stock cost and reports use the AFN equivalent at this rate.",
    "د ټول بل لپاره یو نرخ. بیې په همدې اسعارو لیکل کېږي؛ په ګدام او راپورونو کې د توکي بیه په همدې نرخ افغانۍ معادل ده.",
  )
  fun priceIn(code: String): String = t("قیمتِ هر واحد (${asIs(code)})", "Unit price ($code)", "د یو واحد بیه ($code)")
  fun totalIn(code: String): String = t("جمعِ خرید (${asIs(code)})", "Purchase total ($code)", "د پېرود ټولګه ($code)")
  val afnEquivalent: String get() = t("معادلِ افغانی", "AFN equivalent", "افغانۍ معادل")
  fun paidIn(code: String): String = t("پرداخت‌شده (${asIs(code)})", "Paid ($code)", "ورکړل شوي ($code)")
  fun paidAll(amount: String): String = t("همهٔ ${asIs(ltr(amount))} پرداخت شد", "All $amount paid", "ټول ${ltr(amount)} ورکړل شول")
  val laterAfnOnly: String get() = t(
    "سفارشِ خرید فقط به افغانی است؛ خریدِ ارزی را با «بله، رسیده» ثبت کنید",
    "Purchase orders are in AFN only; record a foreign-currency purchase as received",
    "د پېرود امر یوازې په افغانیو دی؛ په بل اسعارو پېرود د «هو، رسېدلی» په توګه ثبت کړئ",
  )
  val drawerNote: String get() = t(
    "پرداختِ به ارز از دخلِ افغانی کم نمی‌شود.",
    "A payment made in a foreign currency is not taken from the AFN drawer.",
    "په بل اسعارو تادیه له افغانۍ دخل نه کمېږي.",
  )

  // ── حسابِ تأمین‌کننده ──
  fun owed(amount: String, book: String): String =
    t("بدهی: ${asIs(ltr(amount))} (ارزشِ دفتری ${asIs(ltr(book))})", "We owe: $amount (book value $book)", "پور: ${ltr(amount)} (دفتري ارزښت ${ltr(book)})")
  fun credit(amount: String): String = t("طلبِ ما: ${asIs(ltr(amount))}", "Our credit: $amount", "زموږ پور: ${ltr(amount)}")
  val byCurrency: String get() = t("بدهی به تفکیکِ ارز", "Owed by currency", "د اسعارو له مخې پور")
  val bookValueNote: String get() = t(
    "ارزشِ دفتری = معادلِ افغانی با نرخ‌های ثبت‌شدهٔ خریدها؛ جزوِ بدهیِ افغانیِ بالاست.",
    "Book value = the AFN equivalent at the recorded purchase rates; it is part of the AFN balance above.",
    "دفتري ارزښت = د پېرودونو په ثبت شوو نرخونو افغانۍ معادل؛ د پورته افغانۍ پور برخه ده.",
  )

  // ── پرداخت ──
  val account: String get() = t("حساب", "Account", "حساب")
  val afnAccount: String get() = t("افغانی", "AFN", "افغانۍ")
  val payWith: String get() = t("پرداخت با", "Pay with", "تادیه په")
  fun amountIn(code: String): String = t("مبلغ (${asIs(code)})", "Amount ($code)", "اندازه ($code)")
  fun value(amount: String): String = t("ارزشِ این پرداخت به نرخِ آن: ${asIs(ltr(amount))}", "This payment at its rate: $amount", "دا تادیه په خپل نرخ: ${ltr(amount)}")
  fun settles(amount: String): String = t("از بدهی صاف می‌شود: ${asIs(ltr(amount))}", "Settles from the balance: $amount", "له پور څخه پاکېږي: ${ltr(amount)}")
  fun fxLoss(amount: String): String = t("اختلافِ نرخ: ${asIs(ltr(amount))} زیان", "Exchange difference: $amount loss", "د نرخ توپیر: ${ltr(amount)} تاوان")
  fun fxGain(amount: String): String = t("اختلافِ نرخ: ${asIs(ltr(amount))} سود", "Exchange difference: $amount gain", "د نرخ توپیر: ${ltr(amount)} ګټه")
  val amountBad: String get() = t("مبلغ یا نرخ درست نیست", "The amount or rate is not valid", "اندازه یا نرخ سم نه دی")

  // ── گزارش و فهرست ──
  val currencyCol: String get() = t("ارز", "Currency", "اسعار")
  val fxAmountCol: String get() = t("مبلغ به ارز", "Amount in currency", "اندازه په اسعارو")
  val rateCol: String get() = t("نرخ", "Rate", "نرخ")
  val fxDiffCol: String get() = t("اختلافِ نرخ (افغانی)", "Exchange difference (AFN)", "د نرخ توپیر (افغانۍ)")
  fun rateLine(line: String): String = t("نرخ: ${asIs(ltr(line))}", "Rate: $line", "نرخ: ${ltr(line)}")
  fun docLine(amount: String, line: String): String = t("${asIs(ltr(amount))} · نرخ ${asIs(ltr(line))}", "$amount · rate $line", "${ltr(amount)} · نرخ ${ltr(line)}")
  val boughtIn: String get() = t("خرید به ارزِ دیگر", "Purchases in other currencies", "په نورو اسعارو پېرود")
  val fxDiffReport: String get() = t("اختلافِ نرخِ ارز در پرداخت به تأمین‌کننده", "Exchange difference on supplier payments", "تأمینوونکو ته په تادیه کې د نرخ توپیر")
  val fxDiffReportNote: String get() = t(
    "مثبت = زیان (بیش از ارزشِ دفتری پرداخت شد)، منفی = سود. جزوِ سود و زیانِ فروش نیست.",
    "Positive = loss (paid more than the book value), negative = gain. Not part of the sales profit.",
    "مثبت = تاوان (له دفتري ارزښت زیات ورکړل شو)، منفي = ګټه. د پلور د ګټې برخه نه ده.",
  )

  // ── کلیدِ دکان (تنظیمات ← کالا و انبار؛ `PurchaseFxPref`، پیش‌فرض خاموش) ──
  val settingTitle: String get() = t("خرید به ارزِ دیگر", "Buying in another currency", "په بل اسعارو پېرود")
  val settingOn: String get() = t("فاکتورِ خرید ارز و نرخ می‌گیرد", "Purchase invoices take a currency and rate", "د پېرود بل اسعار او نرخ اخلي")
  val helpLine: String get() = t("فاکتورِ خرید به دالر، کلدار یا ارزِ دیگر", "Purchase invoices in USD, PKR or another currency", "د پېرود بل په ډالرو، کلدارو یا بل اسعارو")
  val helpExample: String get() = t(
    "۱۰۰ دالر جنس به نرخِ ۷۰: بهای کالا ۷٬۰۰۰ افغانی و بدهی به تأمین‌کننده ۱۰۰ دالر.",
    "100 USD of goods at 70: stock cost 7,000 AFN and you owe the supplier 100 USD.",
    "د ۷۰ په نرخ ۱۰۰ ډالر توکي: د توکو بیه ۷٬۰۰۰ افغانۍ او عرضه‌کوونکي ته پور ۱۰۰ ډالر.",
  )
  val settingNote: String get() = t(
    "روشن باشد، در فاکتورِ خرید ارز و نرخ را انتخاب می‌کنید. خاموش باشد، فاکتورِ تازه به افغانی است؛ فاکتور و بدهیِ ارزیِ پیشین همچنان دیده و پرداخت می‌شود.",
    "When on, you choose the currency and rate on a purchase invoice. When off, new invoices are in AFN; earlier foreign-currency invoices and debts still show and can still be paid.",
    "کله چې روښانه وي، د پېرود په بل کې اسعار او نرخ ټاکئ. کله چې بند وي، نوی بل په افغانیو دی؛ پخواني بلونه او په بل اسعارو پورونه لا هم ښکاري او ورکول کېږي.",
  )
  val updateFirst: String get() = t(
    "پیش از روشن کردن، همهٔ دستگاه‌های دکان را به‌روز کنید. دستگاهِ کهنه خریدِ ارزی را افغانی می‌خواند و پرداختِ ارزی را از دخلِ افغانی کم می‌کند.",
    "Before switching on, update every device in the shop. An old device reads a foreign-currency purchase as AFN and takes a foreign-currency payment out of the AFN drawer.",
    "د روښانولو مخکې د دوکان ټول وسایل تازه کړئ. زړه وسیله په بل اسعارو پېرود افغانۍ لولي او په بل اسعارو تادیه د افغانیو له دخل څخه کموي.",
  )
  val managersOnly: String get() = t(
    "فقط صاحب یا مدیرِ دکان این کلید را عوض می‌کند.",
    "Only the shop owner or a manager can change this switch.",
    "یوازې د دوکان خاوند یا مدیر دا کیلي بدلولی شي.",
  )
}
