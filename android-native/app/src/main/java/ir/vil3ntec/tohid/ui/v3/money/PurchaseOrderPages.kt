package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeliver
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrPurchaseUnits
import ir.vil3ntec.tohid.core.i18n.StrReturnPages
import ir.vil3ntec.tohid.core.i18n.StrSafe
import ir.vil3ntec.tohid.core.i18n.StrSupplierBills
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.product.PurchaseUnits
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.purchase.ReorderCore
import ir.vil3ntec.tohid.data.PoConfigDto
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PoReceiptDto
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.repo.PurchaseOrderRepository
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.platform.PurchaseOrderHost
import ir.vil3ntec.tohid.ui.platform.rememberPurchaseOrderHost
import ir.vil3ntec.tohid.ui.screens.PoSuggest
import ir.vil3ntec.tohid.ui.screens.purchaseUnitsOn
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PickerRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.stock.ProductPickerList
import kotlinx.coroutines.launch

/*
 *  PUR-02/03/04/14 روی گوشیِ نسخهٔ ۳ — «خرید و تأمین‌کننده ⇐ سفارش‌های خرید» (پیش‌تر فقط پنجرهٔ «سفارش‌های خرید» ِ کامپیوتر):
 *    `purchasing/orders`               فهرست با پالایهٔ وضعیت (پیش‌نویس، فرستاده، بخشی، کامل، لغو)
 *    `purchasing/orders/new`           ساختن (تأمین‌کننده، قلم، مقدار، فی؛ پیشنهاد با فروش، کم‌موجودها، «همهٔ کالاهای این تأمین‌کننده»)
 *    `purchasing/orders/suggest`       پیشنهادِ سفارش (همان `PoSuggest` ِ کامپیوتر: پیش‌نویس با یک کلید برای هر تأمین‌کننده)
 *    `purchasing/orders/{id}`          جزئیات، فرستادن، لغو با دلیل، رسیدها و ثبت در دفتر
 *    `purchasing/orders/{id}/edit`     ویرایشِ پیش‌نویس (نسخهٔ دیده‌شده؛ کهنه ⇒ ۴۰۹ ِ سرور)
 *    `purchasing/orders/{id}/receive`  دریافتِ کامل/جزئی با اسکن (بارکدخوانِ وصل، کادرِ بارکد، دوربین)، خراب و باقی‌ماندهٔ باز
 *    `purchasing/orders/{id}/match`    سفارش ⇔ رسید ⇔ فاکتور با اختلاف‌های برجسته
 *  همهٔ منطق در `data/PoForms` و `data/PurchaseOrderBook` (مشترکِ کامپیوتر) و `core/purchase` (همان بردارهای سرور)؛ این‌جا فقط
 *  سیم‌کشی و کشیدن است. ⛔ پرچمِ خاموشِ `shop.purchase_orders` ⇒ در پنهان و صفحه «روشن نیست». ⛔ مجوز همان سرور:
 *  `permissions.manage` (`stock.adjust`) ساختن/ویرایش/فرستادن/لغو، `permissions.receive` (`stock.move`) دریافت؛ دکمهٔ کارِ
 *  نامجاز دیده نمی‌شود و سرور خودش دوباره می‌سنجد. کلیدِ یکتای ساختن و شناسهٔ رسید همان کامپیوتر (یک بار برای هر فرم).
 */

/** درِ «سفارش‌های خرید» در زبانهٔ خرید: فقط با پرچمِ روشن (همان `PurchaseOrdersEntry` ِ کامپیوتر)؛ بی اینترنت پنهان */
@Composable
internal fun rememberPurchaseOrdersDoor(): Boolean {
  val host = rememberPurchaseOrderHost()
  var cfg by remember { mutableStateOf<PoConfigDto?>(null) }
  LaunchedEffect(host) { cfg = host.repo.config().valueOrNull() }
  return cfg?.enabled == true
}

/* ══════════════════════════ بارگذاری و درِ صفحه ══════════════════════════ */

/** `config` و دادهٔ صفحه؛ با «دوباره» ([tick]) هر دو از نو، دادهٔ پیشین تا آمدنِ تازه می‌ماند */
internal class PoState<T : Any> {
  var cfg by mutableStateOf<PoConfigDto?>(null)
  var cfgFailure by mutableStateOf<ApiFailure?>(null)
  var value by mutableStateOf<T?>(null)
  var failure by mutableStateOf<ApiFailure?>(null)

