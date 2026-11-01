package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.PickBatchDto
import ir.vil3ntec.tohid.core.model.PickEventsDto
import ir.vil3ntec.tohid.core.model.PickPhotoDto
import ir.vil3ntec.tohid.core.model.PickPhotosDto
import ir.vil3ntec.tohid.core.model.PickProposedDto
import ir.vil3ntec.tohid.core.model.PickSuggestionsDto
import ir.vil3ntec.tohid.core.model.PickShelfDto
import ir.vil3ntec.tohid.core.model.PickShelfScanDto
import ir.vil3ntec.tohid.core.model.PickSheetDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  جمع‌آوری و بسته‌بندی (PCK) — `/shop/orders/:id/pick…` پشتِ پرچمِ `shop.orders.picking`.
 *
 *  ⛔ دکان و نقش را سرور از توکن می‌گیرد. هر کارِ جمع‌آور یک `eventId` دارد؛ همان رویداد پس از قطعِ شبکه
 *  با همان شناسه دوباره می‌رود و سرور یک بار حساب می‌کند. بسته‌بندی با `Idempotency-Key`.
 *  ⛔ مبلغ را فقط سرور می‌سازد؛ برنامه فقط پیش‌نمایش نشان می‌دهد (`core/pick/PickCore`).
 */
class PickingRepository(private val api: ApiClient) {

  /** برگه؛ شروع‌نشده ⇒ null */
  suspend fun sheet(orderId: String): ApiResult<PickSheetDto?> =
    result { ApiJson.decodeAt<PickSheetDto>(api.get("$ROOT/${enc(orderId)}/pick"), "pick") }

  /** شروع (یا همان برگهٔ موجود) — «قبول‌شده» ⇒ «در حال جمع‌آوری» */
  suspend fun start(orderId: String): ApiResult<PickSheetDto> =
    result { sheetOf(api.post("$ROOT/${enc(orderId)}/pick/start")) }

  suspend fun send(orderId: String, events: List<JsonObject>): ApiResult<PickEventsDto> = result {
    ApiJson.decode<PickEventsDto>(api.post("$ROOT/${enc(orderId)}/pick/events", buildJsonObject { put("events", JsonArray(events)) }))
  }

  suspend fun pack(orderId: String, bags: Int, key: String): ApiResult<PickSheetDto> = result {
    sheetOf(api.postKeyed("$ROOT/${enc(orderId)}/pick/pack", packJson(bags), key))
  }

  /** جای کالا در دکان (PCK-01) — برای برگه‌های بعدی؛ هر سه خالی ⇒ پاک */
  suspend fun setLocation(productId: String, aisle: String, shelf: String, level: String): ApiResult<Unit> = result {
    api.put("$ROOT/pick/locations/${enc(productId)}", buildJsonObject {
      put("aisle", aisle.trim())
      put("shelf", shelf.trim())
      put("level", level.trim())
    })
    Unit
  }

  // ── جایگزین (PCK-05) و حلال (PCK-06) ─────────────────────────────────

  suspend fun suggestions(orderId: String, lineNo: Int): ApiResult<PickSuggestionsDto> =
    result { ApiJson.decode<PickSuggestionsDto>(api.get("$ROOT/${enc(orderId)}/pick/substitutes?lineNo=$lineNo")) }

  /** پیشنهاد برای قلمِ «پیدا نشد»؛ کالای بارکددار با بارکدِ خودش. همان `eventId` ⇒ همان پیشنهاد */
  suspend fun propose(orderId: String, eventId: String, lineNo: Int, productId: String, barcode: String): ApiResult<PickProposedDto> = result {
    ApiJson.decode<PickProposedDto>(api.post("$ROOT/${enc(orderId)}/pick/substitutions", buildJsonObject {
      put("eventId", eventId)
      put("lineNo", lineNo)
      put("productId", productId)
      if (barcode.isNotBlank()) put("barcode", barcode.trim())
    }))
  }

  /** تصمیمِ کارمند پس از تماس/واتس‌اپ: `accept` یا `reject` */
  suspend fun decide(orderId: String, substitutionId: String, decision: String): ApiResult<Unit> = result {
    api.post("$ROOT/${enc(orderId)}/pick/substitutions/decide", buildJsonObject {
      put("substitutionId", substitutionId)
      put("decision", decision)
    })
    Unit
  }

  /** حلالِ کالا (PCK-06): `halal`، `non_halal` یا خالی (نامعلوم) — فقط `stock.adjust` */
  suspend fun setHalal(productId: String, halal: String): ApiResult<Unit> = result {
    api.put("$ROOT/pick/products/${enc(productId)}", buildJsonObject { put("halal", halal) })
    Unit
  }

