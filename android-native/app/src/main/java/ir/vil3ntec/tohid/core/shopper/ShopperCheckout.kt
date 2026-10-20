package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.text.LocaleFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** فرمِ ثبتِ سفارش — همان میدان‌های ویترینِ وب (CHK-01..12). */
data class CheckoutForm(
  val fulfillment: String = "",
  val name: String = "",
  val phone: String = "",
  val addressText: String = "",
  val landmark: String = "",
  val pin: GeoPoint? = null,
  /** ناحیه‌ای که خریدار بی پین برگزیده (پین باشد، پین برنده است) */
  val zoneId: String = "",
  /** آغازِ بازهٔ برگزیده؛ null ⇒ «هرچه زودتر» */
  val slotStartsAt: Long? = null,
  val note: String = "",
  val callBeforeArrival: Boolean = false,
  val leaveWith: String = "",
)

/** چرا هنوز نمی‌شود ثبت کرد — صفحه کنارِ همان میدان می‌گوید. */
enum class FormProblem {
  CART_EMPTY, CART_UNAVAILABLE, TOO_MANY_LINES, QTY_OVER_LIMIT, FULFILLMENT_REQUIRED, NAME_REQUIRED, PHONE_INVALID, PHONE_NOT_VERIFIED,
  ADDRESS_REQUIRED, ZONE_REQUIRED, OUT_OF_ZONE, SLOT_REQUIRED, SLOT_UNAVAILABLE, BELOW_MIN_ORDER,
}

/**
 *  جمعِ پیش از ثبت. `feeKnown = false` یعنی ارسالِ ناحیه‌دار هنوز ناحیه ندارد
 *  و هزینه را نمی‌دانیم — پس «جمعِ دیده‌شده» هم هنوز نیست.
 */
data class CheckoutTotals(
  val subtotalMinor: Long,
  val deliveryFeeMinor: Long,
  val minOrderMinor: Long,
  val feeKnown: Boolean = true,
  /** ارسال به‌خاطرِ «رایگان از … به بالا» ِ ناحیه صفر شد */
  val freeDelivery: Boolean = false,
) {
  val totalMinor: Long get() = subtotalMinor + deliveryFeeMinor
  val belowMin: Boolean get() = subtotalMinor < minOrderMinor
}

/** خطای ثبت، خوانده‌شده از `ApiFailure.detail` — هر کدام راهِ درست را هم دارد. */
sealed class CheckoutIssue {
  /** S135: قیمت عوض شد؛ سبد تازه شد و بی پذیرشِ دوباره ثبت نمی‌شود */
  data class PriceChanged(val quote: Quote) : CheckoutIssue()

  data class Unavailable(val productIds: List<String>) : CheckoutIssue()

  data class OverLimit(val productId: String, val maxQtyMilli: Long) : CheckoutIssue()

  data class BelowMin(val minOrderMinor: Long) : CheckoutIssue()

  /** بلیت در سرور باطل است (۴۰۱) ⇒ بلیتِ این دستگاه پاک و دوباره تأیید */
  object NeedsVerification : CheckoutIssue()

  /** شمارهٔ فرم همان شمارهٔ بلیت نیست (۴۰۳) ⇒ همین شماره را تأیید کن؛ بلیت می‌ماند */
  object PhoneMismatch : CheckoutIssue()

  data class OutOfZone(val zones: List<DeliveryZone>, val pickup: Boolean) : CheckoutIssue()

  data class ZoneNeeded(val zones: List<DeliveryZone>) : CheckoutIssue()

  /** بازه لازم است، دیگر نیست یا پر شد — با بازه‌های تازه */
  data class SlotNeeded(val slots: List<DeliverySlot>, val full: Boolean) : CheckoutIssue()

  /** روشِ تحویل دیگر در این ویترین نیست ⇒ پروفایل دوباره خوانده شود */
  object FulfillmentGone : CheckoutIssue()

  data class Failed(val failure: ApiFailure) : CheckoutIssue()
}

/**
 *  قاعده‌های خالصِ ثبتِ سفارش — هم‌زادِ `parseCheckout`/`pricer`/`forCheckout` ِ
 *  سرور، فقط برای این‌که خطا پیش از فرستادن کنارِ همان میدان دیده شود. سرور
 *  داورِ نهایی است.
 */
object ShopperCheckout {
  const val DELIVERY = "delivery"
  const val PICKUP = "pickup"
  const val PAYMENT_COD = "cod"

  /** همان سقف‌های سرور (`text(…, n)`) */
  const val MAX_NAME = 80
  const val MAX_ADDRESS = 300
  const val MAX_LANDMARK = 200
  const val MAX_NOTE = 500
  const val MAX_LEAVE_WITH = 80

  /** روشِ پیش‌فرض: ارسال اگر هست، وگرنه دریافت از دکان (همان وب) */
  fun defaultFulfillment(store: StoreProfile): String = when {
    store.fulfillment.delivery -> DELIVERY
    store.fulfillment.pickup -> PICKUP
    else -> ""
  }

