package ir.vil3ntec.tohid.core.i18n

/**
 *  فهرستِ همهٔ جدول‌های نوشته — برای آزمونِ پوشش (`I18nCoverageTest`).
 *  جدولِ تازه (`object StrX`) این‌جا هم اضافه شود؛ آزمون جاافتاده را می‌گیرد.
 *
 *  بازسازیِ VILL3N: هر گروه جدولِ خودش را دارد (`ORDER.md`)؛ جدول‌های گروه‌ها و `StrKit` (واژه‌های کیتِ
 *  نسخهٔ ۳) هم این‌جا ثبت‌اند تا آزمونِ پوشش (`I18nCoverageTest`) آن‌ها را هم بشمارد.
 */
object I18nTables {
  val all: List<Any> get() = listOf(
    StrPlaces, // INV-20/21
    Str, StrNav, StrAuth, StrSettings, StrHome, StrSale, StrShell, StrPos, StrExpenses, StrFields,
    StrInvoices, StrSecurity, StrLine, StrLoyalty, StrKit, StrProducts, StrStock, StrMoney, StrReports, StrAccount,
    StrTraining, // OPS-07
    StrSimpleAsk,
    StrEvidence, StrLateApology, StrErasure, // RSK-08، NTF-10، SEC-10: صفحه‌های برنامه برای سه درِ سرور
    StrOnline, StrSearch, StrTouch, StrPick, StrStaffTime, StrShiftReview, StrWhy, StrLocalNet, StrShrink,
    StrPriceCheck, StrChat, StrLabel, StrOrderSlip, StrCount, StrGift, StrRetPolicy, StrDeliver, StrProduct, StrGrid,
    StrRisk, StrPay, StrAdmin, StrShift, StrCourier, StrOrderOps, StrPayFix, StrSpecialOrder, StrSafe, StrGiftCard,
    StrTaxQuarter, StrCustomerCard, StrOrderFeedback, StrLayaway, StrVisualSearch,
    StrTill, // S28b، S45، S46، S50
    StrRounding, // PAY-09: گردکردنِ نقدی
    StrSettingsHelp, // تنظیماتِ کم (۱۴۰۵/۰۸/۱۱): خطِ ساده و «این چه می‌کند؟»
    StrSettingsHome, // تنظیماتِ نسخهٔ دوم (۱۴۰۵/۰۸/۱۱): جست‌وجو، شش دسته و «قابلیت‌ها»
    StrDevices, // «دستگاه‌ها» و اسکن در پس‌زمینه (۱۴۰۵/۰۸/۱۱)
    StrPayStatus, // PAY-07: وضعیتِ پرداخت
    StrQuickRegister, // S03/POS-17: ثبتِ سریعِ کالا در صندوقِ گوشیِ نسخهٔ ۳
    StrReach, // درهای نسخهٔ ۳ به کارهای بی‌راه‌مانده پس از رابطِ قدیم (`V3ReachabilityTest`)
    StrMarketRegion, // S109: ولایت و ولسوالیِ دکان در «نمایش در بازارچه»
    StrProductSuggest, // AI-06: پیشنهادِ دسته و نام در فرمِ کالا
    StrOnlineReport, // RPT-14: گزارشِ فروشِ اینترنتی در برنامهٔ مدیریت
    StrBranches, // RPT-18: گزارشِ همهٔ شعبه‌ها
    StrImageChanges, // IMG-PRO-17/16: تغییرِ دسته‌ایِ عکس‌ها و نسخه‌های عکس
    StrDrawers, // PAY-17: چند کشو و شیفتِ شناور
    StrSupplierBills, // PUR-14: فاکتورهای تأمین‌کننده در گوشیِ نسخهٔ ۳
    StrHistory, // «تاریخچه» ِ روز/ماه/سال و چاپِ گزارشِ هر دوره
    StrCourierApp, // حالتِ پیک (`ui/courier`) — پیش‌تر متنِ خام، بی هیچ زبانی (LOC-08 دری)
    StrSettingsPages, // صفحه‌های تنظیماتِ نسخهٔ ۳ با متنِ خام: گزارشِ صاحب، زمانِ تحویل، اولین خرید، موتورِ موجودی، یادداشتِ صندوق
    StrOrdersHub, // «سفارش‌های آنلاین» ِ خانه، صف و گفت‌وگوی هر سفارش
    StrOrderFlow, // «چرا سفارش نمی‌رسد؟»، «پرداخت پس از پذیرش» و «به سبد / فاکتور» ِ سفارشِ آنلاین
    StrPush, // پوشِ گوشی (Firebase، O-01): حالِ «سفارشِ آنلاین»، «اجازهٔ اعلان» و نامِ کانال‌های اعلان
    StrTillPay, // نسیه فقط با مشتری؛ حساب‌پی با NFC/QR در صندوقِ گوشیِ نسخهٔ ۳ (PAY-21)
    StrHero, // HOME-V3: کادرِ بالای خانه (صندوق/گاوصندوق، نمودار و «+»)
    StrSalePages, StrReturnPages, StrDebtPages, StrOrderPages, StrShellPages, // صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ صدا می‌زند — پیش‌تر متنِ خام (LOC-08 دری، دورِ ۲)
    StrCustomerPages, StrStockPages, StrDevicePages, StrProductPages, StrPurchasePages, StrPromoPages, StrPayPages, StrReportPages, // همان، دورِ ۳
  )
}
