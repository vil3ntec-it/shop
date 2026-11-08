package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrDeliver
import ir.vil3ntec.tohid.core.i18n.StrLayaway
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrReturnPages
import ir.vil3ntec.tohid.core.i18n.StrSupplierBills
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.scan.ScanRouter
import ir.vil3ntec.tohid.ui.platform.PurchaseOrderHost
import ir.vil3ntec.tohid.ui.platform.rememberPurchaseOrderHost
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import kotlinx.coroutines.launch

/*
 *  PUR-04 و PUR-14 روی گوشیِ نسخهٔ ۳ — دریافت با اسکن و تطبیقِ سه‌طرفهٔ «سفارش‌های خرید». درِ صفحه، بارگذاری و ثبتِ رسید در
 *  دفتر همان `PurchaseOrderPages.kt` است؛ منطق در `data/PoForms` (مشترکِ کامپیوتر) و `core/purchase`.
 */

/* ══════════════════════════ دریافت (PUR-04) ══════════════════════════ */

/** `purchasing/orders/{id}/receive` — نتیجه شناسهٔ سفارش تا جزئیات دوباره بخواند */
@Composable
internal fun RouteScope.PurchaseOrderReceiveRoute(id: String) {
  val host = rememberPurchaseOrderHost()
  var tick by remember { mutableStateOf(0) }
  val st = rememberPoState(host.repo, id, tick, { it.receive }) { it.get(id) }
  PoPage {
    val gate = st.gate { it.receive }
    val o = st.value
    if (gate != PoForms.Gate.OPEN || o == null) { PoGateView(gate, st.failure ?: st.cfgFailure, StrPurchaseOrders.receiveDenied) { tick++ }; return@PoPage }
    val perms = st.cfg?.permissions ?: PoPermsDto()
    if (!PoForms.actions(o, perms).receive) { V3EmptyState(Icons.Outlined.Lock, StrPurchaseOrders.notReceivable); return@PoPage }
    PoReceiveForm(host, o, perms)
  }
}

