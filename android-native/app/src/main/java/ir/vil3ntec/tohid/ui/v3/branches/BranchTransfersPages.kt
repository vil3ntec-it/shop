package ir.vil3ntec.tohid.ui.v3.branches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrBranchTransfers
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrTransfer
import ir.vil3ntec.tohid.core.stock.BranchTransfer
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Status
import ir.vil3ntec.tohid.data.BranchDesk
import ir.vil3ntec.tohid.data.BranchTransferBook
import ir.vil3ntec.tohid.data.CountSheetModel
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.BranchTransfersRepository
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.theme.V3Section as V3SectionTone
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3DataRow
import ir.vil3ntec.tohid.ui.v3.V3DataRowSpec
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3NavRow
import ir.vil3ntec.tohid.ui.v3.V3RowAction
import ir.vil3ntec.tohid.ui.v3.V3RowMeta
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Summary
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3PageFootnote
import ir.vil3ntec.tohid.ui.v3.nav.V3RoutePage
import ir.vil3ntec.tohid.ui.v3.stock.DraftCountField
import ir.vil3ntec.tohid.ui.v3.stock.ProductPickerList
import ir.vil3ntec.tohid.ui.v3.stock.milliText
import ir.vil3ntec.tohid.ui.v3.stock.placeLabel
import kotlinx.coroutines.launch

/*
 *  INV-11/S21 — «انتقال میانِ شعبه‌ها» ِ گوشی (فازِ ۴ ِ `docs/BRANCHES-fa.md` §۱۰) روی کالبدِ یکسانِ صفحه: `V3RoutePage`
 *  (`Chrome.OWN`)، تراشهٔ پالایه در جای قاب، سرِ صفحه یک `V3Summary` ِ رنگِ انبار، فهرست با `V3DataRow` و کارِ اصلی یک `V3Fab`.
 *  کامپیوتر همان حال‌ها را با `ui/desk` می‌کشد (`DeskBranchTransfers.kt`، درزِ `if (desk)`).
 *   • `branches/transfers`        فهرست (به این شعبه، از این شعبه، پیش‌نویس، بسته) — «انتقالِ تازه» دکمهٔ شناور
 *   • `branches/transfers/new`    فرم: مقصد، دو مکان، کالاها، یادداشت؛ «ارسال» شناور، «ذخیرهٔ پیش‌نویس» در نوار
 *   • `branches/transfers/{id}`   جزئیات: مقصد ⇒ «دریافت» (ناقص/پایانی، کسری با دلیل) و رد؛ فرستنده ⇒ ارسال/ویرایش/لغوِ پیش‌نویس و
 *                                 لغوِ «در راه» ِ هیچ‌نرسیده
 *   • `branches/transfers/{id}/edit` ویرایشِ پیش‌نویس
 *  ⛔ پس از هر پاسخِ روشن دفترِ همین دستگاه با سرور هم‌تراز می‌شود (`BranchTransferBook.mirror`، فقط شعبهٔ میز).
 */

/** شعبهٔ میز و پیوندِ آینهٔ دفتر برای حال‌ها */
@Composable
internal fun RouteScope.rememberTransferHost(): Pair<BranchTransfersRepository?, Pair<String, suspend (List<BranchTransfer.Transfer>) -> Unit>> {
  val context = LocalContext.current
  //  همان راهِ `PurchaseOrderHost`: بی ورود هر درخواست روشن رد می‌شود (صفحه «نیامد» می‌گوید)، نه صفحهٔ خالی
  val here = remember { BranchDesk.current(context) }
  val repo: BranchTransfersRepository? = remember(context) { BranchTransfersRepository(Backend.api(context)) }
  val st = store
  val mirror: suspend (List<BranchTransfer.Transfer>) -> Unit = { ts -> BranchTransferBook.mirror(context, st, here, ts) }
  return repo to (here to mirror)
}

