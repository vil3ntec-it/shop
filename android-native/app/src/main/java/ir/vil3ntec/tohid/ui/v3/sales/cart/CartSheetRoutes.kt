package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/*
 *  چسبِ برگه‌های سبد — هر کدام یک مسیر (`sale/line/{i}`، `sale/free-line`،
 *  `sale/keypad/{productId}`) روی همان سبدِ زنده (`CartStore`). ردیف یا کالایی که هنگامِ
 *  باز شدن دیگر نیست ⇒ برگشت، با یک پیامِ کوتاه.
 */

/** «تعداد و قیمتِ این ردیف»؛ `i` شمارهٔ ردیف در سبد */
@Composable
fun RouteScope.LineRoute() {
  val context = LocalContext.current
  val at = args.int("i") ?: -1
  val line = remember { cart.lines.value.getOrNull(at) }
  val info = remember(line) { line?.let { LineModel.info(data, it) } }
  if (line == null || info == null) {
    LaunchedEffect(Unit) { toasts.show(StrSale.lineGone, V3Tone.WARNING); back() }
    return
  }
  LineSheet(
    info = info,
    onSave = { form, start -> cart.set(LineModel.apply(cart.lines.value, info, form, start)) },
    onRemove = {
      cart.set(LineModel.remove(cart.lines.value, info))
      Haptics.confirm(context)
      toasts.undo(StrSale.removedFromCart(info.name)) { cart.set(LineModel.restore(cart.lines.value, line, at)) }
    },
    onClose = { back() },
  )
}

/** «قلمِ آزاد» — همان بوقِ «خواند» پس از افزودن */
@Composable
fun RouteScope.FreeLineRoute() {
  val context = LocalContext.current
  FreeLineSheet(
    onAdd = { form ->
      cart.set(FreeLineModel.add(cart.lines.value, form, Ulid::next))
      ScanFeedback.ok(context)
      toasts.show(StrSale.freeAdded(form.label.trim()), V3Tone.SUCCESS)
    },
    onClose = { back() },
  )
}

/** صفحه‌کلیدِ مقدار/وزنِ یک کالا */
@Composable
fun RouteScope.KeypadRoute() {
  val item = remember { KeypadModel.item(data, args["productId"].orEmpty(), cart.lines.value) }
  if (item == null) {
    LaunchedEffect(Unit) { toasts.show(StrSale.itemGone, V3Tone.WARNING); back() }
    return
  }
  KeypadSheet(
    item = item,
    onConfirm = { q -> cart.set(KeypadModel.apply(cart.lines.value, item, q)) },
    onClose = { back() },
  )
}
