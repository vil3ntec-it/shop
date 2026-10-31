package ir.vil3ntec.tohid.data

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
      p.keywords.isNullOrEmpty() && p.aisle.isNullOrEmpty() && p.shelf.isNullOrEmpty() && p.shelfLevel.isNullOrEmpty()

  fun of(p: Product): ProductInfo = ProductInfo(
    namePs = p.namePs.orEmpty(),
    nameEn = p.nameEn.orEmpty(),
    synonyms = p.synonyms.orEmpty(),
    keywords = p.keywords.orEmpty(),
    location = shelf(p),
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
  )

  /** پرس‌وجو در نامِ پشتو/انگلیسی، مترادف یا واژهٔ جست‌وجو هست؟ (نامِ دری و بارکد کارِ همان جست‌وجوی پیشین) */
  fun matches(p: Product, query: String): Boolean = !hasNone(p) && of(p).matches(query)

  /** کالا برای نمایهٔ `CatalogMatch` — `extra = false` ⇒ همان `Item(id, name, barcodes)` ِ پیشین */
  fun searchItem(p: Product, extra: Boolean): CatalogMatch.Item =
    if (!extra || hasNone(p)) CatalogMatch.Item(p.id, p.name, p.barcodes)
    else of(p).let { CatalogMatch.Item(p.id, p.name, p.barcodes, aliases = it.aliases, keywords = it.keywords) }
}
