package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPayFix
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell

/**
 *  فهرستِ بخش‌های تنظیمات (VILL3N، G9) — خالص، بی Compose.
 *
 *  هر بخش (کلیدش همان عنوانِ پیشینِ بخش است و عوض نمی‌شود) دقیقاً در یک گروه
 *  است، نامِ نشانیِ خودش را دارد (`settings/<گروه>/<بخش>`) و صفحه‌اش در
 *  `ui/v3/settings/SettingsPages.kt` است. ردیفِ گروه در «بیشتر» یک‌راست صفحهٔ
 *  همان گروه را باز می‌کند و ردیفِ بخش صفحهٔ همان بخش را — بی پیمایشِ ریشه.
 *  `SettingsLayoutTest` همین را از رویِ **متنِ** این فایل و `SettingsPages.kt`
 *  می‌سنجد: بخشی بی گروه، بی نشانی یا بی صفحه آزمون را قرمز می‌کند.
 *
 *  ردیف‌های «پیوند» (`LINKS`) بخشِ تنظیمات نیستند: صفحهٔ دیگری از برنامه را باز
 *  می‌کنند (چاپگرها و دستگاه‌های جانبی).
 */
object SettingsLayout {
  data class Group(val title: String, val sections: List<String>)

  const val GENERAL = "عمومی"
  const val SECURITY = "امنیت"
  const val SALES = "فروش و پرداخت"
  const val DEVICES = "دستگاه‌ها"
  const val DATA = "داده‌ها"
  const val ABOUT = "درباره"

  /**
   *  بخشِ دوربین. ⚠️ کامپیوتر این عبارت را در ساخت به «بارکدخوان» برمی‌گرداند
   *  (جدولِ `phrases`ِ `desktop/build.gradle.kts`)؛ آزمون‌ها از همین ثابت می‌خوانند.
   */
  const val CAMERA = "دوربین بارکدخوان"

  /** ویرایشگرِ نوارِ پایین (فقط گوشی) */
  const val NAV_BAR = "نوار پایین"
  /** پیوند به چاپگرها در صفحهٔ «دستگاه‌های جانبی» */
  const val PRINTER = "چاپگر و رسید"
  /** پیوند به خودِ صفحهٔ «دستگاه‌های جانبی» */
  const val PERIPHERALS = "دستگاه‌های جانبی"
  const val ABOUT_APP = "درباره برنامه"

