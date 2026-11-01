package ir.vil3ntec.tohid.core.i18n

/**
 *  کالا — نام‌های دیگر و واژه‌های جست‌وجو (PRD-02)، محلِ قفسه (PRD-26)، کدِ کالا (PRD-03) و کالای خدماتی (PRD-09).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrProduct {

  // تنظیمات
  val section: String get() = t("کد، نام‌های دیگر و محلِ قفسه", "Code, other names and shelf location", "کوډ، نور نومونه او د المارۍ ځای")
  val note: String get() = t(
    "روشن: فرمِ کالا کدِ کالا (SKU، با ساختِ خودکار)، «خدماتی یا بی موجودی»، نامِ پشتو و انگلیسی، مترادف‌ها، واژه‌های جست‌وجو، محلِ قفسه (راهرو، قفسه، طبقه)، کالاهای جایگزین و فیلدهای سفارشی را هم می‌گیرد؛ جست‌وجوی صندوق و فهرستِ کالا آن‌ها را هم می‌گردند، محلِ قفسه کنارِ اقلامِ سفارش دیده می‌شود و صندوق برای کالای تمام‌شده جایگزینِ موجود را پیشنهاد می‌کند. فقط روی همین دستگاه.",
    "On: the item form also takes an item code (SKU, with Generate), \"service or non-stock\", a Pashto and an English name, synonyms, search words, a shelf location (aisle, shelf, level), substitute items and custom fields; till search and the item list search them too, the shelf location shows next to order items and the till suggests an in-stock substitute for an item that ran out. This device only.",
    "روښانه: د توکي فورمه د توکي کوډ (SKU، له په خپله جوړولو سره)، «خدماتي یا بې موجودۍ»، پښتو او انګلیسي نوم، مترادفونه، د لټون کلمې، د المارۍ ځای (دهلیز، المارۍ، پوړ)، بدیل توکي او دودیز ساحې هم اخلي؛ د صندوق لټون او د توکو لړلیک هغه هم لټوي، د المارۍ ځای د فرمایش د توکو تر څنګ ښکاري او صندوق د خلاص شوي توکي لپاره موجود بدیل وړاندیز کوي. یوازې همدا وسیله.",
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

  // کدِ کالا (PRD-03) و کالای خدماتی (PRD-09)
  val sku: String get() = t("کدِ کالا (SKU)", "Item code (SKU)", "د توکي کوډ (SKU)")
  val skuAuto: String get() = t("ساختِ خودکار", "Generate", "په خپله جوړول")
  val skuHint: String get() = t("حرف، رقم و - _ . / — یکتا در دکان", "Letters, digits and - _ . / — unique in the shop", "توري، شمېرې او - _ . / — په دوکان کې یوازینی")
  val skuBadChar: String get() = t("فقط حرف، رقم و - _ . /", "Only letters, digits and - _ . /", "یوازې توري، شمېرې او - _ . /")
  fun skuTooLong(a: Any): String = t("حداکثر $a نویسه", "At most $a characters", "تر $a تورو پورې")
  fun skuTaken(a: Any, b: Any): String = t("کدِ «$a» برای «$b» ثبت شده", "Code \"$a\" already belongs to \"$b\"", "کوډ «$a» د «$b» لپاره ثبت دی")
  val stockless: String get() = t("کالای خدماتی یا بی موجودی", "Service or non-stock item", "خدماتي یا بې موجودۍ توکی")
  val stocklessHint: String get() = t(
    "فروش موجودی نمی‌خواهد و حدِ کم بودن ندارد، مثلِ دوخت یا شارژ",
    "Sells without stock and has no low-stock level, e.g. tailoring or top-up",
    "پرته له موجودۍ پلورل کېږي او د کمښت حد نه لري، لکه ګنډل یا چارج",
  )
  val serviceTag: String get() = t("خدماتی", "Service", "خدماتي")

  // کیت (PRD-10) و قیمتِ زمان‌بندی‌شده (PRD-13)
  val kit: String get() = t("اجزای کیت (نام یا بارکد × مقدار)", "Kit parts (name or barcode × amount)", "د کټ برخې (نوم یا بارکوډ × اندازه)")
  val kitHint: String get() = t(
    "هر خط یک جزء، مثلاً «روغن ۵ لیتری × 2». فروشِ کیت موجودیِ اجزا را کم می‌کند",
    "One part per line, e.g. \"Oil 5 L × 2\". Selling the kit takes stock from its parts",
    "هره کرښه یوه برخه، لکه «غوړي ۵ لیتره × 2». د کټ پلور د برخو موجودي کموي",
  )
  fun kitBad(a: Any): String = t("این خط درست نیست یا کالایش پیدا نشد: $a", "This line is wrong or its item was not found: $a", "دا کرښه سمه نه ده یا یې توکی ونه موندل شو: $a")
  val kitPartGone: String get() = t("یکی از اجزای کیت دیگر در دکان نیست", "A kit part is no longer in the shop", "د کټ یوه برخه نور په دوکان کې نشته")
  fun kitNested(a: Any): String = t("کیتِ تو در تو نمی‌شود: «$a»", "Kits cannot be nested: \"$a\"", "کټ په کټ کې نه کېږي: «$a»")
  fun kitStock(a: Any): String = t("$a کیتِ کامل از اجزای موجود", "$a complete kits from parts in stock", "له موجودو برخو $a بشپړ کټونه")
  val prices: String get() = t("قیمتِ زمان‌بندی‌شده", "Scheduled prices", "مهالویش شوې بیې")
  val pricesHint: String get() = t(
    "هر خط «از تا تا = قیمت»، مثلاً «۱۴۰۵/۱۲/۱۰ تا ۱۴۰۵/۱۲/۲۰ = 450»؛ هر دو روز شامل‌اند",
    "One \"from to to = price\" per line, e.g. \"1405/12/10 to 1405/12/20 = 450\"; both days included",
    "هره کرښه «له تر = بیه»، لکه «۱۴۰۵/۱۲/۱۰ تا ۱۴۰۵/۱۲/۲۰ = 450»؛ دواړه ورځې شاملې دي",
  )
  fun pricesBad(a: Any): String = t("این خط درست نیست یا با بازهٔ دیگر هم‌پوشان است: $a", "This line is wrong or overlaps another range: $a", "دا کرښه سمه نه ده یا له بلې مودې سره ګډه ده: $a")
  fun priceToday(a: Any): String = t("قیمتِ امروز: $a", "Today's price: $a", "د نن بیه: $a")
}
