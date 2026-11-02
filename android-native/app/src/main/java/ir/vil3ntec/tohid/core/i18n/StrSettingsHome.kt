package ir.vil3ntec.tohid.core.i18n

/**
 *  تنظیماتِ نسخهٔ دوم (۱۴۰۵/۰۸/۱۱): صفحهٔ اول = کادرِ جست‌وجو + شش دسته + پیوندِ «قابلیت‌ها»؛ سرخطِ بخش‌های کوتاهِ
 *  هر دسته، کارت‌های «قابلیت‌ها» و واژه‌های جست‌وجوی هر بخش. نقشه از کلید: `SettingsLayout` و `SettingsSearchIndex`.
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrSettingsHome {
  //  ── شش دسته ───────────────────────────────────────────────────────
  val shop: String get() = t("دکان و امنیت", "Shop & security", "دوکان او امنیت")
  val goods: String get() = t("کالا و انبار", "Items & stock", "توکي او ګدام")
  val data: String get() = t("داده و پشتیبان", "Data & backup", "معلومات او بیک اپ")
  val look: String get() = t("ظاهر و زبان", "Look & language", "بڼه او ژبه")

  val shopLine: String get() = t("حساب، قفل، کدِ شاگرد و هشدارها", "Account, lock, staff code and alerts", "حساب، قلف، د شاګرد کوډ او خبرتیاوې")
  val salesLine: String get() = t("پرداخت، فاکتور، رسید و برگشت", "Payment, invoices, receipts and returns", "تادیه، بلونه، رسید او بېرته ورکړه")
  val goodsLine: String get() = t("ثبتِ کالا، برچسب و جای کالا در انبار", "Adding items, labels and where stock is kept", "د توکو ثبت، لیبل او په ګدام کې د توکو ځای")
  val devicesLine: String get() = t("چاپگر، بارکدخوان، ترازو و نمایشگر", "Printer, scanner, scale and display", "چاپګر، بارکوډ لوستونکی، تله او ښودونکی")
  val dataLine: String get() = t("همگام‌سازی، پشتیبان و نسخهٔ برنامه", "Sync, backup and app version", "همغږي، بیک اپ او د اپ نسخه")
  val lookLine: String get() = t("رنگ، زبان، تاریخ و رقم", "Colours, language, dates and digits", "رنګ، ژبه، نېټه او شمېرې")

  //  ── سرخطِ بخش‌های کوتاهِ هر دسته ───────────────────────────────────
  val partShop: String get() = t("دکان", "Shop", "دوکان")
  val partAccess: String get() = t("ورود و قفل", "Sign-in and lock", "ننوتل او قلف")
  val partProtect: String get() = t("نگهداریِ دفتر", "Keeping your books safe", "د دفتر ساتنه")
  val partPay: String get() = t("پرداخت", "Payment", "تادیه")
  val partInvoice: String get() = t("فاکتور", "Invoice", "بل")
  val partReceipt: String get() = t("رسید، برگشت و مشتری", "Receipts, returns and customers", "رسید، بېرته ورکړه او پېرودونکي")
  val partTill: String get() = t("صندوقِ کامپیوتر", "Computer till", "د کمپیوټر صندوق")
  val partOnline: String get() = t("فروشِ آنلاین", "Online sales", "آنلاین پلور")
  val partGoods: String get() = t("کالا", "Items", "توکي")
  val partStock: String get() = t("انبار", "Stock", "ګدام")
  val partData: String get() = t("داده", "Data", "معلومات")
  val partReports: String get() = t("گزارش‌ها", "Reports", "راپورونه")
  val partLook: String get() = t("نمایش", "Display", "ښودنه")
  val partUse: String get() = t("کار با برنامه", "Using the app", "له اپ سره کار")

  //  ── ردیفِ «حسابِ من» ─────────────────────────────────────────────
  val profileRow: String get() = t("حساب و نامِ دکان", "Account & shop name", "حساب او د دوکان نوم")
  val profileLine: String get() = t("نامِ دکان، نام و عکسِ شما", "Shop name, your name and photo", "د دوکان نوم، ستاسو نوم او انځور")

  //  ── جست‌وجو ──────────────────────────────────────────────────────
  val searchHint: String get() = t("جست‌وجو در تنظیمات", "Search settings", "په امستنو کې لټون")
  fun noResults(query: String): String =
    t("برای «${asIs(query)}» چیزی پیدا نشد", "Nothing found for “$query”", "د «$query» لپاره هېڅ ونه موندل شو")
  val noResultsHint: String get() = t(
    "ساده‌تر بنویسید؛ مثلاً «رسید»، «قفل» یا «مالیات».",
    "Try a simpler word, for example “receipt”, “lock” or “tax”.",
    "ساده لیکئ؛ د بېلګې په توګه «رسید»، «قلف» یا «مالیه».",
  )
  /** برچسبِ نتیجه‌ای که هنوز آزمایشی است و در فهرست‌ها دیده نمی‌شود */
  val experimental: String get() = t("آزمایشی", "Experimental", "ازمایښتي")
  /** خطِ زیرِ نتیجهٔ یک دسته */
  val categoryHit: String get() = t("دستهٔ تنظیمات", "Settings category", "د امستنو برخه")

  //  ── «قابلیت‌ها» ──────────────────────────────────────────────────
  val features: String get() = t("قابلیت‌ها", "Features", "ځانګړنې")
  val featuresLine: String get() = t(
    "کارتِ هدیه، قسط، امتیاز و بیشتر — هر کدام را خواستید روشن کنید",
    "Gift cards, instalments, points and more — turn on what you need",
    "ډالۍ کارت، قسط، امتیاز او نور — هغه چې غواړئ چالان یې کړئ",
  )
  val featuresIntro: String get() = t(
    "این‌ها بخش‌های اختیاریِ کارند. هر کدام را لازم دارید روشن کنید؛ گزینه‌هایش همان‌جا که به کار می‌آید پیدا می‌شود. خاموش کردن هیچ داده‌ای را پاک نمی‌کند.",
    "These are optional parts of the business. Turn on the ones you need; their options appear where they are used. Turning one off never deletes any data.",
    "دا د کار اختیاري برخې دي. هغه چې اړتیا ورته لرئ چالان یې کړئ؛ انتخابونه یې هلته ښکاري چې کارول کېږي. بندول هېڅ معلومات نه پاکوي.",
  )
  val turnOn: String get() = t("روشن کن", "Turn on", "چالان یې کړئ")
  val turnOff: String get() = t("خاموش کن", "Turn off", "بند یې کړئ")
  val setUp: String get() = t("تنظیم کن", "Set up", "تنظیم یې کړئ")
  val options: String get() = t("گزینه‌ها", "Options", "انتخابونه")
  fun turnedOn(name: String): String = t("«${asIs(name)}» روشن شد", "“$name” is on", "«$name» چالان شو")
  fun turnedOff(name: String): String = t("«${asIs(name)}» خاموش شد", "“$name” is off", "«$name» بند شو")
  fun featureWhere(place: String): String = t("گزینه‌ها در «${asIs(place)}»", "Options are in “$place”", "انتخابونه په «$place» کې دي")
}
