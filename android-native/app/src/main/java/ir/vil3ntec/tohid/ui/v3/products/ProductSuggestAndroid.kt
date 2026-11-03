package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.product.ProductSuggest
import ir.vil3ntec.tohid.data.ProductSuggestPref
import ir.vil3ntec.tohid.data.ShopData

/*
 *  AI-06 — مرزِ پلتفرم برای «پیشنهادِ دسته و نام»: فقط خواندنِ کلیدِ همین دستگاه (`ProductSuggestPref`). منطق در
 *  `core/product/ProductSuggest` و رابط در `ProductSuggestParts`.
 */

/** پیشنهادِ دسته از نام برای فرم؛ کلیدِ خاموش یا دفترِ بی دسته ⇒ null (هیچ‌چیز رسم نمی‌شود) */
@Composable
internal fun rememberCategorySuggest(data: ShopData): ((String) -> List<ProductSuggest.Pick>)? {
  val context = LocalContext.current
  val on = remember { ProductSuggestPref.on(context) }
  return remember<((String) -> List<ProductSuggest.Pick>)?>(on, data.products, data.productCategories) {
    if (!on) return@remember null
    val index = ProductSuggest.Index(data.products.map { ProductSuggest.Known(it.name, it.category) }, data.productCategories)
    if (index.isEmpty) null else { name: String -> index.categories(name) }
  }
}

/** «افزودن از متن» دیده شود؟ — همان کلیدِ همین دستگاه */
@Composable
internal fun rememberPasteOn(): Boolean {
  val context = LocalContext.current
  return remember { ProductSuggestPref.on(context) }
}
