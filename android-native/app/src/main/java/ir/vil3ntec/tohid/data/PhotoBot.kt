package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.io.File

/**
 *  ربات عکس — همهٔ عکس‌های کالا را خودش تا جایی که می‌شود بهتر می‌کند.
 *
 *  همان کارِ استودیو (`PhotoStudio.process` با تنظیمِ پیش‌فرض: زمینهٔ سفید،
 *  جدا کردنِ کالا، برش و مربعِ ۸۰۰، نور و رنگ، تیزی) روی **همهٔ** عکس‌ها،
 *  یکی‌یکی و در پس‌زمینه. حالتِ خودکار: هر عکسِ تازه‌ای که از دستگاهِ دیگر
 *  برسد هم خودش بهتر می‌شود.
 *
 *  ── ایمنی ──────────────────────────────────────────────────────────
 *  • **اصلِ هر عکس نگه داشته می‌شود** (`product-photos-original/`) و
 *    «برگرداندنِ اصلی» آن را برمی‌گرداند — ربات هیچ‌وقت چیزی را نابود نمی‌کند.
 *  • عکسی که از قبل آماده است (مربعِ ۸۰۰ با گوشه‌های سفید) دوباره
 *    پردازش نمی‌شود: بی این، دو کامپیوتر با رباتِ روشن عکسِ همدیگر را
 *    هر بار تیزتر می‌کردند.
 *  • یک کار در یک زمان (قفل)؛ هر عکس روی نخِ پس‌زمینه و میانِ دو عکس
 *    `yield` — برنامه کند نمی‌شود.
 *  ──────────────────────────────────────────────────────────────────
 */
object PhotoBot {

  /** (انجام‌شده، کل) — صفحه نوارِ پیشرفت را از این می‌کشد */
  val progress = mutableStateOf<Pair<Int, Int>?>(null)
  val lastResult = mutableStateOf<String?>(null)

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val lock = Mutex()

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-photobot", 0)
  private fun originals(context: Context) = File(context.filesDir, "product-photos-original").apply { mkdirs() }

  fun autoEnabled(context: Context): Boolean = prefs(context).getBoolean("auto", false)
  fun setAuto(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean("auto", on).apply()
    if (on) start(context, onlyNew = true)
  }

  fun hasOriginal(context: Context, id: String) = File(originals(context), "$id.jpg").exists()
  fun originalsCount(context: Context) = originals(context).listFiles()?.count { it.name.endsWith(".jpg") } ?: 0

  /** عکسِ آماده؟ مربعِ ۸۰۰ با چهار گوشهٔ سفید — خروجیِ استودیو یا ربات */
  fun looksDone(img: PhotoStudio.Image): Boolean {
    if (img.width != img.height || img.width < 600) return false
    fun white(c: Int) = ((c shr 16) and 255) > 245 && ((c shr 8) and 255) > 245 && (c and 255) > 245
    val w = img.width
    return listOf(0, w - 1, (w - 1) * w, w * w - 1).all { white(img.px[it]) }
  }

  /** شروعِ کار در پس‌زمینه — اگر در حالِ کار است، کاری نمی‌کند */
  fun start(context: Context, onlyNew: Boolean = false) {
    val app = context.applicationContext
    if (lock.isLocked) return
    scope.launch { runCatching { runAll(app, onlyNew) } }
  }

  suspend fun runAll(context: Context, onlyNew: Boolean): Int = lock.withLock {
    //  اجرای دستی یعنی «همه» — حتی آن‌هایی که قبلاً برگردانده شده‌اند
    if (!onlyNew) prefs(context).edit().remove("kept").apply()
    val ids = PhotoStore.localIds(context).sorted()
    var done = 0
    var changed = 0
    progress.value = 0 to ids.size
    for (id in ids) {
      yield()
      //  خودکار به عکسی که کاربر به اصلش برگردانده دست نمی‌زند
      val kept = prefs(context).getStringSet("kept", emptySet()).orEmpty()
      val skip = onlyNew && (hasOriginal(context, id) || id in kept)
      if (!skip) {
        val bmp = PhotoStore.load(context, id)
        if (bmp != null) {
          val img = PhotoStore.toStudio(bmp)
          if (!looksDone(img)) {
            //  اول اصل، بعد نسخهٔ بهتر — اگر وسطِ کار برنامه بسته شود، اصل سالم است
            val orig = File(originals(context), "$id.jpg")
            if (!orig.exists()) runCatching { PhotoStore.file(context, id).copyTo(orig, overwrite = false) }
            val out = PhotoStudio.process(img)
            if (PhotoStore.saveBitmap(context, id, PhotoStore.fromStudio(out)).isSuccess) {
              ir.vil3ntec.tohid.sync.PhotoSync.changed(context, id)
              changed++
            }
          }
        }
      }
      done++
      progress.value = done to ids.size
    }
    progress.value = null
    if (changed > 0) ir.vil3ntec.tohid.sync.PhotoSync.version.value = ir.vil3ntec.tohid.sync.PhotoSync.version.value + 1
    lastResult.value = if (changed == 0) "همهٔ عکس‌ها از قبل آماده بودند" else "${changed} عکس بهتر شد — اصلِ هر کدام نگه داشته شد"
    changed
  }

  /** برگرداندنِ اصلِ همهٔ عکس‌ها */
  fun restoreAll(context: Context) {
    val app = context.applicationContext
    scope.launch {
      lock.withLock {
        var n = 0
        originals(app).listFiles()?.filter { it.name.endsWith(".jpg") }?.forEach { f ->
          val id = f.name.removeSuffix(".jpg")
          if (runCatching { f.copyTo(PhotoStore.file(app, id), overwrite = true) }.isSuccess) {
            f.delete(); ir.vil3ntec.tohid.sync.PhotoSync.changed(app, id); n++
          }
        }
        val ids = PhotoStore.localIds(app)
        prefs(app).edit().putStringSet("kept", ids).apply()
        ir.vil3ntec.tohid.sync.PhotoSync.version.value = ir.vil3ntec.tohid.sync.PhotoSync.version.value + 1
        lastResult.value = "${n} عکس به اصلش برگشت"
      }
    }
  }
}
