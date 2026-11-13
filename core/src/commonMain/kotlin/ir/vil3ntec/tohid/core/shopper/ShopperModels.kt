package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.Fulfillment
import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.market.PublicPresence
import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.market.PublicShop
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 *  مدل‌های برنامهٔ خرید که در قراردادِ بازار (`core/market/MarketContract.kt`) نیستند — همان JSON ِ
 *  `server/src/routes/storefront.js` و `routes/market.js` که امروز فقط JS ِ وب (`server/public/store`) می‌خواند.
 *  فیلدِ ناشناخته نادیده؛ پول به یک‌صدمِ افغانی (`…Minor`)، مقدار به هزارم (`…Milli`)، زمان میلی‌ثانیهٔ یونیکس.
 */

/** ناحیهٔ ارسال (`publicZone` ِ `lib/storefront-delivery.js`؛ CHK-05/06/07). */
@Serializable
data class DeliveryZone(
  val id: String,
  val name: String,
  val feeMinor: Long = 0,
  val minOrderMinor: Long = 0,
  /** null ⇒ ارسالِ رایگان ندارد */
  val freeOverMinor: Long? = null,
  /** null ⇒ زیرِ حداقل پذیرفته نیست؛ عدد ⇒ با همین هزینه پذیرفته است (CHK-06) */
  val smallBasketFeeMinor: Long? = null,
)

@Serializable
data class Scheduling(val delivery: Boolean = false, val pickup: Boolean = false)

/** `store.delivery` (`publicInfo`): پرچمِ `shop.storefront.zones` خاموش ⇒ بی ناحیه و بی زمان‌بندی. */
@Serializable
data class StoreDelivery(
  val zoned: Boolean = false,
  val zones: List<DeliveryZone> = emptyList(),
  val allowAsap: Boolean = true,
  val scheduling: Scheduling = Scheduling(),
)

/** `GET /api/store/:slug` — پروفایلِ دکان + آن‌چه ثبت لازم دارد. */
class StorePage(
  val shop: PublicShop,
  val delivery: StoreDelivery,
  /** کدِ تأییدِ شماره روی این سرور واقعاً می‌رسد؟ (۲۳.۳) نه ⇒ ثبت با برنامه شدنی نیست */
  val verificationAvailable: Boolean,
  val marketAvailable: Boolean,
  /** بخش‌های پرچم‌دارِ همین دکان (`features` ِ پاسخ؛ پرچمِ خاموش ⇒ کلید نیست ⇒ false) */
  val features: StoreFeatures = StoreFeatures(),
  /** WEB-13: «دیده‌شده‌های اخیر» (`recent: true` ِ ریشهٔ پاسخ، پرچمِ `shop.storefront.recent`؛ [RecentProducts]) */
  val recent: Boolean = false,
)

/** `features` ِ `GET /api/store/:slug` (فقط کلیدهایی که برنامهٔ خرید به کار می‌برد). */
@Serializable
data class StoreFeatures(
  /** گفت‌وگو با دکان (`shop.chat`) */
  val chat: Boolean = false,
  /** نظرهای تأییدشده (`shop.orders.reviews`) */
  val reviews: Boolean = false,
  /** WEB-11: ♥ و «فهرستِ خرید» (`shop.storefront.favorites`) */
  val favorites: Boolean = false,
  /** CHK-01: حسابِ مشتری در ویترین (`shop.storefront.web_account`)؛ سبدِ حساب فقط با [accountCart] (`shop.storefront.cart`) */
  val webAccount: Boolean = false,
  val accountCart: Boolean = false,
  /** CHK-14: «با کدام اسکناس…» در برگهٔ ثبت (`shop.orders.delivery_prefs`؛ [CheckoutRules.tenders]) */
  val deliveryPrefs: Boolean = false,
  /** RPT-14: تپشِ بالای قیف (`shop.orders.online_report`؛ [ShopperFunnel]) */
  val funnel: Boolean = false,
  /** CRM-09: عکسِ دروازه و یادداشتِ صوتیِ نشانیِ حساب (`shop.storefront.address_media`؛ [AddressMedia]) */
  val addressMedia: Boolean = false,
  /** CHK-08 / ۴-۳: «انتخاب روی نقشه» (`shop.storefront.map_pin` + `STORE_MAP_TILES` ِ سرور؛ [PinMap]) */
  val mapPin: MapPinFeature? = null,
)

