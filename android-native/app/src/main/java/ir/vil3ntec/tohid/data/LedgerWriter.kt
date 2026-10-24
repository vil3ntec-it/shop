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
 */
class LedgerWriter(
  private val lock: Any,
  private val current: () -> ShopData,
  private val write: (ShopData) -> Boolean,
) {
  fun save(next: ShopData): Boolean = synchronized(lock) { write(next) }

  fun edit(change: (ShopData) -> ShopData): Boolean = synchronized(lock) { write(change(current())) }

  companion object {
    /** ردیفِ سابقه ته دفترِ همان لحظه؛ ردیف را همان دفتر می‌سازد (مثلاً صندوقِ باز برای `no_sale`) */
    fun appendAudit(row: (ShopData) -> AuditEntry): (ShopData) -> ShopData =
      { d -> d.copy(auditLog = d.auditLog + row(d)) }
  }
}
