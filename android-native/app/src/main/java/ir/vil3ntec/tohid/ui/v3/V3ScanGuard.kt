package ir.vil3ntec.tohid.ui.v3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import ir.vil3ntec.tohid.core.hw.FieldScanGuard
import ir.vil3ntec.tohid.print.HwTools
import ir.vil3ntec.tohid.scan.ScanPrefs
import ir.vil3ntec.tohid.scan.ScanRouter
import ir.vil3ntec.tohid.scan.scanChar
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.scan.SaleScan
import kotlin.time.TimeSource

/**
 *  HW-05 «اسکن هرگز در کادرِ قیمت یا تعداد نمی‌نشیند» در نسخهٔ ۳ — کادرهای `NUMBER` و `MONEY` ِ [V3TextField]،
 *  فقط با «ابزارهای تازهٔ دستگاه» (`HwTools`، پیش‌فرض خاموش ⇒ کادر همان امروز). همان `FieldScanGuard` ِ هسته
 *  که `NumberField` ِ رابطِ پیشین داشت: بارکدخوان مثلِ صفحه‌کلید تایپ می‌کند، پس نویسه‌ها اول به کادر می‌رسند؛
 *  وقتی هجوم اسکن از آب درآمد مقدارِ پیش از هجوم برمی‌گردد و کد به همان راهِ بارکدخوانِ وصل‌شده می‌رود
 *  ([toListenerOrCart]). پژواکِ بارکدخوان (`Hit.echo`) فقط کادر را برمی‌گرداند. تایپِ آدم دست نمی‌خورد.
 *  `ScanPrefs` و `HwTools` را `AppRoot` در آغاز می‌خواند؛ زمان یکنواخت است (جابه‌جاییِ ساعتِ دستگاه اسکن را نمی‌شکند).
 *  [toCart] = false (کادرِ شمارش و انتقال): کد فقط به صفحه‌ای که گوش می‌دهد؛ وگرنه کنار می‌رود — رفتن به صندوق آن
 *  صفحه و عددهای نوشته‌نشده‌اش را می‌بست.
 */
@Composable
internal fun Modifier.v3ScanGuard(kind: V3FieldKind, value: String, onValueChange: (String) -> Unit, toCart: Boolean = true): Modifier {
  if (!scanGuarded(kind, HwTools.on.value)) return this
  val guard = remember { FieldScanGuard(ScanPrefs.current()) }
  val clock = remember { TimeSource.Monotonic.markNow() }
  val current by rememberUpdatedState(value)
  val restore by rememberUpdatedState(onValueChange)
  fun hit(h: FieldScanGuard.Hit): Boolean {
    restore(h.restore)
    if (!h.echo) { if (toCart) toListenerOrCart(h.code) else ScanRouter.offer(h.code) }
    return true
  }
  return this.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val now = clock.elapsedNow().inWholeMilliseconds
    if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
      guard.enter(now)?.let(::hit) ?: false
    } else {
      val ch = e.scanChar() ?: return@onPreviewKeyEvent false
      //  نویسه به کادر هم می‌رسد (`false`) — تا اسکن بودنش معلوم نشده، تایپ است
      guard.key(ch, now, current)?.let(::hit) ?: false
    }
  }
}

/** فقط کادرِ عدد و پول، و فقط با «ابزارهای تازهٔ دستگاه» */
internal fun scanGuarded(kind: V3FieldKind, hwTools: Boolean): Boolean =
  hwTools && (kind == V3FieldKind.NUMBER || kind == V3FieldKind.MONEY)

/** همان راهِ `LiveScans` ِ `AppRoot`: صفحه‌ای که گوش می‌دهد (`ScanRouter.offer`)، وگرنه سبدِ صندوقِ نسخهٔ ۳ */
internal fun toListenerOrCart(
  code: String,
  offer: (String) -> Boolean = ScanRouter::offer,
  cart: (String) -> Unit = { c ->
    SaleScan.reads.trySend(SaleScan.Read(c))
    if (AppNav.route.value != SalesPaths.SALE) AppNav.go(SalesPaths.SALE)
  },
) {
  if (!offer(code)) cart(code)
}
