package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.data.PoBillLineDto
import ir.vil3ntec.tohid.data.PoBilledDto
import ir.vil3ntec.tohid.data.PoConfigDto
import ir.vil3ntec.tohid.data.PoLineDto
import ir.vil3ntec.tohid.data.PoOrderDto
import ir.vil3ntec.tohid.data.PoPageDto
import ir.vil3ntec.tohid.data.PoReceivedDto
import ir.vil3ntec.tohid.data.PoReceiptLineDto
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  سفارشِ خرید — `/api/shop/purchase-orders` (`server/src/routes/purchase-orders.js`، PUR-02/04).
 *
 *  ⛔ دکان و نقش را سرور از توکن می‌گیرد. ⛔ بی پرچمِ `shop.purchase_orders` فقط `config` جواب
 *  می‌دهد و صفحه دیده نمی‌شود. ساختن با `Idempotency-Key` و رسید با شناسهٔ خودش (کلیدِ یکتا):
 *  تکرارِ همان درخواست (قطعِ نت، دو بار زدن) همان سفارش/رسید است، نه دومی.
 */
class PurchaseOrderRepository(private val api: ApiClient) {

  suspend fun config(): ApiResult<PoConfigDto> = result { ApiJson.decode<PoConfigDto>(api.get("$ROOT/config")) }

  /**
   *  [status] (PUR-02/03) وضعیتِ نمایشی برای سرور (`sent`/`partial`/`complete`/`open`)؛ سرورِ کهنه نمی‌شناسد و فقط [state] را
   *  می‌پالاید، پس پالایشِ دومِ برنامه (`PoForms.shown`، `PurchaseOrderBook.onOrderMilli`) سرِ جایش می‌ماند. صفحهٔ کم‌تر از ۵۰
   *  (حتی تهی) با `next` یعنی «هنوز هست».
   */
  suspend fun list(state: String? = null, cursor: String? = null, status: String? = null): ApiResult<PoPageDto> = result {
    val q = buildString {
      append("$ROOT?limit=50")
      if (!state.isNullOrBlank()) append("&state=").append(enc(state))
      if (!status.isNullOrBlank()) append("&status=").append(enc(status))
      if (!cursor.isNullOrBlank()) append("&cursor=").append(enc(cursor))
    }
    ApiJson.decode<PoPageDto>(api.get(q))
  }

  /**
   *  PUR-03/10: سفارش‌های پیشنهادِ سفارش (`PurchaseOrderBook.planningOrders`) — همهٔ بازها برای «در راه» و صفحهٔ نخستِ
   *  فرستاده‌ها برای زمانِ تأمین. پیش‌تر فقط صفحهٔ نخستِ فرستاده‌ها (۵۰ تا، پر از سفارشِ کامل) ⇒ بازِ کهنه «در راه» نبود.
   */
  suspend fun planningOrders(): List<PurchaseOrderDto> = PurchaseOrderBook.planningOrders { cursor, open ->
    list(PurchaseOrderCore.State.SENT, cursor, if (open) PurchaseOrderBook.OPEN_STATUS else null).valueOrNull()
  }

  suspend fun get(id: String): ApiResult<PurchaseOrderDto> =
    result { ApiJson.decode<PoOrderDto>(api.get("$ROOT/${enc(id)}")).order }

  /** پیش‌نویسِ تازه؛ [key] را صفحه یک بار برای همین فرم می‌سازد */
  suspend fun create(key: String, draft: Draft): ApiResult<PurchaseOrderDto> =
    result { ApiJson.decode<PoOrderDto>(api.postKeyed(ROOT, draft.json(version = null), key)).order }

  /** ویرایشِ پیش‌نویس با نسخهٔ دیده‌شده (کهنه ⇒ ۴۰۹ `version_conflict`) */
  suspend fun update(id: String, version: Int, draft: Draft): ApiResult<PurchaseOrderDto> =
    result { ApiJson.decode<PoOrderDto>(api.put("$ROOT/${enc(id)}", draft.json(version))).order }

  suspend fun send(id: String): ApiResult<PurchaseOrderDto> =
    result { ApiJson.decode<PoOrderDto>(api.post("$ROOT/${enc(id)}/send")).order }

  suspend fun cancel(id: String, reason: String): ApiResult<PurchaseOrderDto> = result {
    ApiJson.decode<PoOrderDto>(api.post("$ROOT/${enc(id)}/cancel", buildJsonObject { put("reason", reason) })).order
  }

