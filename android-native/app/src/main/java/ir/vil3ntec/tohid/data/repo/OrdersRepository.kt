package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.HandoverPageDto
import ir.vil3ntec.tohid.core.model.OrderBulkOutDto
import ir.vil3ntec.tohid.core.model.OrderCustomerPageDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderNoticeDto
import ir.vil3ntec.tohid.core.model.OrderNoticesDto
import ir.vil3ntec.tohid.core.model.OrderPageDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.model.OrdersSummaryDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderDraft
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  بخشِ «سفارش‌ها»ی برنامهٔ دکان — `/shop/orders` (فاز ۴).
 *
 *  ⛔ دکان و نقش را سرور از توکن می‌گیرد؛ این‌جا هیچ شناسهٔ دکانی فرستاده نمی‌شود.
 *  ⛔ وضعیت را فقط سرور عوض می‌کند؛ `from` همراهِ هر گذار می‌رود تا اگر کسِ
 *  دیگری زودتر زده بود، ۴۰۹ (`status_changed`) بیاید نه گذارِ روی وضعیتِ کهنه.
 *  ⛔ سفارشِ دستی با `clientKey`: تکرارِ همان فرم (قطعِ نت، دو بار زدن) همان
 *  سفارش است، نه دومی (CHK-19).
 */
class OrdersRepository(private val api: ApiClient) {

  /**
   *  `caps=saleOnHandover`: این برنامه فروشِ تحویل را خودش ثبت می‌کند ⇒ سرور کفِ تحویلِ این دکان
   *  را (اگر نیست) همین لحظه می‌گذارد؛ تحویلِ پیش از آن هرگز فروش نمی‌گیرد.
   */
  suspend fun config(): ApiResult<OrdersConfigDto> =
    result { ApiJson.decode<OrdersConfigDto>(api.get("$ROOT/config?caps=saleOnHandover")) }

  suspend fun summary(): ApiResult<OrdersSummaryDto> =
    result { ApiJson.decode<OrdersSummaryDto>(api.get("$ROOT/summary")) }

  suspend fun list(tab: String, cursor: String? = null, limit: Int = PAGE): ApiResult<OrderPageDto> = result {
    val q = buildString {
      append("$ROOT?tab=").append(enc(tab)).append("&limit=").append(limit)
      if (!cursor.isNullOrBlank()) append("&cursor=").append(enc(cursor))
    }
    ApiJson.decode<OrderPageDto>(api.get(q))
  }

  /**
   *  CRM-07: سفارش‌های هنوز تحویل‌نشده یا لغوشدهٔ یک مشتری (تا دو شماره، اول و دوم) برای پروندهٔ او؛
   *  فقط صاحب و مدیر (`orders.contact`)، پاسخ همان کارتِ فهرست با شمارهٔ پوشیده.
   */
  suspend fun forCustomer(phones: List<String>, customerId: String? = null): ApiResult<OrderCustomerPageDto> = result {
    val q = phones.take(2).joinToString("&") { "phone=" + enc(it) } +
      //  CRM-15: شناسهٔ پرونده ⇐ ردیفِ «چه کسی پرونده را دید» ِ سرور زیرِ همین پرونده (بی آن کلیدِ یک‌طرفهٔ شماره)
      (customerId?.takeIf { it.isNotBlank() }?.let { "&customerId=" + enc(it) } ?: "")
    ApiJson.decode<OrderCustomerPageDto>(api.get("$ROOT/customer?$q"))
  }

  suspend fun get(id: String): ApiResult<OrderDto> =
    result { order(api.get("$ROOT/${enc(id)}")) }

  /**
   *  RSK-03: سابقهٔ همین مشتری در همین دکان پیش از ارسال (`GET /:id/risk`) — فقط خواندن، بی شماره؛ پرچمِ
   *  `shop.order_risk` خاموش ⇒ `enabled = false` (کارت دیده نمی‌شود).
   */
  suspend fun risk(id: String): ApiResult<ir.vil3ntec.tohid.core.orders.OrderRisk.View> =
    result { ir.vil3ntec.tohid.core.orders.OrderRisk.parse(api.get("$ROOT/${enc(id)}/risk")) }

  /**
   *  RSK-03 «کارت در صف»: سفارش‌های پیش از ارسالِ همین دکان با هشدار (`GET /risk/queue`، یک پرسش برای همهٔ صف)؛ پرچمِ
   *  `shop.order_risk` خاموش ⇒ `enabled = false`؛ سرورِ کهنه ⇒ ۴۰۴ (کارتی نیست، `OrderRisk.queueCard`).
   */
  suspend fun riskQueue(): ApiResult<ir.vil3ntec.tohid.core.orders.OrderRisk.Queue> =
    result { ir.vil3ntec.tohid.core.orders.OrderRisk.queue(api.get("$ROOT/risk/queue")) }

