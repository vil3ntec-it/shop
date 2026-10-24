package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.focusable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import ir.vil3ntec.tohid.scan.HidScan
import ir.vil3ntec.tohid.scan.scanChar

/**
 *  بارکدخوانِ سخت‌افزاری (کابلی یا بلوتوثی) که خودش را صفحه‌کلید معرفی می‌کند — همان قاعدهٔ
 *  صندوقِ پیشین: `onKeyEvent` (نه preview) تا نوشتن در یک کادر به خودِ کادر برسد و نامِ مشتری
 *  بلعیده نشود؛ `focusable` تا بی کادرِ باز هم رویداد برسد. Enter ⇒ کدِ کامل (`HidScan.submit`)؛
 *  نویسه‌ها با کلیدِ فیزیکی (چیدمانِ فارسی/پشتو «۶» و «ش» هم همان رقم است، F0-06).
 */
fun Modifier.hidScanner(onCode: (String) -> Unit): Modifier = composed {
  val hid = remember { HidScan() }
  val focus = remember { FocusRequester() }
  val latest by rememberUpdatedState(onCode)
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  DisposableEffect(Unit) { onDispose { hid.reset() } }
  focusRequester(focus)
    .focusable()
    .onKeyEvent { event ->
      if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
      val now = System.currentTimeMillis()
      if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
        val code = hid.submit(now) ?: return@onKeyEvent false
        latest(code)
        return@onKeyEvent true
      }
      val ch = event.scanChar(allowSpace = true) ?: return@onKeyEvent false
      hid.feed(ch, now)
      true
    }
}
