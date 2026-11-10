package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicOrder
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/*
 *  برنامهٔ خریدِ مشتری، مرحلهٔ ۲ — نظر و امتیاز (NTF-09، DLV-18) و تخفیفِ ویترین (MKT-01/02/09) روی همان درهای امروز:
 *    • نظرِ سفارش: کلیدِ `review` در پیگیری و `POST /:slug/orders/:token/review` (`lib/order-reviews.js`)؛
 *    • امتیاز به پیک: `GET`/`POST /:slug/orders/:token/rating` (`lib/delivery-ratings.js`)؛
 *    • نظرهای تأییدشدهٔ دکان: `GET /:slug/reviews`؛
 *    • تخفیف: `GET /:slug/promos` و `POST /:slug/promos/quote` (`lib/storefront-promos.js`).
 *  اجازه همان توکنِ امضاشدهٔ پیگیری است؛ پرچمِ خاموش ⇒ کلید نیست یا ۴۰۴ ⇒ برنامه آن بخش را نشان نمی‌دهد.
 *  ⛔ پول همیشه از سرور: برنامه تخفیف را حساب نمی‌کند؛ فقط عددِ پیش‌نمایشِ سرور را در جمعِ دیده‌شده می‌گذارد.
 */

@Serializable
data class OrderReview(val stars: Int, val comment: String = "", val status: String = "", val createdAt: Long = 0)

/** کلیدِ `review` ِ پیگیری (پرچمِ `shop.orders.reviews`) */
@Serializable
data class ReviewState(val canReview: Boolean = false, val commentMax: Int = ReviewRules.COMMENT_MAX, val review: OrderReview? = null)

/**
 *  کلیدهای پیگیری که در `PublicOrder` ِ قراردادِ بازار نیستند (پرچم‌دار؛ نبود ⇒ پیش‌فرض)؛ [deliveryPrefs] ⇒ CHK-24 ([PrefsRules]).
 *  [refund] جدا خوانده می‌شود (`ShopperApi.trackPage`) تا شکلِ تازه یا نادرستِ آن نظر و گفت‌وگو را نبرد.
 */
@Serializable
data class OrderExtras(
  val review: ReviewState? = null,
  val chat: Boolean = false,
  val deliveryPrefs: CustomerDeliveryPrefs? = null,
  @Transient val refund: RefundView? = null,
)

/** یک ردیفِ دفترِ بازپرداخت در پیگیری: فقط مبلغ، روش و زمان (سرور دلیل و کننده را نمی‌دهد) */
@Serializable
data class RefundEntry(val amountMinor: Long = 0, val method: String = "", val at: Long = 0)

/**
 *  کلیدِ `refund` ِ پیگیری (NTF-08، پرچمِ خاموشِ `shop.orders.refund_tracking`، `lib/order-refund-view.js`): نبود یا
 *  `null` ⇒ هیچ کارتی. وضعیت `requested` · `in_progress` · `partial` · `full`؛ ناشناخته ⇒ همان «درخواست ثبت شد» ِ وب.
 */
@Serializable
data class RefundView(
  val status: String = "requested",
  val requestedAt: Long? = null,
  val refundedMinor: Long = 0,
  val remainingMinor: Long = 0,
  val entries: List<RefundEntry> = emptyList(),
  val updatedAt: Long? = null,
)

/** کارتِ بازپرداختِ صفحهٔ سفارش — همان `renderRefund` ِ `store/order-changes.js` */
object RefundRules {
  fun note(r: RefundView): String = when (r.status) {
    "in_progress" -> ShopperText.rfInProgress
    "partial" -> ShopperText.rfPartial
    "full" -> ShopperText.rfFull
    else -> ShopperText.rfRequested
  }

  /** بسته شد ⇒ رنگِ «انجام شد»، وگرنه «آگاهی» */
  fun closed(r: RefundView): Boolean = r.status == "partial" || r.status == "full"

  /** یک ردیف: «زمان — روش» و مبلغ */
  fun line(e: RefundEntry): Pair<String, String> =
    (ShopperText.stamp(e.at) + " — " + if (e.method == "cash") ShopperText.rfCash else ShopperText.rfOther) to ShopperText.money(e.amountMinor)
}

class TrackPage(val order: PublicOrder, val extras: OrderExtras)

@Serializable
data class ReviewSubmitted(val review: OrderReview, val replayed: Boolean = false)

@Serializable
data class CourierRating(val rater: String = "customer", val stars: Int, val tags: List<String> = emptyList(), val createdAt: Long = 0)

/** `GET …/rating` (پرچمِ `shop.delivery_ratings`) */
@Serializable
data class RatingState(val canRate: Boolean = false, val rating: CourierRating? = null, val tags: List<String> = emptyList())

@Serializable
data class RatingSubmitted(val rating: CourierRating, val replayed: Boolean = false)

@Serializable
data class PublicReview(val stars: Int, val comment: String = "", val at: Long = 0)

/** `GET /:slug/reviews` — میانگین به دهم (عددِ صحیح) */
@Serializable
data class ShopReviews(val count: Int = 0, val avgTenths: Int? = null, val reviews: List<PublicReview> = emptyList())

