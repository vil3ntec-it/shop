package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Close
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskCell
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDialog
import ir.vil3ntec.tohid.ui.desk.DeskDialogAction
import ir.vil3ntec.tohid.ui.desk.DeskDialogSize
import ir.vil3ntec.tohid.ui.desk.DeskDialogText
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskReasonDialog
import ir.vil3ntec.tohid.ui.desk.DeskSearchField
import ir.vil3ntec.tohid.ui.desk.DeskSectionTitle
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskSubhead
import ir.vil3ntec.tohid.ui.desk.DeskTableHead
import ir.vil3ntec.tohid.ui.desk.DeskTableRow
import ir.vil3ntec.tohid.ui.desk.DeskTextField
import ir.vil3ntec.tohid.ui.desk.DeskToggleRow
import ir.vil3ntec.tohid.ui.desk.DeskToolbar
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskClickable
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
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
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
import ir.vil3ntec.tohid.data.PoBillDto
import ir.vil3ntec.tohid.data.PoConfigDto
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PoReceiptDto
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseSuggest
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
import ir.vil3ntec.tohid.ui.platform.printReceiptLabels
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
  ir.vil3ntec.tohid.ui.desk.DeskButton(StrPurchasePages.purchaseOrders, { open = true }, modifier, icon = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ListAlt)
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
      //  کامپیوتر (`claude/e-desk-d3b`): پنجرهٔ کارتِ کامپیوتر — سرخط با «فهرست» و «بستن»، بی سطحِ رنگی
      val dc = ir.vil3ntec.tohid.ui.desk.Desk.colors
      Box(Modifier.padding(12.dp).widthIn(max = 920.dp).fillMaxWidth().fillMaxHeight(0.94f).deskCard(dc)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (mode != PoMode.Index) {
              ir.vil3ntec.tohid.ui.desk.DeskButton(StrPurchasePages.list, { mode = PoMode.Index }, kind = ir.vil3ntec.tohid.ui.desk.DeskButtonKind.GHOST)
              Spacer(Modifier.width(8.dp))
            }
            ir.vil3ntec.tohid.ui.desk.DeskSectionTitle(StrPurchasePages.purchaseOrders, Modifier.weight(1f))
            ir.vil3ntec.tohid.ui.desk.DeskIconButton(androidx.compose.material.icons.Icons.Outlined.Close, Str.close, onDismiss)
          }
          Spacer(Modifier.height(12.dp))
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
    //  PUR-02: «فرستاده/باز» فقط فرستاده و بخشی (`status=open` ِ سرور)؛ سرورِ کهنه همان `state=sent` و پالایشِ دوم در برنامه
    val r = repo.list(state, status = if (state == PurchaseOrderCore.State.SENT) PurchaseOrderBook.OPEN_STATUS else null)
    orders = r.valueOrNull()?.orders?.let { list -> if (state == PurchaseOrderCore.State.SENT) PoForms.openOnly(list) else list }
    error = r.errorMessage()
  }
  //  کامپیوتر (`claude/e-desk-d3b`): وضعیت یک کادرِ کشویی، کارها کنارش — یک نوار که می‌پیچد، بریده نمی‌شود
  ir.vil3ntec.tohid.ui.desk.DeskToolbar(
    end = {
      ir.vil3ntec.tohid.ui.desk.DeskButton(StrPurchasePages.reorderSuggest, onSuggest)
      if (perms.manage) ir.vil3ntec.tohid.ui.desk.DeskButton(StrPurchasePages.newPo, onNew, kind = ir.vil3ntec.tohid.ui.desk.DeskButtonKind.PRIMARY)
    },
  ) {
    ir.vil3ntec.tohid.ui.desk.DeskDropdown(
      state,
      listOf(null to Str.all, "draft" to StrPurchasePages.draft, "sent" to StrPurchasePages.sentOpen, "cancelled" to StrPay.cancel).map { (k, label) -> ir.vil3ntec.tohid.ui.desk.DeskOption(k, label) },
      { state = it },
      label = ir.vil3ntec.tohid.core.i18n.StrDesk.colStatus,
    )
  }
  Spacer(Modifier.height(10.dp))
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    when {
      error != null -> Text(error!!, color = c.danger, style = MaterialTheme.typography.bodySmall)
      orders == null -> Text(StrReports.loading, color = c.muted, style = MaterialTheme.typography.bodySmall)
      orders!!.isEmpty() -> EmptyNote(StrPurchasePages.noPoYet)
      //  ردیفِ آرام (خواستِ صاحب‌کار): عنوان، یک خطِ کم‌رنگ، مبلغ و وضعیت به واژه — بی قرصِ رنگی
      else -> orders!!.forEach { o ->
        val st = PurchaseOrderBook.status(o)
        ir.vil3ntec.tohid.ui.desk.DeskCalmRow(
          title = StrSupplierBills.orderTitle(o.no, o.supplierName.ifBlank { StrMoney.supplierLabel }),
          sub = StrPurchasePages.poLine(o.lines.size, money(PurchaseOrderBook.asAfn(o.totals.orderedMinor)), money(PurchaseOrderBook.asAfn(o.totals.remainingMinor))) +
            (o.expectedDate?.let { StrPurchasePages.arrivesSuffix(ir.vil3ntec.tohid.formatDate(it)) } ?: ""),
          amount = "${money(PurchaseOrderBook.asAfn(o.totals.orderedMinor))} ؋",
          status = PurchaseOrderBook.label(st),
          selected = false,
          onClick = { onOpen(o.id) },
        )
      }
    }
  }
}