internal fun statusPill(s: Status): V3Status = when (s) {
  Status.DRAFT -> V3Status.REVIEW
  Status.IN_TRANSIT -> V3Status.IN_DELIVERY
  Status.PARTIAL -> V3Status.AWAITING
  Status.RECEIVED -> V3Status.DELIVERED
  Status.REJECTED, Status.CANCELLED -> V3Status.CANCELLED
}

internal fun productName(data: ShopData, id: String): String = data.products.firstOrNull { it.id == id }?.name ?: id
private fun unitOf(data: ShopData, id: String): String = data.products.firstOrNull { it.id == id }?.unit.orEmpty()

@Composable
internal fun RouteScope.BranchTransfersRoute() {
  val (repo, h) = rememberTransferHost()
  val (here, mirror) = h
  val state = remember(repo) { repo?.let { BranchTransfersState(it, here, mirror) } }
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  //  «انتقالِ تازه» فقط با `stock.move` ِ سرور در همین شعبه (هر بارِ باز شدن یک پرسشِ کوچک؛ سرور باز هم می‌سنجد)
  var move by remember { mutableStateOf(false) }
  LaunchedEffect(repo) { move = (repo?.canMove() as? ir.vil3ntec.tohid.core.net.ApiResult.Success)?.value == true }
  if (desk) return DeskBranchTransfers(state, repo, data, here, mirror, move, onRefresh = { tick++ })
  val c = PhoneV3.colors
  val ready = state?.takeIf { it.list != null }
  V3RoutePage(
    StrBranchTransfers.title,
    actions = listOf(V3Action(Icons.Outlined.Refresh, StrBranchTransfers.refresh) { tick++ }),
    chips = if (ready == null) null else ({
      V3ChipRow {
        TransferFilter.entries.forEach { f ->
          V3Chip(BranchTransfersState.label(f), ready.filter == f, { ready.filter = f }, count = ready.of(f).size.takeIf { it > 0 && f != TransferFilter.CLOSED })
        }
      }
    }),
    fab = if (!move || state == null || state.off) null else ({ e ->
      V3Fab(Icons.Outlined.Add, StrBranchTransfers.newTransfer, e, {
        nav.openForResult(BranchTransferPaths.NEW) { id -> tick++; if (id != null) nav.open(BranchTransferPaths.detail(id)) }
      })
    }),
  ) {
    val list = state?.list
    when {
      state == null || state.off -> V3EmptyState(Icons.Outlined.LocalShipping, StrBranchTransfers.off)
      list == null && state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrBranchTransfers.failed, action = StrBranchTransfers.retry, onAction = { tick++ })
      list == null -> Text(StrBranchTransfers.loading, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 16.dp))
      else -> {
        V3Summary(
          label = StrBranchTransfers.incomingLabel,
          value = state.incomingOpen.toString(),
          caption = StrBranchTransfers.summaryCaption(state.outgoingOpen, state.draftCount),
          collapseKey = BranchTransferPaths.LIST,
          section = V3SectionTone.STOCK,
        )
        if (state.failed) V3Message(V3Tone.WARNING, StrBranchTransfers.failed, Modifier.padding(top = 8.dp))
        val shown = state.shown
        if (shown.isEmpty()) {
          V3EmptyState(Icons.Outlined.LocalShipping, if (state.filter == TransferFilter.INCOMING) StrBranchTransfers.emptyIncoming else StrBranchTransfers.emptyList)
        } else {
          V3Section(null) {
            shown.forEachIndexed { i, t ->
              if (i > 0) V3Divider(start = V3DataRowSpec.DIVIDER_START)
              TransferRow(t) { nav.open(BranchTransferPaths.detail(t.id)) }
            }
          }
        }
      }
    }
    V3PageFootnote(StrBranchTransfers.note)
  }
}

