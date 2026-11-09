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
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Print
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
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrLayaway
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrReturnPages
import ir.vil3ntec.tohid.core.i18n.StrSupplierBills
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PoReceiptDto
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.scan.ScanRouter
import ir.vil3ntec.tohid.ui.platform.PurchaseOrderHost
import ir.vil3ntec.tohid.ui.platform.printReceiptLabels
import ir.vil3ntec.tohid.ui.platform.rememberPurchaseOrderHost
import ir.vil3ntec.tohid.ui.screens.purchaseUnitsOn
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
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
 *  PUR-13: پس از ثبتِ رسید (و کنارِ هر رسید در جزئیات) «برچسب» و «A4» ِ اقلامِ پذیرفته — همان شمارش (`PoForms.labels`) و همان
 *  راهِ چاپِ کامپیوتر (`PurchaseOrderHost.printReceiptLabels` ⇐ `printLabel`). PRD-07: اسکنِ بارکدِ واحدِ خرید و «به موجودی».
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
  //  PUR-13: رسیدِ تازه با کالای پذیرفته ⇒ همین صفحه «برچسب» را می‌دهد تا «بازگشت»
  var saved by remember(o.id) { mutableStateOf<Pair<PurchaseOrderDto, PoReceiptDto>?>(null) }
  val index = remember(data) { ShopStore.barcodeIndex(data) }
  //  PRD-07: بارکدِ واحدِ خرید (کارتنِ ۲۴) همان واحد را می‌شمارد — فقط با `ShopPolicy.productTools`
  val unitsOn = purchaseUnitsOn()
  val unitIndex = remember(data, unitsOn) { if (unitsOn) ShopStore.purchaseUnitIndex(data) else null }

  fun scan(raw: String) {
    if (raw.isBlank()) return
    message = when (val s = PoForms.scan(o, rows, index, raw, unitIndex)) {
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
      //  سد باز نمی‌شود: همین رسید دوباره فرستاده نمی‌شود
      if (got.receipt.posted.any { it.acceptedMilli > 0 }) { busy = false; saved = got.order to got.receipt } else nav.finish(o.id)
    }
  }

  val done = saved
  if (done != null) {
    val (order, receipt) = done
    Text(StrPurchasePages.receiveTitle(o.no, o.supplierName), style = V3Type.title, color = c.ink, modifier = Modifier.semantics { heading() })
    V3Message(V3Tone.SUCCESS, StrPurchasePages.receiptSaved, modifier = Modifier.padding(top = 12.dp))
    Text(StrPurchaseOrders.labelsNote, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 8.dp))
    PoReceiptLabels(host, order, receipt, Modifier.padding(top = 12.dp))
    V3Button(StrKit.back, { nav.finish(o.id) }, Modifier.fillMaxWidth().padding(top = 12.dp), kind = V3ButtonKind.SECONDARY)
    return
  }

  Text(StrPurchasePages.receiveTitle(o.no, o.supplierName), style = V3Type.title, color = c.ink, modifier = Modifier.semantics { heading() })
  Text(StrPurchasePages.receiveNote, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp))
  Spacer(Modifier.height(12.dp))
  V3SearchField(
    scanText, { scanText = it.take(64) }, StrPurchaseOrders.scanHint,
    onScan = { nav.openForResult(SalesPaths.scan("purchase")) { code -> code?.let(::scan) } },
    onSubmit = { scan(scanText); scanText = "" },
  )
  PoReceiveAll(o, rows, enabled = !busy) { filled -> rows = filled; error = null; message = V3Tone.INFO to StrPurchaseOrders.filledAll }
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
      //  PRD-07: ردیفِ به واحدِ خرید ⇒ آنچه به موجودی می‌رود به واحدِ پایه (مقدار × ضریب)
      PoForms.toStock(data, l, r)?.let { Text(it, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 2.dp)) }
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

/**
 *  PUR-04 «دریافتِ همهٔ باقی‌مانده»: دریافتِ کامل با یک لمس، بی تایپ یا اسکنِ تک‌تکِ اقلام — «رسیده» ِ هر ردیفِ باز همان
 *  باقی‌مانده (`PoForms.fillRemaining`، دقیق به هزارمِ واحدِ ردیف). فقط کادرها پر می‌شوند؛ ثبت همان دکمهٔ پایینِ صفحه با
 *  سنجشِ `PoForms.receipt` و سرور. سفارشی که ردیفِ باز ندارد دکمه ندارد.
 */
@Composable
internal fun PoReceiveAll(o: PurchaseOrderDto, rows: Map<String, PoForms.RecvRow>, enabled: Boolean, onFilled: (Map<String, PoForms.RecvRow>) -> Unit) {
  if (!PoForms.hasRemaining(o)) return
  V3Button(
    StrPurchaseOrders.receiveAll, { onFilled(PoForms.fillRemaining(o, rows)) },
    Modifier.fillMaxWidth().padding(top = 8.dp), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.DoneAll, enabled = enabled,
  )
}

/* ══════════════════════════ برچسب (PUR-13) ══════════════════════════ */

/**
 *  «برچسب» (چاپگرِ برچسب، هر واحد یکی تا ۹۹) و «A4» (برگهٔ ۳×۸ ِ سیستم، تا ۲۴) برای اقلامِ پذیرفتهٔ رسیدِ [r] — همان شمارش و
 *  راهِ چاپِ پنجرهٔ کامپیوتر (`printReceiptLabels`)؛ پیام‌ها با `toasts`. تا چاپ تمام نشده دکمه‌ها دوباره نمی‌فرستند.
 */
@Composable
internal fun RouteScope.PoReceiptLabels(host: PurchaseOrderHost, o: PurchaseOrderDto, r: PoReceiptDto, modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  var printing by remember(r.id) { mutableStateOf(false) }

  fun print(a4: Boolean) {
    if (printing) return
    printing = true
    scope.launch {
      try { host.printReceiptLabels(data, o, r, a4) { toasts.show(it) } } finally { printing = false }
    }
  }

  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    V3Button(
      StrPurchasePages.labelBtn, { print(a4 = false) }, Modifier.weight(1f),
      kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Print, loading = printing,
    )
    V3Button("A4", { print(a4 = true) }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, enabled = !printing)
  }
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
