package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.text.LocaleFormat
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlinx.serialization.Serializable

/*
 *  برنامهٔ خریدِ مشتری، مرحلهٔ ۲ — نشانی‌های ذخیره‌شده با پین (CHK-08/09/10).
 *
 *  پین منبعِ اصلیِ نشانی است (CHK-08): از «موقعیتِ من» ِ گوشی، از پیوند/مختصاتی که مشتری از برنامهٔ نقشه می‌چسباند
 *  ([PinText])، یا «انتخاب روی نقشه» ([PinMap]) فقط با سرورِ تکه‌هایی که سرورِ ویترین می‌گوید (`STORE_MAP_TILES` + پرچمِ دکان؛
 *  برنامه خودش هیچ سرورِ نقشه‌ای نمی‌شناسد). «نمایش روی نقشه» همان پیوندِ استانداردِ `geo:` است که هر برنامهٔ نقشهٔ گوشی باز
 *  می‌کند و فقط مختصات در آن می‌رود.
 *
 *  ⛔ قاعده‌ها همان `parseAddress` ِ `server/src/lib/store-customers.js` است (نام لازم، پینِ کامل و در بازه، یکی از
 *  پین/نشانی/نشانه، حداکثر ۱۰ نشانی) تا وقتی حسابِ مشتری آمد (CHK-02، پس از D-09) همین فهرست بی تغییر به درهای
 *  `/:slug/account/addresses` برود — [ShopperApi.addAddress] همان درها را با نشستِ حساب می‌زند.
 */

/** پینِ تحویل؛ [accuracyM] دقتِ موقعیتِ گوشی به متر (null ⇒ از پیوندِ نقشه). */
@Serializable
data class GeoPin(val lat: Double, val lng: Double, val accuracyM: Int? = null)

/**
 *  یک نشانیِ نام‌دار — همان `addressView` ِ سرور. شناسهٔ `adr-…` یعنی فقط روی همین دستگاه؛ شناسهٔ دیگر یعنی نشانیِ دفترِ
 *  حساب ([AddressSync]، CRM-09). [media] فقط از درِ دکانی با پرچمِ `shop.storefront.address_media`؛ [dirty] تغییرِ همین
 *  دستگاه روی نشانیِ حساب که هنوز به سرور نرسیده.
 */
@Serializable
data class SavedAddress(
  val id: String,
  val label: String,
  val text: String = "",
  val landmark: String = "",
  val lat: Double? = null,
  val lng: Double? = null,
  val updatedAt: Long = 0,
  val media: AddressMediaFlags? = null,
  val dirty: Boolean = false,
) {
  val pin: GeoPin? get() = if (lat != null && lng != null) GeoPin(lat, lng) else null
}

/** [mediaLimits] فقط با پرچمِ رسانهٔ نشانیِ همان دکان */
@Serializable
data class AddressList(val addresses: List<SavedAddress> = emptyList(), val mediaLimits: AddressMediaLimits? = null)

/** بدنهٔ `POST`/`PUT /:slug/account/addresses` (همان کلیدهای `parseAddress`). */
@Serializable
data class AddressInput(val label: String, val text: String = "", val landmark: String = "", val lat: Double? = null, val lng: Double? = null)

/** چرا نشانی ذخیره نمی‌شود — هر کدام یک پیامِ روشن در [ShopperText.addressProblem] دارد. */
enum class AddressProblem { LABEL, PIN, EMPTY, FULL }

object AddressRules {
  /** همان `MAX_ADDRESSES` ِ سرور */
  const val MAX = 10
  const val LABEL_MAX = 40
  const val TEXT_MAX = 300
  const val LANDMARK_MAX = 200

  /**
   *  شش رقمِ اعشار (~۱۰ سانتی‌متر)، همان گردِ `coord` ِ سرور: `Math.round` ِ JS، نیمه رو به بالا. `kotlin.math.round` نیمه را
   *  زوج می‌کرد (34.5000005 ⇒ 34.5، سرور 34.500001). بردارِ `core/test-vectors/delivery-zone-v1.json` (`round6`).
   */
  fun round6(v: Double): Double {
    val x = v * 1e6
    val f = floor(x)
    return (if (x - f >= 0.5) f + 1 else f) / 1e6
  }

  fun validPin(lat: Double, lng: Double): Boolean =
    lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0 &&
      //  (۰، ۰) تقریباً همیشه «موقعیت نیامد» است، نه نشانیِ واقعی
      !(abs(lat) < 1e-9 && abs(lng) < 1e-9)

  /** همان `text()` ِ سرور: بی نویسهٔ کنترلی، بی فاصلهٔ دو سر، بریده تا سقف */
  fun text(v: String, max: Int): String = v.filter { it >= ' ' && it != '\u007F' }.trim().take(max)

  fun clean(label: String, text: String, landmark: String, pin: GeoPin?): AddressInput = AddressInput(
    label = text(label, LABEL_MAX), text = text(text, TEXT_MAX), landmark = text(landmark, LANDMARK_MAX),
    lat = pin?.let { round6(it.lat) }, lng = pin?.let { round6(it.lng) },
  )

  /** null ⇒ درست؛ ترتیبِ همان سرور */
  fun problem(a: AddressInput): AddressProblem? = when {
    a.label.isBlank() -> AddressProblem.LABEL
    (a.lat == null) != (a.lng == null) -> AddressProblem.PIN
    a.lat != null && a.lng != null && !validPin(a.lat, a.lng) -> AddressProblem.PIN
    a.lat == null && a.text.isBlank() && a.landmark.isBlank() -> AddressProblem.EMPTY
    else -> null
  }
}

