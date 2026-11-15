package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.platform.rememberPurchaseOrderHost
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/**
 *  «الان رسیده؟ نه» ⇒ همان سفارشِ خریدِ امروز (`PoForms.draft` ⇐ `create` با کلیدِ همین فرم ⇐ `send`)، سپس پیش‌پرداخت (اگر بود)
 *  با `LedgerEngine.paySupplier` و کلیدِ «کلید-pay». ⛔ اتمی و تکرارپذیر: سرور اول؛ تا سفارش فرستاده نشده هیچ چیز در دفتر نمی‌نشیند.
 *  دوباره زدن (یا قطعِ نت پس از سرور) همان سفارش است (`Idempotency-Key`، `replayed`) و فرستادنِ سفارشِ فرستاده تکرار نمی‌شود؛
 *  پیش‌پرداختِ همان کلید دوباره ثبت نمی‌شود. کالا، بها و بدهی فقط با «دریافتِ کالا» (`PurchaseOrderBook.post`) می‌آیند.
 */
@Composable
internal fun RouteScope.rememberSaveLater(key: String): (PurchaseForm, (Boolean) -> Unit) -> Unit {
  val repo = rememberPurchaseOrderHost().repo
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  return { f, done ->
    val d = data
    val supplierId = f.supplierId.orEmpty()
    when (val chk = PoForms.draft(d, supplierId, PurchaseFormModel.orderRows(f, d), "", f.notes.trim())) {
      is PoForms.DraftCheck.Bad -> { toasts.show(chk.message, V3Tone.ERROR); done(false) }
      is PoForms.DraftCheck.Ok -> scope.launch {
        val made = repo.create(key, chk.draft).valueOrNull()
        if (made == null) { toasts.show(StrPurchases.orderFailed, V3Tone.ERROR); done(false); return@launch }
        val sent = if (PurchaseOrderBook.status(made) == PurchaseOrderCore.Status.DRAFT) repo.send(made.id).valueOrNull() else made
        if (sent == null) { toasts.show(StrPurchases.orderNotSent(made.no), V3Tone.ERROR); done(false); return@launch }
        val prepaid = PurchaseFormModel.paid(f)
        val payKey = "$key-pay"
        val base = store.data.value
        if (prepaid > 0 && base.supplierPayments.none { it.id == payKey }) {
          val r = LedgerEngine.paySupplier(base, supplierId, prepaid, f.date, StrPurchases.prepaidNote(sent.no), todayIso(), System.currentTimeMillis(), ::moneyId, key = payKey)
          MoneyWriter.ledger(store, null, context, base, r, StrPurchases.orderSent(sent.no))
        }
        toasts.show(StrPurchases.orderSent(sent.no), V3Tone.SUCCESS)
        done(true)
      }
    }
  }
}
