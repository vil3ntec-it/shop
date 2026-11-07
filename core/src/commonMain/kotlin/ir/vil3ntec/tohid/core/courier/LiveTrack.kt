package ir.vil3ntec.tohid.core.courier

import kotlin.math.abs

/**
 *  DLV-16 — «موقعیتِ پیک فقط در زمانِ مأموریت» (§۳.۱۱: سرویسِ پیش‌زمینه از نوعِ `location` فقط در حینِ مأموریت).
 *
 *  منطقِ خالصِ سرویسِ پیش‌زمینهٔ برنامهٔ پیک (`ui/courier/CourierLiveTrackAndroid.kt` ⇐ `CourierTrackAndroid.kt`)، بی سکو تا
 *  روی JVM و آیفون آزموده شود. سرور داور است (`server/src/lib/delivery-live.js` ‏`putLocation`)؛ این‌جا فقط این‌که
 *  برنامه **کی** بپرسد و بفرستد:
 *
 *  - **کدام مأموریت:** فقط کارتی که سرور برایش `tracking.location` داد (هر دو پرچمِ دکان و «در راه» ِ سرور، همان
 *    شرطِ `putLocation`) **و** وضعیتی که پیک روی گوشی می‌بیند (سرور + صفِ نرفته) هنوز «در راه» است — «رسیدم» ِ
 *    بی اینترنت همان دم می‌بندد، «گرفتم» ِ نرفته تا سرور نپذیرفته باز نمی‌کند.
 *  - **هر چند وقت:** نه تندتر از [PERIOD_MS] (باتری) و نه تندتر از `minIntervalMs` ِ سرور.
 *  - **فقط آخرین نقطه، هرگز صف:** نقطهٔ کهنه‌تر از [MAX_FIX_AGE_MS] یا نامعتبر فرستاده نمی‌شود و نقطه‌ای که نرسید
 *    (بی اینترنت) دور ریخته می‌شود؛ سرور هم فقط آخرین نقطه را و فقط ۱۰ دقیقه نگه می‌دارد.
 *  - **«دیگر نه» ِ سرور** ([STOP_CODES]) آن مأموریت را تا کارتِ تازه‌اش (نسخهٔ دیگرِ `updatedAt`) کنار می‌گذارد؛
 *    هیچ هدفی نماند ⇒ سرویس خودش را می‌بندد.
 */
object LiveTrack {

  /** همان `ON_THE_WAY` ِ سرور (`delivery-live.js`): تنها بازه‌ای که موقعیت گرفته و نشان داده می‌شود */
  val ON_THE_WAY: Set<String> = setOf("picked_up", "moving")

  /** کمترین فاصلهٔ دو فرستادن برای یک مأموریت (و گامِ حلقهٔ سرویس) */
  const val PERIOD_MS: Long = 30_000L

  /** نقطهٔ کهنه‌تر از این «همین حالا» نیست — فرستاده نمی‌شود */
  const val MAX_FIX_AGE_MS: Long = 60_000L

  /** ساعتِ فراهم‌کنندهٔ موقعیت کمی جلوتر از ساعتِ گوشی پذیرفته است، بیشتر نه */
  const val MAX_FIX_SKEW_MS: Long = 5_000L

  /**
   *  پاسخ‌هایی که با دوباره فرستادن درست نمی‌شوند: مأموریت دیگر «در راه» نیست، پیدا نیست (پس‌گرفته/پیکِ دیگر)،
   *  دکان موقعیتِ زنده را خاموش کرد، پیک غیرفعال شد، یا نشستِ پیک تمام شد.
   */
  val STOP_CODES: Set<String> = setOf(
    "not_on_the_way", "assignment_not_found", "live_location_disabled", "courier_inactive", "session_expired", "courier_scope",
  )

  /** یک مأموریت که موقعیت می‌خواهد. [version] = `updatedAt` ِ کارتِ سرور (کنارگذاشتن تا کارتِ تازه) */
  data class Target(val assignmentId: String, val intervalMs: Long, val version: Long)

  /** یک نقطهٔ دستگاه. [accuracyM]: شعاعِ خطا به متر، null = نامعلوم */
  data class Fix(val lat: Double, val lng: Double, val accuracyM: Int?, val at: Long)

  /** کارت‌هایی که اکنون موقعیت می‌خواهند — به ترتیبِ کارت‌های سرور، هر شناسه یک بار */
  fun targets(cards: List<MissionCard>, queue: QueueState): List<Target> =
    cards.asSequence()
      .filter { it.trackLocation && CourierQueue.projectedStatus(it.status, it.id, queue) in ON_THE_WAY }
      .distinctBy { it.id }
      .map { Target(it.id, maxOf(PERIOD_MS, it.trackMinIntervalMs), it.updatedAt) }
      .toList()