@Composable
private fun TransferRow(t: BranchTransfer.Transfer, onClick: () -> Unit) {
  val amount = if (t.status.open) t.pendingMilli else t.sentMilli
  V3DataRow(
    title = BranchTransfersState.title(t),
    secondary = StrBranchTransfers.rowLine(t.lines.size, formatMillis(t.sentAt ?: t.createdAt)),
    meta = V3RowMeta(amount = milliText(amount), status = V3RowStatus.of(BranchTransferText.status(t.status), statusPill(t.status))),
    icon = Icons.Outlined.LocalShipping,
    hue = V3Modules.hueOf("stock"),
    onClick = onClick,
  )
}

/* ───────────────────────────── جزئیات ───────────────────────────── */

@Composable
internal fun RouteScope.BranchTransferRoute(id: String) {
  val (repo, h) = rememberTransferHost()
  val (here, mirror) = h
  val scope = rememberCoroutineScope()
  val state = remember(repo, id) { repo?.let { BranchTransferState(it, here, id, mirror) } }
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  var receiving by remember { mutableStateOf(false) }
  var asking by remember { mutableStateOf<String?>(null) }   //  "reject" | "cancel"
  val t = state?.transfer
  val c = PhoneV3.colors
  fun done(ok: BranchTransfer.Transfer?, text: String) { if (ok != null) toasts.show(text, V3Tone.SUCCESS) }
  val overflow = buildList {
    if (state == null || t == null) return@buildList
    if (BranchTransfer.canEdit(t, here)) add(V3MenuItem(Icons.Outlined.Edit, StrBranchTransfers.edit) { nav.openForResult(BranchTransferPaths.edit(t.id)) { tick++ } })
    if (state.canCancel) {
      add(V3MenuItem(Icons.AutoMirrored.Outlined.Undo, if (t.status == Status.DRAFT) StrBranchTransfers.cancelDraft else StrBranchTransfers.cancelSent, danger = true) { asking = "cancel" })
    }
    if (state.canReceive) add(V3MenuItem(Icons.Outlined.Block, StrBranchTransfers.reject, danger = true) { asking = "reject" })
  }
  V3RoutePage(
    StrBranchTransfers.detail,
    overflow = overflow,
    fab = when {
      state == null || t == null -> null
      state.canReceive -> ({ e -> V3Fab(Icons.Outlined.MoveToInbox, StrBranchTransfers.receive, e, { receiving = true }) })
      state.canSend -> ({ e -> V3Fab(Icons.AutoMirrored.Outlined.Send, StrBranchTransfers.send, e, { scope.launch { done(state.sendDraft(), StrBranchTransfers.sentOk) } }) })
      else -> null
    },
  ) {
    when {
      state == null || state.off -> V3EmptyState(Icons.Outlined.LocalShipping, StrBranchTransfers.off)
      state.missing -> V3EmptyState(Icons.Outlined.LocalShipping, StrBranchTransfers.notFound)
      t == null && state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrBranchTransfers.failed, action = StrBranchTransfers.retry, onAction = { tick++ })
      t == null -> Text(StrBranchTransfers.loading, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 16.dp))
      else -> TransferBody(t, state.steps, data, state.message)
    }
  }
  if (receiving && t != null && state != null) {
    ReceiveSheet(t, data, state.busy, onDismiss = { receiving = false }) { counted, final, why ->
      scope.launch { state.receive(counted, final, why)?.let { receiving = false; done(it, StrBranchTransfers.receivedOk) } }
    }
  }
  val ask = asking
  if (ask != null && t != null && state != null) {
    ReasonSheet(
      title = if (ask == "reject") StrBranchTransfers.reject else if (t.status == Status.DRAFT) StrBranchTransfers.cancelDraft else StrBranchTransfers.cancelSent,
      label = if (ask == "reject") StrBranchTransfers.rejectReason else StrBranchTransfers.cancelReason,
      required = ask == "reject" || BranchTransfer.cancelNeedsReason(t),
      busy = state.busy,
      message = state.message,
      onDismiss = { asking = null },
    ) { why ->
      scope.launch {
        val ok = if (ask == "reject") state.reject(why) else state.cancel(why)
        if (ok != null) { asking = null; done(ok, if (ask == "reject") StrBranchTransfers.rejectedOk else StrBranchTransfers.cancelledOk) }
      }
    }
  }
}