  /*
   *  بخش‌هایی که از تنظیماتِ رابطِ امروز (`ui/legacy/SettingsLegacy.kt`) به نسخهٔ ۳ آمدند — همان کلیدها و
   *  همان ذخیره (`SettingsPortTest` می‌سنجد هیچ تنظیمِ رابطِ امروز در نسخهٔ ۳ بی‌جا نمانده است).
   */
  /** رمزگذاریِ دفتر روی همین دستگاه (SEC-01، `AtRest`) */
  const val ENCRYPTION = "رمزگذاری دفتر"
  /** قاعده‌های پیش از پایانِ فاکتور (POS-24، `CheckoutRulesPref`) */
  const val CHECKOUT_RULES = "قاعده‌های فاکتور"
  /** چند فاکتورِ هم‌زمان (POS-12، `HeldInvoicePrefs`) */
  const val HELD = "فاکتورهای هم‌زمان"
  /** برگشت و تعویض (RET، `ReturnSettings`) — همان `RETURN_SETTINGS_TITLE` */
  const val RETURNS = "برگشت و تعویض"
  /** پرداخت و صندوق (PAY، `ShopPolicy.payV2`) */
  const val PAY = "پرداخت و صندوق"
  /** سقفِ اعتبار در صندوق (CRM-03، `ShopPolicy.creditGate`) */
  const val CREDIT_GATE = "سقف اعتبار"
  /** گزارش‌های مشتری (CRM-04/06/10/14، `ShopPolicy.crmReports`) — فقط کامپیوتر */
  const val CRM_REPORTS = "گزارش‌های مشتری"
  /** دریافتِ وجه یا قسط از مشتری در صندوق (POS-39، `TillReceivePref`) */
  const val TILL_RECEIVE = "دریافت از مشتری"
  /** موجودی و بها (`StockActions.setMode`، پشتِ پرچمِ سرورِ `shop.stock_v2`) — همان `STOCK_SETTINGS_TITLE` */
  const val STOCK_MODE = "موجودی و بها"
  /** تحلیلِ موجودی (INV-15/16، `ShopPolicy.stockInsight`) */
  const val STOCK_INSIGHT = "تحلیل موجودی"
  /** نقطهٔ سفارش و بیشینه (INV-12، `ShopPolicy.stockLevels`) */
  const val STOCK_LEVELS = "نقطه سفارش"
  /** ابزارهای تازهٔ کالا (PRD-19/04، `ShopPolicy.productTools`) */
  const val PRODUCT_TOOLS = "ابزارهای کالا"
  /** ردِ حذف از فاکتور (RPT-09، `FraudTrailPref`) — فقط همین دستگاه */
  const val FRAUD_TRAIL = "سابقه ضدتقلب"
  /** پیشرفت و جای دستگاه در گفت‌وگو (`ShopPolicy.chatMediaGuard`) */
  const val CHAT_GUARD = "جای گفت‌وگو"
  /** اصلاحِ روشِ پرداخت پس از فروش (POS-40، `PayFixPref`) — همان عنوانِ `PayFixSettingsWired` */
  const val PAY_FIX = "اصلاحِ روشِ پرداخت پس از فروش"
  /** لیستِ قیمت در صندوق (POS-18، `PriceListPrefs`) — همان `PRICE_LIST_SETTINGS_TITLE` */
  const val PRICE_LIST = "لیستِ قیمت در صندوق (خرده/عمده)"
  /** پیش‌فاکتور (POS-25، `QuotePref`) — همان عنوانِ `QuoteSettingsWired` */
  const val QUOTE = "پیش‌فاکتور (قیمت‌دهی)"
  /** کارتِ هدیه و اعتبارِ فروشگاه (PAY-24، `GiftCardPref`) — همان عنوانِ `GiftCardSettingsWired` */
  const val GIFT_CARD = "کارتِ هدیه و اعتبارِ فروشگاه"
  /** فروشِ امانی و قسطی (POS-27، `LayawayPref`) */
  const val LAYAWAY = "فروشِ امانی و قسطی"
  /** امتیازِ وفاداری و سطحِ مشتری (PRM-13، `LoyaltyPref`) */
  const val LOYALTY = "امتیازِ وفاداری و سطحِ مشتری"
  /** گاوصندوق (PAY-18، `SafeBookPref`) */
  const val SAFE_BOOK = "گاوصندوق"
  /** گزارشِ مالیاتِ سه‌ماهه (RPT-10، `TaxQuarterPref`) */
  const val TAX_QUARTER = "گزارشِ مالیاتِ سه‌ماهه"
  /** کارت یا بارکدِ مشتری (CRM-16، `CustomerCardPref`) */
  const val CUSTOMER_CARD = "کارت یا بارکدِ مشتری"
  /** محل‌ها و بسته‌بندیِ دوباره (INV-20/21، `StockPlacesPref`) — همان `STOCK_PLACES_TITLE` */
  const val STOCK_PLACES = "محل‌ها و بسته‌بندیِ دوباره"
  /** پیش‌بینیِ تقاضا (AI-07، `SmartForecastPref`) — همان `SMART_FORECAST_TITLE` */
  const val SMART_FORECAST = "پیش‌بینیِ تقاضا (آزمایشی)"
  /** تشخیصِ ناهنجاری (AI-08، `AnomalyPref`) — همان `ANOMALY_TITLE` */
  const val ANOMALY = "تشخیصِ ناهنجاری (آزمایشی)"
  /** گزارشِ خودکار برای صاحب (PAY-20/RPT-12، `OwnerReportLive`، پرچمِ سرورِ `shop.owner_report`) — همان `OWNER_REPORT_TITLE` */
  const val OWNER_REPORT = "گزارشِ خودکار برای صاحب"
  /** زمانِ تقریبیِ تحویلِ ویترین (WEB-04، `StorefrontEtaLive`، پرچمِ سرورِ `shop.storefront.eta`) — همان `STOREFRONT_ETA_TITLE` */
  const val STOREFRONT_ETA = "زمانِ تقریبیِ تحویلِ ویترین"
  /** اولین خرید و سدِ تخفیف (MKT-02/RSK-09، `PromoGuardLive`، پرچمِ سرورِ `shop.storefront.promo_guard`) — همان `PROMO_GUARD_TITLE` */
  const val PROMO_GUARD = "تخفیفِ اولین خرید و سدِ سوءاستفاده"
  /** رسیدِ هدیهٔ بی قیمت (POS-34، `GiftReceiptPrefs`) */
  const val GIFT_RECEIPT = "رسیدِ هدیه"
  /** فروش با تحویلِ بعدی (POS-38، `DeliverLaterPrefs`) */
  const val DELIVER_LATER = "فروش با تحویلِ بعدی"
  /** سفارشِ ویژه با پیش‌پرداخت (POS-26، `SpecialOrderPref`) */
  const val SPECIAL_ORDER = "سفارشِ ویژه با پیش‌پرداخت"
  /** «چرا این قیمت؟» (POS-22، `PriceWhyPrefs`) */
  const val PRICE_WHY = "«چرا این قیمت؟» در صندوق"
  /** گزارشِ ضایعات و کسری (INV-17، `ShrinkReportPref`) */
  const val SHRINK_REPORT = "گزارشِ ضایعات و کسری"
  /** انبارگردانیِ تازه (INV-06/07/08، `CountToolsPref`) */
  const val COUNT_TOOLS = "انبارگردانیِ تازه"
  /** برچسب و بارکدِ داخلی (PRD-23/PRD-05، `LabelToolsPref`) */
  const val LABEL_TOOLS = "برچسب و بارکدِ داخلی"
  /** کد، نام‌های دیگر و محلِ قفسه (PRD-02/PRD-26، `ProductInfoPrefs`) */
  const val PRODUCT_INFO = "کد، نام‌های دیگر و محلِ قفسه"
  /** جست‌وجوی مقاومِ صندوق (POS-04، `PosSearchPrefs`) — فقط کامپیوتر */
  const val POS_SEARCH = "جست‌وجوی مقاومِ صندوق"
  /** قیمت‌پرسی در صندوق (POS-15، `PosPriceCheckPrefs`) — فقط کامپیوتر */
  const val POS_PRICE_CHECK = "قیمت‌پرسی در صندوق (Ctrl+I)"
  /** حالتِ لمسیِ صندوق (POS-31، `PosTouchPrefs`) — فقط کامپیوتر */
  const val POS_TOUCH = "حالتِ لمسیِ صندوق (کامپیوتر)"
  /** کلیدهای بیشترِ صندوق (`PosMoreKeysPrefs`) — فقط کامپیوتر */
  const val POS_MORE_KEYS = "کلیدهای بیشترِ صندوق"
  /** کلیدهای دوره در گزارش‌ها (RPT-13، `ReportKeysPref`) — فقط کامپیوتر */
  const val REPORT_KEYS = "کلیدهای دوره در گزارش‌ها"
  /** شماره‌های جاافتادهٔ اسناد (RPT-17، `DocGapsPref`) — فقط کامپیوتر */
  const val DOC_GAPS = "شماره‌های جاافتادهٔ اسناد"
  /** قیمت و تخفیفِ ردیف (POS-08/10/20/21، `LinePolicyPrefs`) */
  const val LINE_PRICING = "قیمت و تخفیفِ ردیف"
  /** یادداشتِ ردیف و فاکتور (POS-11، `PosNotesPrefs`) */
  const val POS_NOTES = "یادداشتِ ردیف و فاکتور"
  /** هزینهٔ خودکارِ فاکتور (POS-36، `AutoFeesPref`) */
  const val AUTO_FEES = "هزینهٔ خودکار"
  /** پرسشِ ساده از گزارش‌ها (RPT-16، `SimpleAskPref`) */
  const val SIMPLE_ASK = "پرسشِ ساده از گزارش‌ها"
  /** راه‌اندازیِ آسانِ دستگاه‌ها (HW-12، `EasySetupPref`) — همان `EASY_SETUP_TITLE` */
  const val EASY_SETUP = "راه‌اندازیِ آسانِ چاپگر و بارکدخوان (آزمایشی)"
  /** حالتِ آموزشی (OPS-07، `TrainingPref` و `TrainingLedger`) — همان `TRAINING_SETTINGS_TITLE` */
  const val TRAINING = "حالتِ آموزشی"

