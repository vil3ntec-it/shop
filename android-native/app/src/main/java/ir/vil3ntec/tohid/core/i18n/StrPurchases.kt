package ir.vil3ntec.tohid.core.i18n

/**
 *  «خریدها» (`claude/e-rows-63b`): فهرستِ همهٔ خریدها، جزئیاتِ هر خرید، صفحهٔ تأمین‌کننده و فرمِ سادهٔ «خریدِ تازه».
 *  قاعده‌ها سرِ `I18n.kt`؛ دری از `DariText`. «پرداخت»، «دریافتِ کالا» و «برگشت به تأمین‌کننده» همان `StrMoney.pay`،
 *  `StrPurchasePages.receiveGoods` و `StrReach.supplierReturn`؛ «امروز»/«این ماه»/«امسال» همان `Str.today` و `StrHistory`.
 */
@Suppress("unused")
object StrPurchases {
  /* ── فهرست ── */
  val tabPurchases: String get() = t("خریدها", "Purchases", "پېرودونه")
  val newPurchase: String get() = t("خریدِ تازه", "New purchase", "نوی پېرود")
  val searchHint: String get() = t("نامِ کالا، بارکد یا تأمین‌کننده", "Item name, barcode or supplier", "د توکي نوم، بارکوډ یا عرضه‌کوونکی")
  val totalBought: String get() = t("جمعِ خرید", "Total bought", "ټول پېرود")
  fun count(n: Any): String = t("${asIs(n)} خرید", "$n ${pl(n, "purchase", "purchases")}", "$n پېرودونه")
  fun leftAll(amount: Any): String = t("${asIs(amount)} ؋ مانده", "$amount ؋ left to pay", "$amount ؋ پاتې")
  fun overdueCount(n: Any): String = t("سررسیدِ ${asIs(n)} خرید گذشته", "$n overdue", "د $n پېرودونو نېټه تېره شوې")
  val emptyTitle: String get() = t("هنوز خریدی ثبت نشده", "No purchases yet", "لا کوم پېرود نه دی ثبت شوی")
  val emptyBody: String get() = t(
    "هر خرید با اقلام و بارکدها، پرداخت و رسیدنش این‌جا دیده می‌شود.",
    "Every purchase shows here with its items and barcodes, payment and delivery.",
    "هر پېرود له توکو، بارکوډونو، ورکړې او رسېدو سره دلته ښکاري.",
  )
  val noneFound: String get() = t("خریدی با این پالایه یا جست‌وجو نیست", "No purchase matches", "هېڅ پېرود سره سمون نه لري")
  /** «۵ قلم» — شمارِ اقلامِ یک خرید */
  fun items(n: Any): String = t("${asIs(n)} قلم", "$n ${pl(n, "item", "items")}", "$n توکي")
  fun orderNo(n: Any): String = t("سفارشِ #${asIs(n)}", "Order #$n", "فرمایش #$n")
  fun fromOrder(n: Any): String = t("از سفارشِ خرید #${asIs(n)}", "From purchase order #$n", "د پېرود فرمایش #$n څخه")

  /* ── حالِ پرداخت ── */
  val paid: String get() = t("پرداخت‌شده", "Paid", "ورکړل شوی")
  fun partly(amount: Any): String = t("نیمه‌پرداخت ${asIs(amount)} ؋ مانده", "Part-paid, $amount ؋ left", "نیمه ورکړل شوی، $amount ؋ پاتې")
  val credit: String get() = t("نسیه", "On credit", "پور")
  fun creditDue(day: Any): String = t("نسیه — سررسید ${asIs(day)}", "On credit — due $day", "پور — نېټه $day")
  fun overdue(amount: Any): String = t("سررسید گذشته — ${asIs(amount)} ؋ مانده", "Overdue — $amount ؋ left", "نېټه تېره — $amount ؋ پاتې")
  /** خطِ مبلغِ ردیف (۶۸c، کوتاه و یک‌خطی): «سررسید گذشته» — مانده کنارش جدا می‌آید */
  val overdueRow: String get() = t("سررسید گذشته", "Overdue", "نېټه تېره")
  /** خطِ مبلغِ ردیف (۶۸c، کوتاه و یک‌خطی): «سررسید ۴ قوس» — نسیه خودش از سررسید پیداست */
  fun dueOn(day: Any): String = t("سررسید ${asIs(day)}", "Due $day", "نېټه $day")
  val returned: String get() = t("برگشت", "Returned", "بېرته")

