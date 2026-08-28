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

  private const val MAX_EDGE = 400
  private const val QUALITY = 82

  private fun dir(context: Context): File =
    File(context.filesDir, "product-photos").apply { if (!exists()) mkdirs() }

  fun file(context: Context, productId: String): File =
    File(dir(context), "$productId.jpg")

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

  fun load(context: Context, productId: String): Bitmap? {
    val f = file(context, productId)
    if (!f.exists()) return null
    return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
  }

  fun delete(context: Context, productId: String) {
    runCatching { file(context, productId).delete() }
  }

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
