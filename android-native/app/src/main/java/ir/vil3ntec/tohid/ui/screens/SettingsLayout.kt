package ir.vil3ntec.tohid.ui.screens

/**
 *  گروه‌بندیِ تنظیماتِ گوشی (phone-ui، تصویرِ مرجعِ ۴) — خالص، بی Compose.
 *
 *  هر بخشِ موجودِ `SettingsScreen` (عنوانِ `SettingsSection`ش) دقیقاً در یک گروه
 *  است؛ **هیچ تنظیمی برداشته یا نام‌عوض نشده** و کارِ هیچ‌کدام عوض نشده — فقط
 *  زیرِ سرخطِ گروه کنارِ هم نشسته‌اند. `SettingsLayoutTest` همین را از رویِ
 *  متنِ `Settings.kt` و `LocaleSection.kt` می‌سنجد: بخشِ تازه‌ای که گروه نگیرد
 *  یا گروهِ بی‌بخش، آزمون را قرمز می‌کند.
 *
 *  کامپیوتر گروه نمی‌بیند: ستونِ «تنظیمات»ش همان فهرستِ `DeskSettings` است.
 */
object SettingsLayout {
  data class Group(val title: String, val sections: List<String>)

  const val GENERAL = "عمومی"
  const val SALES = "فروش و پرداخت"
  const val SECURITY = "امنیت"
  const val DEVICES = "دستگاه‌ها"
  const val DATA = "همگام‌سازی و پشتیبان"
  const val ABOUT = "درباره"

  val GROUPS: List<Group> = listOf(
    Group(GENERAL, listOf("هشدارها", "ظاهر", "تاریخ و رقم")),
    Group(SALES, listOf("کالای منقضی")),
    Group(SECURITY, listOf("قفل برنامه", "تغییر رمز حساب", "کد شاگرد")),
    Group(DEVICES, listOf("دوربین بارکدخوان", "دستگاه‌های وارد شده")),
    Group(DATA, listOf("داده‌ها")),
    Group(ABOUT, listOf("درباره برنامه")),
  )

  /** گروهِ یک بخش؛ null یعنی بخشِ ناشناس (آزمون جلویش را می‌گیرد) */
  fun groupOf(section: String): String? = GROUPS.firstOrNull { section in it.sections }?.title

  /** همهٔ بخش‌ها به ترتیبِ نمایش */
  val sections: List<String> get() = GROUPS.flatMap { it.sections }
}
