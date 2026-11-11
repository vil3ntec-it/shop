package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.sync.SyncReconnect
import ir.vil3ntec.tohid.core.sync.SyncRetryAfter
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 *  SYN-12: هجومِ پس از وصلِ دوباره روی گوشی و کامپیوتر — همان قاعدهٔ `SyncReconnect` ِ core (`reconnectHold` ِ وب).
 *
 *  `SyncV1Worker` درِ یگانهٔ دورِ خودکارِ هر دو موتور است (v1 و v3؛ دوره‌ای، پس از تغییر، پیمایشِ `AutoSync`) و پیش از
 *  هر دورِ شبکه‌دار [beforeRound] را می‌پرسد. دستگاهی که بی‌نتی‌اش دیده شد ([offline]: دورِ بی‌نت، شکستِ «اینترنت وصل
 *  نیست»، تغییرِ ثبت‌شده یا پیمایشِ بی‌نت) نخستین دورِ خودکار پس از برگشتنِ شبکه را با درنگِ یکنواختِ ۰ تا
 *  `SyncReconnect.SPREAD_MS` می‌زند و دورهای خودکارِ دیگرِ همان پنجره تا پایانِ همان درنگ صبر می‌کنند. «همگام‌سازی
 *  حالا» ([userAsked]) هرگز صبر نمی‌کند و درنگ را پایان می‌دهد.
 *
 *  ⛔ فقط در حافظهٔ همین فرآیند؛ صف، بدنهٔ push و فاصلهٔ تلاشِ موتور و WorkManager دست نمی‌خورند. شکستِ سرور یا راهِ
 *     سرور (دستگاه شبکه دارد) [offline] نیست، پس تلاشِ پس از آن همان فاصلهٔ پیشین را دارد.
 *  ⛔ قفل ندارد: دو کارگرِ هم‌زمان در بدترین حالت هر کدام درنگِ خودش را می‌گیرد — هر دو درونِ همان بازه.
 *
 *  ۳-۱۳ (پرسشِ ۱ الف): `Retry-After` ِ پاسخِ سرور ([serverAsked]) هم همین‌جاست — دورهای خودکار تا پایانش صبر می‌کنند
 *  (`SyncRetryAfter`، سقفِ ۵ دقیقه)؛ «همگام‌سازی حالا» نه و پایانش می‌دهد. درنگِ بلندتر از پخشِ وصلِ دوباره را کارگر در خودش
 *  نمی‌خوابد ([SyncV1Worker]: `Result.retry()` و دورِ بعدی دوباره می‌پرسد).
 */
internal class ReconnectGate(
  private val now: () -> Long = monotonicMs(),
  private val random: () -> Double = { Random.nextDouble() },
) {
  @Volatile private var away = false
  @Volatile private var until = 0L
  @Volatile private var user = false
  @Volatile private var server = 0L

  /** شبکهٔ خودِ دستگاه نبود */
  fun offline() { away = true }

  /** دورِ بعدی را کاربر خواست («همگام‌سازی حالا») */
  fun userAsked() { user = true }

  /** پاسخِ سرور `Retry-After` داشت (میلی‌ثانیه، `ApiFailure.retryAfterMs`)؛ null ⇒ هیچ */
  fun serverAsked(hintMs: Long?) { server = SyncRetryAfter.until(server, now(), hintMs) }

  /** پیش از دورِ خودکاری که شبکه دارد: چند میلی‌ثانیه صبر کند (۰ = همین حالا) */
  fun beforeRound(): Long {
    val cause = when {
      user -> SyncReconnect.USER
      away -> SyncReconnect.RECONNECT
      else -> SyncReconnect.TIMER
    }
    user = false
    away = false
    val at = now()
    val h = SyncReconnect.hold(until, cause, at, random())
    until = h.until
    if (cause == SyncReconnect.USER) server = 0L
    return maxOf(h.waitMs, SyncRetryAfter.waitMs(server, at, cause == SyncReconnect.USER))
  }
}

/** ساعتِ یکنواخت (عوض شدنِ ساعتِ دستگاه درنگ را دراز یا کوتاه نمی‌کند) */
private fun monotonicMs(): () -> Long {
  val start = TimeSource.Monotonic.markNow()
  return { start.elapsedNow().inWholeMilliseconds }
}
