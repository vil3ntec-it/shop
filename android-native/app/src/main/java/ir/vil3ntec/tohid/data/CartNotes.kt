package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.SaleNotes

/**
 *  یادداشتِ ردیفِ سبد — POS-11. متن همیشه از `SaleNotes.clean` (یک خط، حداکثر ۱۲۰ نویسه، خالی ⇒
 *  `null`) می‌گذرد؛ ردیف‌های دیگر دست نمی‌خورند و سبدِ بی تغییر همان شیء برمی‌گردد.
 */
object CartNotes {
  fun set(cart: List<SalesEngine.CartLine>, productId: String, note: String?): List<SalesEngine.CartLine> {
    val clean = SaleNotes.clean(note)
    if (cart.none { it.productId == productId && it.note != clean }) return cart
    return cart.map { if (it.productId == productId) it.copy(note = clean) else it }
  }
}
