package ir.vil3ntec.tohid.data

import android.content.Context
import android.net.Uri
import ir.vil3ntec.tohid.core.image.BulkJob
import ir.vil3ntec.tohid.core.image.BulkMatch
import ir.vil3ntec.tohid.core.image.BulkStore
import ir.vil3ntec.tohid.core.image.ImagePrep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 *  ورودِ گروهیِ عکسِ کالا روی گوشی و کامپیوتر (v3 ۳۶.۸، IMG-01/02/03/14).
 *  منطقِ خالص در `core/image/BulkJob.kt`، `BulkMatch.kt` و `BulkStore.kt`؛ این‌جا
 *  فقط فایلِ کاربر و راهِ عکسِ امروز (`PhotoStore` + `PhotoSync`) و بانک.
 *
 *  ⛔ هر فایل همان لحظهٔ انتخاب **آماده** می‌شود (`ImageBank.prepare`) و فقط
 *     نسخهٔ نمایش/کوچکِ بی EXIF/GPS زیرِ `noBackupFilesDir` می‌ماند — اجازهٔ
 *     خواندنِ فایلِ گالری پس از بستنِ برنامه از دست می‌رود، پس «ادامه» واقعاً
 *     ادامه است؛ اصلِ فایل هرگز روی دیسک نمی‌نشیند و به پشتیبانِ ابری نمی‌رود.
 *  ⛔ پسوند پیش از خواندن و سقفِ ۴۰ مگ هنگامِ خواندن سنجیده می‌شود؛ کمبودِ
 *     حافظه فقط همان ردیف را «ناموفق» می‌کند.
 *  ⛔ کارِ نیمه‌کاره پس از هر قدم روی دیسک است (`job.json`).
 */
object ImageBulk {
  /** بزرگ‌ترین فایلی که خوانده می‌شود */
  const val MAX_FILE_BYTES = 40L * 1024 * 1024
  private const val MAX_CSV_BYTES = 4L * 1024 * 1024

  private fun disk(context: Context): BulkStore {
    //  پوشهٔ پیش از این (زیرِ filesDir، در پشتیبانِ ابری) اگر از ساختِ آزمایشی مانده
    File(context.filesDir, BulkStore.DIR).takeIf { it.exists() }?.let { runCatching { it.deleteRecursively() } }
    return BulkStore.under(context.noBackupFilesDir)
  }

  fun load(context: Context): BulkJob? = disk(context).load()

  fun save(context: Context, job: BulkJob) = disk(context).save(job)

  /** پس از هر ردیف: فایلِ بی‌صاحب پاک؛ همه نهایی ⇒ کلِ پوشهٔ فایل‌ها */
  fun tidy(context: Context, job: BulkJob) = disk(context).tidy(job)

  fun clear(context: Context) = disk(context).clear()

  fun items(d: ShopData): List<BulkMatch.Item> = d.products.map { BulkMatch.Item(it.id, it.name, it.barcodes) }

  private class TooBig : Exception()

