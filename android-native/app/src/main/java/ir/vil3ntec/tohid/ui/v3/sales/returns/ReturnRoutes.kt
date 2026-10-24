package ir.vil3ntec.tohid.ui.v3.sales.returns

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.print.PrintJob
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.ReturnsShot
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.history.returnsV2
import ir.vil3ntec.tohid.ui.v3.sales.print.PrintSheet
import kotlinx.coroutines.launch

/*
 *  چسبِ برگشت: «برگشت و تعویض» روشن ⇒ `ReturnFlow` (رسیدِ برگشت پس از ثبت)؛ خاموش ⇒ همان
 *  مرجوعیِ ساده با `SalesEngine.recordReturn`.
 */

/** `sales/{saleId}/return` */
@Composable
fun RouteScope.ReturnRoute() {
  val scope = rememberCoroutineScope()
  val id = args["saleId"].orEmpty()
  val sale = data.sales.find { it.id == id }
  if (sale == null) { LaunchedEffect(id) { toasts.show(StrSale.invoiceGone, V3Tone.WARNING); back() }; return }
  if (returnsV2()) {
    ReturnFlowSheet(
      d = data,
      sale = sale,
      save = { store.save(it) },
      onPosted = { doc, note -> toasts.show(note, V3Tone.SUCCESS); nav.replace(SalesPaths.returnReceipt(doc)) },
      onClose = { back() },
      prefill = ReturnsShot.open.value == "first",
    )
    return
  }
  ReturnSheet(
    d = data,
    sale = sale,
    onConfirm = { quantities, reason ->
      when (val r = SalesEngine.recordReturn(data, sale.id, quantities, reason, todayIso(), System.currentTimeMillis(), Ulid::next)) {
        is SalesEngine.Result.Failed -> toasts.show(r.message, V3Tone.ERROR)
        is SalesEngine.Result.Ok -> {
          scope.launch { store.save(r.data) }
          toasts.show(StrSale.returnDone, V3Tone.SUCCESS)
        }
      }
      back()
    },
    onClose = { back() },
  )
}

/** `sales/return/none` — برگشتِ بی رسید (فقط «برگشت و تعویض» روشن) */
@Composable
fun RouteScope.NoReceiptReturnRoute() {
  if (!returnsV2()) { LaunchedEffect(Unit) { back() }; return }
  ReturnFlowSheet(
    d = data,
    sale = null,
    save = { store.save(it) },
    onPosted = { doc, note -> toasts.show(note, V3Tone.SUCCESS); nav.replace(SalesPaths.returnReceipt(doc)) },
    onClose = { back() },
  )
}

/** `sales/returns/{docId}` — رسیدِ برگشت (RET-12) */
@Composable
fun RouteScope.ReturnReceiptRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val doc = args["docId"].orEmpty()
  if (ReturnFlow.doc(data, doc) == null) { LaunchedEffect(doc) { back() }; return }
  ReturnReceiptSheet(
    d = data,
    docId = doc,
    onPrint = { nav.open(SalesPaths.returnPrint(doc)) },
    onSend = if (Platform.DESKTOP) null else ({
      scope.launch { PrintJob.shareReturn(context, data, doc)?.let { toasts.show(it, V3Tone.WARNING) } }
    }),
    onClose = { back() },
  )
}

/** `sales/returns/{docId}/print` */
@Composable
fun RouteScope.ReturnPrintRoute() {
  val context = LocalContext.current
  val doc = args["docId"].orEmpty()
  if (ReturnFlow.doc(data, doc) == null) { LaunchedEffect(doc) { back() }; return }
  PrintSheet(
    title = StrSale.printReturnReceipt,
    needsReason = false,
    onPrint = { link, width, _ -> PrintJob.printReturn(context, data, doc, link, width) ?: StrSale.returnReceiptSent },
    onDone = { message -> toasts.show(message, V3Tone.INFO); back() },
    onClose = { back() },
  )
}