  // ── عکسِ بسته (PCK-08) ───────────────────────────────────────────────

  suspend fun photos(orderId: String): ApiResult<PickPhotosDto> =
    result { ApiJson.decode<PickPhotosDto>(api.get("$ROOT/${enc(orderId)}/pick/photos")) }

  /** فقط از بسته (`parcelOnly` همیشه true، کاربر پیش از دوربین تأیید کرده)؛ همان `eventId` ⇒ همان عکس */
  suspend fun addPhoto(orderId: String, eventId: String, imageBase64: String, deviceTime: Long): ApiResult<PickPhotoDto> = result {
    ApiJson.decodeAt<PickPhotoDto>(
      api.post("$ROOT/${enc(orderId)}/pick/photos", buildJsonObject {
        put("eventId", eventId)
        put("imageBase64", imageBase64)
        put("parcelOnly", true)
        put("deviceTime", deviceTime)
      }),
      "photo",
    ) ?: throw ApiFailure.InvalidResponse()
  }

  // ── قفسهٔ انتظار (PCK-10) و جمع‌آوریِ گروهی (PCK-12) ─────────────────

  suspend fun shelf(): ApiResult<PickShelfDto> = result { ApiJson.decode<PickShelfDto>(api.get("$ROOT/pick/shelf")) }

  /** اسکنِ برچسبِ کیسه روی جای قفسه — تکرارِ همان کیسه بی‌اثر است (سرور) */
  suspend fun shelfScan(qr: String, slot: String): ApiResult<PickShelfScanDto> = result {
    ApiJson.decode<PickShelfScanDto>(api.post("$ROOT/pick/shelf/scan", buildJsonObject {
      put("qr", qr.trim())
      put("slot", slot.trim())
    }))
  }

  /** دستهٔ ۲ تا ۱۰ سفارش؛ همان کلید ⇒ همان دسته */
  suspend fun createBatch(orderIds: List<String>, key: String): ApiResult<PickBatchDto> = result {
    batchOf(api.postKeyed("$ROOT/pick/batches", batchJson(orderIds), key))
  }

  suspend fun batch(batchId: String): ApiResult<PickBatchDto> = result { batchOf(api.get("$ROOT/pick/batches/${enc(batchId)}")) }

  private fun batchOf(body: JsonObject): PickBatchDto =
    ApiJson.decodeAt<PickBatchDto>(body, "batch") ?: throw ApiFailure.InvalidResponse()

  private fun sheetOf(body: JsonObject): PickSheetDto =
    ApiJson.decodeAt<PickSheetDto>(body, "pick") ?: throw ApiFailure.InvalidResponse()

  companion object {
    const val ROOT = "/shop/orders"

    /** شناسهٔ سفارش ULID است؛ هر نویسهٔ دیگری درصدی می‌رود (بی `java.net`، راهِ آیفون) */
    internal fun enc(s: String): String = buildString {
      for (b in s.encodeToByteArray()) {
        val ch = b.toInt() and 0xFF
        if (ch in 0x30..0x39 || ch in 0x41..0x5A || ch in 0x61..0x7A || ch == 0x2D || ch == 0x5F || ch == 0x2E) append(ch.toChar())
        else append('%').append(HEX[ch shr 4]).append(HEX[ch and 0xF])
      }
    }

    private const val HEX = "0123456789ABCDEF"

    /**
     *  شناسهٔ یکتای رویداد (`KEY_RE`ِ سرور: ۸ تا ۱۲۸ نویسهٔ [A-Za-z0-9_.:-]) — ۱۲۸ بیتِ تصادفی؛ فقط
     *  یکتایی لازم است، نه راز (بی `java.util`، راهِ آیفون).
     */
    fun newEventId(): String = "pk-" + buildString {
      for (b in kotlin.random.Random.nextBytes(16)) {
        val v = b.toInt() and 0xFF
        append(HEX[v shr 4]).append(HEX[v and 0xF])
      }
    }

    /** بدنهٔ یک رویداد — همان شکلی که `parseEvent` ِ سرور می‌خواند */
    fun event(eventId: String, type: String, lineNo: Int, barcode: String? = null, qtyMilli: Long? = null): JsonObject =
      buildJsonObject {
        put("eventId", eventId)
        put("type", type)
        put("lineNo", lineNo)
        if (barcode != null) put("barcode", barcode)
        if (qtyMilli != null) put("qtyMilli", qtyMilli)
      }

    fun packJson(bags: Int): JsonObject = buildJsonObject { put("bags", bags) }

    fun batchJson(orderIds: List<String>): JsonObject = buildJsonObject {
      put("orderIds", JsonArray(orderIds.map { JsonPrimitive(it) }))
    }
  }
}