  /** RSK-04: ردیف‌های فعالِ فهرستِ بسته و مطمئن — فقط صاحب و مدیر (`orders.contact`)؛ پرچمِ خاموش ⇒ ۴۰۳ `order_risk_disabled` */
  suspend fun riskLists(): ApiResult<List<ir.vil3ntec.tohid.core.orders.OrderRisk.Entry>> =
    result { ir.vil3ntec.tohid.core.orders.OrderRisk.entries(api.get("$ROOT/risk/lists")) }

  /** RSK-04: افزودن (بدنه از `OrderRisk.phoneEntryBody` یا `pinEntryBody`)؛ ردِ سرور (شمارهٔ هم‌اکنون در فهرستِ دیگر، سقف) ⇒ پیامِ خودش */
  suspend fun addRiskEntry(body: JsonObject): ApiResult<Unit> = result { api.post("$ROOT/risk/lists", body); Unit }

  /** RSK-04: برداشتنِ نرم (سابقه می‌ماند)؛ تکرار بی اثر */
  suspend fun removeRiskEntry(entryId: String): ApiResult<Unit> = result { api.delete("$ROOT/risk/lists/${enc(entryId)}"); Unit }

  /**
   *  سفارش‌های تحویل‌شده (مشتری گرفت / به پیک داده شد / رسید) که فروششان هنوز در دفترِ
   *  همگام‌شدهٔ دکان نیست (`items`)، و برگشته‌هایی که فروششان لغو نشده (`reversals`).
   *  [since]: کفِ سرور؛ [after]: صفحهٔ بعد؛ [orderId]: فقط همان سفارش (پیش از هر ثبت).
   */
  suspend fun handover(since: Long, after: String? = null, orderId: String? = null): ApiResult<HandoverPageDto> = result {
    val q = buildString {
      append("$ROOT/handover?since=").append(since).append("&limit=20")
      if (!after.isNullOrBlank()) append("&after=").append(enc(after))
      if (!orderId.isNullOrBlank()) append("&orderId=").append(enc(orderId))
    }
    ApiJson.decode<HandoverPageDto>(api.get(q))
  }

  /**
   *  «به فاکتور» (`POST /:id/invoice`، `lib/order-flow.claimInvoice`): سرور «فاکتور شد» را یک بار زیرِ قفل می‌نشاند؛
   *  تکرار (همین دستگاه یا دیگری) همان فاکتور و همان زمان ⇒ بی کلیدِ یکتایی. پاسخ جزئیاتِ تازه با `invoice`.
   */
  suspend fun invoice(id: String): ApiResult<OrderDto> =
    result { order(api.post("$ROOT/${enc(id)}/invoice", JsonObject(emptyMap()))) }

  suspend fun create(clientKey: String, body: OrderDraft.Body): ApiResult<OrderDto> =
    result { order(api.post(ROOT, createJson(clientKey, body))) }

  /**
   *  ویرایشِ دستورِ تحویل تا پیش از ارسال (CHK-24) — فقط کلیدهای آمده؛ `expectedVersion` ِ کهنه ⇒
   *  ۴۰۹ و هیچ تغییری (ویرایشِ هم‌زمانِ دو دستگاه بی‌صدا رونویسی نمی‌شود).
   */
  suspend fun editDelivery(id: String, expectedVersion: Long, body: JsonObject): ApiResult<OrderDto> =
    result { order(api.post("$ROOT/${enc(id)}/delivery", JsonObject(body + ("expectedVersion" to JsonPrimitive(expectedVersion))))) }

  /**
   *  «بررسی شد» ِ صاحب یا مدیر برای ردیفِ بررسیِ فروشِ تحویل (`before_floor`، `reverse_blocked`) —
   *  ردیف برای همهٔ دستگاه‌ها می‌رود؛ تکرار بی‌اثر.
   */
  suspend fun reviewAck(id: String, kind: String): ApiResult<Unit> = result {
    api.post("$ROOT/${enc(id)}/review-ack", buildJsonObject { put("kind", kind) })
    Unit
  }

  suspend fun transition(
    id: String,
    to: String,
    from: String,
    reason: String = "",
    note: String = "",
    readyInMinutes: Int? = null,
  ): ApiResult<OrderDto> = result {
    val body = buildJsonObject {
      put("to", to)
      put("from", from)
      if (reason.isNotEmpty()) put("reason", reason)
      if (note.isNotBlank()) put("note", note.trim())
      if (readyInMinutes != null) put("readyInMinutes", readyInMinutes)
    }
    order(api.post("$ROOT/${enc(id)}/transition", body))
  }

