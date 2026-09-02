package ir.vil3ntec.tohid.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 *  پشتیبانِ کامل — دفتر **به‌علاوهٔ عکس‌ها**.
 *
 *  ── چه اشکالی را می‌بندد ────────────────────────────────────────────
 *  عکسِ کالاها درست و حساب‌شده جدا از دفتر نگهداری می‌شود (`PhotoStore`)
 *  و روی خودِ کالا فقط پرچمِ `photo = true` می‌نشیند — تا دفترِ JSON با
 *  چند مگابایت عکس سنگین نشود.
 *
 *  ولی `exportBackup()` فقط همان دفتر را می‌نوشت. یعنی فایلِ پشتیبان
 *  **هیچ عکسی نداشت**. گوشی که عوض می‌شد یا خراب می‌شد، کاربر پشتیبانش
 *  را برمی‌گرداند، همه‌چیز سرِ جایش بود — و همهٔ عکس‌ها رفته بودند، در
 *  حالی که برنامه هنوز فکر می‌کرد عکس دارند و جای خالیِ عکس را نشان
 *  می‌داد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── شکلِ فایل ──────────────────────────────────────────────────────
 *  یک ZIP ساده:
 *
 *      shop-data.json      ← همان فایلی که تا امروز ساخته می‌شد، مو به مو
 *      photos/<شناسه>.jpg  ← عکسِ هر کالا
 *
 *  چون `shop-data.json`ِ داخلش دقیقاً همان قالبِ قبلی است، هرکس ZIP را
 *  باز کند همان فایلِ آشنا را دارد و می‌تواند در نسخهٔ وب هم بازش کند.
 *  و پشتیبانِ سادهٔ JSON هم سرِ جایش می‌ماند: `read` هر دو را می‌شناسد.
 *  ──────────────────────────────────────────────────────────────────
 */
object BackupBundle {

  const val LEDGER_ENTRY = "shop-data.json"
  private const val PHOTO_DIR = "photos/"

  /** امضای هر فایلِ ZIP: `PK` */
  private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

  /** بیش از این اندازه، فایل عکس نیست و باز نمی‌شود */
  private const val MAX_PHOTO_BYTES = 4L * 1024 * 1024

  data class Opened(val data: ShopData, val photos: Int)

  /* ------------------------------ نوشتن ------------------------------ */