object ReviewRules {
  /** همان `COMMENT_MAX` ِ سرور */
  const val COMMENT_MAX = 500
  /** همان `MAX_TAGS` ِ امتیازِ پیک */
  const val MAX_TAGS = 4

  fun validStars(n: Int): Boolean = n in 1..5

  /** همان پاک‌سازیِ سرور (نویسهٔ کنترلی و جهت ⇒ فاصله) و سقف به نویسهٔ یونیکد، نه UTF-16 */
  fun cleanComment(s: String, max: Int = COMMENT_MAX): String {
    val c = s.map { ch -> if (ch < ' ' && ch != '\n' || ch == '\u007F' || ch in '‎'..'‏' || ch in '‪'..'‮' || ch in '⁦'..'⁩') ' ' else ch }
      .joinToString("").trim()
    var cps = 0
    var i = 0
    while (i < c.length && cps < max) {
      i += if (c[i].isHighSurrogate() && i + 1 < c.length) 2 else 1
      cps++
    }
    return c.substring(0, i)
  }

  /** برچسب‌ها: فقط از فهرستِ سرور، یکتا، حداکثر چهار، به ترتیبِ همان فهرست (همان `parse` ِ سرور) */
  fun cleanTags(picked: Collection<String>, allowed: List<String>): List<String> =
    allowed.filter { it in picked }.take(MAX_TAGS)

  fun toggleTag(picked: List<String>, tag: String, allowed: List<String>): List<String> =
    if (tag in picked) picked - tag else if (picked.size >= MAX_TAGS) picked else cleanTags(picked + tag, allowed)

  /** «۴٫۳» از دهم */
  fun average(tenths: Int): Pair<Int, Int> = (tenths / 10) to (tenths % 10)
}

// ── تخفیف (MKT-01/09، CHK-04) ──

@Serializable
data class PromoAuto(val id: String = "", val name: String = "", val kind: String = "", val endsOn: String? = null)

/** `GET /:slug/promos`: تخفیف‌های خودکارِ زنده و «کد پذیرفته می‌شود؟» — هیچ کدی فهرست نمی‌شود */
@Serializable
data class PromoList(val promos: List<PromoAuto> = emptyList(), val codes: Boolean = false, val serverTime: Long = 0, val guard: Boolean = false)

@Serializable
data class PromoCodeStatus(val code: String = "", val status: String = "")

@Serializable
data class PromoApplied(val name: String = "", val discountMinor: Long = 0)

@Serializable
data class PromoQuote(
  val subtotalMinor: Long = 0,
  val discountMinor: Long = 0,
  val totalMinor: Long = 0,
  val applied: List<PromoApplied> = emptyList(),
  val code: PromoCodeStatus? = null,
  val unavailable: List<String> = emptyList(),
  val firstOrderMinor: Long = 0,
)

@Serializable
data class PromoQuoteLine(val productId: String, val qtyMilli: Long)

object PromoRules {
  /** همان سقفِ `codeOf` ِ سرور */
  const val CODE_MAX = 64
  const val APPLIED = "applied"

  /** کدی که مشتری زد: بی فاصله و بزرگ‌حرف (سرور هم `normalizeCode` می‌کند؛ این فقط برای نمایش و کلید است) */
  fun cleanCode(raw: String): String =
    ir.vil3ntec.tohid.core.text.LocaleFormat.asciiDigits(raw).filter { !it.isWhitespace() }.uppercase().take(CODE_MAX)

  /** کلیدِ تازگیِ پیش‌نمایش (همان `promoKey` ِ وب): سبد (شناسه، مقدار، قیمتِ دیده‌شده)، کد و — با سد — شماره */
  fun keyOf(cart: Cart?, code: String, guardPhone: String?): String =
    (cart?.lines.orEmpty().joinToString(";") { "${it.productId}:${it.qtyMilli}:${it.priceMinor}" }) + "|" + code + "|" + guardPhone.orEmpty()

  fun lines(cart: Cart?): List<PromoQuoteLine> =
    cart?.lines.orEmpty().filter { !it.unavailable }.map { PromoQuoteLine(it.productId, it.qtyMilli) }

  /** پیش‌نمایشِ همین سبد ⇒ آنچه در بدنهٔ ثبت می‌رود؛ کدِ اعمال‌نشده هرگز نمی‌رود */
  fun applied(q: PromoQuote?, code: String, deviceId: String?): AppliedPromo? {
    if (q == null) return null
    val ok = code.isNotBlank() && q.code?.status == APPLIED
    return AppliedPromo(q.discountMinor.coerceAtLeast(0), if (ok) code else "", deviceId)
  }

  /** پایانِ روزِ `endsOn` (`YYYY-MM-DD`) به وقتِ کابل (+۴:۳۰) ⇒ میلی‌ثانیه؛ نادرست ⇒ null */
  fun endsAtMs(endsOn: String?): Long? {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(endsOn ?: return null) ?: return null
    val (y, mo, d) = m.destructured
    val days = daysFromCivil(y.toInt(), mo.toInt(), d.toInt())
    return (days * 86_400_000L) + (23 * 3600 + 59 * 60 + 59) * 1000L - (4 * 3600 + 30 * 60) * 1000L
  }

  private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
    val y = if (m <= 2) y0 - 1 else y0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468L
  }
}
