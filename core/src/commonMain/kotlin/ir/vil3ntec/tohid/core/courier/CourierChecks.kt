package ir.vil3ntec.tohid.core.courier

import ir.vil3ntec.tohid.core.pick.PickDesk
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 *  پیش‌شرط‌های رویدادِ پیک — DLV-07 (اسکنِ کیسه‌ها هنگامِ گرفتن از دکان) و DLV-17 («تحویل شد» فقط نزدیکِ پین).
 *  منطقِ خالصِ برنامهٔ پیک، commonMain (بی `java.*`). تصمیمِ نهایی با سرور است (`server/src/lib/courier-checks.js`،
 *  پشتِ پرچم‌های خاموشِ `shop.delivery_bag_scan` و `shop.delivery_near_pin`)؛ این‌جا فقط پیش‌سنجش تا پیک پیش
 *  از فرستادن بداند کدام کیسه مانده یا چقدر از پین دور است. کارتِ پیک `bagScan.bags` و `nearPin.maxM` را می‌دهد.
 *
 *  ⛔ دوقلوی همان پرونده و هر دو با بردارِ `core/test-vectors/courier-checks-v1.json` سنجیده می‌شوند
 *  (`CourierChecksVectorsTest` و `server/test/courier-checks.test.js`). خودِ موقعیت فقط برای همان یک رویداد
 *  فرستاده می‌شود و سرور فقط فاصلهٔ گرد را نگه می‌دارد.
 */
object CourierChecks {

  const val DEFAULT_MAX_M = 300
  const val MIN_M = 20
  const val MAX_M = 5000
  private const val EARTH_M = 6371008.8

  data class Point(val lat: Double, val lng: Double)

  /** نتیجهٔ سنجش؛ `code` خالی ⇒ پذیرفته. همان کدهای ردِ سرور. */
  data class Verdict(
    val code: String = "",
    val missing: List<Int> = emptyList(),
    val scanned: Int = 0,
    val distanceM: Long? = null,
    val checked: Boolean = false,
  ) {
    val ok: Boolean get() = code.isEmpty()
  }

  /** برچسب‌های اسکن‌شده در برابرِ سفارشِ `orderNo` با `bags` کیسه. تکرار بی‌اثر؛ نخستین خطا برنده. */
  fun bagsCheck(labels: List<String>, orderNo: Long, bags: Int): Verdict {
    val seen = mutableSetOf<Int>()
    for (raw in labels) {
      val l = PickDesk.parseLabel(raw) ?: return Verdict(code = "bad_label")
      if (l.orderNo != orderNo) return Verdict(code = "bag_wrong_order")
      if (l.of != bags) return Verdict(code = "label_mismatch")
      seen += l.bag
    }
    val missing = (1..bags).filter { it !in seen }
    if (missing.isNotEmpty()) return Verdict(code = if (seen.isEmpty()) "bags_required" else "bags_incomplete", missing = missing)
    return Verdict(scanned = seen.size)
  }

  /** پیک یک کیسه را اسکن کرد: کیسه‌های تا این‌جا + همین، یا کدِ رد (کیسه‌های پیشین دست نمی‌خورند). */
  fun scanOne(scanned: Set<Int>, raw: String, orderNo: Long, bags: Int): Pair<Set<Int>, String> {
    val l = PickDesk.parseLabel(raw) ?: return scanned to "bad_label"
    if (l.orderNo != orderNo) return scanned to "bag_wrong_order"
    if (l.of != bags) return scanned to "label_mismatch"
    return (scanned + l.bag) to ""
  }

  /** برچسبِ کیسه به همان شکلِ سرور — صف همین را می‌فرستد، نه نوشتهٔ خامِ بارکدخوان */
  fun labelOf(orderNo: Long, bag: Int, of: Int): String = "VLNB-$orderNo-$bag-$of"

  /** کیسه‌هایی که هنوز اسکن نشده‌اند */
  fun missing(scanned: Set<Int>, bags: Int): List<Int> = (1..bags).filter { it !in scanned }

  /** فاصلهٔ دو نقطه روی کره (هاورساین)، متر، گرد به عددِ صحیح — همان `distanceM` ِ سرور. */
  fun distanceM(a: Point, b: Point): Long {
    fun rad(x: Double) = x * PI / 180
    val dLat = rad(b.lat - a.lat)
    val dLng = rad(b.lng - a.lng)
    val s1 = sin(dLat / 2)
    val s2 = sin(dLng / 2)
    val h = s1 * s1 + cos(rad(a.lat)) * cos(rad(b.lat)) * s2 * s2
    return (2 * EARTH_M * asin(min(1.0, sqrt(h)))).roundToLong()
  }

  /** «تحویل شد» نزدیکِ پین؟ بی پین ⇒ سنجیده نمی‌شود؛ بی موقعیت ⇒ `position_required`. */
  fun nearPin(position: Point?, pin: Point?, maxM: Int): Verdict {
    if (pin == null) return Verdict(checked = false)
    if (position == null) return Verdict(code = "position_required")
    val d = distanceM(position, pin)
    return if (d <= maxM) Verdict(checked = true, distanceM = d) else Verdict(code = "too_far_from_pin", distanceM = d)
  }

  /** مقدارِ `max_m` ِ دکان: عددِ صحیحِ ۲۰ تا ۵۰۰۰، وگرنه پیش‌فرض. */
  fun maxMOf(v: Long?): Int = if (v != null && v in MIN_M.toLong()..MAX_M.toLong()) v.toInt() else DEFAULT_MAX_M
}
