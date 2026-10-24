package ir.vil3ntec.tohid.core.image

import java.io.File

/**
 *  دیسکِ ورودِ گروهیِ عکس — فقط `java.io` (گوشی و کامپیوتر؛ آزمون روی JVM).
 *
 *  ⛔ ریشه زیرِ `noBackupFilesDir` است (`under`)، نه `filesDir`: پشتیبانِ ابریِ
 *     اندروید کلِ `filesDir` را می‌برد و سقفش ۲۵ مگ است؛ سیصد عکس آن را پر
 *     می‌کرد و پشتیبانِ دفترِ دکان بی‌صدا می‌ایستاد.
 *  ⛔ اصلِ فایلِ کاربر (با EXIF/GPS) هرگز روی دیسک نمی‌نشیند: همان لحظهٔ انتخاب
 *     آماده می‌شود و فقط نسخهٔ نمایش و کوچکِ بی‌فراداده (`<sum>.d`، `<sum>.t`)
 *     می‌ماند — `sum` همان SHA-256ِ اصل است تا فایلِ هم‌محتوا «تکراری» شود.
 *  ⛔ فایلی که هیچ ردیفی لازمش ندارد پاک می‌شود و وقتی همهٔ ردیف‌ها نهایی‌اند
 *     کلِ پوشهٔ فایل‌ها (`tidy`). `job.json` کوچک است و نتیجه را نگه می‌دارد.
 */
class BulkStore(val root: File) {
  private val files get() = File(root, "files")
  private val jobFile get() = File(root, "job.json")

  private fun display(sum: String) = File(files, "$sum.d")
  private fun thumb(sum: String) = File(files, "$sum.t")

  class Ready(val display: ByteArray, val thumb: ByteArray?)

  fun has(sum: String): Boolean = display(sum).isFile

  /** نسخه‌های آماده (بی EXIF/GPS) — هرگز اصل */
  fun put(sum: String, display: ByteArray, thumb: ByteArray?) {
    files.mkdirs()
    thumb?.let { write(thumb(sum), it) }
    write(display(sum), display)
  }

  fun get(sum: String): Ready? {
    val d = display(sum).takeIf { it.isFile }?.readBytes() ?: return null
    return Ready(d, thumb(sum).takeIf { it.isFile }?.readBytes())
  }

  fun load(): BulkJob? =
    jobFile.takeIf { it.isFile }?.let { f -> runCatching { BulkJob.fromJson(f.readText()) }.getOrNull() }?.reopen()

  @Synchronized
  fun save(job: BulkJob) {
    root.mkdirs()
    write(jobFile, job.toJson().toByteArray(Charsets.UTF_8))
  }

  fun clear() {
    runCatching { root.deleteRecursively() }
  }

  /** فایلِ بی‌صاحب پاک؛ همهٔ ردیف‌ها نهایی ⇒ کلِ پوشهٔ فایل‌ها */
  @Synchronized
  fun tidy(job: BulkJob) {
    if (job.settled()) { runCatching { files.deleteRecursively() }; return }
    val keep = job.rows.filter(job::needsFile).mapTo(HashSet()) { it.sum }
    files.listFiles()?.forEach { f -> if (f.name.substringBeforeLast('.') !in keep) f.delete() }
  }

  private fun write(target: File, bytes: ByteArray) {
    val tmp = File(target.parentFile, target.name + ".part")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
  }

  companion object {
    const val DIR = "image-bulk"
    fun under(noBackupDir: File) = BulkStore(File(noBackupDir, DIR))
  }
}
