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
 *  نویسه‌ها با کلیدِ فیزیکی (چیدمانِ فارسی/پشتو «۶» و «ش» هم همان رقم است، F0-06). بارکدخوانی که
 *  به‌جای Enter پسوندِ تنظیم‌شده می‌زند، کدش را از خروجیِ `HidScan.feed` می‌دهد (HW-05، همان
 *  قراردادِ `Sale.kt` در #437) — منطقش [hidKey]، خالص و سنجیده.
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
      val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
      val ch = if (enter) null else event.scanChar(allowSpace = true)
      val step = hidKey(enter, ch, System.currentTimeMillis(), hid::feed, hid::submit) ?: return@onKeyEvent false
      step.code?.let(latest)
      true
    }
}

/** یک کلید که بارکدخوان «خورد»؛ [code] کدِ کامل، اگر همین کلید اسکن را تمام کرد */
internal class HidStep(val code: String?)

/**
 *  خالص: یک کلید ⇒ `null` (به کادر یا دکمه برسد) یا [HidStep].
 *  Enter ⇒ `submit`؛ Enter ِ بی اسکن (آدم) خورده نمی‌شود. نویسه ⇒ `feed` — و خروجیِ آن **خوانده
 *  می‌شود**: پسوندِ تنظیم‌شده (بارکدخوانِ بی Enter) همین‌جا کد را تمام می‌کند.
 */
internal fun hidKey(
  enter: Boolean,
  ch: Char?,
  now: Long,
  feed: (Char, Long) -> String?,
  submit: (Long) -> String?,
): HidStep? {
  if (enter) return submit(now)?.let { HidStep(it) }
  val c = ch ?: return null
  feed(c, now)
  return HidStep(null)
}