@Composable
private fun TransferBody(t: BranchTransfer.Transfer, steps: List<BranchTransfer.Step>, data: ShopData, message: String?) {
  val from = BranchTransfersState.name(t.fromBranch)
  val to = BranchTransfersState.name(t.toBranch)
  V3Summary(
    label = StrBranchTransfers.route("$from · ${placeLabel(t.from)}", "$to · ${placeLabel(t.to)}"),
    value = BranchTransferText.status(t.status),
    caption = StrBranchTransfers.figures(milliText(t.sentMilli), milliText(t.receivedMilli), milliText(t.pendingMilli)),
    note = listOfNotNull(
      formatMillis(t.sentAt ?: t.createdAt),
      t.note.ifBlank { null },
      t.reason.ifBlank { null },
    ).joinToString(" · "),
    collapseKey = "branches/transfer",
    section = V3SectionTone.STOCK,
  )
  message?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 8.dp)) }
  V3Section(StrBranchTransfers.items) {
    t.lines.forEachIndexed { i, l ->
      if (i > 0) V3Divider(start = V3DataRowSpec.DIVIDER_START)
      val unit = unitOf(data, l.productId)
      val extra = listOfNotNull(
        l.shortMilli.takeIf { it > 0 }?.let { StrBranchTransfers.short(milliText(it, unit)) },
        l.returnedMilli.takeIf { it > 0 }?.let { StrBranchTransfers.returned(milliText(it, unit)) },
      )
      V3DataRow(
        title = productName(data, l.productId),
        secondary = (listOf(StrBranchTransfers.figures(milliText(l.sentMilli, unit), milliText(l.receivedMilli ?: 0, unit), milliText(l.pendingMilli, unit))) + extra).joinToString(" · "),
        meta = V3RowMeta(amount = milliText(if (t.status.open) l.pendingMilli else l.sentMilli, unit)),
        icon = Icons.Outlined.Inventory2,
        hue = V3Modules.hueOf("stock"),
      )
    }
  }
  if (steps.isNotEmpty()) {
    V3Section(StrBranchTransfers.history) {
      steps.forEachIndexed { i, s ->
        if (i > 0) V3Divider(start = V3DataRowSpec.DIVIDER_START)
        V3DataRow(
          title = BranchTransferText.step(s.kind),
          secondary = listOfNotNull(formatMillis(s.at), s.note.ifBlank { null }).joinToString(" · "),
          meta = s.lines.takeIf { it.isNotEmpty() }?.let { V3RowMeta(amount = milliText(it.sumOf { l -> l.qtyMilli })) },
          dense = true,
        )
      }
    }
  }
}

