package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeliver
import ir.vil3ntec.tohid.core.i18n.StrGrid
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrLayaway
import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrReturnPages
import ir.vil3ntec.tohid.core.i18n.StrSafe
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSupplierBills
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.purchase.ReorderCore
import ir.vil3ntec.tohid.core.purchase.SupplierPricing
import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.core.product.PurchaseUnits
import ir.vil3ntec.tohid.data.PoBillDto
import ir.vil3ntec.tohid.data.PoConfigDto
import ir.vil3ntec.tohid.data.PoLineDto
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PoReceiptDto
import ir.vil3ntec.tohid.data.PoReceiptLineDto
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.PurchaseOrderRepository
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.platform.PurchaseOrderHost
import ir.vil3ntec.tohid.ui.platform.rememberPurchaseOrderHost
import kotlinx.coroutines.launch

/*
 *  سفارشِ خرید از تأمین‌کننده — PUR-02 (وضعیت‌ها)، PUR-03 (ساختن از کم‌موجودها و «همهٔ کالاهای این
 *  تأمین‌کننده»)، PUR-04 (دریافتِ کامل/جزئی با اسکن، خراب و باقی‌ماندهٔ باز)، PUR-14 (تطبیق). §۶.۶، S19.
 *
 *  ⛔ پشتِ پرچمِ سرورِ `shop.purchase_orders` (پیش‌فرض خاموش): تا `GET /config` نگوید `enabled`، هیچ
 *  دکمه‌ای دیده نمی‌شود و صفحهٔ خرید همان امروز است (آفلاین هم همین). سفارش و رسید در سرور‌اند؛
 *  رسیدِ ثبت‌شده با `PurchaseOrderBook.post` همان فاکتورِ خریدِ دفتر را می‌سازد (شناسه‌های قطعی،
 *  پس تکرار هرگز دو خرید نمی‌سازد). گوشی و کامپیوتر همین یک فایل.
 */

/** دکمهٔ «سفارش‌های خرید» — فقط با پرچمِ روشن */
@Composable
fun PurchaseOrdersEntry(store: ShopStore, d: ShopData, toast: (String) -> Unit, modifier: Modifier = Modifier) {
  //  دسترسیِ سکو و ساعت فقط از مرزِ سکو (`ui/platform/PurchaseOrderHost.kt`، §۴۰.۳)
  val host = rememberPurchaseOrderHost()
  val repo = host.repo
  var config by remember { mutableStateOf<PoConfigDto?>(null) }
  var open by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { config = repo.config().valueOrNull() }
  val cfg = config
  if (cfg == null || !cfg.enabled) return
  OutlinedButton(onClick = { open = true }, modifier = modifier) { Text(StrPurchasePages.purchaseOrders) }
  if (open) PurchaseOrdersDialog(store, d, host, cfg.permissions, toast) { open = false }
}

private sealed interface PoMode {
  data object Index : PoMode
  data class Detail(val id: String) : PoMode
  /** `base == null` ⇒ سفارشِ تازه */
  data class Edit(val base: PurchaseOrderDto?) : PoMode
  data class Receive(val order: PurchaseOrderDto) : PoMode
  /** PUR-14: ثبتِ فاکتورِ جدای تأمین‌کننده */
  data class Bill(val order: PurchaseOrderDto) : PoMode
  /** پیشنهادِ سفارش (INV-13/14، `PurchaseSuggestView.kt`) */
  data object Suggest : PoMode
}

@Composable
private fun PurchaseOrdersDialog(
  store: ShopStore,
  d: ShopData,
  host: PurchaseOrderHost,
  perms: PoPermsDto,
  toast: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var mode by remember { mutableStateOf<PoMode>(PoMode.Index) }
  val c = Shop.colors
  val repo = host.repo
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    DialogEntry {
      Surface(
        color = c.surfaceSolid,
        shape = Shape.dialog,
        modifier = Modifier.padding(12.dp).widthIn(max = 920.dp).fillMaxWidth().fillMaxHeight(0.94f),
      ) {
        Column(Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (mode != PoMode.Index) TextButton(onClick = { mode = PoMode.Index }) { Text(StrPurchasePages.list) }
            Text(StrPurchasePages.purchaseOrders, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(Str.close) }
          }
          Spacer(Modifier.height(8.dp))
          when (val m = mode) {
            PoMode.Index -> PoList(repo, perms, onOpen = { mode = PoMode.Detail(it) }, onNew = { mode = PoMode.Edit(null) }, onSuggest = { mode = PoMode.Suggest })
            PoMode.Suggest -> PoSuggest(d, repo, perms, toast, onCreated = { mode = PoMode.Detail(it.id) })
            is PoMode.Detail -> PoDetail(store, d, host, perms, m.id, toast,
              onEdit = { mode = PoMode.Edit(it) }, onReceive = { mode = PoMode.Receive(it) }, onBill = { mode = PoMode.Bill(it) })
            is PoMode.Edit -> PoEditor(d, repo, m.base, toast, onSaved = { mode = PoMode.Detail(it.id) })
            is PoMode.Receive -> PoReceive(store, d, host, perms, m.order, toast, onDone = { mode = PoMode.Detail(m.order.id) })
            is PoMode.Bill -> PoBill(d, repo, m.order, toast, onDone = { mode = PoMode.Detail(m.order.id) })
          }
        }
      }
    }
  }
}

