package ir.vil3ntec.tohid.ui.v3

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import ir.vil3ntec.tohid.ui.screens.PhotoPrefs
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  عکسِ کوچکِ کالا: همان عکسِ `PhotoStore` (و همگامِ دستگاه‌های دیگر)؛ بی عکس —
 *  یا وقتی نمایشِ عکس در این دستگاه خاموش است — کاشیِ حرفِ اولِ نام با رنگِ
 *  پایدارِ پالتِ پشتیبان، تا فهرست هرگز خالی/خاکستریِ بی‌معنی نشود.
 */
@Composable
fun V3ProductThumb(
  productId: String,
  name: String,
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
  version: Int = 0,
  force: Boolean = false,
) {
  val c = PhoneV3.colors
  val context = LocalContext.current
  PhotoPrefs.load(context)
  val show = force || PhotoPrefs.show.value
  val synced = ir.vil3ntec.tohid.sync.PhotoSync.version.value
  var bitmap by remember(productId, version, synced, show) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(productId, version, synced, show) {
    bitmap = if (show) withContext(Dispatchers.IO) { runCatching { PhotoStore.load(context, productId) }.getOrNull() } else null
  }
  val shape = RoundedCornerShape(size * 0.27f)
  Box(
    modifier.size(size).clip(shape).background(c.tile).border(1.dp, c.hairline, shape),
    contentAlignment = Alignment.Center,
  ) {
    val image = bitmap
    if (image != null) {
      Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
    } else {
      val hue = V3Hue.entries[(name.hashCode() and 0x7fffffff) % V3Hue.entries.size]
      Text(
        name.trim().take(1).ifEmpty { "•" },
        style = V3Type.headline.copy(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold),
        color = PhoneV3Palette.glyph(c, hue),
      )
    }
  }
}
