package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.pos.CatalogSearch
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalogItem

/** چه چیزی جای کاشی‌ها دیده شود */
enum class CatalogEmpty {
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
 *  کاتالوگِ صندوق و «فروشِ سریع» — یک قاعده برای هر دو: نمایهٔ جست‌وجو (`CatalogSearch`:
 *  پرفروش‌ترین اول، «ي/ی» و رقم‌ها یکی، بارکدِ کامل پیش از همه)، دادهٔ کاشی
 *  (`PhoneCatalogItem`) با موجودیِ `ShopStore.stock`، «پرفروش‌ها» و پیامِ خالی.
 */
object CatalogModel {

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

  /** «پرفروش‌ها» ِ سرِ کاتالوگ — کالایی که هرگز فروخته نشده نه */
  fun bestsellers(d: ShopData, index: CatalogSearch.Index, inCart: Map<String, Double>): List<PhoneCatalogItem> {
    val byId = d.products.associateBy { it.id }
    return CatalogSearch.top(index).mapNotNull { id -> byId[id]?.let { item(d, it, inCart[id] ?: 0.0) } }
  }

  /** کاشی‌ای نیست ⇒ کدام پیام */
  fun empty(index: CatalogSearch.Index, shown: Int, query: String): CatalogEmpty = when {
    shown > 0 -> CatalogEmpty.NONE
    index.size == 0 -> CatalogEmpty.NO_PRODUCTS
    query.isNotBlank() -> CatalogEmpty.NO_MATCH
    else -> CatalogEmpty.EMPTY_CATEGORY
  }

  /** دستهٔ انتخاب‌شده‌ای که دیگر نیست (پاک شد) انتخاب نمی‌ماند */
  fun keepCategory(selected: String?, categories: List<String>): String? = selected?.takeIf { it in categories }
}
