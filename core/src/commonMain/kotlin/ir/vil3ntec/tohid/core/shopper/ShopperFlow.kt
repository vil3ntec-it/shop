package ir.vil3ntec.tohid.core.shopper

/**
 *  پس از ردِ ثبت چه کنیم — یک جا، تا صفحهٔ اندروید و (مرحلهٔ ۲) آیفون یک رفتار داشته باشند. هیچ ردی بی‌صدا
 *  سفارش را عوض نمی‌کند: قیمتِ تازه یا کالای رفته روشن نشان داده می‌شود و ثبت فقط با زدنِ دوبارهٔ مشتری است
 *  (S135، همان `checkout.js` ِ وب).
 */
object CheckoutFlow {

  sealed class After {
    /** قیمت عوض شد: سبد با قیمتِ امروز و فهرستِ «نام: پیش ← پس»؛ دکمه می‌شود «پذیرفتن و ثبت» */
    data class Repriced(val cart: Cart, val changes: List<Triple<String, Long, Long>>, val totalMinor: Long) : After()
    /** کالایی دیگر در ویترین نیست: همان قلم‌ها خط‌خورده */
    data class Unavailable(val cart: Cart) : After()
    /** سقفِ هر سفارشِ یک کالا: مقدار همان سقف شد */
    data class Capped(val cart: Cart) : After()
    /** بلیت منقضی یا شمارهٔ دیگر: دوباره تأیید (بلیتِ کهنه پاک) */
    object Reverify : After()
    /** بازه پر شد/نیست: بازه‌های تازهٔ همان پاسخ، و بازهٔ برگزیده پاک */
    data class Slots(val slots: List<DeliverySlot>?) : After()
    /** ناحیه دیگر نیست یا لازم است: ناحیهٔ برگزیده پاک */
    object Rezone : After()
    /** MKT-01: کدِ تخفیف در همان تراکنش اعمال نشد ⇒ کد برداشته و پیش‌نمایش از نو (هرگز سفارشِ بی‌صدا با قیمتِ دیگر) */
    data class PromoRejected(val status: String) : After()
    /**
     *  S41: همین سبد و همین نشانی چند دقیقه پیش سفارشِ [orderNo] شد ⇒ پرسش: «همان را دنبال کن» (بی‌خطر، پیش‌فرض) ⇒
     *  پیگیریِ [token]، هیچ سفارشِ تازه‌ای نه؛ «سفارشِ دوم» ⇒ همان سبد با `secondOrder` (بدنهٔ دیگر ⇒ کلیدِ یکتاییِ تازه)
     */
    data class Duplicate(val orderNo: Long, val token: String) : After()
    /** بقیه (شبکه، ۴۲۹، توقفِ دکان…): فقط پیام؛ کلیدِ یکتایی می‌ماند تا زدنِ دوباره همان سفارش باشد */
    object Message : After()
  }

  fun after(cart: Cart, e: ShopperError, at: Long): After = when (e.code) {
    ShopperError.PRICE_CHANGED -> e.quote()?.let { q ->
      val (next, changed) = CartRules.applyQuote(cart, q, at)
      After.Repriced(next, changed, q.totalMinor)
    } ?: After.Message
    "product_unavailable" -> After.Unavailable(CartRules.markUnavailable(cart, e.unavailable(), at))
    "qty_over_limit" -> e.maxQty()?.let { (id, max) -> After.Capped(CartRules.capAt(cart, id, max, at)) } ?: After.Message
    "verification_required", "phone_not_verified" -> After.Reverify
    "slot_required", "slot_unavailable", "slot_full" -> After.Slots(e.slots())
    "bad_zone", "zone_required" -> After.Rezone
    ShopperError.PROMO_REJECTED -> After.PromoRejected(e.promoStatus().orEmpty())
    ShopperError.DUPLICATE_ORDER -> e.duplicate()?.let { (no, token) -> After.Duplicate(no, token) } ?: After.Message
    else -> After.Message
  }

  /** پیامِ زیرِ دکمهٔ ثبت برای [after] (قیمتِ تازه با فهرستِ تغییرها، وگرنه همان متنِ خطا) */
  fun note(after: After, e: ShopperError): String = when (after) {
    is After.Repriced -> ShopperText.repriced(
      after.changes.joinToString(if (ShopperText.lang == ShopperLang.EN) ", " else "، ") { (n, a, b) -> ShopperText.priceMove(n, a, b) } +
        if (after.changes.isEmpty()) "" else ".",
      after.totalMinor,
    )
    is After.PromoRejected -> ShopperText.promoStatus(after.status)
    is After.Duplicate -> ShopperText.dupAsk(after.orderNo)
    else -> ShopperText.error(e)
  }
}
