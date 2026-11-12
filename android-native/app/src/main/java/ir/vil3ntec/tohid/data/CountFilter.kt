package ir.vil3ntec.tohid.data

/*
 *  INV-05 — صافیِ «انبارگردانی» (`stock/count` ِ نسخهٔ ۳ با `CountState.rows`، و «انبارگردانیِ تازه»): فقط کالاهای یک دسته یا یک تأمین‌کننده دیده شوند تا
 *  شمارشِ جزئی (یک قفسه، یک تأمین‌کننده) آسان شود. صافی فقط فهرستِ دیده‌شده را کم می‌کند؛ شمرده‌ها و ثبت
 *  همان است (خانهٔ خالی یعنی شمرده نشد). بی صافی همان فهرستِ پیشین.
 *  برند (PRD-06، `Product.brand`) و قفسه (PRD-26، راهرو + قفسهٔ `ShelfLocation`؛ طبقه جدا نمی‌شود) هم صافی‌اند؛
 *  دکانی که هیچ کالایش برند یا قفسه ندارد هیچ تراشهٔ تازه‌ای نمی‌بیند (فهرست‌های خالی ⇒ همان صفحهٔ پیشین).
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

  /** برندهای کالاهای فعال، یکتا و الفبایی؛ «بی‌برند» جدا نمی‌آید */
  fun brands(products: List<Product>): List<String> =
    products.asSequence().filter { it.active }.mapNotNull { it.brand?.trim()?.takeIf { b -> b.isNotEmpty() } }.distinct().sorted().toList()

  private const val SEP = '\u001F'

  /** کلیدِ قفسهٔ کالا = راهرو + قفسه (همان تمیزکاریِ `ShelfLocation.of`)؛ بی هر دو ⇒ null */
  fun shelfKey(p: Product): String? {
    val loc = ir.vil3ntec.tohid.core.product.ShelfLocation.of(p.aisle, p.shelf, null)
    return if (loc.aisle.isEmpty() && loc.shelf.isEmpty()) null else "${loc.aisle}$SEP${loc.shelf}"
  }

  /** راهرو و قفسهٔ یک کلید (برای برچسبِ تراشه) */
  fun shelfParts(key: String): Pair<String, String> = key.substringBefore(SEP) to key.substringAfter(SEP, "")

  /** برچسبِ تراشهٔ قفسه: «راهرو ۲ · قفسه ۱» (هر بخشِ خالی نمی‌آید) — هر دو پنجرهٔ انبارگردانی */
  fun shelfLabel(key: String): String {
    val (aisle, shelf) = shelfParts(key)
    return listOfNotNull(
      aisle.takeIf { it.isNotEmpty() }?.let { ir.vil3ntec.tohid.core.i18n.StrProduct.aisleIs(it) },
      shelf.takeIf { it.isNotEmpty() }?.let { ir.vil3ntec.tohid.core.i18n.StrProduct.shelfIs(it) },
    ).joinToString(" · ")
  }

  /** قفسه‌های کالاهای فعال، یکتا و مرتب */
  fun shelves(products: List<Product>): List<String> =
    products.asSequence().filter { it.active }.mapNotNull(::shelfKey).distinct().sorted().toList()

  /** کالاهای فعالِ همان جست‌وجو (نام یا بارکد)، دسته، تأمین‌کننده، برند و قفسه؛ `null` ⇒ بی صافی */
  fun shown(
    products: List<Product>,
    term: String,
    category: String? = null,
    supplierId: String? = null,
    brand: String? = null,
    shelf: String? = null,
  ): List<Product> {
    val q = term.trim()
    return products.filter { it.active }
      .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.barcodes.any { b -> b.contains(q) } }
      .filter { category == null || it.category.trim() == category }
      .filter { supplierId == null || it.supplierId == supplierId }
      .filter { brand == null || it.brand?.trim() == brand }
      .filter { shelf == null || shelfKey(it) == shelf }
      .sortedBy { it.name }
  }
}
