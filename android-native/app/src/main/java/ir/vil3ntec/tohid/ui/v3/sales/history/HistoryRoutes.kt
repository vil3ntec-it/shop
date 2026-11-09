package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.audit.InvoiceVoid
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.FraudTrailPref
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.ReturnSettings
import ir.vil3ntec.tohid.data.CartPriceList
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleReopen
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.print.PrintJob
import ir.vil3ntec.tohid.print.PrintLog
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.sync.platform.DocChainSync
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.v3.sales.cart.HeldSession
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.ReturnsShot
import ir.vil3ntec.tohid.ui.screens.SalesVoid
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.checkout.storeName
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

/** `sales` — تاریخچه (`?filter=`، `?q=` و `?where=` از پیوند)؛ گوشی، تبلت و کامپیوتر — بدنه در `HistoryHostAndroid.kt` */
@Composable
fun RouteScope.HistoryRoute() = HistoryHost()

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
  //  PAY-07 — وضعیتِ ردیف‌های غیرنقد
  val payRows = ir.vil3ntec.tohid.data.PayStatusFlow.view(data, sale)
  val sells = remember { ShopRole.canWrite(context, "sales") }
  val manages = remember { !ShopRole.isStaff(context) }
  val payActs = payRows.any { it.manual && ir.vil3ntec.tohid.core.pay.PayStatus.allowed(it.status, sells, manages).isNotEmpty() }
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
      //  POS-32، کامپیوتر: برگهٔ اشتراک نیست ⇒ همان تصویر و PDF به فایل
      saveImage = if (Platform.DESKTOP) ({ scope.launch { toasts.show(ir.vil3ntec.tohid.print.InvoiceFile.save(context, data, sale, pdf = false)) } }) else null,
      savePdf = if (receiptV2 && Platform.DESKTOP) ({ scope.launch { toasts.show(ir.vil3ntec.tohid.print.InvoiceFile.save(context, data, sale, pdf = true)) } }) else null,
    ),
    paper = paper,
    payRows = payRows,
    //  فقط وقتی همین کاربر کاری روی یکی از ردیف‌ها دارد (ثبتِ دستیِ «تأییدشده» را فقط صاحب/مدیر اصلاح می‌کند)
    onPayRow = if (payActs) ({ nav.open(SalesPaths.payStatus(sale.id, it)) }) else null,
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

/**
 *  `sales/{saleId}/cancel` — لغو بی دلیل ثبت نمی‌شود؛ صاحب و مدیر (`sales.cancel`)، و STF-02: نقشِ «نیازمند تأیید» فقط روی
 *  دستگاهِ Sync v3 با برگهٔ امضاشدهٔ مدیر/صاحب (`CancelApproval.offered`؛ دکانِ v1 همان امروز: «لغو با صاحب یا مدیر»).
 */