  fun gate(need: (PoPermsDto) -> Boolean): PoForms.Gate =
    PoForms.loaded(PoForms.gate(cfg, cfgFailure, need), value != null, failure)
}

private fun ApiResult<*>.failure(): ApiFailure? = (this as? ApiResult.Failure)?.error

@Composable
internal fun <T : Any> rememberPoState(
  repo: PurchaseOrderRepository,
  key: Any?,
  tick: Int,
  need: (PoPermsDto) -> Boolean,
  load: suspend (PurchaseOrderRepository) -> ApiResult<T>,
): PoState<T> {
  val st = remember(key) { PoState<T>() }
  LaunchedEffect(key, tick) {
    st.cfgFailure = null
    st.failure = null
    val c = repo.config()
    c.valueOrNull()?.let { st.cfg = it } ?: run { st.cfgFailure = c.failure() }
    val cfg = st.cfg ?: return@LaunchedEffect
    if (!cfg.enabled || !need(cfg.permissions)) return@LaunchedEffect
    val r = load(repo)
    r.valueOrNull()?.let { st.value = it } ?: run { st.failure = r.failure() }
  }
  return st
}

/** حالتِ صفحه پیش از داده: در راه، بی اینترنت، خطا، پرچمِ خاموش، نقشِ بی اجازه */
@Composable
internal fun PoGateView(gate: PoForms.Gate, failure: ApiFailure?, denied: String, onRetry: () -> Unit) {
  when (gate) {
    PoForms.Gate.LOADING -> V3Skeleton(rows = 4, modifier = Modifier.padding(top = 16.dp))
    PoForms.Gate.OFFLINE -> V3EmptyState(
      Icons.Outlined.CloudOff, StrPurchaseOrders.offline, body = StrPurchaseOrders.offlineBody,
      action = StrSupplierBills.retry, onAction = onRetry,
    )
    PoForms.Gate.FAILED -> V3EmptyState(
      Icons.Outlined.ErrorOutline, StrPurchaseOrders.failedTitle, body = failure?.userMessage,
      action = StrSupplierBills.retry, onAction = onRetry,
    )
    PoForms.Gate.OFF -> V3EmptyState(Icons.Outlined.Inventory2, StrSupplierBills.off)
    PoForms.Gate.DENIED -> V3EmptyState(Icons.Outlined.Lock, denied)
    PoForms.Gate.OPEN -> Unit
  }
}

private fun pill(s: PurchaseOrderCore.Status): V3Status = when (s) {
  PurchaseOrderCore.Status.DRAFT -> V3Status.INACTIVE
  PurchaseOrderCore.Status.SENT -> V3Status.AWAITING
  PurchaseOrderCore.Status.PARTIAL -> V3Status.WARNING
  PurchaseOrderCore.Status.COMPLETE -> V3Status.DELIVERED
  PurchaseOrderCore.Status.CANCELLED -> V3Status.CANCELLED
}

internal fun poAfn(minor: Long): String = money(PurchaseOrderBook.asAfn(minor))
internal fun poUnits(milli: Long): String = qty(PurchaseOrderBook.units(milli))

/** ستونِ پیمایش‌پذیرِ همهٔ صفحه‌ها (پهنای گوشی، فاصلهٔ ۱۶) */
@Composable
internal fun PoPage(content: @Composable () -> Unit) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
}

/* ══════════════════════════ فهرست ══════════════════════════ */

