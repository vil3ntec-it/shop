package ir.vil3ntec.tohid.core.model

import kotlinx.serialization.Serializable

/*
 *  شکلِ پاسخ‌های `/api/shop/orders` (server/src/routes/orders.js و lib/orders.js).
 *  هر میدان پیش‌فرض دارد تا میدانِ تازهٔ سرور یا میدانِ نبوده هیچ نسخهٔ نصب‌شده‌ای
 *  را نشکند (`ApiJson`: ignoreUnknownKeys، explicitNulls=false).
 */

@Serializable
data class OrderPermsDto(
  val create: Boolean = false,
  val manage: Boolean = false,
  val contact: Boolean = false,
  val refund: Boolean = false,
  /**
   *  همان سنجشِ Sync v1 برای فروشِ تحویل (`order-ledger.capsOf`): نقشی که این‌ها را ندارد
   *  فروش را فقط روی دستگاهِ خودش نگه می‌داشت ⇒ ثبت را به دستگاهِ توانا می‌سپارد.
   */
  val recordSale: Boolean = false,
  val saleDiscount: Boolean = false,
  val saleCancel: Boolean = false,
)

/** `GET /shop/orders/config` — بخش فقط وقتی `enabled` است دیده می‌شود */
@Serializable
data class OrdersConfigDto(
  val flag: Boolean = false,
  val entitled: Boolean = false,
  val enabled: Boolean = false,
  val tabs: List<String> = emptyList(),
  val reasons: Map<String, List<String>> = emptyMap(),
  val sources: List<String> = emptyList(),
  val permissions: OrderPermsDto = OrderPermsDto(),
  /** «با اسکناسِ …» و ویرایشِ دستورِ تحویل تا پیش از ارسال (CHK-14، CHK-24؛ پرچمِ `shop.orders.delivery_prefs`) */
  val deliveryPrefs: Boolean = false,
  /** سرور «فروشِ تحویل در برنامه» را دارد — بی این (سرورِ کهنه) برنامه هیچ فروشی نمی‌سازد */
  val saleOnHandover: Boolean = false,
  /**
   *  کفِ همین دکان روی سرور: تحویلِ پیش از این لحظه هرگز فروشِ برنامه نمی‌گیرد. سرور آن را با
   *  نخستین `config?caps=saleOnHandover` می‌گذارد؛ null ⇒ هنوز نه ⇒ هیچ فروشی.
   */
  val handoverSince: Long? = null,
  /** پرچمِ خاموشِ `shop.orders.picking` (PCK): برگهٔ جمع‌آوری به‌جای گذارهای سادهٔ «جمع‌آوری/بسته‌بندی» */
  val picking: Boolean = false,
  /** ترجیحِ کلیِ جایگزینی در فرمِ سفارشِ دستی (CHK-15، پرچمِ `shop.orders.substitution`) */
  val substitution: Boolean = false,
  /** ORD-07 «عقب انداختن» (پرچمِ `shop.orders.delay`) — خاموش ⇒ null و هیچ دکمه‌ای */
  val delay: OrderDelayCfgDto? = null,
)

/** `delay` ِ `/config` (`order-delay.configOf`): وضعیت‌های مجاز، دلیل‌ها و سقفِ دقیقه */
@Serializable
data class OrderDelayCfgDto(
  val statuses: List<String> = emptyList(),
  val reasons: List<String> = emptyList(),
  val maxMinutes: Int = 0,
)

/** `GET /shop/orders/summary` — شمارشِ برگه‌های باز و نشانگرِ تغییر */
@Serializable
data class OrdersSummaryDto(
  val counts: Map<String, Int> = emptyMap(),
  val oldestNewAt: Long? = null,
  val rev: Long = 0,
  val serverTime: Long = 0,
)

@Serializable
data class OrderCustomerDto(
  val name: String = "",
  val phoneMasked: String = "",
  /** فقط با دسترسیِ `orders.contact` از سرور می‌آید */
  val phone: String? = null,
)

@Serializable
data class OrderAddressDto(
  val lat: Double? = null,
  val lng: Double? = null,
  val landmark: String = "",
  val text: String = "",
)

/** دستورِ تحویلِ سفارش — «پیش از رسیدن زنگ بزنید»، «تحویل به…»، اسکناسِ مشتری (CHK-14/23/24) */
@Serializable
data class OrderDeliveryDto(
  val callBeforeArrival: Boolean = false,
  val leaveWith: String = "",
  val photoConsent: Boolean = false,
  /** مبلغی که مشتری هنگامِ تحویل می‌دهد (یک‌صدم)؛ null ⇒ نگفته */
  val cashTenderMinor: Long? = null,
)

@Serializable
data class OrderLineDto(
  val lineNo: Int = 0,
  val productId: String = "",
  val name: String = "",
  val unit: String = "",
  val qtyMilli: Long = 0,
  val unitPriceMinor: Long = 0,
  val lineTotalMinor: Long = 0,
  val note: String = "",
  val substitution: String = "",
  /** «کالای مشخص»: نامِ جایگزین (CHK-15) */
  val substituteName: String = "",
)

/** `detail` ِ رویداد — `type` فقط برای رویدادِ `note` (`ready_delay` ِ ORD-07، `start_alert` ِ ORD-18، …) */
@Serializable
data class OrderEventDetailDto(
  val readyAt: Long? = null,
  val type: String = "",
  val prevReadyAt: Long? = null,
  val scheduledFor: Long? = null,
)

