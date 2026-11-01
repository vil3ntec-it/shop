package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 *  مرزِ پلتفرمِ عکسِ بسته — مشترکِ عکسِ تحویلِ پیک (DLV-08، `CourierHome`) و عکسِ بستهٔ برگهٔ جمع‌آوری (PCK-08):
 *  برگزیدن از دوربین/گالری (`PhotoSourceSheet`)، کوچک و فشرده کردن زیرِ سقفِ ۶۰۰ کیلوبایتِ سرور، و base64.
 */

/** کوچک و فشرده — زیرِ سقفِ ۶۰۰ کیلوبایتِ سرور */
internal fun parcelJpeg(context: Context, uri: Uri): ByteArray? {
  val bmp = PhotoStore.decode(context, uri) ?: return null
  val edge = maxOf(bmp.width, bmp.height)
  val scaled = if (edge > 1280) Bitmap.createScaledBitmap(bmp, bmp.width * 1280 / edge, bmp.height * 1280 / edge, true) else bmp
  var quality = 80
  var out: ByteArray
  do {
    out = ByteArrayOutputStream().use { s -> scaled.compress(Bitmap.CompressFormat.JPEG, quality, s); s.toByteArray() }
    quality -= 15
  } while (out.size > 550 * 1024 && quality >= 35)
  if (scaled !== bmp) scaled.recycle()
  bmp.recycle()
  return out.takeIf { it.size in 100..(600 * 1024) }
}

/** برگهٔ دوربین/گالری ⇒ بایت‌های فشرده، یا null وقتی عکس خوانده نشد */
@Composable
internal fun ParcelPhotoSource(open: Boolean, note: String, onDismiss: () -> Unit, onBytes: (ByteArray?) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  PhotoSourceSheet(open = open, onDismiss = onDismiss, note = note) { uri ->
    scope.launch { onBytes(withContext(Dispatchers.IO) { runCatching { parcelJpeg(context, uri) }.getOrNull() }) }
  }
}

/** بدنهٔ `imageBase64` ِ سرور */
internal fun parcelBase64(bytes: ByteArray): String = ir.vil3ntec.tohid.sync.PhotoSync.b64(bytes)

/** زمانِ دستگاه (فقط نگه داشته می‌شود؛ مرجعِ سرور زمانِ خودش است) */
internal fun parcelDeviceTime(): Long = System.currentTimeMillis()