@Suppress("unused")
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
   *  برچسب (تا ۹۹؛ برگهٔ A4 تا ۲۴)، کالای وزنی یکی — شمارش و چاپ همان گوشیِ نسخهٔ ۳ (`printReceiptLabels` ⇐ `PoForms.labels`).
   */
  fun printLabels(r: PoReceiptDto, a4: Boolean) {
    scope.launch { host.printReceiptLabels(d, o, r, a4, toast) }
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

  //  کامپیوتر (`claude/e-desk-d3c`): سرخط با وضعیت به واژه (بی قرص)، جدولِ `ui/desk`، کارها با `DeskButton` و لغو با `DeskReasonDialog`؛
  //  مجوزِ هر کار همان `PoForms.actions` و هر درخواست همان پیشین
  val dc = Desk.colors
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      DeskSectionTitle("#${o.no} · ${o.supplierName}", Modifier.weight(1f))
      Text(PurchaseOrderBook.label(st), style = DeskType.bodyStrong, color = dc.inkSecondary, maxLines = 1)
    }
    val meta = listOfNotNull(
      o.expectedDate?.let { StrPurchasePages.arrivesOn(ir.vil3ntec.tohid.formatDate(it)) },
      o.note.takeIf { it.isNotBlank() },
      o.cancelReason?.takeIf { st == PurchaseOrderCore.Status.CANCELLED }?.let { StrPurchasePages.cancelReasonIs(it) },
    )
    if (meta.isNotEmpty()) DeskNote(meta.joinToString(" · "), maxLines = 3)

    //  ردیف‌ها: سفارش، پذیرفته، خراب، باقی‌مانده (PUR-04)
    val cols = listOf(
      DeskColumn(StrMoney.item, 3f), DeskColumn(StrOnline.dlColOrder, 1f, end = true), DeskColumn(StrPurchasePages.accepted, 1f, end = true),
      DeskColumn(StrPurchasePages.damaged, 1f, end = true), DeskColumn(StrInvoices.colBalance, 1f, end = true), DeskColumn(StrReturnPages.unitPriceAfn, 1f, end = true),
    )
    DeskTableHead(cols)
    val prog = o.progress.associateBy { it.lineId }
    o.lines.forEach { l ->
      val p = prog[l.id]
      val name = l.name.ifBlank { names[l.productId] ?: l.productId }
      DeskTableRow(cols, name, onClick = null) { i ->
        when (i) {
          0 -> DeskCell(name)
          1 -> DeskCell("${qty(PurchaseOrderBook.units(l.qtyMilli))} ${l.unit}")
          2 -> DeskCell(qty(PurchaseOrderBook.units(p?.acceptedMilli ?: 0L)), color = dc.positive)
          3 -> DeskCell(qty(PurchaseOrderBook.units(p?.damagedMilli ?: 0L)), color = if ((p?.damagedMilli ?: 0L) > 0) dc.negative else dc.inkSecondary)
          4 -> DeskCell(qty(PurchaseOrderBook.units(p?.remainingMilli ?: 0L)), color = if ((p?.remainingMilli ?: 0L) > 0) dc.warning else dc.inkSecondary)
          else -> DeskCell(money(PurchaseOrderBook.asAfn(l.priceMinor)), muted = true)
        }
      }
    }
    Text(
      StrPurchasePages.poTotals(money(PurchaseOrderBook.asAfn(o.totals.orderedMinor)), money(PurchaseOrderBook.asAfn(o.totals.acceptedMinor)), money(PurchaseOrderBook.asAfn(o.totals.remainingMinor))),
      style = DeskType.bodyStrong, color = dc.ink,
    )

    //  کارها — مجوزِ هر کار از همان `PoForms.actions` ِ گوشیِ نسخهٔ ۳ (نقش × وضعیت)
    val can = PoForms.actions(o, perms)
    DeskToolbar(Modifier.padding(top = DeskSpace.x2)) {
      if (can.receive) DeskButton(StrPurchasePages.receiveGoods, { onReceive(o) }, kind = DeskButtonKind.PRIMARY, enabled = !busy)
      if (can.send) DeskButton(StrPurchasePages.sendToSupplier, { act({ repo.send(o.id) }, StrPurchasePages.poSent) }, kind = DeskButtonKind.PRIMARY, enabled = !busy)
      if (can.edit) DeskButton(StrMoney.edit, { onEdit(o) }, enabled = !busy)
      if (can.cancel) DeskButton(StrPay.cancel, { cancelAsk = true }, enabled = !busy)
      //  PUR-14: فاکتورِ کاغذیِ خودِ تأمین‌کننده، جدا از رسید (پس از فرستادن؛ فقط مدیر)
      if (can.bill) DeskButton(StrPurchasePages.supplierBill, { onBill(o) }, enabled = !busy)
    }

    //  PUR-14: فاکتورهای ثبت‌شدهٔ تأمین‌کننده — همان فهرست (`PurchaseBills.kt`، شاخهٔ کامپیوتر)
    PoBillsList(o, perms.manage, busy) { voidAsk = it }

    //  رسیدها و ثبت در دفترِ همین دستگاه
    if (o.receipts.isNotEmpty()) {
      DeskSubhead(Str.receipts, Modifier.padding(top = DeskSpace.x3))
      o.receipts.forEach { r ->
        val posted = PurchaseOrderBook.isPosted(d, r)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
          Column(Modifier.weight(1f)) {
            Text("${ir.vil3ntec.tohid.formatMillis(r.createdAt)} · ${money(PurchaseOrderBook.asAfn(r.totalMinor))} ؋", style = DeskType.body, color = dc.ink)
            val dmg = r.posted.filter { it.damagedMilli > 0 }
            if (dmg.isNotEmpty()) Text(
              StrPurchasePages.damagedReturned + dmg.joinToString("، ") { p ->
                "${o.lines.find { it.id == p.lineId }?.name.orEmpty()} ${qty(PurchaseOrderBook.units(p.damagedMilli))}"
              },
              style = DeskType.secondary, color = dc.negative,
            )
          }
          if (r.posted.any { it.acceptedMilli > 0 }) {
            DeskButton(StrPurchasePages.labelBtn, { printLabels(r, a4 = false) }, kind = DeskButtonKind.GHOST)
            DeskButton("A4", { printLabels(r, a4 = true) }, kind = DeskButtonKind.GHOST)
          }
          if (posted) Text(StrPurchasePages.inLedger, style = DeskType.secondary, color = dc.positive)
          else DeskButton(StrPurchasePages.postToLedger, { postReceipt(r) })
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
    val needWhy = PoForms.cancelNeedsReason(o)
    DeskReasonDialog(
      StrPurchasePages.cancelPoAsk(o.no), if (needWhy) StrPurchasePages.cancelOpenNote else StrPurchasePages.cancelDraftNote,
      if (needWhy) StrLoyalty.adjustReason else StrSafe.reason, why, { why = it.take(300) },
      StrPurchasePages.cancelIt, enabled = !needWhy || why.isNotBlank(),
      onConfirm = {
        cancelAsk = false
        act({ repo.cancel(o.id, why.trim()) }, StrPurchasePages.poCancelled)
      },
      onDismiss = { cancelAsk = false },
    )
  }
}


