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
 *    `core/test-vectors/market-public-v<نسخه>.json` (آزمونِ سرور و `MarketContractTest` هر دو). نسخهٔ تازه فقط می‌افزاید.
 *  • فیلدِ ناشناخته نادیده گرفته می‌شود (سرورِ تازه‌تر برنامهٔ کهنه را نمی‌شکند): `ignoreUnknownKeys` ِ [MarketContract.json]
 *    و `ShopperJson` ِ برنامهٔ خرید — `MarketContractTest.toleratesNewerServer` و `ShopperApiTest` می‌سنجند.
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

/** «سفارش می‌پذیرد؟» ِ ویترین (#467): `accepting=false` ⇒ ثبتِ سفارش بسته، با کد و پیامِ سرور */
@Serializable
data class StoreOrdering(val accepting: Boolean = true, val code: String? = null, val message: String? = null)

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
  /** «سفارش می‌پذیرد؟» (#467) — فقط با پرچمِ `shop.orders.subscribers_only`؛ نبودن = همان رفتارِ پیشین */
  val ordering: StoreOrdering? = null,
  /** نشانی و «مسیر روی نقشه» ِ دکان برای «دریافت از دکان» (مهاجرتِ ۲۱۵، ممیزیِ ویترین ۱۴۰۵/۰۸/۱۷)؛ نبودن = نگفته */
  val place: ShopPlace? = null,
)

/** نشانیِ دکان و پیوندِ نقشهٔ آن (سرور می‌سازد: پینِ صاحب، وگرنه جست‌وجوی نام و نشانی) */
@Serializable
data class ShopPlace(val text: String = "", val map: String = "")

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
  /** IMG-12: نسخهٔ عکس (۱۲ hex) برای `?v=` ِ نشانیِ عکس؛ عکسِ تازه ⇒ نسخهٔ تازه. null ⇒ بی عکس یا سرورِ کهنه (نشانیِ بی نسخه). */
  val photoVersion: String? = null,
  /** MKT-06: «چرخهٔ مصرف» (روز، ۱ تا ۳۶۵) — فقط وقتی دکان گذاشته؛ پایهٔ کارتِ «وقتِ دوباره خریدن؟» ِ برنامهٔ خرید (`Refills`) */
  val consumptionDays: Int? = null,
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

/**
 *  کارتِ «پرداخت» ِ پیگیری وقتی دکان «پرداخت پس از پذیرش» را روشن دارد (`server/src/lib/order-flow.js`، #910):
 *  [state] یکی از awaiting_accept · awaiting_choice · cash · online_pending · paid_online · cash_collected ·
 *  cash_settled · refunded · closed؛ [choice] = '' یا cash؛ [cash] = delivery یا pickup؛ مبلغ همیشه از سرور.
 */
@Serializable
data class PaymentStep(
  val state: String = "",
  val choice: String = "",
  val amountMinor: Long = 0,
  val cash: String = "",
  val canChoose: Boolean = false,
  val online: Boolean = false,
)

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
  /** فقط در پیگیری و فقط با «پرداخت پس از پذیرش» ِ روشن ([PaymentStep]) */
  val paymentStep: PaymentStep? = null,
)

/** v2 — یک ردیفِ دفترِ بازپرداخت در پیگیری: فقط مبلغ، روش، زمان و «پایانی» (سرور دلیل و کننده را نمی‌دهد) */
@Serializable
data class PublicRefundEntry(val amountMinor: Long = 0, val method: String = "", val at: Long = 0, val final: Boolean = false)

/** v2 — رسیدِ اصلاحیِ هر ردیف (E2E-RET-11، پرچمِ `shop.orders.refund_receipt`): `no` = `<شمارهٔ سفارش>-R<seq>`؛ مبلغ‌ها از سرور */
@Serializable
data class PublicRefundReceipt(
  val no: String = "",
  val seq: Int = 0,
  val at: Long = 0,
  val orderNo: Long = 0,
  val currency: String = "AFN",
  val method: String = "",
  val final: Boolean = false,
  val totalMinor: Long = 0,
  val refundedBeforeMinor: Long = 0,
  val amountMinor: Long = 0,
  val refundedAfterMinor: Long = 0,
  val netMinor: Long = 0,
)

/**
 *  v2 — `order.refund?` ِ پیگیری (NTF-08، `server/src/lib/order-refund-view.js`؛ پرچمِ پیش‌فرض‌روشنِ `shop.orders.refund_tracking`):
 *  نبود یا `null` ⇒ هیچ کارتی. وضعیت `requested` · `in_progress` · `partial` · `full`؛ ناشناخته ⇒ همان «درخواست ثبت شد».
 *  ⛔ در [PublicOrder] نیست: برنامهٔ خرید آن را جدا می‌خواند (`ShopperApi.trackPage`) تا شکلِ نادرستش فقط کارتِ بازپرداخت را ببرد.
 */
@Serializable
data class PublicRefund(
  val status: String = "requested",
  val requestedAt: Long? = null,
  val refundedMinor: Long = 0,
  val remainingMinor: Long = 0,
  val currency: String = "AFN",
  val entries: List<PublicRefundEntry> = emptyList(),
  val updatedAt: Long? = null,
  /** فقط با پرچمِ `shop.orders.refund_receipt`؛ نبود ⇒ خالی */
  val receipts: List<PublicRefundReceipt> = emptyList(),
)

/** §۱.۸ — پیک برای مشتری (S5 وصلش می‌کند؛ امروز سرور در پاسخی نمی‌دهد). */
@Serializable
data class PublicDelivery(val status: String, val mode: String)

object MarketContract {
  /** همان `SCHEMA_VERSION` ِ `server/src/lib/public-views.js`. */
  const val SCHEMA_VERSION = 2

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
  fun refund(text: String): PublicRefund = parse(PublicRefund.serializer(), text)
}
