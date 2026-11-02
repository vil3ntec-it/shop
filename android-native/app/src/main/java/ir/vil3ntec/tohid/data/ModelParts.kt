package ir.vil3ntec.tohid.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/*
 *  مدل‌های خالصِ دفتر که `Models.kt` به آن‌ها اشاره می‌کند — جدا از منطقِ اندرویدیِ پرونده‌های خودشان
 *  (`ProductPlans.kt`، `ProductVariantsMap.kt`، `SpecialOrders.kt`) تا ابزارِ `tools/shop-migrate` هم که فقط مدل‌ها را
 *  کامپایل می‌کند (`legacyFiles`) بی `ShopStore`/`SalesEngine` بسازد. JSON و رفتار همان است.
 */

/** PRD-10 — یک جزءِ کیت: کالا و مقدار به `unit` ِ همان کالا (`Product.kit`، و رونوشتش روی `SaleItem.parts`) */
@Serializable
data class KitPart(val productId: String = "", val qty: Double = 0.0)

/** PRD-13 — یک بازهٔ قیمتِ زمان‌بندی‌شده: از و تا (هر دو شامل، `yyyy-MM-dd`) و قیمتِ فروش (`Product.priceWindows`) */
@Serializable
data class PriceWindow(val from: String = "", val to: String = "", val price: Double = 0.0)

/** PRD-11 — یک ویژگیِ مدل («رنگ»: قرمز، آبی) روی `Product.variantAxes` */
@Serializable
data class VariantAxis(val name: String = "", val values: List<String> = emptyList())

/**
 *  POS-26 — سفارشِ ویژه روی ردیفِ سابقه (`SpecialOrders`): سند روی `special_order` (مشتری، کالا یا شرح، مقدار، جمع،
 *  پیش‌پرداخت، روزِ تحویل) و وضعیتِ تازه روی `special_order_status` (`to`، و فاکتورِ تحویل در `saleId`). پول به پول
 *  (یک‌صدمِ افغانی). هر فیلدِ خالی نوشته نمی‌شود — ردیفِ وضعیت فقط `to` (و `saleId`) دارد.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SpecialOrderDoc(
  @EncodeDefault(EncodeDefault.Mode.NEVER) val debtorId: String? = null,
  /** کالای فهرست؛ `null` ⇒ شرحِ دلخواه (قلمِ آزادِ فاکتورِ تحویل) */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val productId: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val item: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val qtyMilli: Long? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val totalMinor: Long? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val depositMinor: Long? = null,
  /** روزِ تحویل (`yyyy-MM-dd`) */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val due: String? = null,
  /** وضعیتِ تازه (`SpecialOrder.Status.code`) — فقط ردیفِ `special_order_status` */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val to: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val saleId: String? = null,
)

/**
 *  POS-27 — فروشِ امانی روی ردیفِ سابقه (`Layaways`): سند روی `layaway` (مشتری، نوع، ردیف‌های سبد با قیمتِ ثابت، جمع،
 *  پیش‌پرداخت و جدولِ قسط) و وضعیتِ تازه روی `layaway_status` (`to`، و فاکتورِ تحویل در `saleId`). پول به پول
 *  (یک‌صدمِ افغانی). هر فیلدِ خالی نوشته نمی‌شود — ردیفِ وضعیت فقط `to` (و `saleId`) دارد.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class LayawayDoc(
  @EncodeDefault(EncodeDefault.Mode.NEVER) val debtorId: String? = null,
  /** `Layaway.LAYAWAY` (کالا تا تسویه نزدِ دکان) */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val kind: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val totalMinor: Long? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val depositMinor: Long? = null,
  /** ردیف‌های سبد با قیمتِ همان لحظه — فاکتورِ تحویل همین‌ها را می‌فروشد */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val lines: List<LayawayLine>? = null,
  /** جدولِ قسط (`Layaway.schedule`) همان‌طور که به مشتری داده شد؛ جمع = جمع − پیش‌پرداخت */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val schedule: List<LayawayDue>? = null,
  /** وضعیتِ تازه (`Layaways.DELIVERED`/`CANCELLED`) — فقط ردیفِ `layaway_status` */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val to: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val saleId: String? = null,
)

/**
 *  یک ردیفِ سبدِ امانی: کالای فهرست ([productId]) یا قلمِ آزاد ([label]، فقط همان)، نامِ همان لحظه برای نمایش و چاپ
 *  (و قلمِ آزادِ فاکتور اگر کالا پاک شد)، مقدار به هزارم و قیمتِ واحد به پول
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class LayawayLine(
  val productId: String = "",
  @EncodeDefault(EncodeDefault.Mode.NEVER) val label: String? = null,
  val name: String = "",
  val qtyMilli: Long = 1000,
  val unitMinor: Long = 0,
)

/** یک قسطِ جدول: شماره از ۱، سررسید (`yyyy-MM-dd`) و مبلغ به پول */
@Serializable
data class LayawayDue(val no: Int = 0, val due: String = "", val amountMinor: Long = 0)