@Composable
private fun RouteScope.PoReceiveForm(host: PurchaseOrderHost, o: PurchaseOrderDto, perms: PoPermsDto) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  //  شناسهٔ یکتای همین رسید: تلاشِ دوباره همان رسید است (سرور «تکراری» می‌گوید، دو بار نمی‌خورد)
  val receiptId = remember(o.id) { moneyId() }
  val guard = remember { PoForms.SubmitGuard() }
  var rows by remember(o.id) { mutableStateOf(PoForms.recvRows(o)) }
  var allowOver by remember { mutableStateOf(false) }
  var freight by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var scanText by remember { mutableStateOf("") }
  var message by remember { mutableStateOf<Pair<V3Tone, String>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val index = remember(data) { ShopStore.barcodeIndex(data) }

  fun scan(raw: String) {
    if (raw.isBlank()) return
    message = when (val s = PoForms.scan(o, rows, index, raw)) {
      is PoForms.Scan.NotInPo -> V3Tone.ERROR to StrPurchasePages.notInPo(s.code)
      is PoForms.Scan.Added -> { rows = s.rows; error = null; V3Tone.SUCCESS to StrPurchaseOrders.scanned(s.name, s.received) }
    }
  }
  //  بارکدخوانِ وصل یا گوشیِ جفت‌شده تا این صفحه باز است همین‌جا (نه صندوق) — همان راهِ «انتقالِ موجودی»
  val owner = remember { Any() }
  val latest by rememberUpdatedState<(String) -> Unit> { code -> scan(code) }
  DisposableEffect(Unit) {
    ScanRouter.register(owner) { code -> latest(code); true }
    onDispose { ScanRouter.unregister(owner) }
  }

  fun submit() {
    if (!guard.start()) return
    val chk = PoForms.receipt(o, rows, receiptId, allowOver && perms.manage, if (perms.manage) freight else "")
    if (chk is PoForms.ReceiptCheck.Bad) { error = chk.message; guard.done(); return }
    chk as PoForms.ReceiptCheck.Ok
    busy = true
    error = null
    scope.launch {
      val res = host.repo.receive(o.id, receiptId, chk.lines, allowOver && perms.manage, note.trim(), chk.freightMinor)
      val got = res.valueOrNull()
      if (got == null) {
        busy = false
        guard.done()
        error = res.errorMessage() ?: StrSupplierBills.failedShort
        return@launch
      }
      //  رسید در سرور ثبت شد ⇒ فاکتورِ خریدِ دفتر (شناسه‌های قطعی؛ تکرار بی اثر)
      when (val p = postReceipt(host, got.order, got.receipt)) {
        is PurchaseOrderBook.Posting.Ok -> toasts.show(StrPurchasePages.receiptPosted(p.invoiceNo), V3Tone.SUCCESS)
        PurchaseOrderBook.Posting.Already -> toasts.show(StrPurchasePages.receiptSaved, V3Tone.SUCCESS)
        is PurchaseOrderBook.Posting.Failed -> toasts.show(StrPurchasePages.receiptNoLedger(p.message), V3Tone.WARNING)
        null -> toasts.show(StrPurchasePages.receiptLedgerFailed, V3Tone.ERROR)
      }
      nav.finish(o.id)
    }
  }

  Text(StrPurchasePages.receiveTitle(o.no, o.supplierName), style = V3Type.title, color = c.ink, modifier = Modifier.semantics { heading() })
  Text(StrPurchasePages.receiveNote, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp))
  Spacer(Modifier.height(12.dp))
  V3SearchField(
    scanText, { scanText = it.take(64) }, StrPurchaseOrders.scanHint,
    onScan = { nav.openForResult(SalesPaths.scan("purchase")) { code -> code?.let(::scan) } },
    onSubmit = { scan(scanText); scanText = "" },
  )
  message?.let { (tone, text) -> V3Message(tone, text, modifier = Modifier.padding(top = 8.dp)) }

  val prog = remember(o) { o.progress.associateBy { it.lineId } }
  o.lines.forEach { l ->
    val r = rows[l.id] ?: PoForms.RecvRow()
    val left = prog[l.id]?.remainingMilli ?: l.qtyMilli
    V3Card(Modifier.padding(top = 10.dp), padding = PaddingValues(12.dp)) {
      Text(StrPurchasePages.remainingOf(l.name, poUnits(left), l.unit), style = V3Type.body, color = c.ink)
      Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V3TextField(
          r.received, { v -> rows = rows + (l.id to r.copy(received = v)); error = null },
          StrPurchasePages.received, Modifier.weight(1f), kind = V3FieldKind.NUMBER,
        )
        V3TextField(
          r.damaged, { v -> rows = rows + (l.id to r.copy(damaged = v)); error = null },
          StrPurchasePages.damaged, Modifier.weight(1f), kind = V3FieldKind.NUMBER,
        )
      }
      //  شاگرد فی را عوض نمی‌کند (سرور هم نمی‌پذیرد) — کادرِ فی فقط برای مدیر
      if (perms.manage) V3TextField(
        r.price, { v -> rows = rows + (l.id to r.copy(price = v)); error = null },
        StrReturnPages.unitPriceAfn, Modifier.fillMaxWidth().padding(top = 6.dp), kind = V3FieldKind.MONEY,
      )
      PoForms.after(o, l.id, r)?.let { a ->
        val (text, tone) = when {
          a.overMilli > 0 -> StrPurchaseOrders.overAfter(poUnits(a.overMilli), l.unit) to V3Tone.WARNING
          a.done -> StrPurchaseOrders.doneAfter to V3Tone.SUCCESS
          else -> StrPurchaseOrders.leftAfter(poUnits(a.leftMilli), l.unit) to V3Tone.INFO
        }
        Text(text, style = V3Type.footnote, color = PhoneV3Roles.tone(c, tone).fg, modifier = Modifier.padding(top = 6.dp))
      }
    }
  }
  if (perms.manage) {
    V3Section(null) {
      V3ToggleRow(StrPurchasePages.acceptOver, allowOver, { allowOver = it; error = null }, icon = Icons.Outlined.WarningAmber)
    }
    Spacer(Modifier.height(8.dp))
    V3TextField(freight, { freight = it; error = null }, StrPurchasePages.freightField, kind = V3FieldKind.MONEY, helper = StrPurchasePages.freightNote)
  }
  Spacer(Modifier.height(8.dp))
  V3TextField(note, { note = it.take(500) }, StrDeliver.noteField)
  error?.let { V3Message(V3Tone.ERROR, it, modifier = Modifier.padding(top = 8.dp)) }
  V3Button(
    StrLayaway.payConfirm, { submit() },
    Modifier.fillMaxWidth().padding(top = 12.dp), icon = Icons.Outlined.MoveToInbox, loading = busy, haptic = true,
  )
}

