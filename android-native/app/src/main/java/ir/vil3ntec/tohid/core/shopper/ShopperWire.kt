package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.storefront.MarketCategoryDto
import kotlinx.serialization.Serializable

/*
 *  شکلِ پاسخ‌های بازارچه و ویترین، همان‌طور که سرور می‌دهد — حالتِ «خرید».
 *
 *  داور: `server/src/lib/market.js` (کارتِ دکان، جست‌وجو، خانهٔ «اول کالا»)،
 *  `server/src/lib/storefront.js` (پروفایل، کالا، سفارشِ عمومی) و
 *  `server/src/lib/storefront-delivery.js` (ناحیه و بازه).
 *
 *  ⛔ پول به یک‌صدمِ افغانی (Long) و مقدار به هزارم (Long) — بی ممیزِ شناور.
 *  ⛔ هر میدان پیش‌فرض دارد: سرورِ قدیمی‌تر یا تازه‌تر نباید برنامه را بشکند
 *  (`ApiJson.format`: کلیدِ ناشناخته نادیده، `null` ⇒ همان پیش‌فرض).
 */

/* ------------------------------ وضعیتِ دکان (§۳۱.۱) ------------------------------ */

/**
 *  هم‌شکلِ `PresenceStatusDto` (`data/repo/StorefrontRepository.kt`، برگهٔ ویترینِ صاحب
 *  دکان). هسته نمی‌تواند به `data.repo` تکیه کند؛ یکی شدنشان = بردنِ آن مدل به
 *  `core/storefront` و عوض کردنِ importِ صفحهٔ ویترین — کارِ گروهِ همان صفحه (G8).
 *
 *  `badge`: open_online | open_unconfirmed | declared_until | closed | offline
 *  `orderAcceptance`: accepting | delayed | later — سفارش در هر سه ثبت می‌شود؛
 *  فقط می‌گوید کی بررسی می‌شود.
 */
@Serializable
data class ShopStatus(
  val badge: String = "",
  val orderAcceptance: String = "",
  val connection: String = "",
  val lastConfirmedAt: Long? = null,
  val declaredUntil: Long? = null,
  val timezone: String = "Asia/Kabul",
  val localTime: String = "",
  val hoursSet: Boolean = false,
  val holiday: Boolean = false,
  /** بازه‌های امروز به دقیقه از نیمه‌شب: `[[باز، بسته]، …]` */
  val today: List<List<Int>> = emptyList(),
  val notice: String = "",
) {
  /** «باز» برای کارت: آنلاین، اعلام‌شده یا باز بی تأییدِ اتصال */
  val isOpen: Boolean get() = badge == "open_online" || badge == "declared_until" || badge == "open_unconfirmed"
}

@Serializable
data class Fulfillment(val delivery: Boolean = false, val pickup: Boolean = false)

/* ------------------------------ بازارچه ------------------------------ */

@Serializable
data class Availability(val available: Boolean = false)

@Serializable
data class MarketOverview(
  val shopCount: Int = 0,
  /** همان مدلِ دستهٔ برگهٔ «بازارچه» ِ صاحب دکان (`MarketListing`)، با شمارِ دکان‌ها */
  val categories: List<MarketCategoryDto> = emptyList(),
  val verification: Availability = Availability(),
)

/** کارتِ عمومیِ دکان — بی شناسهٔ داخلی، شماره، صاحب یا مختصات. */
@Serializable
data class ShopCard(
  val slug: String = "",
  val title: String = "",
  val tagline: String = "",
  val accent: String = "",
  val category: String = "",
  val categoryLabel: String = "",
  val area: String = "",
  val status: ShopStatus = ShopStatus(),
  val fulfillment: Fulfillment = Fulfillment(),
  val deliveryFeeMinor: Long = 0,
  val minOrderMinor: Long = 0,
  val currency: String = "AFN",
  val productCount: Int = 0,
  val distanceKm: Double? = null,
  /** شناسهٔ کالایی از خودِ دکان که عکس دارد (جلدِ کارت) — سرورِ قدیمی ⇒ null */
  val cover: String? = null,
  val isNew: Boolean = false,
)

@Serializable
data class ShopPage(
  val shops: List<ShopCard> = emptyList(),
  val total: Int = 0,
  val nextOffset: Int? = null,
)

/** نشانِ موجودی (۳۲.۳) — ناشناخته هرگز «موجود» نیست. */
@Serializable
data class StockBadge(
  val status: String = "unverified",
  val checkedAt: Long? = null,
  val prepMinutes: Int? = null,
) {
  /** سفارش‌پذیر: هر چیزی جز ناموجود و «قابلِ سفارش نیست» */
  val orderable: Boolean get() = status != "out_of_stock" && status != "unavailable"
  val known: Boolean get() = status == "in_stock" || status == "in_warehouse" || !orderable
}

