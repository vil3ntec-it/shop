package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.courier.CourierEvent
import ir.vil3ntec.tohid.core.courier.EventResult
import ir.vil3ntec.tohid.core.courier.MissionCard
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
      area = addr?.let { str(it, "area") }.orEmpty(),
      gateColor = addr?.let { str(it, "gateColor") }.orEmpty(),
      addressCorrectedAt = addr?.let { long(it, "correctedAt") },
    )
  }

  private fun prim(o: JsonObject, key: String): JsonPrimitive? = (o[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
  fun str(o: JsonObject, key: String): String = prim(o, key)?.takeIf { it.isString }?.content.orEmpty()
  fun long(o: JsonObject, key: String): Long? = prim(o, key)?.longOrNull
  fun double(o: JsonObject, key: String): Double? = prim(o, key)?.doubleOrNull
  fun bool(o: JsonObject, key: String): Boolean = prim(o, key)?.booleanOrNull == true
}
