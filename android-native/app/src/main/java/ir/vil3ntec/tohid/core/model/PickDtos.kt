package ir.vil3ntec.tohid.core.model

import kotlinx.serialization.Serializable

/*
 *  شکلِ پاسخ‌های جمع‌آوری و بسته‌بندی — `/api/shop/orders/:id/pick…` (server/src/routes/order-picking.js و
 *  lib/order-picking.js، PCK). هر میدان پیش‌فرض دارد تا میدانِ تازهٔ سرور یا میدانِ نبوده هیچ نسخهٔ
 *  نصب‌شده‌ای را نشکند (`ApiJson`: ignoreUnknownKeys، explicitNulls=false).
 */

@Serializable
data class PickLocDto(val aisle: String = "", val shelf: String = "", val level: String = "")

@Serializable
data class PickLineDto(
  val lineNo: Int = 0,
  val productId: String = "",
  val name: String = "",
  val unit: String = "",
  /** `pending` · `picked` · `short` · `not_found` · `substituted` · `removed` */
  val status: String = "",
  val orderedQtyMilli: Long = 0,
  val pickedQtyMilli: Long = 0,
  val unitPriceMinor: Long = 0,
  val originalTotalMinor: Long = 0,
  val weighed: Boolean = false,
  val location: PickLocDto = PickLocDto(),
  val scans: Int = 0,
  val wrongScans: Int = 0,
)

@Serializable
data class PickProgressDto(val total: Int = 0, val pending: Int = 0, val done: Int = 0)

@Serializable
data class PickTotalsDto(
  val originalTotalMinor: Long = 0,
  val subtotalMinor: Long = 0,
  val discountMinor: Long = 0,
  val deliveryFeeMinor: Long = 0,
  val totalMinor: Long = 0,
  val deductedMinor: Long = 0,
)

/** برچسبِ کیسه (PCK-09): متنِ QRِ `VLNB-<شماره>-<کیسه>-<همه>` */
@Serializable
data class PickLabelDto(val bag: Int = 0, val of: Int = 0, val qr: String = "")

/** برگهٔ جمع‌آوری؛ قلم‌ها به ترتیبِ قفسه (PCK-01) */
@Serializable
data class PickSheetDto(
  val orderId: String = "",
  val orderNo: Long = 0,
  val orderStatus: String = "",
  /** `picking` · `packed` · `cancelled` */
  val status: String = "",
  val pickerId: String = "",
  val bags: Int = 0,
  val startedAt: Long = 0,
  val packedAt: Long? = null,
  val version: Long = 0,
  val toleranceBp: Int = 1000,
  val lines: List<PickLineDto> = emptyList(),
  val progress: PickProgressDto = PickProgressDto(),
  val totals: PickTotalsDto = PickTotalsDto(),
  val labels: List<PickLabelDto> = emptyList(),
)

/** نتیجهٔ یک رویدادِ جمع‌آور */
@Serializable
data class PickResultDto(
  val eventId: String = "",
  /** `accepted` · `rejected` */
  val status: String = "",
  val code: String = "",
  val message: String = "",
  val replayed: Boolean = false,
  /** فقط برای `over_tolerance`: بیشترین وزنِ پذیرفتنی (هزارم) */
  val maxMilli: Long? = null,
  val line: PickLineDto? = null,
)

@Serializable
data class PickEventsDto(val results: List<PickResultDto> = emptyList(), val pick: PickSheetDto? = null)
