package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.market.PublicShop
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 *  خطای سرور (`{ error: { code, message, …data } }`) یا شبکه (`status = 0`، `code = network`).
 *  [data] همان شیءِ `error` است؛ کمکی‌ها فقط کلیدهای شناخته را می‌خوانند.
 */
class ShopperError(
  val status: Int,
  val code: String,
  val message: String = "",
  val data: JsonObject? = null,
  /** `Retry-After` ِ ۴۲۹ یا سقفِ شماره (RSK-07)، به ثانیه */
  val retryAfterSeconds: Int? = null,
) {
  val network: Boolean get() = status == 0

  /** ۴۰۹ ِ `price_changed`: برگهٔ قیمتِ امروز */
  fun quote(): PriceQuote? = (data?.get("quote") as? JsonObject)?.let { decodeOrNull(PriceQuote.serializer(), it) }

  /** ۴۰۹ ِ `product_unavailable`: شناسه‌های کالای دیگر-در-ویترین-نیست */
  fun unavailable(): List<String> =
    (data?.get("unavailable") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

  /** ۳۰۸ ِ `store_moved` (S3): نشانیِ تازهٔ دکان */
  fun movedTo(): String? = if (code == STORE_MOVED) (data?.get("slug") as? JsonPrimitive)?.contentOrNull else null

  /** ۴۰۰ ِ `qty_over_limit`: سقفِ هر سفارشِ همان کالا */
  fun maxQty(): Pair<String, Long>? {
    val id = (data?.get("productId") as? JsonPrimitive)?.contentOrNull ?: return null
    val max = (data["maxQtyMilli"] as? JsonPrimitive)?.longOrNull ?: return null
    return id to max
  }

  /** `slot_*` و `bad_zone`/`zone_required`: فهرستِ تازهٔ بازه‌ها یا ناحیه‌ها در همان پاسخ */
  fun slots(): List<DeliverySlot>? = (data?.get("slots") as? JsonArray)?.let { a -> a.mapNotNull { decodeOrNull(DeliverySlot.serializer(), it) } }

  override fun toString(): String = "ShopperError($status, $code)"

  companion object {
    const val NETWORK = "network"
    const val BAD_RESPONSE = "bad_response"
    const val STORE_MOVED = "store_moved"
    const val PRICE_CHANGED = "price_changed"
  }
}

/** نتیجهٔ هر درخواست — یا مقدار، یا خطای روشن (هرگز استثنای بی‌دسته به صفحه نمی‌رسد). */
sealed class ShopperResult<out T> {
  data class Ok<out T>(val value: T) : ShopperResult<T>()
  data class Fail(val error: ShopperError) : ShopperResult<Nothing>()

  fun valueOrNull(): T? = (this as? Ok)?.value
  fun errorOrNull(): ShopperError? = (this as? Fail)?.error
}

private fun <T> decodeOrNull(s: DeserializationStrategy<T>, e: JsonElement): T? =
  try { ShopperJson.decodeFromJsonElement(s, e) } catch (_: Exception) { null }

/**
 *  کارخواهِ تایپ‌شدهٔ ویترین و بازارچه برای برنامهٔ خرید — همان درهای عمومی که `server/public/store` و
 *  `server/public/market` می‌زنند؛ هیچ درِ تازه‌ای در سرور لازم نیست.
 *
 *  ⛔ پول همیشه از سرور: قیمتِ دیده‌شده فقط در `expectedUnitPriceMinor`/`expectedTotalMinor` می‌رود و ناهمخوانی
 *  ۴۰۹ ِ `price_changed` با برگهٔ امروز است ([ShopperError.quote]). ثبت فقط با کلیدِ یکتایی ([CheckoutKeys]) —
 *  زدنِ دوباره پس از قطعِ شبکه همان سفارش است، نه سفارشِ دوم.
 *
 *  [lang] زبانِ پیام‌های سرور (`Accept-Language`) است؛ برنامه متنِ خطا را خودش از [ShopperText.error] می‌گوید.
 */
class ShopperApi(private val http: ShopperHttp, private val lang: () -> String = { "fa" }) {

  // ── بازارچه ──

  fun market(): ShopperResult<MarketOverview> = get("/api/market", MarketOverview.serializer())

  fun shops(q: String = "", category: String = "", offset: Int = 0, limit: Int = 20): ShopperResult<ShopList> =
    get("/api/market/shops" + ShopperUrl.query(
      "q" to q.trim().take(60), "category" to category, "offset" to offset.takeIf { it > 0 }?.toString(), "limit" to limit.toString(),
    ), ShopList.serializer())

  // ── دکان ──

  fun store(slug: String): ShopperResult<StorePage> = call("GET", base(slug), null) { root ->
    val store = root["store"] as? JsonObject ?: return@call null
    val shop = decodeOrNull(PublicShop.serializer(), store) ?: return@call null
    val delivery = store["delivery"]?.let { decodeOrNull(StoreDelivery.serializer(), it) } ?: StoreDelivery()
    StorePage(shop, delivery, flag(root, "verification"), flag(root, "market"))
  }

  /** کاتالوگ: جست‌وجو (`q`، حداکثر ۶۰ نویسه، مثلِ سرور)، دسته و صفحهٔ بعد (`cursor`). */
  fun products(slug: String, q: String = "", category: String = "", cursor: String? = null, limit: Int = 30): ShopperResult<CatalogPage> =
    get(base(slug) + "/products" + ShopperUrl.query(
      "q" to q.trim().take(60), "category" to category, "cursor" to cursor, "limit" to limit.toString(),
    ), CatalogPage.serializer())

  /** همان کالاها با شناسه (حداکثر ۱۰۰) — تازه کردنِ قیمت و «هنوز در ویترین» ِ سبد (WEB-12). */
  fun productsByIds(slug: String, ids: List<String>): ShopperResult<CatalogPage> =
    if (ids.isEmpty()) ShopperResult.Ok(CatalogPage())
    else get(base(slug) + "/products" + ShopperUrl.query("ids" to ids.distinct().take(100).joinToString(",")), CatalogPage.serializer())

  fun product(slug: String, id: String): ShopperResult<ProductPage> =
    get(base(slug) + "/products/" + ShopperUrl.encode(id), ProductPage.serializer())

  fun locate(slug: String, lat: Double, lng: Double): ShopperResult<LocateResult> =
    get(base(slug) + "/delivery" + ShopperUrl.query("lat" to lat.toString(), "lng" to lng.toString()), LocateResult.serializer())

  fun slots(slug: String, fulfillment: String): ShopperResult<SlotsPage> =
    get(base(slug) + "/slots" + ShopperUrl.query("fulfillment" to fulfillment), SlotsPage.serializer())

  // ── تأییدِ شماره (۲۳) ──

  fun verifyStart(slug: String, phone: String): ShopperResult<CodeSent> =
    post(base(slug) + "/verify/start", obj("phone" to phone), CodeSent.serializer())

  fun verifyCheck(slug: String, phone: String, code: String): ShopperResult<PhoneTicket> =
    post(base(slug) + "/verify/check", obj("phone" to phone, "code" to code), PhoneTicket.serializer())

  // ── ثبت و پیگیری ──

  /** ثبت (CHK-19): [key] همان کلیدِ [CheckoutKeys] است؛ [ticket] شمارهٔ تأییدشده. */
  fun checkout(slug: String, request: CheckoutRequest, key: String, ticket: String?): ShopperResult<Placed> {
    val headers = buildMap {
      put("Idempotency-Key", key)
      if (!ticket.isNullOrEmpty()) put("X-Store-Ticket", ticket)
    }
    return call("POST", base(slug) + "/checkout", ShopperJson.encodeToString(CheckoutRequest.serializer(), request), headers) { root ->
      val order = root["order"]?.let { decodeOrNull(PublicOrder.serializer(), it) } ?: return@call null
      Placed(order, (root["replayed"] as? JsonPrimitive)?.booleanOrNull == true)
    }
  }

  fun track(slug: String, token: String): ShopperResult<PublicOrder> = order("GET", base(slug) + "/orders/" + ShopperUrl.encode(token), null)

  /** لغو تا دکان قبول نکرده (`cannot_cancel` پس از آن). تکرارش همان پاسخ. */
  fun cancel(slug: String, token: String): ShopperResult<PublicOrder> =
    order("POST", base(slug) + "/orders/" + ShopperUrl.encode(token) + "/cancel", "{}")

  /** «پرداخت پس از پذیرش» (`lib/order-flow.js`): انتخابِ نقد — مبلغ و حالت را سرور می‌گوید. */
  fun payCash(slug: String, token: String): ShopperResult<PublicOrder> =
    order("POST", base(slug) + "/orders/" + ShopperUrl.encode(token) + "/pay-choice", obj("method" to "cash"))

  /** نشانیِ عکس (نسبت به ریشهٔ سرور): کارت ⇒ `thumb`، برگهٔ کالا ⇒ `photo`؛ `?v=` همان `photoVersion` (IMG-12). */
  fun photoPath(slug: String, productId: String, version: String?, thumb: Boolean): String =
    base(slug) + "/products/" + ShopperUrl.encode(productId) + (if (thumb) "/thumb" else "/photo") + ShopperUrl.query("v" to version)

  // ── درون ──

  private fun base(slug: String) = "/api/store/" + ShopperUrl.encode(slug)

  private fun order(method: String, path: String, body: String?): ShopperResult<PublicOrder> =
    call(method, path, body) { root -> root["order"]?.let { decodeOrNull(PublicOrder.serializer(), it) } }

  private fun <T> get(path: String, s: DeserializationStrategy<T>): ShopperResult<T> = call("GET", path, null) { decodeOrNull(s, it) }

  private fun <T> post(path: String, body: String, s: DeserializationStrategy<T>): ShopperResult<T> = call("POST", path, body) { decodeOrNull(s, it) }

  private fun obj(vararg kv: Pair<String, String>): String =
    ShopperJson.encodeToString(JsonObject.serializer(), JsonObject(kv.associate { it.first to JsonPrimitive(it.second) }))

  private fun flag(root: JsonObject, key: String): Boolean =
    ((root[key] as? JsonObject)?.get("available") as? JsonPrimitive)?.booleanOrNull == true

  private fun <T> call(
    method: String,
    path: String,
    body: String?,
    extra: Map<String, String> = emptyMap(),
    read: (JsonObject) -> T?,
  ): ShopperResult<T> {
    val headers = buildMap {
      put("Accept", "application/json")
      put("Accept-Language", lang())
      if (body != null) put("Content-Type", "application/json")
      putAll(extra)
    }
    val res = try {
      http.send(ShopperRequest(method, path, headers, body))
    } catch (e: CancellationException) {
      throw e   //  لغوِ کوروتینِ صفحه «خطای شبکه» نیست
    } catch (_: Exception) {
      return ShopperResult.Fail(ShopperError(0, ShopperError.NETWORK))
    }
    val root = try {
      if (res.body.isBlank()) JsonObject(emptyMap()) else ShopperJson.parseToJsonElement(res.body).jsonObject
    } catch (_: Exception) {
      null
    }
    if (res.status !in 200..299) {
      val err = root?.get("error") as? JsonObject
      val code = (err?.get("code") as? JsonPrimitive)?.contentOrNull
        ?: if (res.status == 429) "rate_limited" else ShopperError.BAD_RESPONSE
      val msg = (err?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
      val retry = res.header("Retry-After")?.trim()?.toIntOrNull()
      return ShopperResult.Fail(ShopperError(res.status, code, msg, err, retry))
    }
    val value = root?.let(read) ?: return ShopperResult.Fail(ShopperError(res.status, ShopperError.BAD_RESPONSE))
    return ShopperResult.Ok(value)
  }
}

