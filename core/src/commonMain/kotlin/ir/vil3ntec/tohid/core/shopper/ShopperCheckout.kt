package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicShop
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.sync.v1.Ulid
import kotlinx.serialization.Serializable

/** برگهٔ ثبت، همان کادرهای `server/public/store/checkout.js` (بی پین و بی کادرهای پرچم‌دارِ CHK-08 در مرحلهٔ ۱). */
@Serializable
data class CheckoutForm(
  /** `delivery` یا `pickup`؛ خالی ⇒ پیش‌فرضِ دکان ([CheckoutRules.fulfillmentOf]) */
  val fulfillment: String = "",
  val name: String = "",
  val phone: String = "",
  val addressText: String = "",
  val landmark: String = "",
  val area: String = "",
  val gateColor: String = "",
  val zoneId: String? = null,
  /** بازهٔ برگزیده (CHK-12)؛ null ⇒ «هرچه زودتر» */
  val slotStartsAt: Long? = null,
  val note: String = "",
  val callBeforeArrival: Boolean = false,
  val leaveWith: String = "",
)

/** چرا «ثبت» هنوز بسته است — هر کدام یک پیامِ روشن در [ShopperText.problem] دارد. */
enum class CheckoutProblem { CLOSED, EMPTY, UNAVAILABLE_LINES, NO_FULFILLMENT, NAME, PHONE, VERIFY, ADDRESS, ZONE, SLOT, BELOW_MIN }

/**
 *  برگهٔ پولِ پیش از ثبت. [deliveryFeeMinor] null ⇒ «پس از گزینشِ ناحیه» (هنوز دانسته نیست). جمعِ نهایی را
 *  سرور می‌گوید؛ این همان برآوردی است که وب نشان می‌دهد و در `expectedTotalMinor` می‌رود.
 */
data class CheckoutSummary(
  val subtotalMinor: Long,
  val deliveryFeeMinor: Long?,
  /** درونِ [deliveryFeeMinor] (همان ریزِ سرور، CHK-06/07) */
  val smallBasketFeeMinor: Long,
  val slotFeeMinor: Long,
  val totalMinor: Long,
  val minOrderMinor: Long,
  /** چقدر تا حداقلِ سفارش مانده (۰ = رسیده یا با هزینهٔ سبدِ کوچک پذیرفته) */
  val shortOfMinMinor: Long,
  /** چقدر تا ارسالِ رایگانِ ناحیه مانده؛ null ⇒ ندارد یا رسیده */
  val freeLeftMinor: Long?,
)

/**
 *  قاعده‌های ثبت — همان وب (`store.js`: `zoneFee`، `feeFor`، `minFor`؛ `checkout.js`: `orderBody`) و سرور
 *  (`storefront-delivery.forCheckout`، `storefront.parseCheckout`). هر قاعده‌ای که این‌جا با سرور فرق کند فقط
 *  پیامِ سرور را به‌جای پیامِ برنامه می‌آورد؛ پول و پذیرش همیشه با سرور است.
 */
object CheckoutRules {
  const val DELIVERY = "delivery"
  const val PICKUP = "pickup"

  /** روش‌های این دکان؛ ارسال اول (پیش‌فرضِ وب) */
  fun fulfillments(shop: PublicShop): List<String> =
    listOfNotNull(DELIVERY.takeIf { shop.fulfillment.delivery }, PICKUP.takeIf { shop.fulfillment.pickup })

  fun fulfillmentOf(shop: PublicShop, form: CheckoutForm): String? {
    val all = fulfillments(shop)
    return form.fulfillment.takeIf { it in all } ?: all.firstOrNull()
  }

  /** «سفارش می‌پذیرد؟» (#467) — فقط وقتی سرور صریح «نه» گفته */
  fun closed(shop: PublicShop): Boolean = shop.ordering?.accepting == false

  /** ناحیه‌های ارسال؛ null ⇒ دکان ناحیه ندارد (هزینه و حداقلِ ثابت) */
  fun zones(page: StorePage): List<DeliveryZone>? =
    page.delivery.zones.takeIf { page.delivery.zoned && it.isNotEmpty() }

  fun zoneOf(page: StorePage, form: CheckoutForm): DeliveryZone? =
    form.zoneId?.let { id -> zones(page)?.firstOrNull { it.id == id } }

