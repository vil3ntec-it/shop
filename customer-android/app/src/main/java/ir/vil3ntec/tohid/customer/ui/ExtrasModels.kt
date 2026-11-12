package ir.vil3ntec.tohid.customer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.shopper.AddressBook
import ir.vil3ntec.tohid.core.shopper.AddressRules
import ir.vil3ntec.tohid.core.shopper.AppliedPromo
import ir.vil3ntec.tohid.core.shopper.Cart
import ir.vil3ntec.tohid.core.shopper.GeoPin
import ir.vil3ntec.tohid.core.shopper.OrderExtras
import ir.vil3ntec.tohid.core.shopper.PhoneTicket
import ir.vil3ntec.tohid.core.shopper.PinText
import ir.vil3ntec.tohid.core.shopper.PromoList
import ir.vil3ntec.tohid.core.shopper.PromoQuote
import ir.vil3ntec.tohid.core.shopper.PromoRules
import ir.vil3ntec.tohid.core.shopper.RatingState
import ir.vil3ntec.tohid.core.shopper.ReviewRules
import ir.vil3ntec.tohid.core.shopper.ReviewState
import ir.vil3ntec.tohid.core.shopper.SavedAddress
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperResult
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.core.shopper.ShopReviews
import ir.vil3ntec.tohid.sync.v1.Ulid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** موقعیتِ گوشی (مرزِ سکو: `LocationAndroid.kt`، آیفون `LocationIos.kt`). نبود/اجازه نداد ⇒ null. */
fun interface Locator {
  fun locate(done: (GeoPin?) -> Unit)
}

/**
 *  تخفیفِ برگهٔ ثبت (MKT-01/02/09): فهرستِ زنده و پیش‌نمایشِ **سرور** برای همین سبد (`/promos/quote`). تا پیش‌نمایشِ
 *  همین سبد نرسیده «ثبت» بسته است (همان `pending()` ِ وب)؛ کدِ اعمال‌نشده پیامِ روشن می‌گیرد و هرگز در بدنه نمی‌رود.
 */
class PromoModel(private val api: ShopperApi, private val scope: CoroutineScope, private val io: CoroutineDispatcher) {
  /** Ready(null) ⇒ این دکان تخفیفِ ویترین ندارد (۴۰۴/پرچمِ خاموش) — برگه همان مرحلهٔ ۱ */
  var list: Load<PromoList?> by mutableStateOf(Load.Idle)
    private set
  var code: String by mutableStateOf("")
    private set
  var input: String by mutableStateOf("")
  var quote: PromoQuote? by mutableStateOf(null)
    private set
  var note: String? by mutableStateOf(null)
    private set
  private var slug = ""
  private var quotedKey = ""
  private var askedKey = ""
  private var job: Job? = null

  val on: Boolean get() = list.valueOrNull() != null

  fun load(s: String, then: () -> Unit) {
    if (s == slug && (list is Load.Ready || list is Load.Busy)) return then()
    slug = s
    code = ""
    quote = null
    quotedKey = ""
    askedKey = ""
    note = null
    list = Load.Busy
    scope.launch {
      val r = withContext(io) { api.promos(s) }
      if (s != slug) return@launch
      list = Load.Ready(r.valueOrNull())
      then()
    }
  }

  private fun guardPhone(t: PhoneTicket?): String? = if (list.valueOrNull()?.guard == true) t?.phone else null

  /** پیش‌نمایشِ همین سبد هنوز نرسیده؟ (خاموش ⇒ هرگز) */
  fun pending(cart: Cart?, t: PhoneTicket?): Boolean =
    on && PromoRules.lines(cart).isNotEmpty() && quotedKey != PromoRules.keyOf(cart, code, guardPhone(t))

