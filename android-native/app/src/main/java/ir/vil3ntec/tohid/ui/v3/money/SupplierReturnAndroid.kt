package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.SupplierReturnDialog
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3SnackVisuals
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** نوشتن و پیامِ پس از بستنِ برگه — برگه با `back()` از ترکیب بیرون می‌رود، پس نه حوزهٔ همان صفحه */
private val after = MainScope()

/**
 *  `purchasing/suppliers/{id}/return` — برگشت به تأمین‌کننده (PUR-08): کالا از انبار بیرون و «سندِ کسر» بدهی را کم
 *  می‌کند (`PurchaseBook.supplierReturn`، همان پنجرهٔ `SupplierReturnDialog` ِ خریدِ کامپیوتر و گوشیِ پیشین). S25:
 *  پس از ثبت همان برگهٔ چاپیِ «سندِ کسر» ِ کامپیوتر (`PurchaseBook.invoiceSheet`) با چاپِ گوشی (`ReportPrint`).
 */
@Composable
internal fun RouteScope.SupplierReturnRoute() {
  val supplierId = args["id"].orEmpty()
  if (data.suppliers.none { it.id == supplierId }) { LandOn(MoneyPaths.PURCHASING); return }
  val context = LocalContext.current
  val d = data
  SupplierReturnDialog(d, supplierId, onDismiss = { back() }) { productId, q, price, reason, date, key ->
    when (val r = PurchaseBook.supplierReturn(
      d, supplierId, productId, q, price, reason, date, todayIso(), System.currentTimeMillis(), ::moneyId,
      userId = runCatching { SyncStore(context).accountName }.getOrNull(), key = key,
    )) {
      is PurchaseBook.Result.Failed -> toasts.show(r.message, V3Tone.ERROR)
      is PurchaseBook.Result.Ok -> {
        back()
        after.launch {
          if (!store.save(r.data)) return@launch
          val credit = PurchaseBook.invoices(r.data, supplierId).filter { it.isReturn }.maxByOrNull { it.createdAt }
          val asked = toasts.host.showSnackbar(V3SnackVisuals(StrReach.returnSaved, V3Tone.SUCCESS, if (credit != null) StrReach.printCreditNote else null))
          if (asked == SnackbarResult.ActionPerformed && credit != null) {
            PurchaseBook.invoiceSheet(r.data, credit).let { sh -> ir.vil3ntec.tohid.print.ReportPrint.print(context, sh, sh.name) }
          }
        }
      }
    }
  }
}
