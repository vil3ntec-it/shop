package ir.vil3ntec.tohid.update

import android.content.Context

/**
 *  «برگهٔ برنامه با نامِ تازه یک بار خودش باز شد» (`AppMove.seenKey`) — انبارِ سکو (`tohid` ِ SharedPreferences)، جدا از
 *  `UpdateManager` تا منطقِ مشترک بی وابستگیِ پلتفرمی بماند. خواندن یا نوشتنِ ناموفق ⇒ «ندیده» (برگه یک بار دیگر باز می‌شود).
 */
internal object AppMoveSeen {
  private fun prefs(context: Context) = context.getSharedPreferences("tohid", Context.MODE_PRIVATE)

  fun seen(context: Context, to: String): Boolean =
    runCatching { prefs(context).getBoolean(AppMove.seenKey(to), false) }.getOrDefault(false)

  fun mark(context: Context, to: String) {
    runCatching { prefs(context).edit().putBoolean(AppMove.seenKey(to), true).apply() }
  }
}
