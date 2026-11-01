package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  «نمایش در بازارچه» — سمتِ دکان (`GET/PUT /api/shop/market`،
 *  `server/src/routes/market-admin.js` و `lib/market.js`).
 *
 *  فهرست شدن در بازارچه خواستنی است (`market_listings.listed` پیش‌فرض
 *  خاموش)، پس تا صاحب دکان این‌جا روشنش نکند دکان در سایتِ مشتری نیست.
 *
 *  سرور داورِ نهایی است؛ این‌جا همان قاعده‌ها پیش از فرستادن سنجیده
 *  می‌شوند تا خطا کنارِ همان کادر دیده شود:
 *    • دسته: یکی از ۱۲ کلیدِ ثابتِ سرور (یا خالی)
 *    • ناحیه: بی نویسهٔ کنترلی، حداکثر ۶۰ نویسه
 *    • موقعیت: فقط درشت (یک‌صدمِ درجه ≈ یک کیلومتر) — نقطهٔ دقیق هرگز
 *      فرستاده یا نگه داشته نمی‌شود
 */
object MarketListing {

  const val ROOT = "/shop/market"

  /** همان `text(b.area, 60)` ِ سرور */
  const val AREA_MAX = StorefrontRules.AREA_MAX

  /** همان `CATEGORIES` ِ سرور — اگر پاسخ فهرست نداشت (سرورِ قدیمی‌تر) */
  val CATEGORIES: List<MarketCategoryDto> = listOf(
    MarketCategoryDto("grocery", "سوپرمارکت و خواربار"),
    MarketCategoryDto("bakery", "نانوایی"),
    MarketCategoryDto("produce", "میوه و سبزی"),
    MarketCategoryDto("butcher", "قصابی و مرغ"),
    MarketCategoryDto("dairy", "لبنیات"),
    MarketCategoryDto("sweets", "شیرینی و خشکبار"),
    MarketCategoryDto("beauty", "بهداشتی و آرایشی"),
    MarketCategoryDto("household", "لوازمِ خانه"),
    MarketCategoryDto("stationery", "قرطاسیه و کتاب"),
    MarketCategoryDto("clothing", "پوشاک"),
    MarketCategoryDto("electronics", "موبایل و برقی"),
    MarketCategoryDto("other", "دیگر"),
  )

  /* ------------------------------ پاسخ ------------------------------ */

  /** پاسخِ `GET` ⇒ `{ directory, listing }` */
  fun parseState(body: JsonElement): MarketStateDto = ApiJson.decode<MarketStateDto>(body)

  /** پاسخِ `PUT` ⇒ `{ listing }` */
  fun parseSaved(body: JsonObject): MarketListingDto = ApiJson.decode<MarketListingDto>(body["listing"] ?: JsonNull)

  /** فهرستِ دسته‌ها: همان که سرور فرستاد؛ نبود ⇒ فهرستِ ثابتِ همین‌جا */
  fun categoriesOf(listing: MarketListingDto): List<MarketCategoryDto> =
    listing.categories.filter { it.key.isNotBlank() && it.label.isNotBlank() }.ifEmpty { CATEGORIES }

  fun labelOf(key: String, categories: List<MarketCategoryDto>): String =
    if (key.isBlank()) "" else categories.firstOrNull { it.key == key }?.label ?: key

  /* ------------------------------ ورودی ------------------------------ */

  /** قاعده‌ها در `core` (`StorefrontRules`، commonMain) — همان سرور */
  fun cleanArea(s: String): String = StorefrontRules.cleanArea(s)

  fun areaError(area: String): String? = StorefrontRules.areaError(area)

  fun categoryError(key: String, listed: Boolean, categories: List<MarketCategoryDto>): String? =
    StorefrontRules.categoryError(key, listed, categories.map { it.key })

  fun coarse(v: Double): Double = StorefrontRules.coarse(v)

  /** نقطهٔ دستگاه ⇒ موقعیتِ درشت؛ نقطهٔ بی‌معنا ⇒ null */
  fun coarseOf(lat: Double, lng: Double): MarketLocationDto? {
    if (!lat.isFinite() || !lng.isFinite() || kotlin.math.abs(lat) > 90 || kotlin.math.abs(lng) > 180) return null
    return MarketLocationDto(coarse(lat), coarse(lng))
  }

  /** تغییرِ موقعیت در فرم — «دست نزن» یعنی کلیدِ `location` اصلاً نمی‌رود */
  sealed interface LocationEdit {
    data object Keep : LocationEdit
    data object Clear : LocationEdit
    data class Set(val location: MarketLocationDto) : LocationEdit
  }