/** `purchasing/orders` */
@Composable
internal fun RouteScope.PurchaseOrdersRoute() {
  val repo = rememberPurchaseOrderHost().repo
  val scope = rememberCoroutineScope()
  var tick by remember { mutableStateOf(0) }
  var filter by remember { mutableStateOf(PoForms.Filter.of(args.query("status"))) }
  val st = rememberPoState(repo, filter, tick, { true }) { it.list(PoForms.serverState(filter), status = PoForms.serverStatus(filter)) }
  var extra by remember(filter, tick) { mutableStateOf(emptyList<PurchaseOrderDto>()) }
  var next by remember(filter, st.value) { mutableStateOf(st.value?.next) }
  var more by remember { mutableStateOf(false) }
  val c = PhoneV3.colors
  val perms = st.cfg?.permissions ?: PoPermsDto()
  PoPage {
    Text(StrPurchaseOrders.note, style = V3Type.footnote, color = c.inkMuted)
    val gate = st.gate { true }
    if (gate != PoForms.Gate.OPEN) {
      PoGateView(gate, st.failure ?: st.cfgFailure, StrPurchaseOrders.manageOnly) { tick++ }
      return@PoPage
    }
    Spacer(Modifier.height(12.dp))
    V3ChipRow(contentPadding = PaddingValues(0.dp)) {
      PoForms.Filter.entries.forEach { f -> V3Chip(PoForms.label(f), selected = filter == f, onClick = { filter = f }) }
    }
    Spacer(Modifier.height(12.dp))
    //  زیرِ هم با پهنای کامل (همان کارهای صفحهٔ جزئیات): کنارِ هم در ۳۶۰dp نوشتهٔ هر دو دکمه بریده می‌شد («New purcha…»)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (perms.manage) {
        V3Button(
          StrPurchasePages.newPoTitle,
          { nav.openForResult(MoneyPaths.NEW_PURCHASE_ORDER) { id -> tick++; if (id != null) nav.open(MoneyPaths.purchaseOrder(id)) } },
          Modifier.fillMaxWidth(), icon = Icons.Outlined.Add,
        )
      }
      V3Button(
        StrPurchasePages.reorderSuggest, { nav.open(MoneyPaths.PURCHASE_ORDER_SUGGEST) }, Modifier.fillMaxWidth(),
        kind = V3ButtonKind.SECONDARY,
      )
    }
    val all = st.value?.orders.orEmpty() + extra
    val list = PoForms.shown(all, filter)
    when {
      list.isEmpty() && next == null && filter == PoForms.Filter.ALL -> V3EmptyState(
        Icons.Outlined.LocalShipping, StrPurchasePages.noPoYet, body = StrPurchaseOrders.emptyBody,
        action = if (perms.manage) StrPurchasePages.newPoTitle else null,
        onAction = if (perms.manage) ({ nav.openForResult(MoneyPaths.NEW_PURCHASE_ORDER) { tick++ } }) else null,
      )
      list.isEmpty() && next == null -> V3EmptyState(Icons.Outlined.LocalShipping, StrPurchaseOrders.noneFiltered)
      list.isNotEmpty() -> V3Section(null) {
        list.forEachIndexed { i, o ->
          if (i > 0) V3Divider()
          val s = PurchaseOrderBook.status(o)
          V3ListRow(
            StrSupplierBills.orderTitle(o.no, o.supplierName.ifBlank { StrMoney.supplierLabel }),
            subtitle = StrPurchasePages.poLine(o.lines.size, poAfn(o.totals.orderedMinor), poAfn(o.totals.remainingMinor)) +
              (o.expectedDate?.let { StrPurchasePages.arrivesSuffix(formatDate(it)) } ?: ""),
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            trailing = { V3StatusPill(pill(s), PurchaseOrderBook.label(s)) },
            chevron = false,
            onClick = { nav.openForResult(MoneyPaths.purchaseOrder(o.id)) { tick++ } },
          )
        }
      }
    }
    val cursor = next
    if (cursor != null) {
      V3Button(
        StrSupplierBills.more,
        {
          if (!more) {
            more = true
            scope.launch {
              repo.list(PoForms.serverState(filter), cursor, PoForms.serverStatus(filter)).valueOrNull()?.let { p -> extra = extra + p.orders; next = p.next }
                ?: toasts.show(StrPurchaseOrders.failedTitle, V3Tone.ERROR)
              more = false
            }
          }
        },
        Modifier.fillMaxWidth().padding(top = 12.dp), kind = V3ButtonKind.SECONDARY, loading = more,
      )
    }
  }
}

/* ══════════════════════════ جزئیات ══════════════════════════ */

