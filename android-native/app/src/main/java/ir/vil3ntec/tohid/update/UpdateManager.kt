package ir.vil3ntec.tohid.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 *  دانلودِ به‌روزرسانی، بیرونِ عمرِ صفحه.
 *
 *  تا حالا این کار در `rememberCoroutineScope()` صفحهٔ «بیشتر» انجام
 *  می‌شد. آن دامنه با بسته شدنِ صفحه لغو می‌شود، پس کاربری که وسطِ دانلود
 *  می‌رفت سراغِ فروش، برمی‌گشت و می‌دید هیچ‌چیز دانلود نشده و نوارِ پیشرفت
 *  هم نیست — کار همان لحظهٔ رفتنش کشته شده بود.
 *
 *  حالا دامنه مالِ خودِ برنامه است و وضعیت هم اینجا می‌ماند: با رفتن و
 *  برگشتن، همان نوار سرِ جایش است و دانلود ادامه دارد.
 *
 *  `Context` هم همیشه `applicationContext` گرفته می‌شود؛ نگه داشتنِ
 *  اکتیویتی در یک `object` یعنی نشتِ حافظه تا آخرِ عمرِ برنامه.
 */
object UpdateManager {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var job: Job? = null

  /** نسخهٔ تازه‌ای که پیدا شده — تا وقتی برنامه باز است یادش می‌ماند */
  var release by mutableStateOf<Updater.Release?>(null)
    private set

  /** درصدِ دانلود، یا ‎-۱ وقتی دانلودی در جریان نیست */
  var progress by mutableStateOf(-1)
    private set

  /** کاری در جریان است (بررسی یا دانلود) */
  var busy by mutableStateOf(false)
    private set

  /** پیام برای کاربر — خطا یا خبر */
  var message by mutableStateOf<String?>(null)
    private set

  /** فایلِ کاملِ آمادهٔ نصب، اگر گرفته شده باشد */
  var ready by mutableStateOf<File?>(null)
    private set

  /** آیا نسخهٔ تازه یک بار بررسی شده — تا صفحه هر بار از نو نپرسد */
  var checked by mutableStateOf(false)
    private set

  /**
   *  گوشی هنوز اجازهٔ نصب از این برنامه را نداده.
   *
   *  این بزرگ‌ترین دلیلِ «به‌روزرسانی کار نمی‌کند» است: فایل کامل گرفته
   *  می‌شود، پنجرهٔ نصب باز نمی‌شود و هیچ خطایی هم دیده نمی‌شود. حالا
   *  به‌جای سکوت، دکمه‌ای می‌آید که یک‌راست همان صفحهٔ اجازه را باز کند.
   */
  var needsPermission by mutableStateOf(false)
    private set

  fun check(context: Context, repo: String, currentVersion: String) {
    if (busy) return
    val app = context.applicationContext
    busy = true
    message = "در حال بررسی…"
    job = scope.launch {
      Updater.check(repo, currentVersion)
        .onSuccess { found ->
          release = found
          checked = true
          // فایلِ همین نسخه شاید از قبل گرفته شده باشد — مثلاً کاربر
          // دانلود کرده و سرِ پرسشِ نصب «نه» زده
          ready = found?.let { Updater.readyFile(app, it) }
          message = when {
            found == null -> "نسخهٔ شما تازه‌ترین است."
            ready != null -> "نسخهٔ ${found.version} از قبل گرفته شده — آمادهٔ نصب."
            else -> null
          }
        }
        .onFailure { message = it.message ?: "بررسی ناموفق بود" }
      busy = false
    }
  }

  fun download(context: Context) {
    val target = release ?: return
    if (busy) return
    val app = context.applicationContext

    // از قبل کامل گرفته شده؟ یک‌راست سراغِ نصب
    Updater.readyFile(app, target)?.let {
      ready = it
      install(app)
      return
    }

    busy = true
    progress = 0
    message = null
    job = scope.launch {
      Updater.download(app, target) { progress = it }
        .onSuccess { file ->
          progress = -1
          ready = file
          message = Updater.lastNote
          install(app)
        }
        .onFailure {
          progress = -1
          message = it.message ?: "دانلود ناموفق بود"
        }
      busy = false
    }
  }

  /** نصبِ فایلی که از قبل گرفته شده */
  fun install(context: Context) {
    val file = ready ?: return
    val app = context.applicationContext

    if (!Updater.canInstall(app)) {
      needsPermission = true
      message = "فایل آماده است، ولی گوشی هنوز اجازهٔ نصب از این برنامه را نداده. " +
        "دکمهٔ زیر را بزنید، «اجازه» را روشن کنید و برگردید."
      return
    }

    needsPermission = false
    Updater.install(app, file)
      .onFailure { message = "نصب‌کنندهٔ اندروید باز نشد" }
  }

  /** بردنِ کاربر به همان صفحهٔ اجازهٔ نصب */
  fun allowInstall(context: Context) {
    Updater.openInstallSettings(context.applicationContext)
      .onFailure { message = "صفحهٔ تنظیماتِ اجازهٔ نصب باز نشد" }
  }

  /** راهِ آخر: گرفتنِ همان فایل با مرورگر */
  fun openInBrowser(context: Context, repo: String) {
    val url = release?.apkUrl ?: Updater.stableApkUrl(repo)
    Updater.openInBrowser(context.applicationContext, url)
      .onFailure { message = "مرورگر باز نشد" }
  }

  /** لغو به‌خواستِ کاربر — نیمهٔ گرفته‌شده می‌ماند و دفعهٔ بعد ادامه پیدا می‌کند */
  fun cancel() {
    job?.cancel()
    job = null
    busy = false
    progress = -1
    message = "دانلود متوقف شد — با زدنِ دوباره از همین‌جا ادامه پیدا می‌کند."
  }
}