  /**
   *  نوشتنِ پشتیبانِ کامل روی یک جریانِ خروجی.
   *
   *  @param ledgerJson همان چیزی که `ShopStore.exportBackup()` می‌سازد
   */
  fun write(context: Context, out: OutputStream, ledgerJson: String): Result<Int> = runCatching {
    var count = 0
    ZipOutputStream(out.buffered()).use { zip ->
      zip.putNextEntry(ZipEntry(LEDGER_ENTRY))
      zip.write(ledgerJson.toByteArray(Charsets.UTF_8))
      zip.closeEntry()

      photoDir(context).listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".jpg") }
        .forEach { photo ->
          //  عکسِ خراب یا نیمه‌نوشته نباید کلِ پشتیبان را بشکند
          runCatching {
            zip.putNextEntry(ZipEntry("$PHOTO_DIR${photo.name}"))
            photo.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            count++
          }
        }
    }
    count
  }

  /* ------------------------------ خواندن ------------------------------ */

  /**
   *  خواندنِ یک فایلِ پشتیبان — ZIP یا JSONِ ساده.
   *
   *  عکس‌ها **فوراً روی دیسک نمی‌نشینند**: بازیابی کاری است که برنمی‌گردد
   *  و کاربر باید اول ببیند داخلِ فایل چیست. عکس‌ها در پوشهٔ موقت
   *  می‌مانند و با `commitPhotos` سرِ جایشان می‌روند — یعنی فقط وقتی که
   *  کاربر بازیابی را تأیید کرده باشد.
   *
   *  @param parse همان `ShopStore.parseBackup` — تا قاعدهٔ «این فایل
   *    پشتیبانِ توحید هست یا نه» یک جا بماند
   */
  fun read(
    context: Context,
    input: InputStream,
    parse: (String) -> Result<ShopData>,
  ): Result<Opened> = runCatching {
    //  چند بایتِ اول می‌گوید ZIP است یا متن. `BufferedInputStream` لازم
    //  است چون بعد از نگاه کردن باید به عقب برگردیم.
    val head = input.buffered(DEFAULT_BUFFER_SIZE)
    head.mark(ZIP_MAGIC.size)
    val magic = ByteArray(ZIP_MAGIC.size)
    val got = head.read(magic)
    head.reset()

    if (got < ZIP_MAGIC.size || !magic.contentEquals(ZIP_MAGIC)) {
      //  پشتیبانِ سادهٔ JSON — همان راهِ همیشگی
      val text = head.reader(Charsets.UTF_8).readText()
      return@runCatching Opened(parse(text).getOrThrow(), 0)
    }

    val staging = staging(context)
    staging.deleteRecursively()
    staging.mkdirs()

    var ledger: String? = null
    var photos = 0

    ZipInputStream(head).use { zip ->
      var entry: ZipEntry? = zip.nextEntry
      while (entry != null) {
        val name = entry.name
        when {
          !entry.isDirectory && name.endsWith(LEDGER_ENTRY) && !name.contains(PHOTO_DIR) ->
            ledger = zip.readBytes().toString(Charsets.UTF_8)

          !entry.isDirectory && name.startsWith(PHOTO_DIR) && name.endsWith(".jpg") -> {
            /*
             *  فقط نامِ خودِ فایل برداشته می‌شود، نه مسیرِ داخلِ ZIP.
             *  فایلِ دست‌ساز می‌تواند مسیری مثل `photos/../../x` داشته
             *  باشد و بیرونِ پوشه بنویسد؛ همین یک خط جلویش را می‌گیرد.
             */
            val leaf = File(name).name
            if (leaf.isNotBlank() && leaf.endsWith(".jpg")) {
              val target = File(staging, leaf)
              if (target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                var written = 0L
                target.outputStream().use { out ->
                  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                  while (true) {
                    val n = zip.read(buffer)
                    if (n <= 0) break
                    written += n
                    if (written > MAX_PHOTO_BYTES) break
                    out.write(buffer, 0, n)
                  }
                }
                if (written in 1..MAX_PHOTO_BYTES) photos++ else target.delete()
              }
            }
          }
        }
        zip.closeEntry()
        entry = zip.nextEntry
      }
    }

    val text = ledger ?: error("این فایل، پشتیبانِ توحید نیست")
    Opened(parse(text).getOrThrow(), photos)
  }

  /**
   *  عکس‌های خوانده‌شده سرِ جایشان می‌نشینند.
   *
   *  فقط پس از تأییدِ بازیابی صدا زده می‌شود. عکسِ کالاهایی که در دفترِ
   *  تازه نیستند هم می‌ماند و ضرری ندارد — با حذفِ همان کالا پاک می‌شود.
   */
  fun commitPhotos(context: Context): Int {
    val staging = staging(context)
    if (!staging.exists()) return 0
    val target = photoDir(context)
    var moved = 0
    staging.listFiles().orEmpty().forEach { photo ->
      runCatching {
        photo.copyTo(File(target, photo.name), overwrite = true)
        moved++
      }
    }
    staging.deleteRecursively()
    return moved
  }

  fun dropStaging(context: Context) {
    runCatching { staging(context).deleteRecursively() }
  }

  /**
   *  پرچمِ `photo` را با واقعیتِ دیسک یکی می‌کند.
   *
   *  پشتیبانِ قدیمی (JSONِ بدونِ عکس) که برگردانده شود، همهٔ کالاها
   *  `photo = true` دارند ولی هیچ فایلی نیست — و کاربر جای خالیِ عکس را
   *  می‌بیند و فکر می‌کند برنامه خراب است. این تابع پرچم را با آنچه
   *  واقعاً روی دیسک هست هماهنگ می‌کند.
   */
  fun reconcilePhotoFlags(context: Context, d: ShopData): ShopData {
    val have = photoDir(context).listFiles().orEmpty()
      .filter { it.isFile }
      .mapTo(HashSet()) { it.name.removeSuffix(".jpg") }
    var changed = false
    val products = d.products.map { p ->
      val real = p.id in have
      if (p.photo == real) p else { changed = true; p.copy(photo = real) }
    }
    return if (changed) d.copy(products = products) else d
  }

  private fun photoDir(context: Context): File =
    File(context.filesDir, "product-photos").apply { if (!exists()) mkdirs() }

  private fun staging(context: Context): File =
    File(context.cacheDir, "restore-photos")
}