/** `purchasing/orders/{id}` — نتیجه (`nav.finish`) ندارد؛ فهرست با برگشت دوباره می‌خواند */
@Composable
internal fun RouteScope.PurchaseOrderRoute(id: String) {
  val host = rememberPurchaseOrderHost()
  val repo = host.repo
  val scope = rememberCoroutineScope()
  var tick by remember { mutableStateOf(0) }
  val st = rememberPoState(repo, id, tick, { true }) { it.get(id) }
  val guard = remember { PoForms.SubmitGuard() }
  var busy by remember { mutableStateOf(false) }
  var cancelAsk by remember { mutableStateOf(false) }
  val c = PhoneV3.colors
  val perms = st.cfg?.permissions ?: PoPermsDto()

  fun act(block: suspend () -> ApiResult<PurchaseOrderDto>, done: String) {
    if (!guard.start()) return
    busy = true
    scope.launch {
      val r = block()
      busy = false
      guard.done()
      r.valueOrNull()?.let { st.value = it; toasts.show(done, V3Tone.SUCCESS) }
        ?: toasts.show(r.errorMessage() ?: StrSupplierBills.failedShort, V3Tone.ERROR)
    }
  }

  /** رسیدی که در دفترِ همین دستگاه نیست (دستگاهِ دیگر گرفت) ⇒ همان فاکتورِ خریدِ کامپیوتر با شناسه‌های قطعی */
  fun post(o: PurchaseOrderDto, r: PoReceiptDto) {
    if (!guard.start()) return
    busy = true
    scope.launch {
      when (val p = postReceipt(host, o, r)) {
        is PurchaseOrderBook.Posting.Ok -> toasts.show(StrPurchasePages.billPosted(p.invoiceNo), V3Tone.SUCCESS)
        PurchaseOrderBook.Posting.Already -> toasts.show(StrPurchasePages.alreadyPosted, V3Tone.INFO)
        is PurchaseOrderBook.Posting.Failed -> toasts.show(p.message, V3Tone.ERROR)
        null -> toasts.show(StrPurchasePages.ledgerNotWritten, V3Tone.ERROR)
      }
      busy = false
      guard.done()
    }
  }

  PoPage {
    val gate = st.gate { true }
    val o = st.value
    if (gate != PoForms.Gate.OPEN || o == null) {
      PoGateView(gate, st.failure ?: st.cfgFailure, StrPurchaseOrders.manageOnly) { tick++ }
      return@PoPage
    }
    val s = PurchaseOrderBook.status(o)
    val can = PoForms.actions(o, perms)
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        StrSupplierBills.orderTitle(o.no, o.supplierName), style = V3Type.title, color = c.ink,
        modifier = Modifier.weight(1f).semantics { heading() },
      )
      V3StatusPill(pill(s), PurchaseOrderBook.label(s))
    }
    val meta = listOfNotNull(
      o.expectedDate?.let { StrPurchasePages.arrivesOn(formatDate(it)) },
      o.note.takeIf { it.isNotBlank() },
      o.cancelReason?.takeIf { s == PurchaseOrderCore.Status.CANCELLED }?.let { StrPurchasePages.cancelReasonIs(it) },
    )
    if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp))
    Text(
      StrPurchasePages.poTotals(poAfn(o.totals.orderedMinor), poAfn(o.totals.acceptedMinor), poAfn(o.totals.remainingMinor)),
      style = V3Type.callout, color = c.ink, modifier = Modifier.padding(top = 8.dp),
    )

    //  کارها: فقط آن‌چه نقش × وضعیت اجازه می‌دهد (`PoForms.actions`)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (can.receive) V3Button(
        StrPurchasePages.receiveGoods,
        { nav.openForResult(MoneyPaths.receivePurchaseOrder(o.id)) { tick++ } },
        Modifier.fillMaxWidth(), icon = Icons.Outlined.MoveToInbox, enabled = !busy,
      )
      if (can.send) {
        V3Button(
          StrPurchasePages.sendToSupplier, { act({ repo.send(o.id) }, StrPurchasePages.poSent) },
          Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Outlined.Send, loading = busy, haptic = true,
        )
        Text(StrPurchaseOrders.sendNote, style = V3Type.footnote, color = c.inkMuted)
      }
      if (can.edit) V3Button(
        StrMoney.edit, { nav.openForResult(MoneyPaths.editPurchaseOrder(o.id)) { tick++ } },
        Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Edit, enabled = !busy,
      )
      if (o.receipts.isNotEmpty() || PurchaseOrderBook.hasSupplierBills(o)) V3Button(
        StrPurchaseOrders.match, { nav.open(MoneyPaths.matchPurchaseOrder(o.id)) },
        Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.AutoMirrored.Outlined.FactCheck,
      )
      //  PUR-14: فاکتورِ کاغذیِ تأمین‌کننده و «باطل» همان صفحه‌های گوشی (`SupplierBillsPages.kt`)
      if (can.bill) V3Button(
        StrSupplierBills.title, { nav.openForResult(MoneyPaths.supplierBill(o.id)) { tick++ } },
        Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.AutoMirrored.Outlined.ReceiptLong,
      )
      if (can.cancel) V3Button(
        StrPurchasePages.cancelIt, { cancelAsk = true },
        Modifier.fillMaxWidth(), kind = V3ButtonKind.DANGER, icon = Icons.Outlined.Block, enabled = !busy,
      )
    }

    //  قلم‌ها: سفارش، پذیرفته، خراب، باقی‌مانده و فی (PUR-04)
    val names = remember(data) { data.products.associate { it.id to it.name } }
    val prog = remember(o) { o.progress.associateBy { it.lineId } }
    V3Section(StrPurchaseOrders.rowsTitle) {
      o.lines.forEachIndexed { i, l ->
        if (i > 0) V3Divider()
        val p = prog[l.id]
        V3ListRow(
          l.name.ifBlank { names[l.productId] ?: l.productId },
          subtitle = StrPurchaseOrders.lineSub(
            poUnits(l.qtyMilli), l.unit, poUnits(p?.acceptedMilli ?: 0L), poUnits(p?.damagedMilli ?: 0L), poUnits(p?.remainingMilli ?: l.qtyMilli), poAfn(l.priceMinor),
          ),
          subtitleLines = 3,
          chevron = false,
        )
      }
    }

    //  رسیدها و ثبت در دفترِ همین دستگاه
    if (o.receipts.isNotEmpty()) {
      V3Section(Str.receipts) {
        o.receipts.forEachIndexed { i, r ->
          if (i > 0) V3Divider()
          val posted = PurchaseOrderBook.isPosted(data, r)
          val dmg = r.posted.filter { it.damagedMilli > 0 }
          V3ListRow(
            StrPurchaseOrders.receiptLine(formatMillis(r.createdAt), poAfn(r.totalMinor)),
            subtitle = if (dmg.isEmpty()) null else StrPurchasePages.damagedReturned + dmg.joinToString("، ") { pd ->
              "${o.lines.find { it.id == pd.lineId }?.name.orEmpty()} ${poUnits(pd.damagedMilli)}"
            },
            chevron = false,
            trailing = {
              if (posted) Text(StrPurchasePages.inLedger, style = V3Type.footnote, color = PhoneV3Roles.tone(c, V3Tone.SUCCESS).fg)
              else V3TextButton(StrPurchasePages.postToLedger, { post(o, r) })
            },
          )
          //  PUR-13: برچسبِ اقلامِ پذیرفتهٔ همین رسید — همان شمارش و چاپِ کامپیوتر (`printReceiptLabels`)
          if (r.posted.any { it.acceptedMilli > 0 }) {
            PoReceiptLabels(host, o, r, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
          }
        }
      }
    }
  }

  val o = st.value
  if (cancelAsk && o != null) {
    PoCancelSheet(o, onDismiss = { cancelAsk = false }) { why ->
      cancelAsk = false
      act({ repo.cancel(o.id, why) }, StrPurchasePages.poCancelled)
    }
  }
}