/* ══════════════════════════ فهرست ══════════════════════════ */

@Composable
private fun PoList(repo: PurchaseOrderRepository, perms: PoPermsDto, onOpen: (String) -> Unit, onNew: () -> Unit, onSuggest: () -> Unit) {
  val c = Shop.colors
  var state by remember { mutableStateOf<String?>(null) }
  var orders by remember { mutableStateOf<List<PurchaseOrderDto>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(state) {
    orders = null; error = null
    val r = repo.list(state)
    orders = r.valueOrNull()?.orders
    error = r.errorMessage()
  }
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    listOf(null to Str.all, "draft" to StrPurchasePages.draft, "sent" to StrPurchasePages.sentOpen, "cancelled" to StrPay.cancel).forEach { (k, label) ->
      FilterChip(selected = state == k, onClick = { state = k }, label = { Text(label) }, shape = Shape.chip)
    }
    Spacer(Modifier.width(12.dp))
    if (perms.manage) Button(onClick = onNew) { Text(StrPurchasePages.newPo) }
    OutlinedButton(onClick = onSuggest) { Text(StrPurchasePages.reorderSuggest) }
  }
  Spacer(Modifier.height(10.dp))
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    when {
      error != null -> Text(error!!, color = c.danger, style = MaterialTheme.typography.bodySmall)
      orders == null -> Text(StrReports.loading, color = c.muted, style = MaterialTheme.typography.bodySmall)
      orders!!.isEmpty() -> EmptyNote(StrPurchasePages.noPoYet)
      else -> orders!!.forEach { o ->
        val st = PurchaseOrderBook.status(o)
        Row(
          Modifier.fillMaxWidth().clip(Shape.button).clickable { onOpen(o.id) }.padding(horizontal = 8.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(StrSupplierBills.orderTitle(o.no, o.supplierName.ifBlank { StrMoney.supplierLabel }), color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              StrPurchasePages.poLine(o.lines.size, money(PurchaseOrderBook.asAfn(o.totals.orderedMinor)), money(PurchaseOrderBook.asAfn(o.totals.remainingMinor))) +
                (o.expectedDate?.let { StrPurchasePages.arrivesSuffix(ir.vil3ntec.tohid.formatDate(it)) } ?: ""),
              style = MaterialTheme.typography.labelSmall, color = c.muted,
            )
          }
          StatusPill(PurchaseOrderBook.label(st), poTone(st))
        }
        HorizontalDivider(color = c.border)
      }
    }
  }
}

private fun poTone(s: PurchaseOrderCore.Status): PillTone = when (s) {
  PurchaseOrderCore.Status.DRAFT -> PillTone.NEUTRAL
  PurchaseOrderCore.Status.SENT -> PillTone.INFO
  PurchaseOrderCore.Status.PARTIAL -> PillTone.WARNING
  PurchaseOrderCore.Status.COMPLETE -> PillTone.SUCCESS
  PurchaseOrderCore.Status.CANCELLED -> PillTone.DANGER
}

/* ══════════════════════════ جزئیات ══════════════════════════ */

