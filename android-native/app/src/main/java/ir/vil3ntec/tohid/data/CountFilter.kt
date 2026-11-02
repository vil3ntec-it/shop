package ir.vil3ntec.tohid.data

/*
 *  INV-05 — صافیِ «انبارگردانی» (`StockCountSheet`): فقط کالاهای یک دسته یا یک تأمین‌کننده دیده شوند تا
 *  شمارشِ جزئی (یک قفسه، یک تأمین‌کننده) آسان شود. صافی فقط فهرستِ دیده‌شده را کم می‌کند؛ شمرده‌ها و ثبت
 *  همان است (خانهٔ خالی یعنی شمرده نشد). بی صافی همان فهرستِ پیشین. برند و قفسه در مدلِ کالا نیستند.
 */
object CountFilter {

  /** دسته‌های کالاهای فعال، یکتا و الفبایی؛ «بی‌دسته» جدا نمی‌آید */
  fun categories(products: List<Product>): List<String> =
    products.asSequence().filter { it.active }.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted().toList()

  /** تأمین‌کننده‌هایی که دست‌کم یک کالای فعال دارند، به ترتیبِ نام */
  fun suppliers(products: List<Product>, suppliers: List<Supplier>): List<Supplier> {
    val used = products.asSequence().filter { it.active }.map { it.supplierId }.filter { it.isNotBlank() }.toHashSet()
    return suppliers.filter { it.id in used }.sortedBy { it.name.ifBlank { it.id } }
  }

  /** کالاهای فعالِ همان جست‌وجو (نام یا بارکد)، دسته و تأمین‌کننده؛ `null` ⇒ بی صافی */
  fun shown(products: List<Product>, term: String, category: String? = null, supplierId: String? = null): List<Product> {
    val q = term.trim()
    return products.filter { it.active }
      .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.barcodes.any { b -> b.contains(q) } }
      .filter { category == null || it.category.trim() == category }
      .filter { supplierId == null || it.supplierId == supplierId }
      .sortedBy { it.name }
  }
}
