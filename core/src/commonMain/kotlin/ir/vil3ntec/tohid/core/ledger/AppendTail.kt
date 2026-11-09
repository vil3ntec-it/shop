package ir.vil3ntec.tohid.core.ledger

/**
 *  POS-42 — «فقط افزوده شد؟»: پایهٔ ساختنِ افزایشیِ جدول‌هایی که از کلِ دفتر ساخته می‌شوند (جدولِ موجودی، شمارِ فروشِ
 *  هر کالا). ثبتِ فروش فهرستِ فاکتورها و اقلام را فقط **به ته** می‌افزاید (`d.sales + sale`)؛ پس جدولِ دفترِ پیشین با
 *  همان ردیف‌های تازه همان جدولِ ساختنِ کامل است — جمع‌ها به همان ترتیب، پس همان عددِ دقیق.
 *
 *  ⛔ برابری با **همان شیء** سنجیده می‌شود (`===`)، نه برابریِ محتوا: ردیفِ پیشینی که عوض شد (لغو، برگشت، ویرایش، ادغامِ
 *  همگام‌سازی) شیءِ تازه است ⇒ `null` ⇒ فراخوان از نو و کامل می‌سازد. هرگز حدس نمی‌زند.
 */
object AppendTail {

  /** [now] = [before] + ردیف‌های تازه با همان شیءها به همان ترتیب ⇒ همان ردیف‌های تازه (شاید تهی)؛ وگرنه `null` */
  fun <T> of(before: List<T>, now: List<T>): List<T>? {
    if (now === before) return emptyList()
    if (now.size < before.size) return null
    val a = before.iterator()
    val b = now.iterator()
    while (a.hasNext()) if (a.next() !== b.next()) return null
    return now.subList(before.size, now.size)
  }
}
