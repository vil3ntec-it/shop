package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSettingsHome
import ir.vil3ntec.tohid.core.text.CatalogMatch
import ir.vil3ntec.tohid.core.text.DariText
import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.ui.screens.SettingsLayout as L

/**
 *  جست‌وجوی تنظیمات (نسخهٔ دوم، ۱۴۰۵/۰۸/۱۱) — خالص، بی Compose؛ گوشی، تبلت، کامپیوتر و آیفون همین را می‌خوانند.
 *
 *  نمایه: شش دسته، صفحهٔ «قابلیت‌ها»، **همهٔ** بخش‌های `SettingsLayout` (پنهانِ A، آزمایشی و قابلیتِ خاموش هم —
 *  تا هیچ صفحه‌ای بی‌راه نماند) و ردیف‌های ماژول‌ها. هر ردیف عنوان و خطِ سادهٔ همان زبانِ صفحه را دارد و واژه‌های
 *  [keywords] را به هر سه زبان و دری. فقط بخشِ این سکو (فقط‌گوشی/فقط‌کامپیوتر) و بخشِ پشتِ پرچمِ خاموشِ سرور
 *  نمی‌آید — همان قاعدهٔ صفحه‌ها.
 *
 *  جور کردن همان `CatalogMatch` ِ صندوق است (نرمال‌سازیِ `TextNormalizer`: ي/ی، ك/ک، نیم‌فاصله، رقم‌ها، غلطِ
 *  تایپی) در دو دور: اول عنوان‌ها، بعد خط و واژه‌ها؛ آزمایشی‌ها همیشه ته (با برچسبِ «آزمایشی»). آزمون:
 *  `SettingsSearchTest`.
 */
object SettingsSearchIndex {
  /** صفحهٔ «قابلیت‌ها» — همان `SettingsPaths.FEATURES` */
  const val FEATURES_KEY = "settings/features"

  enum class Kind { CATEGORY, SECTION, FEATURES, EXTRA }

  /**
   *  یک ردیفِ پیداشدنی.
   *  @param key کلیدِ بخش، عنوانِ دسته، [FEATURES_KEY] یا مسیرِ ردیفِ ماژول
   *  @param place «دسته · بخش» — جایِ همین ردیف در تنظیمات
   */
  data class Doc(
    val key: String,
    val kind: Kind,
    val title: String,
    val subtitle: String?,
    val place: String,
    val experimental: Boolean,
    val keywords: List<String>,
  )

  /** ردیفی که ماژولی در تنظیمات گذاشته (`SettingsEntry`) */
  data class Extra(val route: String, val title: String, val subtitle: String?, val group: String)

  /** همهٔ ردیف‌های پیداشدنی به ترتیبِ تنظیمات، به زبانِ همین لحظه */
  fun docs(desktop: Boolean, serverOn: Set<String>, extras: List<Extra> = emptyList()): List<Doc> = buildList {
    L.GROUPS.forEach { g -> add(Doc(g.title, Kind.CATEGORY, L.groupLabel(g.title), L.groupLine(g.title), "", false, keywords(g.title))) }
    add(Doc(FEATURES_KEY, Kind.FEATURES, StrSettingsHome.features, StrSettingsHome.featuresLine, "", false, keywords(FEATURES_KEY)))
    L.GROUPS.forEach { g ->
      g.parts.forEach { p ->
        val place = listOfNotNull(L.groupLabel(g.title), L.partLabel(p.id)).joinToString(" · ")
        p.rows.forEach { key ->
          val platform = if (desktop) key !in L.DESK_HIDDEN else key !in L.PHONE_HIDDEN
          if (platform && L.shown(key, serverOn)) {
            val title = L.labelOf(key)
            add(Doc(key, Kind.SECTION, title, SettingsHelp.line(key), place, key in L.EXPERIMENTAL, keywords(key) + dari(title)))
          }
        }
      }
    }
    extras.forEach { e -> add(Doc(e.route, Kind.EXTRA, e.title, e.subtitle, L.groupLabel(e.group), false, dari(e.title))) }
  }