  fun offers(store: StoreProfile, fulfillment: String): Boolean = when (fulfillment) {
    DELIVERY -> store.fulfillment.delivery
    PICKUP -> store.fulfillment.pickup
    else -> false
  }

  /** ناحیهٔ این سفارش: پین ⇒ پاسخِ «کدام ناحیه»؛ بی پین ⇒ ناحیهٔ برگزیده */
  fun zoneOf(store: StoreProfile, form: CheckoutForm, located: ZoneLookup?): DeliveryZone? {
    if (form.fulfillment != DELIVERY || !store.delivery.zoned) return null
    if (form.pin != null) return located?.takeIf { it.inZone }?.zone
    return store.delivery.zones.firstOrNull { it.id == form.zoneId }
  }

  fun totals(store: StoreProfile, cart: ShopCart?, form: CheckoutForm, zone: DeliveryZone?): CheckoutTotals {
    val subtotal = cart?.subtotalMinor ?: 0
    if (form.fulfillment != DELIVERY) return CheckoutTotals(subtotal, 0, store.minOrderMinor)
    if (!store.delivery.zoned) return CheckoutTotals(subtotal, store.deliveryFeeMinor, store.minOrderMinor)
    if (zone == null) return CheckoutTotals(subtotal, 0, store.minOrderMinor, feeKnown = false)
    val free = zone.freeOverMinor != null && subtotal >= zone.freeOverMinor
    return CheckoutTotals(subtotal, if (free) 0 else zone.feeMinor, zone.minOrderMinor, freeDelivery = free && zone.feeMinor > 0)
  }

  /** بازه لازم است وقتی این روش بازه دارد و «هرچه زودتر» بسته است */
  fun slotRequired(store: StoreProfile, form: CheckoutForm, slots: SlotList?): Boolean {
    val scheduled = slots?.scheduling ?: store.delivery.scheduling.of(form.fulfillment)
    val asap = slots?.allowAsap ?: store.delivery.allowAsap
    return scheduled && !asap
  }

  fun problems(
    store: StoreProfile,
    cart: ShopCart?,
    form: CheckoutForm,
    tickets: ShopperTickets,
    located: ZoneLookup?,
    slots: SlotList?,
  ): List<FormProblem> {
    val out = mutableListOf<FormProblem>()
    if (cart == null || cart.lines.isEmpty()) out += FormProblem.CART_EMPTY
    else if (cart.hasUnavailable) out += FormProblem.CART_UNAVAILABLE
    if (cart != null && cart.lines.size > MAX_CHECKOUT_LINES) out += FormProblem.TOO_MANY_LINES
    if (cart != null && cart.lines.any { it.qtyMilli > qtyCap(it.maxQtyMilli) }) out += FormProblem.QTY_OVER_LIMIT
    if (!offers(store, form.fulfillment)) out += FormProblem.FULFILLMENT_REQUIRED
    if (form.name.isBlank()) out += FormProblem.NAME_REQUIRED
    val phone = LocaleFormat.normalizePhone(form.phone)
    if (phone == null) out += FormProblem.PHONE_INVALID
    else if (!tickets.covers(phone)) out += FormProblem.PHONE_NOT_VERIFIED
    if (form.fulfillment == DELIVERY) {
      if (form.pin == null && form.addressText.isBlank() && form.landmark.isBlank()) out += FormProblem.ADDRESS_REQUIRED
      if (store.delivery.zoned) {
        when {
          form.pin != null && located != null && !located.inZone -> out += FormProblem.OUT_OF_ZONE
          zoneOf(store, form, located) == null -> out += FormProblem.ZONE_REQUIRED
        }
      }
    }
    if (form.slotStartsAt == null) {
      if (slotRequired(store, form, slots)) out += FormProblem.SLOT_REQUIRED
    } else if (slots != null && slots.slots.none { it.startsAt == form.slotStartsAt && it.available }) {
      out += FormProblem.SLOT_UNAVAILABLE
    }
    val totals = totals(store, cart, form, zoneOf(store, form, located))
    if (cart != null && cart.lines.isNotEmpty() && totals.feeKnown && totals.belowMin) out += FormProblem.BELOW_MIN_ORDER
    return out
  }

  /**
   *  بدنهٔ `POST /store/:slug/checkout`. ⛔ شماره همان E.164 ِ تأییدشده است
   *  (سرور برابری را می‌سنجد، `phone_not_verified`)؛ `expectedTotalMinor` همان
   *  جمعی است که خریدار دید (CHK-17).
   */
  fun body(cart: ShopCart, form: CheckoutForm, totals: CheckoutTotals): JsonObject = buildJsonObject {
    put("fulfillment", form.fulfillment)
    put("paymentMethod", PAYMENT_COD)
    put("customer", buildJsonObject {
      put("name", form.name.trim().take(MAX_NAME))
      put("phone", LocaleFormat.normalizePhone(form.phone) ?: form.phone.trim())
    })
    put("lines", buildJsonArray {
      cart.lines.forEach { l -> add(buildJsonObject { put("productId", l.productId); put("qtyMilli", l.qtyMilli) }) }
    })
    put("note", form.note.trim().take(MAX_NOTE))
    if (totals.feeKnown) put("expectedTotalMinor", totals.totalMinor)
    if (form.fulfillment == DELIVERY) {
      put("address", buildJsonObject {
        put("text", form.addressText.trim().take(MAX_ADDRESS))
        put("landmark", form.landmark.trim().take(MAX_LANDMARK))
        form.pin?.let { put("lat", it.lat); put("lng", it.lng) }
        if (form.pin == null && form.zoneId.isNotBlank()) put("zoneId", form.zoneId)
      })
      put("delivery", buildJsonObject {
        put("callBeforeArrival", form.callBeforeArrival)
        put("leaveWith", form.leaveWith.trim().take(MAX_LEAVE_WITH))
      })
    }
    form.slotStartsAt?.let { put("slotStartsAt", it) }
  }