  /** ردیفی که صفحهٔ دیگری از برنامه را باز می‌کند ⇐ مسیرِ همان صفحه */
  val LINKS: Map<String, String> = mapOf(
    PRINTER to "devices?tab=printers",
    PERIPHERALS to "devices",
  )

  val GROUPS: List<Group> = listOf(
    Group(GENERAL, listOf("هشدارها", "ظاهر", NAV_BAR, "زبان", "تاریخ و رقم", TRAINING)),
    Group(SECURITY, listOf("قفل برنامه", ENCRYPTION, "کد شاگرد", "دستگاه‌های وارد شده", "تغییر رمز حساب", FRAUD_TRAIL, ANOMALY)),
    Group(
      SALES,
      listOf(
        "کالای منقضی", CHECKOUT_RULES, HELD, RETURNS, PAY, PAY_FIX, TILL_RECEIVE, POS_SEARCH, POS_PRICE_CHECK, POS_TOUCH,
        POS_MORE_KEYS, GIFT_CARD, LAYAWAY, SAFE_BOOK, CREDIT_GATE, LOYALTY, CUSTOMER_CARD, CRM_REPORTS, SIMPLE_ASK, REPORT_KEYS,
        DOC_GAPS, TAX_QUARTER, OWNER_REPORT, LINE_PRICING, PRICE_LIST, PRICE_WHY, POS_NOTES, AUTO_FEES, QUOTE,
        SPECIAL_ORDER, DELIVER_LATER, GIFT_RECEIPT, STOCK_MODE, STOCK_INSIGHT, STOCK_LEVELS, SMART_FORECAST,
        STOCK_PLACES, COUNT_TOOLS, SHRINK_REPORT, PRODUCT_TOOLS, PRODUCT_INFO, LABEL_TOOLS,
        STOREFRONT_ETA, PROMO_GUARD, PRINTER,
      ),
    ),
    Group(DEVICES, listOf(CAMERA, EASY_SETUP, PERIPHERALS)),
    Group(DATA, listOf("همگام‌سازی", "داده‌ها", CHAT_GUARD)),
    Group(ABOUT, listOf(ABOUT_APP)),
  )