@Composable
private fun PoDetail(
  store: ShopStore,
  d: ShopData,
  host: PurchaseOrderHost,
  perms: PoPermsDto,
  id: String,
  toast: (String) -> Unit,
  onEdit: (PurchaseOrderDto) -> Unit,
  onReceive: (PurchaseOrderDto) -> Unit,
  onBill: (PurchaseOrderDto) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val repo = host.repo
  var order by remember(id) { mutableStateOf<PurchaseOrderDto?>(null) }
  var error by remember(id) { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var cancelAsk by remember { mutableStateOf(false) }
  //  PUR-14: فاکتورِ تأمین‌کننده‌ای که «باطل» برایش باز است
  var voidAsk by remember { mutableStateOf<PoBillDto?>(null) }
  LaunchedEffect(id) {
    val r = repo.get(id)
    order = r.valueOrNull(); error = r.errorMessage()
  }
  val o = order
  if (o == null) {
    Text(error ?: StrReports.loading, color = if (error != null) c.danger else c.muted, style = MaterialTheme.typography.bodySmall)
    return
  }
  val st = PurchaseOrderBook.status(o)
  val names = remember(d) { d.products.associate { it.id to it.name } }

  fun act(block: suspend () -> ir.vil3ntec.tohid.core.net.ApiResult<PurchaseOrderDto>, done: String) {
    if (busy) return
    busy = true
    scope.launch {
      val r = block()
      busy = false
      r.valueOrNull()?.let { order = it; toast(done) } ?: toast(r.errorMessage() ?: StrSupplierBills.failedShort)
    }
  }

  /**
   *  PUR-13: برچسبِ اقلامِ پذیرفتهٔ یک رسید (S19: همان برچسب روی برگهٔ A4 ِ ۳×۸ هم). هر واحدِ شمارشی یک
   *  برچسب (تا ۹۹؛ برگهٔ A4 تا ۲۴)، کالای وزنی یکی. چاپ از مرزِ سکو (`PurchaseOrderHost.printLabel`).
   */
  fun printLabels(r: PoReceiptDto, a4: Boolean) {
    scope.launch {
      if (!a4 && !host.hasLabelPrinter()) { toast(StrPurchasePages.noLabelPrinterShort); return@launch }
      var done = 0
      for (p in r.posted.filter { it.acceptedMilli > 0 }) {
        val line = o.lines.find { it.id == p.lineId } ?: continue
        val product = d.products.find { it.id == line.productId } ?: continue
        val units = ((p.acceptedMilli + 999) / 1000).toInt()
        val copies = if (line.unit in PurchaseOrderBook.WEIGHED_UNITS) 1 else units.coerceIn(1, if (a4) 24 else 99)
        host.printLabel(product, copies, a4)?.let(toast)
        done++
      }
      if (done == 0) toast(StrPurchasePages.noAcceptedForLabel)
    }
  }

  fun postReceipt(r: PoReceiptDto) {
    scope.launch {
      var msg = StrPlaces.notSaved
      val wrote = store.update { cur ->
        when (val p = PurchaseOrderBook.post(cur, o, r, todayIso(), host.now())) {
          is PurchaseOrderBook.Posting.Ok -> { msg = StrPurchasePages.billPosted(p.invoiceNo); p.data }
          PurchaseOrderBook.Posting.Already -> { msg = StrPurchasePages.alreadyPosted; null }
          is PurchaseOrderBook.Posting.Failed -> { msg = p.message; null }
        }
      }
      toast(if (wrote == false) StrPurchasePages.ledgerNotWritten else msg)
    }
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("#${o.no} · ${o.supplierName}", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
      StatusPill(PurchaseOrderBook.label(st), poTone(st))
    }
    val meta = listOfNotNull(
      o.expectedDate?.let { StrPurchasePages.arrivesOn(ir.vil3ntec.tohid.formatDate(it)) },
      o.note.takeIf { it.isNotBlank() },
      o.cancelReason?.takeIf { st == PurchaseOrderCore.Status.CANCELLED }?.let { StrPurchasePages.cancelReasonIs(it) },
    )
    if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.muted)
    Spacer(Modifier.height(10.dp))

    //  ردیف‌ها: سفارش، پذیرفته، خراب، باقی‌مانده (PUR-04)
    Row(Modifier.fillMaxWidth()) {
      listOf(StrMoney.item to 3f, StrOnline.dlColOrder to 1f, StrPurchasePages.accepted to 1f, StrPurchasePages.damaged to 1f, StrInvoices.colBalance to 1f, StrReturnPages.unitPriceAfn to 1f).forEach { (h, w) ->
        Text(h, style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.weight(w))
      }
    }
    HorizontalDivider(color = c.border)
    val prog = o.progress.associateBy { it.lineId }
    o.lines.forEach { l ->
      val p = prog[l.id]
      Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(l.name.ifBlank { names[l.productId] ?: l.productId }, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(3f))
        Text("${qty(PurchaseOrderBook.units(l.qtyMilli))} ${l.unit}", color = c.text, modifier = Modifier.weight(1f))
        Text(qty(PurchaseOrderBook.units(p?.acceptedMilli ?: 0L)), color = c.success, modifier = Modifier.weight(1f))
        Text(qty(PurchaseOrderBook.units(p?.damagedMilli ?: 0L)), color = if ((p?.damagedMilli ?: 0L) > 0) c.danger else c.muted, modifier = Modifier.weight(1f))
        Text(qty(PurchaseOrderBook.units(p?.remainingMilli ?: 0L)), color = if ((p?.remainingMilli ?: 0L) > 0) c.warning else c.muted, modifier = Modifier.weight(1f))
        Text(money(PurchaseOrderBook.asAfn(l.priceMinor)), color = c.muted, modifier = Modifier.weight(1f))
      }
    }
    HorizontalDivider(color = c.border)
    Spacer(Modifier.height(6.dp))
    Text(
      StrPurchasePages.poTotals(money(PurchaseOrderBook.asAfn(o.totals.orderedMinor)), money(PurchaseOrderBook.asAfn(o.totals.acceptedMinor)), money(PurchaseOrderBook.asAfn(o.totals.remainingMinor))),
      style = MaterialTheme.typography.bodySmall, color = c.text,
    )

    //  کارها
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (perms.receive && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.RECEIVE)) {
        Button(onClick = { onReceive(o) }, enabled = !busy) { Text(StrPurchasePages.receiveGoods) }
      }
      if (perms.manage && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.SEND)) {
        Button(onClick = { act({ repo.send(o.id) }, StrPurchasePages.poSent) }, enabled = !busy) { Text(StrPurchasePages.sendToSupplier) }
      }
      if (perms.manage && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.EDIT)) {
        OutlinedButton(onClick = { onEdit(o) }, enabled = !busy) { Text(StrMoney.edit) }
      }
      if (perms.manage && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.CANCEL)) {
        OutlinedButton(onClick = { cancelAsk = true }, enabled = !busy) { Text(StrPay.cancel, color = c.danger) }
      }
      //  PUR-14: فاکتورِ کاغذیِ خودِ تأمین‌کننده، جدا از رسید (پس از فرستادن؛ فقط مدیر)
      if (PurchaseOrderBook.canBill(o, perms.manage)) {
        OutlinedButton(onClick = { onBill(o) }, enabled = !busy) { Text(StrPurchasePages.supplierBill) }
      }
    }

    //  PUR-14: فاکتورهای ثبت‌شدهٔ تأمین‌کننده — همان فهرستِ گوشیِ نسخهٔ ۳ (`PurchaseBills.kt`)
    PoBillsList(o, perms.manage, busy) { voidAsk = it }

    //  رسیدها و ثبت در دفترِ همین دستگاه
    if (o.receipts.isNotEmpty()) {
      Spacer(Modifier.height(14.dp))
      Text(Str.receipts, style = MaterialTheme.typography.labelLarge, color = c.muted, fontWeight = FontWeight.SemiBold)
      o.receipts.forEach { r ->
        val posted = PurchaseOrderBook.isPosted(d, r)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
              "${ir.vil3ntec.tohid.formatMillis(r.createdAt)} · ${money(PurchaseOrderBook.asAfn(r.totalMinor))} ؋",
              color = c.text, style = MaterialTheme.typography.bodySmall,
            )
            val dmg = r.posted.filter { it.damagedMilli > 0 }
            if (dmg.isNotEmpty()) Text(
              StrPurchasePages.damagedReturned + dmg.joinToString("، ") { p ->
                "${o.lines.find { it.id == p.lineId }?.name.orEmpty()} ${qty(PurchaseOrderBook.units(p.damagedMilli))}"
              },
              style = MaterialTheme.typography.labelSmall, color = c.danger,
            )
          }
          if (r.posted.any { it.acceptedMilli > 0 }) {
            TextButton(onClick = { printLabels(r, a4 = false) }) { Text(StrPurchasePages.labelBtn) }
            TextButton(onClick = { printLabels(r, a4 = true) }) { Text("A4") }
          }
          if (posted) Text(StrPurchasePages.inLedger, style = MaterialTheme.typography.labelSmall, color = c.success)
          else TextButton(onClick = { postReceipt(r) }) { Text(StrPurchasePages.postToLedger) }
        }
      }
      //  PUR-14: سفارش ⇔ رسید ⇔ فاکتورِ تأمین‌کننده (یا فاکتورِ دفتر وقتی تأمین‌کننده فاکتوری نداده)
      PoMatchLines(d, o)
    }
  }

  voidAsk?.let { b ->
    PoBillVoidDialog(b, busy, onDismiss = { voidAsk = null }) { why ->
      voidAsk = null
      busy = true
      scope.launch {
        val r = repo.voidBill(o.id, b.id, why)
        busy = false
        r.valueOrNull()?.let { order = it.order; toast(StrSupplierBills.voided) } ?: toast(r.errorMessage() ?: StrSupplierBills.failedShort)
      }
    }
  }

  if (cancelAsk) {
    var why by remember { mutableStateOf("") }
    val needWhy = st != PurchaseOrderCore.Status.DRAFT
    AlertDialog(
      onDismissRequest = { cancelAsk = false },
      containerColor = c.surfaceSolid,
      title = { Text(StrPurchasePages.cancelPoAsk(o.no), color = c.text) },
      text = {
        Column {
          Text(
            if (needWhy) StrPurchasePages.cancelOpenNote else StrPurchasePages.cancelDraftNote,
            style = MaterialTheme.typography.bodySmall, color = c.muted,
          )
          OutlinedTextField(value = why, onValueChange = { why = it.take(300) }, label = { Text(if (needWhy) StrLoyalty.adjustReason else StrSafe.reason) }, singleLine = true)
        }
      },
      confirmButton = {
        TextButton(enabled = !needWhy || why.isNotBlank(), onClick = {
          cancelAsk = false
          act({ repo.cancel(o.id, why.trim()) }, StrPurchasePages.poCancelled)
        }) { Text(StrPurchasePages.cancelIt, color = c.danger) }
      },
      dismissButton = { TextButton(onClick = { cancelAsk = false }) { Text(Str.cancel) } },
    )
  }
}


