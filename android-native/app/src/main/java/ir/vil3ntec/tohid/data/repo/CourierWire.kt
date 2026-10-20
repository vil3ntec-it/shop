package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.courier.CourierEvent
import ir.vil3ntec.tohid.core.courier.CourierSettlement
import ir.vil3ntec.tohid.core.courier.CourierSummary
import ir.vil3ntec.tohid.core.courier.EventResult
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.MissionStep
import ir.vil3ntec.tohid.core.courier.PeriodStats
import ir.vil3ntec.tohid.core.courier.UnsettledDelivery
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  شکلِ سیمِ درِ پیک — بدنهٔ `POST /api/courier/events` و `courierView`ِ سرور
 *  (`server/src/lib/delivery.js`). جدا از مخزن تا بی شبکه آزموده شود
 *  (`CourierWireTest`).
 */
object CourierWire {

  /** همان کلیدهای بدنهٔ سرور — کلیدِ خالی فرستاده نمی‌شود */
  fun eventJson(e: CourierEvent): JsonObject = buildJsonObject {
    put("eventId", JsonPrimitive(e.eventId))
    put("assignmentId", JsonPrimitive(e.assignmentId))
    put("type", JsonPrimitive(e.type))
    put("deviceTime", JsonPrimitive(e.deviceTime))
    put("deviceSeq", JsonPrimitive(e.deviceSeq))
    if (e.from.isNotEmpty()) put("from", JsonPrimitive(e.from))
    if (e.reason.isNotEmpty()) put("reason", JsonPrimitive(e.reason))
    if (e.note.isNotEmpty()) put("note", JsonPrimitive(e.note))
    e.cashCollectedMinor?.let { put("cashCollectedMinor", JsonPrimitive(it)) }
    if (e.cashReason.isNotEmpty()) put("cashReason", JsonPrimitive(e.cashReason))
    if (e.code.isNotEmpty()) put("code", JsonPrimitive(e.code))
  }

  fun result(o: JsonObject): EventResult =
    EventResult(str(o, "eventId"), str(o, "status"), str(o, "code"), str(o, "message"))

  /** `courierView`ِ سرور ⇒ کارت؛ بی شناسه ⇒ null */
  fun card(o: JsonObject): MissionCard? {
    val id = str(o, "id").ifBlank { return null }
    val addr = o["address"] as? JsonObject
    val cust = o["customer"] as? JsonObject
    val pref = o["delivery"] as? JsonObject
    return MissionCard(
      id = id,
      orderNo = long(o, "orderNo") ?: 0L,
      status = str(o, "status"),
      landmark = addr?.let { str(it, "landmark") }.orEmpty(),
      addressText = addr?.let { str(it, "text") }.orEmpty(),
      lat = addr?.let { double(it, "lat") },
      lng = addr?.let { double(it, "lng") },
      customerName = cust?.let { str(it, "name") }.orEmpty(),
      phoneMasked = cust?.let { str(it, "phoneMasked") }.orEmpty(),
      callBeforeArrival = pref?.let { bool(it, "callBeforeArrival") } == true,
      leaveWith = pref?.let { str(it, "leaveWith") }.orEmpty(),
      itemsCount = (long(o, "itemsCount") ?: 0L).toInt(),
      paymentMethod = str(o, "paymentMethod"),
      expectedCollectMinor = long(o, "expectedCollectMinor") ?: 0L,
      cashCollectedMinor = long(o, "cashCollectedMinor"),
      fareMinor = long(o, "fareMinor") ?: 0L,
      requiresCode = bool(o, "requiresCode"),
      photoAllowed = bool(o, "photoAllowed"),
      recallRequested = o["recall"].let { it != null && it !is JsonNull },
      updatedAt = long(o, "updatedAt") ?: 0L,
      timeline = (o["timeline"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::step) },
    )
  }

  /** یک رویدادِ `timeline`؛ بی زمان ⇒ null */
  fun step(o: JsonObject): MissionStep? {
    val at = long(o, "at") ?: return null
    return MissionStep(kind = str(o, "kind"), to = str(o, "to"), at = at, reason = str(o, "reason"), by = str(o, "by"))
  }

  /** `GET /api/courier/summary` ⇒ «نقدِ من» و آمار؛ کلیدِ ناپیدا ⇒ صفر/خالی */
  fun summary(o: JsonObject): CourierSummary {
    val shop = o["shop"] as? JsonObject ?: JsonObject(emptyMap())
    val cash = o["cash"] as? JsonObject ?: JsonObject(emptyMap())
    val stats = o["stats"] as? JsonObject ?: JsonObject(emptyMap())
    fun period(x: JsonObject?): PeriodStats = if (x == null) PeriodStats() else PeriodStats(
      from = long(x, "from") ?: 0L,
      delivered = (long(x, "delivered") ?: 0L).toInt(),
      failed = (long(x, "failed") ?: 0L).toInt(),
      collectedCashMinor = long(x, "collectedCashMinor") ?: 0L,
      fareMinor = long(x, "fareMinor") ?: 0L,
    )
    return CourierSummary(
      shopName = str(shop, "name"),
      shopPhone = str(shop, "phone"),
      cashInHandMinor = long(cash, "inHandMinor") ?: 0L,
      liveExpectedMinor = long(cash, "liveExpectedMinor") ?: 0L,
      cashCapMinor = long(cash, "capMinor") ?: 0L,
      headroomMinor = long(cash, "headroomMinor") ?: 0L,
      unsettled = (o["unsettled"] as? JsonArray).orEmpty().mapNotNull { el ->
        (el as? JsonObject)?.let {
          UnsettledDelivery(
            orderNo = long(it, "orderNo") ?: 0L, deliveredAt = long(it, "deliveredAt") ?: 0L,
            expectedCollectMinor = long(it, "expectedCollectMinor") ?: 0L,
            cashCollectedMinor = long(it, "cashCollectedMinor") ?: 0L, fareMinor = long(it, "fareMinor") ?: 0L,
          )
        }
      },
      settlements = (o["settlements"] as? JsonArray).orEmpty().mapNotNull { el ->
        (el as? JsonObject)?.let {
          CourierSettlement(
            settleNo = long(it, "settleNo") ?: 0L, at = long(it, "at") ?: 0L, status = str(it, "status"),
            assignmentsCount = (long(it, "assignmentsCount") ?: 0L).toInt(),
            collectedCashMinor = long(it, "collectedCashMinor") ?: 0L, fareTotalMinor = long(it, "fareTotalMinor") ?: 0L,
            handoverDueMinor = long(it, "handoverDueMinor") ?: 0L, receivedMinor = long(it, "receivedMinor") ?: 0L,
            shortageMinor = long(it, "shortageMinor") ?: 0L, excessMinor = long(it, "excessMinor") ?: 0L,
            reason = str(it, "reason"),
          )
        }
      },
      today = period(stats["today"] as? JsonObject),
      week = period(stats["week"] as? JsonObject),
      serverTime = long(o, "serverTime") ?: 0L,
    )
  }

  private fun prim(o: JsonObject, key: String): JsonPrimitive? = (o[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
  fun str(o: JsonObject, key: String): String = prim(o, key)?.takeIf { it.isString }?.content.orEmpty()
  fun long(o: JsonObject, key: String): Long? = prim(o, key)?.longOrNull
  fun double(o: JsonObject, key: String): Double? = prim(o, key)?.doubleOrNull
  fun bool(o: JsonObject, key: String): Boolean = prim(o, key)?.booleanOrNull == true
}
