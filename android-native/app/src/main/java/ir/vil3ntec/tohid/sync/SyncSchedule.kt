package ir.vil3ntec.tohid.sync

/**
 *  زمان‌بندیِ همگام‌سازی — خالص، بدونِ اندروید.
 *
 *  «کِی بفرستیم» از «چطور بفرستیم» جدا شده تا بشود بدونِ گوشی و بدونِ
 *  صبر کردنِ واقعی سنجیدش. `AutoSync` فقط این را صدا می‌زند و می‌خوابد.
 */
object SyncSchedule {

  /** مکث پس از آخرین تغییر — چند تغییرِ پشتِ سرِ هم یک بار می‌روند */
  const val QUIET_MS = 2_000L

  /** حتی اگر تغییرها ادامه داشته باشند، فرستادن بیشتر از این عقب نمی‌افتد */
  const val MAX_WAIT_MS = 10_000L

  /** فاصلهٔ گرفتنِ تغییرهای دیگران، تا وقتی برنامه جلوی چشم است */
  const val POLL_MS = 60_000L

  /** فاصلهٔ تلاشِ دوباره: ۵ ثانیه، ۱۵ ثانیه، یک دقیقه، پنج دقیقه */
  val BACKOFF_MS = longArrayOf(5_000, 15_000, 60_000, 300_000)

  /**
   *  چقدر صبر کنیم پیش از فرستادن.
   *
   *  مکثِ عادی `QUIET_MS` است، ولی از شروعِ پنجرهٔ فعلی بیشتر از
   *  `MAX_WAIT_MS` عقب نمی‌افتیم. بدونِ آن سقف، کسی که پیوسته کار
   *  می‌کند هر بار مکث را از نو شروع می‌کرد و تغییرهایش تا وقتی دست
   *  بکشد روی گوشی می‌ماند — درست خلافِ «در جا روی سرور».
   *
   *  @param windowStart لحظهٔ اولین تغییرِ این پنجره؛ صفر یعنی پنجره تازه است
   */
  fun waitAfterChange(windowStart: Long, now: Long): Long {
    if (windowStart <= 0L) return QUIET_MS
    val waited = now - windowStart
    val remaining = MAX_WAIT_MS - waited
    return if (remaining <= 0L) 0L else minOf(QUIET_MS, remaining)
  }

  /** فاصلهٔ تلاشِ بعدی؛ از آخرین پله بیشتر نمی‌رود */
  fun backoffFor(attempt: Int): Long =
    BACKOFF_MS[attempt.coerceIn(0, BACKOFF_MS.lastIndex)]

  /**
   *  آیا اصلاً تلاشِ دوباره معنی دارد.
   *
   *  @param unsent تغییرِ محلی که هنوز نرفته
   *  @param failed آخرین تلاش شکست خورد
   *  @param ready هم نشانی هست هم حساب
   *
   *  اگر چیزی برای فرستادن نمانده و شکستی هم نبوده، شبکه را بی‌دلیل
   *  بیدار نمی‌کنیم. و اگر حساب رفته — نشست تمام شده — هزار بار
   *  امتحان کردن هم جواب نمی‌دهد؛ کاربر باید دوباره وارد شود.
   */
  fun shouldRetry(unsent: Boolean, failed: Boolean, ready: Boolean): Boolean {
    if (!unsent && !failed) return false
    if (failed && !ready) return false
    return true
  }
}