/* ══════════════════════════ ساختن و ویرایش ══════════════════════════ */

/** `unitName`: PRD-07 واحدِ خریدِ ردیف (مقدار و فی به همان واحد)؛ null = واحدِ پایهٔ `unit` */
private data class EditRow(val key: Int, val productId: String, val name: String, val unit: String, val qty: String, val price: String, val unitName: String? = null)

@Composable
private fun PoEditor(
  d: ShopData,
  repo: PurchaseOrderRepository,
  base: PurchaseOrderDto?,
  toast: (String) -> Unit,
  onSaved: (PurchaseOrderDto) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val unitsOn = purchaseUnitsOn()   //  PRD-07
  //  کلیدِ یکتای همین فرم: دو بار زدن یا قطعِ نت همان سفارش است، نه دومی
  val formKey = remember { newId() }
  var supplierId by remember { mutableStateOf(base?.supplierId ?: d.suppliers.firstOrNull()?.id.orEmpty()) }
  var seq by remember { mutableStateOf(base?.lines?.size ?: 0) }
  var rows by remember {
    mutableStateOf(base?.lines.orEmpty().mapIndexed { i, l ->
      EditRow(i, l.productId, l.name, l.unit, poNum(PurchaseOrderBook.units(l.qtyMilli)), poNum(PurchaseOrderBook.asAfn(l.priceMinor)),
        unitName = if (l.factorMilli != null) l.unit else null)
    })
  }
  var expected by remember { mutableStateOf(base?.expectedDate.orEmpty()) }
  var note by remember { mutableStateOf(base?.note.orEmpty()) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  //  PUR-09: تاریخچهٔ قیمت از دفتر؛ PUR-10: سفارش‌های فرستاده برای زمانِ تأمین و «در راه»
  val buys = remember(d) { PurchaseOrderBook.buys(d) }
  var sentOrders by remember { mutableStateOf<List<PurchaseOrderDto>>(emptyList()) }
  LaunchedEffect(Unit) { sentOrders = repo.list("sent").valueOrNull()?.orders.orEmpty() }
  val lead = remember(d, sentOrders, supplierId) { PurchaseOrderBook.leadTime(sentOrders, supplierId, PurchaseOrderBook.manualLead(d, supplierId)) }
  var historyFor by remember { mutableStateOf<String?>(null) }
  var lastKey by remember { mutableStateOf<Int?>(null) }

  fun addProduct(p: Product, qtyMilli: Long? = null, priceMinor: Long? = null) {
    if (rows.any { it.productId == p.id }) { error = StrPurchasePages.alreadyInPo(p.name); return }
    val q = qtyMilli ?: PurchaseOrderBook.suggestedQtyMilli(d, p)
    val pr = priceMinor ?: PurchaseOrderBook.lastPriceMinor(d, supplierId, p)
    rows = rows + EditRow(seq++, p.id, p.name, p.unit, poNum(PurchaseOrderBook.units(q)), poNum(PurchaseOrderBook.asAfn(pr)))
    error = null
  }

  fun addPicks(onlyLow: Boolean) {
    val picks = PurchaseOrderBook.supplierPicks(d, supplierId, onlyLow).filter { pk -> rows.none { it.productId == pk.productId } }
    if (picks.isEmpty()) { toast(if (onlyLow) StrPurchasePages.noLowFromSupplier else StrPurchasePages.noNewFromSupplier); return }
    rows = rows + picks.map { pk -> EditRow(seq++, pk.productId, pk.name, pk.unit, poNum(PurchaseOrderBook.units(pk.qtyMilli)), poNum(PurchaseOrderBook.asAfn(pk.priceMinor))) }
  }

  fun addPlanned() {
    val plan = PurchaseOrderBook.planned(d, supplierId, sentOrders, todayIso()).filter { pl -> rows.none { it.productId == pl.pick.productId } }
    if (plan.isEmpty()) { toast(StrPurchasePages.nothingToOrder); return }
    rows = rows + plan.map { pl ->
      EditRow(seq++, pl.pick.productId, pl.pick.name, pl.pick.unit, poNum(PurchaseOrderBook.units(pl.pick.qtyMilli)), poNum(PurchaseOrderBook.asAfn(pl.pick.priceMinor)))
    }
  }

  fun save() {
    val supplier = d.suppliers.find { it.id == supplierId } ?: run { error = StrPurchasePages.chooseSupplier; return }
    val lines = rows.mapIndexed { i, r ->
      val q = r.qty.toDoubleOrNull()
      val p = r.price.toDoubleOrNull()
      if (q == null || q <= 0) { error = StrPurchasePages.rowBadQty(i + 1); return }
      if (p == null || p < 0) { error = StrPurchasePages.rowBadPrice(i + 1); return }
      //  PRD-07: ردیفِ به واحدِ خرید ⇒ نامِ همان واحد و ضریبش؛ مقدار و فی به همان واحد
      val pu = if (unitsOn) unitOf(d, r) else null
      PoLineDto(id = "l${i + 1}", productId = r.productId, name = r.name, unit = pu?.name ?: r.unit, qtyMilli = PurchaseOrderBook.milli(q), priceMinor = PurchaseOrderBook.minor(p),
        factorMilli = pu?.factorMilli)
    }
    val check = PurchaseOrderCore.checkOrder(PurchaseOrderCore.Order(supplier.id, PurchaseOrderCore.State.DRAFT,
      lines.map { PurchaseOrderCore.Line(it.id, it.productId, it.qtyMilli, it.priceMinor) }))
    if (check != null) { error = if (check.code == PurchaseOrderCore.Code.LINES_REQUIRED) StrPurchasePages.needOneItem else StrPurchasePages.poInvalid(check.code); return }
    //  تاریخِ ذخیره‌شده همیشه ASCII `YYYY-MM-DD` (LOC-05)
    val exp = ir.vil3ntec.tohid.latinDigits(expected).trim().ifBlank { null }
    if (exp != null && !ir.vil3ntec.tohid.core.stock.StockCore.isoDateOk(exp)) { error = StrPurchasePages.arrivalDateFormat; return }
    val draft = PurchaseOrderRepository.Draft(supplier.id, supplier.name, lines, exp, note.trim())
    busy = true; error = null
    scope.launch {
      val r = if (base == null) repo.create(formKey, draft) else repo.update(base.id, base.version, draft)
      busy = false
      r.valueOrNull()?.let { toast(if (base == null) StrPurchasePages.draftCreated(it.no) else StrOnline.saved); onSaved(it) }
        ?: run { error = r.errorMessage() }
    }
  }

  Column(
    Modifier
      .fillMaxWidth()
      //  Ctrl+I: تاریخچهٔ قیمتِ کالای همین ردیف (آخرین ردیفی که دست خورد، وگرنه آخرین ردیف) — PUR-09
      .onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.isPrimaryModifier && e.key == Key.I) {
          historyFor = (rows.find { it.key == lastKey } ?: rows.lastOrNull())?.productId
          historyFor != null
        } else false
      }
      .verticalScroll(rememberScrollState()),
  ) {
    Text(if (base == null) StrPurchasePages.newPoTitle else StrPurchasePages.editPo(base.no), style = MaterialTheme.typography.titleSmall, color = c.text)
    Spacer(Modifier.height(8.dp))
    if (d.suppliers.isEmpty()) EmptyNote(StrPurchasePages.makeSupplierFirst)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      d.suppliers.sortedBy { it.name }.forEach { s ->
        FilterChip(selected = supplierId == s.id, onClick = { supplierId = s.id }, label = { Text(s.name) }, shape = Shape.chip)
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      lead.days?.let { if (lead.manual) StrPurchasePages.leadManual(it) else StrPurchasePages.leadMeasured(it, lead.samples) }
        ?: StrPurchasePages.leadUnknown(ReorderCore.DEFAULT_LEAD_DAYS),
      style = MaterialTheme.typography.labelSmall, color = c.muted,
    )
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { addPlanned() }, enabled = supplierId.isNotBlank()) { Text(StrPurchasePages.suggestBySales) }
      OutlinedButton(onClick = { addPicks(onlyLow = true) }, enabled = supplierId.isNotBlank()) { Text(StrPurchasePages.lowFromSupplier) }
      OutlinedButton(onClick = { addPicks(onlyLow = false) }, enabled = supplierId.isNotBlank()) { Text(StrPurchasePages.allFromSupplier) }
    }
    Spacer(Modifier.height(8.dp))
    rows.forEachIndexed { i, r ->
      Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${i + 1}. ${r.name}", color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        NumberField(r.qty, { v -> lastKey = r.key; rows = rows.map { if (it.key == r.key) it.copy(qty = v) else it } }, StrPurchasePages.qtyIn(r.unitName ?: r.unit), Modifier.width(120.dp))
        NumberField(r.price, { v -> lastKey = r.key; rows = rows.map { if (it.key == r.key) it.copy(price = v) else it } }, StrReturnPages.unitPriceAfn, Modifier.width(120.dp))
        TextButton(onClick = { historyFor = r.productId }) { Text(StrHome.history) }
        TextButton(onClick = { rows = rows.filterNot { it.key == r.key } }) { Text(StrMoney.delete) }
      }
      //  PRD-07: واحدِ خریدِ ردیف؛ فی به نسبتِ ضریب جابه‌جا می‌شود (فیِ هر واحدِ پایه همان می‌ماند)
      val product = if (unitsOn) d.products.find { it.id == r.productId } else null
      if (product != null && !product.purchaseUnits.isNullOrEmpty()) {
        val lineTotal = (r.qty.toDoubleOrNull() ?: 0.0) * (r.price.toDoubleOrNull() ?: 0.0)
        PurchaseUnitPicker(product, r.unitName, r.qty, lineTotal) { u ->
          rows = rows.map { if (it.key == r.key) rescaled(d, it, u) else it }
        }
      }
      //  PUR-09: آخرین فیِ همین تأمین‌کننده هنگامِ ورود (همان متنِ فرمِ فاکتورِ خریدِ دستی)
      val last = remember(buys, r.productId, supplierId) { PurchaseOrderBook.lastPriceHint(buys, r.productId, supplierId) ?: PurchaseOrderBook.NO_BUY_YET }
      Text(last, style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(bottom = 4.dp))
    }
    PoProductPicker(d, StrSale.addReturnItem) { addProduct(it) }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(value = expected, onValueChange = { expected = it.take(10) }, label = { Text(StrPurchasePages.arrivalOptional) }, singleLine = true, modifier = Modifier.weight(1f))
      OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text(StrDeliver.noteField) }, singleLine = true, modifier = Modifier.weight(2f))
    }
    val total = rows.sumOf { (it.qty.toDoubleOrNull() ?: 0.0) * (it.price.toDoubleOrNull() ?: 0.0) }
    Spacer(Modifier.height(6.dp))
    Text(StrPurchasePages.poTotal(money(total), rows.size), style = MaterialTheme.typography.bodySmall, color = c.text)
    error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(10.dp))
    Button(onClick = { save() }, enabled = !busy && rows.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
      Text(if (base == null) StrPurchasePages.saveDraft else StrKit.save)
    }
  }

  historyFor?.let { pid -> PriceHistoryDialog(d, buys, pid, supplierId) { historyFor = null } }
}

