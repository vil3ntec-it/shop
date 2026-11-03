package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.StrSettingsHelp as H

/**
 *  تنظیماتِ کم (تصمیمِ صاحب‌کار، ۱۴۰۵/۰۸/۱۱) — خالص، بی Compose؛ تنظیماتِ نسخهٔ ۳ (گوشی و صفحهٔ پهن)
 *  همین را می‌خواند.
 *
 *  هر بخشِ `SettingsLayout.GROUPS` دقیقاً در یکی از سه دسته است:
 *  - **A — [ALWAYS_ON]:** فقط نمایش یا یک کارِ اختیاریِ بیشتر؛ هیچ مبلغ، قیمت، مالیات، قرض، شمارِ موجودی یا
 *    دسترسی‌ای را عوض نمی‌کند، فروش را نمی‌بندد، گامِ اجباری نمی‌افزاید، آزمایشی و بسته به دستگاهِ جانبی نیست.
 *    پیش‌فرضِ کلیدش «روشن» است (فقط دستگاهی که هرگز دست نزده؛ «خاموش» ِ دستیِ کاربر می‌ماند) و ردیفی در فهرست
 *    ندارد. صفحه و نوشتنش سرِ جایش است و نشانیِ مستقیمش (`settings/sales/price-why`) همان صفحه را باز می‌کند.
 *  - **C — [ESSENTIAL]:** چیزی که هر دکان یک بار تنظیم می‌کند؛ فهرستِ کوتاهِ اصلی.
 *  - **B — بقیه ([Tier.ADVANCED]):** تصمیمِ پولی، سیاستی، دسترسی، آزمایشی یا وابسته به دستگاه؛ پیش‌فرض دست
 *    نمی‌خورد (خاموش).
 *  تنظیماتِ نسخهٔ دوم (۱۴۰۵/۰۸/۱۱): نسخهٔ ۳ دیگر C و B را جدا نمی‌کشد — هر ردیفِ دیدنی در بخشِ کوتاهِ دستهٔ خودش
 *  است (`SettingsLayout.visible`) و «پیشرفته» نیست؛ A همچنان هیچ ردیفی ندارد. C/B فقط ردهٔ پیش‌فرض‌هاست (رابطِ قدیم
 *  که آن‌ها را جدا می‌کشید ۱۴۰۵/۰۸/۱۱ پاک شد).
 *  بخشِ پشتِ پرچمِ سرور (`SettingsLayout.SERVER_GATED`) هنوز تابعِ همان پرچم است.
 *  آزمون: `SettingsTiersTest`.
 */
object SettingsTiers {
  enum class Tier { MAIN, ADVANCED, HIDDEN }

  /**
   *  C — فهرستِ اصلی (گوشی ۱۲ ردیف؛ کامپیوتر بی دوربین ۱۱). «همهٔ دستگاه‌ها» (‎`PERIPHERALS`‎، درخواستِ صاحب‌کار
   *  ۱۴۰۵/۰۸/۱۱) جای پیوندِ «چاپگر و رسید» را گرفت: همان صفحه است و چاپگرها کارتِ سومش‌اند؛ پیوندِ چاپگر در «پیشرفته» ِ
   *  فروش می‌ماند.
   */
  val ESSENTIAL: Set<String> = setOf(
    "ظاهر", "زبان", "تاریخ و رقم",
    "قفل برنامه", "کد شاگرد", "تغییر رمز حساب",
    SettingsLayout.PAY, SettingsLayout.PERIPHERALS,
    SettingsLayout.CAMERA,
    "همگام‌سازی", "داده‌ها",
    SettingsLayout.ABOUT_APP,
  )

  /** A — روشن و پنهان */
  val ALWAYS_ON: Set<String> = setOf(
    SettingsLayout.WHATS_NEW, SettingsLayout.FRAUD_TRAIL,
    SettingsLayout.POS_SEARCH, SettingsLayout.POS_PRICE_CHECK, SettingsLayout.KEY_HELP,
    SettingsLayout.CUSTOMER_CARD, SettingsLayout.SIMPLE_ASK, SettingsLayout.REPORT_KEYS, SettingsLayout.DOC_GAPS,
    SettingsLayout.PRICE_WHY, SettingsLayout.POS_NOTES, SettingsLayout.GIFT_RECEIPT,
    SettingsLayout.STOCK_INSIGHT, SettingsLayout.STOCK_LEVELS, SettingsLayout.COUNT_TOOLS, SettingsLayout.SHRINK_REPORT,
    SettingsLayout.CHAT_GUARD, SettingsLayout.PRODUCT_SUGGEST,
  )

