package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.LocalV3RowHue
import ir.vil3ntec.tohid.ui.theme.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Hue

/**
 *  **یک** نقشهٔ رنگِ کاشیِ ردیف‌های تنظیمات (۶۵r) — هیچ صفحه‌ای رنگ را خودش نمی‌گذارد:
 *
 *  • هر دسته رنگِ همان کاشیِ خودش در «بیشتر» را دارد ([GROUP]، بی تغییر از پیش).
 *  • هر بخشِ کوتاهِ دسته (`SettingsLayout.Part`) یک رنگ دارد ([PART]): بخشِ اولِ هر دسته همان رنگِ دسته، و بخش‌های
 *    دیگرِ همان صفحه رنگ‌های **جدا** از پالتِ همراهِ «بیشتر» (`V3Hue`) — نه برند کنارِ لاجوردی/نیلی (در تمِ «اقیانوس» و
 *    «شامگاه» تقریباً یکی‌اند). معنا همان نقشهٔ ماژول‌ها (`V3Modules.HUE_BY_ID`): فاکتور نیلیِ تاریخچه، گزارش لاجوردی،
 *    کالا گرافیتِ انبار، آنلاین نفتیِ سفارش (پالتِ لوکسِ ۶۸c).
 *  • هر ردیف رنگِ بخشِ خودش را دارد ([of])؛ ردیفِ ماژول رنگِ بخشی که در آن نشسته؛ ردیف‌های درونِ صفحهٔ یک بخش رنگِ
 *    همان بخش ([page]، از `SettingsPageFrame` در `LocalV3RowHue`).
 *
 *  ۶۵s: بخش‌هایی که با بخش‌های برنامه هم‌پوشانی دارند (فاکتور ⇐ فروش، گزارش، کالا ⇐ انبار، آنلاین ⇐ سفارش) رنگشان را از همان
 *  منبعِ رنگِ بخش‌ها می‌خوانند (`ui/theme/SectionColors.kt` ⇐ `V3Section.hue`) — یک منبع، نه دو نقشهٔ ناهم‌خوان.
 */
internal object SettingsHues {

  /** رنگِ هر دسته — همان کاشیِ «بیشتر ⇐ تنظیمات» و صفحهٔ اولِ تنظیمات */
  val GROUP: Map<String, V3Hue> = mapOf(
    SettingsLayout.SHOP to V3Hue.BRAND,
    SettingsLayout.SALES to V3Hue.ORANGE,
    SettingsLayout.GOODS to V3Section.STOCK.hue,
    SettingsLayout.DEVICES to V3Hue.TEAL,
    SettingsLayout.DATA to V3Hue.PURPLE,
    SettingsLayout.LOOK to V3Hue.BLUE,
    //  نامِ گروه‌های پیشین (بدنهٔ قدیمیِ بی‌مسیر)
    SettingsLayout.GENERAL to V3Hue.BLUE,
    SettingsLayout.SECURITY to V3Hue.BRAND,
    SettingsLayout.ABOUT to V3Hue.BLUE,
  )

  /** رنگِ هر بخشِ کوتاه (`SettingsLayout.Part.id`) — بخشِ اول = رنگِ دسته؛ در یک صفحه هیچ دو بخشی هم‌رنگ نیستند */
  val PART: Map<String, V3Hue> = mapOf(
    //  دکان و امنیت: دکان (برند) · ورود و قفل (عقیق) · نگهداریِ دفتر (نفتی)
    "shop" to V3Hue.BRAND,
    "access" to V3Hue.CORAL,
    "protect" to V3Hue.TEAL,
    //  فروش و پرداخت: پرداخت (زر) · فاکتور (نیلی) · رسید (لاجوردی) · صندوق (زمردی) · آنلاین (نفتی) — ۶۸c: بی لیمویی و مرجانی
    "pay" to V3Hue.ORANGE,
    "invoice" to V3Section.SALES.hue,
    "receipt" to V3Hue.SKY,
    "till" to V3Hue.MINT,
    "online" to V3Section.ORDERS.hue,
    //  کالا و انبار: کالا (گرافیت) · انبار (زر)
    "goods" to V3Section.STOCK.hue,
    "stock" to V3Hue.AMBER,
    //  دستگاه‌ها
    "devices" to V3Hue.TEAL,
    //  داده و پشتیبان: داده (نیلی) · گزارش (لاجوردی)
    "data" to V3Hue.PURPLE,
    "reports" to V3Section.REPORTS.hue,
    //  ظاهر و زبان: ظاهر (لاجوردی) · کار با برنامه (زمردی)
    "look" to V3Hue.BLUE,
    "use" to V3Hue.MINT,
  )

  /** رنگِ «قابلیت‌ها» (پیوندِ زیرِ شش دسته و نتیجهٔ جست‌وجویش) */
  val FEATURES: V3Hue = V3Hue.BRAND

  /** رنگِ ردیفِ «تنظیمات» در جاهای دیگر («حساب من»، مرکزِ مدیریت) — ماژولِ تنظیمات رنگِ ثبت‌شده ندارد */
  val ROOT: V3Hue = V3Hue.BRAND

  fun group(title: String): V3Hue? = GROUP[title]

  fun part(id: String?): V3Hue? = id?.let { PART[it] }

  /** رنگِ ردیفِ یک بخش (یا یک دسته): بخشِ خودش، وگرنه دسته‌اش، وگرنه برند */
  fun of(key: String): V3Hue =
    part(SettingsLayout.partOf(key)) ?: GROUP[key] ?: SettingsLayout.groupOf(key)?.let { GROUP[it] } ?: V3Hue.BRAND

  /** ردیفِ ماژولی که در `part` ِ دستهٔ `group` نشسته (null ⇒ آخرین بخشِ همان دسته، همان قاعدهٔ `rememberCategoryParts`) */
  fun extra(group: String, partId: String?): V3Hue =
    part(partId ?: SettingsLayout.GROUPS.firstOrNull { it.title == group }?.parts?.lastOrNull()?.id) ?: GROUP[group] ?: V3Hue.BRAND

  /** رنگِ ردیف‌های درونِ صفحهٔ بخشی که همین حالا کشیده می‌شود (`SettingsPageFrame` از کلیدِ صفحه در `LocalV3RowHue` می‌گذارد) */
  val page: V3Hue
    @Composable get() = LocalV3RowHue.current ?: V3Hue.BRAND
}
