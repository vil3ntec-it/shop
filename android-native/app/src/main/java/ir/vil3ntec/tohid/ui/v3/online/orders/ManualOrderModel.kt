package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.core.pos.DeliverLater
import ir.vil3ntec.tohid.data.DeliverLaterCart
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore

/**
 *  «سفارشِ دستی» مثلِ سبد (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «همهٔ محصولاتِ موردِ نظر رو انتخاب کنم — مثلِ سبد») — خالص (آزمون:
 *  `ManualOrderModelTest`). کالاهای برگزیده یک سبدِ **جدا** از صندوق‌اند (`SalesEngine.CartLine`، همان `CartModel` ِ صندوق
 *  برای +/− و نگهبانِ موجودی) و قلم‌های دستیِ بیرون از فهرست همان `OrderDraft.LineInput` ِ پیشین. هنگامِ ثبت هر دو یک
 *  `OrderDraft.Form` می‌شوند و داوری همان `OrderDraft.build`:
 *
 *  • کالا ⇒ نامِ همان لحظه، **واحدِ موجودی** و شناسهٔ کالا (همان تبدیلِ `DeliverLaterCart` ِ «فروش با تحویلِ بعدی») تا فروشِ
 *    تحویل همان کالا را کم کند؛ قیمت از کالا (`SalesEngine.linePrice`، یا فیِ دستیِ همان ردیف).
 *  • قلمِ دستی ⇒ بی شناسه — بدنه بایت‌به‌بایت همان فرمِ پیشین.
 *  • جمعِ نوارِ پایین همان قاعدهٔ سرور (`DeliverLater.lineTotalMinor`، گرد به افغانیِ کامل) + هزینهٔ ارسال.
 */
object ManualOrderModel {

  /** یک‌صدم ⇒ «150» یا «150.5» (همان ورودیِ `OrderDraft.scaled(…, 2)`) */
  fun minorText(minor: Long): String {
    val whole = minor / 100
    val cents = minor % 100
    if (cents == 0L) return whole.toString()
    return "$whole." + cents.toString().padStart(2, '0').trimEnd('0')
  }

  /** کالاهای برگزیده ⇒ قلم‌های فرم (ردیفِ کالایی که دیگر در دفتر نیست کنار می‌رود — همان سبدِ صندوق) */
  fun productInputs(d: ShopData, cart: List<SalesEngine.CartLine>): List<OrderDraft.LineInput> =
    DeliverLaterCart.lines(d, cart.filter { !it.free }).map { l ->
      OrderDraft.LineInput(l.name, OrderText.qty(l.qtyMilli), minorText(l.unitPriceMinor), productId = l.productId, unit = l.unit)
    }

  /** قلم‌های دستیِ پُر — ردیفِ دست‌نخوردهٔ پیش‌فرضِ فرم (فقط مقدارِ «۱»، بی نام و فی) قلم نیست */
  fun freeLines(form: OrderDraft.Form): List<OrderDraft.LineInput> = form.lines.filterNot { it.name.isBlank() && it.price.isBlank() }

  /** همان فرمی که ثبت می‌شود: کالاها اول، بعد قلم‌های دستی */
  fun merged(form: OrderDraft.Form, d: ShopData, cart: List<SalesEngine.CartLine>): OrderDraft.Form =
    form.copy(lines = productInputs(d, cart) + freeLines(form))

  fun count(form: OrderDraft.Form, d: ShopData, cart: List<SalesEngine.CartLine>): Int = merged(form, d, cart).lines.size

  /** جمعِ یک قلم با قاعدهٔ سرور؛ مقدار/فیِ نادرست ⇒ null */
  fun lineMinor(l: OrderDraft.LineInput): Long? {
    val q = OrderDraft.scaled(l.qty.trim().ifEmpty { "1" }, 3) ?: return null
    val p = OrderDraft.scaled(l.price.trim(), 2) ?: return null
    if (q > DeliverLater.MAX_QTY_MILLI || p > DeliverLater.MAX_PRICE_MINOR) return null
    return DeliverLater.lineTotalMinor(q, p)
  }

  /** جمعِ نوارِ پایین: قلم‌های درست + هزینهٔ ارسال (فقط «ارسال») */
  fun totalMinor(merged: OrderDraft.Form): Long {
    val lines = merged.lines.sumOf { lineMinor(it) ?: 0L }
    val fee = if (merged.fulfillment == "delivery") OrderDraft.scaled(merged.deliveryFee, 2) ?: 0L else 0L
    return lines + fee
  }

  /** برگهٔ قلمِ یک کالای برگزیده (مقدار و فی) */
  fun inputOf(d: ShopData, cart: List<SalesEngine.CartLine>, productId: String): OrderDraft.LineInput? =
    productInputs(d, cart.filter { it.productId == productId }).firstOrNull()

  /**
   *  «ذخیره» ِ برگهٔ کالا ⇒ سبدِ تازه: مقدارِ تازه (صفر ⇒ ردیف می‌رود) و فیِ دستی فقط وقتی با قیمتِ کالا فرق دارد.
   *  مقدار/فیِ نادرست ⇒ همان سبد (برگه خودش خطا را نشان می‌دهد).
   */
  fun applyEdit(d: ShopData, cart: List<SalesEngine.CartLine>, productId: String, input: OrderDraft.LineInput): List<SalesEngine.CartLine> {
    val q = OrderDraft.scaled(input.qty.trim().ifEmpty { "1" }, 3) ?: return cart
    val p = OrderDraft.scaled(input.price.trim(), 2) ?: return cart
    val price = p / 100.0
    val listed = ShopStore.product(d, productId)?.let { SalesEngine.linePrice(d, SalesEngine.CartLine(productId, 1.0)) }
    val unitPrice = if (listed != null && DeliverLater.minor(listed) == p) null else price
    if (cart.none { it.productId == productId }) {
      return if (q == 0L) cart else cart + SalesEngine.CartLine(productId, q / 1000.0, unitPrice = unitPrice)
    }
    val qtyChanged = SalesEngine.setCartQty(cart, productId, q / 1000.0)
    return qtyChanged.map { if (it.productId == productId) it.copy(unitPrice = unitPrice) else it }
  }
}
