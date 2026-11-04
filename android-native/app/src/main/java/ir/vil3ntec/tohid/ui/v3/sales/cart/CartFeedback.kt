package ir.vil3ntec.tohid.ui.v3.sales.cart

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ScaleLabels
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.TillGoodsPref
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3SnackVisuals
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 *  پاسخِ کوتاهِ سبد به یک کار — متن، رنگِ پیام و (بارکدِ ناشناس) کارِ بعدی با همان کد: برچسبِ دکمه و مسیرش
 *  («ثبتِ کالا»، «ثبتِ سریع» یا «قلمِ آزاد» — `QuickTillModel`)
 */
data class CartNote(val text: String, val tone: V3Tone, val action: String? = null, val path: String? = null)

/**
 *  پاسخِ سبد به کارهای فروشنده — صدا، لرزش و پیامِ کوتاهِ همان امروز، یک‌جا برای صندوق و
 *  فروشِ سریع (نه دو نسخه). سبد فقط از `CartModel` عوض می‌شود. پیام را صدازننده نشان
 *  می‌دهد: پیامِ کوتاهِ پوسته (`show`) یا خطِ پایینِ برگهٔ اسکن.
 *
 *  @param open بازکردنِ مسیرِ کارِ پیام (بارکدِ ناشناس ⇒ `QuickTillModel.path`)
 */
class CartFeedback(
  private val context: Context,
  private val cart: CartStore,
  private val toasts: V3Toasts,
  private val snackbar: SnackbarHostState,
  private val scope: CoroutineScope,
  private val open: (String) -> Unit,
) {

  /** زدنِ کاشی: یک پله، با نگهبانِ موجودی؛ «تمام شد» لرزش و پیامِ موتور */
  fun addOne(d: ShopData, productId: String) {
    val step = CartModel.addOne(d, cart.lines.value, productId)
    //  POS-12: وسطِ جابه‌جاییِ فاکتور سبد قفل است ⇒ اضافه نشد
    if (step.lines != null && !cart.set(step.lines)) { locked(); return }
    if (step.capped) {
      Haptics.confirm(context)
      step.note?.let { toasts.show(it, V3Tone.WARNING) }
    }
  }

  /** POS-03 — لمسِ کاشی با پیشوندِ «۳*»: همان [addOne] با شمار */
  fun addCount(d: ShopData, productId: String, count: Double) {
    val step = CartModel.addCount(d, cart.lines.value, productId, count)
    if (step.lines != null && !cart.set(step.lines)) { locked(); return }
    if (step.capped) {
      Haptics.confirm(context)
      step.note?.let { toasts.show(it, V3Tone.WARNING) }
    }
  }

  fun minusOne(d: ShopData, productId: String) {
    CartModel.minusOne(d, cart.lines.value, productId)?.let(cart::set)
  }

  /**
   *  یک بارکد ⇒ سبد: اول برچسبِ ترازو (اگر دکان روشنش کرده — `ScaleLabels`)، بعد شناختنِ
   *  کالا (`CartModel.scan`) با `multiplier`. صدای «خواند» یا «ناشناس» همین‌جا؛ پیام برمی‌گردد.
   */
  fun scan(d: ShopData, index: Map<String, String>, code: String, multiplier: Double = 1.0): CartNote? {
    when (val w = ScaleLabels.match(ScaleLabels.rules(context), code.trim(), index, d.products)) {
      null -> Unit
      is ScaleLabels.Match.Rejected -> {
        ScanFeedback.unknown(context)
        return CartNote(w.message, V3Tone.WARNING)
      }
      is ScaleLabels.Match.Weighed -> {
        val s = CartModel.weighed(d, cart.lines.value, w.product, w.quantity)
        if (!cart.set(s.lines)) { ScanFeedback.unknown(context); return CartNote(CartStore.NOT_ADDED, V3Tone.WARNING) }
        if (s.capped) ScanFeedback.unknown(context) else ScanFeedback.ok(context)
        return CartNote(CartModel.scanNote(s), if (s.capped) V3Tone.WARNING else V3Tone.SUCCESS)
      }
    }
    //  PRD-04/S05: بارکدِ بسته؛ قیمتِ لیستِ بسته فقط با «واحدِ بزرگ در صندوق» (`TillGoodsPref.units`)
    val packPrice = TillGoodsPref.units(context)
    val wholeOnly = TillGoodsPref.wholePacks(context)
    return when (val s = CartModel.scan(d, index, cart.lines.value, code, multiplier, packPrice, wholeOnly) ?: return null) {
      is CartModel.Scan.Added -> {
        if (!cart.set(s.lines)) { ScanFeedback.unknown(context); return CartNote(CartStore.NOT_ADDED, V3Tone.WARNING) }
        if (s.capped) ScanFeedback.unknown(context) else ScanFeedback.ok(context)
        CartNote(CartModel.scanNote(s), if (s.capped) V3Tone.WARNING else V3Tone.SUCCESS)
      }
      is CartModel.Scan.Several -> {
        ScanFeedback.unknown(context)
        CartNote(StrSale.partialBarcode(s.count), V3Tone.WARNING)
      }
      is CartModel.Scan.Inactive -> {
        ScanFeedback.unknown(context)
        CartNote(StrSale.inactiveActivate(s.product.name), V3Tone.WARNING)
      }
      is CartModel.Scan.Ambiguous -> {
        ScanFeedback.unknown(context)
        CartNote(StrSale.ambiguousBarcode(s.code), V3Tone.WARNING)
      }
      is CartModel.Scan.PackClash -> {
        ScanFeedback.unknown(context)
        CartNote(StrSale.packPriceClash(s.product.name, s.pack), V3Tone.WARNING)
      }
      is CartModel.Scan.PackPart -> {
        ScanFeedback.unknown(context)
        CartNote(StrSale.partPackRejected(s.pack), V3Tone.WARNING)
      }
      is CartModel.Scan.Unknown -> {
        //  CRM-16: کارتِ مشتری (`CustomerCardPref`، پیش‌فرض خاموش) — فقط کدی که هیچ کالایی نمی‌خواند
        ir.vil3ntec.tohid.data.CustomerCards.atTill(ir.vil3ntec.tohid.data.CustomerCardPref.on(context), d, code, index)?.let { c ->
          if (!cart.setDebtor(c.id)) { ScanFeedback.unknown(context); return CartNote(CartStore.NOT_ADDED, V3Tone.WARNING) }
          ScanFeedback.ok(context)
          return CartNote(ir.vil3ntec.tohid.core.i18n.StrCustomerCard.picked(c.name), V3Tone.SUCCESS)
        }
        ScanFeedback.unknown(context)
        //  S03 (POS-17): «ثبتِ سریع» در خودِ صندوق، «قلمِ آزاد» یا «ثبتِ کالا» — همان قاعدهٔ صندوقِ کامپیوتر
        QuickTillModel.unknown(s.code, ir.vil3ntec.tohid.ui.components.pos.quickNext(context))
      }
    }
  }

  /** پیام در میزبانِ پیامِ پوسته؛ بارکدِ ناشناس با دکمهٔ کارِ بعدی */
  fun show(note: CartNote) {
    val path = note.path
    val label = note.action
    if (path == null || label == null) { toasts.show(note.text, note.tone); return }
    scope.launch {
      val answer = snackbar.showSnackbar(V3SnackVisuals(note.text, note.tone, actionLabel = label))
      if (answer == SnackbarResult.ActionPerformed) open(path)
    }
  }

  private fun locked() {
    ScanFeedback.unknown(context)
    toasts.show(CartStore.NOT_ADDED, V3Tone.WARNING)
  }
}
