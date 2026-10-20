package ir.vil3ntec.tohid.core.delivery

import ir.vil3ntec.tohid.core.orders.OrderText

/**
 *  سپردنِ خودکارِ سفارش‌های آماده به پیک‌های دکان (DLV-03 خودکار، DLV-11).
 *
 *  ⛔ پیکی که با این سفارش از سقفِ نقدش می‌گذرد هرگز انتخاب نمی‌شود — و
 *     «آگاهانه بالای سقف» فقط در سپردنِ دستیِ مدیر هست، نه این‌جا.
 *  ⛔ پیکِ غیرفعال کارِ تازه نمی‌گیرد.
 *  • «نزدیک‌ترین پیک» هنوز داده ندارد (موقعیتِ پیک، DLV-16، ساخته نشده)؛ پس
 *    کم‌کارترین پیک انتخاب می‌شود: کمترین مأموریتِ زنده، بعد کمترین نقدِ
 *    درگیر، بعد نام — تا کار پخش شود و نتیجه همیشه یکی باشد.
 *  • سقفِ کارِ هم‌زمان (`maxLive`) اختیاری است؛ تهی یعنی بی‌سقف.
 *  • قدیمی‌ترین سفارش اول.
 *
 *  نقشه فقط پیشنهاد است؛ هر سپردن را سرور با قفلِ ردیفِ پیک دوباره
 *  می‌سنجد. اگر میانِ کار سرور «سقف پر شد» گفت (دستگاهِ دیگری زودتر
 *  سپرد)، میز پیک‌ها را تازه می‌کند و برای باقی دوباره نقشه می‌کشد.
 */
object AutoAssign {

  /** سفارشِ آماده‌ای که هنوز مأموریتِ زنده ندارد */
  data class Candidate(
    val orderId: String,
    val orderNo: Long,
    val expectedCollectMinor: Long,
    val createdAt: Long,
  )

  data class Pick(val orderId: String, val orderNo: Long, val courierId: String, val courierName: String)

  data class Unplaced(val orderId: String, val orderNo: Long, val reason: String)

  /** پیکی که (پس از این نقشه) سفارشِ خودکار نمی‌گیرد، با دلیلِ فارسی */
  data class Blocked(val courierId: String, val name: String, val reason: String)

  data class Plan(val picks: List<Pick>, val unplaced: List<Unplaced>, val blocked: List<Blocked>)

  /** همان حسابِ سرور (`assign`): فقط نقدِ هنگامِ تحویلِ پرداخت‌نشده گرفتنی است */
  fun expectedOf(paymentMethod: String, paymentStatus: String, totalMinor: Long): Long =
    if (paymentMethod == "cod" && paymentStatus != "paid") totalMinor else 0L

  fun plan(orders: List<Candidate>, couriers: List<CourierRow>, maxLive: Int? = null): Plan {
    val loads = couriers.filter { it.active }.associateBy { it.id }.toMutableMap()
    val picks = ArrayList<Pick>()
    val unplaced = ArrayList<Unplaced>()
    val queue = orders.sortedWith(compareBy<Candidate>({ it.createdAt }, { it.orderNo }, { it.orderId }))

    for (o in queue) {
      val best = loads.values
        .filter { CourierCash.fits(it, o.expectedCollectMinor) && (maxLive == null || it.liveCount < maxLive) }
        .minWithOrNull(compareBy<CourierRow>({ it.liveCount }, { CourierCash.committed(it) }, { it.name }, { it.id }))
      if (best == null) {
        unplaced += Unplaced(o.orderId, o.orderNo, whyNot(o, loads.values, maxLive))
        continue
      }
      picks += Pick(o.orderId, o.orderNo, best.id, best.name)
      loads[best.id] = best.copy(
        liveCount = best.liveCount + 1,
        liveExpectedMinor = best.liveExpectedMinor + maxOf(0L, o.expectedCollectMinor),
      )
    }

    val blocked = couriers.mapNotNull { c ->
      val now = loads[c.id] ?: c
      val why = CourierCash.autoBlockReason(now)
        ?: if (maxLive != null && now.liveCount >= maxLive) "$maxLive مأموریتِ هم‌زمان دارد (سقفِ کارِ هم‌زمان)" else null
      why?.let { Blocked(c.id, c.name, it) }
    }
    return Plan(picks, unplaced, blocked)
  }

  private fun whyNot(o: Candidate, loads: Collection<CourierRow>, maxLive: Int?): String {
    if (loads.isEmpty()) return "هیچ پیکِ فعالی نیست"
    val free = loads.filter { maxLive == null || it.liveCount < maxLive }
    if (free.isEmpty()) return "همهٔ پیک‌ها به سقفِ کارِ هم‌زمان ($maxLive) رسیده‌اند"
    val most = free.maxOf { CourierCash.headroom(it) }
    return "نقدِ این سفارش (${OrderText.money(o.expectedCollectMinor)} افغانی) در جای خالیِ نقدِ هیچ پیکی جا نمی‌شود " +
      "(بیشترین جای خالی: ${OrderText.money(most)}) — پیک‌ها را تسویه کنید"
  }
}
