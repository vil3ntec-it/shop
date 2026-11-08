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
 *      (ضدویروس و نمایه‌سازِ ویندوز)، `REPLACE_EXISTING` و تا پنج دور با درنگِ
 *      رو به افزایش ([move]).
 *   ۳) شکست **بی‌صدا نیست**: `IOException` بالا می‌رود تا صدازننده بگوید.
 *   ۴) `fsync` ِ پوشه روی ویندوز تلاش نمی‌شود و هیچ‌جا نوشتن را نمی‌شکند ([syncDir]).
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
   *  سیستمی که پیوندِ سخت نمی‌دهد (FAT/exFAT ِ فلش، درایوِ شبکه) ⇒ **رونوشت** با همان بایت‌ها و همان زمانِ فایل
   *  ([keepPrevious]) — خطایش هرگز نوشتن را نمی‌شکند.
   */
  @Throws(IOException::class)
  fun bytes(target: File, data: ByteArray, keepPrevious: Boolean) = stream(target, keepPrevious) { it.write(data) }

  /**
   *  همان [bytes] با `keepPrevious`، ولی بایت‌ها از [write] به فایلِ کناری می‌روند — بی آرایهٔ کاملِ دفتر در حافظه
   *  (PERF-LEDGER، `LedgerText`). همان ترتیب: کناری ⇐ `fsync` ⇐ `.prev` ⇐ جابه‌جاییِ اتمی ⇐ `fsync` ِ پوشه.
   */
  @Throws(IOException::class)
  fun stream(target: File, keepPrevious: Boolean, write: (OutputStream) -> Unit) {
    if (!keepPrevious) return stream(target, write)
    val prev = previousOf(target)
    stream(target, before = { this.keepPrevious(target, prev) }, write = write)
  }

  /** همان [bytes] با نسخهٔ پیشین به نامِ [previous] — تنظیماتِ کامپیوتر (`DesktopPrefs`، `<نام>.bak`) */
  @Throws(IOException::class)
  internal fun bytesKeeping(target: File, data: ByteArray, previous: File) =
    stream(target, before = { keepPrevious(target, previous) }) { it.write(data) }

  /** نامِ نسخهٔ پیشینِ [target] — همان که [bytes] با `keepPrevious` می‌گذارد */
  fun previousOf(target: File): File = File(target.absoluteFile.parentFile, target.name + PREV)

  /**
   *  نسخهٔ پیشین: پیوندِ سخت؛ نشد ⇒ رونوشت. زمانِ رونوشت همان زمانِ [target] است، چون `LedgerRecovery.best`
   *  نسخه‌ها را با زمانِ فایل می‌چیند: رونوشتی با زمانِ «حالا» از فایلِ کناریِ تازه‌تر (`.tmp`) جلو می‌افتاد و
   *  دفترِ کهنه‌تر برمی‌گشت.
   */
  private fun keepPrevious(target: File, prev: File) {
    if (!target.isFile || Build.VERSION.SDK_INT < 26) return
    val disk = fs
    if (runCatching { disk.hardLink(prev, target) }.isSuccess) return
    runCatching {
      stream(prev, before = {}, stamp = target.lastModified()) { out -> target.inputStream().use { it.copyTo(out) } }
    }
  }

  /** رونوشتِ یک فایل، همان بایت‌ها */
  @Throws(IOException::class)
  fun copy(source: File, target: File) = stream(target) { out ->
    source.inputStream().use { it.copyTo(out) }
  }

  @Throws(IOException::class)
  fun stream(target: File, write: (OutputStream) -> Unit) = stream(target, before = {}, write = write)

  /**
   *  @param before درست پیش از جابه‌جایی (فایلِ کناری کامل و `fsync` شده) — نسخهٔ پیشین ([keepPrevious])
   *  @param stamp زمانِ فایل (میلی‌ثانیه) که فایلِ کناری پیش از جابه‌جایی می‌گیرد؛ `0` ⇒ همان «حالا»
   */
  private fun stream(target: File, before: () -> Unit, stamp: Long = 0L, write: (OutputStream) -> Unit) {
    val dir = target.absoluteFile.parentFile ?: throw IOException(StrAppData.folderUnknown(target))
    if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException(StrAppData.folderNotMade(dir))
    val tmp = File(dir, target.name + ".tmp")
    try {
      FileOutputStream(tmp).use { out ->
        write(out)
        out.flush()
        out.fd.sync()
      }
      if (stamp > 0L) tmp.setLastModified(stamp)
      before()
      move(tmp, target)
    } catch (e: Throwable) {
      tmp.delete()
      throw e as? IOException ?: IOException(e.message ?: StrAppData.diskWriteFailed, e)
    }
    syncDir(dir)
  }

  /**
   *  همان جابه‌جاییِ اتمی که جایگزین می‌کند — برای کوچِ رمز (`AtRest`) و پشتیبانِ پوشه (`BackupMirror`).
   *
   *  هر دور: اول `ATOMIC_MOVE + REPLACE_EXISTING`، نشد (درایوِ شبکه جابه‌جاییِ اتمی نمی‌دهد) `REPLACE_EXISTING`.
   *  هر دو نشد ⇒ درنگِ رو به افزایش ([backoff]) و دورِ بعد: ضدویروس و نمایه‌سازِ ویندوز فایلِ تازه‌بسته را چند
   *  صد میلی‌ثانیه قفل نگه می‌دارند. پس از [ATTEMPTS] دور ⇒ `IOException`، هرگز بی‌صدا.
   */
  internal fun move(tmp: File, target: File) {
    if (Build.VERSION.SDK_INT < 26) {
      if (!tmp.renameTo(target)) throw IOException(StrAppData.notReplaced(target.name))
      return
    }
    val disk = fs
    var last: IOException? = null
    for (attempt in 1..ATTEMPTS) {
      if (attempt > 1) disk.pause(backoff(attempt - 1))
      try {
        //  `AtomicMoveNotSupportedException` هم همین‌جاست
        disk.moveAtomic(tmp, target)
        return
      } catch (e: IOException) {
        last = e
      }
      try {
        disk.moveReplace(tmp, target)
        return
      } catch (e: IOException) {
        last = e
      }
    }
    throw IOException(StrAppData.notReplacedWhy(target.name, last?.message), last)
  }

  /** درنگِ پس از [failed] دورِ ناموفق: ۵۰، ۱۰۰، ۲۰۰، ۴۰۰ میلی‌ثانیه (روی هم کمتر از یک ثانیه) */
  internal fun backoff(failed: Int): Long = BACKOFF_MS shl (failed - 1).coerceIn(0, 10)

  /**
   *  `fsync`ِ خودِ پوشه تا نامِ تازه هم پس از قطعِ برق بماند. ویندوز پوشه را برای `fsync` باز نمی‌کند (و
   *  NTFS نامِ تازه را خودش در ژورنال می‌نشاند) ⇒ آن‌جا اصلاً تلاش نمی‌شود؛ هر جای دیگر خطایش نادیده
   *  گرفته می‌شود — هرگز نوشتنِ نشسته را «ناموفق» نمی‌کند.
   */
  internal fun syncDir(dir: File) {
    if (Build.VERSION.SDK_INT < 26) return
    val disk = fs
    if (disk.windows) return
    runCatching { disk.syncDir(dir) }
  }

  /**
   *  آنچه [DiskWrite] از سیستمِ فایل می‌خواهد. برنامه همیشه [RealFs] است؛ آزمونِ کامپیوتر (`WindowsDiskFaultsTest`)
   *  جایش ویندوزِ ساختگی می‌گذارد — جابه‌جاییِ قفل‌شده، دیسکِ بی پیوندِ سخت، پوشهٔ بی `fsync` — بی ویندوزِ واقعی.
   *  همه با `File` (نه `Path`) تا اندرویدِ ۷ هرگز `java.nio.file` را بار نکند.
   */
  internal interface Fs {
    /** آیا این سیستم ویندوز است — [syncDir] آن‌جا تلاش نمی‌کند */
    val windows: Boolean
    fun moveAtomic(from: File, to: File)
    fun moveReplace(from: File, to: File)
    /** پیوندِ سختِ [link] به [existing]؛ [link] ِ پیشین اول پاک می‌شود */
    fun hardLink(link: File, existing: File)
    fun syncDir(dir: File)
    fun pause(millis: Long)
  }

  @Volatile internal var fs: Fs = RealFs

  private object RealFs : Fs {
    override val windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    override fun moveAtomic(from: File, to: File) = Api26.moveAtomic(from, to)
    override fun moveReplace(from: File, to: File) = Api26.moveReplace(from, to)
    override fun hardLink(link: File, existing: File) = Api26.link(link, existing)
    override fun syncDir(dir: File) = Api26.syncDir(dir)
    override fun pause(millis: Long) = Thread.sleep(millis)
  }

  /** هر چه از `java.nio.file` است همین‌جاست تا اندرویدِ ۷ هرگز بارش نکند. */
  @SuppressLint("NewApi")
  private object Api26 {
    fun moveAtomic(tmp: File, target: File) {
      Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun moveReplace(tmp: File, target: File) {
      Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
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

  /** دورهای جابه‌جایی پیش از «جایگزین نشد» — روی هم تا ۷۵۰ میلی‌ثانیه درنگ ([backoff]) */
  internal const val ATTEMPTS = 5
  private const val BACKOFF_MS = 50L
  private const val PREV = ".prev"
}
