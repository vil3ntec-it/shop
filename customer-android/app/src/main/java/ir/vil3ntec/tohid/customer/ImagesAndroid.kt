package ir.vil3ntec.tohid.customer

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 *  عکسِ کالا روی اندروید: بارگیری در پس‌زمینه، کوچک کردن تا ≤ ۵۱۲px (گوشیِ ارزان) و حافظهٔ LRU ِ ~۸ مگابایتی.
 *  نشانی نسخه‌دار است (`?v=`، IMG-12)؛ عکسِ نیامده ⇒ جای خالیِ هم‌رنگ، بی خطا.
 */
object ImagesAndroid {
  private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
  }

  private fun load(url: String): ImageBitmap? {
    cache.get(url)?.let { return it }
    val c = URL(url).openConnection() as HttpURLConnection
    return try {
      c.connectTimeout = 15_000
      c.readTimeout = 30_000
      if (c.responseCode != 200) return null
      val bytes = c.inputStream.use { it.readBytes() }
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      var sample = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
      val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
      bmp?.asImageBitmap()?.also { cache.put(url, it) }
    } catch (_: Exception) {
      null
    } finally {
      c.disconnect()
    }
  }

  @Composable
  fun Photo(url: String, modifier: Modifier) {
    val img by produceState<ImageBitmap?>(cache.get(url), url) { value = withContext(Dispatchers.IO) { load(url) } }
    val b = img
    if (b != null) Image(b, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
  }
}