/** نتیجهٔ ثبتِ رسید در دفترِ همین دستگاه; `null` ⇒ دفتر نوشته نشد */
internal suspend fun RouteScope.postReceipt(host: PurchaseOrderHost, o: PurchaseOrderDto, r: PoReceiptDto): PurchaseOrderBook.Posting? {
  var out: PurchaseOrderBook.Posting = PurchaseOrderBook.Posting.Already
  //  رسید ⇐ فاکتورِ خریدِ دفتر با شناسه‌های قطعی (`PurchaseOrderBook.post`؛ تکرار بی اثر) — همان کامپیوتر
  val wrote = store.update { cur ->
    out = PurchaseOrderBook.post(cur, o, r, todayIso(), host.now())
    (out as? PurchaseOrderBook.Posting.Ok)?.data
  }
  return if (wrote == false) null else out
}

/** لغو با تأیید؛ فرستاده/بخشی دلیلِ لازم دارد (همان `PurchaseOrderCore.cancel`)، پیش‌نویس نه */
@Composable
private fun PoCancelSheet(o: PurchaseOrderDto, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  val c = PhoneV3.colors
  val needWhy = PoForms.cancelNeedsReason(o)
  var why by remember(o.id) { mutableStateOf("") }
  var tried by remember(o.id) { mutableStateOf(false) }
  V3Sheet(onDismiss = onDismiss, title = StrPurchasePages.cancelPoAsk(o.no), detents = false) { closer ->
    Text(if (needWhy) StrPurchasePages.cancelOpenNote else StrPurchasePages.cancelDraftNote, style = V3Type.callout, color = c.inkMuted)
    Spacer(Modifier.height(12.dp))
    V3TextField(
      why, { why = it.take(300) }, if (needWhy) StrLoyalty.adjustReason else StrSafe.reason,
      required = needWhy, error = if (tried && needWhy && why.isBlank()) StrPurchaseOrders.reasonNeeded else null,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrKit.cancel, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      V3Button(
        StrPurchasePages.cancelIt,
        {
          tried = true
          if (!needWhy || why.isNotBlank()) closer.close { onConfirm(why.trim()) }
        },
        Modifier.weight(1f), kind = V3ButtonKind.DANGER, haptic = true,
      )
    }
  }
}

