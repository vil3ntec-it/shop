package ir.vil3ntec.tohid.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 *  عکسِ محصول‌ها.
 *
 *  عکس داخلِ دفترِ دکان ذخیره نمی‌شود. دفتر یک فایل JSON است که با هر
 *  تغییر کامل بازنویسی می‌شود؛ چند عکسِ base64 داخلش یعنی هر بار چند
 *  مگابایت نوشتن و برنامه‌ای که کند می‌شود. نسخهٔ وب هم به همین دلیل
 *  عکس‌ها را در IndexedDB جدا نگه می‌داشت و روی خودِ محصول فقط یک نشانه
 *  می‌گذاشت (`photo`). اینجا همان کار با فایل انجام می‌شود.
 *
 *  هر عکس پیش از ذخیره تا ۴۰۰ پیکسل کوچک و فشرده می‌شود — همان اندازه‌ای
 *  که وب می‌گرفت. یک عکسِ ۸۰۰×۶۰۰ حدود چند کیلوبایت درمی‌آید.
 */
object PhotoStore {

  /*
   *  ۸۰۰ پیکسل (پیش‌تر ۴۰۰): عکسِ ساخته‌شده در استودیو مربعِ ۸۰۰ است و
   *  روی کامپیوتر بزرگ دیده می‌شود. با زمینهٔ سفید، JPEGِ ۸۰٪ حدود ۴۰ تا
   *  ۸۰ کیلوبایت است — برای همگام‌سازی میانِ دستگاه‌ها هم سبک است.
   */
  private const val MAX_EDGE = 800
  private const val QUALITY = 80

  private fun dir(context: Context): File =
    File(context.filesDir, "product-photos").apply { if (!exists()) mkdirs() }

  fun file(context: Context, productId: String): File =
    File(dir(context), "$productId.jpg")

  /** شناسهٔ همهٔ کالاهایی که روی این دستگاه عکس دارند */
  fun localIds(context: Context): Set<String> =
    dir(context).listFiles()?.filter { it.name.endsWith(".jpg") }?.map { it.name.removeSuffix(".jpg") }?.toSet().orEmpty()

  fun exists(context: Context, productId: String): Boolean =
    file(context, productId).exists()

  /** عکسِ انتخاب‌شده را کوچک و فشرده می‌کند و کنار شناسهٔ محصول می‌گذارد */
  fun save(context: Context, productId: String, source: Uri): Result<Unit> = runCatching {
    val bitmap = context.contentResolver.openInputStream(source).use { stream ->
      requireNotNull(BitmapFactory.decodeStream(stream)) { "عکس خوانده نشد" }
    }
    val scaled = shrink(bitmap)
    file(context, productId).outputStream().use { out ->
      scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
    }
    if (scaled !== bitmap) scaled.recycle()
    bitmap.recycle()
  }

  /** عکسِ آماده (مثلاً خروجیِ استودیو) را ذخیره می‌کند */
  fun saveBitmap(context: Context, productId: String, bitmap: Bitmap): Result<Unit> = runCatching {
    val scaled = shrink(bitmap)
    file(context, productId).outputStream().use { out ->
      scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
    }
    if (scaled !== bitmap) scaled.recycle()
  }

  /** بایت‌های JPEGِ عکسِ کالا — برای فرستادن به سرور */
  fun bytes(context: Context, productId: String): ByteArray? =
    file(context, productId).takeIf { it.exists() }?.readBytes()

  /** عکسی که از سرور رسید، همان‌طور که هست (از قبل کوچک و فشرده شده) */
  fun saveBytes(context: Context, productId: String, data: ByteArray): Result<Unit> = runCatching {
    require(data.size in 100..2_000_000) { "عکس درست نیست" }
    val tmp = File(dir(context), "$productId.part")
    tmp.writeBytes(data)
    val target = file(context, productId)
    if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
  }