  data class Form(
    val listed: Boolean,
    val category: String,
    val area: String,
    val location: LocationEdit = LocationEdit.Keep,
  )

  /** خطاها با کلیدِ همان کادر (`category`، `area`)؛ خالی یعنی فرستادنی است */
  fun errors(form: Form, categories: List<MarketCategoryDto>): Map<String, String> = buildMap {
    categoryError(form.category, form.listed, categories)?.let { put("category", it) }
    areaError(form.area)?.let { put("area", it) }
  }

  /** بدنهٔ `PUT` — فقط کلیدهایی که سرور می‌شناسد؛ موقعیت همیشه درشت */
  fun body(form: Form): JsonObject = buildJsonObject {
    put("listed", form.listed)
    put("category", form.category.trim())
    put("area", cleanArea(form.area))
    when (val l = form.location) {
      LocationEdit.Keep -> Unit
      LocationEdit.Clear -> put("location", JsonNull)
      is LocationEdit.Set -> put("location", buildJsonObject {
        put("lat", JsonPrimitive(coarse(l.location.lat)))
        put("lng", JsonPrimitive(coarse(l.location.lng)))
      })
    }
  }

  /* ------------------------------ حالت ------------------------------ */

  /** چرا بخشِ بازارچه فرم ندارد */
  enum class Gate { READY, MARKET_OFF, STOREFRONT_OFF, NO_PERMISSION, ERROR }

  /**
   *  خطای `GET` ⇒ حالتِ بخش.
   *  ۴۰۴ (هر کدی: `market_not_found` یا سرورِ قدیمی بی این مسیر) = بازارچه نیست.
   */
  fun gateOf(status: Int, code: String): Gate = when {
    status == 404 -> Gate.MARKET_OFF
    status == 403 && code == "storefront_disabled" -> Gate.STOREFRONT_OFF
    status == 403 -> Gate.NO_PERMISSION
    else -> Gate.ERROR
  }

  /** هشدارِ کنارِ کلید: «در فهرست» هست ولی مشتری هنوز نمی‌بیند */
  fun hiddenReason(listed: Boolean, storefrontEnabled: Boolean, storefrontExists: Boolean): String? = when {
    !listed -> null
    !storefrontExists -> "ویترین هنوز ساخته نشده؛ تا ساخته و روشن نشود، دکان در بازارچه دیده نمی‌شود."
    !storefrontEnabled -> "ویترین خاموش است؛ تا روشنش نکنید، دکان در بازارچه دیده نمی‌شود."
    else -> null
  }

  fun locationText(l: MarketLocationDto?): String =
    if (l == null) "گذاشته نشده" else "حدودِ " + toFa("%.2f".format(java.util.Locale.ROOT, l.lat)) + "، " + toFa("%.2f".format(java.util.Locale.ROOT, l.lng))

  private fun toFa(n: Int): String = StorefrontOwner.toFa(n.toString())
  private fun toFa(s: String): String = StorefrontOwner.toFa(s)
}

/** `GET /api/shop/market` و `PUT` همان را برمی‌گرداند (`listingView` ِ سرور) */
class MarketListingRepository(private val api: ApiClient) {

  suspend fun load(): ApiResult<MarketStateDto> = ApiResult.of {
    MarketListing.parseState(api.get(MarketListing.ROOT))
  }

  suspend fun save(form: MarketListing.Form): ApiResult<MarketListingDto> = ApiResult.of {
    MarketListing.parseSaved(api.put(MarketListing.ROOT, MarketListing.body(form)))
  }
}

@Serializable
data class MarketCategoryDto(val key: String = "", val label: String = "")

/** درشت، به درجه (سرور یک‌صدم نگه می‌دارد و همان را به خودِ صاحب دکان برمی‌گرداند) */
@Serializable
data class MarketLocationDto(val lat: Double = 0.0, val lng: Double = 0.0)

@Serializable
data class MarketListingDto(
  val listed: Boolean = false,
  val category: String = "",
  val area: String = "",
  val location: MarketLocationDto? = null,
  val updatedAt: Long? = null,
  val categories: List<MarketCategoryDto> = emptyList(),
)

@Serializable
data class MarketStateDto(
  /** پرچمِ سراسریِ `market.directory` — خاموش یعنی بازارچه هنوز برای مشتری باز نیست */
  val directory: Boolean = false,
  val listing: MarketListingDto = MarketListingDto(),
)
