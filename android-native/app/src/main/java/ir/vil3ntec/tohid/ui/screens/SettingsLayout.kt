package ir.vil3ntec.tohid.ui.screens

/**
 *  گروه‌بندیِ تنظیماتِ گوشی (گوشی نسخهٔ ۳، تصویرِ مرجعِ r5) — خالص، بی Compose.
 *
 *  هر بخشِ موجودِ `SettingsScreen` (عنوانِ `SettingsSection`ش) دقیقاً در یک گروه
 *  است؛ **هیچ تنظیمی برداشته نشده** و کارِ هیچ‌کدام عوض نشده — فقط زیرِ سرخطِ
 *  گروه کنارِ هم نشسته‌اند و روی گوشی هر کدام یک ردیفِ کوتاه است که صفحهٔ خودش
 *  را باز می‌کند. `SettingsLayoutTest` همین را از رویِ متنِ `Settings.kt` و
 *  `LocaleSection.kt` می‌سنجد: بخشِ تازه‌ای که گروه نگیرد یا گروهِ بی‌بخش، آزمون
 *  را قرمز می‌کند.
 *
 *  عنوانِ بخش «کلید» است (کامپیوتر با همان بخش را انتخاب می‌کند)؛ نامی که روی
 *  گوشی دیده می‌شود از `labelOf` می‌آید.
 *
 *  ردیف‌های «پیوند» (`LINKS`) بخشِ تنظیمات نیستند: صفحهٔ دیگری از برنامه را باز
 *  می‌کنند (چاپگرها در «دستگاه‌های جانبی»، ویرایشگرِ نوارِ پایین). کامپیوتر آن‌ها
 *  را نمی‌بیند.
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

  /** پیوند به ویرایشگرِ نوارِ پایین (مسیرش را بخشِ نوار می‌سازد) */
  const val NAV_BAR = "نوار پایین"
  /** پیوند به چاپگرها در صفحهٔ «دستگاه‌های جانبی» */
  const val PRINTER = "چاپگر و رسید"

  /** ردیفی که صفحهٔ دیگری از برنامه را باز می‌کند ⇐ مسیرِ همان صفحه */
  val LINKS: Map<String, String> = mapOf(
    NAV_BAR to "nav-editor",
    PRINTER to "devices",
  )

  val GROUPS: List<Group> = listOf(
    Group(GENERAL, listOf("هشدارها", "ظاهر", NAV_BAR, "زبان", "تاریخ و رقم")),
    Group(SECURITY, listOf("قفل برنامه", "کد شاگرد", "دستگاه‌های وارد شده", "تغییر رمز حساب")),
    Group(SALES, listOf("کالای منقضی", "پرداخت و صندوق", "گزارش‌های مشتری", PRINTER)),
    Group(DEVICES, listOf(CAMERA)),
    Group(DATA, listOf("همگام‌سازی", "داده‌ها")),
    Group(ABOUT, listOf("درباره برنامه")),
  )

  /** نامِ کوتاهِ ردیف روی گوشی (r5) — کلید عوض نمی‌شود */
  private val LABELS: Map<String, String> = mapOf(
    NAV_BAR to "نوارِ پایین",
    "قفل برنامه" to "قفلِ برنامه",
    "کد شاگرد" to "کدِ شاگرد",
    "دستگاه‌های وارد شده" to "دستگاه‌های وصل",
    "تغییر رمز حساب" to "رمزِ حساب",
    CAMERA to "بارکدخوان",
    "داده‌ها" to "پشتیبان و بازگردانی",
  )

  /** کلیدِ ثابتِ صفحهٔ «زبان» (`stateKey` در `LanguageSection`): عنوانش با زبان عوض می‌شود */
  const val LANGUAGE_KEY = "settings-language"

  fun labelOf(row: String): String =
    if (row == LANGUAGE_KEY) ir.vil3ntec.tohid.core.i18n.StrSettings.language else LABELS[row] ?: row

  /** گروهِ یک بخش یا پیوند؛ null یعنی ناشناس (آزمون جلویش را می‌گیرد) */
  fun groupOf(row: String): String? = GROUPS.firstOrNull { row in it.sections }?.title

  fun isLink(row: String): Boolean = row in LINKS

  /** همهٔ بخش‌های واقعیِ تنظیمات (بی پیوندها) به ترتیبِ نمایش */
  val sections: List<String> get() = GROUPS.flatMap { it.sections }.filterNot(::isLink)

  /** جایِ گروه در فهرستِ گوشی — برای «بیشتر ⇐ تنظیمات ⇐ گروه» */
  fun groupIndex(title: String): Int = GROUPS.indexOfFirst { it.title == title }

  /** بخش‌هایی که کامپیوتر عمداً ندارد (دوربینِ گوشی؛ کامپیوتر بارکدخوانِ خودش را دارد) */
  val DESK_HIDDEN: Set<String> = setOf(CAMERA)

  /**
   *  فهرستِ ستونِ کناریِ تنظیماتِ کامپیوتر: همان‌هایی که `AppRoot` داده، به
   *  همان ترتیب، و هر بخشی که آن‌جا نیست (مثلِ «زبان» و «همگام‌سازی» که تازه
   *  آمده‌اند) درست پیش از نزدیک‌ترین بخشِ پسینش در همین گروه‌بندی — تا هیچ
   *  بخشی روی کامپیوتر بی‌راه نماند.
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
}