@Serializable
data class OrderEventDto(
  val seq: Int = 0,
  val kind: String = "",
  val fromStatus: String = "",
  val toStatus: String = "",
  val actorType: String = "",
  val actorId: String = "",
  val reason: String = "",
  val note: String = "",
  val detail: OrderEventDetailDto = OrderEventDetailDto(),
  val at: Long = 0,
)

/** کارتِ فهرست (ORD-11) و جزئیات (ORD-16) — میدان‌های جزئیات در کارت خالی‌اند */
@Serializable
data class OrderDto(
  val id: String = "",
  val orderNo: Long = 0,
  val status: String = "",
  val tab: String = "",
  val next: List<String> = emptyList(),
  val source: String = "",
  val isTest: Boolean = false,
  val fulfillment: String = "",
  val paymentMethod: String = "",
  val paymentStatus: String = "",
  val codStatus: String = "",
  val customer: OrderCustomerDto = OrderCustomerDto(),
  val itemsCount: Int = 0,
  val currency: String = "",
  val totalMinor: Long = 0,
  val scheduledFor: Long? = null,
  val readyAt: Long? = null,
  val statusReason: String = "",
  val version: Long = 0,
  val createdAt: Long = 0,
  val updatedAt: Long = 0,
  val canContact: Boolean = false,
  val address: OrderAddressDto = OrderAddressDto(),
  val note: String = "",
  /** ترجیحِ کلیِ جایگزینی (CHK-15)؛ هر قلم ترجیحِ مؤثرِ خودش را دارد */
  val substitution: String = "",
  val subtotalMinor: Long = 0,
  val discountMinor: Long = 0,
  val deliveryFeeMinor: Long = 0,
  /** دستورِ تحویل (`online_orders.delivery`) — فقط در جزئیات */
  val delivery: OrderDeliveryDto = OrderDeliveryDto(),
  val lines: List<OrderLineDto> = emptyList(),
  val events: List<OrderEventDto> = emptyList(),
  /** فقط در `GET /shop/orders/handover`: لحظهٔ نخستین تحویل (زمانِ سرور) */
  val handedOverAt: Long? = null,
  /** فقط در `handover`: `sale` (فروش بساز) یا `reverse` (فروش را لغو کن) */
  val action: String = "",
  /** فقط در `handover` برای `reverse`: لحظهٔ برگشت به دکان */
  val returnedAt: Long? = null,
  /**
   *  فقط در `handover` برای `refund`: همهٔ رویدادهای بازپرداختِ همین سفارش با مبلغِ سقف‌دارِ سرور
   *  (`order-ledger.refundEventsOf`) — برنامه نشسته‌ها را خودش رد می‌کند (`core/orders/OrderRefunds`)
   */
  val refundEvents: List<RefundEventDto> = emptyList(),
  /** فقط در `handover` برای `refund`: همین کاربر سندِ برگشت را می‌تواند بنویسد (`returns.create`) */
  val refundable: Boolean? = null,
  /**
   *  فقط در `handover` برای `sale`: همین کاربر (اجازهٔ مؤثرش، با سقفِ درصد/مبلغِ تخفیفِ **همین**
   *  سفارش) فروش را می‌تواند بنویسد. null (سرورِ کهنه) ⇒ اجازهٔ کلیِ نقش.
   */
  val recordable: Boolean? = null,
  /** فقط در `handover` برای `reverse`: همین کاربر لغوِ فروش را می‌تواند بنویسد */
  val reversible: Boolean? = null,
  /** فقط در `handover` برای `review`: چرا خودکار نه (`before_floor`: نخستین تحویل پیش از کفِ دکان) */
  val reviewCode: String? = null,
)

/** یک رویدادِ بازپرداخت: `r<seq>` (ردیفِ دفتر) یا `full` (گذارِ «بازپرداختِ کامل» / پولِ برگشتهٔ درگاه) */
@Serializable
data class RefundEventDto(
  val key: String = "",
  val amountMinor: Long = 0,
  val at: Long = 0,
  /** `ledger` | `status` | `gateway` */
  val source: String = "",
)

/** `GET /shop/orders/handover` — سفارش‌های تحویل‌شده‌ای که فروششان هنوز در دفترِ دکان نیست */
@Serializable
data class HandoverPageDto(
  val items: List<OrderDto> = emptyList(),
  /** برگشته به دکان با فروشِ لغونشده */
  val reversals: List<OrderDto> = emptyList(),
  /** بررسیِ صاحب: پیش از کف بیرون رفت و پس از آن تحویل شد — هرگز خودکار */
  val review: List<OrderDto> = emptyList(),
  /** بازپرداختِ سفارشِ دارای فروش که سندِ برگشتش هنوز در دفترِ دکان ننشسته (`action = refund`) */
  val refunds: List<OrderDto> = emptyList(),
  /** صفحهٔ بعد (`after`) — سفارشِ گیرکرده جلوی بقیه را نمی‌گیرد */
  val nextAfter: String? = null,
  val since: Long = 0,
  val serverTime: Long = 0,
)

@Serializable
data class OrderPageDto(
  val tab: String = "",
  val items: List<OrderDto> = emptyList(),
  val nextCursor: String? = null,
)
