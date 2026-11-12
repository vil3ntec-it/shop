package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.market.PublicRefund
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

  /** ۴۰۹ ِ `promo_code_rejected` (MKT-01): وضعیتِ کد (`unknown`، `inactive`، `used_up`، `no_effect`، `limit_reached`) */
  fun promoStatus(): String? = ((data?.get("promo") as? JsonObject)?.get("status") as? JsonPrimitive)?.contentOrNull

  /**
   *  S41 «ادغام یا سفارشِ دوم؟»: ۴۰۹ ِ `duplicate_order` با `ask: track_or_second` ⇒ شماره و توکنِ پیگیریِ همان
   *  سفارشِ پیشین (`lib/order-risk.js`)؛ بی توکن یا بی پرسش ⇒ null (فقط پیامِ سرور، همان رفتارِ پیشین)
   */
  fun duplicate(): Pair<Long, String>? {
    if (code != DUPLICATE_ORDER) return null
    val d = data ?: return null
    if ((d["ask"] as? JsonPrimitive)?.contentOrNull != "track_or_second") return null
    val token = (d["token"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val no = (d["orderNo"] as? JsonPrimitive)?.longOrNull ?: return null
    return no to token
  }

  override fun toString(): String = "ShopperError($status, $code)"

  companion object {
    const val NETWORK = "network"
    const val BAD_RESPONSE = "bad_response"
    const val STORE_MOVED = "store_moved"
    const val PRICE_CHANGED = "price_changed"
    const val PROMO_REJECTED = "promo_code_rejected"
    const val DUPLICATE_ORDER = "duplicate_order"
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
    val features = (root["features"] as? JsonObject)?.let { decodeOrNull(StoreFeatures.serializer(), it) } ?: StoreFeatures()
    val recent = (root["recent"] as? JsonPrimitive)?.booleanOrNull == true
    StorePage(shop, delivery, flag(root, "verification"), flag(root, "market"), features, recent)
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

  /**
   *  CHK-24: «دستورِ تحویل» تا پیش از حرکت — [body] از [PrefsRules.body] با نسخهٔ دیده‌شده (کهنه ⇒ ۴۰۹ ِ `version_conflict`؛
   *  همان مقدار ⇒ `unchanged`). ⛔ بدنه `body.toString()` است (متنِ خودِ شیء، بی تنظیمِ `explicitNulls` ِ [ShopperJson]) تا
   *  `cashTenderMinor: null` (برداشتنِ اسکناس) حتماً برود.
   */
  fun editDelivery(slug: String, token: String, body: JsonObject): ShopperResult<PrefsSaved> =
    call("POST", base(slug) + "/orders/" + ShopperUrl.encode(token) + "/delivery", body.toString()) { root ->
      val o = root["order"] as? JsonObject ?: return@call null
      PrefsSaved(o["deliveryPrefs"]?.let { decodeOrNull(CustomerDeliveryPrefs.serializer(), it) }, (root["unchanged"] as? JsonPrimitive)?.booleanOrNull == true)
    }

  /** پیگیری با کلیدهای پرچم‌دارِ همان پاسخ (`review`، `chat`، `deliveryPrefs`) که در قراردادِ بازار نیستند */
  fun trackPage(slug: String, token: String): ShopperResult<TrackPage> =
    call("GET", base(slug) + "/orders/" + ShopperUrl.encode(token), null) { root ->
      val o = root["order"] as? JsonObject ?: return@call null
      val order = decodeOrNull(PublicOrder.serializer(), o) ?: return@call null
      //  NTF-08: `refund` جدا ⇒ شکلِ نادرستش فقط کارتِ بازپرداخت را می‌برد، نه نظر و گفت‌وگو را
      val refund = o["refund"]?.let { decodeOrNull(PublicRefund.serializer(), it) }
      TrackPage(order, (decodeOrNull(OrderExtras.serializer(), o) ?: OrderExtras()).copy(refund = refund))
    }

  /** WEB-12: «خریدِ دوباره» — قلم‌های همان سفارش با قیمت و موجودیِ امروز (فقط خواندن) */
  fun reorder(slug: String, token: String): ShopperResult<ReorderPage> =
    get(base(slug) + "/orders/" + ShopperUrl.encode(token) + "/reorder", ReorderPage.serializer())

  /** QR/پیوندِ کمپین (MKT-10/11): `GET /store/go/<کد>` با JSON ⇒ slug ِ امروزِ دکان و کدِ کمپینِ زنده */
  fun campaign(code: String): ShopperResult<CampaignTarget> = call("GET", "/store/go/" + ShopperUrl.encode(code), null) { root ->
    val st = root["store"] as? JsonObject ?: return@call null
    val slug = (st["slug"] as? JsonPrimitive)?.contentOrNull ?: return@call null
    CampaignTarget(slug, (root["campaign"] as? JsonPrimitive)?.contentOrNull)
  }

  // ── نظر و امتیاز (NTF-09، DLV-18) — اجازه همان توکنِ پیگیری ──

  fun review(slug: String, token: String, stars: Int, comment: String): ShopperResult<ReviewSubmitted> =
    call("POST", base(slug) + "/orders/" + ShopperUrl.encode(token) + "/review", json(
      "stars" to JsonPrimitive(stars), "comment" to JsonPrimitive(ReviewRules.cleanComment(comment)),
    )) { decodeOrNull(ReviewSubmitted.serializer(), it) }

  fun rating(slug: String, token: String): ShopperResult<RatingState> =
    get(base(slug) + "/orders/" + ShopperUrl.encode(token) + "/rating", RatingState.serializer())

  fun rate(slug: String, token: String, stars: Int, tags: List<String>): ShopperResult<RatingSubmitted> =
    call("POST", base(slug) + "/orders/" + ShopperUrl.encode(token) + "/rating", json(
      "stars" to JsonPrimitive(stars), "tags" to JsonArray(tags.map { JsonPrimitive(it) }),
    )) { decodeOrNull(RatingSubmitted.serializer(), it) }

  /** نظرهای تأییدشدهٔ دکان (۴۰۴ ⇒ پرچم خاموش) */
  fun shopReviews(slug: String): ShopperResult<ShopReviews> = get(base(slug) + "/reviews", ShopReviews.serializer())

  // ── تخفیف (MKT-01/02/09) ──

  /** تخفیف‌های خودکارِ زنده و «کد پذیرفته می‌شود؟» (۴۰۴ ِ `promos_disabled` ⇒ این دکان تخفیفِ ویترین ندارد) */
  fun promos(slug: String): ShopperResult<PromoList> = get(base(slug) + "/promos", PromoList.serializer())

  /** پیش‌نمایشِ همان حسابِ ثبت (بی ثبت و بی مصرف)؛ [ticket] فقط وقتی دکان سدِ «اولین خرید» دارد خوانده می‌شود */
  fun promoQuote(slug: String, lines: List<PromoQuoteLine>, code: String, deviceId: String?, ticket: String?): ShopperResult<PromoQuote> {
    val body = json(
      "lines" to JsonArray(lines.map { JsonObject(mapOf("productId" to JsonPrimitive(it.productId), "qtyMilli" to JsonPrimitive(it.qtyMilli))) }),
      *listOfNotNull(code.takeIf { it.isNotBlank() }?.let { "promoCode" to JsonPrimitive(it) }, deviceId?.let { "deviceId" to JsonPrimitive(it) }).toTypedArray(),
    )
    return call("POST", base(slug) + "/promos/quote", body, ticketHeader(ticket)) { decodeOrNull(PromoQuote.serializer(), it) }
  }

  // ── نشانی‌های حسابِ مشتری (CHK-09) — نشستِ `X-Store-Session`؛ هر خواندن و نوشتن به همان `customer_id` بسته است ──
  //  نشست از [signIn] (بلیتِ تازهٔ کدِ واقعیِ سرور؛ پیامکِ تولید D-09 است).

  fun addresses(slug: String, session: String): ShopperResult<List<SavedAddress>> =
    call("GET", base(slug) + "/account/addresses", null, sessionHeader(session)) { decodeOrNull(AddressList.serializer(), it)?.addresses }

  fun addAddress(slug: String, session: String, input: AddressInput): ShopperResult<SavedAddress> =
    call("POST", base(slug) + "/account/addresses", ShopperJson.encodeToString(AddressInput.serializer(), input), sessionHeader(session)) { address(it) }

  fun updateAddress(slug: String, session: String, id: String, input: AddressInput): ShopperResult<SavedAddress> =
    call("PUT", base(slug) + "/account/addresses/" + ShopperUrl.encode(id), ShopperJson.encodeToString(AddressInput.serializer(), input), sessionHeader(session)) { address(it) }

  fun removeAddress(slug: String, session: String, id: String): ShopperResult<Boolean> =
    call("DELETE", base(slug) + "/account/addresses/" + ShopperUrl.encode(id), null, sessionHeader(session)) { root ->
      (root["deleted"] as? JsonPrimitive)?.booleanOrNull
    }

  // ── حسابِ مشتری: ورود، سبد، ♥ و «فهرستِ خرید» (CHK-01/03، WEB-11؛ [AccountRules]) — همان درهای `acct.js`/`catalog.js` ِ وب ──
  //  ⛔ ورود فقط با بلیتِ تازهٔ کدِ واقعیِ سرور؛ سرورِ بی راهِ پیامک کد نمی‌فرستد (`verification_unavailable`، D-09).

  fun signIn(slug: String, ticket: String, name: String): ShopperResult<AccountSession> =
    call("POST", base(slug) + "/account", obj("name" to name.trim().take(80)), ticketHeader(ticket)) { decodeOrNull(AccountSession.serializer(), it) }

  fun signOut(slug: String, session: String): ShopperResult<Boolean> =
    call("POST", base(slug) + "/account/logout", "{}", sessionHeader(session)) { true }

  fun accountCart(slug: String, session: String): ShopperResult<AccountCart> =
    call("GET", base(slug) + "/account/cart", null, sessionHeader(session)) { accountCartOf(it) }

  fun putAccountCart(slug: String, session: String, version: Long, items: List<AccountItem>): ShopperResult<AccountCart> =
    call("PUT", base(slug) + "/account/cart", write(version, items), sessionHeader(session)) { accountCartOf(it) }

  fun mergeAccountCart(slug: String, session: String, items: List<AccountItem>): ShopperResult<AccountCart> =
    call("POST", base(slug) + "/account/cart/merge", write(null, items), sessionHeader(session)) { accountCartOf(it) }

  fun favorites(slug: String, session: String): ShopperResult<FavoritesPage> =
    call("GET", base(slug) + "/account/favorites", null, sessionHeader(session)) { decodeOrNull(FavoritesPage.serializer(), it) }

  fun setFavorite(slug: String, session: String, productId: String, on: Boolean): ShopperResult<Boolean> =
    call(if (on) "PUT" else "DELETE", base(slug) + "/account/favorites/" + ShopperUrl.encode(productId), if (on) "{}" else null, sessionHeader(session)) { true }

  fun shoppingList(slug: String, session: String): ShopperResult<AccountList> =
    call("GET", base(slug) + "/account/shopping-list", null, sessionHeader(session)) { accountListOf(it) }

  fun putShoppingList(slug: String, session: String, version: Long, items: List<ListItem>): ShopperResult<AccountList> =
    call("PUT", base(slug) + "/account/shopping-list", write(version, items.map { AccountItem(it.id, it.q) }), sessionHeader(session)) { accountListOf(it) }

  fun mergeShoppingList(slug: String, session: String, items: List<ListItem>): ShopperResult<AccountList> =
    call("POST", base(slug) + "/account/shopping-list/merge", write(null, items.map { AccountItem(it.id, it.q) }), sessionHeader(session)) { accountListOf(it) }

  // ── گفت‌وگو با دکان (CHAT-02/06) — هویت همان شمارهٔ تأییدشده (`X-Store-Ticket`، مثلِ `store/chat.js` ِ وب) ──

  fun chats(slug: String, ticket: String): ShopperResult<List<ChatConversation>> =
    call("GET", chatBase(slug) + "/conversations", null, ticketHeader(ticket)) { root ->
      (root["conversations"] as? JsonArray)?.mapNotNull { decodeOrNull(ChatConversation.serializer(), it) }
    }

  /** گفت‌وگوی همین سفارش ([orderNo]) یا گفت‌وگوی عمومی (null)؛ همان رشته اگر هست (دورِ تازهٔ عمومیِ بسته با سرور) */
  fun chatOpen(slug: String, ticket: String, orderNo: Long?, name: String): ShopperResult<ChatConversation> =
    when (val r = chatStart(slug, ticket, orderNo, name)) {
      is ShopperResult.Ok -> ShopperResult.Ok(r.value.conversation)
      is ShopperResult.Fail -> r
    }

  /** همان گشودن با سقف‌های سرور (`limits`، CHAT-05/07)؛ `limits` ِ نیامده یا نادرست ⇒ پیش‌فرض (بی دکمهٔ صدا) */
  fun chatStart(slug: String, ticket: String, orderNo: Long?, name: String): ShopperResult<ChatOpened> {
    val body = json(*listOfNotNull(orderNo?.let { "orderNo" to JsonPrimitive(it) }, name.trim().takeIf { it.isNotEmpty() }?.let { "name" to JsonPrimitive(it.take(60)) }).toTypedArray())
    return call("POST", chatBase(slug) + "/conversations", body, ticketHeader(ticket)) { root ->
      val conv = root["conversation"]?.let { decodeOrNull(ChatConversation.serializer(), it) } ?: return@call null
      ChatOpened(conv, root["limits"]?.let { decodeOrNull(ChatOpenLimits.serializer(), it) } ?: ChatOpenLimits())
    }
  }

  /** تغییرها پس از [after] (rev)؛ [waitMs] > ۰ ⇒ long-poll ِ سرور (حداکثر ۲۵ ثانیه) */
  fun chatChanges(slug: String, ticket: String, convId: String, after: Long, waitMs: Int = 0): ShopperResult<ChatChanges> =
    call("GET", chatBase(slug) + "/conversations/" + ShopperUrl.encode(convId) + "/messages" + ShopperUrl.query(
      "after" to after.toString(), "wait" to waitMs.takeIf { it > 0 }?.toString(),
    ), null, ticketHeader(ticket)) { decodeOrNull(ChatChanges.serializer(), it) }

  /** ارسال با `clientId` ِ صف: تکرار ⇒ همان پیام (`replayed`)، نه پیامِ دوم */
  fun chatSend(slug: String, ticket: String, item: ChatOutItem): ShopperResult<ChatSent> =
    call("POST", chatBase(slug) + "/conversations/" + ShopperUrl.encode(item.convId) + "/messages", ChatOutbox.body(item), ticketHeader(ticket)) {
      decodeOrNull(ChatSent.serializer(), it)
    }

  /** بارگذاریِ بایت‌های عکس (نوع را سرور از بایت می‌شناسد و EXIF را پاک می‌کند)؛ تکرارِ همان بایت ⇒ همان رسانه */
  fun chatUpload(slug: String, ticket: String, convId: String, bytes: ByteArray, thumb: Boolean): ShopperResult<ChatUpload> =
    call(
      "POST", chatBase(slug) + "/conversations/" + ShopperUrl.encode(convId) + "/media" + ShopperUrl.query("role" to if (thumb) "thumb" else "main"),
      null, ticketHeader(ticket) + ("Content-Type" to (ShopperPhoto.mimeOf(bytes) ?: "application/octet-stream")), bytes = bytes,
    ) { decodeOrNull(ChatUpload.serializer(), it) }

  /** بایت‌های یک رسانهٔ همین گفت‌وگو (عکس با بلیت خوانده می‌شود، مثلِ وب) */
  fun chatMedia(slug: String, ticket: String, convId: String, mediaId: String): ShopperResult<ByteArray> {
    val res = try {
      http.send(ShopperRequest("GET", chatBase(slug) + "/conversations/" + ShopperUrl.encode(convId) + "/media/" + ShopperUrl.encode(mediaId),
        mapOf("Accept-Language" to lang()) + ticketHeader(ticket), binary = true))
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      return ShopperResult.Fail(ShopperError(0, ShopperError.NETWORK))
    }
    val bytes = res.bytes
    return if (res.status == 200 && bytes != null && bytes.isNotEmpty()) ShopperResult.Ok(bytes) else ShopperResult.Fail(ShopperError(res.status, ShopperError.BAD_RESPONSE))
  }

  /** «رسید» و «خواندم» تا [readSeq] (یکنواخت؛ سرور هرگز پایین نمی‌آورد) */
  fun chatReceipts(slug: String, ticket: String, convId: String, deliveredSeq: Long, readSeq: Long): ShopperResult<ChatConversation> =
    call("POST", chatBase(slug) + "/conversations/" + ShopperUrl.encode(convId) + "/receipts", json(
      "deliveredSeq" to JsonPrimitive(deliveredSeq), "readSeq" to JsonPrimitive(readSeq),
    ), ticketHeader(ticket)) { root -> root["conversation"]?.let { decodeOrNull(ChatConversation.serializer(), it) } }

  /**
   *  «حذف برای من» (فقط نمای همین مشتری؛ پیش‌نمایش و شمارِ نخوانده هم با سرور) یا «حذف برای همه» (سنگِ قبر) — همان
   *  `DELETE …/messages/:id?for=` ِ وب (CHAT-08). پاسخ همان پیام با `rev` ِ تازه (`hidden` یا `deleted`).
   */
  fun chatDelete(slug: String, ticket: String, convId: String, messageId: String, scope: ChatDeleteScope): ShopperResult<ChatMessage> =
    call("DELETE", chatBase(slug) + "/conversations/" + ShopperUrl.encode(convId) + "/messages/" + ShopperUrl.encode(messageId) +
      ShopperUrl.query("for" to scope.wire), null, ticketHeader(ticket)) { root -> root["message"]?.let { decodeOrNull(ChatMessage.serializer(), it) } }

  /** ویرایشِ پیامِ خودم (CHAT-14) با `POST …/edit` (همان PATCH ِ سرور؛ HttpURLConnection PATCH نمی‌فرستد) ⇒ همان پیام با `rev` ِ تازه */
  fun chatEdit(slug: String, ticket: String, convId: String, messageId: String, text: String): ShopperResult<ChatMessage> =
    call("POST", chatBase(slug) + "/conversations/" + ShopperUrl.encode(convId) + "/messages/" + ShopperUrl.encode(messageId) + "/edit",
      json("body" to JsonPrimitive(text)), ticketHeader(ticket)) { root -> root["message"]?.let { decodeOrNull(ChatMessage.serializer(), it) } }

  /** سنجاق/برداشتنِ سنجاقِ خودم (فقط برای خودم؛ سقفِ ۵ ⇒ ۴۰۹ `pin_limit`) ⇒ گفت‌وگو با `pinned` ِ تازه */
  fun chatPin(slug: String, ticket: String, convId: String, messageId: String, on: Boolean): ShopperResult<ChatConversation> =
    call(if (on) "PUT" else "DELETE", chatBase(slug) + "/conversations/" + ShopperUrl.encode(convId) + "/pins/" + ShopperUrl.encode(messageId),
      null, ticketHeader(ticket)) { root -> root["conversation"]?.let { decodeOrNull(ChatConversation.serializer(), it) } }

  /** جست‌وجو در پیام‌های همین دکان و همین شماره (پاک‌شده و «حذف برای من» بیرون؛ کوتاه‌تر از ۲ ⇒ ۴۰۰ `query_too_short`) */
  fun chatSearch(slug: String, ticket: String, q: String, limit: Int = ChatActs.SEARCH_LIMIT): ShopperResult<List<ChatHit>> =
    call("GET", chatBase(slug) + "/search" + ShopperUrl.query("q" to q, "limit" to limit.toString()), null, ticketHeader(ticket)) { root ->
      (root["results"] as? JsonArray)?.mapNotNull { decodeOrNull(ChatHit.serializer(), it) }?.filter { it.id.isNotEmpty() && it.conversationId.isNotEmpty() }
    }

  /** «مشکل دارم»: رشتهٔ سفارشِ بسته تا مهلتِ سرور دوباره باز می‌شود (پس از آن ۴۰۹ ِ `issue_window_passed`) */
  fun chatIssue(slug: String, ticket: String, convId: String): ShopperResult<ChatConversation> =
    call("POST", chatBase(slug) + "/conversations/" + ShopperUrl.encode(convId) + "/issue", "{}", ticketHeader(ticket)) { root ->
      root["conversation"]?.let { decodeOrNull(ChatConversation.serializer(), it) }
    }

  /** نشانیِ عکس (نسبت به ریشهٔ سرور): کارت ⇒ `thumb`، برگهٔ کالا ⇒ `photo`؛ `?v=` همان `photoVersion` (IMG-12). */
  fun photoPath(slug: String, productId: String, version: String?, thumb: Boolean): String =
    base(slug) + "/products/" + ShopperUrl.encode(productId) + (if (thumb) "/thumb" else "/photo") + ShopperUrl.query("v" to version)

  // ── درون ──

  private fun base(slug: String) = "/api/store/" + ShopperUrl.encode(slug)

  private fun chatBase(slug: String) = base(slug) + "/chat"

  private fun ticketHeader(ticket: String?): Map<String, String> = if (ticket.isNullOrEmpty()) emptyMap() else mapOf("X-Store-Ticket" to ticket)

  private fun sessionHeader(session: String): Map<String, String> = mapOf("X-Store-Session" to session)

  private fun address(root: JsonObject): SavedAddress? = root["address"]?.let { decodeOrNull(SavedAddress.serializer(), it) }

  private fun accountCartOf(root: JsonObject): AccountCart? = root["cart"]?.let { decodeOrNull(AccountCart.serializer(), it) }

  private fun accountListOf(root: JsonObject): AccountList? = root["list"]?.let { decodeOrNull(AccountList.serializer(), it) }

  private fun write(version: Long?, items: List<AccountItem>): String =
    ShopperJson.encodeToString(AccountWrite.serializer(), AccountWrite(version, items))

  private fun json(vararg kv: Pair<String, JsonElement>): String = ShopperJson.encodeToString(JsonObject.serializer(), JsonObject(kv.toMap()))

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
    bytes: ByteArray? = null,
    read: (JsonObject) -> T?,
  ): ShopperResult<T> {
    val headers = buildMap {
      put("Accept", "application/json")
      put("Accept-Language", lang())
      if (body != null) put("Content-Type", "application/json")
      putAll(extra)
    }
    val res = try {
      http.send(ShopperRequest(method, path, headers, body, bytes))
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