  /**
   *  FIN-01 «ثبتِ بازپرداخت» (`POST /:id/refunds`، فقط با `refundLedger` ِ `/config`): [key] یک بار برای هر برگه ⇒ «تأیید» ِ
   *  دوباره پس از قطعِ نت ردیفِ دوم نمی‌سازد. فقط نقد (سرور). [final] = بستنِ سفارش با «بازپرداختِ بخشی»؛ برابر با مانده
   *  خودبه‌خود «بازپرداختِ کامل». بیش از مانده ⇒ ۴۰۹ `refund_exceeds_remaining` و هیچ ردیفی.
   */
  suspend fun refund(id: String, key: String, amountMinor: Long, reason: String, final: Boolean): ApiResult<OrderDto> = result {
    val body = buildJsonObject {
      put("amountMinor", amountMinor)
      put("reason", reason.trim())
      put("method", "cash")
      put("final", final)
    }
    order(api.postKeyed("$ROOT/${enc(id)}/refunds", body, key))
  }

  /**
   *  ORD-07 «عقب انداختن» (`POST /:id/delay`، پرچمِ `shop.orders.delay`): [key] یک بار برای هر پنجره ⇒
   *  «تأیید» ِ دوباره پس از قطعِ نت دو بار عقب نمی‌برد. [expectedReadyAt] همان زمانی است که کاربر دید
   *  (`null` = «بی زمان»)؛ کسِ دیگری زودتر عوضش کرده ⇒ ۴۰۹ `ready_changed` و هیچ تغییری.
   */
  suspend fun delay(id: String, key: String, minutes: Int, reason: String, note: String, expectedReadyAt: Long?): ApiResult<OrderDto> = result {
    val body = buildJsonObject {
      put("clientKey", key)
      put("minutes", minutes)
      put("expectedReadyAt", expectedReadyAt?.let { JsonPrimitive(it) } ?: JsonNull)
      if (reason.isNotEmpty()) put("reason", reason)
      if (note.isNotBlank()) put("note", note.trim().take(300))
    }
    order(api.post("$ROOT/${enc(id)}/delay", body))
  }

