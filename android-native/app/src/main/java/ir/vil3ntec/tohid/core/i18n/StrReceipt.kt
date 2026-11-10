package ir.vil3ntec.tohid.core.i18n

/**
 *  برچسب‌های فاکتور، رسیدِ هدیه و صورت‌حسابِ چاپی (#348) — کاغذ، PNG، PDF و صفحهٔ فاکتور (`print/Receipts.kt`).
 *
 *  فارسیِ هر کلید بایت‌به‌بایت همان `ReceiptLabels.FA`، `GiftReceiptLayout.Labels.FA` و `StatementLabels.FA` ِ هسته است
 *  (آزمون: `ReceiptLangTest`)؛ دری همان فارسی از لایهٔ واژه (`t`، همان دورِ ۷)؛ انگلیسی و پشتو ترجمه. هیچ جای خالی‌ای
 *  ندارد: دادهٔ فاکتور (نامِ کالا، دکان، مشتری، یادداشت، نامِ روشی که دکان ساخته) هرگز از این جدول نمی‌گذرد.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrReceipt {

  /* ------------------------------ فاکتور (`ReceiptLabels`) ------------------------------ */

  val fallbackShop: String get() = t("فروشگاه", "Shop", "دوکان")
  val invoiceNo: String get() = t("شمارهٔ فاکتور", "Invoice no.", "د بل شمېره")
  val date: String get() = t("تاریخ", "Date", "نېټه")
  val lunarDate: String get() = t("تاریخِ قمری", "Lunar date", "قمري نېټه")
  val cashier: String get() = t("فروشنده", "Cashier", "پلورونکی")
  val kind: String get() = t("نوع", "Type", "ډول")
  val cancelled: String get() = t("لغو شد", "Cancelled", "لغوه شو")
  val returned: String get() = t("مرجوعی", "Returned", "بېرته راګرځېدلي")
  val listPrice: String get() = t("قیمتِ اصلی", "List price", "اصلي بیه")
  val count: String get() = t("تعداد اقلام", "Items", "د توکو شمېر")
  /** «تعداد اقلام: ۲ ردیف» و «۵ ردیف» ِ صورت‌حساب */
  val rows: String get() = t("ردیف", "lines", "قطارونه")
  /** همان، برای «۱» */
  val rowOne: String get() = t("ردیف", "line", "قطار")
  val pieces: String get() = t("عدد", "pcs", "دانې")
  /** همان، برای «۱» */
  val pieceOne: String get() = t("عدد", "pc", "دانه")
  val subtotal: String get() = t("جمع اقلام", "Items total", "د توکو ټولګه")
  val discount: String get() = t("تخفیف", "Discount", "تخفیف")
  val total: String get() = t("مبلغ نهایی", "Total", "وروستی مبلغ")
  val paid: String get() = t("پرداختی", "Paid", "ورکړل شوي")
  val tendered: String get() = t("دریافتی", "Received", "ترلاسه شوې")
  val change: String get() = t("پولِ پس", "Change", "پاتې پیسې")
  val remaining: String get() = t("باقی‌مانده (نسیه)", "Balance due (credit)", "پاتې (پور)")
  val debtor: String get() = t("قرض‌دار", "Debtor", "پوروړی")
  val phone: String get() = t("تلفن", "Phone", "ټیلیفون")
  val scan: String get() = t("برای دیدنِ این خرید اسکن کنید", "Scan to view this purchase", "د دې پېرود لیدو لپاره سکن کړئ")
  val storeScan: String get() = t("سفارشِ اینترنتی از همین دکان — اسکن کنید", "Order online from this shop — scan", "له همدې دوکان څخه آنلاین فرمایش — سکن کړئ")
  val footer: String get() = t("تشکر از خرید شما", "Thank you for your purchase", "له پېرود څخه مو مننه")
  val cash: String get() = t("نقدی", "Cash", "نغد")
  val credit: String get() = t("نسیه", "Credit", "پور")
  val online: String get() = t("آنلاین", "Online", "آنلاین")
  val note: String get() = t("یادداشت", "Note", "یادښت")
  val rounding: String get() = t("گردکردنِ نقدی", "Cash rounding", "د نغدو ګردول")

  /* ------------------------------ رسیدِ هدیه (POS-34) ------------------------------ */

  val giftTitle: String get() = t("رسیدِ هدیه", "Gift receipt", "د ډالۍ رسید")
  val giftNote: String get() = t("این رسید برای تعویض است و قیمت ندارد", "This receipt is for exchange and shows no prices", "دا رسید د بدلولو لپاره دی او بیه نه لري")

  /* ------------------------------ صورت‌حسابِ مشتری (CRM-05) ------------------------------ */

  val statementTitle: String get() = t("صورت‌حسابِ مشتری", "Customer statement", "د پېرودونکي حساب")
  val customer: String get() = t("مشتری", "Customer", "پېرودونکی")
  val give: String get() = t("برد", "Given", "ورکړه")
  val receive: String get() = t("رسید", "Received", "ترلاسه شوې")
  val balance: String get() = t("مانده", "Balance", "پاتې")
  val totalGive: String get() = t("جمعِ برد", "Total given", "ټوله ورکړه")
  val totalReceive: String get() = t("جمعِ رسید", "Total received", "ټول ترلاسه شوي")
  /** حالِ ماندهٔ پایانی، درونِ پرانتزِ «مانده (…)» */
  val owes: String get() = t("بدهکار", "owing", "پوروړی")
  val inCredit: String get() = t("طلبکار", "in credit", "طلب لري")
  val settled: String get() = t("صاف", "settled", "تصفیه")
  val empty: String get() = t("هنوز ردیفی نیست", "No entries yet", "لا کوم قطار نشته")
  val statementFooter: String get() = t("این صورت‌حساب از دفترِ دکان ساخته شده است", "This statement was made from the shop's ledger", "دا حساب د دوکان له دفتر څخه جوړ شوی دی")

  /*
   *  نامِ روشِ پرداخت **فقط روی کاغذ** — همان واژه‌های ثابتِ برنامه (`PayFlow.KIND_LABELS`، `Pay.DEFAULT_METHODS`،
   *  «به حسابِ مشتری»). خودِ آن جدول‌ها داده‌اند (هم‌شکلِ `report-core.js` و ستونِ خروجی) و دست نمی‌خورند؛ نگاشت در
   *  `Receipts.payName` است و هر نامِ دیگر (روشی که دکان ساخته، شناسهٔ ناشناخته) همان‌طور که هست می‌ماند.
   */

  val payCash: String get() = t("نقد", "Cash", "نغد")
  val payCredit: String get() = t("نسیه", "Credit", "پور")
  val payCard: String get() = t("کارت", "Card", "کارت")
  val payTransfer: String get() = t("حواله", "Transfer", "حواله")
  val payWallet: String get() = t("کیف پول", "Wallet", "بټوه")
  val payStoreCredit: String get() = t("اعتبار فروشگاه", "Store credit", "د دوکان اعتبار")
  val payGiftCard: String get() = t("کارت هدیه", "Gift card", "د ډالۍ کارت")
  val payOther: String get() = t("سایر", "Other", "نور")
  val payMixed: String get() = t("ترکیبی", "Mixed", "ګډ")
  val payOnline: String get() = t("پرداختِ آنلاین", "Online payment", "آنلاین تادیه")
  val payCod: String get() = t("نقد هنگامِ تحویل", "Cash on delivery", "د سپارلو پر وخت نغدې")
  val payAccount: String get() = t("به حسابِ مشتری", "To customer account", "د پېرودونکي حساب ته")
  val payAfPayCard: String get() = t("کارت AfPay", "AfPay card", "د AfPay کارت")
  /** PAY-10 — ردیفِ ارزِ نقد (ریزِ ارز و نرخ کنارش، رقم و کدِ لاتین) */
  val payFx: String get() = t("ارز", "Foreign cash", "بهرنۍ پیسې")
}
