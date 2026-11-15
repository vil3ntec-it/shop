package ir.vil3ntec.tohid.data.platform

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.data.SupportDiag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  سیم‌کشیِ «جزئیات برای پشتیبانی» (`data/SupportDiag`): یادِ آخرین گرهِ فرستاده در `tohid-support-diag`، صفِ OPS-03 و
 *  فرستادن با همان `CrashReporter.flushIds` (`POST /api/errors`). هرگز خطا بیرون نمی‌دهد: گزارشِ پشتیبانی نباید صفحه را بشکند.
 */
object SupportDiagAndroid {

  private const val PREFS = "tohid-support-diag"

  /**
   *  گرهِ [kind] با متنِ خامِ [detail] و جزئیاتِ [facts] ⇒ صف و فرستادن (همان گره تا `SupportDiag.REPEAT_MS` یک بار).
   *  ⇒ `true` فقط وقتی سرور همین گره را گرفته است («پشتیبانی خبردار شد»)؛ نت نیست ⇒ `false` و گزارش در صف می‌ماند تا
   *  اجرای بعدیِ برنامه (`CrashReporter.flush` ِ شروع).
   */
  suspend fun report(context: Context, kind: String, detail: String, facts: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
    runCatching {
      val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      val now = System.currentTimeMillis()
      val r = SupportDiag.report(kind, detail, facts, AppConfig.appVersion, now)
      if (!SupportDiag.due(prefs.getString("sig", null), prefs.getLong("at", 0L), r.signature, now)) {
        return@runCatching prefs.getString("sent", null) == r.signature || flushed(context, prefs, r.signature, prefs.getString("id", null))
      }
      prefs.edit().putString("sig", r.signature).putLong("at", now).putString("id", r.id).remove("sent").apply()
      CrashReporter.enqueue(context, r, now)
      flushed(context, prefs, r.signature, r.id)
    }.getOrDefault(false)
  }

  /** فرستادنِ صف؛ «فرستاده» را خودِ `CrashReporter.flushIds` با [markSent] می‌نویسد */
  private suspend fun flushed(context: Context, prefs: android.content.SharedPreferences, signature: String, id: String?): Boolean {
    if (id == null) return false
    CrashReporter.flushIds(context)
    return prefs.getString("sent", null) == signature
  }

  /** سرور این گزارش‌ها را گرفت: اگر آخرین گرهِ پشتیبانی در میانشان است، «فرستاده» */
  fun markSent(context: Context, ids: Set<String>) {
    if (ids.isEmpty()) return
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val id = prefs.getString("id", null) ?: return
    if (id in ids) prefs.edit().putString("sent", prefs.getString("sig", null)).apply()
  }
}
