package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.ProductAlternatives
import ir.vil3ntec.tohid.core.product.ProductInfo
import ir.vil3ntec.tohid.core.product.ShelfLocation
import ir.vil3ntec.tohid.core.text.CatalogMatch

/**
 *  PRD-02 و PRD-26 — مدلِ دفتر (`Product.namePs`، `nameEn`، `synonyms`، `keywords`، `aisle`، `shelf`،
 *  `shelfLevel`) ⇄ شکلِ تمیزِ هسته (`core/product/ProductInfo`).
 *
 *  ⛔ خالی همیشه `null` است تا کلید نوشته نشود: کالایی که این‌ها را ندارد همان JSON ِ پیشین را دارد.
 *  ⛔ صدازننده فقط وقتی تنظیمِ همین دستگاه (`ProductInfoPrefs`) روشن است [matches] و [searchItem] با
 *  `extra = true` را می‌خواند؛ خاموش ⇒ جست‌وجو همان نامِ دری و بارکدِ پیشین.
 */
object ProductInfoMap {

  /** هیچ‌کدام از این فیلدها پُر نیست — راهِ کوتاهِ جست‌وجو روی هزاران کالا */
  fun hasNone(p: Product): Boolean =
    p.namePs.isNullOrEmpty() && p.nameEn.isNullOrEmpty() && p.synonyms.isNullOrEmpty() &&
      p.keywords.isNullOrEmpty() && p.aisle.isNullOrEmpty() && p.shelf.isNullOrEmpty() && p.shelfLevel.isNullOrEmpty() &&
      p.alternatives.isNullOrEmpty() && p.customFields.isNullOrEmpty()

  fun of(p: Product): ProductInfo = ProductInfo(
    namePs = p.namePs.orEmpty(),
    nameEn = p.nameEn.orEmpty(),
    synonyms = p.synonyms.orEmpty(),
    keywords = p.keywords.orEmpty(),
    location = shelf(p),
    alternatives = p.alternatives.orEmpty(),
    custom = p.customFields.orEmpty().toList(),
  )

  fun shelf(p: Product): ShelfLocation = ShelfLocation.of(p.aisle, p.shelf, p.shelfLevel)

  /** همهٔ فیلدها از [info]؛ خالی ⇒ `null` (پاک شدن هم همگام می‌شود: `LedgerDiff` کلیدِ برداشته را null می‌فرستد) */
  fun apply(p: Product, info: ProductInfo): Product = p.copy(
    namePs = info.namePs.ifEmpty { null },
    nameEn = info.nameEn.ifEmpty { null },
    synonyms = info.synonyms.ifEmpty { null },
    keywords = info.keywords.ifEmpty { null },
    aisle = info.location.aisle.ifEmpty { null },
    shelf = info.location.shelf.ifEmpty { null },
    shelfLevel = info.location.level.ifEmpty { null },
    alternatives = info.alternatives.ifEmpty { null },
    //  ترتیبِ ورود می‌ماند (LinkedHashMap)
    customFields = if (info.custom.isEmpty()) null else LinkedHashMap<String, String>().apply { info.custom.forEach { (k, v) -> put(k, v) } },
  )

  /** پرس‌وجو در نامِ پشتو/انگلیسی، مترادف یا واژهٔ جست‌وجو هست؟ (نامِ دری و بارکد کارِ همان جست‌وجوی پیشین) */
  fun matches(p: Product, query: String): Boolean = !hasNone(p) && of(p).matches(query)

  /** کالا برای نمایهٔ `CatalogMatch` — `extra = false` ⇒ همان `Item(id, name, barcodes)` ِ پیشین */
  fun searchItem(p: Product, extra: Boolean): CatalogMatch.Item =
    if (!extra || hasNone(p)) CatalogMatch.Item(p.id, p.name, p.barcodes)
    else of(p).let { CatalogMatch.Item(p.id, p.name, p.barcodes, aliases = it.aliases, keywords = it.searchWords) }

  /**
   *  PRD-24 — دنبالهٔ پیامِ «فقط X موجود است» ِ صندوق: « — جایگزینِ موجود: Y (۵ عدد)، Z (۲ عدد)»؛
   *  تنظیمِ خاموش ([on] = false) یا بی جایگزینِ موجود ⇒ تهی، یعنی همان پیامِ پیشین.
   */
  fun insteadSuffix(d: ShopData, p: Product, on: Boolean): String {
    if (!on) return ""
    val list = suggestions(d, p)
    if (list.isEmpty()) return ""
    val text = list.joinToString("، ") { (q, s) -> "${q.name} (${ir.vil3ntec.tohid.qty(s)}${if (q.unit.isNotBlank()) " ${q.unit}" else ""})" }
    return " — " + ir.vil3ntec.tohid.core.i18n.StrProduct.instead(text)
  }

  /** کالاهای دفتر برای پیدا کردنِ جایگزین از روی نام یا بارکد (PRD-24) */
  fun refs(d: ShopData): List<ProductAlternatives.Ref> = d.products.map { ProductAlternatives.Ref(it.id, it.name, it.barcodes) }

  /**
   *  PRD-24 — جایگزین‌های فعال و موجودِ کالای [p] برای پیامِ صندوق (حداکثر `ProductAlternatives.SHOW`)،
   *  هر کدام با نام و موجودی؛ کالای بی جایگزین ⇒ تهی. فقط وقتی تنظیم روشن است صدا زده شود.
   */
  fun suggestions(d: ShopData, p: Product): List<Pair<Product, Double>> {
    val ids = p.alternatives.orEmpty()
    if (ids.isEmpty()) return emptyList()
    val byId = d.products.associateBy { it.id }
    val stock = HashMap<String, Double>()
    fun stockOf(id: String) = stock.getOrPut(id) { ShopStore.stock(d, id) }
    return ProductAlternatives.suggest(ids, p.id) { id ->
      byId[id]?.let { ProductAlternatives.Candidate(it.id, it.name, it.active, stockOf(it.id) > 1e-9) }
    }.map { c -> byId.getValue(c.id) to stockOf(c.id) }
  }
}