/** کاتالوگ با صفحه‌بندیِ نشانگری (`nextCursor`). */
@Serializable
data class CatalogPage(val items: List<PublicProduct> = emptyList(), val nextCursor: String? = null)

/** برگهٔ کالا (WEB-08): کالا با `description`/`specs` و دو قفسه. */
@Serializable
data class ProductPage(
  val product: PublicProduct,
  val similar: List<PublicProduct> = emptyList(),
  val complementary: List<PublicProduct> = emptyList(),
)

/** `GET /:slug/delivery?lat&lng` — این پین در کدام ناحیه است (بی ذخیره). */
@Serializable
data class LocateResult(val zoned: Boolean = false, val inZone: Boolean = true, val zone: DeliveryZone? = null, val pickup: Boolean = false)

/** یک بازهٔ زمانِ تحویل (CHK-12؛ `publicSlot`). */
@Serializable
data class DeliverySlot(
  val startsAt: Long,
  val endsAt: Long,
  val date: String = "",
  val startMin: Int = 0,
  val endMin: Int = 0,
  val available: Boolean = false,
  val few: Boolean = false,
  /** CHK-07: هزینهٔ اضافهٔ همین بازه (فقط ارسال) */
  val feeMinor: Long = 0,
)

@Serializable
data class SlotsPage(
  val fulfillment: String = "",
  val scheduling: Boolean = false,
  val allowAsap: Boolean = true,
  val timezone: String = "Asia/Kabul",
  val slots: List<DeliverySlot> = emptyList(),
)

/** `POST /:slug/verify/start` (کد به ایمیل). `devCode` فقط سرورِ غیرِ تولید (آزمون) — برنامه هرگز خودش پُرش نمی‌کند. */
@Serializable
data class CodeSent(val sent: Boolean = false, val expiresAt: Long? = null, val resendSeconds: Int? = null, val devCode: String? = null)

/**
 *  بلیتِ ایمیلِ تأییدشده (`X-Store-Ticket`؛ «فقط ایمیل»، ۱۴۰۵/۰۸/۲۳)؛ `email` همان ایمیلِ کوچک‌حرفِ سرور. بلیتِ کهنهٔ
 *  شماره‌ای که روی دستگاه مانده `email` ندارد ("" ⇒ هرگز تأییدشده نیست، [CheckoutRules.verified]).
 */
@Serializable
data class CustomerTicket(val email: String = "", val ticket: String, val expiresAt: Long)

/** قلمِ برگهٔ قیمتِ امروز (`quoteOf` ِ سرور، درونِ ۴۰۹ ِ `price_changed`). */
@Serializable
data class QuoteLine(
  val productId: String,
  val name: String = "",
  val unit: String = "",
  val qtyMilli: Long = 0,
  val unitPriceMinor: Long = 0,
  val lineTotalMinor: Long = 0,
)

@Serializable
data class PriceQuote(
  val lines: List<QuoteLine> = emptyList(),
  val subtotalMinor: Long = 0,
  val deliveryFeeMinor: Long = 0,
  val totalMinor: Long = 0,
  val minOrderMinor: Long? = null,
)

/** پاسخِ ثبت: ۲۰۱ ⇒ تازه؛ ۲۰۰ با `replayed` ⇒ همان سفارشِ پیشینِ همین کلیدِ یکتایی (CHK-19). */
class Placed(val order: PublicOrder, val replayed: Boolean)

// ── بازارچه (`/api/market`، پرچمِ `market.directory`) ──

@Serializable
data class MarketCategory(val key: String, val label: String = "", val count: Int = 0)

@Serializable
data class MarketOverview(val shopCount: Int = 0, val categories: List<MarketCategory> = emptyList())

