package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ir.vil3ntec.tohid.data.ReceiptFlag
import ir.vil3ntec.tohid.print.Receipts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.receipt.ReceiptTokens
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.ReturnSettings
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.print.PrintJob
import ir.vil3ntec.tohid.print.PrintLog
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.ReturnsShot
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.checkout.storeName
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerModel
import ir.vil3ntec.tohid.ui.v3.sales.print.PrintModel
import ir.vil3ntec.tohid.ui.v3.sales.print.PrintSheet
import kotlinx.coroutines.launch

/*
 *  چسبِ تاریخچه و فاکتور: هر کارِ پنجره‌ایِ `SalesHistoryScreen`/`InvoiceDialog` ِ پیشین حالا
 *  مسیرِ خودش است و همان موتور: `SalesEngine.cancel` (با دلیل، یا «ویرایش در سبد»)،
 *  `PrintSpooler.printSale` (چاپِ دوباره با دلیل)، `PrintJob.shareSale`.
 */

/** «برگشت و تعویض» روشن است؟ — تنظیمِ دکان، یا عکسِ CI (`ReturnsShot`) */
@Composable
internal fun returnsV2(): Boolean {
  val context = LocalContext.current
  return remember { ReturnSettings.enabled(context) } || ReturnsShot.open.value != null
}

/** `sales` — تاریخچه (`?filter=` و `?q=` از پیوند) */
@Composable
fun RouteScope.HistoryRoute() {
  val context = LocalContext.current
  val v2 = returnsV2()
  val canCancel = remember { ShopRole.canCancelSale(context) }
  val blind = remember { ShopPolicy.payV2(context) && ShopRole.isStaff(context) }
  val query = rememberSaveable(saver = HistoryQuerySaver) {
    HistoryQuery(HistoryFilter.of(args.query("filter")), args.query("q").orEmpty(), HistoryModel.ALL_MONTHS)
  }
  val today = remember { todayIso() }
  //  نشانِ همین دکان در QRِ فاکتورِ نسخهٔ ۲ (`ReceiptTokens.shopTag`)
  val shopTag = remember { ReceiptTokens.shopTag(LedgerOwner.currentShop(context)) }
  //  عکسِ CI (`DevShot` «ret»): فرمِ برگشتِ تازه‌ترین فاکتورِ زنده، یا برگشتِ بی رسید
  val shot = ReturnsShot.open.value
  LaunchedEffect(shot) {
    when (shot) {
      "first" -> data.sales.filter { HistoryModel.canReturn(data, it) }.maxByOrNull { it.createdAt }?.let { nav.open(SalesPaths.returnOf(it.id)) }
      "none" -> nav.open(SalesPaths.RETURN_NONE)
    }
  }
  HistoryScreen(
    d = data,
    today = today,
    yesterday = remember(today) { HistoryModel.yesterday(today) },
    returnsV2 = v2,
    canCancel = canCancel,
    drawer = DrawerModel.line(data, System.currentTimeMillis(), blind),
    query = query,
    actions = HistoryActions(
      open = { nav.open(SalesPaths.invoice(it)) },
      ret = { nav.open(SalesPaths.returnOf(it)) },
      cancel = { nav.open(SalesPaths.cancelOf(it)) },
      fix = { nav.open(SalesPaths.fixOf(it)) },
      returnReceipt = { nav.open(SalesPaths.returnReceipt(it)) },
      drawer = { nav.open(SalesPaths.DRAWER) },
      noReceipt = if (v2) ({ nav.open(SalesPaths.RETURN_NONE) }) else null,
      //  گوشی: QRِ رسید با دوربین؛ کامپیوتر: اسکنرِ دستی همین کادر را پر می‌کند
      scanReceipt = if (v2 && !Platform.DESKTOP) ({
        nav.openForResult(SalesPaths.scan(SalesPaths.SCAN_RECEIPT)) { code ->
          if (code != null) {
            query.search = code
            if (ReturnFlow.isForeignReceipt(code, shopTag)) toasts.show(ReturnFlow.FOREIGN_RECEIPT, V3Tone.WARNING)
            //  یک فاکتورِ زنده ⇒ همان لحظه فرمِ برگشت
            HistoryModel.scanned(data, code, shopTag)?.let { nav.open(SalesPaths.returnOf(it.id)) }
          }
        }
      }) else null,
      newSale = { nav.switchTab(SalesPaths.SALE) },
      back = backOrNull,
    ),
    bottomReserve = bottomReserve,
    shopTag = shopTag,
  )
}

private val HistoryQuerySaver = androidx.compose.runtime.saveable.listSaver<HistoryQuery, String>(
  save = { listOf(it.filter.id, it.search, it.month) },
  restore = { HistoryQuery(HistoryFilter.of(it[0]), it[1], it[2]) },
)

/** فاکتورِ مسیر، یا پیام و برگشت */
@Composable
private fun RouteScope.saleOrBack(): Sale? {
  val id = args["saleId"].orEmpty()
  val sale = data.sales.find { it.id == id }
  if (sale == null) LaunchedEffect(id) { toasts.show(StrSale.invoiceGone, V3Tone.WARNING); back() }
  return sale
}

