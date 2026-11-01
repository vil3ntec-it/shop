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

  /** ردیفی که صفحهٔ دیگری از برنامه را باز می‌کند ⇐ مسیرِ همان صفحه */
  val LINKS: Map<String, String> = mapOf(
    PRINTER to "devices?tab=printers",
    PERIPHERALS to "devices",
  )

  val GROUPS: List<Group> = listOf(
    Group(GENERAL, listOf("هشدارها", "ظاهر", NAV_BAR, "زبان", "تاریخ و رقم")),
    Group(SECURITY, listOf("قفل برنامه", ENCRYPTION, "کد شاگرد", "دستگاه‌های وارد شده", "تغییر رمز حساب", FRAUD_TRAIL)),
    Group(
      SALES,
      listOf(
        "کالای منقضی", CHECKOUT_RULES, HELD, RETURNS, PAY, PAY_FIX, TILL_RECEIVE, CREDIT_GATE, CRM_REPORTS,
        PRICE_LIST, QUOTE, STOCK_MODE, STOCK_INSIGHT, STOCK_LEVELS, PRODUCT_TOOLS, PRINTER,
      ),
    ),
    Group(DEVICES, listOf(CAMERA, PERIPHERALS)),
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
   *  کامپیوتر را نشان/پنهان می‌کند و گوشی دکمهٔ بی‌اثر نمی‌گیرد.
   */
  val PHONE_HIDDEN: Set<String> = setOf(CRM_REPORTS)

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
