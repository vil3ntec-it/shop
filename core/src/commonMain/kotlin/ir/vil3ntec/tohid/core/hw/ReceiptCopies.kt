package ir.vil3ntec.tohid.core.hw

/**
 *  شمارِ نسخهٔ رسیدِ خودکار پس از فروش (POS-35) — تنظیمِ **هر دستگاه**، منطقِ خالص.
 *
 *  قاعده‌ها:
 *    • پیش‌فرض و هر مقدارِ خراب یا نبوده ⇒ **۱** = همان یک رسیدِ امروز، بایت‌به‌بایت.
 *    • بیشینه [MAX]: تنظیمِ اشتباه کاغذ و صف را پر نکند.
 *    • نسخهٔ اول (اصل) بی نشان است؛ نسخهٔ k از n (k > 1) روی کاغذ «نسخهٔ k از n» دارد
 *      تا رسیدِ دوم با اصل اشتباه نشود (مثلاً برای برگشتِ دوباره).
 *    • هر نسخه کارِ جدای صف است (`PrintQueue.Job.copy`): به ترتیب، و تلاشِ دوبارهٔ یکی
 *      نسخه‌های دیگر را دوباره چاپ نمی‌کند. تلاشِ دوباره نشانِ «چاپِ دوباره» را هم کنارِ
 *      نشانِ نسخه می‌برد ([retryMark]).
 */
object ReceiptCopies {

  const val MIN = 1
  const val MAX = 3
  const val DEFAULT = 1

  /** هر عددِ بیرون از بازه به نزدیک‌ترین مرز */
  fun clamp(n: Int): Int = n.coerceIn(MIN, MAX)

  /** مقدارِ ذخیره‌شدهٔ دستگاه ⇒ شمار؛ نبوده یا خراب ⇒ [DEFAULT] */
  fun parse(raw: String?): Int = raw?.trim()?.toIntOrNull()?.let(::clamp) ?: DEFAULT

  /** شماره‌های نسخه برای یک فروش، از ۱ */
  fun plan(copies: Int): List<Int> = (1..clamp(copies)).toList()

  /** «نسخهٔ k از n» برای نسخهٔ دوم به بعد (عنوانِ کارِ صف)؛ اصل (یا تک‌نسخه) ⇒ null */
  fun label(copy: Int, of: Int): String? {
    val n = clamp(of)
    require(copy in 1..n) { "نسخهٔ $copy از $n نیست" }
    if (copy == 1) return null
    return "نسخهٔ ${fa(copy)} از ${fa(n)}"
  }

  /** نشانِ روی کاغذ — یک خط، به همان شکلِ نشانِ «چاپِ دوباره»؛ اصل بی نشان ⇒ null */
  fun mark(copy: Int, of: Int): String? = label(copy, of)?.let { "— $it —" }

  /**
   *  نشانِ تلاشِ دوبارهٔ همین نسخه. اصل ⇒ همان [reprintMark] (همان کاغذِ امروز)؛
   *  نسخهٔ دوم به بعد ⇒ هر دو در یک خط: «— نسخهٔ ۲ از ۲ · چاپِ دوباره —».
   */
  fun retryMark(copy: Int, of: Int, reprintMark: String): String =
    label(copy, of)?.let { "— $it · ${reprintMark.trim('—', ' ')} —" } ?: reprintMark

  private fun fa(n: Int): String = n.toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
}