  /* ── حالِ رسیدن ── */
  val received: String get() = t("رسیده", "Received", "رسېدلی")
  val partReceived: String get() = t("نیمه‌رسیده", "Partly received", "نیمه رسېدلی")
  val notReceived: String get() = t("هنوز نرسیده", "Not received yet", "لا نه دی رسېدلی")

  /* ── پالایه‌ها (یک ردیفِ کادرهای کشویی) ── */
  val allSuppliers: String get() = t("همهٔ تأمین‌کننده‌ها", "All suppliers", "ټول عرضه‌کوونکي")
  val payLabel: String get() = t("پرداخت", "Payment", "ورکړه")
  val payAll: String get() = t("پرداخت: همه", "Payment: all", "ورکړه: ټول")
  val unpaid: String get() = t("پرداخت‌نشده", "Unpaid", "نه ورکړل شوی")
  val overdueFilter: String get() = t("سررسیدگذشته", "Overdue", "نېټه تېره")
  val receiptLabel: String get() = t("رسیدن", "Delivery", "رسېدل")
  val receiptAll: String get() = t("رسیدن: همه", "Delivery: all", "رسېدل: ټول")
  val notReceivedFilter: String get() = t("نرسیده", "Not received", "نه دی رسېدلی")
  val allTime: String get() = t("همهٔ زمان‌ها", "All time", "ټول وخت")

  /* ── جزئیات ── */
  fun title(no: Any): String = t("خریدِ #${asIs(no)}", "Purchase #$no", "پېرود #$no")
  val purchase: String get() = t("خرید", "Purchase", "پېرود")
  val notFound: String get() = t("این خرید پیدا نشد", "Purchase not found", "دا پېرود ونه موندل شو")
  fun lines(n: Any): String = t("اقلام (${asIs(n)})", "Items ($n)", "توکي ($n)")
  fun barcode(code: Any): String = t("بارکد ${asIs(code)}", "Barcode $code", "بارکوډ $code")
  val noCode: String get() = t("بی بارکد", "No barcode", "بې بارکوډه")
  fun batch(b: Any): String = t("سری ${asIs(b)}", "Batch $b", "لړۍ $b")
  fun expires(day: Any): String = t("انقضا ${asIs(day)}", "Expires $day", "پای $day")
  fun receivedOf(received: Any, ordered: Any): String = t("رسیده ${asIs(received)} از ${asIs(ordered)}", "Received $received of $ordered", "$received له $ordered څخه رسېدلي")
  val total: String get() = t("جمع", "Total", "ټول")
  val paidLabel: String get() = t("پرداخت‌شده", "Paid", "ورکړل شوی")
  val remaining: String get() = t("مانده", "Left to pay", "پاتې")
  val landed: String get() = t("هزینهٔ جانبی (روی بها)", "Extra costs (on cost)", "اضافي لګښت (پر بیه)")
  val printShare: String get() = t("چاپ یا اشتراک", "Print or share", "چاپ یا شریکول")
  val openOrder: String get() = t("جزئیاتِ سفارش", "Order details", "د فرمایش جزئیات")
  val pendingNote: String get() = t(
    "کالا هنوز کامل نرسیده؛ «دریافتِ کالا» آن را به انبار می‌برد و بدهی از همان لحظه حساب می‌شود.",
    "Goods are not fully here yet; “Receive goods” puts them in stock and the debt starts then.",
    "توکي لا بشپړ نه دي رسېدلي؛ «د توکو ترلاسه کول» یې ګدام ته وړي او پور له هماغه وخته حسابېږي.",
  )
  val returnNote: String get() = t("برگشت به تأمین‌کننده — از بدهیِ ما کم شد", "Returned to supplier — taken off what we owe", "عرضه‌کوونکي ته بېرته — زموږ له پور کم شو")
  val payments: String get() = t("پرداخت‌ها", "Payments", "ورکړې")
  val noPayments: String get() = t("هنوز پرداختی نشده", "No payments yet", "لا هېڅ ورکړه نه ده شوې")

