package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.ExternalCompany
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.delivery.Settlement
import ir.vil3ntec.tohid.core.delivery.SettlementPage
import ir.vil3ntec.tohid.core.delivery.SettlementPreview
import ir.vil3ntec.tohid.core.delivery.SettleRow
import ir.vil3ntec.tohid.core.delivery.Statement
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints.Delivery
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonObject

/**
 *  میزِ تحویلِ برنامهٔ دکان — `/api/shop/delivery/…` (C01).
 *
 *  ⛔ دکان و نقش را سرور از توکن می‌گیرد؛ هیچ شناسهٔ دکانی فرستاده نمی‌شود.
 *  ⛔ هر حساب (سقفِ نقد، کسری، کرایه) را سرور دوباره می‌کند؛ میز فقط پیش‌نمایش
 *     می‌دهد. سپردن، گذار و تسویه فقط پس از پاسخِ سرور «ثبت شد» است.
 *  ⛔ کدِ دریافتِ حضوری فقط یک بار در پاسخِ سپردن/کدِ تازه می‌آید؛ جایی
 *     نگه داشته نمی‌شود.
 */
class DeliveryRepository(private val api: ApiClient) {

  data class Assigned(val mission: Mission, val code: String?)
  data class Settled(val settlement: Settlement, val rows: List<SettleRow>, val replayed: Boolean)

  suspend fun config(): ApiResult<DeskConfig> = result { DeliveryWire.config(api.get(Delivery.CONFIG)) }

  suspend fun couriers(): ApiResult<List<CourierRow>> =
    result { DeliveryWire.rows(api.get(Delivery.COURIERS), "items", DeliveryWire::courier) }

  /** `scope`: «live» (زنده) یا «done» (پایان‌یافته؛ تازه‌ترین اول) */
  suspend fun board(scope: String): ApiResult<List<Mission>> =
    result { DeliveryWire.rows(api.get(Delivery.board(scope)), "items", DeliveryWire::mission) }

  suspend fun assign(orderId: String, body: JsonObject): ApiResult<Assigned> = result {
    val out = api.post(Delivery.assign(orderId), body)
    val m = (out["assignment"] as? JsonObject)?.let(DeliveryWire::mission) ?: throw ApiFailure.InvalidResponse()
    Assigned(m, CourierWire.str(out, "code").ifBlank { null })
  }

  /** کدِ تازه — کدِ قبلی باطل و شمارِ تلاش صفر می‌شود */
  suspend fun newCode(assignmentId: String): ApiResult<String> = result {
    CourierWire.str(api.post(Delivery.code(assignmentId)), "code").ifBlank { throw ApiFailure.InvalidResponse() }
  }

  /** گذارِ کارمند (`DeliveryWire.statusJson`) — ردِ کسب‌وکاری = `ApiFailure` با پیامِ سرور */
  suspend fun status(assignmentId: String, body: JsonObject): ApiResult<Mission> = result {
    val out = api.post(Delivery.status(assignmentId), body)
    (out["assignment"] as? JsonObject)?.let(DeliveryWire::mission) ?: throw ApiFailure.InvalidResponse()
  }

  suspend fun recall(assignmentId: String, reason: String, note: String): ApiResult<Mission> = result {
    val out = api.post(Delivery.recall(assignmentId), DeliveryWire.recallJson(reason, note))
    (out["assignment"] as? JsonObject)?.let(DeliveryWire::mission) ?: throw ApiFailure.InvalidResponse()
  }

  suspend fun updateCourier(courierId: String, body: JsonObject): ApiResult<CourierRow> = result {
    val out = api.post(Delivery.courier(courierId), body)
    (out["courier"] as? JsonObject)?.let(DeliveryWire::courier) ?: throw ApiFailure.InvalidResponse()
  }

  suspend fun settlementPreview(courierId: String): ApiResult<SettlementPreview> = result {
    DeliveryWire.preview(api.get(Delivery.settlementPreview(courierId))) ?: throw ApiFailure.InvalidResponse()
  }

  suspend fun settle(courierId: String, body: JsonObject): ApiResult<Settled> = result {
    val out = api.post(Delivery.settle(courierId), body)
    val s = (out["settlement"] as? JsonObject)?.let(DeliveryWire::settlement) ?: throw ApiFailure.InvalidResponse()
    Settled(s, DeliveryWire.rows(out, "rows", DeliveryWire::settleRow), CourierWire.bool(out, "replayed"))
  }

  suspend fun settlements(courierId: String, limit: Int = 20): ApiResult<SettlementPage> =
    result { DeliveryWire.settlementPage(api.get(Delivery.settlements(courierId, limit))) }

  suspend fun companies(): ApiResult<List<ExternalCompany>> =
    result { DeliveryWire.rows(api.get(Delivery.EXTERNAL), "items", DeliveryWire::company) }

  suspend fun statement(name: String, from: Long?, to: Long?): ApiResult<Statement> =
    result { DeliveryWire.statement(api.get(Delivery.statement(name, from, to))) }

  companion object {
    /** کلیدِ یکتاییِ تسویه — یک بار برای هر فرم */
    fun newKey(): String = "dsk-" + java.util.UUID.randomUUID().toString()
  }
}