/* ══════════════════════════ ساختن و ویرایش ══════════════════════════ */

/** `purchasing/orders/new?supplier=` — نتیجه شناسهٔ سفارشِ ساخته‌شده */
@Composable
internal fun RouteScope.PurchaseOrderNewRoute() {
  val repo = rememberPurchaseOrderHost().repo
  var tick by remember { mutableStateOf(0) }
  val st = rememberPoState(repo, Unit, tick, { it.manage }) { ApiResult.Success(Unit) }
  PoPage {
    val gate = st.gate { it.manage }
    if (gate != PoForms.Gate.OPEN) { PoGateView(gate, st.failure ?: st.cfgFailure, StrPurchaseOrders.manageOnly) { tick++ }; return@PoPage }
    PoEditorForm(repo, null, args.query("supplier"))
  }
}

/** `purchasing/orders/{id}/edit` — فقط پیش‌نویس؛ نتیجه شناسهٔ همان سفارش */
@Composable
internal fun RouteScope.PurchaseOrderEditRoute(id: String) {
  val repo = rememberPurchaseOrderHost().repo
  var tick by remember { mutableStateOf(0) }
  val st = rememberPoState(repo, id, tick, { it.manage }) { it.get(id) }
  PoPage {
    val gate = st.gate { it.manage }
    val o = st.value
    if (gate != PoForms.Gate.OPEN || o == null) { PoGateView(gate, st.failure ?: st.cfgFailure, StrPurchaseOrders.manageOnly) { tick++ }; return@PoPage }
    if (!PoForms.actions(o, st.cfg?.permissions ?: PoPermsDto()).edit) { V3EmptyState(Icons.Outlined.Lock, StrPurchaseOrders.notDraft); return@PoPage }
    PoEditorForm(repo, o, null)
  }
}

private enum class EditorSheet { SUPPLIER, PRODUCT }

