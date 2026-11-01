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
  /** PCK-05: پیشنهادهای جایگزینِ این برگه (`forSheet` ِ سرور) */
  val substitutions: List<PickSubstitutionDto> = emptyList(),
)

/** PCK-05 — یک پیشنهادِ جایگزین (`subView` ِ سرور)؛ مبلغ را فقط سرور می‌سازد */
@Serializable
data class PickSubstitutionDto(
  val id: String = "",
  val lineNo: Int = 0,
  val productId: String = "",
  val name: String = "",
  val unit: String = "",
  val qtyMilli: Long = 0,
  val shelfUnitPriceMinor: Long = 0,
  val chargedUnitPriceMinor: Long = 0,
  val totalMinor: Long = 0,
  val preference: String = "",
  /** `pending` · `accepted` · `rejected` · `withdrawn` */
  val status: String = "",
  val deadlineAt: Long = 0,
  val expired: Boolean = false,
  val willApply: String? = null,
)

/** PCK-05/06 — دو جایگزینِ امنِ پیشنهادیِ سرور برای یک قلم (`GET …/pick/substitutes?lineNo=`) */
@Serializable
data class PickSuggestionDto(
  val productId: String = "",
  val name: String = "",
  val unit: String = "",
  val brand: String = "",
  val halal: String = "",
  val shelfUnitPriceMinor: Long = 0,
  val chargedUnitPriceMinor: Long = 0,
  val totalMinor: Long = 0,
  val qtyMilli: Long = 0,
  val needsScan: Boolean = false,
)

@Serializable
data class PickSuggestionsDto(
  val lineNo: Int = 0,
  val preference: String = "",
  val proposable: Boolean = true,
  /** `category_unknown` ⇒ دستهٔ کالای اصل معلوم نیست و جایگزینِ امن ساخته نمی‌شود */
  val reason: String = "",
  val suggestions: List<PickSuggestionDto> = emptyList(),
)

@Serializable
data class PickCustomerLinkDto(val link: String = "", val whatsappUrl: String = "")

@Serializable
data class PickProposedDto(
  val substitution: PickSubstitutionDto = PickSubstitutionDto(),
  val replayed: Boolean = false,
  val customer: PickCustomerLinkDto = PickCustomerLinkDto(),
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

// ── قفسهٔ انتظار (PCK-10) و جمع‌آوریِ گروهی (PCK-12) — `lib/pick-shelf.js` ─────────────────

/** یک سفارش روی قفسهٔ انتظار */
@Serializable
data class PickShelfItemDto(
  val orderId: String = "",
  val orderNo: Long = 0,
  val slot: String = "",
  val bags: Int = 0,
  val scanned: Int = 0,
  val ready: Boolean = false,
  val readyAt: Long? = null,
  val status: String = "",
  val fulfillment: String = "",
)

@Serializable
data class PickShelfDto(val shelf: List<PickShelfItemDto> = emptyList())

/** PCK-13 — `GET /shop/orders/pick/metrics` (۷ روزِ گذشته؛ زمان میلی‌ثانیه، نرخ یک‌دهمِ درصد) */
@Serializable
data class PickMetricsDto(
  val from: Long = 0,
  val to: Long = 0,
  val orders: Int = 0,
  val lines: Int = 0,
  val found: Int = 0,
  val notFound: Int = 0,
  val short: Int = 0,
  val substituted: Int = 0,
  val avgPickMs: Long? = null,
  val medianPickMs: Long? = null,
  val foundRateBp: Long? = null,
  val substitutionRateBp: Long? = null,
  val wrongScans: Int = 0,
)

/** PCK-14 — یک وظیفهٔ «پیدا نشد» (`GET /shop/orders/pick/issues`)؛ `detail.name` نامِ کالا در همان سفارش */
@Serializable
data class PickIssueDetailDto(val name: String = "", val orderedMilli: Long = 0, val pickedMilli: Long = 0)

@Serializable
data class PickIssueDto(
  val id: String = "",
  val productId: String = "",
  val status: String = "",
  val hits: Int = 0,
  val detail: PickIssueDetailDto = PickIssueDetailDto(),
  val createdAt: Long = 0,
  val note: String = "",
)

@Serializable
data class PickIssuesDto(val issues: List<PickIssueDto> = emptyList())

/** PCK-08 — عکسِ بسته (بی بایت؛ `GET/POST /shop/orders/:id/pick/photos`) */
@Serializable
data class PickPhotoDto(val id: String = "", val size: Int = 0, val bags: Int = 0, val createdAt: Long = 0)

@Serializable
data class PickPhotosDto(val photos: List<PickPhotoDto> = emptyList())

/** نتیجهٔ اسکنِ یک کیسه روی قفسه */
@Serializable
data class PickShelfScanDto(
  val orderId: String = "",
  val orderNo: Long = 0,
  val slot: String = "",
  val bag: Int = 0,
  val bags: Int = 0,
  val scanned: Int = 0,
  val ready: Boolean = false,
  val readyNow: Boolean = false,
  val status: String = "",
)

@Serializable
data class PickBatchOrderDto(
  val orderId: String = "",
  val orderNo: Long = 0,
  /** «سبدِ» این سفارش در دسته (۱..n به ترتیبِ شمارهٔ سفارش) */
  val bin: Int = 0,
  val status: String = "",
  val progress: PickProgressDto = PickProgressDto(),
)

@Serializable
data class PickBatchLineDto(
  val orderId: String = "",
  val orderNo: Long = 0,
  val bin: Int = 0,
  val lineNo: Int = 0,
  val productId: String = "",
  val name: String = "",
  val unit: String = "",
  val status: String = "",
  val orderedQtyMilli: Long = 0,
  val pickedQtyMilli: Long = 0,
  val weighed: Boolean = false,
  val location: PickLocDto = PickLocDto(),
)

/** دسته: سفارش‌ها و فهرستِ یک‌جا به ترتیبِ قفسه */
@Serializable
data class PickBatchDto(
  val batchId: String = "",
  val orders: List<PickBatchOrderDto> = emptyList(),
  val lines: List<PickBatchLineDto> = emptyList(),
  val progress: PickProgressDto = PickProgressDto(),
)
