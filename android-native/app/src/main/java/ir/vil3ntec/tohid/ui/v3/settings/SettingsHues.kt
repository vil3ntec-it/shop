package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.LocalV3RowHue
import ir.vil3ntec.tohid.ui.v3.V3Hue

/**
 *  **یک** نقشهٔ رنگِ کاشیِ ردیف‌های تنظیمات (۶۵r) — هیچ صفحه‌ای رنگ را خودش نمی‌گذارد:
 *
 *  • هر دسته رنگِ همان کاشیِ خودش در «بیشتر» را دارد ([GROUP]، بی تغییر از پیش).
 *  • هر بخشِ کوتاهِ دسته (`SettingsLayout.Part`) یک رنگ دارد ([PART]): بخشِ اولِ هر دسته همان رنگِ دسته، و بخش‌های
 *    دیگرِ همان صفحه رنگ‌های **جدا** از پالتِ همراهِ «بیشتر» (`V3Hue`) — نه برند کنارِ آسمانی/بنفش (در تمِ «اقیانوس» و
 *    «شامگاه» تقریباً یکی‌اند). معنا همان نقشهٔ ماژول‌ها (`V3Modules.HUE_BY_ID`): فاکتور بنفشِ تاریخچه، گزارش آسمانی،
 *    کالا کهربایی، مشتری و آنلاین مرجانی.
 *  • هر ردیف رنگِ بخشِ خودش را دارد ([of])؛ ردیفِ ماژول رنگِ بخشی که در آن نشسته؛ ردیف‌های درونِ صفحهٔ یک بخش رنگِ
 *    همان بخش ([page]، از `SettingsPageFrame` در `LocalV3RowHue`).
 *
 *  ⚠️ نشانه‌های رنگِ معناییِ بخش‌ها (۶۵s، کارت‌های خلاصه) که برسند، همین دو جدول به آن‌ها اشاره می‌کنند — جای دیگری
 *  دست نمی‌خورد (`SettingsHuesTest`).
 */
internal object SettingsHues {

  /** رنگِ هر دسته — همان کاشیِ «بیشتر ⇐ تنظیمات» و صفحهٔ اولِ تنظیمات */
  val GROUP: Map<String, V3Hue> = mapOf(
    SettingsLayout.SHOP to V3Hue.BRAND,
    SettingsLayout.SALES to V3Hue.ORANGE,
    SettingsLayout.GOODS to V3Hue.AMBER,
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
    //  دکان و امنیت: دکان (برند) · ورود و قفل (مرجانی) · نگهداریِ دفتر (سبزآبی)
    "shop" to V3Hue.BRAND,
    "access" to V3Hue.CORAL,
    "protect" to V3Hue.TEAL,
    //  فروش و پرداخت: پرداخت (نارنجی) · فاکتور (بنفش) · رسید (آسمانی) · صندوق (لیمویی) · آنلاین (مرجانی)
    "pay" to V3Hue.ORANGE,
    "invoice" to V3Hue.VIOLET,
    "receipt" to V3Hue.SKY,
    "till" to V3Hue.LIME,
    "online" to V3Hue.CORAL,
    //  کالا و انبار: کالا (کهربایی) · انبار (لیمویی)
    "goods" to V3Hue.AMBER,
    "stock" to V3Hue.LIME,
    //  دستگاه‌ها
    "devices" to V3Hue.TEAL,
    //  داده و پشتیبان: داده (بنفش) · گزارش (آسمانی)
    "data" to V3Hue.PURPLE,
    "reports" to V3Hue.SKY,
    //  ظاهر و زبان: ظاهر (آبی) · کار با برنامه (نعنایی)
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