  /** نامِ نشانیِ هر گروه — ثابت (`vill3n://app/settings/security`) */
  val GROUP_SLUGS: Map<String, String> = mapOf(
    GENERAL to "general",
    SECURITY to "security",
    SALES to "sales",
    DEVICES to "devices",
    DATA to "data",
    ABOUT to "about",
  )

  /** نامِ نشانیِ هر بخش زیرِ گروهِ خودش — ثابت (`settings/security/app-lock`) */
  val SECTION_SLUGS: Map<String, String> = mapOf(
    "هشدارها" to "alerts",
    "ظاهر" to "appearance",
    NAV_BAR to "navbar",
    "زبان" to "language",
    "تاریخ و رقم" to "digits",
    "قفل برنامه" to "app-lock",
    ENCRYPTION to "encryption",
    "کد شاگرد" to "staff-code",
    "دستگاه‌های وارد شده" to "sessions",
    "تغییر رمز حساب" to "password",
    "کالای منقضی" to "expiry",
    CHECKOUT_RULES to "checkout-rules",
    HELD to "held-invoices",
    RETURNS to "returns",
    PAY to "payments",
    PAY_FIX to "payment-fix",
    CREDIT_GATE to "credit-limit",
    CRM_REPORTS to "customer-reports",
    PRICE_LIST to "price-lists",
    QUOTE to "quotes",
    GIFT_CARD to "gift-cards",
    LAYAWAY to "layaway",
    LOYALTY to "loyalty",
    SAFE_BOOK to "safe",
    TAX_QUARTER to "tax-quarter",
    CUSTOMER_CARD to "customer-cards",
    STOCK_PLACES to "stock-places",
    SMART_FORECAST to "forecast",
    ANOMALY to "anomalies",
    OWNER_REPORT to "owner-report",
    STOREFRONT_ETA to "storefront-eta",
    PROMO_GUARD to "promo-guard",
    GIFT_RECEIPT to "gift-receipt",
    DELIVER_LATER to "deliver-later",
    SPECIAL_ORDER to "special-orders",
    PRICE_WHY to "price-why",
    SHRINK_REPORT to "shrink-report",
    COUNT_TOOLS to "stock-count",
    LABEL_TOOLS to "labels",
    PRODUCT_INFO to "product-info",
    POS_SEARCH to "smart-search",
    POS_PRICE_CHECK to "price-check",
    POS_TOUCH to "touch-mode",
    POS_MORE_KEYS to "more-keys",
    REPORT_KEYS to "report-keys",
    DOC_GAPS to "doc-gaps",
    LINE_PRICING to "line-pricing",
    POS_NOTES to "notes",
    AUTO_FEES to "auto-fees",
    SIMPLE_ASK to "simple-ask",
    EASY_SETUP to "easy-setup",
    TRAINING to "training",
    TILL_RECEIVE to "till-receive",
    STOCK_MODE to "stock-mode",
    STOCK_INSIGHT to "stock-insight",
    STOCK_LEVELS to "stock-levels",
    PRODUCT_TOOLS to "product-tools",
    FRAUD_TRAIL to "fraud-trail",
    CHAT_GUARD to "chat-storage",
    CAMERA to "camera",
    "همگام‌سازی" to "sync",
    "داده‌ها" to "backup",
    ABOUT_APP to "app",
  )