  fun tier(key: String): Tier = when (key) {
    in ALWAYS_ON -> Tier.HIDDEN
    in ESSENTIAL -> Tier.MAIN
    else -> Tier.ADVANCED
  }

  /** بخش‌های یک گروه در همین دسته، به ترتیبِ `SettingsLayout` */
  fun rows(group: SettingsLayout.Group, tier: Tier): List<String> = group.sections.filter { tier(it) == tier }
}

/**
 *  یک خطِ ساده زیرِ هر ردیفِ دیده‌شده (C و B) و یک مثالِ «این چه می‌کند؟» بالای صفحهٔ هر کدام — به زبانِ صفحه.
 *  پیوندها (`SettingsLayout.LINKS`) صفحهٔ تنظیمات ندارند و فقط خط دارند. `null` ⇒ بخشِ پنهان (A) یا ناشناس.
 */
object SettingsHelp {
  /** ردیفِ «پوزش و کوپنِ تأخیر» (`SettingsPaths.LATE_APOLOGY`، بیرون از `SettingsLayout`) */
  const val LATE_APOLOGY = "settings/late-apology"

  fun line(key: String): String? = when (key) {
    "ظاهر" -> H.appearanceLine
    "زبان" -> H.languageLine
    "تاریخ و رقم" -> H.digitsLine
    "قفل برنامه" -> H.appLockLine
    "کد شاگرد" -> H.staffCodeLine
    "تغییر رمز حساب" -> H.passwordLine
    SettingsLayout.PAY -> H.payLine
    SettingsLayout.HESAB_SHOP -> H.hesabShopLine
    SettingsLayout.PRINTER -> H.printerLine
    SettingsLayout.CAMERA -> H.cameraLine
    "همگام‌سازی" -> H.syncLine
    "داده‌ها" -> H.backupLine
    SettingsLayout.ABOUT_APP -> H.aboutLine
    "هشدارها" -> H.alertsLine
    SettingsLayout.NAV_BAR -> H.navBarLine
    SettingsLayout.TRAINING -> H.trainingLine
    SettingsLayout.ENCRYPTION -> H.encryptionLine
    "دستگاه‌های وارد شده" -> H.sessionsLine
    SettingsLayout.ANOMALY -> H.anomalyLine
    "کالای منقضی" -> H.expiryLine
    SettingsLayout.CHECKOUT_RULES -> H.checkoutRulesLine
    SettingsLayout.HELD -> H.heldLine
    SettingsLayout.RETURNS -> H.returnsLine
    SettingsLayout.PAY_FIX -> H.payFixLine
    SettingsLayout.TILL_RECEIVE -> H.tillReceiveLine
    SettingsLayout.MULTI_DRAWER -> H.multiDrawerLine
    SettingsLayout.POS_TOUCH -> H.touchLine
    SettingsLayout.POS_MORE_KEYS -> H.moreKeysLine
    SettingsLayout.TILL_SWITCH -> H.tillSwitchLine
    SettingsLayout.GIFT_CARD -> H.giftCardLine
    SettingsLayout.LAYAWAY -> H.layawayLine
    SettingsLayout.SAFE_BOOK -> H.safeLine
    SettingsLayout.CREDIT_GATE -> H.creditGateLine
    SettingsLayout.LOYALTY -> H.loyaltyLine
    SettingsLayout.CRM_REPORTS -> H.crmLine
    SettingsLayout.TAX_QUARTER -> H.taxLine
    SettingsLayout.OWNER_REPORT -> H.ownerReportLine
    SettingsLayout.LINE_PRICING -> H.linePricingLine
    SettingsLayout.TILL_GOODS -> H.tillGoodsLine
    SettingsLayout.PRICE_LIST -> H.priceListLine
    SettingsLayout.AUTO_FEES -> H.feesLine
    SettingsLayout.QUOTE -> H.quoteLine
    SettingsLayout.SPECIAL_ORDER -> H.specialOrderLine
    SettingsLayout.DELIVER_LATER -> H.deliverLaterLine
    SettingsLayout.STOCK_MODE -> H.stockModeLine
    SettingsLayout.SMART_FORECAST -> H.forecastLine
    SettingsLayout.STOCK_PLACES -> H.placesLine
    SettingsLayout.PRODUCT_TOOLS -> H.productToolsLine
    SettingsLayout.PRODUCT_INFO -> H.productInfoLine
    SettingsLayout.LABEL_TOOLS -> H.labelsLine
    SettingsLayout.STOREFRONT_ETA -> H.etaLine
    SettingsLayout.PROMO_GUARD -> H.promoLine
    SettingsLayout.ONLINE_ORDERS -> ir.vil3ntec.tohid.core.i18n.StrOrderFlow.settingsLine
    SettingsLayout.EASY_SETUP -> H.easySetupLine
    SettingsLayout.SCAN_CASH -> H.scanCashLine
    SettingsLayout.SERVERS -> ir.vil3ntec.tohid.core.i18n.StrReach.serversLine
    SettingsLayout.WEBCAM -> H.webcamLine
    SettingsLayout.PERIPHERALS -> H.peripheralsLine
    SettingsLayout.PROFILE -> ir.vil3ntec.tohid.core.i18n.StrSettingsHome.profileLine
    LATE_APOLOGY -> H.lateApologyLine
    else -> null
  }

