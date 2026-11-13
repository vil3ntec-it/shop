package ir.vil3ntec.tohid.core.courier

/*
 *  DLV-06 / S5 — «تقریباً کی می‌رسم» ِ خودِ پیک برای صفحهٔ پیگیریِ مشتری (`POST /api/courier/missions/:id/eta`،
 *  `lib/delivery-live.putEta`). ⛔ فقط با `etaEnabled` ِ `/courier/me` (پرچمِ `shop.delivery_customer_view`) و فقط
 *  «در راه» — همان `ON_THE_WAY` ِ سرور؛ سرور مالکیت، وضعیت و بازه را دوباره می‌سنجد. فقط آنلاین (بی صف): زمانِ
 *  دیررسیده بی‌معنی است.
 *
 *  منطقِ خالص؛ آزمون: `CourierEtaTest` (commonTest).
 */
object CourierEta {

  /** همان `ON_THE_WAY` ِ `server/src/lib/delivery-live.js` */
  val ON_THE_WAY: Set<String> = setOf("picked_up", "moving")

  /** همان `MAX_ETA_MINUTES` ِ سرور */
  const val MAX_MINUTES = 240

  val CHOICES: List<Int> = listOf(5, 10, 15, 20, 30, 45, 60)

  fun visible(enabled: Boolean, status: String): Boolean = enabled && status in ON_THE_WAY

  fun valid(minutes: Int): Boolean = minutes in 1..MAX_MINUTES
}