  /** خواندن با سقف — فایلِ بزرگ‌تر تا ته خوانده نمی‌شود */
  private fun readCapped(context: Context, uri: Uri, max: Long): ByteArray? =
    context.contentResolver.openInputStream(uri)?.use { input ->
      val out = ByteArrayOutputStream()
      val buf = ByteArray(64 * 1024)
      var total = 0L
      while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > max) throw TooBig()
        out.write(buf, 0, n)
      }
      out.toByteArray()
    }

  /**
   *  فایل‌های انتخاب‌شده ⇒ ردیف‌ها (CSVها فقط نقشه‌اند، ردیف نمی‌شوند).
   *  `progress(i, n)` پس از هر عکس.
   */
  suspend fun add(
    context: Context, job: BulkJob?, uris: List<Uri>, d: ShopData,
    progress: (Int, Int) -> Unit = { _, _ -> },
  ): BulkJob = withContext(Dispatchers.IO) {
    val s = disk(context)
    val j = job ?: BulkJob("job-${System.currentTimeMillis()}")
    val named = uris.map { it to FileName.of(context, it) }
    val csv = LinkedHashMap<String, List<BulkMatch.CsvRow>>()
    for ((u, n) in named) if (BulkMatch.isCsvName(n)) {
      try {
        readCapped(context, u, MAX_CSV_BYTES)?.let { BulkMatch.parseCsv(it.toString(Charsets.UTF_8), csv) }
      } catch (c: CancellationException) { throw c } catch (_: Exception) {}
    }
    val list = items(d)
    val pics = named.filterNot { BulkMatch.isCsvName(it.second) }
    for ((i, pair) in pics.withIndex()) {
      ensureActive()
      val (u, n) = pair
      val key = (j.rows.maxOfOrNull { it.key } ?: 0) + 1
      fun fail(note: String, status: BulkJob.Status = BulkJob.Status.FAILED) {
        j.rows += BulkJob.Row(key, n, 0, "skip-$key", status = status, note = note)
      }
      //  پسوند پیش از خواندن
      if (!BulkMatch.isImageName(n)) { fail("پسوندِ فایل عکس نیست", BulkJob.Status.NOT_IMAGE); progress(i + 1, pics.size); continue }
      val bytes = try {
        readCapped(context, u, MAX_FILE_BYTES)
      } catch (c: CancellationException) { throw c } catch (_: TooBig) {
        fail("فایل بزرگ‌تر از ${MAX_FILE_BYTES / (1024 * 1024)} مگابایت است"); progress(i + 1, pics.size); continue
      } catch (_: OutOfMemoryError) {
        fail("حافظهٔ دستگاه برای خواندنِ این فایل کافی نبود"); progress(i + 1, pics.size); continue
      } catch (_: Exception) { null }
      if (bytes == null || bytes.isEmpty()) { fail("فایل خوانده نشد"); progress(i + 1, pics.size); continue }
      val sum = ImageBank.sha256(bytes)
      val m = BulkMatch.match(n, list, csv)
      val row = BulkJob.Row(key, n, bytes.size.toLong(), sum, productId = m.productId, candidates = m.candidates, how = m.how)
      //  هم‌محتوا با ردیفِ دیگر ⇒ «تکراری» در `plan`؛ دوباره آماده نمی‌شود
      if (j.rows.none { it.sum == sum } && !s.has(sum)) {
        try {
          val prep = ImageBank.prepare(bytes)
          s.put(sum, prep.display, prep.thumb)
          row.small = prep.small
        } catch (c: CancellationException) { throw c } catch (_: OutOfMemoryError) {
          row.status = BulkJob.Status.FAILED; row.note = "حافظهٔ دستگاه برای آماده‌سازیِ این عکس کافی نبود"
        } catch (e: Exception) {
          row.status = BulkJob.Status.NOT_IMAGE; row.note = e.message ?: "این فایل عکس نیست یا خراب است"
        }
      }
      j.rows += row
      s.save(j)
      progress(i + 1, pics.size)
    }
    j.plan { id -> d.products.firstOrNull { it.id == id }?.photo }
    s.save(j)
    s.tidy(j)
    j
  }

  /**
   *  یک ردیف: عکسِ دستگاه (+ همگام‌سازیِ امروز) ← بانک (اگر روشن).
   *  خطای گذرا (اینترنت) بالا می‌رود تا کار بایستد و بعداً از همان قدم ادامه یابد.
   *  ⛔ کالا و عکسش همین لحظه از تازه‌ترین دفتر دوباره سنجیده می‌شوند: کالای
   *     حذف‌شده «بی‌تطبیق» و کالایی که در این فاصله عکس گرفته بی تیکِ
   *     «جایگزینی» «عکس‌دار» می‌ماند.
   */
  suspend fun process(context: Context, store: ShopStore, job: BulkJob, row: BulkJob.Row) {
    val ready = withContext(Dispatchers.IO) { disk(context).get(row.sum) }
    if (ready == null) { row.status = BulkJob.Status.FAILED; row.note = "فایل روی این دستگاه نماند — دوباره انتخابش کنید"; return }
    val prep = ImageBank.Prepared(ready.display, ready.thumb, row.small)
    if (!row.local) {
      val p = store.data.value.products.firstOrNull { it.id == row.productId }
      if (p == null) {
        row.productId = ""; row.status = BulkJob.Status.UNMATCHED; row.note = "این کالا دیگر در فهرست نیست"
        return
      }
      if (p.photo && !job.replace) { row.status = BulkJob.Status.HAS_PHOTO; return }
      withContext(Dispatchers.IO) { PhotoStore.saveBytes(context, row.productId, prep.display) }.getOrThrow()
      //  نشانهٔ عکس روی خودِ کالا — روی تازه‌ترین دفتر، نه نسخهٔ آغازِ کار
      val cur = store.data.value
      if (cur.products.any { it.id == row.productId && !it.photo }) {
        store.save(cur.copy(products = cur.products.map { if (it.id == row.productId) it.copy(photo = true) else it }))
      }
      //  بارگذاریِ بانک همین‌جاست، نه صف
      ImageBank.photoSaved(context, row.productId, own = false)
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
    } catch (c: CancellationException) {
      throw c
    } catch (f: Exception) {
      if (ImageBank.transient(f)) { row.status = BulkJob.Status.PENDING_BANK; row.note = "روی دستگاه گذاشته شد؛ بانک منتظرِ اینترنت"; throw f }
      row.status = BulkJob.Status.DONE
      row.note = "روی دستگاه گذاشته شد؛ بانکِ تصاویر نپذیرفت: ${(f as? ir.vil3ntec.tohid.core.net.ApiFailure)?.userMessage ?: f.message.orEmpty()}"
    }
  }
}
