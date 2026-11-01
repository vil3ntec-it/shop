package ir.vil3ntec.tohid.core.cost

/**
 *  روشِ بهای تمام‌شده برای سود و حاشیه (RPT-03) — راهبرد، نه تصمیم.
 *
 *  تصمیمِ D-23 (روشِ بها و مهاجرتِ دادهٔ تاریخی با تأییدِ حسابدار/مالک) **هنوز باز است**
 *  (`docs/spec/BLOCKERS.md`، §۱۵.۴ بند ۴، §۲۸.۴). پس:
 *  • پیش‌فرض همان رفتارِ امروز است: بهای هر قلم = قیمتِ خریدِ ثبت‌شده روی همان قلم؛
 *  • روشِ دیگر فقط «پیش‌نمایش» است و هیچ روشی «مصوب» نیست ⇒ هر سودی «تقریبی» برچسب می‌خورد؛
 *  • روزی که D-23 تصمیم شد، فقط [DECIDED] (و در صورتِ نیاز [DEFAULT]) عوض می‌شود، نه صفحه‌ها.
 *
 *  خالص (commonMain): نه دیسک، نه ساعت. شناسه‌ها همان `costing` ِ هستهٔ گزارش‌اند
 *  (`ReportCore.Costing` و `opts.costing` ِ `license/report-core.js`): `line` و `mavg`.
 */
enum class CostMethod(val id: String, val label: String, val note: String) {
  /** رفتارِ امروز */
  PURCHASE_PRICE(
    "line", "قیمت خرید روی قلم",
    "بهای هر قلم همان قیمتِ خریدی است که هنگامِ فروش روی قلم ثبت شد (رفتارِ پیشین).",
  ),

  /** پیشنهادِ سند (§۳، «روش بهای تمام‌شدهٔ پیشنهادی»)؛ تا D-23 فقط پیش‌نمایش */
  MOVING_AVERAGE(
    "mavg", "میانگین موزون (پیش‌نمایش)",
    "میانگینِ موزونِ متحرکِ هر کالا از ورودهای انبار، فروش و برگشت به ترتیبِ زمان؛ پیش از نخستین ورود همان قیمتِ روی قلم.",
  ),
}

object CostPolicy {
  /** رفتارِ امروز — تا تصمیمِ D-23 عوض نمی‌شود */
  val DEFAULT: CostMethod = CostMethod.PURCHASE_PRICE

  /** روشِ مصوبِ D-23؛ null یعنی هنوز تصمیم نشده */
  val DECIDED: CostMethod? = null

  /** مقدارِ ذخیره‌شدهٔ تنظیم ⇒ روش؛ نبود یا ناشناخته ⇒ [DEFAULT] (هرگز خطا) */
  fun parse(stored: String?): CostMethod = CostMethod.entries.firstOrNull { it.id == stored?.trim() } ?: DEFAULT

  /** سودِ این روش قطعی است؟ فقط اگر D-23 همین روش را مصوب کرده باشد */
  fun approved(m: CostMethod, decided: CostMethod? = DECIDED): Boolean = decided != null && m == decided

  /** برچسبِ عددِ سود/بها: «سود ناخالص (تقریبی)» یا «… (تقریبی، میانگین موزون)» */
  fun label(base: String, m: CostMethod, decided: CostMethod? = DECIDED): String {
    val tags = buildList {
      if (!approved(m, decided)) add("تقریبی")
      if (m != DEFAULT) add(m.label.substringBefore(" (").trim())
    }
    return if (tags.isEmpty()) base else "$base (${tags.joinToString("، ")})"
  }

  /** یک جمله برای زیرِ کارت‌های سود: روش، و چرا تقریبی است */
  fun explain(m: CostMethod, decided: CostMethod? = DECIDED): String =
    m.note + if (approved(m, decided)) "" else " روشِ بهای تمام‌شده هنوز تصمیم نشده (D-23)؛ سود تقریبی است."
}