  /**
   *  کلیدِ ثابتِ بخش از عنوانی که صفحه داده: کلیدِ فارسیِ خودش، یا عنوانِ ترجمه‌شده‌ای که `labelOf` ِ یکی از
   *  کلیدهاست (پس از عوض‌شدنِ زبان)؛ وگرنه `fallback` (همان `stateKey` ِ بخش).
   */
  fun keyOf(title: String, fallback: String = title): String = when {
    title in SECTION_SLUGS -> title
    else -> SECTION_SLUGS.keys.firstOrNull { labelOf(it) == title } ?: fallback
  }

  /** نامِ ردیف به زبانِ صفحه — کلید عوض نمی‌شود */
  fun labelOf(row: String): String = when (row) {
    "هشدارها" -> Str.alerts
    "ظاهر" -> Str.appearance
    NAV_BAR -> StrSettings.navBar
    "زبان" -> StrSettings.language
    "تاریخ و رقم" -> Str.dateAndDigits
    "قفل برنامه" -> StrSettings.appLockRow
    ENCRYPTION -> StrSettings.encryptionRow
    "کد شاگرد" -> StrSettings.staffCodeRow
    "دستگاه‌های وارد شده" -> StrSettings.sessionsRow
    "تغییر رمز حساب" -> StrSettings.passwordRow
    "کالای منقضی" -> Str.expiredGoods
    CHECKOUT_RULES -> StrSale.checkoutRulesTitle
    HELD -> StrPos.heldSection
    RETURNS -> StrSettings.returnsRow
    PAY -> StrShell.payAndRegister
    PAY_FIX -> StrPayFix.settingsTitle
    CREDIT_GATE -> StrShell.creditGate
    CRM_REPORTS -> StrShell.crmReports
    PRICE_LIST -> StrPos.priceListTitle
    QUOTE -> StrPos.quoteSettingsTitle
    GIFT_CARD -> ir.vil3ntec.tohid.core.i18n.StrGiftCard.settingsTitle
    LAYAWAY -> ir.vil3ntec.tohid.core.i18n.StrLayaway.settingsTitle
    LOYALTY -> ir.vil3ntec.tohid.core.i18n.StrLoyalty.settingsTitle
    SAFE_BOOK -> ir.vil3ntec.tohid.core.i18n.StrSafe.settingsTitle
    TAX_QUARTER -> ir.vil3ntec.tohid.core.i18n.StrTaxQuarter.settingsTitle
    CUSTOMER_CARD -> ir.vil3ntec.tohid.core.i18n.StrCustomerCard.settingsTitle
    STOCK_PLACES -> STOCK_PLACES_TITLE
    SMART_FORECAST -> SMART_FORECAST_TITLE
    ANOMALY -> ANOMALY_TITLE
    OWNER_REPORT -> OWNER_REPORT_TITLE
    STOREFRONT_ETA -> STOREFRONT_ETA_TITLE
    PROMO_GUARD -> PROMO_GUARD_TITLE
    GIFT_RECEIPT -> ir.vil3ntec.tohid.core.i18n.StrGift.section
    DELIVER_LATER -> ir.vil3ntec.tohid.core.i18n.StrDeliver.section
    SPECIAL_ORDER -> ir.vil3ntec.tohid.core.i18n.StrSpecialOrder.settingsTitle
    PRICE_WHY -> ir.vil3ntec.tohid.core.i18n.StrWhy.section
    SHRINK_REPORT -> ir.vil3ntec.tohid.core.i18n.StrShrink.settingsTitle
    COUNT_TOOLS -> ir.vil3ntec.tohid.core.i18n.StrCount.settingsTitle
    LABEL_TOOLS -> ir.vil3ntec.tohid.core.i18n.StrLabel.settingsTitle
    PRODUCT_INFO -> ir.vil3ntec.tohid.core.i18n.StrProduct.section
    POS_SEARCH -> StrPos.smartSearch
    POS_PRICE_CHECK -> ir.vil3ntec.tohid.core.i18n.StrPriceCheck.section
    POS_TOUCH -> ir.vil3ntec.tohid.core.i18n.StrTouch.section
    POS_MORE_KEYS -> StrPos.moreKeys
    REPORT_KEYS -> ir.vil3ntec.tohid.core.i18n.StrHome.reportKeys
    DOC_GAPS -> ir.vil3ntec.tohid.core.i18n.StrReports.docGaps
    LINE_PRICING -> ir.vil3ntec.tohid.core.i18n.StrLine.section
    POS_NOTES -> StrPos.notesSection
    AUTO_FEES -> StrSale.feesTitle
    SIMPLE_ASK -> ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.settingsTitle
    EASY_SETUP -> EASY_SETUP_TITLE
    TRAINING -> TRAINING_SETTINGS_TITLE
    TILL_RECEIVE -> StrSettings.tillReceiveTitle
    STOCK_MODE -> STOCK_SETTINGS_TITLE
    STOCK_INSIGHT -> StrShell.stockInsight
    STOCK_LEVELS -> StrShell.stockLevels
    PRODUCT_TOOLS -> StrShell.productTools
    FRAUD_TRAIL -> StrShell.fraudTrail
    CHAT_GUARD -> ir.vil3ntec.tohid.core.i18n.StrChat.guard
    PRINTER -> StrSettings.printerRow
    CAMERA -> StrSettings.scannerRow
    PERIPHERALS -> StrSettings.peripheralsRow
    "همگام‌سازی" -> StrSettings.syncRow
    "داده‌ها" -> StrSettings.backupRow
    ABOUT_APP -> StrSettings.versionRow
    else -> row
  }

