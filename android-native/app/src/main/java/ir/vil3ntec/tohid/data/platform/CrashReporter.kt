package ir.vil3ntec.tohid.data.platform

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.ops.CrashReport
import ir.vil3ntec.tohid.core.ops.CrashReports
import ir.vil3ntec.tohid.data.CrashStore
import ir.vil3ntec.tohid.data.repo.Backend
import java.io.File

/**
 *  OPS-03 — گزارشِ از کار افتادنِ برنامه، با شناسه و بی دادهٔ شخصی.
 *
 *   • `install` (یک بار، سرِ `MainActivity.onCreate`): گیرندهٔ خطای گرفته‌نشده. گزارشِ
 *     پوشانده‌شده (`core/ops/CrashReports`) **هم‌زمان** روی دیسک نوشته می‌شود و بعد همان
 *     گیرندهٔ پیشین (سیستم) کارش را می‌کند — برنامه مثلِ پیش بسته می‌شود.
 *   • `flush` (اجرای بعدی، پس‌زمینه): هر گزارش با `POST /api/errors` و `report_id` ِ خودش؛
 *     پاسخِ ۲xx ⇒ از صف بیرون؛ نت نیست ⇒ می‌ماند تا اجرای بعد. بی حساب هم (بی توکن).
 *   • `notice`: کدِ تازه‌ترین گزارش یک بار به کاربر نشان داده می‌شود («این کد را به
 *     پشتیبانی بگویید»).
 *  ⛔ هیچ‌کدام خطا بیرون نمی‌دهد: گزارشِ خطا نباید خودش برنامه را بشکند.
 *  پوشهٔ مرزیِ `platform/`: فقط سیم‌کشیِ اندروید/کامپیوتر (Context، فایل، گیرندهٔ Thread)؛ منطق در core.
 */
object CrashReporter {

  private const val FILE = "crash-reports.json"
  private val lock = Any()
  @Volatile private var installed = false

  private fun file(context: Context) = File(context.filesDir, FILE)

  private fun read(context: Context): List<CrashReport> =
    runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull().let(CrashStore::decode)

  /** نوشتنِ اتمی: فایلِ موقت و جابه‌جایی — مرگِ پروسه وسطِ نوشتن صف را خراب نمی‌کند */
  private fun write(context: Context, list: List<CrashReport>) {
    val f = file(context)
    val tmp = File(f.parentFile, "$FILE.tmp")
    tmp.writeText(CrashStore.encode(list))
    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
  }

  private fun update(context: Context, change: (List<CrashReport>) -> List<CrashReport>) = synchronized(lock) {
    runCatching { write(context, change(read(context))) }
  }

  fun install(context: Context) {
    if (installed) return
    installed = true
    val app = context.applicationContext
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
      runCatching { record(app, error, System.currentTimeMillis()) }
      //  همان رفتارِ پیشین: گیرندهٔ سیستم (اندروید: بستنِ برنامه)، وگرنه چاپِ خطا مثلِ JVM
      if (previous != null) previous.uncaughtException(thread, error) else error.printStackTrace()
    }
  }

  /** خطا ⇒ گزارشِ پوشانده‌شده در صفِ روی دیسک */
  fun record(context: Context, error: Throwable, nowMs: Long) {
    val report = CrashReports.fromThrowable(error, AppConfig.appVersion, nowMs)
    update(context) { CrashReports.enqueue(it, report, nowMs) }
  }

  /** فرستادنِ صف؛ ⇒ شمارِ فرستاده‌ها. نخستین شکست (نت نیست) بقیه را برای بعد می‌گذارد. */
  suspend fun flush(context: Context): Int {
    if (!runCatching { AppConfig.isConfigured(context) }.getOrDefault(false)) return 0
    val now = System.currentTimeMillis()
    val pending = synchronized(lock) { CrashReports.toSend(read(context), now) }
    if (pending.isEmpty()) return 0
    val api = Backend.api(context)
    val sent = HashSet<String>()
    for (r in pending) {
      if (runCatching { api.postPublic("/errors", CrashStore.body(r)) }.isFailure) break
      sent += r.id
    }
    //  «فرستاده» علامت می‌خورد؛ ردیف تا نشان دادنِ کدش (یا کهنه شدن) می‌ماند
    update(context) { CrashReports.settle(CrashReports.markSent(it, sent), now) }
    return sent.size
  }

  /** کدِ گزارشی که هنوز به کاربر نشان داده نشده؛ null ⇒ هیچ */
  fun notice(context: Context): String? =
    synchronized(lock) { CrashReports.notice(read(context), System.currentTimeMillis())?.id }

  fun markShown(context: Context, id: String) =
    update(context) { CrashReports.settle(CrashReports.markShown(it, id), System.currentTimeMillis()) }
}