  fun requote(cart: Cart?, t: PhoneTicket?, deviceId: String) {
    val l = list.valueOrNull() ?: return
    val lines = PromoRules.lines(cart)
    if (lines.isEmpty()) return
    val key = PromoRules.keyOf(cart, code, guardPhone(t))
    if (key == quotedKey || key == askedKey) return
    askedKey = key
    job?.cancel()
    val c = code
    job = scope.launch {
      val r = withContext(io) {
        api.promoQuote(slug, lines, c, deviceId.takeIf { l.guard && it.isNotEmpty() }, t?.ticket.takeIf { l.guard })
      }
      if (askedKey != key) return@launch
      askedKey = ""
      when (r) {
        is ShopperResult.Ok -> {
          if (c.isNotEmpty() && r.value.code?.status != PromoRules.APPLIED) {
            //  کد اعمال نشد ⇒ برداشته و پیش‌نمایشِ بی کد (پیامِ همان وضعیت)
            rejected(r.value.code?.status.orEmpty())
            requote(cart, t, deviceId)
            return@launch
          }
          quote = r.value
          quotedKey = key
          //  بی کد ⇒ پیامِ پیشین (مثلاً «این کد پذیرفته نشد») می‌ماند تا مشتری ببیند
          if (c.isNotEmpty()) note = ShopperText.promoOk(c, r.value.discountMinor)
        }
        is ShopperResult.Fail -> note = ShopperText.error(r.error)
      }
    }
  }

  fun apply(cart: Cart?, t: PhoneTicket?, deviceId: String) {
    code = PromoRules.cleanCode(input)
    input = ""
    quotedKey = ""
    requote(cart, t, deviceId)
  }

  fun clear(cart: Cart?, t: PhoneTicket?, deviceId: String) {
    code = ""
    note = null
    quotedKey = ""
    requote(cart, t, deviceId)
  }

  /** ۴۰۹ ِ `promo_code_rejected` یا پیش‌نمایشِ «اعمال نشد» */
  fun rejected(status: String) {
    code = ""
    quotedKey = ""
    note = ShopperText.promoStatus(status)
  }

  /** آنچه در بدنهٔ ثبت می‌رود؛ خاموش ⇒ null (همان بدنهٔ مرحلهٔ ۱) */
  fun applied(t: PhoneTicket?, deviceId: String): AppliedPromo? {
    val l = list.valueOrNull() ?: return null
    return PromoRules.applied(quote, code, deviceId.takeIf { l.guard && it.isNotEmpty() })
  }
}

/** نظرِ سفارش (NTF-09) و امتیاز به رساننده (DLV-18) — هر دو با توکنِ پیگیری و یک بار (تکرار ⇒ همان پاسخ). */
class FeedbackModel(private val api: ShopperApi, private val scope: CoroutineScope, private val io: CoroutineDispatcher) {
  var review: ReviewState? by mutableStateOf(null)
    private set
  var rating: RatingState? by mutableStateOf(null)
    private set
  var stars: Int by mutableStateOf(0)
  var comment: String by mutableStateOf("")
  var rateStars: Int by mutableStateOf(0)
  var tags: List<String> by mutableStateOf(emptyList())
  var busy: Boolean by mutableStateOf(false)
    private set
  var note: String? by mutableStateOf(null)
    private set
  var shop: ShopReviews? by mutableStateOf(null)
    private set
  private var token = ""
  private var shopSlug = ""

  /** پیگیری رسید: کلیدِ `review` ِ همان پاسخ؛ امتیازِ رساننده فقط برای ارسالِ تحویل‌شده */
  fun bind(slug: String, o: PublicOrder, extras: OrderExtras) {
    if (o.token != token) {
      token = o.token
      stars = 0; comment = ""; rateStars = 0; tags = emptyList(); note = null; rating = null
    }
    review = extras.review
    if (rating == null && o.fulfillment == "delivery" && o.status in DELIVERED) {
      scope.launch {
        val r = withContext(io) { api.rating(slug, o.token) }
        if (token == o.token) rating = r.valueOrNull()
      }
    }
  }

  fun sendReview(slug: String) {
    if (!ReviewRules.validStars(stars)) { note = ShopperText.reviewPickStars; return }
    val t = token
    busy = true
    scope.launch {
      val r = withContext(io) { api.review(slug, t, stars, comment) }
      busy = false
      when (r) {
        is ShopperResult.Ok -> { review = ReviewState(canReview = false, review = r.value.review); note = ShopperText.reviewThanks }
        is ShopperResult.Fail -> note = ShopperText.error(r.error)
      }
    }
  }

  fun toggleTag(tag: String) {
    tags = ReviewRules.toggleTag(tags, tag, rating?.tags.orEmpty())
  }

