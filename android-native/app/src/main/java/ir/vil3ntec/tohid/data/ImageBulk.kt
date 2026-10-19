package ir.vil3ntec.tohid.data

import android.content.Context
import android.net.Uri
import ir.vil3ntec.tohid.core.image.BulkJob
import ir.vil3ntec.tohid.core.image.BulkMatch
import ir.vil3ntec.tohid.core.image.ImagePrep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 *  ورودِ گروهیِ عکسِ کالا روی گوشی و کامپیوتر (v3 ۳۶.۸، IMG-01/02/03/14).
 *  منطقِ خالص در `core/image/BulkJob.kt` و `BulkMatch.kt`؛ این‌جا فقط دیسک،
 *  فایلِ کاربر و راهِ عکسِ امروز (`PhotoStore` + `PhotoSync`) و بانک.
 *
 *  ⛔ هر فایل همان لحظهٔ انتخاب در پوشهٔ خودِ برنامه کپی می‌شود (نامش =
 *     SHA-256) — اجازهٔ خواندنِ فایلِ گالری پس از بستنِ برنامه از دست می‌رود؛
 *     کپی یعنی «ادامه» واقعاً ادامه است. پایانِ کار یا «پاک کردن» ⇒ پوشه خالی.
 *  ⛔ کارِ نیمه‌کاره پس از هر قدم روی دیسک است (`job.json`).
 */
object ImageBulk {
  private fun dir(context: Context) = File(context.filesDir, "image-bulk").apply { if (!exists()) mkdirs() }
  private fun files(context: Context) = File(dir(context), "files").apply { if (!exists()) mkdirs() }
  private fun jobFile(context: Context) = File(dir(context), "job.json")

  fun load(context: Context): BulkJob? =
    jobFile(context).takeIf { it.isFile }?.let { f -> runCatching { BulkJob.fromJson(f.readText()) }.getOrNull() }?.reopen()

  @Synchronized
  fun save(context: Context, job: BulkJob) {
    val tmp = File(dir(context), "job.json.part")
    tmp.writeText(job.toJson())
    val target = jobFile(context)
    if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
  }

  fun clear(context: Context) {
    runCatching { dir(context).deleteRecursively() }
  }

  fun items(d: ShopData): List<BulkMatch.Item> = d.products.map { BulkMatch.Item(it.id, it.name, it.barcodes) }

  private fun sha256(b: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

  /** فایل‌های انتخاب‌شده ⇒ ردیف‌ها (CSVها فقط نقشه‌اند، ردیف نمی‌شوند) */
  suspend fun add(context: Context, job: BulkJob?, uris: List<Uri>, d: ShopData): BulkJob = withContext(Dispatchers.IO) {
    val j = job ?: BulkJob("job-${System.currentTimeMillis()}")
    val named = uris.map { it to FileName.of(context, it) }
    val csv = HashMap<String, BulkMatch.CsvRow>()
    for ((u, n) in named) if (BulkMatch.isCsvName(n)) {
      runCatching { context.contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.UTF_8) } }
        .getOrNull()?.let { csv += BulkMatch.parseCsv(it) }
    }
    val list = items(d)
    for ((u, n) in named) {
      if (BulkMatch.isCsvName(n)) continue
      val bytes = runCatching { context.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull()
      val key = (j.rows.maxOfOrNull { it.key } ?: 0) + 1
      if (bytes == null || bytes.isEmpty()) {
        j.rows += BulkJob.Row(key, n, 0, "missing-$key", status = BulkJob.Status.FAILED, note = "فایل خوانده نشد")
        continue
      }
      val sum = sha256(bytes)
      val m = BulkMatch.match(n, list, csv)
      val row = BulkJob.Row(key, n, bytes.size.toLong(), sum, productId = m.productId, candidates = m.candidates, how = m.how)
      if (!BulkMatch.isImageName(n)) { row.status = BulkJob.Status.NOT_IMAGE; row.note = "پسوندِ فایل عکس نیست" }
      else File(files(context), sum).takeIf { !it.exists() }?.writeBytes(bytes)
      j.rows += row
    }
    j.plan { id -> d.products.firstOrNull { it.id == id }?.photo }
    save(context, j)
    j
  }

  /**
   *  یک ردیف: آماده‌سازی ← عکسِ دستگاه (+ همگام‌سازیِ امروز) ← بانک (اگر روشن).
   *  خطای گذرا (اینترنت) بالا می‌رود تا کار بایستد و بعداً از همان قدم ادامه یابد.
   */
  suspend fun process(context: Context, store: ShopStore, job: BulkJob, row: BulkJob.Row) {
    val bytes = withContext(Dispatchers.IO) { File(files(context), row.sum).takeIf { it.isFile }?.readBytes() }
    if (bytes == null) { row.status = BulkJob.Status.FAILED; row.note = "فایل روی این دستگاه نماند — دوباره انتخابش کنید"; return }
    val prep = try { ImageBank.prepare(bytes) } catch (e: Exception) {
      row.status = BulkJob.Status.NOT_IMAGE; row.note = e.message ?: "این فایل عکس نیست یا خراب است"; return
    }
    if (!row.local) {
      withContext(Dispatchers.IO) { PhotoStore.saveBytes(context, row.productId, prep.display) }.getOrThrow()
      //  نشانهٔ عکس روی خودِ کالا — روی تازه‌ترین دفتر، نه نسخهٔ آغازِ کار
      val cur = store.data.value
      if (cur.products.any { it.id == row.productId && !it.photo }) {
        store.save(cur.copy(products = cur.products.map { if (it.id == row.productId) it.copy(photo = true) else it }))
      }
      ir.vil3ntec.tohid.sync.PhotoSync.changed(context, row.productId)
      row.local = true
      if (prep.small) row.note = "عکس کوچک است (زیرِ ${ImagePrep.MIN_SIDE} پیکسل)"
      if (ImageBank.enabled.value != true) { row.status = BulkJob.Status.DONE; return }
      row.status = BulkJob.Status.PENDING_BANK
      save(context, job)
    }
    if (ImageBank.enabled.value != true) { row.status = BulkJob.Status.DONE; return }
    try {
      ImageBank.uploadOwn(context, row.productId, prep, row.upload) { save(context, job) }
      row.status = BulkJob.Status.DONE
    } catch (f: Exception) {
      if (ImageBank.transient(f)) { row.status = BulkJob.Status.PENDING_BANK; row.note = "روی دستگاه گذاشته شد؛ بانک منتظرِ اینترنت"; throw f }
      row.status = BulkJob.Status.DONE
      row.note = "روی دستگاه گذاشته شد؛ بانکِ تصاویر نپذیرفت: ${(f as? ir.vil3ntec.tohid.core.net.ApiFailure)?.userMessage ?: f.message.orEmpty()}"
    }
  }
}
