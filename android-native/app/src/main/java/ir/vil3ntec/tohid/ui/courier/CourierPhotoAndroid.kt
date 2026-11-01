package ir.vil3ntec.tohid.ui.courier

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.ui.screens.PhotoSourceSheet
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/*
 *  مرزِ پلتفرمِ عکسِ بستهٔ پیکِ نسخهٔ ۳: خواندن و فشردنِ عکس (`Context`، `Bitmap`، `Uri`). از `CourierHome.kt` جدا شد
 *  تا صفحهٔ پیک زیرِ سقفِ اندازه و بی وابستگیِ تازهٔ پلتفرم بماند؛ رفتار همان است.
 */

/**
 *  عکسِ بسته (DLV-08): فقط با رضایتِ مشتری (`photoAllowed` از سرور) و فقط از
 *  بسته، نه از شخص — پیک پیش از دوربین تأیید می‌کند.
 */
@Composable
internal fun PhotoFlow(c: MissionCard, onDismiss: () -> Unit, onReady: (ByteArray) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val p = PhoneV3.colors
  var parcelOnly by remember { mutableStateOf(false) }
  var pick by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  if (!pick) {
    V3Sheet(onDismiss = onDismiss, title = "عکسِ بسته") { _ ->
      Text(
        "مشتریِ سفارشِ #${c.orderNo.fa()} به عکسِ تحویل رضایت داده است. عکس فقط از بسته گرفته شود؛ چهرهٔ کسی در عکس نباشد.",
        style = courierText(15.sp), color = p.inkMuted,
      )
      Spacer(Modifier.height(12.dp))
      V3Card(padding = PaddingValues(0.dp)) {
        V3ToggleRow("عکس فقط از بسته است", parcelOnly, { parcelOnly = it }, icon = Icons.Outlined.PhotoCamera, hue = V3Hue.SKY)
      }
      error?.let {
        Spacer(Modifier.height(8.dp))
        V3Message(V3Tone.ERROR, it)
      }
      Spacer(Modifier.height(18.dp))
      StepButton(
        "گرفتنِ عکس", { pick = true }, Modifier.fillMaxWidth(),
        enabled = parcelOnly && !busy, loading = busy, icon = Icons.Outlined.PhotoCamera,
      )
      Spacer(Modifier.height(8.dp))
      CourierSecondary("انصراف", onDismiss, Modifier.fillMaxWidth())
    }
  }
  PhotoSourceSheet(
    open = pick,
    onDismiss = { pick = false },
    note = "فقط بسته؛ عکس پس از رسیدن به دکان از این گوشی پاک می‌شود",
  ) { uri ->
    busy = true
    scope.launch {
      val bytes = withContext(Dispatchers.IO) { runCatching { parcelJpeg(context, uri) }.getOrNull() }
      busy = false
      if (bytes == null) error = "عکس خوانده نشد؛ دوباره بگیرید" else onReady(bytes)
    }
  }
}

/** کوچک و فشرده — زیرِ سقفِ ۶۰۰ کیلوبایتِ سرور */
private fun parcelJpeg(context: Context, uri: Uri): ByteArray? {
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
