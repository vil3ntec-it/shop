package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.shopper.GeoPin
import ir.vil3ntec.tohid.core.shopper.MapPinFeature
import ir.vil3ntec.tohid.core.shopper.PinMap
import ir.vil3ntec.tohid.core.shopper.PinText
import ir.vil3ntec.tohid.core.shopper.PlaceText
import kotlin.math.roundToInt

/**
 *  CHK-08 / ۴-۳ — «انتخاب روی نقشه» ِ برنامهٔ خرید: همان «کاشیِ ثابت + پین» ِ ویترین (`store/pin-map.js`) بی کتابخانهٔ نقشه.
 *  ریاضی و الگوی تکه‌ها در core ([PinMap]، بردارِ مشترکِ وب)؛ بارگیریِ تکه با سکو ([Platform.tile]: بی کوکی، فقط تصویر).
 *  نشانهٔ وسط ثابت است و نقشه زیرش با کشیدن یا دکمه‌های جهت جابه‌جا می‌شود (برای صفحه‌خوان و بی لمسِ دقیق هم)؛ «همین‌جا» ⇒
 *  [onPick] (همان راهِ «موقعیتِ من»). هیچ تکه‌ای نیامد ⇒ پیامِ روشن به جای نقشهٔ خاکستری.
 *  ⛔ جهت‌ها فیزیکی‌اند (شمال بالا، غرب چپ) ⇒ قابِ نقشه و دکمه‌های جهت همیشه چپ‌به‌راست، حتی در فارسی.
 */
@Composable
fun PinMapBox(feature: MapPinFeature, start: GeoPin?, onPick: (GeoPin) -> Unit, onClose: () -> Unit) {
  val tile = LocalPlatform.current.tile ?: return
  var view by remember { mutableStateOf(PinMap.open(start)) }
  var size by remember { mutableStateOf(IntSize.Zero) }
  var loaded by remember { mutableIntStateOf(0) }
  var failed by remember { mutableIntStateOf(0) }
  val density = LocalDensity.current.density
  val step = PinMap.STEP.toDouble()
  val here = PinMap.here(view)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Box(
        Modifier.fillMaxWidth().height(260.dp).clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)
          .onSizeChanged { size = it }
          .pointerInput(Unit) {
            detectDragGestures { change, drag ->
              change.consume()
              view = PinMap.move(view, -drag.x / density.toDouble(), -drag.y / density.toDouble())
            }
          }
          .semantics { contentDescription = PlaceText.mapView },
      ) {
        //  واحدِ نقشه dp است (تکهٔ ۲۵۶ ⇒ ۲۵۶dp)، همان اندازهٔ دیدنیِ وب روی هر تراکمِ صفحه
        val w = (size.width / density).roundToInt()
        val h = (size.height / density).roundToInt()
        for (t in PinMap.tiles(view, w, h, feature.tiles)) {
          key(t.key) {
            tile(t.url, Modifier.absoluteOffset { IntOffset((t.left * density).roundToInt(), (t.top * density).roundToInt()) }.size(PinMap.SIZE.dp)) { ok ->
              if (ok) loaded++ else failed++
            }
          }
        }
        Icon(
          Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.align(Alignment.Center).offset(y = (-18).dp).size(36.dp),
        )
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { view = PinMap.move(view, -step, 0.0) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, PlaceText.mapWest) }
        IconButton(onClick = { view = PinMap.move(view, 0.0, -step) }) { Icon(Icons.Filled.KeyboardArrowUp, PlaceText.mapNorth) }
        IconButton(onClick = { view = PinMap.move(view, 0.0, step) }) { Icon(Icons.Filled.KeyboardArrowDown, PlaceText.mapSouth) }
        IconButton(onClick = { view = PinMap.move(view, step, 0.0) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, PlaceText.mapEast) }
      }
    }
    Text(PlaceText.mapHelp, style = MaterialTheme.typography.bodySmall)
    Text(
      if (loaded == 0 && failed > 0) PlaceText.mapFail else PlaceText.mapWhere(PinText.short(here)),
      style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(onClick = { view = PinMap.zoom(view, 1) }, enabled = view.z < PinMap.MAX_Z) { Text(PlaceText.mapIn) }
      OutlinedButton(onClick = { view = PinMap.zoom(view, -1) }, enabled = view.z > PinMap.MIN_Z) { Text(PlaceText.mapOut) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Button(onClick = { onPick(here) }) { Text(PlaceText.mapPick) }
      TextButton(onClick = onClose) { Text(PlaceText.mapClose) }
    }
    MapSource(feature)
  }
}

/** «© OpenStreetMap» با پیوندِ پروانه (شرطِ ODbL) — فقط https */
@Composable
private fun MapSource(feature: MapPinFeature) {
  val uri = LocalUriHandler.current
  val link = feature.copyright.takeIf { it.startsWith("https://") }
  val name = feature.attribution.ifBlank { "OpenStreetMap" }
  val m = if (link == null) Modifier else Modifier.clickable(role = Role.Button) { runCatching { uri.openUri(link) } }
  Text(PlaceText.mapSource(name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = m)
}
