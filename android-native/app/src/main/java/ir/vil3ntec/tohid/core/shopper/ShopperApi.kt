package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  مخزنِ حالتِ «خرید» — بازارچه و ویترینِ دکان‌ها، بی‌توکنِ حساب.
 *
 *  ⛔ همهٔ مسیرها عمومی‌اند (`getPublic`/`postPublic`): خریدار حسابِ دکان
 *  ندارد و توکنِ صاحب دکان (اگر روی همین گوشی وارد شده) هرگز این‌جا نمی‌رود.
 *  تنها هویت، بلیتِ شمارهٔ تأییدشده است (`X-Store-Ticket`) — و فقط برای
 *  مسیرهایی که لازمش دارند (ثبت، «سفارش‌های من»).
 *
 *  سرور داور است: قیمت، موجودی، ناحیه و بازه همان لحظه در سرور سنجیده
 *  می‌شوند؛ این‌جا فقط خوانده و فرستاده می‌شود.
 */
class ShopperApi(
  private val api: ApiClient,
  /** بلیتِ معتبرِ همین دستگاه — یا null */
  private val ticket: () -> PhoneTicket? = { null },
  private val now: () -> Long = { System.currentTimeMillis() },
) {

  /* ------------------------------ بازارچه ------------------------------ */

  suspend fun overview(): ApiResult<MarketOverview> = ApiResult.of {
    ApiJson.decode<MarketOverview>(api.getPublic(ApiEndpoints.Market.HOME))
  }

  /**
   *  @param first دکان‌های دلخواهِ همین دستگاه (تا ۲۰)، اول — سرور ذخیره نمی‌کند؛
   *         سرورِ قدیمی‌تر این را نادیده می‌گیرد.
   */
  suspend fun shops(
    query: String = "",
    category: String = "",
    offset: Int = 0,
    limit: Int = PAGE,
    first: List<String> = emptyList(),
    near: GeoPoint? = null,
  ): ApiResult<ShopPage> = ApiResult.of {
    val path = ApiEndpoints.withQuery(
      ApiEndpoints.Market.SHOPS,
      mapOf(
        "q" to query.trim(), "category" to category, "offset" to offset.takeIf { it > 0 }, "limit" to limit,
        "first" to first.take(MAX_FIRST).joinToString(","), "lat" to near?.lat, "lng" to near?.lng,
      ),
    )
    ApiJson.decode<ShopPage>(api.getPublic(path))
  }

  /** دست‌کم دو حرف (همان قاعدهٔ سرور، `query_too_short`) */
  suspend fun search(query: String, near: GeoPoint? = null): ApiResult<MarketSearch> = ApiResult.of {
    val path = ApiEndpoints.withQuery(
      ApiEndpoints.Market.SEARCH,
      mapOf("q" to query.trim(), "lat" to near?.lat, "lng" to near?.lng),
    )
    ApiJson.decode<MarketSearch>(api.getPublic(path))
  }

  /**
   *  خانهٔ «اول کالا». سرورِ پیش از این مسیر ⇒ `Success(null)` تا خانه همان
   *  فهرستِ دکان‌ها بماند، نه پیامِ خطا.
   */
  suspend fun feed(category: String = "", near: GeoPoint? = null): ApiResult<MarketFeed?> = ApiResult.of {
    val path = ApiEndpoints.withQuery(
      ApiEndpoints.Market.PRODUCTS,
      mapOf("category" to category, "lat" to near?.lat, "lng" to near?.lng),
    )
    try {
      ApiJson.decode<MarketFeed>(api.getPublic(path))
    } catch (missing: ApiFailure.NotFound) {
      if (missing.code == ROUTE_MISSING) null else throw missing
    }
  }

  /** «سفارش‌های من» از همهٔ ویترین‌ها — بی بلیت ⇒ ۴۰۱ `verification_required` */
  suspend fun myOrders(): ApiResult<List<MarketOrder>> = ApiResult.of {
    val body = api.getPublic(ApiEndpoints.Market.ORDERS, ticketHeader())
    ApiJson.decodeAt<List<MarketOrder>>(body, "orders").orEmpty()
  }

  /* ------------------------------ ویترینِ یک دکان ------------------------------ */

  suspend fun store(slug: String): ApiResult<StoreHome> = ApiResult.of {
    ApiJson.decode<StoreHome>(api.getPublic(ApiEndpoints.Store.home(slug)))
  }

  suspend fun highlights(slug: String): ApiResult<Highlights> = ApiResult.of {
    ApiJson.decode<Highlights>(api.getPublic(ApiEndpoints.Store.highlights(slug)))
  }

  suspend fun catalog(
    slug: String,
    query: String = "",
    category: String = "",
    cursor: String? = null,
    limit: Int = CATALOG_PAGE,
  ): ApiResult<CatalogPage> = ApiResult.of {
    val path = ApiEndpoints.withQuery(
      ApiEndpoints.Store.products(slug),
      mapOf("q" to query.trim(), "category" to category, "cursor" to cursor, "limit" to limit),
    )
    ApiJson.decode<CatalogPage>(api.getPublic(path))
  }

  /**
   *  همان کالاها با قیمت و موجودیِ امروز (تازه کردنِ سبد، «خرید دوباره»).
   *  نبودن در پاسخ یعنی دیگر در ویترین نیست. بیش از ۱۰۰ ⇒ چند درخواست.
   */
  suspend fun productsByIds(slug: String, ids: Collection<String>): ApiResult<List<StoreProduct>> = ApiResult.of {
    ids.distinct().chunked(MAX_IDS).flatMap { chunk ->
      val path = ApiEndpoints.withQuery(ApiEndpoints.Store.products(slug), mapOf("ids" to chunk.joinToString(",")))
      ApiJson.decode<CatalogPage>(api.getPublic(path)).items
    }
  }

  suspend fun product(slug: String, id: String): ApiResult<ProductDetail> = ApiResult.of {
    ApiJson.decode<ProductDetail>(api.getPublic(ApiEndpoints.Store.product(slug, id)))
  }

  suspend fun slots(slug: String, fulfillment: String): ApiResult<SlotList> = ApiResult.of {
    val path = ApiEndpoints.withQuery(ApiEndpoints.Store.slots(slug), mapOf("fulfillment" to fulfillment))
    ApiJson.decode<SlotList>(api.getPublic(path))
  }

  /** ناحیهٔ پین — بی ذخیره در سرور؛ سقفِ روزانه به‌ازای هر دکان */
  suspend fun locate(slug: String, pin: GeoPoint): ApiResult<ZoneLookup> = ApiResult.of {
    val path = ApiEndpoints.withQuery(ApiEndpoints.Store.delivery(slug), mapOf("lat" to pin.lat, "lng" to pin.lng))
    ApiJson.decode<ZoneLookup>(api.getPublic(path))
  }

  /* ------------------------------ تأییدِ شماره ------------------------------ */

  /**
   *  فرستادنِ کد. با `slug` از راهِ همان ویترین (کار می‌کند حتی وقتی بازارچه
   *  خاموش است)، بی آن از راهِ بازارچه. هر دو یک سطلِ سقفِ درخواست دارند.
   */
  suspend fun startVerification(phone: String, slug: String? = null): ApiResult<CodeSent> = ApiResult.of {
    val path = if (slug == null) ApiEndpoints.Market.VERIFY_START else ApiEndpoints.Store.verifyStart(slug)
    ApiJson.decode<CodeSent>(api.postPublic(path, buildJsonObject { put("phone", phone) }))
  }

  suspend fun checkVerification(phone: String, code: String, slug: String? = null): ApiResult<PhoneTicket> = ApiResult.of {
    val path = if (slug == null) ApiEndpoints.Market.VERIFY_CHECK else ApiEndpoints.Store.verifyCheck(slug)
    ApiJson.decode<PhoneTicket>(api.postPublic(path, buildJsonObject { put("phone", phone); put("code", code) }))
  }

  /* ------------------------------ سفارش ------------------------------ */

  /**
   *  ثبتِ سفارش. `key` همان کلیدِ یکتایی است تا زدنِ دوباره پس از قطعِ شبکه
   *  سفارشِ دوم نسازد (CHK-19)؛ سرور همان سفارش را با `replayed` برمی‌گرداند.
   *  خطاها `ApiFailure` با `detail` اند — `ShopperCheckout.interpret` می‌خواندشان.
   */
  suspend fun checkout(slug: String, body: JsonObject, key: String): ApiResult<PlacedOrder> = ApiResult.of {
    val out = api.postPublic(ApiEndpoints.Store.checkout(slug), body, ticketHeader() + (IDEMPOTENCY to key))
    val order = ApiJson.decodeAt<PublicOrder>(out, "order") ?: throw ApiFailure.InvalidResponse()
    PlacedOrder(order, replayed = ApiJson.bool(out, "replayed"))
  }

  suspend fun track(slug: String, token: String): ApiResult<PublicOrder> = ApiResult.of {
    ApiJson.decodeAt<PublicOrder>(api.getPublic(ApiEndpoints.Store.order(slug, token)), "order")
      ?: throw ApiFailure.InvalidResponse()
  }

  /** لغوِ رایگان تا پیش از قبولِ دکان؛ بعدش ۴۰۹ `cannot_cancel` */
  suspend fun cancel(slug: String, token: String): ApiResult<PublicOrder> = ApiResult.of {
    ApiJson.decodeAt<PublicOrder>(api.postPublic(ApiEndpoints.Store.cancel(slug, token), EMPTY), "order")
      ?: throw ApiFailure.InvalidResponse()
  }

  suspend fun reorder(slug: String, token: String): ApiResult<Reorder> = ApiResult.of {
    ApiJson.decode<Reorder>(api.getPublic(ApiEndpoints.Store.reorder(slug, token)))
  }

  private fun ticketHeader(): Map<String, String> =
    ticket()?.takeIf { it.validAt(now()) }?.let { mapOf(TICKET to it.ticket) }.orEmpty()

  companion object {
    const val PAGE = 24
    const val CATALOG_PAGE = 40
    const val MAX_IDS = 100
    const val MAX_FIRST = 20
    const val TICKET = "X-Store-Ticket"
    const val IDEMPOTENCY = "Idempotency-Key"

    /** کدِ ۴۰۴ ِ «این مسیر روی سرور نیست» — جدا از `market_not_found` و `store_not_found` */
    const val ROUTE_MISSING = "not_found"
    private val EMPTY = JsonObject(emptyMap())
  }
}

/** موقعیتِ درشت برای «نزدیکِ من» و پینِ تحویل */
data class GeoPoint(val lat: Double, val lng: Double)

data class PlacedOrder(val order: PublicOrder, val replayed: Boolean)