/** `sales/{saleId}` — صفحهٔ فاکتور */
@Composable
fun RouteScope.InvoiceRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val id = args["saleId"].orEmpty()
  val sale = data.sales.find { it.id == id }
  if (sale == null) {
    V3EmptyState(Icons.Outlined.SearchOff, StrSale.invoiceGone, action = StrSale.backWord, onAction = { back() })
    return
  }
  val v2 = returnsV2()
  val canCancel = remember { ShopRole.canCancelSale(context) }
  val row = HistoryModel.row(data, sale, v2)
  //  فاکتورِ نسخهٔ ۲ فقط با پرچمِ `shop.receipt_v2` (خاموش ⇒ همین رسیدِ کارتی، مثلِ پیش)
  val receiptV2 = remember { ReceiptFlag.enabled(context) }
  val paper by produceState<ImageBitmap?>(null, sale, data.saleItems.size, receiptV2) {
    if (!receiptV2) return@produceState
    value = withContext(Dispatchers.Default) {
      //  همان QRِ رسیدِ برگشت که چاپ می‌گذارد (فروشِ بی توکن)
      val qr = if (ReturnSettings.enabled(context) && sale.status != "cancelled") ReturnFlow.receiptCode(sale) else null
      runCatching { Receipts.bitmap(context, data, sale, legacyQr = qr).asImageBitmap() }.getOrNull()
    }
  }
  InvoiceScreen(
    d = data,
    sale = sale,
    storeName = remember { storeName(context) },
    returnsV2 = v2,
    actions = InvoiceActions(
      print = { nav.open(SalesPaths.print(sale.id)) },
      //  گوشی: فاکتور به واتساپ/تلگرامِ مشتری — بی چاپگر هم
      send = if (Platform.DESKTOP) null else ({
        scope.launch { PrintJob.shareSale(context, data, sale)?.let { toasts.show(it, V3Tone.WARNING) } }
      }),
      ret = if (row.canReturn) ({ nav.open(SalesPaths.returnOf(sale.id)) }) else null,
      fix = if (row.canCancel && canCancel) ({ nav.open(SalesPaths.fixOf(sale.id)) }) else null,
      cancel = if (row.canCancel && canCancel) ({ nav.open(SalesPaths.cancelOf(sale.id)) }) else null,
      returnReceipt = { nav.open(SalesPaths.returnReceipt(it)) },
      debtor = { nav.open(MoneyPaths.debtor(it)) },
      back = backOrNull,
      pdf = if (receiptV2 && !Platform.DESKTOP) ({
        scope.launch { PrintJob.sharePdf(context, data, sale)?.let { toasts.show(it, V3Tone.WARNING) } }
      }) else null,
    ),
    paper = paper,
  )
}

/** `sales/{saleId}/print` — صفِ چاپ؛ فاکتورِ پیش‌تر چاپ‌شده فقط با دلیل (HW-02) */
@Composable
fun RouteScope.SalePrintRoute() {
  val context = LocalContext.current
  val sale = saleOrBack() ?: return
  PrintSheet(
    title = StrSale.printInvoice,
    needsReason = remember(sale.id) { PrintModel.needsReason(PrintLog.wasPrinted(context, sale.id), data, sale.id) },
    onPrint = { link, width, reason -> PrintSpooler.printSale(context, data, sale, link, width, reason) },
    onDone = { message -> toasts.show(message, V3Tone.INFO); back() },
    onClose = { back() },
  )
}

/** `sales/{saleId}/cancel` — لغو بی دلیل ثبت نمی‌شود؛ فقط صاحب و مدیر (`sales.cancel`) */
@Composable
fun RouteScope.CancelRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sale = saleOrBack() ?: return
  val canCancel = remember { ShopRole.canCancelSale(context) }
  if (!canCancel) { LaunchedEffect(Unit) { toasts.show(StrSale.cancelDenied, V3Tone.WARNING); back() }; return }
  CancelSheet(
    warning = SalesEngine.cancelWarning(sale),
    onConfirm = { reason ->
      when (val r = SalesEngine.cancel(data, sale.id, todayIso(), System.currentTimeMillis(), Ulid::next, reason = reason)) {
        is SalesEngine.Result.Failed -> toasts.show(r.message, V3Tone.ERROR)
        is SalesEngine.Result.Ok -> {
          scope.launch { store.save(r.data) }
          toasts.show(StrSale.saleCancelled, V3Tone.SUCCESS)
        }
      }
      back()
    },
    onClose = { back() },
  )
}

/**
 *  `sales/{saleId}/fix` — «اصلاحِ فاکتور»: فاکتور لغو می‌شود (موجودی برمی‌گردد) و اقلامش با
 *  همان قیمت به سبد می‌نشینند؛ ثبتِ دوباره شمارهٔ تازه می‌گیرد. فاکتور سند است و مستقیم ویرایش نمی‌شود.
 */
@Composable
fun RouteScope.FixRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sale = saleOrBack() ?: return
  val canCancel = remember { ShopRole.canCancelSale(context) }
  if (!canCancel) { LaunchedEffect(Unit) { toasts.show(StrSale.cancelDenied, V3Tone.WARNING); back() }; return }
  V3ConfirmSheet(
    title = StrSale.fixTitle,
    message = StrSale.fixBody(ReceiptModel.number(sale)),
    confirm = StrSale.fixConfirm,
    icon = Icons.Outlined.Edit,
    danger = false,
    //  برگه پیش از `onConfirm` خودش بسته شده (`onDismiss`)؛ این‌جا برگشتِ دوباره نیست
    onConfirm = {
      val lines = HistoryModel.reopenLines(data, sale, Ulid::next)
      when (val r = SalesEngine.cancel(data, sale.id, todayIso(), System.currentTimeMillis(), Ulid::next, reason = HistoryModel.EDIT_IN_CART)) {
        is SalesEngine.Result.Failed -> toasts.show(r.message, V3Tone.ERROR)
        is SalesEngine.Result.Ok -> {
          scope.launch { store.save(r.data) }
          toasts.show(StrSale.fixDone, V3Tone.SUCCESS)
          cart.set(lines)
          cart.setDebtor(sale.debtorId)
          nav.switchTab(SalesPaths.SALE)
        }
      }
    },
    onDismiss = { back() },
  )
}