/* ══════════════════════════ تطبیق (PUR-14) ══════════════════════════ */

/** `purchasing/orders/{id}/match` — سفارش ⇔ رسید ⇔ فاکتور؛ ردیفِ اختلاف‌دار بالا و برجسته */
@Composable
internal fun RouteScope.PurchaseOrderMatchRoute(id: String) {
  val repo = rememberPurchaseOrderHost().repo
  var tick by remember { mutableStateOf(0) }
  val st = rememberPoState(repo, id, tick, { true }) { it.get(id) }
  val c = PhoneV3.colors
  PoPage {
    val gate = st.gate { true }
    val o = st.value
    if (gate != PoForms.Gate.OPEN || o == null) { PoGateView(gate, st.failure ?: st.cfgFailure, StrPurchaseOrders.manageOnly) { tick++ }; return@PoPage }
    val m = remember(data, o) { PoForms.matchView(data, o) }
    Text(StrSupplierBills.orderTitle(o.no, o.supplierName), style = V3Type.title, color = c.ink, modifier = Modifier.semantics { heading() })
    Text(if (m.supplier) StrPurchaseOrders.sideSupplier else StrPurchaseOrders.sideLedger, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp))
    if (!m.hasData) { V3EmptyState(Icons.AutoMirrored.Outlined.FactCheck, StrPurchaseOrders.matchNothing); return@PoPage }
    Text(
      StrPurchaseOrders.matchTotals(poAfn(m.orderedMinor), poAfn(m.acceptedMinor), poAfn(m.billedMinor)),
      style = V3Type.callout, color = c.ink, modifier = Modifier.padding(top = 8.dp),
    )
    if (m.matched) V3Message(V3Tone.SUCCESS, PurchaseOrderBook.matchTitle(m.supplier), modifier = Modifier.padding(top = 10.dp))
    else V3Message(V3Tone.WARNING, StrPurchaseOrders.differences(m.flaggedCount), modifier = Modifier.padding(top = 10.dp))
    V3Section(StrPurchaseOrders.rowsTitle) {
      m.rows.forEachIndexed { i, r ->
        if (i > 0) V3Divider()
        val warn = PhoneV3Roles.tone(c, V3Tone.WARNING)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, style = V3Type.body, color = c.ink, modifier = Modifier.weight(1f))
            if (r.flagged) V3StatusPill(V3Status.WARNING, StrPurchaseOrders.flagged)
          }
          Text(
            StrPurchaseOrders.matchQty(poUnits(r.orderedMilli), poUnits(r.acceptedMilli), poUnits(r.billedMilli), r.unit),
            style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 2.dp),
          )
          Text(StrPurchaseOrders.matchMoney(poAfn(r.expectedMinor), poAfn(r.billedMinor)), style = V3Type.footnote, color = c.inkMuted)
          r.issues.forEach { t -> Text(t, style = V3Type.callout, color = warn.fg, modifier = Modifier.padding(top = 2.dp)) }
        }
      }
    }
  }
}
