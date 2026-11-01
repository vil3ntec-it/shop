package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.PriceWhy

/**
 *  POS-22 — یک ردیفِ سبد ⇐ گام‌های «چرا این قیمت؟» (`core/pos/PriceWhy`).
 *
 *  • قیمتِ فهرست: `Product.salePrice` (قلمِ آزاد ندارد)؛
 *  • قیمتِ دستی: `CartLine.unitPrice` ِ سبدِ **پیش از** تخفیفِ خودکار (`cart`)؛
 *  • تخفیف‌های خودکار: سهمِ هر قاعده از همین ردیف (`PromoEngine.Outcome.tags`)؛
 *  • جمعِ ردیف: از سبدِ **پس از** تخفیفِ خودکار (`priced`) با همان `SalesEngine.lineTotal` که سبد نشان می‌دهد.
 *  بیرون از بازهٔ امنِ هسته یا ردیفِ بی کالا ⇒ `null` و پنلی نشان داده نمی‌شود.
 */
object PriceWhyLine {

  private fun minor(afn: Double): Long = Math.round(afn * 100.0)

  fun rows(
    d: ShopData,
    cart: List<SalesEngine.CartLine>,
    priced: List<SalesEngine.CartLine>,
    promo: PromoEngine.Outcome?,
    id: String,
  ): List<PriceWhy.Row>? {
    val line = cart.find { it.productId == id } ?: return null
    val after = priced.find { it.productId == id } ?: line
    val product = if (line.free) null else d.products.find { it.id == id } ?: return null
    val qtyMilli = Math.round(line.quantity * 1000.0)
    val list = product?.let { minor(it.salePrice) }
    val manual = line.unitPrice?.let(::minor)
    val promos = promo?.tags?.get(id)?.promo.orEmpty().map { PriceWhy.Promo(it.name, it.discountMinor) }
    val final = minor(SalesEngine.lineTotal(SalesEngine.linePrice(d, after), after.quantity))
    return runCatching { PriceWhy.explain(qtyMilli, list, manual, promos, final) }.getOrNull()
  }
}
