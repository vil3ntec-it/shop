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
@Composable
fun ProductPhoto(
  productId: String,
  size: Dp = 48.dp,
  version: Int = 0,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var bitmap by remember(productId, version) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(productId, version) {
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
