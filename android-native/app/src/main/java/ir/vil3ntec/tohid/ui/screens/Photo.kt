package ir.vil3ntec.tohid.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  عکسِ یک محصول، یا جای خالیِ آبرومند وقتی عکسی نیست.
 *
 *  خواندن از دیسک روی نخِ پس‌زمینه انجام می‌شود؛ در فهرستی که ده‌ها کارت
 *  دارد، خواندنِ روی نخِ اصلی اسکرول را کند می‌کند.
 *
 *  `version` برای تازه‌سازی است: وقتی عکس عوض شد، همین عدد بالا می‌رود و
 *  عکسِ کش‌شده دور ریخته می‌شود.
 */
/**
 *  نمایشِ عکسِ کالاها روشن/خاموش — انتخابِ هر دستگاه جدا (روی گوشیِ
 *  کُند یا فهرستِ بلند، بی عکس سریع‌تر است). عکس‌ها پاک نمی‌شوند.
 */
object PhotoPrefs {
  val show = androidx.compose.runtime.mutableStateOf(true)
  private var loaded = false
  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    show.value = context.getSharedPreferences("tohid-ui", 0).getBoolean("show_photos", true)
  }
  fun set(context: android.content.Context, value: Boolean) {
    show.value = value
    context.getSharedPreferences("tohid-ui", 0).edit().putBoolean("show_photos", value).apply()
  }
}

@Composable
fun ProductPhoto(
  productId: String,
  size: Dp = 48.dp,
  version: Int = 0,
  modifier: Modifier = Modifier,
  /** صفحهٔ خودِ کالا عکس را همیشه نشان می‌دهد — جای گذاشتنِ عکس همان‌جاست */
  force: Boolean = false,
  /** کالای بی‌عکس: به‌جای آیکنِ خاکستریِ «عکس» این کشیده می‌شود (کارتِ صندوق: حرفِ اولِ نام) */
  fallback: (@Composable () -> Unit)? = null,
) {
  val context = LocalContext.current
  //  عکس‌ها خاموش‌اند ⇒ هیچ جایی گرفته نمی‌شود و فایلی خوانده نمی‌شود
  PhotoPrefs.load(context)
  if (!force && !PhotoPrefs.show.value) return
  //  عکسی که از دستگاهِ دیگر رسید هم همین‌جا تازه می‌شود
  val synced = ir.vil3ntec.tohid.sync.PhotoSync.version.value
  var bitmap by remember(productId, version, synced) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(productId, version, synced) {
    bitmap = withContext(Dispatchers.IO) { PhotoStore.load(context, productId) }
  }

  Box(
    modifier
      .size(size)
      .clip(RoundedCornerShape(Radius.sm))
      .background(Shop.colors.surface2),
    contentAlignment = Alignment.Center,
  ) {
    val image = bitmap
    if (image != null) {
      Image(
        bitmap = image.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size),
      )
    } else if (fallback != null) {
      fallback()
    } else {
      Icon(
        Icons.Filled.Image,
        contentDescription = null,
        tint = Shop.colors.muted2,
        modifier = Modifier.size(size / 2.4f),
      )
    }
  }
}
