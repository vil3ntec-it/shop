package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.core.pos.TillPins
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

  /**
   *  نمایه — یک بار با هر تغییرِ کالاها یا فروش‌ها، نه با هر حرف؛ شمارِ فروش پس از یک فروش افزایشی (`CatalogSearch.counts`).
   *  [codes] (POS-01، `ProductInfoPrefs`): کدِ کالا (SKU) هم در جست‌وجوی همان یک کادر، مثلِ بارکد.
   */
  fun index(d: ShopData, codes: Boolean = false): CatalogSearch.Index =
    CatalogSearch.index(d.products, CatalogSearch.counts(d.sales, d.saleItems), codes)

  /**
   *  کاشیِ یک کالا با مقدارش در سبد. [held] (INV-03، `HeldStock.reserved`): مقدارِ همان کالا در فاکتورهای نگه‌داشته ⇒
   *  موجودیِ کاشی «قابل‌فروش» است (موجودی − نگه‌داشته)؛ خالی ⇒ همان `ShopStore.stock`.
   */
  fun item(d: ShopData, p: Product, inCart: Double, held: Map<String, Double> = emptyMap()): PhoneCatalogItem = PhoneCatalogItem(
    id = p.id,
    name = p.name,
    price = p.salePrice,
    unit = p.unit,
    stock = ir.vil3ntec.tohid.data.HeldStock.sellable(ShopStore.stock(d, p.id), p.id, held),
    hasPhoto = p.photo,
    inCart = inCart,
    weighed = SalesEngine.isFractionalUnit(p.unit),
    stockless = p.stockless == true,
  )

  /**
   *  POS-03 — پیشوندِ شمار «۳*» یا «۳×» پیش از نام یا بارکد (همان `CartQuery` ِ صندوقِ کامپیوتر، رقمِ فارسی هم): جست‌وجو با
   *  بخشِ پس از ستاره و هر لمسِ کاشی همان شمار را می‌افزاید. بی پیشوند ⇒ شمار ۱ و همان نوشته (رفتارِ امروز).
   *  با [d]: متنی که خودش بارکدِ کامل است («12x34») بی پیشوند می‌ماند (`PosSearch.isFullCode`، همان صندوقِ کامپیوتر و وب)؛
   *  نمایه‌ها فقط وقتی ساخته می‌شوند که پیشوندی خوانده شد.
   */
  fun query(text: String, d: ShopData? = null): ir.vil3ntec.tohid.data.CartQuery.Parsed =
    if (d == null) ir.vil3ntec.tohid.data.CartQuery.parse(text)
    else ir.vil3ntec.tohid.data.CartQuery.parse(text) { ir.vil3ntec.tohid.ui.components.pos.PosSearch.isFullCode(it, ShopStore.barcodeIndex(d), ShopStore.packIndex(d)) }

  /**
   *  کاشی‌های جورشده به ترتیبِ نمایش — با پیشوندِ شمار فقط بخشِ پس از ستاره جست‌وجو می‌شود. [pins] (POS-06، `TillPins`
   *  ِ همین دستگاه): بی جست‌وجو سنجاق‌شده‌ها اول؛ خالی ⇒ همان ترتیبِ امروز.
   */
  fun items(
    d: ShopData,
    index: CatalogSearch.Index,
    query: String,
    category: String?,
    inCart: Map<String, Double>,
    held: Map<String, Double> = emptyMap(),
    pins: List<String> = emptyList(),
    synonyms: CatalogSearch.Synonyms = ir.vil3ntec.tohid.TillSynonyms.search,
  ): List<PhoneCatalogItem> {
    val byId = d.products.associateBy { it.id }
    val term = query(query, d).term
    //  POS-04: فهرستِ مرکزیِ مترادف (روزی یک بار از سرور، بی‌شبکه همان نسخهٔ ذخیره‌شده؛ تهی ⇒ همان جست‌وجوی پیشین)
    val ids = CatalogSearch.search(index, term, category, synonyms)
      .let { if (term.isBlank()) TillPins.arrange(it, pins) else it }
    return ids.mapNotNull { id -> byId[id]?.let { item(d, it, inCart[id] ?: 0.0, held) } }
  }

  /** «پرفروش‌ها» ِ سرِ کاتالوگ — کالایی که هرگز فروخته نشده نه */
  fun bestsellers(d: ShopData, index: CatalogSearch.Index, inCart: Map<String, Double>, held: Map<String, Double> = emptyMap()): List<PhoneCatalogItem> {
    val byId = d.products.associateBy { it.id }
    return CatalogSearch.top(index).mapNotNull { id -> byId[id]?.let { item(d, it, inCart[id] ?: 0.0, held) } }
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