/** برگهٔ دریافت: رسیدهٔ همین گام برای هر ردیف (خالی ⇒ در گامِ پایانی «بقیه رسید»، در گامِ ناقص هیچ)، «همه رسید»، دلیلِ کسری */
@Composable
private fun ReceiveSheet(
  t: BranchTransfer.Transfer,
  data: ShopData,
  busy: Boolean,
  onDismiss: () -> Unit,
  onReceive: (Map<String, Long>, Boolean, String) -> Unit,
) {
  var texts by remember(t.id) { mutableStateOf(mapOf<String, String>()) }
  var reason by remember(t.id) { mutableStateOf("") }
  val counted = remember(texts) { CountSheetModel.own(texts).first }
  val bad = remember(texts) { CountSheetModel.own(texts).second != null }
  val short = BranchTransfer.shortages(t, counted)
  V3Sheet(onDismiss = onDismiss, title = StrBranchTransfers.receive) {
    t.lines.filter { it.pendingMilli > 0 }.forEach { l ->
      val unit = unitOf(data, l.productId)
      val text = texts[l.productId].orEmpty()
      val over = CountSheetModel.parse(text).let { it is CountSheetModel.Cell.Ok && it.milli > l.pendingMilli }
      Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
          Text(productName(data, l.productId), style = V3Type.callout, color = PhoneV3.colors.ink)
          Text(StrBranchTransfers.figures(milliText(l.sentMilli, unit), milliText(l.receivedMilli ?: 0, unit), milliText(l.pendingMilli, unit)),
            style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
        }
        DraftCountField(text, { texts = texts + (l.productId to it) }, productName(data, l.productId), over || (text.isNotBlank() && CountSheetModel.parse(text) !is CountSheetModel.Cell.Ok))
      }
    }
    Spacer(Modifier.height(8.dp))
    V3Button(StrBranchTransfers.allArrived, { texts = t.lines.filter { it.pendingMilli > 0 }.associate { it.productId to CountSheetModel.text(it.pendingMilli) } },
      Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
    if (short.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      V3TextField(reason, { reason = it.take(BranchTransfer.MAX_REASON) }, StrBranchTransfers.shortReason, Modifier.fillMaxWidth(), required = true)
    }
    if (bad) V3Message(V3Tone.ERROR, StrTransfer.badQty, Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrBranchTransfers.receivePartial, { onReceive(counted, false, "") }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, enabled = !busy && !bad)
      V3Button(StrBranchTransfers.receiveFinal, { onReceive(counted, true, reason) }, Modifier.weight(1f), enabled = !busy && !bad && (short.isEmpty() || reason.isNotBlank()), loading = busy)
    }
  }
}

@Composable
private fun ReasonSheet(title: String, label: String, required: Boolean, busy: Boolean, message: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  var why by remember { mutableStateOf("") }
  V3Sheet(onDismiss = onDismiss, title = title, detents = false) { closer ->
    V3TextField(why, { why = it.take(BranchTransfer.MAX_REASON) }, label, Modifier.fillMaxWidth(), required = required)
    message?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrKit.cancel, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      V3Button(title, { onConfirm(why) }, Modifier.weight(1f), enabled = !busy && (!required || why.isNotBlank()), haptic = true, loading = busy)
    }
  }
}

/* ───────────────────────────── فرم ───────────────────────────── */

@Composable
internal fun RouteScope.BranchTransferFormRoute(editId: String?) {
  val (repo, h) = rememberTransferHost()
  val (here, mirror) = h
  val scope = rememberCoroutineScope()
  val state = remember(repo, editId) { repo?.let { BranchTransferFormState(it, here, editId, mirror) } }
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  fun submit(asDraft: Boolean) {
    val s = state ?: return
    scope.launch {
      s.submit(asDraft)?.let { t ->
        toasts.show(if (asDraft || s.isEdit) StrBranchTransfers.draftSaved else StrBranchTransfers.sentOk, V3Tone.SUCCESS)
        nav.finish(t.id)
      }
    }
  }
  V3RoutePage(
    if (editId != null) StrBranchTransfers.editDraft else StrBranchTransfers.newTransfer,
    actions = if (editId == null && state != null && !state.off) listOf(V3Action(Icons.Outlined.Save, StrBranchTransfers.saveDraft) { submit(true) }) else emptyList(),
    fab = if (state == null || state.off || state.destinations.isNullOrEmpty()) null else ({ e ->
      if (editId != null) V3Fab(Icons.Outlined.Save, StrBranchTransfers.save, e, { submit(true) })
      else V3Fab(Icons.AutoMirrored.Outlined.Send, StrBranchTransfers.send, e, { submit(false) })
    }),
  ) {
    val dests = state?.destinations
    when {
      state == null || state.off -> V3EmptyState(Icons.Outlined.LocalShipping, StrBranchTransfers.off)
      dests == null && state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrBranchTransfers.failed, action = StrBranchTransfers.retry, onAction = { tick++ })
      dests == null -> Text(StrBranchTransfers.loading, style = V3Type.callout, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 16.dp))
      dests.isEmpty() -> V3EmptyState(Icons.Outlined.LocalShipping, StrBranchTransfers.noDestination)
      else -> TransferForm(state, dests, data)
    }
  }
}