  fun example(key: String): String? = when (key) {
    "ظاهر" -> H.appearanceExample
    "زبان" -> H.languageExample
    "تاریخ و رقم" -> H.digitsExample
    "قفل برنامه" -> H.appLockExample
    "کد شاگرد" -> H.staffCodeExample
    "تغییر رمز حساب" -> H.passwordExample
    SettingsLayout.PAY -> H.payExample
    SettingsLayout.HESAB_SHOP -> H.hesabShopExample
    SettingsLayout.CAMERA -> H.cameraExample
    "همگام‌سازی" -> H.syncExample
    "داده‌ها" -> H.backupExample
    SettingsLayout.ABOUT_APP -> H.aboutExample
    "هشدارها" -> H.alertsExample
    SettingsLayout.NAV_BAR -> H.navBarExample
    SettingsLayout.TRAINING -> H.trainingExample
    SettingsLayout.ENCRYPTION -> H.encryptionExample
    "دستگاه‌های وارد شده" -> H.sessionsExample
    SettingsLayout.ANOMALY -> H.anomalyExample
    "کالای منقضی" -> H.expiryExample
    SettingsLayout.CHECKOUT_RULES -> H.checkoutRulesExample
    SettingsLayout.HELD -> H.heldExample
    SettingsLayout.RETURNS -> H.returnsExample
    SettingsLayout.PAY_FIX -> H.payFixExample
    SettingsLayout.TILL_RECEIVE -> H.tillReceiveExample
    SettingsLayout.MULTI_DRAWER -> H.multiDrawerExample
    SettingsLayout.POS_TOUCH -> H.touchExample
    SettingsLayout.POS_MORE_KEYS -> H.moreKeysExample
    SettingsLayout.TILL_SWITCH -> H.tillSwitchExample
    SettingsLayout.GIFT_CARD -> H.giftCardExample
    SettingsLayout.LAYAWAY -> H.layawayExample
    SettingsLayout.SAFE_BOOK -> H.safeExample
    SettingsLayout.CREDIT_GATE -> H.creditGateExample
    SettingsLayout.LOYALTY -> H.loyaltyExample
    SettingsLayout.CRM_REPORTS -> H.crmExample
    SettingsLayout.TAX_QUARTER -> H.taxExample
    SettingsLayout.OWNER_REPORT -> H.ownerReportExample
    SettingsLayout.LINE_PRICING -> H.linePricingExample
    SettingsLayout.TILL_GOODS -> H.tillGoodsExample
    SettingsLayout.PRICE_LIST -> H.priceListExample
    SettingsLayout.AUTO_FEES -> H.feesExample
    SettingsLayout.QUOTE -> H.quoteExample
    SettingsLayout.SPECIAL_ORDER -> H.specialOrderExample
    SettingsLayout.DELIVER_LATER -> H.deliverLaterExample
    SettingsLayout.STOCK_MODE -> H.stockModeExample
    SettingsLayout.SMART_FORECAST -> H.forecastExample
    SettingsLayout.STOCK_PLACES -> H.placesExample
    SettingsLayout.PRODUCT_TOOLS -> H.productToolsExample
    SettingsLayout.PRODUCT_INFO -> H.productInfoExample
    SettingsLayout.LABEL_TOOLS -> H.labelsExample
    SettingsLayout.STOREFRONT_ETA -> H.etaExample
    SettingsLayout.PROMO_GUARD -> H.promoExample
    SettingsLayout.ONLINE_ORDERS -> ir.vil3ntec.tohid.core.i18n.StrOrderFlow.settingsExample
    SettingsLayout.EASY_SETUP -> H.easySetupExample
    SettingsLayout.SCAN_CASH -> H.scanCashExample
    SettingsLayout.SERVERS -> ir.vil3ntec.tohid.core.i18n.StrReach.serversExample
    SettingsLayout.WEBCAM -> H.webcamExample
    //  پیوند است ولی صفحهٔ «دستگاه‌ها» کارتِ «این چه می‌کند؟» دارد (`DevicesScreen`)
    SettingsLayout.PERIPHERALS -> H.peripheralsExample
    LATE_APOLOGY -> H.lateApologyExample
    else -> null
  }
}
