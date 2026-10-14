package ir.vil3ntec.tohid.data

import android.annotation.SuppressLint
import android.os.Build
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

  /** رونوشتِ یک فایل، همان بایت‌ها */
  @Throws(IOException::class)
  fun copy(source: File, target: File) = stream(target) { out ->
    source.inputStream().use { it.copyTo(out) }
  }

  @Throws(IOException::class)
  fun stream(target: File, write: (OutputStream) -> Unit) {
    val dir = target.absoluteFile.parentFile ?: throw IOException("پوشهٔ «$target» معلوم نیست")
    if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("پوشهٔ «$dir» ساخته نشد")
    val tmp = File(dir, target.name + ".tmp")
    try {
      FileOutputStream(tmp).use { out ->
        write(out)
        out.flush()
        out.fd.sync()
      }
      move(tmp, target)
    } catch (e: Throwable) {
      tmp.delete()
      throw e as? IOException ?: IOException(e.message ?: "نوشتن روی دیسک نشد", e)
    }
    syncDir(dir)
  }

  private fun move(tmp: File, target: File) {
    if (Build.VERSION.SDK_INT >= 26) return Api26.move(tmp, target)
    if (!tmp.renameTo(target)) throw IOException("«${target.name}» روی دیسک جایگزین نشد")
  }

  /**
   *  `fsync`ِ خودِ پوشه تا نامِ تازه هم پس از قطعِ برق بماند. روی ویندوز
   *  پوشه باز نمی‌شود و لازم هم نیست — خطایش نادیده گرفته می‌شود.
   */
  private fun syncDir(dir: File) {
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
      throw IOException("«${target.name}» روی دیسک جایگزین نشد: ${last?.message}", last)
    }

    fun syncDir(dir: File) {
      FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }
  }

  private const val ATTEMPTS = 3
}