@Composable
private fun TransferForm(state: BranchTransferFormState, dests: List<BranchTransfer.End>, data: ShopData) {
  val d = state.draft
  var picking by remember { mutableStateOf(false) }
  var texts by remember(d.key) { mutableStateOf(d.lines.associate { it.productId to CountSheetModel.text(it.qtyMilli) }) }
  state.message?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(bottom = 8.dp)) }
  V3Section(StrBranchTransfers.destination) {
    V3ChipRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
      dests.forEach { b -> V3Chip(BranchTransfersState.name(b), d.toBranch == b.id, { state.draft = d.copy(toBranch = b.id); state.message = null }) }
    }
  }
  V3Section(null) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(StrBranchTransfers.fromPlace, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
      V3Segmented(listOf(StrTransfer.shopFloor, StrTransfer.warehouse), BranchTransfer.ENDPOINTS.indexOf(d.from).coerceAtLeast(0), { i -> state.draft = d.copy(from = BranchTransfer.ENDPOINTS[i]) })
      Text(StrBranchTransfers.toPlace, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
      V3Segmented(listOf(StrTransfer.shopFloor, StrTransfer.warehouse), BranchTransfer.ENDPOINTS.indexOf(d.to).coerceAtLeast(0), { i -> state.draft = d.copy(to = BranchTransfer.ENDPOINTS[i]) })
    }
  }
  V3Section(StrBranchTransfers.items, footer = if (d.lines.isEmpty()) StrBranchTransfers.noItems else null) {
    d.lines.forEachIndexed { i, l ->
      if (i > 0) V3Divider(start = V3DataRowSpec.DIVIDER_START)
      val text = texts[l.productId] ?: CountSheetModel.text(l.qtyMilli)
      val bad = CountSheetModel.parse(text) !is CountSheetModel.Cell.Ok
      V3DataRow(
        title = productName(data, l.productId),
        secondary = unitOf(data, l.productId).ifBlank { null },
        metaContent = {
          DraftCountField(text, { v ->
            texts = texts + (l.productId to v)
            (CountSheetModel.parse(v) as? CountSheetModel.Cell.Ok)?.let { state.draft = state.draft.with(l.productId, it.milli.coerceAtLeast(1)) }
          }, productName(data, l.productId), bad)
        },
        action = V3RowAction(StrBranchTransfers.remove, Icons.Outlined.DeleteOutline) { texts = texts - l.productId; state.draft = state.draft.with(l.productId, 0) },
        icon = Icons.Outlined.Inventory2,
        hue = V3Modules.hueOf("stock"),
      )
    }
    if (d.lines.isNotEmpty()) V3Divider(start = V3DataRowSpec.DIVIDER_START)
    V3NavRow(StrBranchTransfers.addProduct, Icons.Outlined.Add, V3Modules.hueOf("stock"), onClick = { picking = true })
  }
  V3TextField(d.note, { state.draft = d.copy(note = it.take(BranchTransfer.MAX_NOTE)) }, StrBranchTransfers.noteField, Modifier.fillMaxWidth().padding(top = 8.dp))
  if (picking) {
    V3Sheet(onDismiss = { picking = false }, title = StrBranchTransfers.addProduct, scroll = false) { closer ->
      ProductPickerList(data, null, onPick = { p ->
        if (state.draft.lines.none { it.productId == p.id }) { texts = texts + (p.id to "1"); state.draft = state.draft.with(p.id, 1000) }
        closer.close()
      }, onNew = null, modifier = Modifier.heightIn(max = 520.dp))
    }
  }
}
