package ir.vil3ntec.tohid.ui.v3.branches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrBranchTransfers
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrTransfer
import ir.vil3ntec.tohid.core.stock.BranchTransfer
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Status
import ir.vil3ntec.tohid.data.CountSheetModel
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.BranchTransfersSource
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskCell
import ir.vil3ntec.tohid.ui.desk.DeskChip
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskEmpty
import ir.vil3ntec.tohid.ui.desk.DeskGrid
import ir.vil3ntec.tohid.ui.desk.DeskHues
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.desk.DeskInfoRow
import ir.vil3ntec.tohid.ui.desk.DeskKpiRow
import ir.vil3ntec.tohid.ui.desk.DeskMenuRow
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskPage
import ir.vil3ntec.tohid.ui.desk.DeskPane
import ir.vil3ntec.tohid.ui.desk.DeskSearchField
import ir.vil3ntec.tohid.ui.desk.DeskSegmented
import ir.vil3ntec.tohid.ui.desk.DeskSkeleton
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskSplit
import ir.vil3ntec.tohid.ui.desk.DeskStatTile
import ir.vil3ntec.tohid.ui.desk.DeskSubhead
import ir.vil3ntec.tohid.ui.desk.DeskTextField
import ir.vil3ntec.tohid.ui.desk.DeskToolbar
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.v3.stock.milliText
import ir.vil3ntec.tohid.ui.v3.stock.placeLabel
import kotlinx.coroutines.launch

/*
 *  INV-11/S21 — «انتقال میانِ شعبه‌ها» ِ کامپیوتر روی `ui/desk` (همان حال‌های `BranchTransfersState.kt` ِ گوشی): سه کارتِ عدد
 *  (به این شعبه، از این شعبه، پیش‌نویس) که پالایه هم هستند، نوارِ ابزار با پالایه‌ها و «انتقالِ تازه» (Ctrl+N ِ کارِ اصلی)، جدولِ
 *  آرامِ تنبل با ↑↓/Enter و پنلِ کنار: جزئیات و کارها (دریافتِ ناقص/پایانی با کسریِ دلیل‌دار، رد، ارسال/ویرایش/لغوِ پیش‌نویس، لغوِ
 *  «در راه» ِ هیچ‌نرسیده)، یا فرمِ انتقالِ تازه/ویرایشِ پیش‌نویس. داورِ هر گام سرور است؛ پس از هر پاسخِ روشن دفترِ دستگاه هم‌تراز.
 */

