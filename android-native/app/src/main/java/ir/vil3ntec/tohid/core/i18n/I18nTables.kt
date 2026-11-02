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
  )
}
