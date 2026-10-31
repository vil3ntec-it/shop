package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.DeskPerms
import ir.vil3ntec.tohid.core.delivery.ExternalCompany
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.delivery.SettleRow
import ir.vil3ntec.tohid.core.delivery.Settlement
import ir.vil3ntec.tohid.core.delivery.SettlementPage
import ir.vil3ntec.tohid.core.delivery.SettlementPreview
import ir.vil3ntec.tohid.core.delivery.SettlementSums
import ir.vil3ntec.tohid.core.delivery.Statement
import ir.vil3ntec.tohid.core.delivery.StatementRow
import ir.vil3ntec.tohid.data.repo.CourierWire.bool
import ir.vil3ntec.tohid.data.repo.CourierWire.long
import ir.vil3ntec.tohid.data.repo.CourierWire.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 *  شکلِ سیمِ میزِ تحویلِ دکان — `/api/shop/delivery/…`
 *  (`server/src/routes/delivery.js` و `lib/delivery.js`). جدا از مخزن تا بی
 *  شبکه آزموده شود (`DeliveryWireTest`). میدانِ نبوده ⇒ پیش‌فرض، نه خطا:
 *  سرورِ قدیمی‌تر هم برنامه را نمی‌شکند.
 */
object DeliveryWire {

  /* ------------------------------ خواندن ------------------------------ */

