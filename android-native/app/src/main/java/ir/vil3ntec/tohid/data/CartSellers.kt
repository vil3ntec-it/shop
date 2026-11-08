package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.staff.LineSeller

/**
 *  فروشندهٔ ردیفِ سبد — POS-23 (پایهٔ پورسانت، STF-08). خالص و بی android.*؛ صفحه فقط با تنظیمِ روشنِ دکان
 *  (`LineSellerPref`) آن را صدا می‌زند و ثبت فقط با `Checkout.lineSellers` آن را روی قلم می‌نشاند.
 *
 *  • [assign]: فروشندهٔ ردیف‌های [ids] (شناسهٔ ردیف = `productId`، همان `CartNotes`) را [seller] می‌کند؛ خالی یا همان
 *    فروشندهٔ فاکتور ([invoice]) ⇒ `null` (برگشت به فروشندهٔ فاکتور). ردیف‌های دیگر دست نمی‌خورند و سبدِ بی تغییر همان
 *    شیء برمی‌گردد.
 *  • ⛔ مجوز پیش از نوشتن: نامِ دیگری فقط با [othersAllowed] (`LineSeller.mayAssign`)؛ بی آن سبد همان می‌ماند.
 */
object CartSellers {

  fun assign(
    cart: List<SalesEngine.CartLine>, ids: Set<String>, seller: String?, invoice: String, me: String, othersAllowed: Boolean,
  ): List<SalesEngine.CartLine> {
    if (!LineSeller.mayAssign(me, seller, othersAllowed)) return cart
    val clean = LineSeller.stored(true, seller, invoice)
    if (cart.none { it.productId in ids && it.seller != clean }) return cart
    return cart.map { if (it.productId in ids) it.copy(seller = clean) else it }
  }

  /** فروشندهٔ نمایشیِ ردیف (`LineSeller.of`)؛ ردیفِ بی فروشنده ⇒ فروشندهٔ فاکتور */
  fun of(line: SalesEngine.CartLine, invoice: String): String = LineSeller.of(line.seller, invoice)

  /** سبد فروشندهٔ ردیفی جدا از فاکتور دارد؟ (برچسبِ دکمه) */
  fun any(cart: List<SalesEngine.CartLine>, invoice: String): Boolean = cart.any { LineSeller.stored(true, it.seller, invoice) != null }
}
