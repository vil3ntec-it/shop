package ir.vil3ntec.tohid.data

/**
 *  S125: نمایه‌های کالا برای **همان** فهرست (همان شیءِ `d.products`) یک بار ساخته می‌شوند — با ۵۰ هزار کالا هر اسکن
 *  پیش‌تر نمایهٔ بارکد را دو بار از نو می‌ساخت و هر ردیفِ سبد چند جست‌وجوی خطی داشت. فهرستِ تازه (ویرایشِ کالا،
 *  `d.copy(products = …)`) ⇒ شیءِ تازه ⇒ نمایهٔ تازه. خروجی فقط‌خواندنی است. راه: `ShopStore.barcodeIndex/product/productsById`.
 */
internal object ProductIndexes {

  class LastOf<V>(private val build: (List<Product>) -> V) {
    @Volatile private var last: Pair<List<Product>, V>? = null
    operator fun invoke(ps: List<Product>): V {
      val l = last
      if (l != null && l.first === ps) return l.second
      return build(ps).also { last = ps to it }
    }
  }

  val barcodes = LastOf<Map<String, String>> { ps -> buildMap { ps.forEach { p -> p.barcodes.forEach { code -> put(code, p.id) } } } }

  /** شناسه ⇒ کالا؛ شناسهٔ تکراری ⇒ نخستین، همان `products.find { it.id == … }` */
  val byId = LastOf<Map<String, Product>> { ps -> HashMap<String, Product>(ps.size * 2).also { m -> ps.forEach { m.putIfAbsent(it.id, it) } } }
}
