package ir.vil3ntec.tohid.ui.v3.sales.quick

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.pos.CatalogSearch
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalogItem

/** چه چیزی جای کاشی‌ها دیده شود */
enum class QuickEmpty {
  /** کاشی هست */
  NONE,

  /** دکان هنوز کالای فعالی ندارد */
  NO_PRODUCTS,

  /** جست‌وجو چیزی نیافت */
  NO_MATCH,

  /** این دسته کالای فعالی ندارد */
  EMPTY_CATEGORY,
}

/**
 *  حالتِ خالصِ «فروشِ سریع» (`sale/quick`): کاشی‌ها از همان نمایهٔ جست‌وجوی صندوق
 *  (`CatalogSearch`: پرفروش‌ترین اول، «ي/ی» و رقم‌ها یکی، بارکدِ کامل پیش از همه) و
 *  همان دادهٔ کاشیِ صندوق (`PhoneCatalogItem`). موجودی از `ShopStore.stock`.
 */
object QuickSaleModel {

  /** نمایه — یک بار با هر تغییرِ کالاها یا فروش‌ها، نه با هر حرف */
  fun index(d: ShopData): CatalogSearch.Index = CatalogSearch.index(d.products, CatalogSearch.popularity(d.sales, d.saleItems))

  /** کاشیِ یک کالا با مقدارش در سبد */
  fun item(d: ShopData, p: Product, inCart: Double): PhoneCatalogItem = PhoneCatalogItem(
    id = p.id,
    name = p.name,
    price = p.salePrice,
    unit = p.unit,
    stock = ShopStore.stock(d, p.id),
    hasPhoto = p.photo,
    inCart = inCart,
    weighed = SalesEngine.isFractionalUnit(p.unit),
  )

  /** کاشی‌های جورشده به ترتیبِ نمایش */
  fun items(
    d: ShopData,
    index: CatalogSearch.Index,
    query: String,
    category: String?,
    inCart: Map<String, Double>,
  ): List<PhoneCatalogItem> {
    val byId = d.products.associateBy { it.id }
    return CatalogSearch.search(index, query, category).mapNotNull { id -> byId[id]?.let { item(d, it, inCart[id] ?: 0.0) } }
  }

  /** کاشی‌ای نیست ⇒ کدام پیام */
  fun empty(index: CatalogSearch.Index, shown: Int, query: String): QuickEmpty = when {
    shown > 0 -> QuickEmpty.NONE
    index.size == 0 -> QuickEmpty.NO_PRODUCTS
    query.isNotBlank() -> QuickEmpty.NO_MATCH
    else -> QuickEmpty.EMPTY_CATEGORY
  }

  /** دستهٔ انتخاب‌شده‌ای که دیگر نیست (پاک شد) انتخاب نمی‌ماند */
  fun keepCategory(selected: String?, categories: List<String>): String? = selected?.takeIf { it in categories }
}
