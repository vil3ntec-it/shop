package ir.vil3ntec.tohid.core.shopper

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.serialization.Serializable

/*
 *  CHK-08، تصمیمِ ۴-۳ «الف» — «انتخاب روی نقشه» برای پینِ نشانی در برنامهٔ خرید؛ همتای `server/public/store/pin-map.js` ِ ویترین
 *  (بردارِ مشترکِ `core/test-vectors/pin-map-v1.json`، همان که `server/test/store-pin-map.test.js` با آن پرونده می‌خواند).
 *
 *  بی کتابخانهٔ نقشه: فقط تکه‌های ۲۵۶ پیکسلیِ همان الگوی `{z}/{x}/{y}` که سرور در `features.mapPin.tiles` می‌دهد (پرچمِ
 *  `shop.storefront.map_pin` + `STORE_MAP_TILES` ِ سرور؛ نبود ⇒ دکمه‌ای نیست). نشانهٔ وسط ثابت است و نقشه زیرش جابه‌جا می‌شود؛
 *  «همین‌جا» ⇒ همان راهِ «موقعیتِ من» (ناحیه را فقط سرور می‌گوید). تکه‌ها مستقیم از گوشی به سرورِ نقشه می‌روند (فقط تصویر،
 *  بی کوکی) — همان خطرِ «الف» در تصمیم: آن سرور IP و ناحیهٔ دیده‌شده را می‌بیند.
 */

/** `features.mapPin` ِ ویترین */
@Serializable
data class MapPinFeature(val tiles: String = "", val attribution: String = "", val copyright: String = "")

/** نمای نقشه: مرکز به پیکسلِ جهان (Web Mercator) در بزرگنمایی [z] */
data class MapView(val x: Double, val y: Double, val z: Int)

/** یک تکهٔ دیدنی: [left]/[top] جای گوشهٔ بالا-چپ در قابِ نما، پیکسل */
data class MapTile(val key: String, val url: String, val left: Int, val top: Int)

object PinMap {
  const val SIZE = 256
  const val MIN_Z = 3
  const val MAX_Z = 19
  const val START_Z = 16

  /** پیکسلِ هر فشارِ دکمهٔ جهت (همان `STEP` ِ وب) */
  const val STEP = 64
  const val LAT_MAX = 85.05112878

  /** بی پینِ پیشین: کابل (همان پیش‌فرضِ وب) */
  val DEFAULT = GeoPin(34.5553, 69.2075)

  private val BAD = Regex("[\"'\\s;,<>\\\\]")
  private val HTTPS = Regex("^https://[a-z0-9.-]+(:\\d{1,5})?/", RegexOption.IGNORE_CASE)
  private val SAME_ORIGIN = Regex("^/[^/]")

  /** همان `valid()` ِ `lib/store-map.js`: سه جای‌نگهدار، https ِ میزبانِ ساده یا مسیرِ همین سرور، بی نویسهٔ خطرناک */
  fun valid(t: String): Boolean =
    t.length <= 300 && !BAD.containsMatchIn(t) && "{z}" in t && "{x}" in t && "{y}" in t &&
      (HTTPS.containsMatchIn(t) || SAME_ORIGIN.containsMatchIn(t))

  /** دکمهٔ «انتخاب روی نقشه»: فقط با الگوی درست */
  fun usable(f: MapPinFeature?): MapPinFeature? = f?.takeIf { valid(it.tiles) }

  private fun clampLat(v: Double): Double = v.coerceIn(-LAT_MAX, LAT_MAX)

  private fun world(z: Int): Double = SIZE * 2.0.pow(z)

  /** مختصات ⇐ پیکسلِ جهان در [z] */
  fun toPx(lat: Double, lng: Double, z: Int): Pair<Double, Double> {
    val n = world(z)
    val s = sin(clampLat(lat) * PI / 180)
    return (lng + 180) / 360 * n to (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * n
  }

  /** پیکسلِ جهان ⇐ مختصات (شش رقمِ اعشار، همان گردِ سرور؛ طول در بازهٔ ‎-180..180) */
  fun toLL(x: Double, y: Double, z: Int): GeoPin {
    val n = world(z)
    var lng = x / n * 360 - 180
    val k = PI - 2 * PI * y / n
    val lat = 180 / PI * atan(0.5 * (exp(k) - exp(-k)))
    lng = ((lng + 180) % 360 + 360) % 360 - 180
    return GeoPin(AddressRules.round6(clampLat(lat)), AddressRules.round6(lng))
  }

  fun open(center: GeoPin?): MapView {
    val c = center?.takeIf { it.lat.isFinite() && it.lng.isFinite() } ?: DEFAULT
    val (x, y) = toPx(c.lat, c.lng, START_Z)
    return MapView(x, y, START_Z)
  }

  /** مرکزِ نما = جای نشانه */
  fun here(v: MapView): GeoPin = toLL(v.x, v.y, v.z)

  /** کشیدن یا دکمهٔ جهت: طول دور می‌زند، عرض در لبهٔ جهان می‌ایستد */
  fun move(v: MapView, dx: Double, dy: Double): MapView = v.copy(x = v.x + dx, y = (v.y + dy).coerceIn(0.0, world(v.z)))

  /** «نزدیک‌تر»/«دورتر»: همان نقطهٔ زیرِ نشانه، در بازهٔ ۳ تا ۱۹ */
  fun zoom(v: MapView, d: Int): MapView {
    val nz = (v.z + d).coerceIn(MIN_Z, MAX_Z)
    if (nz == v.z) return v
    val p = here(v)
    val (x, y) = toPx(p.lat, p.lng, nz)
    return MapView(x, y, nz)
  }

  fun url(template: String, z: Int, x: Int, y: Int): String =
    template.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())

  /** تکه‌هایی که قابِ [w]×[h] را می‌پوشانند (همان `draw` ِ وب)؛ شمارهٔ ستون دور می‌زند، ردیفِ بیرون از جهان نه */
  fun tiles(v: MapView, w: Int, h: Int, template: String): List<MapTile> {
    if (w <= 0 || h <= 0) return emptyList()
    val n = 1 shl v.z
    val left = v.x - w / 2.0
    val top = v.y - h / 2.0
    val out = ArrayList<MapTile>()
    for (ty in floor(top / SIZE).toInt()..floor((top + h) / SIZE).toInt()) {
      if (ty < 0 || ty >= n) continue
      for (tx in floor(left / SIZE).toInt()..floor((left + w) / SIZE).toInt()) {
        val wx = ((tx % n) + n) % n
        out += MapTile("${v.z}/$tx/$ty", url(template, v.z, wx, ty), (tx * SIZE - left).roundToInt(), (ty * SIZE - top).roundToInt())
      }
    }
    return out
  }
}
