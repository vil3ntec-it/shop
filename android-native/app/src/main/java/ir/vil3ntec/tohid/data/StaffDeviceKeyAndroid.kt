package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 *  رازِ دستگاهِ شاگرد برای نشستی که پیش از ۲.۱۴.۵ باز شد (مهاجرتِ آرام، `AuthRepository.ensureStaffDeviceKey`).
 *
 *  سرور ورودِ دوبارهٔ شاگرد با کد را فقط با رازِ همین دستگاه می‌پذیرد. شاگردی که با نسخهٔ کهنه وارد شده رازی ندارد؛
 *  تا نشستش باز است، همین‌جا یک بار از سرور می‌گیرد — وگرنه پس از خروج دیگر با همان کد به حسابِ خودش برنمی‌گشت و
 *  «کدِ تازه از صاحبِ دکان» می‌خواست. صاحب و حسابِ ایمیلی پاسخِ «لازم نیست» می‌گیرند؛ پس حداکثر هر [EVERY_MS] یک
 *  پرسش، و رازِ موجود یعنی هیچ پرسشی. بی انتظار و بی پیام: شکست فقط یعنی «دفعهٔ بعد».
 */
object StaffDeviceKey {

  /** فاصلهٔ دو پرسش (فقط وقتی رازی نیست) */
  const val EVERY_MS: Long = 6L * 60 * 60 * 1000

  private val mutex = Mutex()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @Volatile private var askedAt = 0L

  /** از چرخهٔ عمرِ صفحه (`AppRoot` ⇐ ON_START) — بی انتظار */
  fun ensureSoon(context: Context) {
    val app = context.applicationContext
    scope.launch {
      runCatching {
        mutex.withLock {
          if (!Backend.isReady(app)) return@withLock
          if (!Backend.tokens(app).staffDeviceKey.isNullOrBlank()) return@withLock
          val now = System.currentTimeMillis()
          if (askedAt > 0 && now - askedAt in 0 until EVERY_MS) return@withLock
          askedAt = now
          Backend.auth(app).ensureStaffDeviceKey()
        }
      }
    }
  }
}
