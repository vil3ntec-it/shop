package ir.vil3ntec.tohid.data

/**
 *  تنها درِ نوشتنِ دفتر، بی `Context` تا آزمون‌پذیر باشد — `ShopStore` با قفلِ
 *  موتورِ همگام‌سازی و «نوشتنِ دیسک + op» می‌سازدش.
 *
 *    • `save` دفترِ آمادهٔ صفحه را می‌نویسد — رفتارِ همیشگی.
 *    • `edit` تغییرِ کوچکی را **زیرِ همان قفل** روی دفترِ همان لحظه می‌سازد.
 *      نوشتنِ پس‌زمینه (ردیفِ سابقهٔ صفِ چاپ) هرگز از تصویرِ کهنه ساخته
 *      نمی‌شود: فروشی که هم‌زمان ثبت شد گم نمی‌شود و `record` برایش opِ حذف
 *      به سرور نمی‌فرستد.
 *
 *  [current] دفترِ همین لحظهٔ **دیسک** است (`ShopStore.freshLocked`)، نه فقط دفترِ حافظهٔ این
 *  نمونه؛ `null` یعنی دیسک جلوتر بود و خوانده نشد ⇒ هیچ نوشتنی.
 */
class LedgerWriter(
  private val lock: Any,
  private val current: () -> ShopData?,
  private val write: (ShopData) -> Boolean,
  /**
   *  STF-09: مُهر و زنجیرهٔ ردیف‌های تازهٔ سابقه (`AuditSeal`، `prior` = `base` = دفترِ همان لحظه)؛ زیرِ همین قفل،
   *  پس افزودن‌های هم‌زمانِ یک دستگاه پشتِ هم‌اند. `null` ⇐ بی مُهر (آزمون‌های پیشین).
   */
  private val seal: ((prior: ShopData, next: ShopData) -> AuditSeal.Plan)? = null,
) {
  /**
   *  [base] `null`: جایگزینیِ کامل (بازیابی، بازسازی، «پاک کردنِ همه») — بی مُهر، ردیف‌ها مالِ این لحظه نیستند.
   *  [base] ِ صریح (راهِ پیشینِ بی ادغام): ردیف‌های تازه نسبت به [base] مُهر می‌خورند.
   */
  fun save(next: ShopData, base: ShopData? = null): Boolean = synchronized(lock) { if (base == null) write(next) else sealed(base, next) }

  fun edit(change: (ShopData) -> ShopData): Boolean = synchronized(lock) {
    val cur = current() ?: return@synchronized false
    sealed(cur, change(cur))
  }

  private fun sealed(prior: ShopData, next: ShopData): Boolean {
    val plan = seal?.let { runCatching { it(prior, next) }.getOrNull() } ?: return write(next)
    return write(plan.data).also { if (it) plan.commit() }
  }

  companion object {
    /** ردیفِ سابقه ته دفترِ همان لحظه؛ ردیف را همان دفتر می‌سازد (مثلاً صندوقِ باز برای `no_sale`) */
    fun appendAudit(row: (ShopData) -> AuditEntry): (ShopData) -> ShopData =
      { d -> d.copy(auditLog = d.auditLog + row(d)) }

    /** چند ردیفِ سابقه در یک نوشتن (RPT-09: ردیف‌های حذف‌شدهٔ یک کار)؛ فهرستِ خالی ⇒ دفتر همان */
    fun appendAudits(rows: (ShopData) -> List<AuditEntry>): (ShopData) -> ShopData =
      { d -> rows(d).let { r -> if (r.isEmpty()) d else d.copy(auditLog = d.auditLog + r) } }
  }
}
