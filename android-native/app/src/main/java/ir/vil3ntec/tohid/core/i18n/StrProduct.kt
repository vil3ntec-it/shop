package ir.vil3ntec.tohid.core.i18n

/**
 *  کالا — نام‌های دیگر و واژه‌های جست‌وجو (PRD-02) و محلِ قفسه (PRD-26).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrProduct {

  // تنظیمات
  val section: String get() = t("نام‌های دیگر و محلِ قفسه", "Other names and shelf location", "نور نومونه او د المارۍ ځای")
  val note: String get() = t(
    "روشن: فرمِ کالا نامِ پشتو و انگلیسی، مترادف‌ها، واژه‌های جست‌وجو، محلِ قفسه (راهرو، قفسه، طبقه)، کالاهای جایگزین و فیلدهای سفارشی را هم می‌گیرد؛ جست‌وجوی صندوق و فهرستِ کالا آن‌ها را هم می‌گردند، محلِ قفسه کنارِ اقلامِ سفارش دیده می‌شود و صندوق برای کالای تمام‌شده جایگزینِ موجود را پیشنهاد می‌کند. فقط روی همین دستگاه.",
    "On: the item form also takes a Pashto and an English name, synonyms, search words, a shelf location (aisle, shelf, level), substitute items and custom fields; till search and the item list search them too, the shelf location shows next to order items and the till suggests an in-stock substitute for an item that ran out. This device only.",
    "روښانه: د توکي فورمه پښتو او انګلیسي نوم، مترادفونه، د لټون کلمې، د المارۍ ځای (دهلیز، المارۍ، پوړ)، بدیل توکي او دودیز ساحې هم اخلي؛ د صندوق لټون او د توکو لړلیک هغه هم لټوي، د المارۍ ځای د فرمایش د توکو تر څنګ ښکاري او صندوق د خلاص شوي توکي لپاره موجود بدیل وړاندیز کوي. یوازې همدا وسیله.",
  )

  // فرمِ کالا
  val names: String get() = t("نام‌های دیگر و جست‌وجو", "Other names and search", "نور نومونه او لټون")
  val namePs: String get() = t("نامِ پشتو", "Pashto name", "پښتو نوم")
  val nameEn: String get() = t("نامِ انگلیسی", "English name", "انګلیسي نوم")
  val synonyms: String get() = t("مترادف‌ها (نام‌های دیگرِ همین کالا)", "Synonyms (other names for this item)", "مترادفونه (د همدې توکي نور نومونه)")
  val keywords: String get() = t("واژه‌های جست‌وجو (برند، نوع…)", "Search words (brand, type…)", "د لټون کلمې (برنډ، ډول…)")
  val listHint: String get() = t("چند تا را با «،» جدا کنید", "Separate several with commas", "څو یې په «،» سره جلا کړئ")
  val location: String get() = t("محلِ قفسه", "Shelf location", "د المارۍ ځای")
  val aisle: String get() = t("راهرو", "Aisle", "دهلیز")
  val shelf: String get() = t("قفسه", "Shelf", "المارۍ")
  val level: String get() = t("طبقه", "Level", "پوړ")

  // اقلامِ سفارش
  fun aisleIs(a: Any): String = t("راهرو $a", "Aisle $a", "دهلیز $a")
  fun shelfIs(a: Any): String = t("قفسه $a", "Shelf $a", "المارۍ $a")
  fun levelIs(a: Any): String = t("طبقه $a", "Level $a", "پوړ $a")

  // کالای جایگزین (PRD-24) و فیلدهای سفارشی (PRD-25)
  val alternatives: String get() = t("کالاهای جایگزین (نام یا بارکد)", "Substitute items (name or barcode)", "بدیل توکي (نوم یا بارکوډ)")
  val alternativesHint: String get() = t(
    "به ترتیبِ ترجیح، با «،» جدا. وقتی این کالا تمام شود، صندوق جایگزینِ موجود را پیشنهاد می‌کند",
    "In order of preference, separated by commas. When this item runs out, the till suggests one in stock",
    "د غوره والي په ترتیب، په «،» سره جلا. کله چې دا توکی خلاص شي، صندوق موجود بدیل وړاندیز کوي",
  )
  fun notFound(a: Any): String = t("پیدا نشد: $a", "Not found: $a", "ونه موندل شو: $a")
  val customFields: String get() = t("فیلدهای سفارشی", "Custom fields", "دودیز ساحې")
  val customHint: String get() = t("هر خط «نام: مقدار»، مثلاً «گارانتی: ۱۲ ماه»", "One \"name: value\" per line, e.g. \"Warranty: 12 months\"", "هره کرښه «نوم: ارزښت»، لکه «تضمین: ۱۲ میاشتې»")
  fun customBad(a: Any): String = t("این خط «نام: مقدار» نیست: $a", "This line is not \"name: value\": $a", "دا کرښه «نوم: ارزښت» نه ده: $a")
  fun instead(a: Any): String = t("جایگزینِ موجود: $a", "In stock instead: $a", "موجود بدیل: $a")
}
