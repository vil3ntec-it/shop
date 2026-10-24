package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings

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

  /** ردیفی که صفحهٔ دیگری از برنامه را باز می‌کند ⇐ مسیرِ همان صفحه */
  val LINKS: Map<String, String> = mapOf(
    PRINTER to "devices?tab=printers",
    PERIPHERALS to "devices",
  )

  val GROUPS: List<Group> = listOf(
    Group(GENERAL, listOf("هشدارها", "ظاهر", NAV_BAR, "زبان", "تاریخ و رقم")),
    Group(SECURITY, listOf("قفل برنامه", "کد شاگرد", "دستگاه‌های وارد شده", "تغییر رمز حساب")),
    Group(SALES, listOf("کالای منقضی", PRINTER)),
    Group(DEVICES, listOf(CAMERA, PERIPHERALS)),
    Group(DATA, listOf("همگام‌سازی", "داده‌ها")),
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
    "کد شاگرد" to "staff-code",
    "دستگاه‌های وارد شده" to "sessions",
    "تغییر رمز حساب" to "password",
    "کالای منقضی" to "expiry",
    CAMERA to "camera",
    "همگام‌سازی" to "sync",
    "داده‌ها" to "backup",
    ABOUT_APP to "app",
  )

  /** نامِ ردیف به زبانِ صفحه — کلید عوض نمی‌شود */
  fun labelOf(row: String): String = when (row) {
    "هشدارها" -> Str.alerts
    "ظاهر" -> Str.appearance
    NAV_BAR -> StrSettings.navBar
    "زبان" -> StrSettings.language
    "تاریخ و رقم" -> Str.dateAndDigits
    "قفل برنامه" -> StrSettings.appLockRow
    "کد شاگرد" -> StrSettings.staffCodeRow
    "دستگاه‌های وارد شده" -> StrSettings.sessionsRow
    "تغییر رمز حساب" -> StrSettings.passwordRow
    "کالای منقضی" -> Str.expiredGoods
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
  val deskSections: List<String> get() = deskOrder(sections)
}
