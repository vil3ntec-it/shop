package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import ir.vil3ntec.tohid.core.i18n.StrTransfer
import ir.vil3ntec.tohid.core.stock.StockTransfer
import ir.vil3ntec.tohid.data.CountSheetModel
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StockTransferBook
import ir.vil3ntec.tohid.data.StockTransferFlag
import ir.vil3ntec.tohid.scan.ScanRouter
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 *  INV-11 — سیم‌کشیِ «انتقالِ موجودی»: پرچم و نمای سرور (`StockTransferFlag`)، گام‌های دفتر (`StockTransferBook`)،
 *  و همان راهِ بارکدِ صندوق: بارکدخوانِ وصل/گوشیِ جفت‌شده (`ScanRouter`، تا صفحه باز است همین‌جا)، کادرِ اسکن
 *  (بارکدخوانِ صفحه‌کلیدی + Enter) و دوربین (`SalesPaths.scan`). ⛔ پرچم خاموش ⇒ صفحه چیزی نمی‌خواند و برمی‌گردد.
 */

/** فقط ساعت: «18:30» */
private fun clock(ms: Long): String {
  val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
  return cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0') + ":" +
    cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
}

/** نمای سرور (نگه‌داشته، سپس تازه) + گام‌های دفتر؛ [reload] پس از ثبتِ گام */
private class TransferState(val snap: StockTransferFlag.Snapshot, val steps: List<StockTransfer.Step>) {
  val view: StockTransfer.View = StockTransfer.project(snap.transfers, steps, snap.outcomes)
}

@Composable
private fun RouteScope.rememberTransfers(): Pair<TransferState, () -> Unit> {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val steps = remember(data.auditLog) { StockTransferBook.steps(data) }
  var snap by remember { mutableStateOf(StockTransferFlag.cached(context)) }
  val latest by rememberUpdatedState(steps)
  val reload: () -> Unit = { scope.launch { snap = StockTransferFlag.fetch(context, latest) } }
  LaunchedEffect(Unit) { snap = StockTransferFlag.fetch(context, latest) }
  return remember(snap, steps) { TransferState(snap, steps) } to reload
}

/** پرچم خاموش (یا اجازهٔ جابه‌جایی نیست) ⇒ برگشت؛ هیچ ردیفی */
@Composable
private fun RouteScope.gateOpen(): Boolean {
  val context = LocalContext.current
  val on = remember { StockTransferFlag.on(context) }
  if (!on) LaunchedEffect(Unit) { back() }
  return on
}

/** بارکدخوانِ وصل‌شده تا این صفحه باز است همین‌جا (نه صندوق) */
@Composable
private fun listenScans(onCode: (String) -> Unit) {
  val owner = remember { Any() }
  val latest by rememberUpdatedState(onCode)
  DisposableEffect(Unit) {
    ScanRouter.register(owner) { code -> latest(code); true }
    onDispose { ScanRouter.unregister(owner) }
  }
}

@Composable
internal fun RouteScope.TransferListRoute() {
  if (!gateOpen()) return
  val context = LocalContext.current
  val (state, reload) = rememberTransfers()
  val names = remember(data) { data.products.associate { it.id to it.name } }
  TransferListScreen(
    view = state.view,
    names = names,
    at = state.snap.at.takeIf { it > 0 }?.let(::clock),
    live = state.snap.live,
    hasDraft = remember { StockTransferFlag.draft(context) != null },
    onRefresh = reload,
    onNew = { nav.open(StockPaths.TRANSFER_NEW) },
    onOpen = { nav.open(StockPaths.transfer(it)) },
    onBack = backOrNull,
  )
}

@Composable
internal fun RouteScope.TransferDraftRoute() {
  if (!gateOpen()) return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val writer = rememberStockWriter()
  var draft by remember { mutableStateOf(StockTransferFlag.draft(context) ?: StockTransfer.Draft(Ulid.next())) }
  var message by remember { mutableStateOf<Pair<V3Tone, String>?>(null) }
  var busy by remember { mutableStateOf(false) }
  val index = remember(data) { ShopStore.barcodeIndex(data) }
  val byId = remember(data) { data.products.associateBy { it.id } }
  fun change(d: StockTransfer.Draft) { draft = d; message = null; StockTransferFlag.saveDraft(context, d) }
  fun scan(raw: String) {
    if (raw.isBlank()) return
    val code = raw.trim()
    val id = index[code] ?: index[ir.vil3ntec.tohid.latinDigits(code)]
    val p = id?.let(byId::get)
    message = when {
      p == null -> V3Tone.ERROR to StrTransfer.unknown(code)
      SalesEngine.isFractionalUnit(p.unit) -> V3Tone.WARNING to StrTransfer.weighed(p.name)
      else -> {
        val next = draft.plus(p.id, CountSheetMilli)
        draft = next; StockTransferFlag.saveDraft(context, next)
        V3Tone.SUCCESS to StrTransfer.scanned(p.name, CountSheetModel.text(next.qty(p.id)))
      }
    }
  }
  listenScans(::scan)
  TransferDraftScreen(
    data = data,
    draft = draft,
    message = message,
    onChange = ::change,
    onScanCode = ::scan,
    onCamera = { nav.openForResult(SalesPaths.scan("transfer")) { code -> code?.let(::scan) } },
    onSend = {
      when (val r = StockTransfer.send(draft, writer.now)) {
        is StockTransfer.Result.Bad -> message = V3Tone.ERROR to problemText(r.problem)
        is StockTransfer.Result.Ok -> if (!busy) {
          busy = true
          scope.launch {
            when (StockTransferFlag.record(context, store, r.step, writer.user, writer.today)) {
              is StockTransferFlag.Saved.Ok -> {
                StockTransferFlag.saveDraft(context, null)
                writer.info(StrTransfer.sent)
                nav.replace(StockPaths.transfer(r.step.key))
              }
              StockTransferFlag.Saved.NotSaved -> message = V3Tone.ERROR to StrTransfer.notSaved
            }
            busy = false
          }
        }
      }
    },
    onDiscard = { StockTransferFlag.saveDraft(context, null); back() },
    onBack = { back() },
  )
}