  /**
   *  ORD-19 گذارِ گروهی (`POST /shop/orders/bulk/transition`، پرچمِ `shop.orders.bulk`): یک `from` برای همه؛ سرور
   *  هر سفارش را جدا از همان درِ گذارِ تکی می‌گذراند و نتیجهٔ هر کدام جداست. تکرار ⇒ `unchanged`.
   */
  suspend fun bulkTransition(ids: List<String>, to: String, from: String, reason: String = "", note: String = "", readyInMinutes: Int? = null): ApiResult<OrderBulkOutDto> = result {
    val body = buildJsonObject {
      put("ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
      put("to", to)
      put("from", from)
      if (reason.isNotEmpty()) put("reason", reason)
      if (note.isNotBlank()) put("note", note.trim())
      if (readyInMinutes != null) put("readyInMinutes", readyInMinutes)
    }
    ApiJson.decode<OrderBulkOutDto>(api.post("$ROOT/bulk/transition", body))
  }

  /** ORD-16 پیام‌های وضعیت به مشتریِ همین سفارش (`GET /:id/notices`) */
  suspend fun notices(id: String): ApiResult<OrderNoticesDto> =
    result { ApiJson.decode<OrderNoticesDto>(api.get("$ROOT/${enc(id)}/notices")) }

  /**
   *  ORD-16 «فرستادنِ دوباره» (`POST /:id/notices/:noticeId/resend`، پرچمِ `shop.orders.notice_resend`): همان ردیف
   *  دوباره در صف؛ [key] یک بار برای هر دکمه ⇒ دو کلیک یا قطعِ نت پس از ثبت دو بار نمی‌فرستد.
   */
  suspend fun resendNotice(id: String, noticeId: String, key: String): ApiResult<OrderNoticeDto> = result {
    val out = api.post("$ROOT/${enc(id)}/notices/${enc(noticeId)}/resend", buildJsonObject { put("clientKey", key) })
    ApiJson.decodeAt<OrderNoticeDto>(out, "notice") ?: throw ApiFailure.InvalidResponse()
  }

  /**
   *  ORD-14 «ویرایشِ سفارش» (`POST /:id/amendments`): فقط مقدارِ تازهٔ قلم‌ها (۰ = برداشتن)؛ قیمت را سرور می‌گذارد.
   *  سفارشِ کارمند با رضایتِ ثبت‌شده همان دم اعمال می‌شود؛ سفارشِ ویترین پیشنهادی است که خودِ مشتری می‌پذیرد.
   *  [key] یک بار برای هر پنجره ⇒ «ثبت» ِ دوباره پس از قطعِ نت دو بار ویرایش نمی‌کند.
   */
  suspend fun amend(id: String, key: String, changes: List<Pair<Int, Long>>, reason: String, note: String, consentVia: String, consentNote: String): ApiResult<Unit> = result {
    val body = buildJsonObject {
      put("clientKey", key)
      put("changes", buildJsonArray { changes.forEach { (no, q) -> add(buildJsonObject { put("lineNo", no); put("qtyMilli", q) }) } })
      if (reason.isNotEmpty()) put("reason", reason)
      if (note.isNotBlank()) put("note", note.trim())
      if (consentVia.isNotEmpty()) put("consent", buildJsonObject { put("via", consentVia); if (consentNote.isNotBlank()) put("note", consentNote.trim()) })
    }
    api.post("$ROOT/${enc(id)}/amendments", body)
    Unit
  }

  /** پس گرفتنِ پیشنهادِ بازِ ویترین (`POST /:id/amendments/:amendId/withdraw`) */
  suspend fun withdrawAmend(id: String, amendId: String): ApiResult<Unit> = result {
    api.post("$ROOT/${enc(id)}/amendments/${enc(amendId)}/withdraw", JsonObject(emptyMap()))
    Unit
  }

  /** ثبتِ تسویهٔ نقدیِ تفاوتِ ویرایشِ سفارشِ پرداخت‌شده (`POST /:id/amendments/:amendId/settlement`؛ فقط نقد، بی درگاه) */
  suspend fun settleAmend(id: String, amendId: String, key: String, note: String): ApiResult<Unit> = result {
    api.post("$ROOT/${enc(id)}/amendments/${enc(amendId)}/settlement", buildJsonObject {
      put("clientKey", key); put("method", "cash"); if (note.isNotBlank()) put("note", note.trim())
    })
    Unit
  }

  private fun order(body: JsonObject): OrderDto =
    ApiJson.decodeAt<OrderDto>(body, "order") ?: throw ApiFailure.InvalidResponse()

  companion object {
    const val ROOT = "/shop/orders"
    const val PAGE = 30

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    /** بدنهٔ `POST /shop/orders` — همان شکلی که `parseCreate`ِ سرور می‌خواند */
    fun createJson(clientKey: String, b: OrderDraft.Body): JsonObject = buildJsonObject {
      put("clientKey", clientKey)
      put("source", b.source)
      put("fulfillment", b.fulfillment)
      put("paymentMethod", "cod")
      put("customer", buildJsonObject {
        put("name", b.customerName)
        if (b.customerPhone.isNotEmpty()) put("phone", b.customerPhone)
      })
      if (b.fulfillment == "delivery") {
        put("address", buildJsonObject {
          if (b.addressText.isNotEmpty()) put("text", b.addressText)
          if (b.landmark.isNotEmpty()) put("landmark", b.landmark)
        })
        if (b.deliveryFeeMinor > 0) put("deliveryFeeMinor", b.deliveryFeeMinor)
        //  CHK-14: فقط وقتی نوشته شده (سرورِ کهنه/پرچمِ خاموش همان بدنهٔ پیشین را می‌بیند)
        b.cashTenderMinor?.let { t -> put("delivery", buildJsonObject { put("cashTenderMinor", t) }) }
      }
      put("lines", buildJsonArray {
        for (l in b.lines) add(buildJsonObject {
          //  POS-38: قلمِ صندوق شناسهٔ کالا و واحد دارد؛ تهی ⇒ نوشته نمی‌شود (بدنهٔ سفارشِ دستی همان پیشین)
          if (l.productId.isNotEmpty()) put("productId", l.productId)
          put("name", l.name)
          if (l.unit.isNotEmpty()) put("unit", l.unit)
          put("qtyMilli", l.qtyMilli)
          put("unitPriceMinor", l.unitPriceMinor)
        })
      })
      if (b.note.isNotEmpty()) put("note", b.note)
      if (b.test) put("test", JsonPrimitive(true))
      //  CHK-15: فقط وقتی برگزیده شده (سرورِ کهنه/پرچمِ خاموش همان بدنهٔ پیشین را می‌بیند)
      if (b.substitution.isNotEmpty()) put("substitution", b.substitution)
    }

    /** کلیدِ یکتایی — یک بار برای هر فرم */
    fun newKey(): String = "app-" + java.util.UUID.randomUUID().toString()
  }
}
