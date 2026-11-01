package ir.vil3ntec.tohid.core.courier

import ir.vil3ntec.tohid.core.courier.CourierChecks.Point

/**
 *  چند سفارش در یک مسیر با ترتیبِ پیشنهادی — DLV-04. منطقِ خالص، commonMain (بی `java.*`).
 *
 *  «نزدیک‌ترین بعدی» از نقطهٔ شروع (موقعیتِ همین لحظهٔ پیک، وگرنه نخستین مأموریتِ پین‌دار به ترتیبِ سپردن)، سپس
 *  2-opt روی مسیرِ باز تا وقتی کوتاه‌تر شود. فاصله عددِ صحیحِ متر است (`CourierChecks.distanceM`) تا تساوی فقط با
 *  جای ورودی شکسته شود. ⛔ دوقلوی `server/src/lib/courier-route.js` (`plan`، پشتِ پرچمِ خاموشِ `shop.delivery_route`)؛
 *  هر دو با بردارِ `core/test-vectors/courier-route-v1.json` (`RoutePlanVectorsTest` و `server/test/courier-route.test.js`).
 *  فقط پیشنهاد است؛ هیچ وضعیتی عوض نمی‌شود.
 */
object RoutePlan {

  private const val MAX_PASSES = 50

  /** `order` و `unpinned` جایِ ایستگاه در ورودی‌اند؛ `legsM[k]` فاصلهٔ رسیدن به `order[k]` (بی شروع ⇒ نخستین ۰). */
  data class Plan(val order: List<Int>, val legsM: List<Long>, val totalM: Long, val unpinned: List<Int>)

  fun plan(start: Point?, pins: List<Point?>): Plan {
    val idx = pins.indices.filter { pins[it] != null }
    val unpinned = pins.indices.filter { pins[it] == null }
    if (idx.isEmpty()) return Plan(emptyList(), emptyList(), 0, unpinned)
    fun d(a: Int, b: Int) = CourierChecks.distanceM(pins[a]!!, pins[b]!!)
    fun fromStart(a: Int) = if (start != null) CourierChecks.distanceM(start, pins[a]!!) else 0L

    //  ۱) نزدیک‌ترین بعدی — تساوی با جای ورودی
    val left = idx.toMutableSet()
    val order = mutableListOf<Int>()
    var cur = -1
    if (start == null) { cur = idx[0]; order += cur; left -= cur }
    while (left.isNotEmpty()) {
      var best = -1
      var bestD = Long.MAX_VALUE
      for (i in idx) {
        if (i !in left) continue
        val di = if (cur == -1) fromStart(i) else d(cur, i)
        if (di < bestD) { best = i; bestD = di }
      }
      order += best
      left -= best
      cur = best
    }

    //  ۲) 2-opt روی مسیرِ باز: برگرداندنِ [i..j]؛ نخستین بهبود پذیرفته
    val n = order.size
    fun edge(a: Int, b: Int): Long = if (a == -1) fromStart(b) else d(a, b)
    var passes = 0
    var improved = true
    while (improved && passes++ < MAX_PASSES) {
      improved = false
      var i = 0
      while (i < n - 1 && !improved) {
        var j = i + 1
        while (j < n && !improved) {
          val p = if (i > 0) order[i - 1] else -1
          val nx = if (j + 1 < n) order[j + 1] else null
          val before = edge(p, order[i]) + (if (nx == null) 0L else d(order[j], nx))
          val after = edge(p, order[j]) + (if (nx == null) 0L else d(order[i], nx))
          if (after < before) {
            val seg = order.subList(i, j + 1).reversed()
            for (k in seg.indices) order[i + k] = seg[k]
            improved = true
          }
          j++
        }
        i++
      }
    }

    val legs = order.mapIndexed { k, s -> if (k == 0) fromStart(s) else d(order[k - 1], s) }
    return Plan(order.toList(), legs, legs.sum(), unpinned)
  }

  /** همان `STOP_STATUSES` ِ `courier-route.js`: پس از «قبول» و پیش از پایان */
  val STOP_STATUSES: Set<String> = setOf("assigned", "picked_up", "moving", "arrived")

  data class CardStop(val card: MissionCard, val seq: Int, val legM: Long)
  data class CardRoute(val stops: List<CardStop>, val unpinned: List<MissionCard>, val totalM: Long)

  /** کارت‌های پیک ⇒ همان ترتیبِ `routeOf` ِ سرور، روی خودِ گوشی و بی اینترنت (DLV-04، DLV-10) */
  fun forCards(start: Point?, cards: List<MissionCard>): CardRoute {
    val stops = cards.filter { it.status in STOP_STATUSES }
    val pins = stops.map { c ->
      val la = c.lat
      val ln = c.lng
      if (la != null && ln != null) Point(la, ln) else null
    }
    val p = plan(start, pins)
    return CardRoute(p.order.mapIndexed { k, i -> CardStop(stops[i], k + 1, p.legsM[k]) }, p.unpinned.map { stops[it] }, p.totalM)
  }

  /** فاصله برای صفحه: زیرِ هزار متر «متر»، وگرنه کیلومتر با یک رقمِ اعشار (رقمِ لاتین؛ رقمِ صفحه از فونت) */
  fun km(m: Long): String = "${m / 1000}.${(m % 1000) / 100}"
}
