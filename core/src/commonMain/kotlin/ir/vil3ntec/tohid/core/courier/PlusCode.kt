package ir.vil3ntec.tohid.core.courier

import kotlin.math.floor

/**
 *  S33 — Plus Code (Open Location Code، کدِ ۱۰ نویسه‌ای، دقتِ ≈ ۱۴ متر) از **پینِ** نشانی، تا پیکی که پینِ مقصد را دارد کدِ
 *  کوتاهی هم داشته باشد که در هر نقشه، آفلاین هم، و با تلفن گفته شود. پیش‌تر فقط Plus Code ِ تایپ‌شده نگه داشته می‌شد.
 *
 *  همان الگوریتمِ مرجعِ OLC (github.com/google/open-location-code): مختصات ضربدرِ دقتِ نهایی و گرد به ۶ رقم (تا خطای
 *  دودویی کم شود)، سپس فقط حسابِ صحیح؛ عرضِ ۹۰ یک پله پایین، طول به [-۱۸۰، ۱۸۰). مختصاتِ نامعتبر (NaN/بی‌نهایت) ⇒ `null`.
 *  بی ژئوکدر و بی شبکه: هیچ چیز جایی فرستاده نمی‌شود. Kotlinِ خالص در commonMain (آیفون هم).
 */
object PlusCode {

  private const val ALPHABET = "23456789CFGHJMPQRVWX"
  private const val PAIRS = 5
  /** دقتِ نهایی ضربدرِ تقسیمِ شبکه (مثلِ مرجع): عرض ۸۰۰۰ × ۵^۵، طول ۸۰۰۰ × ۴^۵ */
  private const val LAT_MUL = 8_000.0 * 3_125
  private const val LNG_MUL = 8_000.0 * 1_024
  private const val LAT_GRID = 3_125L
  private const val LNG_GRID = 1_024L

  /** نیمه به بالا، بی وابستگی به `Math.round` ِ جاوا */
  private fun halfUp(x: Double): Double = floor(x + 0.5)

  fun encode(lat: Double, lng: Double): String? {
    if (!lat.isFinite() || !lng.isFinite()) return null
    var la = lat.coerceIn(-90.0, 90.0)
    if (la == 90.0) la -= 1.0 / 8_000
    var lo = lng
    while (lo < -180.0) lo += 360.0
    while (lo >= 180.0) lo -= 360.0
    var latVal = (halfUp((la + 90.0) * LAT_MUL * 1e6) / 1e6).toLong() / LAT_GRID
    var lngVal = (halfUp((lo + 180.0) * LNG_MUL * 1e6) / 1e6).toLong() / LNG_GRID
    val out = CharArray(PAIRS * 2)
    for (i in PAIRS - 1 downTo 0) {
      out[i * 2] = ALPHABET[(latVal % 20).toInt()]
      out[i * 2 + 1] = ALPHABET[(lngVal % 20).toInt()]
      latVal /= 20
      lngVal /= 20
    }
    val s = out.concatToString()
    return s.substring(0, 8) + "+" + s.substring(8)
  }
}
