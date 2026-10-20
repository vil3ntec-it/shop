package ir.vil3ntec.tohid.core.delivery

/*
 *  مدلِ میزِ تحویلِ دکان (C01) — همان شکلِ پاسخ‌های `/api/shop/delivery`
 *  (`server/src/lib/delivery.js`: staffView، courierRowView، settleRowView،
 *  settlementView، externalCompanies، externalStatement).
 *
 *  خالص است: نه شبکه، نه UI. خواندنِ JSON در `data/repo/DeliveryWire.kt`ِ
 *  برنامه است تا این‌جا بی وابستگی بماند. همهٔ پول‌ها عددِ صحیح به واحدِ
 *  کوچک (یک‌صدمِ افغانی) است، مثلِ سرور.
 */

/** دسترسی‌های همین کاربر — سرور از نقش حساب می‌کند (`/delivery/config`) */
data class DeskPerms(
  val view: Boolean = false,
  val assign: Boolean = false,
  val handover: Boolean = false,
  val couriers: Boolean = false,
  val settle: Boolean = false,
  val photo: Boolean = false,
)

/** `GET /shop/delivery/config` — بخش فقط وقتی `readable` است دیده می‌شود */
data class DeskConfig(
  val readable: Boolean = false,
  val entitled: Boolean = false,
  val enabled: Boolean = false,
  val shopCourier: Boolean = true,
  val external: Boolean = true,
  val pickup: Boolean = true,
  val reasons: Map<String, List<String>> = emptyMap(),
  val defaultCashCapMinor: Long = 0,
  val perms: DeskPerms = DeskPerms(),
)

/** پیکِ دکان با نقدِ در دستش (DLV-11) */
data class CourierRow(
  val id: String,
  val userId: String = "",
  val name: String = "",
  val phone: String = "",
  val active: Boolean = true,
  /** سقفِ نقد */
  val cashCapMinor: Long = 0,
  /** گرفته و هنوز تسویه‌نشده */
  val cashHeldMinor: Long = 0,
  /** مأموریت‌های زنده */
  val liveCount: Int = 0,
  /** نقدی که از مأموریت‌های زنده باید بگیرد */
  val liveExpectedMinor: Long = 0,
)

/** یک مأموریتِ تحویل از دیدِ دکان (`staffView` + نامِ مشتری و وضعیتِ سفارش) */
data class Mission(
  val id: String,
  val orderId: String = "",
  val orderNo: Long = 0,
  val attemptNo: Int = 1,
  /** shop_courier | external | pickup */
  val mode: String = "",
  val courierId: String = "",
  val externalName: String = "",
  val externalRef: String = "",
  val status: String = "",
  val statusReason: String = "",
  val live: Boolean = false,
  val expectedCollectMinor: Long = 0,
  val cashCollectedMinor: Long? = null,
  /** کرایه یا دستمزدِ همین تحویل (DLV-13) */
  val fareMinor: Long = 0,
  val cashReview: Boolean = false,
  val hasCode: Boolean = false,
  val codeUsed: Boolean = false,
  val codeLocked: Boolean = false,
  val recallRequested: Boolean = false,
  val recallOutcome: String = "",
  val deliveredAt: Long? = null,
  val settlementId: String = "",
  val version: Long = 0,
  val createdAt: Long = 0,
  val updatedAt: Long = 0,
  val orderStatus: String = "",
  val customerName: String = "",
)

/** یک ردیفِ پیش‌نمایشِ تسویه */
data class SettleRow(
  val assignmentId: String,
  val orderId: String = "",
  val orderNo: Long = 0,
  val deliveredAt: Long? = null,
  val expectedCollectMinor: Long = 0,
  val cashCollectedMinor: Long = 0,
  val collectionDiffMinor: Long = 0,
  val fareMinor: Long = 0,
  val cashReview: Boolean = false,
)

data class SettlementPreview(
  val courier: CourierRow,
  val rows: List<SettleRow>,
  val serverTime: Long = 0,
)

/** سندِ بستهٔ تسویه (`settlementView`) */
data class Settlement(
  val id: String,
  val settleNo: Long = 0,
  val courierId: String = "",
  val assignmentsCount: Int = 0,
  val periodFrom: Long? = null,
  val periodTo: Long? = null,
  val expectedCashMinor: Long = 0,
  val collectedCashMinor: Long = 0,
  val collectionDiffMinor: Long = 0,
  val fareTotalMinor: Long = 0,
  val farePaidFromCash: Boolean = false,
  val handoverDueMinor: Long = 0,
  val handedOverMinor: Long = 0,
  val receivedMinor: Long = 0,
  val shortageMinor: Long = 0,
  val excessMinor: Long = 0,
  val reason: String = "",
  val note: String = "",
  val confirmedBy: String = "",
  val createdAt: Long = 0,
)

/** جمعِ همهٔ تسویه‌های یک پیک (نه فقط صفحهٔ آمده) */
data class SettlementSums(
  val settlements: Int = 0,
  val assignments: Int = 0,
  val expectedCashMinor: Long = 0,
  val collectedCashMinor: Long = 0,
  val fareTotalMinor: Long = 0,
  val handoverDueMinor: Long = 0,
  val receivedMinor: Long = 0,
  val shortageMinor: Long = 0,
  val excessMinor: Long = 0,
)

data class SettlementPage(val items: List<Settlement>, val sums: SettlementSums)

/** شرکتِ پیکِ بیرونی — از خودِ سپردن‌ها (DLV-19) */
data class ExternalCompany(
  val name: String,
  val assignments: Int = 0,
  val live: Int = 0,
  val delivered: Int = 0,
  /** کرایهٔ آخرین سپردن — پیش‌فرضِ «کرایهٔ هر سفارش» */
  val lastFareMinor: Long = 0,
  val lastUsedAt: Long = 0,
)

data class StatementRow(
  val assignmentId: String,
  val orderId: String = "",
  val orderNo: Long = 0,
  val externalRef: String = "",
  val status: String = "",
  val fareMinor: Long = 0,
  val expectedCollectMinor: Long = 0,
  val cashCollectedMinor: Long? = null,
  val deliveredAt: Long? = null,
  val createdAt: Long = 0,
)

data class Statement(
  val name: String,
  val rows: List<StatementRow>,
  /** بیش از سقفِ یک بار (۱۰۰۰ ردیف) بود ⇒ بازه را کوچک‌تر کنید */
  val truncated: Boolean = false,
  /** جمعِ سرور: کرایهٔ تحویل‌شده‌ها (دکان به شرکت بدهکار است) */
  val deliveredFareMinor: Long = 0,
  /** جمعِ سرور: نقدِ COD که شرکت از مشتری گرفت (شرکت به دکان بدهکار است) */
  val deliveredCashMinor: Long = 0,
)