@Composable
internal fun DeskBranchTransfers(
  state: BranchTransfersState?,
  repo: BranchTransfersSource?,
  data: ShopData,
  here: String,
  mirror: suspend (List<BranchTransfer.Transfer>) -> Unit,
  move: Boolean,
  onRefresh: () -> Unit,
) {
  val tone = DeskHues.tone("warehouse")
  var selected by remember { mutableStateOf<String?>(null) }
  //  null ⇒ بسته؛ "" ⇒ انتقالِ تازه؛ شناسه ⇒ ویرایشِ همان پیش‌نویس
  var form by remember { mutableStateOf<String?>(null) }
  DeskPage {
    val list = state?.list
    if (state != null && list != null) {
      DeskKpiRow {
        DeskStatTile(StrBranchTransfers.incomingLabel, state.incomingOpen.toString(), Icons.Outlined.MoveToInbox, tone, Modifier.weight(1f),
          selected = state.filter == TransferFilter.INCOMING, onClick = { state.filter = TransferFilter.INCOMING; selected = null })
        DeskStatTile(StrBranchTransfers.outgoing, state.outgoingOpen.toString(), Icons.Outlined.Outbox, tone, Modifier.weight(1f),
          selected = state.filter == TransferFilter.OUTGOING, onClick = { state.filter = TransferFilter.OUTGOING; selected = null })
        DeskStatTile(StrBranchTransfers.drafts, state.draftCount.toString(), Icons.Outlined.Drafts, tone, Modifier.weight(1f),
          selected = state.filter == TransferFilter.DRAFTS, onClick = { state.filter = TransferFilter.DRAFTS; selected = null })
      }
    }
    DeskToolbar(end = {
      DeskButton(StrBranchTransfers.refresh, onRefresh, icon = Icons.Outlined.Refresh)
      if (move && state != null && !state.off) {
        DeskButton(StrBranchTransfers.newTransfer, { form = ""; selected = null }, icon = Icons.Outlined.Add, kind = DeskButtonKind.PRIMARY)
      }
    }) {
      if (state != null && list != null) {
        TransferFilter.entries.forEach { f ->
          DeskChip(BranchTransfersState.label(f), state.filter == f, { state.filter = f; selected = null }, tone = tone,
            count = state.of(f).size.takeIf { it > 0 && f != TransferFilter.CLOSED })
        }
      }
    }
    val open = form
    val pick = selected
    DeskSplit(
      Modifier.weight(1f),
      pane = when {
        repo == null -> null
        open != null -> ({ _ ->
          DeskTransferForm(repo, here, open.ifEmpty { null }, data, mirror, onClose = { form = null }) { t -> form = null; selected = t.id; onRefresh() }
        })
        pick != null -> ({ _ ->
          DeskTransferPane(repo, here, pick, data, mirror, onClose = { selected = null }, onEdit = { form = it; selected = null }, onChanged = onRefresh)
        })
        else -> null
      },
    ) {
      when {
        state == null || state.off -> DeskEmpty(Icons.Outlined.LocalShipping, StrBranchTransfers.title, StrBranchTransfers.off, tone)
        list == null && state.failed -> DeskEmpty(Icons.Outlined.CloudOff, StrBranchTransfers.title, StrBranchTransfers.failed, tone,
          action = StrBranchTransfers.retry, onAction = onRefresh)
        list == null -> DeskSkeleton()
        else -> {
          val rows = state.shown
          val columns = listOf(
            DeskColumn(StrBranchTransfers.colRoute, weight = 2f), DeskColumn(StrBranchTransfers.colItems, weight = 1f),
            DeskColumn(StrBranchTransfers.colPending, width = 110.dp, end = true), DeskColumn(StrBranchTransfers.colStatus, width = 120.dp),
            DeskColumn(StrBranchTransfers.colDate, width = 130.dp),
          )
          DeskGrid(
            columns, rows.size, Modifier.fillMaxSize(),
            key = { rows[it].id },
            selected = rows.indexOfFirst { it.id == pick }.takeIf { it >= 0 },
            onPick = { selected = rows[it].id; form = null },
            onOpen = { selected = rows[it].id; form = null },
            rowLabel = { BranchTransfersState.title(rows[it]) },
            empty = {
              DeskEmpty(Icons.Outlined.LocalShipping, StrBranchTransfers.title,
                if (state.filter == TransferFilter.INCOMING) StrBranchTransfers.emptyIncoming else StrBranchTransfers.emptyList, tone)
            },
          ) { r, col ->
            val t = rows[r]
            when (col) {
              0 -> DeskCell(BranchTransfersState.title(t), strong = true)
              1 -> DeskCell(t.lines.joinToString("، ") { productName(data, it.productId) }, muted = true)
              2 -> DeskCell(milliText(if (t.status.open) t.pendingMilli else t.sentMilli))
              3 -> DeskCell(BranchTransferText.status(t.status), muted = !t.status.live)
              else -> DeskCell(formatMillis(t.sentAt ?: t.createdAt), muted = true)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DeskTransferPane(
  repo: BranchTransfersSource,
  here: String,
  id: String,
  data: ShopData,
  mirror: suspend (List<BranchTransfer.Transfer>) -> Unit,
  onClose: () -> Unit,
  onEdit: (String) -> Unit,
  onChanged: () -> Unit,
) {
  val c = Desk.colors
  val scope = rememberCoroutineScope()
  val st = remember(id) { BranchTransferState(repo, here, id, mirror) }
  LaunchedEffect(st) { st.load() }
  //  null ⇒ نما؛ "receive" | "reject" | "cancel"
  var mode by remember(id) { mutableStateOf<String?>(null) }
  var texts by remember(id) { mutableStateOf(mapOf<String, String>()) }
  var why by remember(id) { mutableStateOf("") }
  val t = st.transfer
  val counted = CountSheetModel.own(texts).first
  val badQty = CountSheetModel.own(texts).second != null
  val short = if (t != null && mode == "receive") BranchTransfer.shortages(t, counted) else emptyList()
  fun after(ok: BranchTransfer.Transfer?) { if (ok != null) { mode = null; texts = emptyMap(); why = ""; onChanged() } }
  DeskPane(
    title = t?.let { BranchTransfersState.title(it) } ?: StrBranchTransfers.detail,
    subtitle = t?.let { BranchTransferText.status(it.status) + " · " + StrBranchTransfers.figures(milliText(it.sentMilli), milliText(it.receivedMilli), milliText(it.pendingMilli)) },
    onClose = onClose,
    actions = if (t == null) null else ({
      when (mode) {
        null -> {
          if (st.canReceive) {
            DeskButton(StrBranchTransfers.receive, { mode = "receive" }, icon = Icons.Outlined.MoveToInbox, kind = DeskButtonKind.PRIMARY)
            DeskButton(StrBranchTransfers.reject, { mode = "reject" })
          }
          if (st.canSend) DeskButton(StrBranchTransfers.send, { scope.launch { after(st.sendDraft()) } }, icon = Icons.AutoMirrored.Outlined.Send, kind = DeskButtonKind.PRIMARY, enabled = !st.busy)
          if (BranchTransfer.canEdit(t, here)) DeskButton(StrBranchTransfers.edit, { onEdit(t.id) })
          if (st.canCancel) DeskButton(if (t.status == Status.DRAFT) StrBranchTransfers.cancelDraft else StrBranchTransfers.cancelSent, { mode = "cancel" }, kind = DeskButtonKind.GHOST)
        }
        "receive" -> {
          DeskButton(StrBranchTransfers.receiveFinal, { scope.launch { after(st.receive(counted, true, why)) } }, kind = DeskButtonKind.PRIMARY,
            enabled = !st.busy && !badQty && (short.isEmpty() || why.isNotBlank()))
          DeskButton(StrBranchTransfers.receivePartial, { scope.launch { after(st.receive(counted, false, "")) } }, enabled = !st.busy && !badQty)
          DeskButton(StrBranchTransfers.allArrived, { texts = t.lines.filter { it.pendingMilli > 0 }.associate { it.productId to CountSheetModel.text(it.pendingMilli) } })
          DeskButton(StrKit.cancel, { mode = null }, kind = DeskButtonKind.GHOST)
        }
        else -> {
          val need = mode == "reject" || BranchTransfer.cancelNeedsReason(t)
          DeskButton(if (mode == "reject") StrBranchTransfers.reject else StrBranchTransfers.cancelSent,
            { scope.launch { after(if (mode == "reject") st.reject(why) else st.cancel(why)) } }, kind = DeskButtonKind.PRIMARY,
            enabled = !st.busy && (!need || why.isNotBlank()))
          DeskButton(StrKit.cancel, { mode = null }, kind = DeskButtonKind.GHOST)
        }
      }
    }),
  ) {
    when {
      st.missing -> DeskNote(StrBranchTransfers.notFound)
      st.off -> DeskNote(StrBranchTransfers.off)
      t == null && st.failed -> DeskNote(StrBranchTransfers.failed)
      t == null -> DeskSkeleton(rows = 4)
      else -> {
        DeskInfoRow(StrBranchTransfers.origin, BranchTransfersState.name(t.fromBranch) + " · " + placeLabel(t.from))
        DeskInfoRow(StrBranchTransfers.target, BranchTransfersState.name(t.toBranch) + " · " + placeLabel(t.to))
        if (t.note.isNotBlank()) DeskNote(t.note, maxLines = 3)
        if (t.reason.isNotBlank()) DeskNote(t.reason, maxLines = 3)
        st.message?.let { Text(it, style = DeskType.secondary, color = c.negative) }
        DeskSubhead(StrBranchTransfers.items)
        t.lines.forEach { l ->
          val name = productName(data, l.productId)
          if (mode == "receive" && l.pendingMilli > 0) {
            val text = texts[l.productId].orEmpty()
            val over = CountSheetModel.parse(text).let { it is CountSheetModel.Cell.Ok && it.milli > l.pendingMilli }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
              Column(Modifier.weight(1f)) {
                Text(name, style = DeskType.body, color = c.ink, maxLines = 1)
                DeskNote(StrBranchTransfers.figures(milliText(l.sentMilli), milliText(l.receivedMilli ?: 0), milliText(l.pendingMilli)))
              }
              DeskTextField(value = text, onChange = { texts = texts + (l.productId to it) }, placeholder = milliText(l.pendingMilli), modifier = Modifier.width(96.dp),
                isError = over || (text.isNotBlank() && CountSheetModel.parse(text) !is CountSheetModel.Cell.Ok))
            }
          } else {
            DeskInfoRow(name, StrBranchTransfers.figures(milliText(l.sentMilli), milliText(l.receivedMilli ?: 0), milliText(l.pendingMilli)))
            if (l.shortMilli > 0) DeskNote(StrBranchTransfers.short(milliText(l.shortMilli)))
            if (l.returnedMilli > 0) DeskNote(StrBranchTransfers.returned(milliText(l.returnedMilli)))
          }
        }
        if (mode == "receive" && short.isNotEmpty()) DeskTextField(value = why, onChange = { why = it.take(BranchTransfer.MAX_REASON) }, placeholder = StrBranchTransfers.shortReason, modifier = Modifier.fillMaxWidth())
        if (mode == "reject" || mode == "cancel") {
          DeskTextField(value = why, onChange = { why = it.take(BranchTransfer.MAX_REASON) },
            placeholder = if (mode == "reject") StrBranchTransfers.rejectReason else StrBranchTransfers.cancelReason, modifier = Modifier.fillMaxWidth())
        }
        if (badQty) DeskNote(StrTransfer.badQty)
        if (st.steps.isNotEmpty()) {
          DeskSubhead(StrBranchTransfers.history)
          st.steps.forEach { s -> DeskInfoRow(BranchTransferText.step(s.kind), formatMillis(s.at)) }
        }
      }
    }
  }
}

@Composable
private fun DeskTransferForm(
  repo: BranchTransfersSource,
  here: String,
  editId: String?,
  data: ShopData,
  mirror: suspend (List<BranchTransfer.Transfer>) -> Unit,
  onClose: () -> Unit,
  onDone: (BranchTransfer.Transfer) -> Unit,
) {
  val c = Desk.colors
  val scope = rememberCoroutineScope()
  val st = remember(editId) { BranchTransferFormState(repo, here, editId, mirror) }
  LaunchedEffect(st) { st.load() }
  var query by remember { mutableStateOf("") }
  var texts by remember(editId) { mutableStateOf(mapOf<String, String>()) }
  val d = st.draft
  val dests = st.destinations
  fun submit(asDraft: Boolean) { scope.launch { st.submit(asDraft)?.let(onDone) } }
  DeskPane(
    title = if (editId != null) StrBranchTransfers.editDraft else StrBranchTransfers.newTransfer,
    onClose = onClose,
    actions = if (dests.isNullOrEmpty()) null else ({
      if (editId == null) {
        DeskButton(StrBranchTransfers.send, { submit(false) }, icon = Icons.AutoMirrored.Outlined.Send, kind = DeskButtonKind.PRIMARY, enabled = !st.busy)
        DeskButton(StrBranchTransfers.saveDraft, { submit(true) }, enabled = !st.busy)
      } else {
        DeskButton(StrBranchTransfers.save, { submit(true) }, kind = DeskButtonKind.PRIMARY, enabled = !st.busy)
      }
    }),
  ) {
    when {
      st.off -> DeskNote(StrBranchTransfers.off)
      dests == null && st.failed -> DeskNote(StrBranchTransfers.failed)
      dests == null -> DeskSkeleton(rows = 4)
      dests.isEmpty() -> DeskNote(StrBranchTransfers.noDestination, maxLines = 2)
      else -> {
        st.message?.let { Text(it, style = DeskType.secondary, color = c.negative) }
        DeskDropdown(
          d.toBranch, dests.map { DeskOption<String?>(it.id, BranchTransfersState.name(it)) }, { v -> st.draft = st.draft.copy(toBranch = v); st.message = null },
          Modifier.fillMaxWidth(), label = StrBranchTransfers.destination, shown = if (d.toBranch == null) "—" else null,
        )
        DeskSubhead(StrBranchTransfers.fromPlace)
        DeskSegmented(listOf(BranchTransfer.SHOP_FLOOR to StrTransfer.shopFloor, BranchTransfer.WAREHOUSE to StrTransfer.warehouse), d.from, { st.draft = st.draft.copy(from = it) })
        DeskSubhead(StrBranchTransfers.toPlace)
        DeskSegmented(listOf(BranchTransfer.SHOP_FLOOR to StrTransfer.shopFloor, BranchTransfer.WAREHOUSE to StrTransfer.warehouse), d.to, { st.draft = st.draft.copy(to = it) })
        DeskSubhead(StrBranchTransfers.items)
        if (d.lines.isEmpty()) DeskNote(StrBranchTransfers.noItems, maxLines = 2)
        d.lines.forEach { l ->
          val text = texts[l.productId] ?: CountSheetModel.text(l.qtyMilli)
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
            Text(productName(data, l.productId), Modifier.weight(1f), style = DeskType.body, color = c.ink, maxLines = 1)
            DeskTextField(value = text, onChange = { v ->
              texts = texts + (l.productId to v)
              (CountSheetModel.parse(v) as? CountSheetModel.Cell.Ok)?.let { st.draft = st.draft.with(l.productId, it.milli.coerceAtLeast(1)) }
            }, placeholder = "1", modifier = Modifier.width(96.dp), isError = CountSheetModel.parse(text) !is CountSheetModel.Cell.Ok)
            DeskIconButton(Icons.Outlined.DeleteOutline, StrBranchTransfers.remove, { texts = texts - l.productId; st.draft = st.draft.with(l.productId, 0) })
          }
        }
        DeskSearchField(value = query, onChange = { query = it }, placeholder = StrBranchTransfers.searchProduct, modifier = Modifier.fillMaxWidth())
        val q = query.trim()
        if (q.isNotEmpty()) {
          data.products.asSequence().filter { p -> p.name.contains(q, ignoreCase = true) && d.lines.none { it.productId == p.id } }.take(6).forEach { p ->
            DeskMenuRow(p.name, icon = Icons.Outlined.Add) { texts = texts + (p.id to "1"); st.draft = st.draft.with(p.id, 1000); query = "" }
          }
        }
        DeskTextField(value = d.note, onChange = { st.draft = st.draft.copy(note = it.take(BranchTransfer.MAX_NOTE)) }, placeholder = StrBranchTransfers.noteField, modifier = Modifier.fillMaxWidth())
      }
    }
  }
}