  /**
   *  نتیجه‌ها به ترتیب: عنوانی که با پرس‌وجو آغاز می‌شود یا همهٔ واژه‌هایش را دارد، بعد خطِ ساده و واژه‌های دیگر،
   *  بعد غلطِ تایپی؛ آزمایشی‌ها ته. پرس‌وجوی خالی ⇒ هیچ.
   */
  fun search(docs: List<Doc>, query: String): List<Doc> {
    if (TextNormalizer.searchKey(query).isBlank()) return emptyList()
    val titles = CatalogMatch.index(docs.mapIndexed { i, d -> CatalogMatch.Item(i.toString(), d.title) })
    val full = CatalogMatch.index(
      docs.mapIndexed { i, d -> CatalogMatch.Item(i.toString(), d.title, keywords = listOfNotNull(d.subtitle) + d.keywords) },
    )
    val hits = (CatalogMatch.search(titles, query, fuzzyBelow = 0) + CatalogMatch.search(full, query)).distinct()
    return hits.map { docs[it.toInt()] }.sortedBy { it.experimental }
  }

  /** همان عنوان در دری، وقتی صفحه فارسی است (دری‌نویس «گدام» می‌نویسد و ردیفِ «انبار» پیدا می‌شود) */
  private fun dari(title: String): List<String> =
    if (I18n.lang == Lang.FA) listOf(DariText.of(title)).filter { it != title } else emptyList()

  /**
   *  واژه‌های دیگرِ جست‌وجو برای یک کلید: هم‌معنی‌ها و همان مفهوم به هر سه زبان — فارسی، دریِ همان فارسی
   *  (`DariText`)، انگلیسی و پشتو با هم. دادهٔ جست‌وجوست، نه نوشتهٔ صفحه.
   */
  fun keywords(key: String): List<String> {
    val k = KEYWORDS[key] ?: return emptyList()
    return listOf(k.first, DariText.of(k.first), k.second, k.third).distinct()
  }

  private fun kw(fa: String, en: String, ps: String) = Triple(fa, en, ps)