@Serializable
data class ProductSpec(val name: String = "", val value: String = "")

/** کالای ویترین — در کاتالوگ، جست‌وجو، قفسه و صفحهٔ کالا. */
@Serializable
data class StoreProduct(
  val id: String = "",
  val name: String = "",
  val unit: String = "",
  val category: String = "",
  val priceMinor: Long? = null,
  /** قیمتِ دفترِ دکان وقتی قیمتِ آنلاین کمتر است (قفسهٔ «ارزان‌تر») */
  val regularPriceMinor: Long? = null,
  val maxQtyMilli: Long? = null,
  val photo: Boolean = false,
  val stock: StockBadge = StockBadge(),
  val description: String = "",
  val specs: List<ProductSpec> = emptyList(),
)

/** دکانِ یک کالا در نتیجهٔ بازارچه */
@Serializable
data class ShopRef(
  val slug: String = "",
  val title: String = "",
  val accent: String = "",
  val badge: String = "",
  val orderAcceptance: String = "",
  val distanceKm: Double? = null,
)

@Serializable
data class MarketProduct(
  val id: String = "",
  val name: String = "",
  val unit: String = "",
  val category: String = "",
  val priceMinor: Long? = null,
  val regularPriceMinor: Long? = null,
  val maxQtyMilli: Long? = null,
  val photo: Boolean = false,
  val stock: StockBadge = StockBadge(),
  val shop: ShopRef = ShopRef(),
) {
  fun asStoreProduct(): StoreProduct = StoreProduct(
    id = id, name = name, unit = unit, category = category, priceMinor = priceMinor,
    regularPriceMinor = regularPriceMinor, maxQtyMilli = maxQtyMilli, photo = photo, stock = stock,
  )
}

@Serializable
data class MarketSearch(
  val q: String = "",
  /** عبارت همان نامِ یک دستهٔ دکان بود ⇒ کلیدِ آن دسته */
  val category: String = "",
  val shops: List<ShopCard> = emptyList(),
  val products: List<MarketProduct> = emptyList(),
)

/** خانهٔ «اول کالا» (`GET /market/products`) — فقط دادهٔ واقعی؛ نبود ⇒ خالی. */
@Serializable
data class MarketFeed(
  val items: List<MarketProduct> = emptyList(),
  val popular: List<MarketProduct> = emptyList(),
  val deals: List<MarketProduct> = emptyList(),
  val windowDays: Int = 0,
  val shops: List<ShopCard> = emptyList(),
  val shopTotal: Int = 0,
)

/* ------------------------------ ویترینِ یک دکان ------------------------------ */

@Serializable
data class DeliveryZone(
  val id: String = "",
  val name: String = "",
  val feeMinor: Long = 0,
  val minOrderMinor: Long = 0,
  val freeOverMinor: Long? = null,
)

@Serializable
data class Scheduling(val delivery: Boolean = false, val pickup: Boolean = false) {
  fun of(fulfillment: String): Boolean = if (fulfillment == ShopperCheckout.PICKUP) pickup else delivery
}

/** ناحیه و بازهٔ ویترین؛ پرچمِ دکان خاموش ⇒ بی ناحیه، «هرچه زودتر» باز. */
@Serializable
data class DeliveryInfo(
  val zoned: Boolean = false,
  val zones: List<DeliveryZone> = emptyList(),
  val allowAsap: Boolean = true,
  val scheduling: Scheduling = Scheduling(),
)

@Serializable
data class PaymentInfo(val methods: List<String> = listOf("cod"))

@Serializable
data class StoreProfile(
  val slug: String = "",
  val title: String = "",
  val tagline: String = "",
  val contactPhone: String = "",
  val accent: String = "",
  val status: ShopStatus = ShopStatus(),
  val fulfillment: Fulfillment = Fulfillment(),
  val deliveryFeeMinor: Long = 0,
  val minOrderMinor: Long = 0,
  val currency: String = "AFN",
  val payment: PaymentInfo = PaymentInfo(),
  val categories: List<String> = emptyList(),
  val delivery: DeliveryInfo = DeliveryInfo(),
)

@Serializable
data class StoreHome(
  val store: StoreProfile = StoreProfile(),
  val verification: Availability = Availability(),
  val market: Availability = Availability(),
)

@Serializable
data class Highlights(
  val bestsellers: List<StoreProduct> = emptyList(),
  val newArrivals: List<StoreProduct> = emptyList(),
  val windowDays: Int = 0,
)

@Serializable
data class CatalogPage(
  val items: List<StoreProduct> = emptyList(),
  val nextCursor: String? = null,
)

@Serializable
data class ProductDetail(
  val product: StoreProduct = StoreProduct(),
  val similar: List<StoreProduct> = emptyList(),
  val complementary: List<StoreProduct> = emptyList(),
)

