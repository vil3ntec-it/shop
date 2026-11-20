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

/**
 *  PUR-12/AI-10: عکسِ فاکتورِ کاغذی برای خواندنِ متن — ضلعِ بلند تا ۲۰۰۰ (متنِ ریز خوانا بماند)، جهتِ EXIF روی پیکسل
 *  (`ShopperPhoto.orient`؛ JPEG ِ تازه EXIF ندارد) و JPEG زیرِ ۱٫۸ مگابایت (سقفِ سرور ۶). باز نشد ⇒ null.
 */
internal fun invoiceJpeg(context: Context, uri: Uri): ByteArray? {
  val head = runCatching {
    context.contentResolver.openInputStream(uri)?.use { s ->
      val buf = ByteArray(256 * 1024)
      var n = 0
      while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
      buf.copyOf(n)
    }
  }.getOrNull() ?: return null
  val orientation = ir.vil3ntec.tohid.core.shopper.ShopperPhoto.orientationOf(head)
  val bmp = PhotoStore.decode(context, uri, side = 2400) ?: return null
  val edge = maxOf(bmp.width, bmp.height)
  val scaled = if (edge > 2000) Bitmap.createScaledBitmap(bmp, bmp.width * 2000 / edge, bmp.height * 2000 / edge, true) else bmp
  val upright = if (orientation in 2..8) {
    val w = scaled.width; val h = scaled.height
    val px = IntArray(w * h)
    scaled.getPixels(px, 0, w, 0, 0, w, h)
    val (o, ow, oh) = ir.vil3ntec.tohid.core.shopper.ShopperPhoto.orient(px, w, h, orientation)
    Bitmap.createBitmap(o, ow, oh, Bitmap.Config.ARGB_8888)
  } else scaled
  var quality = 88
  var out: ByteArray
  do {
    out = ByteArrayOutputStream().use { st -> upright.compress(Bitmap.CompressFormat.JPEG, quality, st); st.toByteArray() }
    quality -= 12
  } while (out.size > 1800 * 1024 && quality >= 40)
  if (upright !== scaled) upright.recycle()
  if (scaled !== bmp) scaled.recycle()
  bmp.recycle()
  return out.takeIf { it.size in 100..(6 * 1024 * 1024) }
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
