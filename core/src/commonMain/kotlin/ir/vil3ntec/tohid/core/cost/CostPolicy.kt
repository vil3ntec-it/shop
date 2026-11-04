package ir.vil3ntec.tohid.core.cost

/**
 *  روشِ بهای تمام‌شده برای سود و حاشیه (RPT-03) — راهبرد.
 *
 *  D-23 گرفته‌شده به روشِ شرکت‌های بزرگ، قابلِ برگشت (۱۴۰۵/۰۸/۱۳، `docs/OWNER-DECISIONS-fa.md`، الف):
 *  **میانگینِ موزون** (IAS 2 هر دو را می‌پذیرد؛ خرید و فروش می‌مانند و FIFO از همان داده حساب‌شدنی است). پس:
 *  • [DEFAULT] و [DECIDED] هر دو میانگینِ موزون‌اند؛ صفحه `costApproved = approved(روش)` را به هسته می‌دهد؛
 *  • «قیمت خرید روی قلم» (رفتارِ پیشین) انتخابیِ همین دستگاه می‌ماند و همیشه «تقریبی» است؛
 *  • برگشتِ تصمیم فقط همین دو مقدار را عوض می‌کند، نه صفحه‌ها.
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

  /** پیشنهادِ سند (§۳، «روش بهای تمام‌شدهٔ پیشنهادی») و روشِ مصوبِ D-23 */
  MOVING_AVERAGE(
    "mavg", "میانگین موزون",
    "میانگینِ موزونِ متحرکِ هر کالا از ورودهای انبار، فروش و برگشت به ترتیبِ زمان؛ پیش از نخستین ورود همان قیمتِ روی قلم.",
  ),
}

object CostPolicy {
  /** پیش‌فرضِ هر دستگاهی که روشی نگزیده — همان روشِ مصوب */
  val DEFAULT: CostMethod = CostMethod.MOVING_AVERAGE

  /** روشِ مصوبِ D-23 (۱۴۰۵/۰۸/۱۳)؛ null یعنی هنوز تصمیم نشده */
  val DECIDED: CostMethod? = CostMethod.MOVING_AVERAGE

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
  fun explain(m: CostMethod, decided: CostMethod? = DECIDED): String = m.note + when {
    approved(m, decided) -> ""
    decided == null -> " روشِ بهای تمام‌شده هنوز تصمیم نشده (D-23)؛ سود تقریبی است."
    else -> " روشِ مصوبِ بها (D-23) «${decided.label}» است؛ این سود تقریبی است."
  }
}