/** کارتِ دکان در بازار (`shopCard` ِ `lib/public-views.js`). */
@Serializable
data class ShopCard(
  val slug: String,
  val title: String,
  val tagline: String = "",
  val accent: String = "",
  val category: String = "",
  val categoryLabel: String = "",
  val area: String = "",
  val status: PublicPresence? = null,
  val fulfillment: Fulfillment = Fulfillment(),
  val deliveryFeeMinor: Long = 0,
  val minOrderMinor: Long = 0,
  val currency: String = "AFN",
  val productCount: Int = 0,
  val distanceKm: Double? = null,
  val isNew: Boolean = false,
  val cover: String? = null,
)

@Serializable
data class ShopList(val shops: List<ShopCard> = emptyList(), val total: Int = 0, val nextOffset: Int? = null)

// ── بدنهٔ ثبت (همان `orderBody` ِ `server/public/store/checkout.js`) ──

@Serializable
data class CheckoutCustomer(val name: String, val phone: String)

/** CHK-17/S32: فیِ دیده‌شده؛ ناهمخوان ⇒ ۴۰۹ `price_changed` حتی اگر جمع از قضا برابر بماند */
@Serializable
data class CheckoutLine(val productId: String, val qtyMilli: Long, val expectedUnitPriceMinor: Long)

@Serializable
data class CheckoutAddress(
  val text: String = "",
  val landmark: String = "",
  val area: String = "",
  val gateColor: String = "",
  /** بی پین ⇒ ناحیهٔ برگزیده (`source: customer`) */
  val zoneId: String? = null,
  /** CHK-08: پین (هر دو یا هیچ)؛ با پین ناحیه را سرور می‌گوید */
  val lat: Double? = null,
  val lng: Double? = null,
  /** CRM-09: نشانیِ حسابِ برگزیده (با نشستِ حساب ⇒ سرور عکسِ دروازه/یادداشتِ همان نشانی را به سفارش می‌بندد)؛ null در بدنه نمی‌رود */
  val savedId: String? = null,
)

/** [cashTenderMinor] (CHK-14): اسکناسِ مشتری هنگامِ تحویل؛ null ⇒ در بدنه نیست (همان سفارشِ بی پرچم) */
@Serializable
data class CheckoutDelivery(val callBeforeArrival: Boolean = false, val leaveWith: String = "", val cashTenderMinor: Long? = null)

@Serializable
data class CheckoutRequest(
  val fulfillment: String,
  val paymentMethod: String = "cod",
  val customer: CheckoutCustomer,
  val lines: List<CheckoutLine>,
  val note: String = "",
  /** جمعِ دیده‌شده — فقط تا سفارشِ با قیمتِ عوض‌شده ثبت نشود؛ مبلغ را سرور می‌گوید */
  val expectedTotalMinor: Long,
  /** NTF-03: پیامِ وضعیتِ سفارش به همین زبان */
  val lang: String,
  val slotStartsAt: Long? = null,
  val address: CheckoutAddress? = null,
  val delivery: CheckoutDelivery? = null,
  /** MKT-01: کدِ تخفیف — سرور در همان تراکنش می‌سنجد */
  val promoCode: String? = null,
  /** MKT-02: شناسهٔ تصادفیِ همین دستگاه، فقط وقتی دکان سدِ «یک بار برای هر دستگاه» دارد */
  val deviceId: String? = null,
  /** MKT-11: کدِ پیوند/QR ِ کمپین که مشتری با آن آمد — فقط نسبت داده می‌شود، مبلغ را دست نمی‌زند */
  val campaign: String? = null,
  /** S41: پاسخِ «سفارشِ دوم» به پرسشِ تکراری — فقط `true` فرستاده می‌شود (null در بدنه نمی‌رود ⇒ بدنهٔ پیشین همان) */
  val secondOrder: Boolean? = null,
)

/** JSON ِ برنامهٔ خرید: فیلدِ ناشناخته نادیده؛ null در بدنه نمی‌رود (همان `undefined` ِ وب). */
val ShopperJson: Json = Json {
  ignoreUnknownKeys = true
  coerceInputValues = true
  encodeDefaults = true
  explicitNulls = false
}