  /**
   *  خواندنِ عکسِ انتخاب‌شده برای استودیو — عکسِ ۱۲ مگاپیکسلیِ دوربین
   *  اول با `inSampleSize` تا حدودِ ۲۴۰۰ پیکسل کوچک خوانده می‌شود، وگرنه
   *  ۴۸ مگابایت حافظه می‌گرفت.
   */
  fun decode(context: Context, source: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2400) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, opts) }
  }.getOrNull()

  fun toStudio(bitmap: Bitmap): PhotoStudio.Image {
    val w = bitmap.width; val h = bitmap.height
    val px = IntArray(w * h)
    bitmap.getPixels(px, 0, w, 0, 0, w, h)
    return PhotoStudio.Image(w, h, px)
  }

  fun fromStudio(image: PhotoStudio.Image): Bitmap =
    Bitmap.createBitmap(image.px, image.width, image.height, Bitmap.Config.ARGB_8888)

  fun load(context: Context, productId: String): Bitmap? {
    val f = file(context, productId)
    if (!f.exists()) return null
    return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
  }

  fun delete(context: Context, productId: String) {
    runCatching { file(context, productId).delete() }
  }

  /* ------------------------ عکس‌ها، به نامِ حساب ------------------------ */

  /*
   *  عکس‌ها باید با دفتر جابه‌جا شوند.
   *
   *  اگر می‌ماندند، دو چیز پیش می‌آمد: پوشه با هر حسابِ تازه بزرگ‌تر
   *  می‌شد و هیچ‌وقت خالی نمی‌شد، و — چون شناسه‌ی محصول از دفتر می‌آید —
   *  عکسِ حسابِ قبلی روی گوشی می‌ماند بی‌آنکه چیزی به آن اشاره کند.
   *
   *  حالا دقیقاً مثل خودِ دفتر: پوشه‌ی حسابِ قبلی زیرِ نامِ او بایگانی
   *  می‌شود و پوشه‌ی حسابِ تازه سرِ جایش. احمد که برگردد، عکس‌هایش هم
   *  با دفترش برمی‌گردند.
   */

  private fun vault(context: Context, key: String): File =
    File(context.filesDir, "product-photos-${safeKey(key)}")

  fun stashTo(context: Context, key: String) {
    runCatching {
      val live = dir(context)
      val saved = vault(context, key)
      saved.deleteRecursively()
      live.renameTo(saved)
      live.mkdirs()
    }
  }

  fun openFrom(context: Context, key: String) {
    runCatching {
      val live = File(context.filesDir, "product-photos")
      live.deleteRecursively()
      val saved = vault(context, key)
      if (saved.exists()) saved.renameTo(live) else live.mkdirs()
    }
    //  حسابِ دیگر ⇒ همگام‌سازیِ عکس از اول (عکس‌های همین حساب یک بار فرستاده می‌شوند)
    ir.vil3ntec.tohid.sync.PhotoSync.forget(context)
  }

  /* ------------------------ عکسِ خودِ کاربر ------------------------ */

  /*
   *  عکسِ حساب، به نامِ همان حساب.
   *
   *  نامِ فایل شناسهٔ حساب را دارد و برای همین — برعکسِ عکسِ کالاها —
   *  به بایگانی و جابه‌جایی نیازی نیست: روی یک گوشیِ مشترک، هر حساب
   *  فایلِ خودش را می‌بیند و عکسِ نفرِ قبلی جای عکسِ نفرِ بعدی نمی‌نشیند.
   *
   *  فقط روی گوشی می‌ماند و هیچ‌جا آپلود نمی‌شود؛ پس بدونِ اینترنت هم
   *  همان‌قدر کار می‌کند.
   */
  private fun avatar(context: Context, accountId: String): File =
    File(context.filesDir, "account-avatar-${safeKey(accountId)}.jpg")

  fun saveAvatar(context: Context, accountId: String, source: Uri): Result<Unit> = runCatching {
    val bitmap = context.contentResolver.openInputStream(source).use { stream ->
      requireNotNull(BitmapFactory.decodeStream(stream)) { "عکس خوانده نشد" }
    }
    val scaled = shrink(bitmap)
    avatar(context, accountId).outputStream().use { out ->
      scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
    }
    if (scaled !== bitmap) scaled.recycle()
    bitmap.recycle()
  }

  fun loadAvatar(context: Context, accountId: String): Bitmap? {
    val f = avatar(context, accountId)
    if (!f.exists()) return null
    return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
  }

  fun deleteAvatar(context: Context, accountId: String) {
    runCatching { avatar(context, accountId).delete() }
  }

  private fun safeKey(key: String): String =
    key.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(48).ifBlank { "anon" }

  /** بلندترین ضلع به MAX_EDGE می‌رسد و نسبت‌ها دست‌نخورده می‌ماند */
  private fun shrink(source: Bitmap): Bitmap {
    val longest = maxOf(source.width, source.height)
    if (longest <= MAX_EDGE) return source
    val ratio = MAX_EDGE.toFloat() / longest
    return Bitmap.createScaledBitmap(
      source,
      (source.width * ratio).toInt().coerceAtLeast(1),
      (source.height * ratio).toInt().coerceAtLeast(1),
      true,
    )
  }
}