private fun changeText(p: SupplierPricing.Point): String = PurchaseOrderBook.changeText(p)

/** تاریخچهٔ قیمتِ خریدِ یک کالا از همهٔ تأمین‌کنندگان، تازه‌ترین بالا (PUR-09) — فرمِ سفارشِ خرید و فاکتورِ خریدِ دستی */
@Composable
internal fun PriceHistoryDialog(d: ShopData, buys: List<SupplierPricing.Buy>, productId: String, supplierId: String, onDismiss: () -> Unit) {
  val c = Shop.colors
  val names = remember(d) { d.suppliers.associate { it.id to it.name } }
  val rows = remember(buys, productId) { SupplierPricing.history(buys, productId).take(30) }
  val cheapest = remember(buys, productId) { SupplierPricing.bySupplier(buys, productId).firstOrNull() }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text(StrPurchasePages.priceHistoryOf(d.products.find { it.id == productId }?.name.orEmpty()), color = c.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (rows.isEmpty()) EmptyNote(StrPurchasePages.noPurchaseYet)
        cheapest?.let {
          Text(StrPurchasePages.cheapestLast(names[it.supplierId].orEmpty(), money(PurchaseOrderBook.asAfn(it.priceMinor))),
            style = MaterialTheme.typography.labelSmall, color = c.success)
        }
        rows.forEach { p ->
          Text(
            "${ir.vil3ntec.tohid.formatDate(p.date)} · ${names[p.supplierId] ?: "—"} · ${qty(PurchaseOrderBook.units(p.qtyMilli))} × ${money(PurchaseOrderBook.asAfn(p.priceMinor))} ؋" + changeText(p),
            style = MaterialTheme.typography.bodySmall,
            color = if (p.supplierId == supplierId) c.text else c.muted,
            fontWeight = if (p.supplierId == supplierId) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 3.dp),
          )
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(Str.close) } },
  )
}