/* ══════════════════════════ ساختن و ویرایش ══════════════════════════ */

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
  //  کلیدِ یکتای همین فرم: دو بار زدن یا قطعِ نت همان سفارش است، نه دومی
  val formKey = remember { newId() }
  //  PRD-07: انتخابِ واحدِ خریدِ ردیف فقط با `ShopPolicy.productTools` (پیش‌فرض خاموش ⇒ همان فرمِ پیشین)
  val unitsOn = purchaseUnitsOn()
  var supplierId by remember { mutableStateOf(base?.supplierId ?: d.suppliers.firstOrNull()?.id.orEmpty()) }
  var rows by remember { mutableStateOf(PoForms.rowsOf(base, d)) }
  var expected by remember { mutableStateOf(base?.expectedDate.orEmpty()) }
  var note by remember { mutableStateOf(base?.note.orEmpty()) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  //  PUR-09: تاریخچهٔ قیمت از دفتر؛ PUR-10: سفارش‌های فرستاده برای زمانِ تأمین و «در راه» (همهٔ بازها، `planningOrders`)
  val buys = remember(d) { PurchaseOrderBook.buys(d) }
  var sentOrders by remember { mutableStateOf<List<PurchaseOrderDto>>(emptyList()) }
  LaunchedEffect(Unit) { sentOrders = repo.planningOrders() }
  val lead = remember(d, sentOrders, supplierId) { PurchaseOrderBook.leadTime(sentOrders, supplierId, PurchaseOrderBook.manualLead(d, supplierId)) }
  val smart = rememberSmartForecastOn()
  var historyFor by remember { mutableStateOf<String?>(null) }
  var lastKey by remember { mutableStateOf<Int?>(null) }

  //  ردیف‌ها، سنجش و پیش‌نویس از `PoForms` (همان فرمِ گوشیِ نسخهٔ ۳)
  fun addProduct(p: Product) {
    rows = PoForms.addProduct(d, rows, supplierId, p, unitsOn) ?: run { error = StrPurchasePages.alreadyInPo(p.name); return }
    error = null
  }

  fun addPicks(onlyLow: Boolean) {
    val next = PoForms.addPicks(rows, PurchaseOrderBook.supplierPicks(d, supplierId, onlyLow))
    if (next.size == rows.size) { toast(if (onlyLow) StrPurchasePages.noLowFromSupplier else StrPurchasePages.noNewFromSupplier); return }
    rows = next
  }

  fun addPlanned() {
    //  ۳-۱۷: همان پیشنهادِ صفحهٔ «پیشنهادِ سفارش» (`PurchaseSuggest.forSupplier`)، نه حسابِ جدای ۳۰ روزه
    val next = PoForms.addPicks(rows, PurchaseSuggest.forSupplier(d, supplierId, sentOrders, todayIso(), smart).map { it.pick })
    if (next.size == rows.size) { toast(StrPurchasePages.nothingToOrder); return }
    rows = next
  }

  fun save() {
    if (busy) return
    val draft = when (val chk = PoForms.draft(d, supplierId, rows, expected, note)) {
      is PoForms.DraftCheck.Bad -> { error = chk.message; return }
      is PoForms.DraftCheck.Ok -> chk.draft
    }
    busy = true; error = null
    scope.launch {
      val r = if (base == null) repo.create(formKey, draft) else repo.update(base.id, base.version, draft)
      busy = false
      r.valueOrNull()?.let { toast(if (base == null) StrPurchasePages.draftCreated(it.no) else StrOnline.saved); onSaved(it) }
        ?: run { error = r.errorMessage() }
    }
  }

  //  کامپیوتر (`claude/e-desk-d3c`): تأمین‌کننده کشویی، پیشنهادها `DeskButton`، جدولِ ردیف‌ها با `DeskNumberField` (همان نگهبانِ اسکن)،
  //  جست‌وجوی کالا و دکمهٔ اصلی — ردیف‌ها، سنجش، کلیدِ یکتا و فرستادن همان پیشین (`PoForms`، `repo.create/update`)
  val dc = Desk.colors
  val cols = listOf(
    DeskColumn("#", width = 32.dp), DeskColumn(StrMoney.item, 1f), DeskColumn(ir.vil3ntec.tohid.core.i18n.StrDeskWork.colQty, width = 130.dp, end = true),
    DeskColumn(StrReturnPages.unitPriceAfn, width = 130.dp, end = true), DeskColumn(StrInvoices.total, width = 120.dp, end = true), DeskColumn("", width = 150.dp),
  )
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
    verticalArrangement = Arrangement.spacedBy(DeskSpace.x3),
  ) {
    DeskSectionTitle(if (base == null) StrPurchasePages.newPoTitle else StrPurchasePages.editPo(base.no))
    if (d.suppliers.isEmpty()) DeskNote(StrPurchasePages.makeSupplierFirst, maxLines = 2)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
      DeskDropdown(
        supplierId, d.suppliers.sortedBy { it.name }.map { DeskOption(it.id, it.name) }, { supplierId = it },
        label = StrMoney.supplierLabel, shown = if (d.suppliers.none { it.id == supplierId }) StrMoney.pickSupplier else null,
      )
      DeskNote(
        lead.days?.let { if (lead.manual) StrPurchasePages.leadManual(it) else StrPurchasePages.leadMeasured(it, lead.samples) }
          ?: StrPurchasePages.leadUnknown(ReorderCore.DEFAULT_LEAD_DAYS),
        Modifier.weight(1f), maxLines = 2,
      )
    }
    DeskToolbar {
      DeskButton(StrPurchasePages.suggestBySales, { addPlanned() }, enabled = supplierId.isNotBlank())
      DeskButton(StrPurchasePages.lowFromSupplier, { addPicks(onlyLow = true) }, enabled = supplierId.isNotBlank())
      DeskButton(StrPurchasePages.allFromSupplier, { addPicks(onlyLow = false) }, enabled = supplierId.isNotBlank())
    }
    if (rows.isNotEmpty()) Column {
      DeskTableHead(cols)
      rows.forEachIndexed { i, r ->
        Row(
          Modifier.fillMaxWidth().heightIn(min = DeskSize.tableRow).padding(horizontal = DeskSpace.x3, vertical = DeskSpace.x1),
          verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
        ) {
          Box(Modifier.width(32.dp)) { DeskCell("${i + 1}", muted = true) }
          Box(Modifier.weight(1f)) { DeskCell(r.name) }
          DeskNumberField(r.qty, { v -> lastKey = r.key; rows = rows.map { if (it.key == r.key) it.copy(qty = v) else it } }, StrPurchasePages.qtyIn(PoForms.unitOf(r)), Modifier.width(130.dp), showLabel = false)
          DeskNumberField(
            r.price, { v -> lastKey = r.key; rows = rows.map { if (it.key == r.key) it.copy(price = v) else it } },
            r.unitName?.let(ir.vil3ntec.tohid.core.i18n.StrPurchaseUnits::priceIn) ?: StrReturnPages.unitPriceAfn, Modifier.width(130.dp), showLabel = false,
          )
          Box(Modifier.width(120.dp), contentAlignment = Alignment.CenterEnd) { DeskCell(money(PoForms.lineTotal(r)), strong = true) }
          Row(Modifier.width(150.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            DeskButton(StrHome.history, { historyFor = r.productId }, kind = DeskButtonKind.GHOST)
            DeskIconButton(androidx.compose.material.icons.Icons.Outlined.Close, StrMoney.delete, { rows = rows.filterNot { it.key == r.key } })
          }
        }
        //  PRD-07: واحدِ خریدِ ردیف؛ فی به نسبتِ ضریب جابه‌جا می‌شود (`PoForms.pickUnit`، فیِ هر واحدِ پایه همان می‌ماند)
        val product = if (unitsOn) d.products.find { it.id == r.productId } else null
        if (product != null && !product.purchaseUnits.isNullOrEmpty()) {
          PurchaseUnitPicker(product, r.unitName, r.qty, PoForms.lineTotal(r)) { u ->
            rows = rows.map { if (it.key == r.key) PoForms.pickUnit(d, it, u) else it }
          }
        }
        //  PUR-09: آخرین فیِ همین تأمین‌کننده هنگامِ ورود (همان متنِ فرمِ فاکتورِ خریدِ دستی)
        val last = remember(buys, r.productId, supplierId) { PurchaseOrderBook.lastPriceHint(buys, r.productId, supplierId) ?: PurchaseOrderBook.NO_BUY_YET }
        DeskNote(last, Modifier.padding(start = DeskSpace.x3 * 2 + 32.dp, bottom = DeskSpace.x1))
        DeskDivider(Modifier.padding(horizontal = DeskSpace.x3))
      }
    }
    PoProductPicker(d, StrSale.addReturnItem) { addProduct(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
      DeskFormLabeled(StrPurchasePages.arrivalOptional, Modifier.weight(1f)) {
        DeskTextField(expected, { expected = it.take(10) }, Modifier.fillMaxWidth(), description = StrPurchasePages.arrivalOptional)
      }
      DeskFormLabeled(StrDeliver.noteField, Modifier.weight(2f)) {
        DeskTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), description = StrDeliver.noteField)
      }
    }
    error?.let { Text(it, style = DeskType.secondary, color = dc.negative) }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrPurchasePages.poTotal(money(PoForms.total(rows)), rows.size), Modifier.weight(1f), style = DeskType.bodyStrong, color = dc.ink)
      DeskButton(if (base == null) StrPurchasePages.saveDraft else StrKit.save, { save() }, kind = DeskButtonKind.PRIMARY, enabled = !busy && rows.isNotEmpty())
    }
  }

  historyFor?.let { pid -> PriceHistoryDialog(d, buys, pid, supplierId) { historyFor = null } }
}