  fun sendRating(slug: String) {
    if (!ReviewRules.validStars(rateStars)) { note = ShopperText.reviewPickStars; return }
    val t = token
    busy = true
    scope.launch {
      val r = withContext(io) { api.rate(slug, t, rateStars, ReviewRules.cleanTags(tags, rating?.tags.orEmpty())) }
      busy = false
      when (r) {
        is ShopperResult.Ok -> rating = rating?.copy(canRate = false, rating = r.value.rating)
        is ShopperResult.Fail -> note = ShopperText.error(r.error)
      }
    }
  }

  /** «★ ۴٫۳ از ۱۲ نظر» ِ برگهٔ دکان (پرچمِ نظرها روشن) */
  fun loadShop(slug: String) {
    if (slug == shopSlug) return
    shopSlug = slug
    shop = null
    scope.launch {
      val r = withContext(io) { api.shopReviews(slug) }
      if (slug == shopSlug) shop = r.valueOrNull()
    }
  }

  companion object {
    /** همان `DELIVERED` ِ `lib/order-reviews.js` */
    val DELIVERED = setOf("delivered", "picked_up", "return_requested", "refunded_partial", "refunded_full")
  }
}

/**
 *  برگهٔ «نشانیِ تازه/ویرایش» (CHK-09) و گرفتنِ پین (CHK-08): «موقعیتِ من» با [Locator]، پیوند/مختصاتِ برنامهٔ نقشه
 *  ([PinText]) یا «انتخاب روی نقشه». ذخیره با همان قاعده و سقفِ سرور ([AddressBook]) روی همین دستگاه؛ با حساب، دفترِ حساب
 *  را `ModelAddressSync.kt` یکی می‌کند ([write] همان را صدا می‌زند).
 */
class AddressModel(
  private val read: () -> List<SavedAddress>,
  private val write: (List<SavedAddress>) -> Unit,
  private val locator: Locator,
  private val clock: () -> Long,
) {
  var editingId: String? by mutableStateOf(null)
    private set
  var label: String by mutableStateOf("")
  var text: String by mutableStateOf("")
  var landmark: String by mutableStateOf("")
  var pin: GeoPin? by mutableStateOf(null)
    private set
  var paste: String by mutableStateOf("")
  var locating: Boolean by mutableStateOf(false)
    private set
  var note: String? by mutableStateOf(null)
    private set

  fun start(a: SavedAddress?, prefill: Triple<String, String, GeoPin?>? = null) {
    editingId = a?.id
    label = a?.label.orEmpty()
    text = a?.text ?: prefill?.first.orEmpty()
    landmark = a?.landmark ?: prefill?.second.orEmpty()
    pin = a?.pin ?: prefill?.third
    paste = ""
    note = null
  }

  fun useMyLocation() {
    locating = true
    note = null
    locator.locate { p ->
      locating = false
      if (p == null) note = ShopperText.pinNone else pin = p
    }
  }

  fun pastePin(): Boolean {
    val p = PinText.parse(paste)
    if (p == null) { note = ShopperText.pinBad; return false }
    pin = p
    paste = ""
    note = null
    return true
  }

  fun clearPin() { pin = null }

  /** CHK-08: «همین‌جا» ِ نقشه */
  fun mapPin(p: GeoPin) {
    pin = p
    paste = ""
    note = null
  }

  /** CRM-09: نشانیِ باز در دفترِ حساب شناسهٔ تازه گرفت ⇒ ذخیره همان نشانی را عوض کند، نه نشانیِ دوم */
  fun follow(old: String, new: String) {
    if (editingId == old) editingId = new
  }

  /** درست ⇒ نشانیِ ذخیره‌شده؛ نادرست ⇒ null و پیامِ روشن */
  fun save(): SavedAddress? {
    val input = AddressRules.clean(label, text, landmark, pin)
    return when (val r = AddressBook.save(read(), editingId, input, clock(), { "adr-" + Ulid.next() })) {
      is AddressBook.Saved.Ok -> { write(r.list); note = null; r.address }
      is AddressBook.Saved.Rejected -> { note = ShopperText.addressProblem(r.problem); null }
    }
  }

  fun remove(id: String) = write(AddressBook.remove(read(), id))
}