  /** هزینهٔ ناحیه: «رایگان از …» فقط هزینهٔ پایه را برمی‌دارد؛ هزینهٔ سبدِ کوچک زیرِ حداقل (CHK-06) */
  fun zoneFee(z: DeliveryZone, subtotalMinor: Long): Long =
    (if (z.freeOverMinor != null && subtotalMinor >= z.freeOverMinor) 0 else z.feeMinor) + smallBasketFee(z, subtotalMinor)

  fun smallBasketFee(z: DeliveryZone, subtotalMinor: Long): Long =
    if (z.smallBasketFeeMinor != null && z.smallBasketFeeMinor > 0 && subtotalMinor < z.minOrderMinor) z.smallBasketFeeMinor else 0

  /** حداقلی که سبد را می‌بندد؛ ناحیهٔ «هزینهٔ سبدِ کوچک» زیرِ حداقل را با هزینه می‌پذیرد */
  fun minFor(page: StorePage, fulfillment: String?, zone: DeliveryZone?): Long = when {
    fulfillment != DELIVERY || zones(page) == null -> page.shop.minOrderMinor
    zone == null -> 0
    zone.smallBasketFeeMinor != null && zone.smallBasketFeeMinor > 0 -> 0
    else -> zone.minOrderMinor
  }

  /** دکان برای این روش زمان‌بندی دارد؟ */
  fun scheduled(page: StorePage, fulfillment: String?): Boolean = when (fulfillment) {
    DELIVERY -> page.delivery.scheduling.delivery
    PICKUP -> page.delivery.scheduling.pickup
    else -> false
  }

  /** بازهٔ برگزیده‌ای که هنوز «هست» است (CHK-12) */
  fun slotOf(slots: SlotsPage?, startsAt: Long?): DeliverySlot? =
    if (slots == null || startsAt == null) null else slots.slots.firstOrNull { it.startsAt == startsAt && it.available }

  /** «هرچه زودتر» بسته است و هنوز بازه‌ای برگزیده نشده؟ (بازه‌ها نیامده ⇒ هنوز دانسته نیست = بسته) */
  fun needsSlot(page: StorePage, fulfillment: String?, slots: SlotsPage?, startsAt: Long?): Boolean =
    scheduled(page, fulfillment) && (slots == null || (!slots.allowAsap && slotOf(slots, startsAt) == null))

  fun summary(page: StorePage, cart: Cart?, form: CheckoutForm, slots: SlotsPage?): CheckoutSummary {
    val f = fulfillmentOf(page.shop, form)
    val sub = CartRules.subtotal(cart)
    val zone = zoneOf(page, form)
    val zoned = zones(page) != null
    val slotFee = if (f == DELIVERY) slotOf(slots, form.slotStartsAt)?.feeMinor ?: 0 else 0
    val fee: Long? = when {
      f != DELIVERY -> 0
      !zoned -> page.shop.deliveryFeeMinor
      zone == null -> null
      else -> zoneFee(zone, sub)
    }
    val small = if (f == DELIVERY && zone != null) smallBasketFee(zone, sub) else 0
    val min = minFor(page, f, zone)
    val freeLeft = if (f == DELIVERY && zone?.freeOverMinor != null && sub < zone.freeOverMinor) zone.freeOverMinor - sub else null
    val feeAll = fee?.plus(slotFee)
    return CheckoutSummary(
      subtotalMinor = sub, deliveryFeeMinor = feeAll, smallBasketFeeMinor = small, slotFeeMinor = slotFee,
      totalMinor = sub + (feeAll ?: 0), minOrderMinor = min, shortOfMinMinor = (min - sub).coerceAtLeast(0), freeLeftMinor = freeLeft,
    )
  }

  /** شمارهٔ نوشته‌شده به قالبِ سرور (`+93…`)؛ نادرست ⇒ null (`LocaleFormat.normalizePhone` ِ همان هسته) */
  fun phoneOf(form: CheckoutForm): String? = LocaleFormat.normalizePhone(form.phone)

  /** بلیتِ همین شماره و هنوز معتبر؟ */
  fun verified(form: CheckoutForm, ticket: PhoneTicket?, now: Long): Boolean {
    val phone = phoneOf(form) ?: return false
    return ticket != null && ticket.phone == phone && ticket.expiresAt > now
  }

