package ir.vil3ntec.tohid.core.market

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/*
 *  قراردادِ دادهٔ دکان ⇐ بازار (`docs/MARKETPLACE-CONTRACT-fa.md`، کارِ S4) — همان
 *  شکل‌هایی که `server/src/lib/public-views.js` با فهرستِ سفید به مشتری می‌دهد.
 *  برنامهٔ خرید (اندروید/آیفون) و پیش‌نمایشِ «مشتری چه می‌بیند» در برنامهٔ مدیریت
 *  یک تعریف دارند.
 *
 *  فقط داده + تجزیهٔ JSON؛ نه شبکه، نه UI، نه android و نه java.
 *  • نسخه: `MarketContract.SCHEMA_VERSION` = نسخهٔ سرور؛ نمونه‌ها و فهرستِ فیلدها در
 *    `core/test-vectors/market-public-v1.json` (آزمونِ سرور و `MarketContractTest` هر دو).
 *  • فیلدِ ناشناخته نادیده گرفته می‌شود (سرورِ تازه‌تر برنامهٔ کهنه را نمی‌شکند).
 *  • وضعیت‌ها رشته‌اند، نه enum: مقدارِ تازهٔ سرور خطای تجزیه نمی‌دهد؛ مقدارهای شناخته در `companion`.
 *  • پول به یک‌صدمِ افغانی (`…Minor`)، مقدار به هزارم (`…Milli`)، زمان میلی‌ثانیهٔ یونیکس — مثلِ سرور.
 */

/** §۱.۲ — حضور و ساعتِ کاری. در کارتِ بازار `mode` نیست. */
@Serializable
data class PublicPresence(
  val badge: String,
  val mode: String? = null,
  val declaredUntil: Long? = null,
  val connection: String,
  val lastConfirmedAt: Long? = null,
  val orderAcceptance: String,
  val timezone: String = "Asia/Kabul",
  val localTime: String = "",
  val hoursSet: Boolean = false,
  val holiday: Boolean = false,
  /** بازه‌های امروز: [آغاز، پایان] به دقیقه از نیمه‌شب */
  val today: List<List<Int>> = emptyList(),
  val notice: String = "",
) {
  companion object {
    const val OPEN_ONLINE = "open_online"
    const val OPEN_UNCONFIRMED = "open_unconfirmed"
    const val DECLARED_UNTIL = "declared_until"
    const val CLOSED = "closed"
    const val OFFLINE = "offline"
    const val ACCEPTING = "accepting"
    const val DELAYED = "delayed"
    const val LATER = "later"
  }
}

@Serializable
data class Fulfillment(val delivery: Boolean = false, val pickup: Boolean = false)

/** `payment.online` ِ دکان؛ همان شکل (بی `decision`) = `onlinePay` ِ پیگیریِ سفارش (PAY-21 #529). */
@Serializable
data class OnlinePayment(val available: Boolean = false, val decision: String = "")

@Serializable
data class Payment(val methods: List<String> = emptyList(), val online: OnlinePayment = OnlinePayment())

/** یک تصویرِ ویترین (M3 #428)؛ `url` نسخه‌دار است. */
@Serializable
data class MediaFile(val version: Long, val width: Int = 0, val height: Int = 0, val mime: String = "", val url: String)

/** لوگو و تصویرِ سرِ ویترین (M3 #428)؛ پرچمِ خاموش ⇒ هر دو null. */
@Serializable
data class ShopMedia(val logo: MediaFile? = null, val header: MediaFile? = null)

/**
 * §۱.۱ — پروفایلِ عمومیِ دکان (`GET /api/store/:slug` ⇒ `store`).
 * `delivery` (ناحیه و زمان‌بندی، §۱.۵) بیرون از این نسخهٔ مدل است و نادیده گرفته می‌شود.
 */
@Serializable
data class PublicShop(
  val slug: String,
  val title: String,
  val tagline: String = "",
  val contactPhone: String = "",
  val accent: String = "",
  val status: PublicPresence? = null,
  val fulfillment: Fulfillment = Fulfillment(),
  val deliveryFeeMinor: Long = 0,
  val minOrderMinor: Long = 0,
  val currency: String = "AFN",
  val payment: Payment = Payment(),
  val categories: List<String> = emptyList(),
  val media: ShopMedia = ShopMedia(),
)

/** §۱.۴ — نشانِ موجودی. عددها فقط وقتی دکان «دقیق» را خواسته. */
@Serializable
data class StockBadge(
  val status: String,
  val reason: String? = null,
  val checkedAt: Long? = null,
  val unit: String? = null,
  val prepMinutes: Int? = null,
  val sellableMilli: Long? = null,
  val readyMilli: Long? = null,
  val laterMilli: Long? = null,
) {
  companion object {
    const val IN_STOCK = "in_stock"
    const val IN_WAREHOUSE = "in_warehouse"
    const val OUT_OF_STOCK = "out_of_stock"
    const val UNVERIFIED = "unverified"
    const val UNAVAILABLE = "unavailable"
  }
}

