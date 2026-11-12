package ir.vil3ntec.tohid.customer.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/**
 *  عکسِ کالا و عکسِ گفت‌وگو روی آیفون — همتای `ImagesAndroid.kt`/`PhotoAndroid.Picture`: بارگیری در پس‌زمینه با همان
 *  [HttpIos]، رمزگشایی با Skia ِ خودِ Compose، و حافظهٔ کوچکِ ۴۰ تایی. نیامد ⇒ جای خالیِ هم‌رنگ، بی خطا.
 */
object ImagesIos {
  private val cache = LinkedHashMap<String, ImageBitmap>()

  private fun decode(bytes: ByteArray): ImageBitmap? =
    try { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() } catch (_: Throwable) { null }

  @Composable
  fun Photo(http: HttpIos, path: String, modifier: Modifier) {
    val img by produceState(cache[path], path) {
      if (value == null) value = withContext(Dispatchers.IO) {
        val r = try { http.send(ShopperRequest("GET", path, binary = true)) } catch (_: Throwable) { null }
        r?.bytes?.takeIf { r.status == 200 }?.let { decode(it) }
      }?.also { b ->
        cache[path] = b
        while (cache.size > 40) cache.remove(cache.keys.first())
      }
    }
    val b = img
    if (b != null) Image(b, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
  }

  /** فرستندهٔ هر مبدأ ِ سرورِ تکه‌ها (نشانیِ کامل؛ `PinMap.valid` فقط https می‌پذیرد) */
  private val origins = HashMap<String, HttpIos>()

  /**
   *  CHK-08 — یک تکهٔ «انتخاب روی نقشه» (`PinMapView.kt`)، همتای `MapTilesAndroid.Tile`: مسیرِ همین سرور با [http]، نشانیِ
   *  https ِ سرورِ تکه‌ها با فرستندهٔ همان مبدأ؛ بی کوکی (نشستِ ephemeral ِ `HttpIos`)، [loaded] یک بار با «آمد؟».
   */
  @Composable
  fun Tile(http: HttpIos, url: String, modifier: Modifier, loaded: (Boolean) -> Unit) {
    val img by produceState(cache[url], url) {
      //  فرستندهٔ مبدأ روی همین نخِ صفحه (نقشهٔ `origins` از چند نخ نوشته نمی‌شود)
      val (sender, path) = if (url.startsWith("/")) http to url else {
        val cut = url.indexOf('/', "https://".length).takeIf { url.startsWith("https://") && it > 0 }
        if (cut == null) null to url else origins.getOrPut(url.substring(0, cut)) { HttpIos { url.substring(0, cut) } } to url.substring(cut)
      }
      if (value == null) value = withContext(Dispatchers.IO) {
        val r = try { sender?.send(ShopperRequest("GET", path, binary = true)) } catch (_: Throwable) { null }
        r?.bytes?.takeIf { r.status == 200 }?.let { decode(it) }
      }?.also { b ->
        cache[url] = b
        while (cache.size > 40) cache.remove(cache.keys.first())
      }
      loaded(value != null)
    }
    val b = img
    if (b != null) Image(b, contentDescription = null, modifier = modifier, contentScale = ContentScale.FillBounds)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
  }

  @Composable
  fun Picture(bytes: ByteArray, modifier: Modifier) {
    val img = remember(bytes) { decode(bytes) }
    if (img != null) Image(img, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
  }
}
