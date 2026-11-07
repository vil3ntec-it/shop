package ir.vil3ntec.tohid.customer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import ir.vil3ntec.tohid.core.shopper.ChatPhoto
import ir.vil3ntec.tohid.core.shopper.ShopperPhoto
import java.io.ByteArrayOutputStream

/**
 *  عکسِ گفت‌وگو روی اندروید (CHAT-05/06): بایت‌ها از گزینشگرِ سیستم (بی اجازهٔ حافظه)، کوچک کردن تا ≤ ۱۶۰۰px، جهتِ
 *  EXIF از خودِ بایت‌ها (`ShopperPhoto.orientationOf`) و چرخش/آینهٔ پیکسل‌ها، شفافیت روی سفید، و JPEG ِ تازه — پس EXIF
 *  (مکان، دستگاه، زمان) نمی‌رود. کیفیت پله‌پله پایین می‌آید تا زیرِ سقفِ سرور (۱٫۵ مگابایت، پیش‌نمایش ۶۴ کیلوبایت).
 */
object PhotoAndroid {
  private const val MAX_INPUT = 25 * 1024 * 1024

  fun prepare(resolver: ContentResolver, uri: Uri): ChatPhoto? = try {
    val bytes = resolver.openInputStream(uri)?.use { s -> s.readBytes().takeIf { it.size in 1..MAX_INPUT } }
    bytes?.let { prepare(it) }
  } catch (_: Exception) {
    null
  } catch (_: OutOfMemoryError) {
    null
  }

  fun prepare(bytes: ByteArray): ChatPhoto? {
    val mime = ShopperPhoto.mimeOf(bytes) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ShopperPhoto.MAIN_PX) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
      inSampleSize = sample
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }) ?: return null
    val (w, h) = ShopperPhoto.fit(decoded.width, decoded.height, ShopperPhoto.MAIN_PX)
    val scaled = if (w == decoded.width && h == decoded.height) decoded else Bitmap.createScaledBitmap(decoded, w, h, true)
    val px = IntArray(w * h)
    scaled.getPixels(px, 0, w, 0, 0, w, h)
    if (scaled !== decoded) decoded.recycle()
    scaled.recycle()
    val orientation = if (mime == "image/jpeg") ShopperPhoto.orientationOf(bytes) else 1
    val (out, ow, oh) = ShopperPhoto.orient(ShopperPhoto.flattenOnWhite(px), w, h, orientation)
    val main = Bitmap.createBitmap(out, ow, oh, Bitmap.Config.ARGB_8888)
    val mainJpeg = jpeg(main, ShopperPhoto.MAIN_QUALITY, ShopperPhoto.MAIN_MAX_BYTES) ?: run { main.recycle(); return null }
    val (tw, th) = ShopperPhoto.fit(ow, oh, ShopperPhoto.THUMB_PX)
    val thumbBmp = Bitmap.createScaledBitmap(main, tw, th, true)
    val thumb = jpeg(thumbBmp, ShopperPhoto.THUMB_QUALITY, ShopperPhoto.THUMB_MAX_BYTES)
    if (thumbBmp !== main) thumbBmp.recycle()
    main.recycle()
    return ChatPhoto(mainJpeg, thumb, ow, oh)
  }

  private fun jpeg(b: Bitmap, start: Int, max: Int): ByteArray? {
    for (q in ShopperPhoto.qualities(start)) {
      val out = ByteArrayOutputStream()
      b.compress(Bitmap.CompressFormat.JPEG, q, out)
      if (out.size() <= max) return out.toByteArray()
    }
    return null
  }

  /** بایت‌های عکسِ گفت‌وگو ⇒ تصویر (یک بار برای همان بایت‌ها) */
  @Composable
  fun Picture(bytes: ByteArray, modifier: Modifier) {
    val img = remember(bytes) { runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }
    if (img != null) Image(img, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
  }
}