@Composable
fun RouteScope.CancelRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sale = saleOrBack() ?: return
  val canCancel = remember { ShopRole.canCancelSale(context) }
  val askable = remember {
    !canCancel && ir.vil3ntec.tohid.core.perm.CancelApproval.offered(
      ShopRole.decide(context, ir.vil3ntec.tohid.core.perm.CancelApproval.PERM).decision,
      runCatching { ir.vil3ntec.tohid.sync.v3.SyncChoice.isV3(context) }.getOrDefault(false),
    )
  }
  if (!canCancel && !askable) { LaunchedEffect(Unit) { toasts.show(StrSale.cancelDenied, V3Tone.WARNING); back() }; return }
  var slip by remember(sale.id) { mutableStateOf<ir.vil3ntec.tohid.core.perm.ApprovalSlip.Slip?>(null) }
  if (!canCancel && slip == null) {
    ir.vil3ntec.tohid.ui.v3.sales.approval.ApprovalSheet(
      ask = ir.vil3ntec.tohid.ui.v3.sales.approval.ApprovalAsk(
        title = StrSale.cancelApproval,
        summary = StrSale.cancelApprovalSummary(ReceiptModel.number(sale), ir.vil3ntec.tohid.money(sale.finalTotal)),
        need = ir.vil3ntec.tohid.core.perm.CancelApproval.need(sale.id, Math.round(sale.finalTotal * 100.0)),
      ),
      shopId = remember { ShopRole.shopId(context) },
      requesterUserId = remember { ir.vil3ntec.tohid.data.TillUser.meId(context) },
      onApproved = { slip = it },
      onClose = { back() },
    )
    return
  }
  //  SEC-03: کنترل فقط از `InvoiceVoid.check` (`SalesVoid.run`) و سندِ معکوس در زنجیرهٔ اسناد فقط پس از ذخیرهٔ موفق
  val by = remember { FraudTrailPref.by(context) }
  CancelSheet(
    warning = SalesEngine.cancelWarning(sale),
    onConfirm = { reason ->
      val before = data
      val cancelSlip = slip?.let(ir.vil3ntec.tohid.data.DiscountGate::json)
      when (val r = SalesVoid.run(before, sale.id, canCancel, InvoiceVoid.Kind.VOID, reason, todayIso(), System.currentTimeMillis(), Ulid::next, by, StrSale.cancelDenied, cancelApproval = cancelSlip)) {
        is SalesVoid.Outcome.Denied -> toasts.show(r.message, V3Tone.WARNING)
        is SalesVoid.Outcome.Failed -> toasts.show(r.message, V3Tone.ERROR)
        is SalesVoid.Outcome.Ok -> {
          scope.launch { if (store.save(before, r.data)) DocChainSync.recordVoid(context, before, sale.id, InvoiceVoid.Kind.VOID, r.reason) }
          //  PRM-02: کدهای سقف‌دارِ این فاکتور پس از نشستنِ لغو در دفتر آزاد می‌شوند (بی اینترنت ⇒ صف)
          ir.vil3ntec.tohid.ui.PromoLive.releaseOnVoid(context, store, sale)
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
  val by = remember { FraudTrailPref.by(context) }
  V3ConfirmSheet(
    title = StrSale.fixTitle,
    message = StrSale.fixBody(ReceiptModel.number(sale)),
    confirm = StrSale.fixConfirm,
    icon = Icons.Outlined.Edit,
    danger = false,
    //  برگه پیش از `onConfirm` خودش بسته شده (`onDismiss`)؛ این‌جا برگشتِ دوباره نیست
    onConfirm = {
      val before = data
      val lines = HistoryModel.reopenLines(before, sale, todayIso(), Ulid::next)
      //  SEC-03: «اصلاح» همان کنترل و سندِ معکوسِ لغو را دارد (دلیلِ ثابتِ `InvoiceVoid.CORRECTION_REASON`)
      when (val r = SalesVoid.run(before, sale.id, canCancel, InvoiceVoid.Kind.CORRECTION, HistoryModel.EDIT_IN_CART, todayIso(), System.currentTimeMillis(), Ulid::next, by, StrSale.cancelDenied)) {
        is SalesVoid.Outcome.Denied -> toasts.show(r.message, V3Tone.WARNING)
        is SalesVoid.Outcome.Failed -> toasts.show(r.message, V3Tone.ERROR)
        is SalesVoid.Outcome.Ok -> {
          scope.launch { if (store.save(before, r.data)) DocChainSync.recordVoid(context, before, sale.id, InvoiceVoid.Kind.CORRECTION, r.reason) }
          //  PRM-02: فاکتورِ اصلاح‌شده لغو است ⇒ کدهایش آزاد؛ ثبتِ دوباره با کلیدِ تازه دوباره می‌گیرد
          ir.vil3ntec.tohid.ui.PromoLive.releaseOnVoid(context, store, sale)
          toasts.show(StrSale.fixDone, V3Tone.SUCCESS)
          cart.set(lines)
          cart.setDebtor(sale.debtorId)
          //  همان تخفیف، یادداشت و لیستِ فاکتور (`SaleReopen`) — پیش‌تر می‌افتادند و فاکتورِ اصلاح‌شده گران‌تر ثبت می‌شد
          cart.setNote(SaleReopen.note(sale))
          if (ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs.on(context)) {
            cart.setPriceList(CartPriceList.keep(before, lines, sale.debtorId?.let { id -> before.debtors.find { it.id == id }?.priceList.orEmpty() }))
          }
          HeldSession.keep(cart, SaleReopen.extras(before, sale, lines))
          nav.switchTab(SalesPaths.SALE)
        }
      }
    },
    onDismiss = { back() },
  )
}
