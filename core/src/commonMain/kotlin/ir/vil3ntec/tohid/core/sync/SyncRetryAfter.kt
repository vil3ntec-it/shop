package ir.vil3ntec.tohid.core.sync

/**
 *  SYN-12، تصمیمِ ۳-۱۳ ِ صاحب‌کار (پرسشِ ۱ الف): `Retry-After` ِ سرور پذیرفته می‌شود، با سقفِ [MAX_MS].
 *
 *  سرور وقتی شلوغ است یا سقفِ درخواست پر شده (۴۲۹ ِ `ratelimit`، ۵۰۳ ِ `db_busy` و `request_timeout`) می‌گوید «پس از چند ثانیه
 *  دوباره بیا». دورِ **خودکارِ** بعدی (تیک، Pull ِ دوره‌ای، پس از تغییر، پس از وصلِ دوباره، تلاشِ پس از شکست) زودتر از آن نمی‌رود؛
 *  «همگام‌سازی حالا» ِ کاربر هرگز صبر نمی‌کند و همان دم پایانش می‌دهد. سقف نمی‌گذارد پاسخِ خراب (یا ساعتِ عوض‌شده) دستگاه را
 *  بیش از ۵ دقیقه نگه دارد — همان سقفِ فاصلهٔ تلاشِ موتورها.
 *
 *  ⛔ فقط عددِ ثانیه (همان که این سرور می‌فرستد)؛ تاریخِ HTTP، منفی، اعشاری، خالی یا بزرگ‌تر از `Long` ⇒ «نگفت» (null، همان رفتارِ
 *     پیشین). دو عدد در دو پاسخ ⇒ دیرترین پایان (هر دو زیرِ سقفِ همان لحظه).
 *  ⛔ صف، بدنهٔ push و فاصلهٔ تلاشِ خودِ موتور این‌جا عوض نمی‌شوند؛ این فقط «زودتر نه» است. Kotlinِ خالص (commonMain)، بی java.*.
 */
object SyncRetryAfter {
  /** سقفِ درنگِ پذیرفته — همان `MAX_BACKOFF_MS` ِ موتورهای v1 و v3 */
  const val MAX_MS: Long = 5 * 60_000L

  private val SECONDS = Regex("^[0-9]{1,9}$")

  /** سرآیندِ `Retry-After` ⇒ میلی‌ثانیه (تا [MAX_MS])؛ نگفت یا نادرست ⇒ null */
  fun parse(header: String?): Long? {
    val s = header?.trim() ?: return null
    if (!SECONDS.matches(s)) return null
    return (s.toLong() * 1000L).coerceAtMost(MAX_MS)
  }

  /**
   *  پایانِ «زودتر نه» پس از پاسخی با [hintMs]: دیرترِ پایانِ جاری ([until]) و `now + hint`، هرگز بیش از `now + MAX_MS`.
   *  [hintMs] = null یا ≤ ۰ ⇒ همان [until].
   */
  fun until(until: Long, now: Long, hintMs: Long?): Long {
    if (hintMs == null || hintMs <= 0L) return until
    val next = now + hintMs.coerceAtMost(MAX_MS)
    return maxOf(until.coerceAtMost(now + MAX_MS), next)
  }

  /** این دور چند میلی‌ثانیه صبر کند: کاربر ⇒ ۰؛ گذشته ⇒ ۰؛ ساعتِ به‌عقب‌رفته ⇒ هرگز بیش از [MAX_MS] */
  fun waitMs(until: Long, now: Long, user: Boolean): Long =
    if (user || until <= now) 0L else (until - now).coerceAtMost(MAX_MS)
}