  /** رسیدِ دریافت؛ [receiptId] کلیدِ یکتای همین رسید است (صفحه یک بار می‌سازد)؛ [freightMinor] کرایهٔ رسید (PUR-06) */
  suspend fun receive(
    id: String,
    receiptId: String,
    lines: List<PoReceiptLineDto>,
    allowOver: Boolean,
    note: String,
    freightMinor: Long = 0,
  ): ApiResult<PoReceivedDto> =
    result {
      val body = buildJsonObject {
        put("id", receiptId)
        put("allowOver", allowOver)
        put("note", note)
        //  کرایهٔ صفر فرستاده نمی‌شود ⇒ بدنهٔ رسیدِ بی کرایه همان پیشین
        if (freightMinor > 0) put("freightMinor", freightMinor)
        put("lines", buildJsonArray {
          lines.forEach { l ->
            add(buildJsonObject {
              put("lineId", l.lineId)
              put("receivedMilli", l.receivedMilli)
              put("damagedMilli", l.damagedMilli)
              put("priceMinor", l.priceMinor)
            })
          }
        })
      }
      ApiJson.decode<PoReceivedDto>(api.post("$ROOT/${enc(id)}/receipts", body))
    }

  /**
   *  PUR-14 — فاکتورِ جدای تأمین‌کننده؛ [billId] کلیدِ یکتای همین فاکتور است (صفحه یک بار می‌سازد): تکرارِ همان
   *  درخواست همان فاکتور است. فقط مدیر و صاحب (`stock.adjust`).
   */
  suspend fun bill(id: String, billId: String, billNo: String, billDate: String?, note: String, lines: List<PoBillLineDto>): ApiResult<PoBilledDto> =
    result {
      val body = buildJsonObject {
        put("id", billId)
        put("billNo", billNo)
        if (!billDate.isNullOrBlank()) put("billDate", billDate)
        put("note", note)
        put("lines", buildJsonArray {
          lines.forEach { l ->
            add(buildJsonObject {
              l.lineId?.let { put("lineId", it) }
              put("productId", l.productId)
              put("qtyMilli", l.qtyMilli)
              put("totalMinor", l.totalMinor)
            })
          }
        })
      }
      ApiJson.decode<PoBilledDto>(api.post("$ROOT/${enc(id)}/bills", body))
    }

  /** باطل کردنِ فاکتورِ تأمین‌کننده با دلیل؛ تکرار بی اثر */
  suspend fun voidBill(id: String, billId: String, reason: String): ApiResult<PoBilledDto> = result {
    ApiJson.decode<PoBilledDto>(api.post("$ROOT/${enc(id)}/bills/${enc(billId)}/void", buildJsonObject { put("reason", reason) }))
  }

  data class Draft(
    val supplierId: String,
    val supplierName: String,
    val lines: List<PoLineDto>,
    val expectedDate: String?,
    val note: String,
  ) {
    fun json(version: Int?): JsonObject = buildJsonObject {
      if (version != null) put("version", version)
      put("supplierId", supplierId)
      put("supplierName", supplierName)
      if (!expectedDate.isNullOrBlank()) put("expectedDate", expectedDate)
      put("note", note)
      put("lines", buildJsonArray {
        lines.forEach { l ->
          add(buildJsonObject {
            put("id", l.id)
            put("productId", l.productId)
            put("name", l.name)
            put("unit", l.unit)
            put("qtyMilli", l.qtyMilli)
            put("priceMinor", l.priceMinor)
            //  PRD-07: فقط ردیفِ به واحدِ خرید — بدنهٔ ردیفِ واحدِ پایه همان پیشین
            l.factorMilli?.let { put("factorMilli", it) }
          })
        }
      })
    }
  }

  private companion object {
    const val ROOT = "/shop/purchase-orders"

    /** درصدی‌کردنِ بخشِ نشانی بی کتابخانهٔ جاوا (قاعدهٔ آیفون، §۴۰.۳): جز حرف و رقمِ لاتین و `-._~` هر بایتِ UTF-8 ⇒ `%XX` */
    fun enc(s: String): String = buildString {
      for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code) {
          append(c.toChar())
        } else {
          append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0x0F])
        }
      }
    }
  }
}