  /** همهٔ دلیل‌های بسته بودنِ «ثبت»، به ترتیبِ برگه؛ خالی ⇒ آمادهٔ فرستادن */
  fun problems(page: StorePage, cart: Cart?, form: CheckoutForm, ticket: PhoneTicket?, slots: SlotsPage?, now: Long): List<CheckoutProblem> {
    val out = mutableListOf<CheckoutProblem>()
    if (closed(page.shop)) out += CheckoutProblem.CLOSED
    if (cart == null || cart.lines.isEmpty()) out += CheckoutProblem.EMPTY
    if (CartRules.hasUnavailable(cart)) out += CheckoutProblem.UNAVAILABLE_LINES
    val f = fulfillmentOf(page.shop, form)
    if (f == null) out += CheckoutProblem.NO_FULFILLMENT
    if (form.name.isBlank()) out += CheckoutProblem.NAME
    if (phoneOf(form) == null) out += CheckoutProblem.PHONE
    else if (!verified(form, ticket, now)) out += CheckoutProblem.VERIFY
    if (f == DELIVERY) {
      if (form.addressText.isBlank() && form.landmark.isBlank()) out += CheckoutProblem.ADDRESS
      if (zones(page) != null && zoneOf(page, form) == null) out += CheckoutProblem.ZONE
    }
    if (needsSlot(page, f, slots, form.slotStartsAt)) out += CheckoutProblem.SLOT
    val s = summary(page, cart, form, slots)
    if (cart != null && cart.lines.isNotEmpty() && s.shortOfMinMinor > 0) out += CheckoutProblem.BELOW_MIN
    return out
  }

  /** بدنهٔ `POST /checkout` (همان `orderBody` ِ وب). [phone] همان E.164 ِ بلیت. */
  fun request(page: StorePage, cart: Cart, form: CheckoutForm, phone: String, lang: String, slots: SlotsPage?): CheckoutRequest {
    val f = fulfillmentOf(page.shop, form) ?: DELIVERY
    val s = summary(page, cart, form, slots)
    val slot = slotOf(slots, form.slotStartsAt)
    return CheckoutRequest(
      fulfillment = f,
      customer = CheckoutCustomer(form.name.trim().take(80), phone),
      lines = cart.lines.map { CheckoutLine(it.productId, it.qtyMilli, it.priceMinor) },
      note = form.note.trim().take(500),
      expectedTotalMinor = s.totalMinor,
      lang = lang,
      slotStartsAt = slot?.startsAt,
      address = if (f != DELIVERY) null else CheckoutAddress(
        text = form.addressText.trim().take(300), landmark = form.landmark.trim().take(200),
        area = form.area.trim().take(80), gateColor = form.gateColor.trim().take(40),
        zoneId = if (zones(page) != null) zoneOf(page, form)?.id else null,
      ),
      delivery = if (f != DELIVERY) null else CheckoutDelivery(form.callBeforeArrival, form.leaveWith.trim().take(80)),
    )
  }
}

/** کلیدِ یکتاییِ یک ثبت و همان بدنه‌ای که با آن ساخته شد (`K.keyFor` ِ وب). */
@Serializable
data class KeyMemo(val json: String, val key: String)

/**
 *  CHK-19: کلیدِ یکتایی تا وقتی بدنه همان است می‌ماند ⇒ زدنِ دوباره پس از قطعِ شبکه یا بستنِ برنامه همان سفارش
 *  است (سرور `replayed` می‌گوید). بدنهٔ دیگر ⇒ کلیدِ تازه (وگرنه ۴۰۹ ِ `idempotency_key_reused`). پس از ثبت
 *  کلید فراموش می‌شود. قالب `app-<ULID>` در `KEY_RE` ِ سرور (`[A-Za-z0-9_.:-]{8,128}`) است.
 */
object CheckoutKeys {
  fun json(request: CheckoutRequest): String = ShopperJson.encodeToString(CheckoutRequest.serializer(), request)

  fun keyFor(memo: KeyMemo?, request: CheckoutRequest, fresh: () -> String = { "app-" + Ulid.next() }): KeyMemo {
    val j = json(request)
    return if (memo != null && memo.json == j) memo else KeyMemo(j, fresh())
  }
}
