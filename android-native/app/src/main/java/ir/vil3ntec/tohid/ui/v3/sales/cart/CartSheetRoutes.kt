package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.LinePolicyPrefs
import ir.vil3ntec.tohid.data.StockPlacesPref
import ir.vil3ntec.tohid.data.TillGoodsPref
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.MainScope

/*
 *  چسبِ برگه‌های سبد — هر کدام یک مسیر (`sale/line/{i}`، `sale/free-line`،
 *  `sale/keypad/{productId}`) روی همان سبدِ زنده (`CartStore`). ردیف یا کالایی که هنگامِ
 *  باز شدن دیگر نیست ⇒ برگشت، با یک پیامِ کوتاه.
 */

/** نوشتنِ ردیفِ سابقهٔ تأییدِ قیمت بیرون از برگه — بسته شدنِ مسیر آن را نبرد (همان `MoneyWriter`) */
private val priceAudit = MainScope()

/** «تعداد و قیمتِ این ردیف»؛ `i` شمارهٔ ردیف در سبد */
@Composable
fun RouteScope.LineRoute() {
  val context = LocalContext.current
  val at = args.int("i") ?: -1
  val line = remember { cart.lines.value.getOrNull(at) }
  //  POS-16: «موجودی در محل‌ها» فقط با «محل‌های نگه‌داری» ِ همین دستگاه (INV-20، پیش‌فرض خاموش ⇒ برگهٔ امروز)
  val placesOn = remember { StockPlacesPref.places(context) }
  //  POS-09: واحدهای کالا و بسته‌ها فقط با «واحدِ بزرگ در صندوق» ِ همین دستگاه (پیش‌فرض خاموش ⇒ برگهٔ امروز)
  val unitsOn = remember { TillGoodsPref.units(context) }
  val wholeOnly = remember { TillGoodsPref.wholePacks(context) }
  val info = remember(line) { line?.let { LineModel.withUnits(LineModel.info(data, it, places = placesOn), unitsOn, wholeOnly) } }
  if (line == null || info == null) {
    LaunchedEffect(Unit) { toasts.show(StrSale.lineGone, V3Tone.WARNING); back() }
    return
  }
  //  S45 (POS-08/10/21): «قیمت و تخفیفِ ردیف» ِ همین دستگاه (پیش‌فرض خاموش) و «درخواستِ تأیید» — همان کادرِ ردیفِ کامپیوتر
  val policy = remember { LinePolicyPrefs.of(context) }
  val priceAsk = remember { LinePolicyPrefs.ask(context, store, priceAudit, info.name, if (info.free) null else info.id) }
  LineSheet(
    info = info,
    policy = policy,
    priceAsk = priceAsk,
    onSave = { form, start, grant ->
      //  تأییدی که به کار رفت ⇒ ردیفِ سابقهٔ `price_override` با درخواست‌کننده، تأییدکننده و دلیل (فقط اگر سبد نشست)
      if (cart.set(LineModel.apply(cart.lines.value, info, form, start, policy, grant))) {
        LineModel.check(policy, form, info, start, grant).used?.let(priceAsk.onGranted)
      }
    },
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
  //  POS-10: سقفِ قیمتِ قلمِ آزاد (`LinePolicyPrefs`، پیش‌فرض خاموش) — همان `FreeLineDialog` ِ کامپیوتر
  val policy = remember { LinePolicyPrefs.of(context) }
  FreeLineSheet(
    policy = policy,
    onAdd = { form ->
      cart.set(FreeLineModel.add(cart.lines.value, form, policy, Ulid::next))
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
  //  POS-06: «سنجاق در صفحهٔ اول» ِ همین دستگاه (`TillPinsPrefs`)
  val togglePin = rememberTillPinToggle()
  val pins by TillPinsPrefs.pins
  KeypadSheet(
    item = item,
    onConfirm = { q -> cart.set(KeypadModel.apply(cart.lines.value, item, q)) },
    onClose = { back() },
    pinned = item.product.id in pins,
    onPin = {
      val change = togglePin(item.product.id)
      TillPinsPrefs.message(change, item.product.name)?.let {
        toasts.show(it, if (change == ir.vil3ntec.tohid.core.pos.TillPins.Change.FULL) V3Tone.WARNING else V3Tone.SUCCESS)
      }
    },
  )
}
