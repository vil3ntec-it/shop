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
 *  CHK-08 — تکه‌های «انتخاب روی نقشه» روی اندروید (`ui/PinMapView.kt`): همان الگوی `features.mapPin.tiles` ِ ویترین، مسیرِ
 *  همین سرور یا https ِ سرورِ تکه‌ها (الگو را core با همان قاعدهٔ سرور سنجیده: `PinMap.valid`). فقط GET ِ تصویر: بی کوکی، بی
 *  Referer، با نامِ برنامه در User-Agent (قاعدهٔ استفادهٔ تکه‌های OSM)، بی دنبال کردنِ ریدایرکت به پروتکلِ دیگر. حافظهٔ LRU ِ
 *  ~۱۲ مگابایتی (کشیدنِ پیاپی همان تکه‌ها را دوباره نمی‌گیرد).
 */
object MapTilesAndroid {
  private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
  }

  private fun load(url: String, agent: String): ImageBitmap? {
    cache.get(url)?.let { return it }
    //  نشانیِ کامل فقط https است (`PinMap.valid`)؛ مسیر روی همان سرورِ برنامه (که در آزمونِ محلی http هم هست)
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    val c = URL(url).openConnection() as HttpURLConnection
    return try {
      c.connectTimeout = 10_000
      c.readTimeout = 20_000
      c.instanceFollowRedirects = false
      c.useCaches = true
      c.setRequestProperty("User-Agent", agent)
      c.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/*")
      if (c.responseCode != 200) return null
      val bytes = c.inputStream.use { it.readBytes() }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { cache.put(url, it) }
    } catch (_: Exception) {
      null
    } finally {
      c.disconnect()
    }
  }

  /** [url] مسیرِ همین سرور (`/tiles/…`) یا نشانیِ کامل؛ [server] نشانیِ سرور برای مسیر */
  @Composable
  fun Tile(server: String, url: String, agent: String, modifier: Modifier, loaded: (Boolean) -> Unit) {
    val full = if (url.startsWith("/")) server.trimEnd('/') + url else url
    val img by produceState(cache.get(full), full) {
      if (value == null) value = withContext(Dispatchers.IO) { load(full, agent) }
      loaded(value != null)
    }
    val b = img
    if (b != null) Image(b, contentDescription = null, modifier = modifier, contentScale = ContentScale.FillBounds)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
  }
}
