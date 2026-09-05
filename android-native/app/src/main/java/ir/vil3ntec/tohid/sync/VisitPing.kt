package ir.vil3ntec.tohid.sync

import android.content.Context
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  «من آمدم» — یک تپشِ کوچک هنگام باز شدنِ برنامه.
 *
 *  ── مشکلی که این حل می‌کند ─────────────────────────────────────────
 *  پنل مدیریت فقط کسانی را می‌دید که ثبت‌نام کرده بودند. کسی که برنامه
 *  را نصب کرده و باز کرده ولی هنوز حساب نساخته — یعنی دقیقاً همان کسی
 *  که باید دنبالش رفت — هیچ‌جا شمرده نمی‌شد.
 *
 *  ── چه چیزی می‌رود ─────────────────────────────────────────────────
 *  شناسهٔ همین دستگاه، سکو و نسخه. توکن اگر باشد، تا بازدید به همان
 *  حساب بچسبد. هیچ داده‌ای از دفترِ دکان — نه فروشی، نه کالایی، نه
 *  نامی.
 *
 *  ── چرا هر شش ساعت یک بار ──────────────────────────────────────────
 *  همان قاعدهٔ `LocationPing`. برنامه‌ای که روزی سی بار باز می‌شود،
 *  سی رفت‌وبرگشتِ بی‌فایده نمی‌سازد و شمارشِ بازدید هم بی‌معنی نمی‌شود.
 *
 *  ── چه چیزی برمی‌گردد ──────────────────────────────────────────────
 *  فقط عددِ پیام‌های خوانده‌نشدهٔ پشتیبانی، تا نقطهٔ قرمز نشان داده شود.
 *  هیچ خطایی به بیرون نمی‌رود: تپش یک خبر است، نه یک شرط.
 */
object VisitPing {

  private const val PREFS = "tohid-visit-ping"
  private const val KEY_SENT = "sentAt"
  private const val EVERY_MS = 6 * 60 * 60 * 1000L

  /** پیام‌های خوانده‌نشدهٔ پشتیبانی — برای نقطهٔ قرمز روی «بیشتر» */
  var supportUnread: Int = 0
    private set

  suspend fun send(context: Context, force: Boolean = false) {
    runCatching {
      if (!AppConfig.isConfigured(context)) return
      val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      val last = prefs.getLong(KEY_SENT, 0)
      if (!force && System.currentTimeMillis() - last < EVERY_MS) return

      val state = SyncStore(context)
      Backend.support(context)
        .visit(
          deviceUid = state.deviceUid,
          platform = "android",
          version = BuildConfig.VERSION_NAME,
        )
        .onSuccess {
          supportUnread = it.supportUnread
          prefs.edit().putLong(KEY_SENT, System.currentTimeMillis()).apply()
        }
    }
  }
}
