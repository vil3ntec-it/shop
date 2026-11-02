package ir.vil3ntec.tohid.ui.components.pos

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.LinePolicyPrefs
import ir.vil3ntec.tohid.data.ProductInfoMap
import ir.vil3ntec.tohid.data.QuickRegister
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.TillGoodsPref
import ir.vil3ntec.tohid.ui.screens.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 *  S03 (POS-17) — مرزِ سکوی «ثبتِ سریع» در صندوقِ گوشی و کامپیوتر (رابطِ امروز). خاموش (`TillGoodsPref.quickRegister`)
 *  ⇒ [rememberQuickTill] `null` است و صندوق همان راهِ پیشین («ثبت کالا» ⇐ صفحهٔ انبار) را می‌رود.
 *  - نقشِ مجاز (`QuickRegister.allowed`): پیامِ «این بارکد ثبت نیست»؛ Enter (کامپیوتر) یا «ثبتِ سریع» پنجره را باز
 *    می‌کند و کالای ثبت‌شده همان‌جا به فاکتور می‌رود.
 *  - صندوق‌دارِ بی‌مجوز: فقط «قلمِ آزاد»، و اگر سقفش بسته باشد هیچ گزینه‌ای.
 */
class QuickTill internal constructor(val may: Boolean, private val freeOpen: Boolean) {
  /** بارکدِ ناشناسِ همین حالا — Enter ِ کامپیوتر پنجره‌اش را باز می‌کند */
  var pending by mutableStateOf<String?>(null)
  /** بارکدِ پنجرهٔ بازِ ثبتِ سریع */
  var open by mutableStateOf<String?>(null)

  /** بارکدِ ناشناس در صندوق: پیام و راهِ بعدی */
  fun unknown(code: String, scope: CoroutineScope, snackbar: SnackbarHostState, onFreeLine: () -> Unit) {
    pending = if (may) code else null
    scope.launch {
      val r = snackbar.showSnackbar(
        if (may) "$QUICK_UNKNOWN — Enter: $QUICK_ACTION" else QUICK_UNKNOWN,
        actionLabel = if (may) QUICK_ACTION else if (freeOpen) QUICK_FREE_ACTION else null,
        duration = SnackbarDuration.Long,
      )
      if (r == SnackbarResult.ActionPerformed) { if (may) { open = code; pending = null } else onFreeLine() }
    }
  }

  /** Enter ِ کامپیوتر: کادرِ خالی یا همان بارکد ⇒ پنجرهٔ ثبتِ سریع */
  fun enter(query: String): Boolean {
    val p = pending ?: return false
    if (query.isNotBlank() && query.trim() != p) return false
    open = p
    pending = null
    return true
  }
}

@Composable
fun rememberQuickTill(): QuickTill? {
  val context = LocalContext.current
  return remember { quickTill(context) }
}

private fun quickTill(context: Context): QuickTill? =
  if (!TillGoodsPref.quickRegister(context)) null
  else QuickTill(QuickRegister.allowed(ShopRole.current(context)), QuickRegister.freeLineOpen(LinePolicyPrefs.of(context)))

/**
 *  پنجرهٔ ثبتِ سریع (اگر باز است): ثبت روی دفترِ همین لحظهٔ دیسک، سپس کالا با موجودیِ تازه به سبد. [onDone] پیامِ
 *  نتیجه را می‌گیرد (صفحه نشانش می‌دهد و کادرِ اسکن را دوباره فوکوس می‌کند).
 */
@Composable
fun QuickTillDialog(q: QuickTill?, store: ShopStore, cartStore: CartStore, onDone: (String?) -> Unit) {
  val code = q?.open ?: return
  val scope = rememberCoroutineScope()
  QuickRegisterDialog(code, onDismiss = { q.open = null; onDone(null) }) { draft ->
    when (val r = QuickRegister.register(
      store.data.value, code, draft, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId,
    )) {
      is QuickRegister.Result.Failed -> r.message
      is QuickRegister.Result.Ok -> {
        q.open = null
        scope.launch {
          if (!store.save(r.data)) { onDone(null); return@launch }
          val name = r.data.products.firstOrNull { it.id == r.productId }?.name.orEmpty()
          val added = SalesEngine.addToCartCapped(cartStore.lines.value, r.productId, r.quantity, ProductInfoMap.sellable(r.data, r.productId))
          onDone(
            if (r.quantity > 0 && cartStore.set(added.cart)) "«$name» ثبت و به فاکتور اضافه شد"
            else "«$name» ثبت شد؛ موجودی ندارد و به فاکتور نرفت",
          )
        }
        null
      }
    }
  }
}
