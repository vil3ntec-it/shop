package ir.vil3ntec.tohid.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import ir.vil3ntec.tohid.core.image.ImagePrep
import ir.vil3ntec.tohid.core.image.JpegMeta
import ir.vil3ntec.tohid.core.image.StorefrontMediaSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 *  عکسِ کاربر ⇒ لوگو یا تصویرِ سرِ ویترین با اندازهٔ ثابت (`StorefrontMediaSpec`):
 *  چرخشِ EXIF روی پیکسل‌ها، بُرشِ وسط به نسبتِ نوع، کوچک‌کردن به عرضِ هدف، JPEG ِ
 *  زیرِ سقفِ حجم و بی EXIF/GPS (`JpegMeta.strip`). روی نخِ پس‌زمینه.
 */
object StorefrontMediaPrep {
  /** فایلِ بزرگ‌تر پیش از رمزگشایی رد می‌شود */
  const val MAX_INPUT_BYTES = 25 * 1024 * 1024

  suspend fun prepare(bytes: ByteArray, kind: StorefrontMediaSpec.Kind): ByteArray = withContext(Dispatchers.Default) {
    require(bytes.size <= MAX_INPUT_BYTES) { "فایل بیش از اندازه بزرگ است" }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, bounds)
    val w0 = bounds.outWidth
    val h0 = bounds.outHeight
    require(w0 > 0 && h0 > 0) { "این فایل عکس نیست یا خراب است" }
    require(!ImagePrep.tooManyPixels(w0, h0)) { "عکس بیش از اندازه بزرگ است (بیش از ۶۰ مگاپیکسل)" }
    val orientation = if (JpegMeta.isJpeg(bytes)) JpegMeta.orientation(bytes) else 1
    //  نمونه‌برداری فقط تا جایی که بُرش هنوز از عرضِ هدف بزرگ‌تر بماند
    val upright0 = ImagePrep.oriented(w0, h0, orientation)
    val crop0 = StorefrontMediaSpec.crop(upright0.w, upright0.h, kind)
    StorefrontMediaSpec.target(crop0.w, kind)   // عکسِ خیلی کوچک ⇒ همین‌جا پیامِ روشن
    val opts = BitmapFactory.Options().apply {
      inSampleSize = ImagePrep.sampleSize(crop0.w, crop0.w, kind.maxW)
    }
    val decoded = BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, opts)
      ?: throw IllegalArgumentException("این فایل عکس نیست یا خراب است")
    val straight = try { ImageBank.upright(decoded, orientation) } finally { decoded.recycle() }
    try {
      val r = StorefrontMediaSpec.crop(straight.width, straight.height, kind)
      val start = StorefrontMediaSpec.target(r.w, kind)
      //  بُرش و کوچک‌کردن در یک کشیدن (ماتریسِ مقیاس + جابه‌جایی) — بی بومِ میانی
      fun encode(s: ImagePrep.Size, q: Int): ByteArray {
        val sx = s.w.toFloat() / r.w
        val sy = s.h.toFloat() / r.h
        val b = Bitmap.createBitmap(s.w, s.h, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
          drawColor(Color.WHITE)
          drawBitmap(straight, Matrix().apply { setValues(floatArrayOf(sx, 0f, -r.x * sx, 0f, sy, -r.y * sy, 0f, 0f, 1f)) }, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, q, out)
        b.recycle()
        return JpegMeta.strip(out.toByteArray())
      }
      val (jpeg, size) = ImagePrep.encodeUnder(start, kind.maxBytes, ::encode)
      require(StorefrontMediaSpec.accepts(size, kind)) { "${kind.label} به اندازهٔ لازم کوچک نشد؛ عکسِ ساده‌تری انتخاب کنید" }
      jpeg
    } finally {
      straight.recycle()
    }
  }
}