private fun changeText(p: SupplierPricing.Point): String = PurchaseOrderBook.changeText(p)

/** تاریخچهٔ قیمتِ خریدِ یک کالا از همهٔ تأمین‌کنندگان، تازه‌ترین بالا (PUR-09) — فرمِ سفارشِ خرید و فاکتورِ خریدِ دستی */
@Composable
internal fun PriceHistoryDialog(d: ShopData, buys: List<SupplierPricing.Buy>, productId: String, supplierId: String, onDismiss: () -> Unit) {
  val names = remember(d) { d.suppliers.associate { it.id to it.name } }
  val rows = remember(buys, productId) { SupplierPricing.history(buys, productId).take(30) }
  val cheapest = remember(buys, productId) { SupplierPricing.bySupplier(buys, productId).firstOrNull() }
  //  کامپیوتر (`claude/e-desk-d3c`): پنجرهٔ `ui/desk` — همان ۳۰ خرید، تازه‌ترین بالا، و ارزان‌ترین آخرین خرید
  val dc = Desk.colors
  DeskDialog(
    title = StrPurchasePages.priceHistoryOf(d.products.find { it.id == productId }?.name.orEmpty()),
    onDismiss = onDismiss,
    width = DeskDialogSize.form,
    confirm = DeskDialogAction(Str.close, onClick = onDismiss),
  ) {
    if (rows.isEmpty()) DeskDialogText(StrPurchasePages.noPurchaseYet)
    cheapest?.let {
      DeskDialogText(StrPurchasePages.cheapestLast(names[it.supplierId].orEmpty(), money(PurchaseOrderBook.asAfn(it.priceMinor))), color = dc.positive)
    }
    rows.forEach { p ->
      Text(
        "${ir.vil3ntec.tohid.formatDate(p.date)} · ${names[p.supplierId] ?: "—"} · ${qty(PurchaseOrderBook.units(p.qtyMilli))} × ${money(PurchaseOrderBook.asAfn(p.priceMinor))} ؋" + changeText(p),
        style = if (p.supplierId == supplierId) DeskType.bodyStrong else DeskType.body,
        color = if (p.supplierId == supplierId) dc.ink else dc.inkSecondary,
      )
    }
  }
}

