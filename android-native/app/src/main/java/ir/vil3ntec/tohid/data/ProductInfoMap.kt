package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.ProductAlternatives
import ir.vil3ntec.tohid.core.product.ProductInfo
import ir.vil3ntec.tohid.core.product.ProductSku
import ir.vil3ntec.tohid.core.product.ShelfLocation
import ir.vil3ntec.tohid.core.text.CatalogMatch

/**
 *  PRD-02 و PRD-26 — مدلِ دفتر (`Product.namePs`، `nameEn`، `synonyms`، `keywords`، `aisle`، `shelf`،
 *  `shelfLevel`؛ PRD-03/09: `sku`، `stockless`) ⇄ شکلِ تمیزِ هسته (`core/product/ProductInfo`).
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
      p.alternatives.isNullOrEmpty() && p.customFields.isNullOrEmpty() && p.sku.isNullOrEmpty() && p.stockless != true &&
      p.kit.isNullOrEmpty() && p.priceWindows.isNullOrEmpty() && p.variantAxes.isNullOrEmpty() && p.serialTracked != true

  fun of(p: Product): ProductInfo = ProductInfo(
    namePs = p.namePs.orEmpty(),
    nameEn = p.nameEn.orEmpty(),
    synonyms = p.synonyms.orEmpty(),
    keywords = p.keywords.orEmpty(),
    location = shelf(p),
    alternatives = p.alternatives.orEmpty(),
    custom = p.customFields.orEmpty().toList(),
    sku = p.sku.orEmpty(),
    stockless = p.stockless == true,
    kit = ProductPlansMap.kitOf(p),
    prices = ProductPlansMap.windowsOf(p),
    axes = ProductVariantsMap.axesOf(p),
    serialTracked = p.serialTracked == true,
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
    sku = info.sku.ifEmpty { null },
    stockless = if (info.stockless) true else null,
    kit = ProductPlansMap.kitModel(info.kit),
    priceWindows = ProductPlansMap.windowsModel(info.prices),
    variantAxes = ProductVariantsMap.axesModel(info.axes),
    serialTracked = if (info.serialTracked) true else null,
  ).let { u ->
    //  PRD-09: خدماتی حدِ موجودی ندارد ⇒ هشدارِ کم‌موجودی و پیشنهادِ سفارش هرگز
    if (info.stockless) u.copy(minStock = 0.0, reorderPoint = null, maxStock = null) else u
  }

  /**
   *  PRD-09 — موجودیِ فروختنی برای سقفِ سبد و ثبتِ فروش: همان `ShopStore.stock`، جز کالای خدماتی یا بی
   *  موجودی که سقف ندارد. کالای بی این پرچم (همهٔ دفترهای پیشین) ⇒ دقیقاً همان `ShopStore.stock`.
   *  POS-27: منهای کالای کنارگذاشتهٔ امانی‌های باز جز سندِ [except] (`Layaways.reserved`؛ تنظیمِ خاموش ⇒ ۰ و همان عدد).
   */
  fun sellable(d: ShopData, productId: String, except: String? = null): Double {
    if (d.products.any { it.id == productId && it.stockless == true }) return STOCKLESS
    val stock = ShopStore.stock(d, productId)
    val held = Layaways.reserved(d, productId, except)
    return if (held == 0.0) stock else stock - held
  }

  /** «بی‌سقف» ِ کالای خدماتی — بزرگ ولی متناهی تا جمع و تفریقِ سبد سالم بماند */
  const val STOCKLESS: Double = 1e12

  /** ردپای ویرایشِ همین بخش (محلِ قفسه، جایگزین، فیلدِ سفارشی، کد، خدماتی) — همان متن‌های پیشین به همان ترتیب */
  fun changes(a: Product, b: Product): List<String> = buildList {
    if (a.aisle != b.aisle || a.shelf != b.shelf || a.shelfLevel != b.shelfLevel) add("محلِ قفسه عوض شد")
    if (a.alternatives != b.alternatives) add("کالاهای جایگزین عوض شد")
    if (a.customFields != b.customFields) add("فیلدهای سفارشی عوض شد")
    if (a.sku.orEmpty() != b.sku.orEmpty()) add("کدِ کالا «${a.sku.orEmpty()}» ⇐ «${b.sku.orEmpty()}»")
    if ((a.stockless == true) != (b.stockless == true)) add(if (b.stockless == true) "خدماتی (بی موجودی) شد" else "دیگر خدماتی نیست")
    addAll(ProductPlansMap.changes(a, b))
    if (a.variantAxes.orEmpty() != b.variantAxes.orEmpty()) add("ویژگی‌های تنوع عوض شد")
    if ((a.serialTracked == true) != (b.serialTracked == true)) add(if (b.serialTracked == true) "سریال‌دار شد" else "دیگر سریال‌دار نیست")
  }

  /**
   *  PRD-10 — کیتِ [info] برای ذخیره: جزءِ ناموجود، خودِ کالا، جزءِ کیت، یا کیت شدنِ کالایی که خودش جزءِ
   *  کیتِ دیگری است (تو در تو) ⇒ رد. کالای بی کیت ⇒ `null`.
   */
  fun kitProblem(d: ShopData, info: ProductInfo, selfId: String?): String? {
    if (info.kit.isEmpty()) return null
    val byId = d.products.associateBy { it.id }
    for (part in info.kit) {
      val q = byId[part.productId] ?: return ir.vil3ntec.tohid.core.i18n.StrProduct.kitPartGone
      if (q.id == selfId || ProductPlansMap.isKit(q)) return ir.vil3ntec.tohid.core.i18n.StrProduct.kitNested(q.name)
    }
    val usedIn = selfId?.let { id -> d.products.firstOrNull { k -> k.kit.orEmpty().any { it.productId == id } } }
    return usedIn?.let { ir.vil3ntec.tohid.core.i18n.StrProduct.kitNested(it.name) }
  }

  /** PRD-03 — کدِ کالاهای دیگرِ دفتر (شناسه ⇒ کد)، برای یکتایی و کدِ خودکار */
  fun skus(d: ShopData): Map<String, String> = buildMap { d.products.forEach { p -> p.sku?.takeIf { it.isNotEmpty() }?.let { put(p.id, it) } } }

  /**
   *  PRD-03 — ایرادِ کدِ کالای [info] برای ذخیره (کدِ کالای دیگر)؛ `null` ⇒ آزاد. فقط کدِ **تازه**
   *  سنجیده می‌شود ([before] = کدِ پیشینِ همین کالا): تعارضِ آفلاین ویرایشِ کالا را قفل نمی‌کند.
   */
  fun skuProblem(d: ShopData, info: ProductInfo, selfId: String?, before: String?): String? {
    kitProblem(d, info, selfId)?.let { return it }
    if (info.sku.isEmpty() || ProductSku.key(info.sku) == ProductSku.key(before.orEmpty())) return null
    val other = ProductSku.owner(info.sku, selfId, skus(d)) ?: return null
    return ir.vil3ntec.tohid.core.i18n.StrProduct.skuTaken(info.sku, d.products.firstOrNull { it.id == other }?.name.orEmpty())
  }

  /** پرس‌وجو در نامِ پشتو/انگلیسی، مترادف یا واژهٔ جست‌وجو هست؟ (نامِ دری و بارکد کارِ همان جست‌وجوی پیشین) */
  fun matches(p: Product, query: String): Boolean = !hasNone(p) && of(p).matches(query)

  /** کالا برای نمایهٔ `CatalogMatch` — `extra = false` ⇒ همان `Item(id, name, barcodes)` ِ پیشین */
  fun searchItem(p: Product, extra: Boolean): CatalogMatch.Item =
    if (!extra || hasNone(p)) CatalogMatch.Item(p.id, p.name, p.barcodes)
    //  PRD-03: کدِ کالا مثلِ بارکد — کامل اولِ فهرست، آغازش در گروهِ «آغازِ بارکد»
    else of(p).let { CatalogMatch.Item(p.id, p.name, p.barcodes + listOfNotNull(it.sku.ifEmpty { null }), aliases = it.aliases, keywords = it.searchWords) }

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
