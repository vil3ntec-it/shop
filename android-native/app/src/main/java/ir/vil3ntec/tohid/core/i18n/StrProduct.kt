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
    "روشن: فرمِ کالا نامِ پشتو و انگلیسی، مترادف‌ها، واژه‌های جست‌وجو و محلِ قفسه (راهرو، قفسه، طبقه) را هم می‌گیرد؛ جست‌وجوی صندوق و فهرستِ کالا آن‌ها را هم می‌گردند و محلِ قفسه کنارِ اقلامِ سفارش دیده می‌شود. فقط روی همین دستگاه.",
    "On: the item form also takes a Pashto and an English name, synonyms, search words and a shelf location (aisle, shelf, level); till search and the item list search them too, and the shelf location shows next to order items. This device only.",
    "روښانه: د توکي فورمه پښتو او انګلیسي نوم، مترادفونه، د لټون کلمې او د المارۍ ځای (دهلیز، المارۍ، پوړ) هم اخلي؛ د صندوق لټون او د توکو لړلیک هغه هم لټوي او د المارۍ ځای د فرمایش د توکو تر څنګ ښکاري. یوازې همدا وسیله.",
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
}
