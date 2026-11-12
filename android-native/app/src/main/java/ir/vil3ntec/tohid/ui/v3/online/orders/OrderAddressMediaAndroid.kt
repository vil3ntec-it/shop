package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAddressMedia
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.orders.OrderAddressMedia
import ir.vil3ntec.tohid.data.ChatMedia
import ir.vil3ntec.tohid.ui.screens.feedbackImage
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import kotlinx.coroutines.launch

/**
 *  CRM-09/CHK-08 — «عکسِ دروازه» و «یادداشتِ صوتی» که مشتری برای نشانیِ همین سفارش گذاشته (`lib/store-address-media.js`).
 *  فقط برای کسی که نشانی و شمارهٔ مشتری را می‌بیند ([canSee] = `orders.contact`؛ سرور هم خودش می‌سنجد). پرچمِ خاموش، پاسخِ
 *  کهنه یا هیچ‌کدام ⇒ هیچ بخشی (همان صفحهٔ امروز). عکس با یک بار آمدن نشان داده می‌شود؛ صدا فقط با «پخش» و از همان
 *  پخش‌کنندهٔ پیامِ صوتیِ گفت‌وگو (`ChatMedia.Player`)؛ رفتن از صفحه پخش را می‌بندد. بایت جایی ذخیره نمی‌شود.
 */
@Composable
internal fun OrderAddressMediaV3(orderId: String, canSee: Boolean, api: ApiClient) {
  val summary = OrderAddressMedia.path(orderId)
  if (!canSee || summary == null) return
  AddressMediaSection("order-$orderId", summary, { OrderAddressMedia.bytesPath(orderId, it) }, api)
}

/**
 *  همان بخش برای هر درِ سرور: برنامهٔ دکان (سفارش) و برنامهٔ پیک (مأموریت، `CourierAddressMediaAndroid.kt`). [key] برای حالت و
 *  نامِ فایلِ موقتِ پخش؛ [summary] نشانیِ نما؛ [bytes] نشانیِ بایتِ هر نوع.
 */
@Composable
internal fun AddressMediaSection(key: String, summary: String, bytes: (OrderAddressMedia.Kind) -> String?, api: ApiClient) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var view by remember(key) { mutableStateOf<OrderAddressMedia.View?>(null) }
  var photo by remember(key) { mutableStateOf<ImageBitmap?>(null) }
  var photoFailed by remember(key) { mutableStateOf(false) }
  var playing by remember(key) { mutableStateOf(false) }
  var voiceFailed by remember(key) { mutableStateOf(false) }
  val player = remember(key) { ChatMedia.Player(context.applicationContext) }
  DisposableEffect(key) { onDispose { player.stop() } }

  fun loadPhoto() {
    val p = bytes(OrderAddressMedia.Kind.GATE_PHOTO) ?: return
    photoFailed = false
    scope.launch {
      val img = runCatching { feedbackImage(api.getBytes(p)) }.getOrNull()
      if (img != null) photo = img else photoFailed = true
    }
  }
  LaunchedEffect(key) {
    val v = OrderAddressMedia.parse(runCatching { api.get(summary) }.getOrNull())
    view = v
    if (v.gatePhoto != null) loadPhoto()
  }
  val v = view ?: return
  if (!OrderAddressMedia.visible(v)) return

  V3Section(StrAddressMedia.title, footer = StrAddressMedia.note) {
    if (v.gatePhoto != null) {
      val img = photo
      if (img != null) {
        Image(
          img, StrAddressMedia.gatePhoto,
          Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(12.dp).clip(RoundedCornerShape(12.dp)),
          contentScale = ContentScale.Fit,
        )
      } else {
        V3ListRow(
          StrAddressMedia.gatePhoto, subtitle = if (photoFailed) StrAddressMedia.failed else StrAddressMedia.loading,
          icon = Icons.Outlined.PhotoCamera, hue = V3Hue.SKY, chevron = false, onClick = if (photoFailed) ({ loadPhoto() }) else null,
        )
      }
    }
    if (v.gatePhoto != null && v.voiceNote != null) V3Divider(start = 64.dp)
    if (v.voiceNote != null) {
      V3ListRow(
        StrAddressMedia.voiceNote,
        subtitle = when { voiceFailed -> StrAddressMedia.failed; playing -> StrAddressMedia.stop; else -> StrAddressMedia.play },
        icon = Icons.Outlined.Mic, hue = V3Hue.MINT, chevron = false,
        onClick = {
          if (playing) { player.stop(); playing = false } else {
            val p = bytes(OrderAddressMedia.Kind.VOICE_NOTE)
            if (p != null) scope.launch {
              voiceFailed = false
              val ok = runCatching { player.play("addr-$key", api.getBytes(p)) { playing = false } }.isSuccess
              playing = ok
              voiceFailed = !ok
            }
          }
        },
      )
    }
  }
}
