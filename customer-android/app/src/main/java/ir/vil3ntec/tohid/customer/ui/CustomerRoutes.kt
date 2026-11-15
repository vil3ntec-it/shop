package ir.vil3ntec.tohid.customer.ui

import ir.vil3ntec.tohid.core.shopper.ShopperError

//  پشتهٔ صفحه‌ها و حالِ بارگیریِ برنامهٔ خرید — از `CustomerModel.kt` جدا شد تا آن زیرِ سقفِ اندازه بماند (`tools/quality`)؛
//  همان بسته و همان نام‌ها ⇒ کاربرها (صفحه‌ها، آیفون، آزمون‌ها) دست نخوردند. بی android.* (آیفون همین پوشه را کامپایل می‌کند).

/** صفحه‌های برنامهٔ خرید (پشتهٔ ساده؛ «برگشت» یکی برمی‌دارد). */
sealed class Route {
  object Home : Route()
  data class Shop(val slug: String) : Route()
  data class Product(val slug: String, val id: String) : Route()
  data class CartPage(val slug: String) : Route()
  data class Checkout(val slug: String) : Route()
  data class Placed(val slug: String, val token: String) : Route()
  data class Track(val slug: String, val token: String) : Route()
  object Settings : Route()
  /** مرحلهٔ ۲: نشانی‌های من (CHK-09)، برگهٔ نشانی، و گفت‌وگو با دکان (CHAT؛ [orderNo] null ⇒ گفت‌وگوی عمومی) */
  object Addresses : Route()
  data class AddressEdit(val id: String?, val fromCheckout: Boolean = false) : Route()
  data class Chat(val slug: String, val orderNo: Long?) : Route()
}

/** حالِ یک بارگیری — صفحه هر چهار را نشان می‌دهد (هیچ «در حالِ بار» ِ بی‌پایان یا خطای بی‌متن). */
sealed class Load<out T> {
  object Idle : Load<Nothing>()
  object Busy : Load<Nothing>()
  data class Ready<out T>(val value: T) : Load<T>()
  data class Failed(val error: ShopperError) : Load<Nothing>()

  fun valueOrNull(): T? = (this as? Ready)?.value
}