@Composable
private fun RouteScope.PoEditorForm(repo: PurchaseOrderRepository, base: PurchaseOrderDto?, supplierArg: String?) {
  val c = PhoneV3.colors
  val d = data
  val scope = rememberCoroutineScope()
  //  کلیدِ یکتای همین فرم (`Idempotency-Key`): دو بار زدن یا قطعِ نت همان سفارش است، نه دومی
  val formKey = remember { moneyId() }
  val guard = remember { PoForms.SubmitGuard() }
  //  PRD-07: واحدِ خریدِ ردیف فقط با `ShopPolicy.productTools` (پیش‌فرض خاموش ⇒ همان فرمِ پیشین)
  val unitsOn = purchaseUnitsOn()
  var supplierId by remember {
    mutableStateOf(base?.supplierId ?: supplierArg?.takeIf { s -> d.suppliers.any { it.id == s } } ?: d.suppliers.singleOrNull()?.id.orEmpty())
  }
  var rows by remember { mutableStateOf(PoForms.rowsOf(base, d)) }
  var expected by remember { mutableStateOf(base?.expectedDate.orEmpty()) }
  var note by remember { mutableStateOf(base?.note.orEmpty()) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var sheet by remember { mutableStateOf<EditorSheet?>(null) }
  //  PUR-09/10: آخرین فی از دفتر؛ سفارش‌های فرستاده برای زمانِ تأمین و «در راه» (همهٔ بازها، `planningOrders`)
  val buys = remember(d) { PurchaseOrderBook.buys(d) }
  var sentOrders by remember { mutableStateOf<List<PurchaseOrderDto>>(emptyList()) }
  LaunchedEffect(Unit) { sentOrders = repo.planningOrders() }
  val lead = remember(d, sentOrders, supplierId) { PurchaseOrderBook.leadTime(sentOrders, supplierId, PurchaseOrderBook.manualLead(d, supplierId)) }
  val supplier = d.suppliers.find { it.id == supplierId }

  fun addPicks(picks: List<PurchaseOrderBook.Pick>, none: String) {
    val next = PoForms.addPicks(rows, picks)
    if (next.size == rows.size) toasts.show(none, V3Tone.INFO) else { rows = next; error = null }
  }

  fun save() {
    if (!guard.start()) return
    val draft = when (val chk = PoForms.draft(d, supplierId, rows, expected, note)) {
      is PoForms.DraftCheck.Bad -> { error = chk.message; guard.done(); return }
      is PoForms.DraftCheck.Ok -> chk.draft
    }
    busy = true
    error = null
    scope.launch {
      val r = if (base == null) repo.create(formKey, draft) else repo.update(base.id, base.version, draft)
      val got = r.valueOrNull()
      if (got == null) {
        busy = false
        guard.done()
        error = r.errorMessage() ?: StrSupplierBills.failedShort
        return@launch
      }
      //  پس از موفقیت سد باز نمی‌شود: لمسِ دیگری پیش از بسته شدنِ صفحه چیزی نمی‌فرستد
      toasts.show(if (base == null) StrPurchasePages.draftCreated(got.no) else StrOnline.saved, V3Tone.SUCCESS)
      nav.finish(got.id)
    }
  }

  Text(if (base == null) StrPurchasePages.newPoTitle else StrPurchasePages.editPo(base.no), style = V3Type.title, color = c.ink, modifier = Modifier.semantics { heading() })
  if (d.suppliers.isEmpty()) {
    V3Message(V3Tone.INFO, StrPurchasePages.makeSupplierFirst, modifier = Modifier.padding(top = 12.dp))
  }
  V3Section(null) {
    V3PickerRow(
      label = StrMoney.supplierLabel, value = supplier?.name, placeholder = StrMoney.pickSupplier,
      icon = Icons.Outlined.LocalShipping, onClick = { sheet = EditorSheet.SUPPLIER },
    )
  }
  Text(
    lead.days?.let { if (lead.manual) StrPurchasePages.leadManual(it) else StrPurchasePages.leadMeasured(it, lead.samples) }
      ?: StrPurchasePages.leadUnknown(ReorderCore.DEFAULT_LEAD_DAYS),
    style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 6.dp),
  )
  //  PUR-03: ردیف از پیشنهادها — همان سه دکمهٔ کامپیوتر؛ هیچ‌کدام خودکار ثبت نمی‌کند
  val picked = supplierId.isNotBlank()
  Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    V3Button(
      StrPurchasePages.suggestBySales,
      { addPicks(PurchaseOrderBook.planned(d, supplierId, sentOrders, todayIso()).map { it.pick }, StrPurchasePages.nothingToOrder) },
      Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, enabled = picked,
    )
    V3Button(
      StrPurchasePages.lowFromSupplier,
      { addPicks(PurchaseOrderBook.supplierPicks(d, supplierId, onlyLow = true), StrPurchasePages.noLowFromSupplier) },
      Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, enabled = picked,
    )
    V3Button(
      StrPurchasePages.allFromSupplier,
      { addPicks(PurchaseOrderBook.supplierPicks(d, supplierId, onlyLow = false), StrPurchasePages.noNewFromSupplier) },
      Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, enabled = picked,
    )
  }

  V3Section(StrPurchaseOrders.rowsTitle) {
    if (rows.isEmpty()) {
      Text(StrPurchaseOrders.noRows, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(16.dp))
    }
    rows.forEachIndexed { i, r ->
      if (i > 0) V3Divider()
      Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("${i + 1}. ${r.name}", style = V3Type.body, color = c.ink, modifier = Modifier.weight(1f))
          V3IconButton(Icons.Outlined.DeleteOutline, StrPurchaseOrders.removeRow(r.name), size = 48.dp) {
            rows = rows.filterNot { it.key == r.key }
          }
        }
        Text(
          PurchaseOrderBook.lastPriceHint(buys, r.productId, supplierId) ?: PurchaseOrderBook.NO_BUY_YET,
          style = V3Type.footnote, color = c.inkMuted,
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          V3TextField(
            r.qty, { v -> rows = rows.map { if (it.key == r.key) it.copy(qty = v) else it }; error = null },
            StrPurchasePages.qtyIn(PoForms.unitOf(r)), Modifier.weight(1f), kind = V3FieldKind.NUMBER,
          )
          V3TextField(
            r.price, { v -> rows = rows.map { if (it.key == r.key) it.copy(price = v) else it }; error = null },
            r.unitName?.let(StrPurchaseUnits::priceIn) ?: StrReturnPages.unitPriceAfn, Modifier.weight(1f), kind = V3FieldKind.MONEY,
          )
        }
        //  PRD-07: واحدِ خریدِ ردیف (`PoForms.pickUnit`: فی به نسبتِ ضریب) و مقدار/بهای واحدِ پایه از هسته
        val units = if (unitsOn) PoForms.purchaseUnits(d, r.productId) else emptyList()
        if (units.isNotEmpty()) {
          PoUnitChips(units, d.products.find { it.id == r.productId }?.unit.orEmpty(), r.unitName) { u ->
            rows = rows.map { if (it.key == r.key) PoForms.pickUnit(d, it, u) else it }
            error = null
          }
          PoForms.unitLine(d, r)?.let { Text(it, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp)) }
        }
      }
    }
  }
  V3Button(
    StrPurchaseOrders.addItem, { sheet = EditorSheet.PRODUCT },
    Modifier.fillMaxWidth().padding(top = 10.dp), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Add,
  )
  Spacer(Modifier.height(12.dp))
  V3TextField(expected, { expected = it.take(10); error = null }, StrPurchasePages.arrivalOptional, kind = V3FieldKind.DATE, placeholder = StrPurchaseOrders.arrivalHint)
  Spacer(Modifier.height(8.dp))
  V3TextField(note, { note = it.take(500) }, StrDeliver.noteField)
  Text(
    StrPurchasePages.poTotal(money(PoForms.total(rows)), rows.size),
    style = V3Type.callout, color = c.ink, modifier = Modifier.padding(top = 10.dp),
  )
  error?.let { V3Message(V3Tone.ERROR, it, modifier = Modifier.padding(top = 8.dp)) }
  V3Button(
    if (base == null) StrPurchasePages.saveDraft else StrKit.save, { save() },
    Modifier.fillMaxWidth().padding(top = 12.dp), enabled = rows.isNotEmpty() && picked, loading = busy, haptic = true,
  )

  when (sheet) {
    EditorSheet.SUPPLIER -> IdPickerSheet(
      title = StrMoney.supplierLabel,
      options = d.suppliers.sortedBy { it.name }.map { PickOption(it.id, it.name, it.phone.ifBlank { null }) },
      selected = supplierId,
      onPick = { id -> if (id != null) supplierId = id },
      onDismiss = { sheet = null },
    )
    EditorSheet.PRODUCT -> V3Sheet(onDismiss = { sheet = null }, title = StrPurchaseOrders.addItem, scroll = false) { closer ->
      ProductPickerList(d, null, onPick = { p ->
        closer.close {
          rows = PoForms.addProduct(d, rows, supplierId, p, unitsOn) ?: run { toasts.show(StrPurchasePages.alreadyInPo(p.name), V3Tone.INFO); rows }
        }
      }, onNew = null)
    }
    null -> Unit
  }
}

