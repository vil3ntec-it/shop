package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.ProductListModel

/**
 *  حالتِ خالصِ فهرستِ کالاها (`products`) — فیلتر، مرتب‌سازی، آمار، نشانِ هر ردیف
 *  و مقصدِ اسکن. تعریفِ «کم» و «تمام‌شده» همان `ProductListModel.status` است (یعنی
 *  همان `DashboardEngine.board`)، پس `products?filter=low` ِ هشدارها همان کالاهایی را
 *  نشان می‌دهد که زنگ شمرده است. آزمون: `ProductsListModelTest`.
 */
object ProductsListModel {

  /** مرتب‌سازی — همان پنج گزینهٔ صفحهٔ پیشین (`?sort=`) */
  enum class Sort(val id: String) {
    NAME("name"), LOW_FIRST("stock_asc"), MOST_STOCK("stock_desc"), PRICIEST("price_desc"), NEWEST("newest");

    companion object {
      fun parse(id: String?): Sort = entries.firstOrNull { it.id == id } ?: NAME
    }
  }

  /** فیلترِ وضعیت (`?filter=low|out|inactive`) */
  enum class Filter(val id: String?, val tab: ProductListModel.Tab) {
    ALL(null, ProductListModel.Tab.ALL),
    LOW("low", ProductListModel.Tab.LOW),
    OUT("out", ProductListModel.Tab.OUT),
    INACTIVE("inactive", ProductListModel.Tab.INACTIVE);

    companion object {
      fun parse(id: String?): Filter = entries.firstOrNull { it.id != null && it.id == id } ?: ALL
    }
  }

  /** نمای فهرست یا شبکهٔ دوستونه — ترجیحِ همین دستگاه */
  enum class Layout(val id: String) {
    LIST("list"), GRID("grid");

    companion object {
      fun parse(id: String?): Layout = entries.firstOrNull { it.id == id } ?: LIST
    }
  }

  data class Query(
    val text: String = "",
    val category: String? = null,
    val filter: Filter = Filter.ALL,
    val sort: Sort = Sort.NAME,
  ) {
    /** چیزی فهرست را تنگ کرده؟ (برای «پاک کردنِ فیلترها») — مرتب‌سازی فیلتر نیست */
    val narrowed: Boolean get() = text.isNotBlank() || category != null || filter != Filter.ALL
  }

  /** حالتِ آغازین از پرسشِ مسیر (`products?q=&category=&filter=&sort=`) */
  fun fromArgs(q: String?, category: String?, filter: String?, sort: String?): Query =
    Query(q.orEmpty(), category?.takeIf { it.isNotBlank() }, Filter.parse(filter), Sort.parse(sort))

  /** ردیف‌های دیده‌شده: وضعیت ∩ دسته ∩ جست‌وجو، مرتب‌شده */
  fun rows(products: List<Product>, stockOf: (String) -> Double, query: Query): List<Product> {
    val list = products.filter { p ->
      ProductListModel.inTab(p, stockOf(p.id), query.filter.tab) &&
        (query.category == null || p.category == query.category) &&
        ProductListModel.matches(p, query.text)
    }
    return when (query.sort) {
      Sort.NAME -> list.sortedBy { it.name }
      Sort.LOW_FIRST -> list.sortedBy { stockOf(it.id) }
      Sort.MOST_STOCK -> list.sortedByDescending { stockOf(it.id) }
      Sort.PRICIEST -> list.sortedByDescending { it.salePrice }
      Sort.NEWEST -> list.sortedByDescending { it.createdAt }
    }
  }

  /** کارتِ بالای فهرست: شمارِ اقلام و سودی که اگر همهٔ موجودی فروخته شود می‌ماند */
  data class Stats(val items: Int, val potentialProfit: Double)

  /** همان حسابِ صفحهٔ پیشین: Σ موجودی × فروش − Σ موجودی × خرید */
  fun stats(products: List<Product>, stockOf: (String) -> Double): Stats {
    var buy = 0.0
    var sell = 0.0
    products.forEach {
      val left = stockOf(it.id)
      buy += left * it.purchasePrice
      sell += left * it.salePrice
    }
    return Stats(products.size, sell - buy)
  }

  /** شمارِ هر فیلترِ وضعیت — کنارِ تراشه‌ها */
  fun counts(products: List<Product>, stockOf: (String) -> Double): Map<Filter, Int> {
    val byTab = ProductListModel.counts(products, stockOf)
    return Filter.entries.associateWith { byTab[it.tab] ?: 0 }
  }

  /** نشانِ ردیف: فقط وقتی چیزی گفتنی است (کم، تمام‌شده، غیرفعال) */
  enum class Badge { NONE, LOW, OUT, INACTIVE }

  fun badge(p: Product, stock: Double): Badge = when (ProductListModel.status(p, stock)) {
    ProductListModel.Status.INACTIVE -> Badge.INACTIVE
    ProductListModel.Status.OUT -> Badge.OUT
    ProductListModel.Status.LOW -> Badge.LOW
    ProductListModel.Status.ACTIVE -> Badge.NONE
  }

  /** فهرستِ خالی چرا خالی است */
  enum class Empty { NONE, NO_PRODUCTS, NO_MATCH }

  fun empty(total: Int, shown: Int): Empty = when {
    total == 0 -> Empty.NO_PRODUCTS
    shown == 0 -> Empty.NO_MATCH
    else -> Empty.NONE
  }

  /** اسکنِ فهرست: کالای شناخته ⇐ صفحهٔ همان کالا؛ ناشناس ⇐ فرمِ کالای تازه با همین بارکد */
  fun scanTarget(barcodes: Map<String, String>, code: String): String {
    val c = code.trim()
    return barcodes[c]?.let(ProductsPaths::item) ?: ProductsPaths.new(barcode = c)
  }

  /** شناسهٔ کالاهایی که در `after` هستند و در `before` نبودند (برای درخششِ ردیف‌های تازه) */
  fun added(before: ShopData, after: ShopData): List<String> {
    val old = before.products.mapTo(HashSet()) { it.id }
    return after.products.filter { it.id !in old }.map { it.id }
  }
}
