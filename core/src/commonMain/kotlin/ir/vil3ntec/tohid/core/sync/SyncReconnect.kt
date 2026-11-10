package ir.vil3ntec.tohid.core.sync

/**
 *  SYN-12 (§۶.۱۲): کنترلِ هجوم پس از وصلِ دوباره. پس از قطعیِ سراسری هزاران دستگاه با هم به شبکه برمی‌گردند؛ دورِ
 *  **نخستِ** پس از برگشتنِ شبکهٔ خودِ دستگاه (رویدادِ `online` ِ مرورگر، یا دورِ خودکاری که پس از دیدنِ بی‌نتی می‌آید)
 *  با درنگِ تصادفیِ یکنواختِ ۰ تا [SPREAD_MS] پخش می‌شود تا همه با هم به سرور نکوبند.
 *
 *  ⛔ دورِ کاربر («همگام‌سازی حالا»)، تغییرِ تازهٔ دفتر و تلاشِ پس از شکست (فاصلهٔ تلاشِ ۲ث × ۲ⁿ ±۲۵٪) هرگز منتظر
 *     نمی‌مانند و هر کدام که اجرا شد درنگ را پایان می‌دهد؛ فقط دورهای زمان‌دار (تیک و Pull ِ دوره‌ای) تا پایانِ درنگ صبر
 *     می‌کنند. رویدادِ `online` ِ دوباره درنگِ جاری را دراز نمی‌کند.
 *  ⛔ صف، بدنهٔ push و فاصلهٔ تلاش این‌جا عوض نمی‌شوند؛ عددِ تصادفیِ نادرست ⇒ بی درنگ (همان رفتارِ پیشین).
 *
 *  قرینهٔ `reconnectDelayMs` / `reconnectHold` در `license/sync-core.js`؛ هر دو با بخش‌های `reconnectDelay` و
 *  `reconnectHold` ِ `core/test-vectors/sync-outage-v1.json`. Kotlinِ خالص (commonMain)، بی java.*.
 */
object SyncReconnect {
  /** سقفِ درنگ — کم‌تر از آستانهٔ اعلانِ قطعی ([SyncOutage.SUSTAIN_MS]) تا خودِ درنگ هرگز اعلان نسازد */
  const val SPREAD_MS: Long = 15_000L

  /** شبکهٔ خودِ دستگاه برگشت */
  const val RECONNECT = "reconnect"

  /** تیک یا Pull ِ دوره‌ای */
  const val TIMER = "timer"

  /** «همگام‌سازی حالا» ِ کاربر */
  const val USER = "user"

  /** تغییرِ تازهٔ دفتر */
  const val CHANGE = "change"

  /** تلاشِ پس از شکست (فاصلهٔ تلاشِ موتور) */
  const val RETRY = "retry"

  /** درنگ از عددِ تصادفیِ [0, 1] (بیرون از بازه ⇒ لبه)؛ NaN یا بی‌نهایت ⇒ ۰ */
  fun delayMs(random: Double): Long =
    if (random.isNaN() || random.isInfinite()) 0L else (random.coerceIn(0.0, 1.0) * SPREAD_MS).toLong()

  /** `until`: پایانِ درنگِ جاری (۰ = بی درنگ)؛ `waitMs`: این دور چند میلی‌ثانیه صبر کند (۰ = همین حالا) */
  data class Hold(val until: Long, val waitMs: Long)

  /**
   *  @param until پایانِ درنگِ جاری (۰ = بی درنگ)
   *  @param cause [RECONNECT] / [TIMER] / [USER] / [CHANGE] / [RETRY]؛ ناشناخته ⇒ همین حالا
   *  @param random عددِ تصادفیِ [0, 1) — فقط برای [RECONNECT] ِ بی درنگِ جاری خوانده می‌شود
   */
  fun hold(until: Long, cause: String, now: Long, random: Double): Hold {
    if (until > now && (cause == RECONNECT || cause == TIMER)) return Hold(until, until - now)
    val wait = if (cause == RECONNECT) delayMs(random) else 0L
    return if (wait > 0) Hold(now + wait, wait) else Hold(0L, 0L)
  }
}
