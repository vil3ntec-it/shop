package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel

/** صافیِ کاشی‌های فروشِ سریعِ پیشین (`QuickSale`؛ فقط پیش‌نمایشِ گوشیِ کامپیوتر به آن می‌رسد) */
internal object QuickSaleSearch {

  /**
   *  کالاهای فعالِ [category] (null ⇒ همه) که نام یا بارکدشان نوشتهٔ کادر را دارد؛ PRD-02 ([infoSearch]، پشتِ
   *  `ProductInfoPrefs`): نامِ پشتو/انگلیسی، مترادف و واژهٔ جست‌وجو هم. نوشته پس از پیشوندِ شمار (POS-03) خوانده می‌شود —
   *  همان `CatalogModel.query` ِ نسخهٔ ۳ (بارکدِ کاملِ ستاره‌دار دست نمی‌خورد): «مشابه» ِ جست‌وجو با عکس نامِ رقم‌دار را با
   *  «1*» در کادر می‌گذارد (`VisualSearch.tillText`) و پیش از این فهرست خالی می‌شد.
   */
  fun shown(d: ShopData, search: String, category: String?, infoSearch: Boolean): List<Product> {
    val term = CatalogModel.query(search, d).term.trim()
    return d.products.filter { p ->
      //  کالای غیرفعال در فروش نمی‌آید — سابقه‌اش در گزارش‌ها می‌ماند
      p.active &&
        (category == null || p.category == category) &&
        (term.isBlank() || p.name.contains(term, ignoreCase = true) ||
          p.barcodes.any { it.contains(term) } ||
          (infoSearch && ir.vil3ntec.tohid.data.ProductInfoMap.matches(p, term)))
    }
  }
}
