package ir.vil3ntec.tohid.core.sync.platform

import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.data.DiskWrite
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 *  مرزِ پلتفرمِ دفترچهٔ §۹.۵ (`ReplayBook.Store`) روی JVM — اندروید و کامپیوتر. منطقِ دفترچه در
 *  `core/sync/RestoreReplay.kt` بی `java.*` است (نگهبانِ آیفون، RULES-fa «هسته‌های مشترک»)؛ این‌جا
 *  فقط پرونده و قفل. رفتار همان پیشین: افزودن با fsync، خطِ نیمه‌کارهٔ قطعِ برق ⇒ رکوردِ تازه از
 *  سرِ خطِ تازه، فشرده کردن با `DiskWrite` (F0-01)، و هیچ شکستی پرتاب نمی‌شود.
 */
class FileReplayStore(private val file: File) : ReplayBook.Store {

  private val lock = Any()

  override fun <T> locked(block: () -> T): T = synchronized(lock) { block() }

  override fun started(): Boolean = runCatching { file.exists() && file.length() > 0 }.getOrDefault(true)

  override fun lines(): List<String> =
    runCatching { if (file.exists()) file.readLines(Charsets.UTF_8).filter { it.isNotBlank() } else emptyList() }
      .getOrDefault(emptyList())

  override fun append(text: String): Boolean = runCatching {
    file.parentFile?.mkdirs()
    val lead = if (file.length() > 0 && lastByte() != '\n'.code) "\n" else ""
    FileOutputStream(file, true).use { out ->
      out.write((lead + text).toByteArray(Charsets.UTF_8))
      out.fd.sync()
    }
  }.isSuccess

  override fun replace(text: String): Boolean = runCatching { DiskWrite.text(file, text) }.isSuccess

  private fun lastByte(): Int = RandomAccessFile(file, "r").use { it.seek(it.length() - 1); it.read() }

  companion object {
    /** دفترچهٔ یک حساب در این پوشه: `<name>-sent.jsonl` */
    fun inDir(dir: File, name: String): ReplayBook = ReplayBook(FileReplayStore(File(dir, ReplayBook.fileName(name))))
  }
}