/** فهرستِ نشانی‌های روی همین دستگاه — همان سقف و قاعده‌های سرور؛ با حساب، [AddressSync] آن را با دفترِ حساب یکی می‌کند. */
object AddressBook {
  sealed class Saved {
    data class Ok(val list: List<SavedAddress>, val address: SavedAddress) : Saved()
    data class Rejected(val problem: AddressProblem) : Saved()
  }

  /** [id] null ⇒ نشانیِ تازه (سقفِ ۱۰)؛ وگرنه همان ردیف عوض می‌شود و جایش می‌ماند */
  fun save(list: List<SavedAddress>, id: String?, input: AddressInput, now: Long, newId: () -> String): Saved {
    AddressRules.problem(input)?.let { return Saved.Rejected(it) }
    val old = id?.let { i -> list.firstOrNull { it.id == i } }
    if (old == null && list.size >= AddressRules.MAX) return Saved.Rejected(AddressProblem.FULL)
    //  ویرایشِ نشانیِ حساب ⇒ «هنوز نرفته» تا گامِ بعدیِ [AddressSync]؛ رسانه همان می‌ماند (نشانی همان است)
    val a = SavedAddress(
      old?.id ?: newId(), input.label, input.text, input.landmark, input.lat, input.lng, now,
      media = old?.media, dirty = old != null && !AddressSync.deviceOnly(old.id),
    )
    return Saved.Ok(if (old == null) list + a else list.map { if (it.id == a.id) a else it }, a)
  }

  fun remove(list: List<SavedAddress>, id: String): List<SavedAddress> = list.filter { it.id != id }

  /** برگهٔ ثبت از نشانیِ برگزیده: نشانی، نشانه و پین؛ ناحیه از نو (از پین، یا گزینشِ دوباره) */
  fun fill(form: CheckoutForm, a: SavedAddress): CheckoutForm =
    form.copy(addressText = a.text, landmark = a.landmark, pinLat = a.lat, pinLng = a.lng, pinAccuracyM = null, zoneId = null, addressId = a.id)
}

/**
 *  مختصات از هر چه مشتری از برنامهٔ نقشه می‌چسباند — بی شبکه و بی ژئوکدر:
 *  `34.5281, 69.1723` (رقمِ فارسی هم)، `geo:34.52,69.17?q=…`، Google Maps (`!3d…!4d…`، `@lat,lng`، `?q=`/`ll=`/`query=`)،
 *  OpenStreetMap (`mlat=…&mlon=…`، `#map=z/lat/lng`) و Apple Maps (`ll=`/`q=`). پیوندِ کوتاه (`maps.app.goo.gl/…`)
 *  بی شبکه باز نمی‌شود ⇒ null (برنامه می‌گوید پیوند را باز کنید و مختصات را بچسبانید).
 */
object PinText {
  private const val N = "(-?\\d{1,3}(?:\\.\\d+)?)"
  private val PATTERNS = listOf(
    Regex("^\\s*geo:$N,$N", RegexOption.IGNORE_CASE),
    Regex("!3d$N!4d$N"),
    Regex("[?&](?:mlat)=$N&(?:amp;)?mlon=$N"),
    Regex("[?&](?:q|query|ll|daddr|destination|center|sll)=(?:loc:)?$N(?:,|%2C)\\s*(?:\\+|%20)?$N", RegexOption.IGNORE_CASE),
    Regex("@$N,$N"),
    Regex("#map=\\d{1,2}/$N/$N"),
    //  مختصاتِ خالی: هر دو با اعشار (تا «۱۲ ۵» ِ بی‌معنا پین نشود)
    Regex("^\\s*(-?\\d{1,3}\\.\\d+)\\s*[,،\\s]\\s*(-?\\d{1,3}\\.\\d+)\\s*$"),
  )

  fun parse(raw: String): GeoPin? {
    val s = LocaleFormat.asciiDigits(raw).replace('٫', '.').trim()
    if (s.isEmpty() || s.length > 2000) return null
    for (re in PATTERNS) {
      val m = re.find(s) ?: continue
      val lat = m.groupValues[1].toDoubleOrNull() ?: continue
      val lng = m.groupValues[2].toDoubleOrNull() ?: continue
      if (AddressRules.validPin(lat, lng)) return GeoPin(AddressRules.round6(lat), AddressRules.round6(lng))
    }
    return null
  }

  /** «نمایش روی نقشه»: فقط مختصات (نه نام، نشانه یا شماره) — همان قاعدهٔ مسیریابِ پیک (`courier-map.js`) */
  fun geoUri(pin: GeoPin): String {
    val p = "${AddressRules.round6(pin.lat)},${AddressRules.round6(pin.lng)}"
    return "geo:$p?q=$p"
  }

  /** نمایشِ کوتاه (پنج رقمِ اعشار ≈ یک متر) با رقمِ ASCII؛ صفحه رقم را به زبان می‌برد */
  fun short(pin: GeoPin): String = "${fmt(pin.lat)}, ${fmt(pin.lng)}"

  private fun fmt(v: Double): String {
    val neg = v < 0
    val scaled = round(abs(v) * 1e5).toLong()
    val body = "${scaled / 100000}.${(scaled % 100000).toString().padStart(5, '0')}"
    return if (neg) "-$body" else body
  }
}