  /** خطای سرور ⇒ مسئلهٔ قابلِ نمایش با دادهٔ همراهش */
  fun interpret(failure: ApiFailure): CheckoutIssue {
    val d = failure.detail ?: JsonObject(emptyMap())
    return when (failure.code) {
      "price_changed" -> decode<Quote>(d["quote"])?.let { CheckoutIssue.PriceChanged(it) }
      "product_unavailable" -> decode(d["unavailable"], ListSerializer(String.serializer()))?.let { CheckoutIssue.Unavailable(it) }
      "qty_over_limit" -> {
        val id = ApiJson.text(d, "productId")
        val max = (d["maxQtyMilli"] as? JsonPrimitive)?.content?.toLongOrNull()
        if (id.isNotEmpty() && max != null) CheckoutIssue.OverLimit(id, max) else null
      }
      "below_min_order" -> CheckoutIssue.BelowMin(ApiJson.long(d, "minOrderMinor"))
      "verification_required" -> CheckoutIssue.NeedsVerification
      "phone_not_verified" -> CheckoutIssue.PhoneMismatch
      "out_of_zone" -> CheckoutIssue.OutOfZone(zones(d), ApiJson.bool(d, "pickup"))
      "zone_required", "bad_zone" -> CheckoutIssue.ZoneNeeded(zones(d))
      "slot_required", "slot_unavailable", "slot_full" ->
        CheckoutIssue.SlotNeeded(decode(d["slots"], ListSerializer(DeliverySlot.serializer())).orEmpty(), failure.code == "slot_full")
      "fulfillment_unavailable", "bad_fulfillment" -> CheckoutIssue.FulfillmentGone
      else -> null
    } ?: CheckoutIssue.Failed(failure)
  }

  private fun zones(d: JsonObject): List<DeliveryZone> =
    decode(d["zones"], ListSerializer(DeliveryZone.serializer())).orEmpty()

  private inline fun <reified T> decode(e: JsonElement?): T? =
    e?.takeIf { it is JsonObject }?.let { runCatching { ApiJson.format.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), it) }.getOrNull() }

  private fun <T> decode(e: JsonElement?, s: kotlinx.serialization.KSerializer<T>): T? =
    e?.takeIf { it is JsonArray }?.let { runCatching { ApiJson.format.decodeFromJsonElement(s, it) }.getOrNull() }
}

/**
 *  کلیدِ یکتاییِ ثبت (CHK-19): تا بدنه همان است همان می‌ماند — زدنِ دوباره پس از
 *  قطعِ شبکه یا بسته شدنِ برنامه سفارشِ دوم نمی‌سازد. ثبتِ موفق ⇒ `drop`.
 */
class CheckoutKeys(
  private val storage: ShopperStorage,
  /** ⛔ رمزنگارانه: کلیدِ قابلِ حدس یعنی دیدنِ پاسخِ سفارشِ دیگری با «دوباره‌پخش» */
  private val random: Random = SecureRandom().asKotlinRandom(),
) {
  @Serializable
  private data class Entry(val slug: String, val body: String, val key: String)

  private val serializer = ListSerializer(Entry.serializer())

  @Synchronized
  fun keyFor(slug: String, body: JsonObject): String {
    val json = body.toString()
    val all = storage.load(ShopperStorage.CHECKOUT_KEY, serializer).orEmpty()
    all.firstOrNull { it.slug == slug && it.body == json }?.let { return it.key }
    val key = "app-" + (1..24).map { ABC[random.nextInt(ABC.length)] }.joinToString("")
    storage.save(ShopperStorage.CHECKOUT_KEY, serializer, all.filterNot { it.slug == slug } + Entry(slug, json, key))
    return key
  }

  @Synchronized
  fun drop(slug: String) {
    val all = storage.load(ShopperStorage.CHECKOUT_KEY, serializer).orEmpty()
    storage.save(ShopperStorage.CHECKOUT_KEY, serializer, all.filterNot { it.slug == slug })
  }

  private companion object {
    const val ABC = "abcdefghijklmnopqrstuvwxyz0123456789"
  }
}

/** نام و آخرین نشانیِ همین دستگاه برای سفارشِ بعدی — فقط محلی (همان وب) */
@Serializable
data class SavedContact(val name: String = "", val addressText: String = "", val landmark: String = "")