@Composable
private fun PoProductPicker(d: ShopData, hint: String, onPick: (Product) -> Unit) {
  var term by remember { mutableStateOf("") }
  val c = Shop.colors
  OutlinedTextField(value = term, onValueChange = { term = it }, placeholder = { Text(hint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
  val t = ir.vil3ntec.tohid.latinDigits(term).trim()
  if (t.isNotEmpty()) {
    val hits = d.products.filter { it.active && (TextNormalizer.matches(it.name, t) || t in it.barcodes) }.take(6)
    if (hits.isEmpty()) Text(StrSale.noProductFound, style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(6.dp))
    hits.forEach { p ->
      Row(
        Modifier.fillMaxWidth().clip(Shape.button).clickable { onPick(p); term = "" }.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(p.name, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(StrPurchasePages.stockOf(qty(ShopStore.stock(d, p.id)), p.unit), style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
    }
  }
}

/* ══════════════════════════ دریافت (PUR-04) ══════════════════════════ */

private data class RecvRow(val received: String = "", val damaged: String = "", val price: String = "")

@Composable
private fun PoReceive(
  store: ShopStore,
  d: ShopData,
  host: PurchaseOrderHost,
  perms: PoPermsDto,
  order: PurchaseOrderDto,
  toast: (String) -> Unit,
  onDone: () -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val repo = host.repo
  //  کلیدِ یکتای همین رسید: تلاشِ دوباره همان رسید است (سرور «تکراری» می‌گوید)
  val receiptId = remember { newId() }
  val prog = remember(order) { order.progress.associateBy { it.lineId } }
  var rows by remember {
    mutableStateOf(order.lines.associate { it.id to RecvRow(price = poNum(PurchaseOrderBook.asAfn(it.priceMinor))) })
  }
  var allowOver by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf("") }
  //  PUR-06: کرایهٔ همین رسید به حمل‌کننده (فقط مدیر؛ خالی = بی کرایه)
  var freight by remember { mutableStateOf("") }
  var scan by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val index = remember(d) { ShopStore.barcodeIndex(d) }
  val unitsOn = purchaseUnitsOn()
  val unitIndex = remember(d) { ShopStore.purchaseUnitIndex(d) }

  /** اسکن: یک واحدِ کالای همان بارکد به «رسیده» */
  fun scanned(code: String): Boolean {
    //  PRD-07: بارکدِ واحدِ خرید (کارتنِ ۲۴) ⇒ ردیفِ همان واحد (+۱)، یا ردیفِ واحدِ دیگرِ همان کالا به نسبتِ ضریب
    val hit = if (unitsOn) unitIndex.find(code) else null
    val pid = hit?.productId ?: index[code.trim()]
    val line = hit?.let { h -> order.lines.find { it.productId == pid && it.factorMilli != null && PurchaseUnits.key(it.unit) == PurchaseUnits.key(h.unit.name) } }
      ?: order.lines.find { it.productId == pid }
    if (line == null) { toast(StrPurchasePages.notInPo(code)); return true }
    val cur = rows[line.id] ?: RecvRow()
    val step = if (hit == null) 1.0 else hit.unit.factorMilli.toDouble() / (line.factorMilli ?: 1000L).toDouble()
    val next = (cur.received.toDoubleOrNull() ?: 0.0) + step
    rows = rows + (line.id to cur.copy(received = poNum(next)))
    error = null
    return true
  }

  val owner = remember { Any() }
  val latestScan by rememberUpdatedState<(String) -> Boolean> { code -> scanned(code) }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.scan.ScanRouter.register(owner) { code -> latestScan(code) }
    onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(owner) }
  }

  fun submit() {
    val lines = ArrayList<PoReceiptLineDto>()
    for ((i, l) in order.lines.withIndex()) {
      val r = rows[l.id] ?: continue
      val rec = r.received.ifBlank { "0" }.toDoubleOrNull()
      val dmg = r.damaged.ifBlank { "0" }.toDoubleOrNull()
      val price = r.price.toDoubleOrNull()
      if (rec == null || rec < 0 || dmg == null || dmg < 0) { error = StrPurchasePages.rowBadQty(i + 1); return }
      if (dmg > rec) { error = StrPurchasePages.rowDamagedOver(i + 1); return }
      if (price == null || price < 0) { error = StrPurchasePages.rowBadPrice(i + 1); return }
      if (rec == 0.0) continue
      lines += PoReceiptLineDto(l.id, PurchaseOrderBook.milli(rec), PurchaseOrderBook.milli(dmg), PurchaseOrderBook.minor(price))
    }
    if (lines.isEmpty()) { error = StrPurchasePages.nothingEntered; return }
    val freightMinor = PurchaseOrderBook.freightMinorOf(freight) ?: run { error = StrPurchasePages.freightBad; return }
    busy = true; error = null
    scope.launch {
      val res = repo.receive(order.id, receiptId, lines, allowOver, note.trim(), freightMinor)
      val got = res.valueOrNull()
      if (got == null) { busy = false; error = res.errorMessage(); return@launch }
      //  رسید در سرور ثبت شد ⇒ فاکتورِ خریدِ دفتر (شناسه‌های قطعی؛ تکرار بی اثر)
      var msg = StrPurchasePages.receiptSaved
      val wrote = store.update { cur ->
        when (val p = PurchaseOrderBook.post(cur, got.order, got.receipt, todayIso(), host.now())) {
          is PurchaseOrderBook.Posting.Ok -> { msg = StrPurchasePages.receiptPosted(p.invoiceNo); p.data }
          PurchaseOrderBook.Posting.Already -> null
          is PurchaseOrderBook.Posting.Failed -> { msg = StrPurchasePages.receiptNoLedger(p.message); null }
        }
      }
      busy = false
      toast(if (wrote == false) StrPurchasePages.receiptLedgerFailed else msg)
      onDone()
    }
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    Text(StrPurchasePages.receiveTitle(order.no, order.supplierName), style = MaterialTheme.typography.titleSmall, color = c.text)
    Text(StrPurchasePages.receiveNote, style = MaterialTheme.typography.labelSmall, color = c.muted)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = scan,
      onValueChange = { v -> if (v.contains('\n')) { scanned(v.trim()); scan = "" } else scan = v.take(64) },
      singleLine = false, maxLines = 1,
      label = { Text(StrGrid.barcode) },
      keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (scan.isNotBlank()) scanned(scan.trim()); scan = "" }),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    order.lines.forEach { l ->
      val r = rows[l.id] ?: RecvRow()
      val left = prog[l.id]?.remainingMilli ?: l.qtyMilli
      Column(Modifier.padding(vertical = 4.dp)) {
        Text(StrPurchasePages.remainingOf(l.name, qty(PurchaseOrderBook.units(left)), l.unit), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumberField(r.received, { v -> rows = rows + (l.id to (rows[l.id] ?: RecvRow()).copy(received = v)); error = null }, StrPurchasePages.received, Modifier.weight(1f))
          NumberField(r.damaged, { v -> rows = rows + (l.id to (rows[l.id] ?: RecvRow()).copy(damaged = v)); error = null }, StrPurchasePages.damaged, Modifier.weight(1f))
          if (perms.manage) NumberField(r.price, { v -> rows = rows + (l.id to (rows[l.id] ?: RecvRow()).copy(price = v)); error = null }, StrReturnPages.unitPriceAfn, Modifier.weight(1f))
        }
      }
    }
    if (perms.manage) Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(checked = allowOver, onCheckedChange = { allowOver = it })
      Text(StrPurchasePages.acceptOver, style = MaterialTheme.typography.bodySmall, color = c.text)
    }
    if (perms.manage) {
      NumberField(freight, { v -> freight = v; error = null }, StrPurchasePages.freightField, Modifier.fillMaxWidth())
      Text(StrPurchasePages.freightNote,
        style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
    OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text(StrDeliver.noteField) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(10.dp))
    Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) StrSale.posting else StrLayaway.payConfirm) }
  }
}

/** عدد برای کادرِ ورودی: بی اعشارِ بی‌مورد و با رقمِ لاتین (همان قاعدهٔ کادرهای انبار) */
/** PRD-07: واحدِ خریدِ ردیف روی کالا، یا null */
private fun unitOf(d: ShopData, r: EditRow): ir.vil3ntec.tohid.core.product.PurchaseUnits.Item? {
  val p = d.products.find { it.id == r.productId } ?: return null
  return ir.vil3ntec.tohid.core.product.PurchaseUnits.find(p.purchaseUnits?.map(ShopStore::coreOf), r.unitName)
}

/** واحدِ ردیف عوض شد ⇒ فی × (ضریبِ تازه ÷ ضریبِ پیشین)، گرد به صدمِ افغانی */
private fun rescaled(d: ShopData, r: EditRow, unit: String?): EditRow {
  val from = unitOf(d, r)?.factorMilli ?: 1000L
  val next = r.copy(unitName = unit)
  val to = unitOf(d, next)?.factorMilli ?: 1000L
  val price = r.price.toDoubleOrNull() ?: return next
  return next.copy(price = poNum(Math.round(price * to / from * 100.0) / 100.0))
}

private fun poNum(value: Double): String =
  if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
