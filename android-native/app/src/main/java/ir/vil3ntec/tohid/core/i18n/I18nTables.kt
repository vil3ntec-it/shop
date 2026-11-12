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
    StrTransfer, // INV-11: انتقالِ موجودی با «در مسیر»، دریافتِ ناقص و با اسکن
    Str, StrNav, StrAuth, StrSettings, StrHome, StrSale, StrShell, StrPos, StrExpenses, StrFields,
    StrInvoices, StrSecurity, StrLine, StrLoyalty, StrKit, StrProducts, StrStock, StrMoney, StrReports, StrAccount,
    StrTraining, // OPS-07
    StrSimpleAsk,
    StrEvidence, StrLateApology, StrErasure, // RSK-08، NTF-10، SEC-10: صفحه‌های برنامه برای سه درِ سرور
    StrCustomerAccess, // CRM-15: «چه کسی این پرونده را دید» (سابقهٔ دسترسی)
    StrOnline, StrSearch, StrTouch, StrPick, StrStaffTime, StrShiftReview, StrWhy, StrLocalNet, StrShrink,
    StrPriceCheck, StrChat, StrLabel, StrOrderSlip, StrCount, StrGift, StrRetPolicy, StrDeliver, StrProduct, StrGrid,
    StrRisk, StrPay, StrAdmin, StrShift, StrCourier, StrOrderOps, StrPayFix, StrSpecialOrder, StrSafe, StrGiftCard,
    StrTaxQuarter, StrCustomerCard, StrOrderFeedback, StrLayaway, StrVisualSearch,
    StrTill, // S28b، S45، S46، S50
    StrA11y, // صفحه‌خوان در نسخهٔ ۳: باز/بسته، کارِ نگه‌داشتن، QR ِ پرداخت، «نیاز به توجه»، ضبطِ صدا با زدن، جابه‌جاییِ نوار
    StrRounding, // PAY-09: گردکردنِ نقدی
    StrSaleTax, // POS-37: مالیاتِ فاکتور
    StrFx, // PAY-10: ارزِ نقد در صندوق
    StrSettingsHelp, // تنظیماتِ کم (۱۴۰۵/۰۸/۱۱): خطِ ساده و «این چه می‌کند؟»
    StrSettingsHome, // تنظیماتِ نسخهٔ دوم (۱۴۰۵/۰۸/۱۱): جست‌وجو، شش دسته و «قابلیت‌ها»
    StrDevices, // «دستگاه‌ها» و اسکن در پس‌زمینه (۱۴۰۵/۰۸/۱۱)
    StrPayStatus, // PAY-07: وضعیتِ پرداخت
    StrQuickRegister, // S03/POS-17: ثبتِ سریعِ کالا در صندوقِ گوشیِ نسخهٔ ۳
    StrReach, // درهای نسخهٔ ۳ به کارهای بی‌راه‌مانده پس از رابطِ قدیم (`V3ReachabilityTest`)
    StrMarketRegion, // S109: ولایت و ولسوالیِ دکان در «نمایش در بازارچه»
    StrCourierArea, // S83: محدودهٔ خدمتِ هر پیک (فرمِ پیک، کارت و سپردنِ دستی)
    StrProductSuggest, // AI-06: پیشنهادِ دسته و نام در فرمِ کالا
    StrOnlineReport, // RPT-14: گزارشِ فروشِ اینترنتی در برنامهٔ مدیریت
    StrBranches, // RPT-18: گزارشِ همهٔ شعبه‌ها
    StrImageChanges, // IMG-PRO-17/16: تغییرِ دسته‌ایِ عکس‌ها و نسخه‌های عکس
    StrImageStudio, // ۴-۲۳ (۱): استودیوی عکسِ سرور — دسته‌ها و تأییدِ نسخه
    StrRefundSlip, // E2E-RET-11: رسیدِ اصلاحیِ بازپرداختِ سفارشِ اینترنتی (چاپ و صفحهٔ سفارش)
    StrProductGallery, // PRD-17/27: عکس‌های تکمیلیِ کالا و عکسِ جدای ویترین
    StrPromoHeld, // PRM-02: آزادِ دستیِ کدِ تخفیفِ نگه‌داشته
    StrDrawers, // PAY-17: چند کشو و شیفتِ شناور
    StrSupplierBills, // PUR-14: فاکتورهای تأمین‌کننده در گوشیِ نسخهٔ ۳
    StrPurchaseOrders, // PUR-02/03/04/14: سفارش‌های خرید در گوشیِ نسخهٔ ۳ (فهرست، فرم، دریافت با اسکن، تطبیق)
    StrPurchaseUnits, // PRD-07: واحدهای خریدِ چندگانه در فرمِ کالا، فاکتورِ خرید و سفارشِ خرید
    StrHistory, // «تاریخچه» ِ روز/ماه/سال و چاپِ گزارشِ هر دوره
    StrCourierApp, // حالتِ پیک (`ui/courier`) — پیش‌تر متنِ خام، بی هیچ زبانی (LOC-08 دری)
    StrSettingsPages, // صفحه‌های تنظیماتِ نسخهٔ ۳ با متنِ خام: گزارشِ صاحب، زمانِ تحویل، اولین خرید، موتورِ موجودی، یادداشتِ صندوق
    StrOrdersHub, // «سفارش‌های آنلاین» ِ خانه، صف و گفت‌وگوی هر سفارش
    StrOrderFlow, // «چرا سفارش نمی‌رسد؟»، «پرداخت پس از پذیرش» و «به سبد / فاکتور» ِ سفارشِ آنلاین
    StrStorePlace, // نشانی و پینِ دکان برای مشتریِ «دریافت از دکان» (ممیزیِ ویترین، مهاجرتِ ۲۱۵)
    StrStoreStock, // WEB-09: «کالای ناموجود را در ویترین پنهان کن» (مهاجرتِ ۲۴۰)
    StrPush, // پوشِ گوشی (Firebase، O-01): حالِ «سفارشِ آنلاین»، «اجازهٔ اعلان» و نامِ کانال‌های اعلان
    StrTillPay, // نسیه فقط با مشتری؛ حساب‌پی با NFC/QR در صندوقِ گوشیِ نسخهٔ ۳ (PAY-21)
    StrHero, // HOME-V3: کادرِ بالای خانه (صندوق/گاوصندوق، نمودار و «+»)
    StrRemoteApproval, // STF-02: «پرسیدن از مدیر» در صندوق و «درخواست‌های تأیید» ِ صاحب/مدیر
    StrBackupSafety, // OPS-02: بازیابیِ آزموده (پشتیبانِ ناقص/تازه‌تر/دکانِ دیگر)، رمزِ نسخه‌های خودکار و صفحهٔ «عبارتِ بازیابی»
    StrConflictInbox, // صندوقِ سندهای ردشده ِ صاحب/مدیر و نشانِ «سرور نپذیرفت» روی فاکتورِ دستگاهِ سازنده
    StrSaleCaps, // STF-03: پیش‌سنجیِ سقفِ قرض، تغییرِ قیمتِ ردیف و «تخفیفِ پنهان» در صندوق (`SaleCapGate`)
    StrCustomerPrices, // POS-18: «قیمتِ خاصِ این مشتری» (پروندهٔ مشتری، صندوق، رسید و تنظیم)
    StrLineSeller, // POS-23/STF-08: فروشندهٔ هر ردیف (پایهٔ پورسانت)
    StrSalePages, StrReturnPages, StrDebtPages, StrOrderPages, StrShellPages, // صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ صدا می‌زند — پیش‌تر متنِ خام (LOC-08 دری، دورِ ۲)
    StrCustomerPages, StrStockPages, StrDevicePages, StrProductPages, StrPurchasePages, StrPromoPages, StrPayPages, StrReportPages, // همان، دورِ ۳
    StrSetupPages, StrImagePages, StrMarketPages, StrCatalogPages, // همان، دورِ ۴
    StrScanPages, StrPanelPages, // همان، دورِ ۵ (صفحه‌های زندهٔ کوچک)
    StrDebtor, // دورِ ۶: حسابِ قرض‌دار (پیش‌تر `t` ِ فقط‌فارسیِ محلی، بی هیچ زبانی)
    StrLedger, StrGoods, StrAppData, // دورِ ۷: پیام‌های لایهٔ دفتر (`data/`) و صفحه‌های زندهٔ قدیمی — پیش‌تر متنِ خام
    StrSync, StrPrint, StrUpdate, // دورِ ۷: پیام‌های همگام‌سازی، چاپ و به‌روزرسانی — پیش‌تر متنِ خام
    StrReceipt, // #348: برچسب‌های فاکتور، رسیدِ هدیه و صورت‌حسابِ چاپی به هر چهار زبان (پیش‌تر فقط فارسی/دری)
    StrDesktop, // LOC-06: منوی سینی و پنجرهٔ فایلِ کامپیوتر — ترجمه‌ها رونوشتِ همان جملهٔ جدولِ دیگر (`DesktopTextTest`)
  )
}
