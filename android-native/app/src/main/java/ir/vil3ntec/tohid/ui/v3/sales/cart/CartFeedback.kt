package ir.vil3ntec.tohid.ui.v3.sales.cart

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3SnackVisuals
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 *  پاسخِ سبد به کارهای فروشنده — صدا، لرزش و پیامِ کوتاهِ همان امروز، یک‌جا برای صندوق و
 *  فروشِ سریع (نه دو نسخه). سبد فقط از `CartModel` عوض می‌شود.
 *
 *  @param register «ثبتِ کالا» با همین بارکد (`products/new?barcode=`)
 */
class CartFeedback(
  private val context: Context,
  private val cart: CartStore,
  private val toasts: V3Toasts,
  private val snackbar: SnackbarHostState,
  private val scope: CoroutineScope,
  private val register: (String) -> Unit,
) {

  /** زدنِ کاشی: یک پله، با نگهبانِ موجودی؛ «تمام شد» لرزش و پیامِ موتور */
  fun addOne(d: ShopData, productId: String) {
    val step = CartModel.addOne(d, cart.lines.value, productId)
    step.lines?.let(cart::set)
    if (step.capped) {
      Haptics.confirm(context)
      step.note?.let { toasts.show(it, V3Tone.WARNING) }
    }
  }

  fun minusOne(d: ShopData, productId: String) {
    CartModel.minusOne(d, cart.lines.value, productId)?.let(cart::set)
  }

  /** یک بارکد ⇒ سبد؛ ناشناس ⇒ «ثبتِ کالا» همان‌جا */
  fun scan(d: ShopData, index: Map<String, String>, code: String, multiplier: Double = 1.0) {
    when (val s = CartModel.scan(d, index, cart.lines.value, code, multiplier) ?: return) {
      is CartModel.Scan.Added -> {
        cart.set(s.lines)
        if (s.capped) ScanFeedback.unknown(context) else ScanFeedback.ok(context)
        toasts.show(CartModel.scanNote(s), if (s.capped) V3Tone.WARNING else V3Tone.SUCCESS)
      }
      is CartModel.Scan.Several -> {
        ScanFeedback.unknown(context)
        toasts.show(StrSale.partialBarcode(s.count), V3Tone.WARNING)
      }
      is CartModel.Scan.Inactive -> {
        ScanFeedback.unknown(context)
        toasts.show(StrSale.inactiveActivate(s.product.name), V3Tone.WARNING)
      }
      is CartModel.Scan.Unknown -> {
        ScanFeedback.unknown(context)
        scope.launch {
          val answer = snackbar.showSnackbar(V3SnackVisuals(StrSale.unknownBarcode, V3Tone.WARNING, actionLabel = StrSale.registerItemAlt))
          if (answer == SnackbarResult.ActionPerformed) register(s.code)
        }
      }
    }
  }
}
