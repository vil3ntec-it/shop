package ir.vil3ntec.tohid.core.account

/**
 *  اشتراکِ کش‌شده (آخرین تپشِ سرور، `HeartbeatCache`) با **ساعتِ مطمئنِ همین دستگاه** — خواستِ صاحب‌کار ۱۴۰۵/۰۸/۲۲:
 *  «اشتراک تکون نمی‌خورد و بی اینترنت تا ابد کار می‌کند».
 *
 *  پیش از این برنامه `active` و `daysLeft` ِ آخرین تپش را همان‌طور می‌خواند ⇒ دستگاهی که آنلاین نمی‌شد همان «۲۵ روز» و
 *  همان «فعال» را برای همیشه داشت، و قفلِ نرم فقط با تپشِ «تمام‌شده» بسته می‌شد که به دستگاهِ آفلاین هرگز نمی‌رسید.
 *  حالا:
 *    • روزِ مانده = تا پایانِ مهلت (`graceEndsAt`، وگرنه `endsAt`) از همان ساعتِ مطمئن — هر روز کم می‌شود، با یا بی اینترنت؛
 *    • «فعال» ِ کش‌شده فقط تا همان پایان؛ پس از آن «تمام‌شده» حساب می‌شود (سرور هنوز چیزی نگفته باشد هم).
 *  ⛔ ساعت از بیرون می‌آید (`LicenseGuard.trustedNow`: بالاترین ساعتِ دیده‌شده؛ عقب بردنِ ساعتِ گوشی چیزی را باز نمی‌کند).
 *  ⛔ دائمی و آزمایشیِ بی تاریخ (پایانِ صفر) همان پاسخِ سرور می‌مانند.
 */
object SubscriptionClock {
  const val DAY = 86_400_000L

  data class Cached(
    val active: Boolean,
    val permanent: Boolean,
    /** `active` | `expired` | `trial` | `none` | … (همان تپش) */
    val status: String,
    val endsAt: Long,
    val graceEndsAt: Long,
    /** عددِ همان تپش؛ فقط وقتی پایان نامعلوم است */
    val daysLeft: Int,
  )

  /** پایانِ واقعی (با مهلت)؛ ۰ ⇒ نامعلوم */
  fun end(c: Cached): Long = if (c.graceEndsAt > 0) c.graceEndsAt else c.endsAt

  /** «فعال» ِ کش‌شده هنوز درست است؟ فقط تا پایانِ خودش با ساعتِ مطمئن */
  fun activeNow(c: Cached, now: Long): Boolean {
    if (!c.active) return false
    if (c.permanent) return true
    val e = end(c)
    return e <= 0 || now <= e
  }

  /** روزِ مانده از همین لحظه؛ پایانِ نامعلوم ⇒ همان عددِ تپش */
  fun daysLeft(c: Cached, now: Long): Int {
    if (c.permanent) return c.daysLeft
    val e = end(c)
    if (e <= 0) return c.daysLeft.coerceAtLeast(0)
    if (now >= e) return 0
    return ((e - now + DAY - 1) / DAY).toInt()
  }

  /** قفلِ نرم از تپشِ کش‌شده: اشتراکی بوده (نه «none») و دیگر فعال نیست */
  fun ended(c: Cached, now: Long): Boolean = !c.permanent && c.status != "none" && c.status.isNotEmpty() && !activeNow(c, now)
}
