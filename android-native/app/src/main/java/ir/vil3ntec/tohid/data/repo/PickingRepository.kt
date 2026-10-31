package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.PickEventsDto
import ir.vil3ntec.tohid.core.model.PickSheetDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
  }
}