  /** نامِ گروه به زبانِ صفحه (سرخطِ کارت، ردیفِ «بیشتر»، عنوانِ صفحهٔ گروه) */
  fun groupLabel(group: String): String = when (group) {
    GENERAL -> StrSettings.groupGeneral
    SECURITY -> StrSettings.groupSecurity
    SALES -> StrSettings.groupSales
    DEVICES -> StrSettings.groupDevices
    DATA -> StrSettings.groupData
    ABOUT -> StrSettings.groupAbout
    else -> group
  }

  /** گروهِ یک بخش یا پیوند؛ null یعنی ناشناس (آزمون جلویش را می‌گیرد) */
  fun groupOf(row: String): String? = GROUPS.firstOrNull { row in it.sections }?.title

  fun isLink(row: String): Boolean = row in LINKS

  /** همهٔ بخش‌های واقعیِ تنظیمات (بی پیوندها) به ترتیبِ نمایش */
  val sections: List<String> get() = GROUPS.flatMap { it.sections }.filterNot(::isLink)

  /** جایِ گروه در فهرست */
  fun groupIndex(title: String): Int = GROUPS.indexOfFirst { it.title == title }

  /** بخش‌هایی که کامپیوتر عمداً ندارد: دوربینِ گوشی و نوارِ پایینِ گوشی */
  val DESK_HIDDEN: Set<String> = setOf(CAMERA, NAV_BAR)