  /** کلید ⇐ (فارسی، انگلیسی، پشتو)؛ کلیدِ ناشناس در آزمون قرمز است */
  internal val KEYWORDS: Map<String, Triple<String, String, String>> = mapOf(
    //  دسته‌ها و «قابلیت‌ها»
    L.SHOP to kw("دکان امنیت حساب رمز قفل", "shop security account password lock", "دوکان امنیت حساب پټنوم قلف"),
    L.SALES to kw("فروش پرداخت صندوق فاکتور رسید", "sales payment till invoice receipt", "پلور تادیه صندوق بل رسید"),
    L.GOODS to kw("کالا جنس انبار موجودی", "items products stock warehouse inventory", "توکي جنس ګدام موجودي"),
    L.DEVICES to kw("دستگاه چاپگر پرینتر بارکدخوان ترازو نمایشگر", "devices printer scanner scale display", "وسیلې چاپګر پرنټر بارکوډ تله"),
    L.DATA to kw("داده پشتیبان بک‌آپ همگام‌سازی نسخه", "data backup sync version", "معلومات بیک اپ همغږي نسخه"),
    L.LOOK to kw("ظاهر رنگ تم زبان تاریخ رقم", "look theme colour language date digits", "بڼه رنګ ژبه نېټه شمېرې"),
    FEATURES_KEY to kw("قابلیت امکانات ماژول افزونه", "features modules add-ons", "ځانګړنې ماډلونه"),
    //  دکان و امنیت
    //  «حذفِ حساب» (قاعدهٔ Google Play) پایینِ همین صفحه است — جست‌وجوی «حذف حساب» به آن می‌برد
    L.PROFILE to kw("حساب پروفایل نام دکان عکس حذف حساب پاک کردن حساب", "account profile shop name photo delete account", "حساب پروفایل د دوکان نوم حساب ړنګول"),
    "هشدارها" to kw("هشدار اعلان قرض سقف", "alerts notifications debt limit", "خبرتیا اعلان پور"),
    "قفل برنامه" to kw("قفل رمز پین اثر انگشت", "app lock pin passcode fingerprint", "قلف پټنوم پین ګوته"),
    "کد شاگرد" to kw("کد شاگرد کارمند ورود", "staff code employee login", "د شاګرد کوډ کارکوونکی"),
    "تغییر رمز حساب" to kw("رمز حساب پسورد گذرواژه", "password account change", "پټنوم حساب بدلول"),
    L.TILL_SWITCH to kw("تعویض کاربر صندوق پین", "switch user till pin", "کارن بدلول صندوق"),
    L.ENCRYPTION to kw("رمزگذاری امنیت دفتر", "encryption security ledger", "کوډ کول امنیت دفتر"),
    "دستگاه‌های وارد شده" to kw("دستگاه وصل نشست خروج", "signed-in devices sessions sign out", "ننوتې وسیلې ناسته"),
    L.FRAUD_TRAIL to kw("تقلب حذف سابقه", "fraud trail deleted lines", "درغلي ړنګ شوي"),
    L.ANOMALY to kw("ناهنجاری غیرعادی", "anomaly unusual activity", "غیرعادي"),
    //  فروش و پرداخت
    L.PAY to kw("پرداخت صندوق نقد نسیه گرد کردن شیفت شمارش کور", "payments register cash credit rounding shift blind count", "تادیه صندوق نغد پور ګردول پټ شمېرل"),
    L.HESAB_SHOP to kw("حساب‌پی حساب پی پرداخت آنلاین", "hesabpay online payment", "حساب پی آنلاین تادیه"),
    L.TILL_RECEIVE to kw("دریافت قسط قرض F7", "receive payment instalment debt F7", "ترلاسه قسط پور"),
    L.MULTI_DRAWER to kw("چند کشو صندوقدار شیفت شناور دخل جدا دستگاه دیگر", "multiple drawers cashiers floating shift till another device", "څو دخلونه صندوقدار ګرځنده شیفټ بله دستګاه"),
    L.PAY_FIX to kw("اصلاح روش پرداخت", "fix payment method", "د تادیې لاره سمول"),
    L.CREDIT_GATE to kw("سقف اعتبار قرض نسیه", "credit limit debt", "د اعتبار حد پور"),
    L.GIFT_CARD to kw("کارت هدیه اعتبار", "gift card store credit", "ډالۍ کارت اعتبار"),
    L.LAYAWAY to kw("امانی قسطی قسط پیش‌پرداخت", "layaway instalments deposit", "قسط امانت"),
    L.SAFE_BOOK to kw("گاوصندوق پول نقد", "safe cash", "صندوق نغدې پیسې"),
    L.CUSTOMER_CARD to kw("کارت مشتری بارکد", "customer card barcode", "د پېرودونکي کارت"),
    L.CHECKOUT_RULES to kw("قاعده فاکتور بستن صفر", "checkout rules invoice", "د بل قاعدې"),
    L.HELD to kw("چند فاکتور نگه‌داشته", "multiple held invoices", "څو بلونه"),
    "کالای منقضی" to kw("منقضی تاریخ انقضا", "expired items expiry date", "تېر شوي توکي"),
    L.LINE_PRICING to kw("تخفیف قیمت ردیف", "discount line price", "تخفیف بیه"),
    L.LINE_SELLER to kw("فروشنده ردیف پورسانت کارمند Shift+F2", "seller line commission staff Shift+F2", "پلورونکی کرښه کمیشن کارکوونکی"), // داده: واژه‌های جست‌وجوی تنظیمات در سه زبان
    L.AUTO_FEES to kw("هزینه خودکار کیسه بسته‌بندی مصرف خودکار مالیات فاکتور", "automatic charges fee bag invoice tax", "اتومات لګښت مالیه"), // POS-37: مالیاتِ فاکتور در همین صفحه
    L.QUOTE to kw("پیش فاکتور قیمت‌دهی", "quote estimate", "مخکینی بل"),
    L.SPECIAL_ORDER to kw("سفارش ویژه پیش پرداخت", "special order deposit", "ځانګړی فرمایش"),
    L.DELIVER_LATER to kw("تحویل بعدی", "deliver later", "وروسته سپارل"),
    L.PRICE_WHY to kw("چرا قیمت", "why this price", "ولې بیه"),
    L.POS_NOTES to kw("یادداشت", "notes", "یادښت"),
    L.PRINTER to kw("چاپگر پرینتر رسید چاپ", "printer receipt print", "چاپګر رسید چاپ"),
    L.RETURNS to kw("برگشت مرجوعی تعویض", "returns exchange refund", "بېرته ورکړه بدلول"),
    L.LOYALTY to kw("امتیاز وفاداری مشتری", "loyalty points customer tier", "امتیاز وفاداري"),
    L.PRICE_LIST to kw("لیست قیمت عمده خرده قیمت خاص مشتری", "price list wholesale retail customer special price", "د بیو لېست عمده ځانګړې بیه"),
    L.GIFT_RECEIPT to kw("رسید هدیه", "gift receipt", "د ډالۍ رسید"),
    L.POS_TOUCH to kw("لمسی صفحه لمسی", "touch mode", "لمسي"),
    L.POS_MORE_KEYS to kw("کلید میانبر", "shortcut keys", "کیلي"),
    L.POS_SEARCH to kw("جست‌وجو صندوق", "till search", "لټون"),
    L.POS_PRICE_CHECK to kw("قیمت پرسی", "price check", "بیه پوښتنه"),
    L.KEY_HELP to kw("راهنما کلید", "key help shortcuts", "د کیلي لارښود"),
    L.STORE_PLACE to kw(
      "نشانی دکان آدرس نقشه پین مسیر دریافت از دکان ویترین موقعیت",
      "shop address map pin directions pickup storefront location",
      "د دوکان پته نقشه پن لار له دوکانه اخیستل ویترین ځای",
    ),
    L.STORE_STOCK to kw(
      "ناموجود تمام شده پنهان مخفی کالای ناموجود ویترین موجودی نشان نده",
      "out of stock sold out hide hidden storefront stock",
      "نشته توکي پټ ویترین موجودي",
    ),
    L.STOREFRONT_ETA to kw("زمان تحویل ویترین", "delivery time storefront", "د سپارلو وخت"),
    L.PROMO_GUARD to kw("تخفیف اولین خرید", "first order discount", "لومړی پېرود تخفیف"),
    L.ONLINE_ORDERS to kw(
      "سفارش آنلاین نمی‌رسد ویترین پرداخت پس از پذیرش اعلان گوشی پوش برنامه بسته زنگ",
      "online orders not arriving storefront pay after accept push phone notification closed app ring",
      "آنلاین فرمایش نه راځي ویترین تادیه خبرتیا تلیفون تړلې برنامه زنګ",
    ),
    //  کالا و انبار
    L.TILL_GOODS to kw("ثبت سریع واحد کارتن بسته", "quick add unit carton pack", "چټک ثبت واحد کارتن"),
    L.PRODUCT_INFO to kw("کد نام دیگر قفسه", "code alias shelf", "کوډ بل نوم الماری"),
    L.PRODUCT_SUGGEST to kw("پیشنهاد دسته نام از متن چسباندن فهرست", "suggest category name paste text list", "وړاندیز ډله نوم له متن"),
    L.LABEL_TOOLS to kw("برچسب بارکد لیبل", "label barcode", "لیبل بارکوډ"),
    L.PRODUCT_TOOLS to kw("ابزار کالا", "product tools", "د توکو وسایل"),
    L.STOCK_PLACES to kw("محل جا انبار قفسه بسته‌بندی دستور ساخت تولید اتلاف سری", "locations warehouse repack recipe bom production loss lot", "ځایونه ګدام بسته لارښود جوړول تولید ضایع لړۍ"),
    L.COUNT_TOOLS to kw("انبارگردانی شمارش", "stock count", "شمېرنه"),
    L.SHRINK_REPORT to kw("ضایعات کسری", "shrink waste", "ضایعات کمښت"),
    L.STOCK_LEVELS to kw("نقطه سفارش حداقل", "reorder point minimum", "د فرمایش ټکی"),
    L.STOCK_INSIGHT to kw("تحلیل موجودی", "stock insight", "د موجودي تحلیل"),
    L.STOCK_MODE to kw("موجودی بها قیمت تمام شده", "stock and cost", "موجودي او بیه"),
    L.SMART_FORECAST to kw("پیش‌بینی تقاضا", "demand forecast", "وړاندوینه"),
    //  دستگاه‌ها
    L.PERIPHERALS to kw("دستگاه چاپگر ترازو نمایشگر", "devices printer scale display", "وسیلې"),
    L.CAMERA to kw("دوربین بارکد اسکن", "camera barcode scan", "کمره بارکوډ"),
    L.EASY_SETUP to kw("راه‌اندازی آسان", "easy setup", "اسانه چمتو کول"),
    L.WEBCAM to kw("وب کم دوربین کامپیوتر لپ تاپ بارکدخوان اسکن", "webcam camera laptop barcode scanner scan", "ویب کم کمره کمپیوټر بارکوډ سکن"),
    L.SCAN_CASH to kw("کارت فرمان فروش نقدی اسکنر بارکدخوان بی برنامه", "command card cash sale scanner background", "د امر کارت نغده پلور سکنر"),
    //  داده و پشتیبان
    "همگام‌سازی" to kw("همگام‌سازی سینک سرور", "sync server", "همغږي"),
    "داده‌ها" to kw(
      "پشتیبان بک‌آپ بازگردانی بازیابی عبارت بازیابی رمز نسخه خودکار چاپ عبارت",
      "backup restore recovery phrase encrypt automatic copies snapshot print phrase",
      "بیک اپ بېرته راوستل د بېرته راګرځولو عبارت کوډ خپلکاره نسخې",
    ),
    L.ABOUT_APP to kw("نسخه به‌روزرسانی آپدیت حریم خصوصی", "version update privacy policy", "نسخه تازه کول محرمیت"),
    L.CHAT_GUARD to kw("گفت‌وگو چت جا", "chat storage", "خبرې اترې"),
    L.WHATS_NEW to kw("تازه‌ها چه چیزی تازه است تغییرها به‌روزرسانی", "what's new changes update", "څه نوي دي بدلونونه"),
    L.SERVERS to kw("سرور دیگر شبکه دکان خانه نشانی", "server other network address home", "سرور شبکه پته"),
    L.OWNER_REPORT to kw("گزارش خودکار صاحب", "owner report", "د خاوند راپور"),
    L.TAX_QUARTER to kw("مالیات سه ماهه", "quarterly tax", "درې میاشتنۍ مالیه"),
    L.CRM_REPORTS to kw(
      "گزارش مشتری ادغام تکراری صورت‌حساب گروه مشتری لیست قیمت سن بدهی",
      "customer reports merge duplicates statement image customer group price list debt aging RFM",
      "د پېرودونکو راپورونه یوځای کول تکراري صورتحساب ډله",
    ),
    L.SIMPLE_ASK to kw("پرسش گزارش", "ask reports", "پوښتنه راپور"),
    L.REPORT_KEYS to kw("کلید دوره گزارش", "report period keys", "د دورې کیلي"),
    L.DOC_GAPS to kw("شماره جاافتاده سند", "document gaps", "ورک شوې شمېرې"),
    //  ظاهر و زبان
    "ظاهر" to kw("ظاهر رنگ تم تیره روشن حرکت", "appearance theme dark light motion", "بڼه رنګ تیاره"),
    "زبان" to kw("زبان دری پشتو انگلیسی فارسی", "language dari pashto english persian", "ژبه دري پښتو"),
    "تاریخ و رقم" to kw("تاریخ رقم تقویم افغانی هفته شنبه جمعه تعطیل روز کاری", "date digits calendar afghani week start working days closed", "نېټه شمېرې کلیز اونۍ جمعه رخصت کاري ورځ"),
    L.NAV_BAR to kw("نوار پایین منو", "bottom bar menu", "لاندې پټه"),
    L.TRAINING to kw("آموزشی تمرین", "training practice", "زده کړه تمرین"),
  )
}