@Serializable
data class DeliverySlot(
  val startsAt: Long = 0,
  val endsAt: Long = 0,
  /** روزِ محلیِ دکان `YYYY-MM-DD` */
  val date: String = "",
  val startMin: Int = 0,
  val endMin: Int = 0,
  val available: Boolean = false,
  /** «کم مانده» — فقط همین، نه شمارِ دقیق */
  val few: Boolean = false,
)

@Serializable
data class SlotList(
  val fulfillment: String = "",
  val scheduling: Boolean = false,
  val allowAsap: Boolean = true,
  val timezone: String = "Asia/Kabul",
  val slots: List<DeliverySlot> = emptyList(),
)

/** «این پین در کدام ناحیه است؟» */
@Serializable
data class ZoneLookup(
  val zoned: Boolean = false,
  val inZone: Boolean = true,
  val zone: DeliveryZone? = null,
  val pickup: Boolean = false,
)

/* ------------------------------ تأییدِ شماره (۲۳) ------------------------------ */

@Serializable
data class CodeSent(
  val sent: Boolean = false,
  val expiresAt: Long = 0,
  val resendSeconds: Int = 0,
)

/** بلیتِ «این شماره تأیید شد» — بینِ بازارچه و همهٔ ویترین‌ها مشترک. */
@Serializable
data class PhoneTicket(
  val phone: String = "",
  val ticket: String = "",
  val expiresAt: Long = 0,
) {
  fun validAt(now: Long): Boolean = ticket.isNotBlank() && phone.isNotBlank() && expiresAt > now
}

/* ------------------------------ سفارش ------------------------------ */

@Serializable
data class OrderLine(
  val productId: String = "",
  val name: String = "",
  val unit: String = "",
  val qtyMilli: Long = 0,
  val unitPriceMinor: Long = 0,
  val lineTotalMinor: Long = 0,
  val note: String = "",
)

@Serializable
data class TimelineEntry(val status: String = "", val at: Long = 0)

/** نمای خریدار از سفارش — نه شناسهٔ کارمند، نه یادداشتِ داخلی، نه شماره و نشانی. */
@Serializable
data class PublicOrder(
  val token: String = "",
  val orderNo: Long = 0,
  val status: String = "",
  val statusReason: String = "",
  val fulfillment: String = "",
  val paymentMethod: String = "",
  val paymentStatus: String = "",
  val currency: String = "AFN",
  val itemsCount: Int = 0,
  val lines: List<OrderLine> = emptyList(),
  val subtotalMinor: Long = 0,
  val discountMinor: Long = 0,
  val deliveryFeeMinor: Long = 0,
  val totalMinor: Long = 0,
  val readyAt: Long? = null,
  val scheduledFor: Long? = null,
  val slotEndsAt: Long? = null,
  val zoneName: String = "",
  val createdAt: Long = 0,
  val updatedAt: Long = 0,
  val canCancel: Boolean = false,
  val timeline: List<TimelineEntry> = emptyList(),
) {
  /** پرداخت‌شده تا امروز (فقط نقد هنگامِ تحویل ⇒ تا «پرداخت شد» صفر) */
  val paidMinor: Long get() = if (paymentStatus == "paid") totalMinor else 0
  val dueMinor: Long get() = totalMinor - paidMinor
}

@Serializable
data class StoreRef(val slug: String = "", val title: String = "", val accent: String = "")

/** «سفارش‌های من» در همهٔ ویترین‌ها (`GET /market/orders`) */
@Serializable
data class MarketOrder(
  val orderNo: Long = 0,
  val status: String = "",
  val totalMinor: Long = 0,
  val itemsCount: Int = 0,
  val fulfillment: String = "",
  val createdAt: Long = 0,
  val token: String = "",
  val store: StoreRef = StoreRef(),
)

/** «خریدِ دوباره» با قیمت و موجودیِ امروز — چیزی ساخته نمی‌شود. */
@Serializable
data class ReorderLine(
  val productId: String = "",
  val name: String = "",
  val unit: String = "",
  val qtyMilli: Long = 0,
  val previousPriceMinor: Long = 0,
  val priceMinor: Long? = null,
  val maxQtyMilli: Long? = null,
  val available: Boolean = false,
  /** out_of_stock | not_in_store | «» */
  val reason: String = "",
)

@Serializable
data class Reorder(val orderNo: Long = 0, val lines: List<ReorderLine> = emptyList())

/** برگهٔ قیمتِ امروزِ سبد — همراهِ خطای `price_changed` و `below_min_order` */
@Serializable
data class Quote(
  val lines: List<OrderLine> = emptyList(),
  val subtotalMinor: Long = 0,
  val deliveryFeeMinor: Long = 0,
  val totalMinor: Long = 0,
  val minOrderMinor: Long? = null,
)