@Composable
private fun PoProductPicker(d: ShopData, hint: String, onPick: (Product) -> Unit) {
  var term by remember { mutableStateOf("") }
  val dc = Desk.colors
  //  کامپیوتر (`claude/e-desk-d3c`): جست‌وجوی `ui/desk` و ردیف‌های آرامِ زدنی — همان شش نتیجه
  DeskSearchField(term, { term = it }, hint, Modifier.fillMaxWidth(), focus = null)
  val t = ir.vil3ntec.tohid.latinDigits(term).trim()
  if (t.isNotEmpty()) {
    val hits = d.products.filter { it.active && (TextNormalizer.matches(it.name, t) || t in it.barcodes) }.take(6)
    if (hits.isEmpty()) DeskNote(StrSale.noProductFound, Modifier.padding(DeskSpace.x2))
    hits.forEach { p ->
      Row(
        Modifier.fillMaxWidth().deskClickable(label = p.name) { onPick(p); term = "" }.padding(horizontal = DeskSpace.x3, vertical = DeskSpace.x2),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(p.name, Modifier.weight(1f), style = DeskType.body, color = dc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        DeskNote(StrPurchasePages.stockOf(qty(ShopStore.stock(d, p.id)), p.unit))
      }
    }
  }
}

/* ══════════════════════════ دریافت (PUR-04) ══════════════════════════ */

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
  var rows by remember { mutableStateOf(PoForms.recvRows(order)) }
  var allowOver by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf("") }
  //  PUR-06: کرایهٔ همین رسید به حمل‌کننده (فقط مدیر؛ خالی = بی کرایه)
  var freight by remember { mutableStateOf("") }
  var scan by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val index = remember(d) { ShopStore.barcodeIndex(d) }
  //  PRD-07: بارکدِ واحدِ خرید (کارتنِ ۲۴) همان واحد را می‌شمارد — فقط با `ShopPolicy.productTools`
  val unitsOn = purchaseUnitsOn()
  val unitIndex = remember(d, unitsOn) { if (unitsOn) ShopStore.purchaseUnitIndex(d) else null }

  /** اسکن: یک واحدِ کالای همان بارکد به «رسیده» (`PoForms.scan`، همان اسکنِ گوشی) */
  fun scanned(code: String): Boolean {
    when (val s = PoForms.scan(order, rows, index, code, unitIndex)) {
      is PoForms.Scan.NotInPo -> toast(StrPurchasePages.notInPo(s.code))
      is PoForms.Scan.Added -> { rows = s.rows; error = null }
    }
    return true
  }

  val owner = remember { Any() }
  val latestScan by rememberUpdatedState<(String) -> Boolean> { code -> scanned(code) }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.scan.ScanRouter.register(owner) { code -> latestScan(code) }
    onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(owner) }
  }

  fun submit() {
    if (busy) return
    //  سنجشِ ردیف‌ها و همان `PurchaseOrderCore.receive` ِ سرور پیش از فرستادن (`PoForms.receipt`)
    val (lines, freightMinor) = when (val chk = PoForms.receipt(order, rows, receiptId, allowOver, freight)) {
      is PoForms.ReceiptCheck.Bad -> { error = chk.message; return }
      is PoForms.ReceiptCheck.Ok -> chk.lines to chk.freightMinor
    }
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

  //  کامپیوتر (`claude/e-desk-d3c`): کادرها و دکمه‌های `ui/desk` (مقدارها با `DeskNumberField` ِ نگهبانِ اسکن) — اسکن، سنجش و ثبت همان
  val dc = Desk.colors
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
    Column {
      DeskSectionTitle(StrPurchasePages.receiveTitle(order.no, order.supplierName))
      DeskNote(StrPurchasePages.receiveNote, maxLines = 3)
    }
    DeskFormLabeled(StrGrid.barcode) {
      DeskTextField(
        scan, { v -> if (v.contains('\n')) { scanned(v.trim()); scan = "" } else scan = v.take(64) }, Modifier.fillMaxWidth(),
        description = StrGrid.barcode,
        onKey = { e ->
          if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { if (scan.isNotBlank()) scanned(scan.trim()); scan = ""; true } else false
        },
      )
    }
    //  PUR-04: دریافتِ کامل با یک کلیک (همان `PoForms.fillRemaining` ِ گوشی)؛ فقط کادرها پر می‌شوند، ثبت همان دکمهٔ پایین
    DeskReceiveAll(order, rows, enabled = !busy) { filled -> rows = filled; error = null; toast(StrPurchaseOrders.filledAll) }
    order.lines.forEach { l ->
      val r = rows[l.id] ?: PoForms.RecvRow()
      val left = prog[l.id]?.remainingMilli ?: l.qtyMilli
      Column(verticalArrangement = Arrangement.spacedBy(DeskSpace.x1)) {
        Text(StrPurchasePages.remainingOf(l.name, qty(PurchaseOrderBook.units(left)), l.unit), style = DeskType.bodyStrong, color = dc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
          DeskNumberField(r.received, { v -> rows = rows + (l.id to (rows[l.id] ?: PoForms.RecvRow()).copy(received = v)); error = null }, StrPurchasePages.received, Modifier.weight(1f))
          DeskNumberField(r.damaged, { v -> rows = rows + (l.id to (rows[l.id] ?: PoForms.RecvRow()).copy(damaged = v)); error = null }, StrPurchasePages.damaged, Modifier.weight(1f))
          if (perms.manage) DeskNumberField(r.price, { v -> rows = rows + (l.id to (rows[l.id] ?: PoForms.RecvRow()).copy(price = v)); error = null }, StrReturnPages.unitPriceAfn, Modifier.weight(1f))
        }
        //  PRD-07: ردیفِ به واحدِ خرید ⇒ آنچه به موجودی می‌رود به واحدِ پایه (مقدار × ضریب)
        PoForms.toStock(d, l, r)?.let { DeskNote(it, maxLines = 2) }
      }
    }
    if (perms.manage) DeskToggleRow(allowOver, StrPurchasePages.acceptOver, { allowOver = it })
    if (perms.manage) DeskNumberField(freight, { v -> freight = v; error = null }, StrPurchasePages.freightField, Modifier.fillMaxWidth(), note = StrPurchasePages.freightNote)
    DeskFormLabeled(StrDeliver.noteField) {
      DeskTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), description = StrDeliver.noteField)
    }
    error?.let { Text(it, style = DeskType.secondary, color = dc.negative) }
    DeskButton(if (busy) StrSale.posting else StrLayaway.payConfirm, { submit() }, Modifier.fillMaxWidth(), kind = DeskButtonKind.PRIMARY, enabled = !busy)
  }
}

/**
 *  PUR-04 «دریافتِ همهٔ باقی‌مانده» در پنجرهٔ کامپیوتر — همتای `PoReceiveAll` ِ گوشی: «رسیده» ِ هر ردیفِ باز = باقی‌ماندهٔ سرور
 *  (`PoForms.fillRemaining`، دقیق به هزارمِ واحدِ ردیف؛ خراب و فی دست‌نخورده). سفارشی که ردیفِ باز ندارد دکمه ندارد.
 */
@Composable
private fun DeskReceiveAll(o: PurchaseOrderDto, rows: Map<String, PoForms.RecvRow>, enabled: Boolean, onFilled: (Map<String, PoForms.RecvRow>) -> Unit) {
  if (!PoForms.hasRemaining(o)) return
  DeskButton(StrPurchaseOrders.receiveAll, { onFilled(PoForms.fillRemaining(o, rows)) }, enabled = enabled)
}