  fun config(o: JsonObject): DeskConfig {
    val modes = o["modes"] as? JsonObject ?: JsonObject(emptyMap())
    val perms = o["permissions"] as? JsonObject ?: JsonObject(emptyMap())
    val reasons = (o["reasons"] as? JsonObject)?.mapValues { (_, v) ->
      (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()
    }.orEmpty()
    return DeskConfig(
      readable = bool(o, "readable"),
      entitled = bool(o, "entitled"),
      enabled = bool(o, "enabled"),
      shopCourier = modes["shop_courier"].let { it == null || (it as? JsonPrimitive)?.content == "true" },
      external = modes["external"].let { it == null || (it as? JsonPrimitive)?.content == "true" },
      pickup = modes["pickup"].let { it == null || (it as? JsonPrimitive)?.content == "true" },
      reasons = reasons,
      defaultCashCapMinor = long(o, "defaultCashCapMinor") ?: 0L,
      perms = DeskPerms(
        view = bool(perms, "view"),
        assign = bool(perms, "assign"),
        handover = bool(perms, "handover"),
        couriers = bool(perms, "couriers"),
        settle = bool(perms, "settle"),
        photo = bool(perms, "photo"),
      ),
      picking = bool(o, "picking"),
    )
  }

  fun courier(o: JsonObject): CourierRow? {
    val id = str(o, "id").ifBlank { return null }
    return CourierRow(
      id = id,
      userId = str(o, "userId"),
      name = str(o, "name"),
      phone = str(o, "phone"),
      active = bool(o, "active"),
      cashCapMinor = long(o, "cashCapMinor") ?: 0L,
      cashHeldMinor = long(o, "cashHeldMinor") ?: 0L,
      liveCount = (long(o, "liveCount") ?: 0L).toInt(),
      liveExpectedMinor = long(o, "liveExpectedMinor") ?: 0L,
    )
  }

  fun mission(o: JsonObject): Mission? {
    val id = str(o, "id").ifBlank { return null }
    val recall = o["recall"] as? JsonObject
    return Mission(
      id = id,
      orderId = str(o, "orderId"),
      orderNo = long(o, "orderNo") ?: 0L,
      attemptNo = (long(o, "attemptNo") ?: 1L).toInt(),
      mode = str(o, "mode"),
      courierId = str(o, "courierId"),
      externalName = str(o, "externalName"),
      externalRef = str(o, "externalRef"),
      status = str(o, "status"),
      statusReason = str(o, "statusReason"),
      live = bool(o, "live"),
      expectedCollectMinor = long(o, "expectedCollectMinor") ?: 0L,
      cashCollectedMinor = long(o, "cashCollectedMinor"),
      fareMinor = long(o, "fareMinor") ?: 0L,
      cashReview = bool(o, "cashReview"),
      hasCode = bool(o, "hasCode"),
      codeUsed = bool(o, "codeUsed"),
      codeLocked = bool(o, "codeLocked"),
      recallRequested = recall != null,
      recallOutcome = recall?.let { str(it, "outcome") }.orEmpty(),
      deliveredAt = long(o, "deliveredAt"),
      settlementId = str(o, "settlementId"),
      version = long(o, "version") ?: 0L,
      createdAt = long(o, "createdAt") ?: 0L,
      updatedAt = long(o, "updatedAt") ?: 0L,
      orderStatus = str(o, "orderStatus"),
      customerName = str(o, "customerName"),
    )
  }

  fun settleRow(o: JsonObject): SettleRow? {
    val id = str(o, "assignmentId").ifBlank { return null }
    return SettleRow(
      assignmentId = id,
      orderId = str(o, "orderId"),
      orderNo = long(o, "orderNo") ?: 0L,
      deliveredAt = long(o, "deliveredAt"),
      expectedCollectMinor = long(o, "expectedCollectMinor") ?: 0L,
      cashCollectedMinor = long(o, "cashCollectedMinor") ?: 0L,
      collectionDiffMinor = long(o, "collectionDiffMinor") ?: 0L,
      fareMinor = long(o, "fareMinor") ?: 0L,
      cashReview = bool(o, "cashReview"),
    )
  }

  fun preview(o: JsonObject): SettlementPreview? {
    val c = (o["courier"] as? JsonObject)?.let(DeliveryWire::courier) ?: return null
    return SettlementPreview(c, rows(o, "rows", DeliveryWire::settleRow), long(o, "serverTime") ?: 0L)
  }

  fun settlement(o: JsonObject): Settlement? {
    val id = str(o, "id").ifBlank { return null }
    return Settlement(
      id = id,
      settleNo = long(o, "settleNo") ?: 0L,
      courierId = str(o, "courierId"),
      assignmentsCount = (long(o, "assignmentsCount") ?: 0L).toInt(),
      periodFrom = long(o, "periodFrom"),
      periodTo = long(o, "periodTo"),
      expectedCashMinor = long(o, "expectedCashMinor") ?: 0L,
      collectedCashMinor = long(o, "collectedCashMinor") ?: 0L,
      collectionDiffMinor = long(o, "collectionDiffMinor") ?: 0L,
      fareTotalMinor = long(o, "fareTotalMinor") ?: 0L,
      farePaidFromCash = bool(o, "farePaidFromCash"),
      handoverDueMinor = long(o, "handoverDueMinor") ?: 0L,
      handedOverMinor = long(o, "handedOverMinor") ?: 0L,
      receivedMinor = long(o, "receivedMinor") ?: 0L,
      shortageMinor = long(o, "shortageMinor") ?: 0L,
      excessMinor = long(o, "excessMinor") ?: 0L,
      reason = str(o, "reason"),
      note = str(o, "note"),
      confirmedBy = str(o, "confirmedBy"),
      createdAt = long(o, "createdAt") ?: 0L,
    )
  }

  fun settlementPage(o: JsonObject): SettlementPage {
    val t = o["totals"] as? JsonObject ?: JsonObject(emptyMap())
    return SettlementPage(
      items = rows(o, "items", DeliveryWire::settlement),
      sums = SettlementSums(
        settlements = (long(t, "settlements") ?: 0L).toInt(),
        assignments = (long(t, "assignments") ?: 0L).toInt(),
        expectedCashMinor = long(t, "expectedCashMinor") ?: 0L,
        collectedCashMinor = long(t, "collectedCashMinor") ?: 0L,
        fareTotalMinor = long(t, "fareTotalMinor") ?: 0L,
        handoverDueMinor = long(t, "handoverDueMinor") ?: 0L,
        receivedMinor = long(t, "receivedMinor") ?: 0L,
        shortageMinor = long(t, "shortageMinor") ?: 0L,
        excessMinor = long(t, "excessMinor") ?: 0L,
      ),
    )
  }

  fun company(o: JsonObject): ExternalCompany? {
    val name = str(o, "name").ifBlank { return null }
    return ExternalCompany(
      name = name,
      assignments = (long(o, "assignments") ?: 0L).toInt(),
      live = (long(o, "live") ?: 0L).toInt(),
      delivered = (long(o, "delivered") ?: 0L).toInt(),
      lastFareMinor = long(o, "lastFareMinor") ?: 0L,
      lastUsedAt = long(o, "lastUsedAt") ?: 0L,
    )
  }

  fun statement(o: JsonObject): Statement = Statement(
    name = str(o, "name"),
    rows = rows(o, "rows") { r ->
      val id = str(r, "assignmentId").ifBlank { return@rows null }
      StatementRow(
        assignmentId = id,
        orderId = str(r, "orderId"),
        orderNo = long(r, "orderNo") ?: 0L,
        externalRef = str(r, "externalRef"),
        status = str(r, "status"),
        fareMinor = long(r, "fareMinor") ?: 0L,
        expectedCollectMinor = long(r, "expectedCollectMinor") ?: 0L,
        cashCollectedMinor = long(r, "cashCollectedMinor"),
        deliveredAt = long(r, "deliveredAt"),
        createdAt = long(r, "createdAt") ?: 0L,
      )
    },
    truncated = bool(o, "truncated"),
    deliveredFareMinor = (o["totals"] as? JsonObject)?.let { long(it, "deliveredFareMinor") } ?: 0L,
    deliveredCashMinor = (o["totals"] as? JsonObject)?.let { long(it, "deliveredCashMinor") } ?: 0L,
  )

  fun <T> rows(o: JsonObject, key: String, map: (JsonObject) -> T?): List<T> =
    (o[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(map) }

  /* ------------------------------ فرستادن ------------------------------ */

  /**
   *  بدنهٔ سپردن (`assign`ِ سرور). کلیدِ بی‌ربط به روش فرستاده نمی‌شود تا
   *  اثرِ انگشتِ درخواست (`request_hash`) فقط از همان روش ساخته شود.
   */
  fun assignJson(
    mode: String,
    courierId: String = "",
    externalName: String = "",
    externalRef: String = "",
    fareMinor: Long = 0,
    overrideCashCap: Boolean = false,
  ): JsonObject = buildJsonObject {
    put("mode", JsonPrimitive(mode))
    if (mode != "pickup" && fareMinor > 0) put("fareMinor", JsonPrimitive(fareMinor))
    if (mode == "shop_courier") {
      put("courierId", JsonPrimitive(courierId))
      if (overrideCashCap) put("overrideCashCap", JsonPrimitive(true))
    }
    if (mode == "external") {
      put("externalName", JsonPrimitive(externalName.trim()))
      if (externalRef.isNotBlank()) put("externalRef", JsonPrimitive(externalRef.trim()))
    }
  }

  /** بدنهٔ گذارِ کارمند (`staffEvent`ِ سرور) — `from` برای CAS (§۲۸.۵) */
  fun statusJson(
    to: String,
    from: String = "",
    reason: String = "",
    note: String = "",
    code: String = "",
    cashCollectedMinor: Long? = null,
    cashReason: String = "",
  ): JsonObject = buildJsonObject {
    put("to", JsonPrimitive(to))
    if (from.isNotEmpty()) put("from", JsonPrimitive(from))
    if (reason.isNotEmpty()) put("reason", JsonPrimitive(reason))
    if (note.isNotBlank()) put("note", JsonPrimitive(note.trim()))
    if (code.isNotEmpty()) put("code", JsonPrimitive(code))
    cashCollectedMinor?.let { put("cashCollectedMinor", JsonPrimitive(it)) }
    if (cashReason.isNotEmpty()) put("cashReason", JsonPrimitive(cashReason))
  }

  /** بدنهٔ تسویه — `clientKey` یکتاییِ سند است: تکرار ⇒ همان سند */
  fun settleJson(
    clientKey: String,
    assignmentIds: List<String>,
    handedOverMinor: Long,
    receivedMinor: Long,
    farePaidFromCash: Boolean,
    reason: String,
    note: String,
  ): JsonObject = buildJsonObject {
    put("clientKey", JsonPrimitive(clientKey))
    put("assignmentIds", buildJsonArray { assignmentIds.forEach { add(JsonPrimitive(it)) } })
    put("handedOverMinor", JsonPrimitive(handedOverMinor))
    put("receivedMinor", JsonPrimitive(receivedMinor))
    put("farePaidFromCash", JsonPrimitive(farePaidFromCash))
    if (reason.isNotBlank()) put("reason", JsonPrimitive(reason.trim()))
    if (note.isNotBlank()) put("note", JsonPrimitive(note.trim()))
  }

  /** بدنهٔ فراخوانی (لغو پس از حرکت) — فقط دلیل و توضیح */
  fun recallJson(reason: String, note: String): JsonObject = buildJsonObject {
    put("reason", JsonPrimitive(reason))
    if (note.isNotBlank()) put("note", JsonPrimitive(note.trim()))
  }

  /**
   *  بدنهٔ «اصلاحِ نشانی» (DLV-09): `{address:{landmark?, area?, gateColor?, text?}, keepPin, expectedVersion}`.
   *  کلیدِ خالی فرستاده نمی‌شود. میز نقشه ندارد: `keepPin = true` ⇒ پینِ مشتری می‌ماند؛
   *  `false` ⇒ پینِ قبلی پاک می‌شود (پین غلط بود و پیک فقط با نشانه می‌رود).
   */
  fun addressJson(
    landmark: String,
    area: String,
    gateColor: String,
    text: String,
    keepPin: Boolean,
    expectedVersion: Long,
  ): JsonObject =
    buildJsonObject {
      put("address", buildJsonObject {
        landmark.trim().takeIf { it.isNotEmpty() }?.let { put("landmark", JsonPrimitive(it)) }
        area.trim().takeIf { it.isNotEmpty() }?.let { put("area", JsonPrimitive(it)) }
        gateColor.trim().takeIf { it.isNotEmpty() }?.let { put("gateColor", JsonPrimitive(it)) }
        text.trim().takeIf { it.isNotEmpty() }?.let { put("text", JsonPrimitive(it)) }
      })
      put("keepPin", JsonPrimitive(keepPin))
      put("expectedVersion", JsonPrimitive(expectedVersion))
    }

  fun courierUpdateJson(cashCapMinor: Long? = null, active: Boolean? = null): JsonObject = buildJsonObject {
    cashCapMinor?.let { put("cashCapMinor", JsonPrimitive(it)) }
    active?.let { put("active", JsonPrimitive(it)) }
  }
}