  /**
   *  بخش‌هایی که فقط کامپیوتر دارد — مثلِ رابطِ امروز: «گزارش‌های مشتری» فقط زبانه‌های «گزارش‌ها»ی
   *  کامپیوتر را نشان/پنهان می‌کند و گوشی دکمهٔ بی‌اثر نمی‌گیرد؛ کلیدهای صندوق و گزارشِ کامپیوتر هم (همان
   *  `Platform.DESKTOP` ِ `SettingsLegacy`). «شماره‌های جاافتاده» (RPT-17) زبانهٔ گوشی هم دارد و این‌جا نیست.
   */
  val PHONE_HIDDEN: Set<String> = setOf(CRM_REPORTS, POS_SEARCH, POS_PRICE_CHECK, POS_TOUCH, POS_MORE_KEYS, REPORT_KEYS)

  /**
   *  بخش‌های پشتِ پرچمِ سرور — مثلِ رابطِ امروز تا سرور «روشن» نگفته ردیفی ندارند (نه در گروه، نه در ستونِ
   *  کامپیوتر). `serverOn` همان بخش‌هایی است که درِ سرورشان همین لحظه «دیده شود» گفته (`SettingsFacts.serverOn`).
   */
  val SERVER_GATED: Set<String> = setOf(OWNER_REPORT, STOREFRONT_ETA, PROMO_GUARD)

  /** ردیفِ این بخش کشیده شود؟ — بخشِ بی پرچم همیشه؛ بخشِ پشتِ پرچم فقط با پاسخِ روشنِ سرور */
  fun shown(key: String, serverOn: Set<String>): Boolean = key !in SERVER_GATED || key in serverOn

  /**
   *  فهرستِ ستونِ کناریِ تنظیماتِ کامپیوتر: ترتیبِ دادهٔ `given` می‌ماند و هر بخشی
   *  که آن‌جا نیست درست پیش از نزدیک‌ترین بخشِ پسینش در همین گروه‌بندی می‌نشیند —
   *  تا هیچ بخشی روی کامپیوتر بی‌راه نماند.
   */
  fun deskOrder(given: List<String>): List<String> {
    val out = given.filter { it in sections }.toMutableList()
    sections.forEachIndexed { i, s ->
      if (s in out || s in DESK_HIDDEN) return@forEachIndexed
      val next = sections.subList(i + 1, sections.size).firstOrNull { it in out }
      val before = sections.subList(0, i).lastOrNull { it in out }
      when {
        next != null -> out.add(out.indexOf(next), s)
        before != null -> out.add(out.indexOf(before) + 1, s)
        else -> out.add(s)
      }
    }
    return out
  }

  /** ستونِ کناریِ کامپیوتر: همهٔ بخش‌ها به ترتیبِ گروه‌ها، بی بخش‌های فقط‌گوشی */
  val deskSections: List<String> get() = sections.filterNot { it in DESK_HIDDEN }
}
