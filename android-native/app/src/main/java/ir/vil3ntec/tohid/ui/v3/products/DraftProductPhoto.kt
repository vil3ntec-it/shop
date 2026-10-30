package ir.vil3ntec.tohid.ui.v3.products

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.sync.PhotoSync
import ir.vil3ntec.tohid.ui.screens.PhotoPrefs
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  عکسِ بزرگِ کالا با اندازه و گوشهٔ دلخواه (کاشیِ شبکه r16، سرِ صفحهٔ کالا) —
 *  همان رفتارِ `V3ProductThumb` (عکسِ `PhotoStore`، همگامِ دستگاه‌های دیگر، و بی
 *  عکس کاشیِ حرفِ اول با رنگِ پایدار) که اندازه‌اش از `modifier` می‌آید.
 *
 *  پیش‌نویس برای کیت: `V3ProductThumb` پارامترِ `radius` و پرکردنِ پهنا ندارد؛ با
 *  افزوده شدنش این فایل کنار می‌رود (فهرستِ «Draft» در PR).
 */
@Composable
internal fun DraftProductPhoto(
  productId: String,
  name: String,
  modifier: Modifier = Modifier,
  radius: Dp = 16.dp,
  force: Boolean = false,
) {
  val c = PhoneV3.colors
  val context = LocalContext.current
  PhotoPrefs.load(context)
  val show = force || PhotoPrefs.show.value
  val synced = PhotoSync.version.value
  val local = ProductPhotos.version.intValue
  var bitmap by remember(productId, local, synced, show) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(productId, local, synced, show) {
    bitmap = if (show) withContext(Dispatchers.IO) { runCatching { PhotoStore.load(context, productId) }.getOrNull() } else null
  }
  val shape = RoundedCornerShape(radius)
  BoxWithConstraints(
    modifier.clip(shape).background(c.tile).border(1.dp, c.hairline, shape),
    contentAlignment = Alignment.Center,
  ) {
    val image = bitmap
    val edge = minOf(maxWidth, maxHeight)
    if (image != null) {
      Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
      val hue = V3Hue.entries[(name.hashCode() and 0x7fffffff) % V3Hue.entries.size]
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val size = edge.value * 0.38f
        Text(
          name.trim().take(1).ifEmpty { "•" },
          style = V3Type.headline.copy(fontSize = size.sp, lineHeight = (size * 1.25f).sp, fontWeight = FontWeight.Bold),
          color = PhoneV3Palette.glyph(c, hue),
        )
      }
    }
  }
}