  /* ── فرمِ «خریدِ تازه» ── */
  val receivedNow: String get() = t("الان رسیده؟", "Received now?", "اوس رسېدلی؟")
  val yesReceived: String get() = t("بله، رسیده", "Yes, it's here", "هو، رسېدلی")
  val laterReceived: String get() = t("نه، بعداً می‌رسد", "No, comes later", "نه، وروسته راځي")
  val laterHow: String get() = t(
    "سفارشِ خرید فرستاده می‌شود؛ با رسیدنِ کالا «دریافتِ کالا» را بزنید تا به انبار برود.",
    "A purchase order is sent; when the goods come, tap “Receive goods” to put them in stock.",
    "د پېرود فرمایش لېږل کېږي؛ کله چې توکي راشي، «د توکو ترلاسه کول» ووهئ چې ګدام ته لاړ شي.",
  )
  val laterOff: String get() = t(
    "کالای نرسیده با «سفارش‌های خرید» ثبت می‌شود که این‌جا روشن نیست یا اینترنت وصل نیست.",
    "Goods that come later are recorded as purchase orders, which are off here or offline.",
    "وروسته راتلونکي توکي د پېرود فرمایش په توګه ثبتېږي چې دلته بند دي یا انټرنېټ نشته.",
  )
  val payFull: String get() = t("پرداختِ کامل", "Paid in full", "بشپړه ورکړه")
  val payPart: String get() = t("بخشی", "Part", "یوه برخه")
  val payLater: String get() = t("بعداً", "Later", "وروسته")
  val prepay: String get() = t("پیش‌پرداخت", "Paid in advance", "مخکې ورکړه")
  fun paidAll(amount: Any): String = t("همهٔ ${asIs(amount)} ؋ پرداخت می‌شود", "All $amount ؋ is paid", "ټول $amount ؋ ورکول کېږي")
  val laterNote: String get() = t("همه نسیه می‌ماند؛ سررسید یادآور می‌شود.", "All on credit; the due date reminds you.", "ټول پور پاتې کېږي؛ نېټه درته یادونه کوي.")
  fun orderSent(no: Any): String = t("سفارشِ #${asIs(no)} فرستاده شد", "Order #$no sent", "فرمایش #$no ولېږل شو")
  fun prepaidNote(no: Any): String = t("پیش‌پرداختِ سفارشِ #${asIs(no)}", "Advance for order #$no", "د فرمایش #$no مخکې ورکړه")
  val orderFailed: String get() = t("سفارش فرستاده نشد؛ چیزی ثبت نشد", "The order was not sent; nothing was saved", "فرمایش ونه لېږل شو؛ هېڅ ثبت نه شو")
  fun orderNotSent(no: Any): String = t(
    "سفارشِ #${asIs(no)} ساخته شد ولی فرستاده نشد؛ «ثبت» را باز بزنید",
    "Order #$no was made but not sent; tap “Save” again",
    "فرمایش #$no جوړ شو خو ونه لېږل شو؛ «ثبت» بیا ووهئ",
  )

  /* ── هشدار ── */
  val dueAlert: String get() = t("سررسیدِ پرداختِ تأمین‌کننده", "Supplier payment due", "د عرضه‌کوونکي د ورکړې نېټه")
  fun dueAlertBody(n: Any, amount: Any): String = t(
    "${asIs(n)} خرید سررسیدش گذشته · ${asIs(amount)} ؋",
    "$n ${pl(n, "purchase", "purchases")} overdue · $amount ؋",
    "$n پېرودونه نېټه تېره · $amount ؋",
  )
}