/** PRD-07: «واحدِ خرید» ِ یک ردیف — واحدِ پایه یا یکی از واحدهای خریدِ کالا ([unit] نامِ برگزیده؛ null = پایه) */
@Composable
internal fun PoUnitChips(units: List<PurchaseUnits.Item>, baseUnit: String, unit: String?, onPick: (String?) -> Unit) {
  val c = PhoneV3.colors
  val chosen = PurchaseUnits.find(units, unit)
  Text(StrPurchaseUnits.unit, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 8.dp))
  V3ChipRow(contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(top = 4.dp)) {
    V3Chip(StrPurchaseUnits.baseOnly(baseUnit), selected = chosen == null, onClick = { onPick(null) })
    units.forEach { u -> V3Chip(PurchaseUnits.label(u, baseUnit), selected = chosen == u, onClick = { onPick(u.name) }) }
  }
}

/* ══════════════════════════ پیشنهادِ سفارش ══════════════════════════ */

/** `purchasing/orders/suggest` — همان `PoSuggest` ِ کامپیوتر (INV-13/14): «ساختِ پیش‌نویس» فقط برای مدیر، هرگز فرستادن */
@Composable
internal fun RouteScope.PurchaseOrderSuggestRoute() {
  val repo = rememberPurchaseOrderHost().repo
  var tick by remember { mutableStateOf(0) }
  val st = rememberPoState(repo, Unit, tick, { true }) { ApiResult.Success(Unit) }
  Column(Modifier.fillMaxSize().padding(16.dp)) {
    val gate = st.gate { true }
    val cfg = st.cfg
    if (gate != PoForms.Gate.OPEN || cfg == null) { PoGateView(gate, st.failure ?: st.cfgFailure, StrPurchaseOrders.manageOnly) { tick++ }; return@Column }
    PoSuggest(data, repo, cfg.permissions, toast = { toasts.show(it) }, onCreated = { nav.open(MoneyPaths.purchaseOrder(it.id)) })
  }
}