@Serializable
data class ProductSpec(val name: String, val value: String)

/** ارجاعِ کوتاه به دکان روی کالای بازار؛ `distanceKm` فقط با «نزدیکِ من»، گردشده. */
@Serializable
data class PublicShopRef(
  val slug: String,
  val title: String,
  val accent: String = "",
  val badge: String = "",
  val orderAcceptance: String = "",
  val distanceKm: Double? = null,
)

/**
 * §۱.۳ — کالا. یک مدل برای ویترین، صفحهٔ کالا (`description`، `specs`)، خانهٔ بازار
 * (`regularPriceMinor` فقط با «ارزان‌تر» ِ صاحب دکان، `shop`) و جست‌وجو.
 */
@Serializable
data class PublicProduct(
  val id: String,
  val name: String,
  val unit: String = "",
  val category: String = "",
  val priceMinor: Long? = null,
  val maxQtyMilli: Long? = null,
  val photo: Boolean = false,
  val stock: StockBadge? = null,
  val description: String? = null,
  val specs: List<ProductSpec> = emptyList(),
  val regularPriceMinor: Long? = null,
  val shop: PublicShopRef? = null,
)

@Serializable
data class PublicOrderLine(
  val productId: String,
  val name: String,
  val unit: String = "",
  val qtyMilli: Long,
  val unitPriceMinor: Long,
  val lineTotalMinor: Long,
  val note: String = "",
  /** CHK-16 (#525): ترجیحِ جایگزینیِ همین قلم ('' | best_match | specific | none | refund) و نامِ «کالای مشخص» */
  val substitution: String = "",
  val substituteName: String = "",
)

@Serializable
data class PublicOrderEvent(val status: String, val at: Long)

/** §۱.۷ — سفارش برای مشتری (پیگیری با توکنِ HMAC دار). */
@Serializable
data class PublicOrder(
  val token: String,
  val orderNo: Long,
  val status: String,
  val statusReason: String = "",
  /** CHK-15 (#525): ترجیحِ کلیِ جایگزینی ('' | best_match | none | refund) */
  val substitution: String = "",
  val fulfillment: String,
  val paymentMethod: String = "cod",
  val paymentStatus: String = "",
  val currency: String = "AFN",
  val itemsCount: Int = 0,
  val lines: List<PublicOrderLine> = emptyList(),
  val subtotalMinor: Long = 0,
  val discountMinor: Long = 0,
  val deliveryFeeMinor: Long = 0,
  val totalMinor: Long,
  val readyAt: Long? = null,
  val scheduledFor: Long? = null,
  val slotEndsAt: Long? = null,
  val zoneName: String = "",
  /** CHK-06/07 (#494): ریزِ هزینهٔ ارسال، هر دو درونِ `deliveryFeeMinor` */
  val smallBasketFeeMinor: Long = 0,
  val slotFeeMinor: Long = 0,
  val createdAt: Long,
  val updatedAt: Long,
  val canCancel: Boolean = false,
  val timeline: List<PublicOrderEvent> = emptyList(),
  /** فقط در پیگیری (`GET /api/store/:slug/orders/:token`، PAY-21 #529) */
  val onlinePay: OnlinePayment? = null,
)

/** §۱.۸ — پیک برای مشتری (S5 وصلش می‌کند؛ امروز سرور در پاسخی نمی‌دهد). */
@Serializable
data class PublicDelivery(val status: String, val mode: String)

object MarketContract {
  /** همان `SCHEMA_VERSION` ِ `server/src/lib/public-views.js`. */
  const val SCHEMA_VERSION = 1

  val json: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = false
  }

  private fun <T> parse(s: DeserializationStrategy<T>, text: String): T = json.decodeFromString(s, text)

  fun shop(text: String): PublicShop = parse(PublicShop.serializer(), text)
  fun presence(text: String): PublicPresence = parse(PublicPresence.serializer(), text)
  fun product(text: String): PublicProduct = parse(PublicProduct.serializer(), text)
  fun products(text: String): List<PublicProduct> = parse(ListSerializer(PublicProduct.serializer()), text)
  fun stock(text: String): StockBadge = parse(StockBadge.serializer(), text)
  fun order(text: String): PublicOrder = parse(PublicOrder.serializer(), text)
  fun delivery(text: String): PublicDelivery = parse(PublicDelivery.serializer(), text)
}
