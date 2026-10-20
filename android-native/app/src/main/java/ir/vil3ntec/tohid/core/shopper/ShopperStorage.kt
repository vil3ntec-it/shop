package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiJson
import kotlinx.serialization.KSerializer

/**
 *  حافظهٔ محلیِ حالتِ «خرید» — سبدِ مهمان، دکان‌های دلخواه، بلیتِ شماره،
 *  سفارش‌های همین دستگاه و نام/نشانیِ سفارشِ پیشین.
 *
 *  فقط روی همین دستگاه می‌ماند و هرگز همگام نمی‌شود (دادهٔ دکان نیست).
 *  گوشی و کامپیوتر هر کدام پیاده‌سازیِ خودشان را می‌دهند؛ آزمون‌ها حافظهٔ
 *  ساده. خواندنِ خراب ⇒ همان «خالی»، نه استثنا: سبدِ خراب نباید صفحه را بکشد.
 */
interface ShopperStorage {
  fun read(key: String): String?

  /** `null` ⇒ پاک */
  fun write(key: String, value: String?)

  companion object Keys {
    const val CARTS = "shopper.carts"
    const val FAVOURITES = "shopper.favourites"
    const val TICKET = "shopper.ticket"
    const val ORDERS = "shopper.orders"
    const val CONTACT = "shopper.contact"
    const val CHECKOUT_KEY = "shopper.checkoutKey"
  }
}

internal fun <T> ShopperStorage.load(key: String, serializer: KSerializer<T>): T? =
  runCatching { read(key)?.let { ApiJson.format.decodeFromString(serializer, it) } }.getOrNull()

internal fun <T> ShopperStorage.save(key: String, serializer: KSerializer<T>, value: T?) {
  runCatching { write(key, value?.let { ApiJson.format.encodeToString(serializer, it) }) }
}
