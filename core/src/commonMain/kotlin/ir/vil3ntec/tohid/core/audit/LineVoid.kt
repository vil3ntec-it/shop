package ir.vil3ntec.tohid.core.audit

/**
 *  RPT-09 — «حذفِ ردیف» ِ فاکتورِ باز، برای گزارشِ ضدتقلب به تفکیکِ کاربر.
 *
 *  هستهٔ گزارش (`ReportCore.fraud`، `license/report-core.js`) ردیفِ سابقهٔ [TYPE] را با
 *  `by` و `amount` می‌شمارد؛ این‌جا فقط قاعدهٔ «کدام ردیف حذف شد» و «به نامِ چه کسی» است،
 *  تا صفحه‌های گوشی و کامپیوتر (و بعداً آیفون) هر کدام دوباره ننویسندش.
 *
 *  - ردیفِ حذف‌شده = ردیفی از سبدِ پیشین که کلیدش در سبدِ تازه نیست. کم شدنِ مقدار حذف نیست.
 *  - کلیدِ تکراری یک بار شمرده می‌شود؛ ترتیب همان ترتیبِ سبدِ پیشین است.
 *  - کاربر فقط نامِ صریحِ حسابِ همین دستگاه است؛ خالی ⇒ `null` (نامعلوم)، هرگز نامِ ساختگی.
 */
object LineVoid {
  /** نوعِ ردیفِ سابقه — همان نامی که هستهٔ گزارش می‌خواند */
  const val TYPE = "line_delete"

  /** کلیدِ ردیف‌هایی از [before] که در [after] نیستند */
  fun removed(before: List<String>, after: List<String>): List<String> {
    if (before.isEmpty()) return emptyList()
    val kept = after.toHashSet()
    val seen = HashSet<String>()
    return before.filter { it !in kept && seen.add(it) }
  }

  /** نامِ کاربر برای ردیفِ سابقه: خالی یا فقط فاصله ⇒ `null` */
  fun who(name: String?): String? = name?.trim()?.ifEmpty { null }
}
