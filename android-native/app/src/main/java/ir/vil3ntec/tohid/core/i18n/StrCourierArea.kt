package ir.vil3ntec.tohid.core.i18n

/**
 *  S83 — محدودهٔ خدمتِ هر پیک: فرمِ پیک (سقف، محدوده و وضعیت)، کارتِ پیک و سپردنِ دستی — فارسیِ معیار؛
 *  رویهٔ دری جداست (`text/DariText.kt`). قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrCourierArea {
  val button: String get() = t("سقف، محدوده و وضعیت", "Cap, area and status", "سقف، سیمه او حالت")
  fun sheetTitle(name: String): String = t("سقف، محدوده و وضعیتِ ${asIs(name)}", "$name — cap, area and status", "د $name سقف، سیمه او حالت")
  val section: String get() = t("محدودهٔ خدمت", "Service area", "د خدمت سیمه")
  val helper: String get() = t(
    "سفارشِ بیرون از این ناحیه‌ها خودکار به این پیک نمی‌رسد و در «سپردن» می‌ماند؛ سفارشِ بی ناحیه به هر پیک. هیچ‌کدام = همه‌جا.",
    "Orders outside these areas are never auto-assigned to this courier and stay in “Assign”; orders without an area go to anyone. None = everywhere.",
    "له دې سیمو بهر فرمایش په خپلکاره ډول دې پیک ته نه ځي او په «سپارل» کې پاتې کېږي؛ بې سیمې فرمایش هر پیک ته. هېڅ = هرځای.",
  )
  val everywhere: String get() = t("همه‌جا", "Everywhere", "هرځای")
  val otherField: String get() = t("ناحیهٔ دیگر", "Another area", "بله سیمه")
  val otherHelper: String get() = t(
    "همان نامی که در «ناحیه» ِ نشانیِ سفارش نوشته می‌شود",
    "The same name written in the order address’s “area”",
    "هماغه نوم چې د فرمایش د پتې په «سیمه» کې لیکل کېږي",
  )
  fun tooMany(max: Int): String = t("حداکثر ${asIs(max)} ناحیه", "At most $max areas", "تر ټولو زیات $max سیمې")
  /** `areas` = نام‌ها با `StrHome.listSep` (آزمون‌های پوشش هر تابع را با متن صدا می‌زنند) */
  fun tag(areas: String): String = t("محدوده: ${asIs(areas)}", "Area: $areas", "سیمه: $areas")
  val outOfArea: String get() = t("بیرون از محدوده", "Outside area", "له سیمې بهر")
  fun orderArea(area: String): String = t("ناحیهٔ سفارش: ${asIs(area)}", "Order area: $area", "د فرمایش سیمه: $area")
  fun assignAnyway(area: String): String = t(
    "آگاهانه می‌سپارم، با این‌که «${asIs(area)}» در محدودهٔ این پیک نیست",
    "Assign anyway, although “$area” is outside this courier’s area",
    "بیا هم یې سپارم، که څه هم «$area» د دې پیک له سیمې بهر ده",
  )
}
