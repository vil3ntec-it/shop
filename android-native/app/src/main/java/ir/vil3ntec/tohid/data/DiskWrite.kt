package ir.vil3ntec.tohid.data

import android.annotation.SuppressLint
import android.os.Build
import ir.vil3ntec.tohid.core.i18n.StrAppData
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 *  نوشتنِ اتمیِ یک فایل روی دیسک — F0-01.
 *
 *  ── چرا ──────────────────────────────────────────────────────────────
 *  دفتر، بایگانی و پشتیبانِ خودکار با «فایلِ کناری، بعد `tmp.renameTo(file)`»
 *  نوشته می‌شدند و نتیجهٔ `renameTo` دیده نمی‌شد. روی اندروید و لینوکس
 *  `rename` فایلِ مقصد را جایگزین می‌کند؛ روی **ویندوز** نه: وقتی فایلِ
 *  مقصد هست، `renameTo` فقط `false` برمی‌گرداند. یعنی روی کامپیوترِ
 *  ویندوزی از ذخیرهٔ دوم به بعد هیچ چیزی به دیسک نمی‌رسید، صفحه «ثبت شد»
 *  نشان می‌داد و با بستنِ برنامه همه می‌رفت.
 *
 *  ── چه می‌کند ────────────────────────────────────────────────────────
 *   ۱) فایلِ کناری (`<نام>.tmp`) نوشته و `fsync` می‌شود — برق که برود،
 *      یا فایلِ کهنه سرِ جایش است یا تازه، نه نصفه.
 *   ۲) جابه‌جایی با `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`؛ اگر
 *      سیستم جابه‌جاییِ اتمی ندهد یا لحظه‌ای فایل را قفل کرده باشد
 *      (ضدویروس و نمایه‌سازِ ویندوز)، `REPLACE_EXISTING` و چند تلاشِ کوتاه.
 *   ۳) شکست **بی‌صدا نیست**: `IOException` بالا می‌رود تا صدازننده بگوید.
 *
 *  ⚠️ `java.nio.file` از اندرویدِ ۸ (API 26) هست؛ پایین‌تر همان `rename`ِ
 *  لینوکس است که جایگزین می‌کند — و نتیجه‌اش این بار بررسی می‌شود.
 */
object DiskWrite {

  /** متن با UTF-8 */
  @Throws(IOException::class)
  fun text(target: File, text: String) = bytes(target, text.toByteArray(Charsets.UTF_8))

  @Throws(IOException::class)
  fun bytes(target: File, data: ByteArray) = stream(target) { it.write(data) }

  /**
   *  همان [bytes]، و پیش از جابه‌جایی نسخهٔ پیشینِ [target] به نامِ `<نام>.prev` می‌ماند (DATA-SAFETY).
   *
   *  ⛔ دفتر (`ShopStore.writeToDisk`) هر ذخیره را این‌طور می‌نویسد: اگر فایلِ تازه بعداً خراب خوانده شد
   *  (خطای دیسک، قطعِ برق روی سیستمی که `fsync` را جدی نمی‌گیرد)، نسخهٔ یک ذخیره پیش‌تر سالم کنارش هست
   *  (`LedgerRecovery`). پیوندِ سخت است، نه رونوشت: هیچ بایتی دوباره نوشته نمی‌شود و ثبتِ فروش کُند نمی‌شود.
   *  سیستمی که پیوندِ سخت نمی‌دهد ⇒ بی `.prev` (نسخه‌های خودکار هنوز هستند) — خطایش هرگز نوشتن را نمی‌شکند.
   */
  @Throws(IOException::class)
  fun bytes(target: File, data: ByteArray, keepPrevious: Boolean) {
    if (!keepPrevious) return bytes(target, data)
    stream(target, before = { linkPrevious(target) }) { it.write(data) }
  }

  /** نامِ نسخهٔ پیشینِ [target] — همان که [bytes] با `keepPrevious` می‌گذارد */
  fun previousOf(target: File): File = File(target.absoluteFile.parentFile, target.name + PREV)

  private fun linkPrevious(target: File) {
    if (!target.isFile || Build.VERSION.SDK_INT < 26) return
    runCatching { Api26.link(previousOf(target), target) }
  }

  /** رونوشتِ یک فایل، همان بایت‌ها */
  @Throws(IOException::class)
  fun copy(source: File, target: File) = stream(target) { out ->
    source.inputStream().use { it.copyTo(out) }
  }

  @Throws(IOException::class)
  fun stream(target: File, write: (OutputStream) -> Unit) = stream(target, before = {}, write = write)

  /** @param before درست پیش از جابه‌جایی (فایلِ کناری کامل و `fsync` شده) — نسخهٔ پیشین ([linkPrevious]) */
  private fun stream(target: File, before: () -> Unit, write: (OutputStream) -> Unit) {
    val dir = target.absoluteFile.parentFile ?: throw IOException(StrAppData.folderUnknown(target))
    if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException(StrAppData.folderNotMade(dir))
    val tmp = File(dir, target.name + ".tmp")
    try {
      FileOutputStream(tmp).use { out ->
        write(out)
        out.flush()
        out.fd.sync()
      }
      before()
      move(tmp, target)
    } catch (e: Throwable) {
      tmp.delete()
      throw e as? IOException ?: IOException(e.message ?: StrAppData.diskWriteFailed, e)
    }
    syncDir(dir)
  }

  /** همان جابه‌جاییِ اتمی که جایگزین می‌کند — برای کوچِ رمز (`AtRest`) */
  internal fun move(tmp: File, target: File) {
    if (Build.VERSION.SDK_INT >= 26) return Api26.move(tmp, target)
    if (!tmp.renameTo(target)) throw IOException(StrAppData.notReplaced(target.name))
  }

  /**
   *  `fsync`ِ خودِ پوشه تا نامِ تازه هم پس از قطعِ برق بماند. روی ویندوز
   *  پوشه باز نمی‌شود و لازم هم نیست — خطایش نادیده گرفته می‌شود.
   */
  internal fun syncDir(dir: File) {
    if (Build.VERSION.SDK_INT >= 26) runCatching { Api26.syncDir(dir) }
  }

  /** هر چه از `java.nio.file` است همین‌جاست تا اندرویدِ ۷ هرگز بارش نکند. */
  @SuppressLint("NewApi")
  private object Api26 {
    fun move(tmp: File, target: File) {
      val from = tmp.toPath()
      val to = target.toPath()
      var last: IOException? = null
      repeat(ATTEMPTS) { attempt ->
        try {
          Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
          return
        } catch (e: IOException) {
          //  `AtomicMoveNotSupportedException` هم همین‌جاست
          last = e
        }
        try {
          Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
          return
        } catch (e: IOException) {
          last = e
        }
        if (attempt < ATTEMPTS - 1) Thread.sleep(40L * (attempt + 1))
      }
      throw IOException(StrAppData.notReplacedWhy(target.name, last?.message), last)
    }

    fun syncDir(dir: File) {
      FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }

    /** پیوندِ سختِ [prev] به [target] (همان inode؛ جابه‌جاییِ بعدی فقط [target] را به فایلِ تازه می‌برد) */
    fun link(prev: File, target: File) {
      Files.deleteIfExists(prev.toPath())
      Files.createLink(prev.toPath(), target.toPath())
    }
  }

  private const val ATTEMPTS = 3
  private const val PREV = ".prev"
}