  /** نقطه درست و تازه است؟ (مختصاتِ معتبر، نه کهنه‌تر از [MAX_FIX_AGE_MS]، نه بیش از [MAX_FIX_SKEW_MS] در آینده) */
  fun usable(fix: Fix?, now: Long): Boolean {
    if (fix == null) return false
    if (!fix.lat.isFinite() || !fix.lng.isFinite() || abs(fix.lat) > 90.0 || abs(fix.lng) > 180.0) return false
    if (fix.lat == 0.0 && fix.lng == 0.0) return false   //  «جزیرهٔ صفر»: نقطهٔ پیش‌فرضِ فراهم‌کنندهٔ خراب
    val age = now - fix.at
    return age <= MAX_FIX_AGE_MS && age >= -MAX_FIX_SKEW_MS
  }

  /** شعاعِ خطا برای سرور (۰ تا ۱۰۰٬۰۰۰ متر، `delivery-live.js`)؛ نامعلوم یا نامعتبر ⇒ null */
  fun accuracyOf(meters: Float): Int? =
    if (meters.isFinite() && meters > 0f) minOf(100_000, kotlin.math.round(meters).toInt()) else null

  /**
   *  حالتِ فرستنده در یک بارِ روشن بودنِ سرویس: آخرین فرستادنِ پذیرفته‌شدهٔ هر مأموریت و مأموریت‌های کنارگذاشته.
   *  چیزی روی گوشی نوشته نمی‌شود (نه نقطه، نه زمان).
   */
  class Pacer {
    private val sentAt = HashMap<String, Long>()
    private val dropped = HashMap<String, Long>()

    /** هدف‌هایی که سرور «دیگر نه» نگفته (یا کارتشان پس از آن تازه شده) */
    fun live(targets: List<Target>): List<Target> = targets.filter { dropped[it.assignmentId] != it.version }

    /** از [live]، آن‌ها که نوبتشان رسیده */
    fun due(targets: List<Target>, now: Long): List<Target> = live(targets).filter { t ->
      val last = sentAt[t.assignmentId] ?: return@filter true
      now - last >= t.intervalMs || now < last   //  ساعتِ گوشی عقب رفت ⇒ دوباره نوبت
    }

    fun sent(t: Target, now: Long) { sentAt[t.assignmentId] = now }

    /** شکست: کدِ «دیگر نه» ⇒ کنار تا کارتِ تازه؛ بقیه (بی اینترنت، سرورِ گرفتار) ⇒ نوبتِ بعد دوباره، بی صف */
    fun failed(t: Target, code: String) { if (code in STOP_CODES) dropped[t.assignmentId] = t.version }

    /** مأموریتی که دیگر کارتش نیست از یاد می‌رود */
    fun forget(keep: Collection<String>) {
      val ids = keep.toSet()
      sentAt.keys.retainAll(ids)
      dropped.keys.retainAll(ids)
    }
  }

  /** نتیجهٔ یک فرستادن: پذیرفته، یا کدِ خطا (کدِ خطای سرور یا شبکه) */
  data class Sent(val ok: Boolean, val code: String = "")

  /**
   *  یک دورِ سرویس بی سکو: هدف‌ها ⇐ نوبت‌رسیده‌ها ⇐ (اگر بود) یک نقطه ⇐ فرستادن برای هر کدام.
   *  نقطه فقط وقتی پرسیده می‌شود که دست‌کم یک مأموریت نوبتش رسیده. ⇒ شمارِ هدف‌های زندهٔ پس از این دور
   *  (۰ ⇒ سرویس بسته شود).
   */
  inline fun tick(
    cards: List<MissionCard>,
    queue: QueueState,
    pacer: Pacer,
    now: Long,
    fix: () -> Fix?,
    send: (Target, Fix) -> Sent,
  ): Int {
    val targets = targets(cards, queue)
    pacer.forget(targets.map { it.assignmentId })
    val due = pacer.due(targets, now)
    if (due.isNotEmpty()) {
      val f = fix()
      if (f != null && usable(f, now)) {
        for (t in due) {
          val r = send(t, f)
          if (r.ok) pacer.sent(t, now) else pacer.failed(t, r.code)
        }
      }
    }
    return pacer.live(targets).size
  }
}
