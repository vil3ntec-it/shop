package ir.vil3ntec.tohid.core.sync

/**
 *  SYN-14 — همگام‌سازیِ عکس‌ها جدا از دفتر، **با اولویتِ پایین‌تر و قابلِ ادامه** (بخشِ ۳.۸).
 *
 *  `PhotoSync` ِ برنامه شبکه و دیسک را می‌زند؛ تصمیم‌ها این‌جاست تا با آزمونِ واحد سنجیده شوند (کاتلینِ خالص،
 *  گوشی، کامپیوتر و آیفون؛ بی `java.*`/`android.*`):
 *  - **ادامه از جای قطع:** نشانهٔ `rev` ِ عکس‌ها فقط تا پیش از نخستین ردیفِ ناتمام جلو می‌رود؛ دانلودِ قطع‌شده
 *    دیگر برای همیشه جا نمی‌ماند (پیش‌تر نشانه تا تهِ صفحه می‌رفت و آن عکس تا تغییرِ بعدی‌اش نمی‌آمد).
 *  - **ردیفِ خراب صف را نمی‌بندد:** همان ردیف پس از [MAX_TRIES] بارِ ناموفق رد می‌شود.
 *  - **اولویتِ پایین:** تا همگام‌سازیِ دفتر مشغول است عکس صبر می‌کند، رویهم حداکثر [MAX_WAIT_MS] در هر دور.
 */
object PhotoSyncPlan {

  /** یک ردیفِ `/sync/v1/photos?since=` و این‌که کارِ این دستگاه با آن تمام شد (گرفت، پاک کرد یا لازم نبود) */
  data class Item(val rev: Long, val done: Boolean)

  /** نشانهٔ بعدی: تا پیش از نخستین ردیفِ ناتمام؛ همه تمام ⇒ `head` ِ صفحه. هرگز عقب نمی‌رود. */
  fun cursor(since: Long, items: List<Item>, head: Long): Long {
    var c = since
    for (it in items) {
      if (!it.done) return c
      if (it.rev > c) c = it.rev
    }
    return maxOf(c, head)
  }

  /** صفحه ناتمام ماند ⇒ صفحهٔ بعد خوانده نمی‌شود؛ دورِ بعد از همان ردیف */
  fun stopped(items: List<Item>): Boolean = items.any { !it.done }

  /** شمارِ تلاشِ یک ردیفِ ناموفق پیش از رد شدن */
  const val MAX_TRIES = 5

  /** ردیفِ گیرکرده (`stuckRev`) با شمارِ تلاشش؛ رسیدن به [MAX_TRIES] ⇒ رد شود */
  fun giveUp(stuckRev: Long, stuckTries: Int, rev: Long): Boolean = stuckRev == rev && stuckTries >= MAX_TRIES

  /** پس از یک شکستِ دیگرِ [rev]: (ردیف، شمار) ِ تازه */
  fun failed(stuckRev: Long, stuckTries: Int, rev: Long): Pair<Long, Int> =
    rev to (if (stuckRev == rev) stuckTries + 1 else 1)

  const val WAIT_STEP_MS = 2_000L
  const val MAX_WAIT_MS = 60_000L

  /**
   *  اولویتِ پایین‌تر از دفتر: دفتر مشغول ⇒ چند میلی‌ثانیه صبر (هر بار حداکثر [WAIT_STEP_MS])؛ `0` ⇒ برو.
   *  رویهم حداکثر [MAX_WAIT_MS] در هر دور تا دفترِ همیشه‌مشغول عکس را برای همیشه نخواباند.
   */
  fun waitMs(ledgerBusy: Boolean, waitedMs: Long): Long =
    if (!ledgerBusy || waitedMs >= MAX_WAIT_MS) 0L else minOf(WAIT_STEP_MS, MAX_WAIT_MS - waitedMs)
}
