package ir.vil3ntec.tohid.ui.components.common

/**
 *  انتخاب و عملیاتِ گروهی — منطقِ خالص (MASTER-SPEC v3 §۱۹.۵ بندهای ۴ و ۱۰).
 *
 *  • عملیات فقط روی ردیف‌های **انتخاب‌شده** اجرا می‌شود؛ ردیفی که در این
 *    فاصله از دفتر رفته (`prune`) کنار گذاشته و جدا گزارش می‌شود.
 *  • پیش از اجرا: پیش‌نمایشِ تعداد و اثر؛ پس از اجرا: نتیجهٔ **هر ردیف**
 *    تا ردیف‌های ناموفق پیدا باشند.
 *  • کارِ خطرناک (حذف) دلیل می‌خواهد (`reasonValid`).
 */
object Selection {
  fun toggle(selected: Set<String>, id: String): Set<String> =
    if (id in selected) selected - id else selected + id

  /** همهٔ ردیف‌های دیده‌شده انتخاب‌اند؟ (فهرستِ خالی ⇒ نه) */
  fun allSelected(selected: Set<String>, visible: List<String>): Boolean =
    visible.isNotEmpty() && visible.all { it in selected }

  fun someSelected(selected: Set<String>, visible: List<String>): Boolean =
    visible.any { it in selected }

  /**
   *  چک‌باکسِ سرستون: اگر همهٔ ردیف‌های دیده‌شده انتخاب‌اند، همان‌ها از
   *  انتخاب بیرون می‌روند؛ وگرنه همه‌شان اضافه می‌شوند. انتخابِ ردیف‌هایی
   *  که در فیلترِ فعلی دیده نمی‌شوند دست نمی‌خورد.
   */
  fun toggleAll(selected: Set<String>, visible: List<String>): Set<String> =
    if (allSelected(selected, visible)) selected - visible.toSet() else selected + visible

  /** شناسه‌هایی که دیگر در دفتر نیستند (حذف یا همگام‌سازی) کنار می‌روند */
  fun prune(selected: Set<String>, existing: Set<String>): Set<String> =
    if (selected.all { it in existing }) selected else selected.filterTo(LinkedHashSet()) { it in existing }

  /** چند تا از انتخاب‌شده‌ها در فیلترِ فعلی دیده نمی‌شوند — در پیش‌نمایش گفته می‌شود */
  fun hiddenCount(selected: Set<String>, visible: List<String>): Int {
    val shown = visible.toHashSet()
    return selected.count { it !in shown }
  }
}

/** نتیجهٔ یک ردیف پس از کارِ گروهی */
data class BulkRowResult(val id: String, val label: String, val ok: Boolean, val note: String)

/** پیش‌نمایشِ یک ردیف پیش از کارِ گروهی: `changes = false` یعنی «بی‌تغییر می‌ماند» */
data class BulkPreviewRow(val id: String, val label: String, val changes: Boolean, val note: String = "")

object BulkModel {
  /** کمینهٔ طولِ دلیل برای کارِ خطرناک — «حذف» به‌تنهایی دلیل نیست */
  const val MIN_REASON = 3

  fun reasonValid(reason: String): Boolean = reason.trim().length >= MIN_REASON

  /** (موفق، ناموفق) */
  fun summary(results: List<BulkRowResult>): Pair<Int, Int> =
    results.count { it.ok } to results.count { !it.ok }

  /** ناموفق‌ها اول — تا کاربر دنبالشان نگردد */
  fun ordered(results: List<BulkRowResult>): List<BulkRowResult> =
    results.sortedBy { if (it.ok) 1 else 0 }
}
