package ir.vil3ntec.tohid.pay.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ir.vil3ntec.tohid.core.image.ImagePrep
import ir.vil3ntec.tohid.core.image.JpegMeta
import ir.vil3ntec.tohid.data.ImageBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 *  عکسِ QR ِ حساب‌پیِ دکان (صاحب‌کار ۱۴۰۵/۰۸/۲۶ — «عکسِ QR ِ حساب‌پیم را بار می‌کنم»؛ هم‌زادِ کامپیوتری: ‎ShopQrPictureDesk.kt‎).
 *   - [prepare]: عکسِ گالری/دوربین ⇒ چرخشِ EXIF روی پیکسل، شفافی روی سفید، ضلعِ بلند ≤ ۱۰۲۴، JPEG ِ بی فراداده (همان که سرور
 *     دوباره می‌سازد) و **متنِ QR ِ خودِ همان عکسِ کوچک‌شده** (ML Kit، بی شبکه). بی QR ⇒ [Prep.NoQr]؛ متن را سرور با همان قاعدهٔ
 *     شمارهٔ حساب می‌سنجد.
 *   - [read]: صندوق پیش از نشان دادنِ عکسِ ذخیره‌شده دوباره می‌خواندش (فقط همان متن ⇒ عکس، وگرنه QR ِ کشیده).
 *   - [picker]: گالری/عکس‌گیر ِ سیستم (فقط عکس) ⇒ بایت‌ها، تا [MAX_INPUT_BYTES].
 */
object ShopQrPicture {
  const val MAX_INPUT_BYTES = 25 * 1024 * 1024
  const val MAX_SIDE = 1024
  private const val QUALITY = 92

  sealed interface Prep {
    class Ok(val jpeg: ByteArray, val payload: String) : Prep
    data object NotImage : Prep
    data object TooBig : Prep
    data object NoQr : Prep
  }

  suspend fun prepare(bytes: ByteArray): Prep = withContext(Dispatchers.Default) {
    if (bytes.size > MAX_INPUT_BYTES) return@withContext Prep.TooBig
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext Prep.NotImage
    if (ImagePrep.tooManyPixels(bounds.outWidth, bounds.outHeight)) return@withContext Prep.TooBig
    val opts = BitmapFactory.Options().apply { inSampleSize = ImagePrep.sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE) }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@withContext Prep.NotImage
    val orientation = if (JpegMeta.isJpeg(bytes)) JpegMeta.orientation(bytes) else 1
    val straight = try { ImageBank.upright(decoded, orientation) } finally { decoded.recycle() }
    val k = minOf(1f, MAX_SIDE.toFloat() / maxOf(straight.width, straight.height))
    val w = maxOf(1, (straight.width * k).toInt())
    val h = maxOf(1, (straight.height * k).toInt())
    val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(flat).apply {
      drawColor(Color.WHITE)
      drawBitmap(straight, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    straight.recycle()
    val jpeg = ByteArrayOutputStream().use { out -> flat.compress(Bitmap.CompressFormat.JPEG, QUALITY, out); out.toByteArray() }
    flat.recycle()
    val payload = read(jpeg) ?: return@withContext Prep.NoQr
    Prep.Ok(jpeg, payload)
  }

  /** متنِ نخستین QR ِ عکس (ML Kit ِ درونِ برنامه)؛ هیچ ⇒ null */
  suspend fun read(bytes: ByteArray): String? {
    val bmp = withContext(Dispatchers.Default) {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
      val opts = BitmapFactory.Options().apply { inSampleSize = ImagePrep.sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE * 2) }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } ?: return null
    val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    return try {
      suspendCancellableCoroutine { cont ->
        scanner.process(InputImage.fromBitmap(bmp, 0))
          .addOnSuccessListener { codes -> cont.resume(codes.firstNotNullOfOrNull { c -> c.rawValue?.trim()?.takeIf { it.isNotEmpty() } }) }
          .addOnFailureListener { cont.resume(null) }
      }
    } finally {
      scanner.close()
      bmp.recycle()
    }
  }

  /** نمایشِ عکسِ ذخیره‌شده */
  fun bitmap(bytes: ByteArray): ImageBitmap? = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()

  /** «انتخابِ عکسِ QR»: گالری/عکس‌گیرِ سیستم ⇒ بایت‌ها (لغو ⇒ هیچ؛ خواندن نشد ⇒ null) */
  @Composable
  fun picker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val app = LocalContext.current.applicationContext
    val done = rememberUpdatedState(onPicked)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) done.value(runCatching { app.contentResolver.openInputStream(uri)?.use { s -> s.readBytes().takeIf { it.size <= MAX_INPUT_BYTES } } }.getOrNull())
    }
    return { runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
  }
}
