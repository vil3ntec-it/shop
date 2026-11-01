package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.PriceCheck
import kotlin.math.roundToLong

/**
 *  POS-15 — قیمت‌پرسیِ یک کالا از دفتر (هسته: `core/…/pos/PriceCheck.kt`). فقط می‌خواند.
 *  موجودی همان `ShopStore.stock`، محموله‌ها همان FEFO ِ `ExpiryEngine.lots`، خریدها از `ShopData.purchases`.
 *
 *  خالص — بی `Context`؛ آزمون: `PriceCheckDataTest`.
 */
object PriceCheckData {

  data class View(
    val product: Product,
    val stock: Double,
    val card: PriceCheck.Card,
    /** نامِ تأمین‌کنندهٔ آخرین خرید؛ نبود ⇒ `null` */
    val supplier: String?,
  )

  private fun minor(afn: Double): Long = if (afn.isFinite()) (afn * 100).roundToLong() else 0L
  private fun milli(q: Double): Long = if (q.isFinite()) (q * 1000).roundToLong() else 0L

  fun of(d: ShopData, product: Product, today: String): View {
    val names = d.suppliers.associate { it.id to it.name }
    val purchases = d.purchases.filter { it.productId == product.id }.map {
      PriceCheck.Purchase(it.date, it.createdAt, minor(it.purchasePrice), milli(it.quantity), it.supplierId)
    }
    val lots = ExpiryEngine.lots(d, today).filter { it.product.id == product.id }
      .map { PriceCheck.Lot(it.entry.expiryDate, milli(it.remaining)) }
    val card = PriceCheck.card(minor(product.salePrice), minor(product.purchasePrice), purchases, lots, today)
    return View(product, ShopStore.stock(d, product.id), card, card.last?.supplier?.let { names[it]?.ifBlank { null } })
  }
}