/** یک واحدِ پایه به هزارم (همان «هر اسکن یک عدد» ِ انبارگردانی) */
private const val CountSheetMilli = ir.vil3ntec.tohid.core.stock.CountScan.UNIT_MILLI

@Composable
internal fun RouteScope.TransferReceiveRoute(key: String) {
  if (!gateOpen()) return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val writer = rememberStockWriter()
  val (state, reload) = rememberTransfers()
  val t = state.view.transfers.firstOrNull { it.sendKey == key }
  if (t == null) {
    V3EmptyState(Icons.Outlined.SwapHoriz, StrTransfer.notFound, action = StrTransfer.refresh, onAction = reload)
    return
  }
  var texts by remember(key) { mutableStateOf(mapOf<String, String>()) }
  var message by remember { mutableStateOf<Pair<V3Tone, String>?>(null) }
  //  کلیدِ گامِ بعدی: همان تا ثبت شود (دو ضربه یک گام)، سپس تازه
  var stepKey by remember(key) { mutableStateOf(Ulid.next()) }
  var busy by remember { mutableStateOf(false) }
  val index = remember(data) { ShopStore.barcodeIndex(data) }
  val byId = remember(data) { data.products.associateBy { it.id } }
  val latestT by rememberUpdatedState(t)
  val queuedFinal = StockTransfer.queuedFinal(t, state.steps, state.snap.outcomes)
  val name = { id: String -> byId[id]?.name ?: id }

  fun counted(): Map<String, Long>? {
    val (ok, bad) = CountSheetModel.own(texts)
    if (bad != null) { message = V3Tone.ERROR to StrTransfer.badQty; return null }
    return ok.filterValues { it > 0 }
  }
  fun scan(raw: String) {
    if (raw.isBlank()) return
    val own = CountSheetModel.own(texts).first
    val (next, r) = StockTransfer.scan(latestT, own, raw, { c -> index[c] }) { id -> SalesEngine.isFractionalUnit(byId[id]?.unit.orEmpty()) }
    if (r is StockTransfer.Scan.Counted) texts = texts + (r.productId to CountSheetModel.text(next.getValue(r.productId)))
    message = when (r) {
      is StockTransfer.Scan.Counted -> V3Tone.SUCCESS to StrTransfer.scanned(name(r.productId), CountSheetModel.text(r.countedMilli))
      is StockTransfer.Scan.Unknown -> V3Tone.ERROR to StrTransfer.unknown(r.code)
      is StockTransfer.Scan.Weighed -> V3Tone.WARNING to StrTransfer.weighed(name(r.productId))
      is StockTransfer.Scan.NotInTransfer -> V3Tone.ERROR to StrTransfer.notInTransfer(name(r.productId))
      is StockTransfer.Scan.Over -> V3Tone.WARNING to StrTransfer.overScan(name(r.productId), milliText(r.pendingMilli, byId[r.productId]?.unit.orEmpty()))
    }
  }
  fun commit(r: StockTransfer.Result, done: String) {
    when (r) {
      is StockTransfer.Result.Bad -> message = V3Tone.ERROR to problemText(r.problem)
      is StockTransfer.Result.Ok -> if (!busy) {
        busy = true
        scope.launch {
          when (StockTransferFlag.record(context, store, r.step, writer.user, writer.today)) {
            is StockTransferFlag.Saved.Ok -> {
              writer.info(done)
              texts = emptyMap(); message = null; stepKey = Ulid.next()
              //  همگام‌سازی همین حالا فرستاد؛ نتیجهٔ سرور کمی بعد
              delay(3000); reload()
            }
            StockTransferFlag.Saved.NotSaved -> message = V3Tone.ERROR to StrTransfer.notSaved
          }
          busy = false
        }
      }
    }
  }
  if (t.status.open && !queuedFinal) listenScans(::scan)
  TransferReceiveScreen(
    data = data,
    t = t,
    texts = texts,
    message = message,
    queuedFinal = queuedFinal,
    onText = { id, v -> texts = texts + (id to v); message = null },
    onScanCode = ::scan,
    onCamera = { nav.openForResult(SalesPaths.scan("transfer")) { code -> code?.let(::scan) } },
    onAll = { texts = StockTransfer.allArrived(t).mapValues { CountSheetModel.text(it.value) }; message = null },
    onPartial = { counted()?.let { commit(StockTransfer.receive(t, stepKey, it, final = false, reason = "", at = writer.now), StrTransfer.receivedSaved) } },
    onFinal = { why -> counted()?.let { commit(StockTransfer.receive(t, stepKey, it, final = true, reason = why, at = writer.now), StrTransfer.receivedSaved) } },
    onReject = { why -> commit(StockTransfer.reject(t, stepKey, why, writer.now), StrTransfer.rejectedSaved) },
    onBack = { back() },
  )
}
