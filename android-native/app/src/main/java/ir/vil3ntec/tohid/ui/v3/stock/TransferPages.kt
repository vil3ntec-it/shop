package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrTransfer
import ir.vil3ntec.tohid.core.stock.StockTransfer
import ir.vil3ntec.tohid.core.stock.StockTransfer.Status
import ir.vil3ntec.tohid.data.CountSheetModel
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FormBar
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  INV-11 — صفحه‌های «انتقالِ موجودی» روی کیتِ نسخهٔ ۳ (گوشی و کامپیوتر، همان کد): فهرست، پیش‌نویس و دریافت با اسکن.
 *  خالص از نگاهِ سکو: داده، شمرده‌ها و کارها از مسیر (`TransferRoutesAndroid.kt`) می‌رسند؛ منطق در `core/stock/StockTransfer`.
 */

/** نامِ مکان برای صفحه */
internal fun placeLabel(loc: String): String = when (loc) {
  StockTransfer.SHOP_FLOOR -> StrTransfer.shopFloor
  StockTransfer.WAREHOUSE -> StrTransfer.warehouse
  else -> loc
}

internal fun statusLabel(s: Status): String = when (s) {
  Status.IN_TRANSIT -> StrTransfer.inTransit
  Status.PARTIAL -> StrTransfer.partial
  Status.RECEIVED -> StrTransfer.received
  Status.REJECTED -> StrTransfer.rejected
  Status.REFUSED -> StrTransfer.refused
}

private fun statusPill(s: Status): V3Status = when (s) {
  Status.IN_TRANSIT -> V3Status.IN_DELIVERY
  Status.PARTIAL -> V3Status.AWAITING
  Status.RECEIVED -> V3Status.DELIVERED
  Status.REJECTED -> V3Status.CANCELLED
  Status.REFUSED -> V3Status.WARNING
}

/** مقدار با واحدِ کالا (هزارم ⇒ متن) */
internal fun milliText(milli: Long, unit: String = ""): String = listOf(qty(milli / 1000.0), unit).filter { it.isNotBlank() }.joinToString(" ")

/** پیامِ ردِ قاعده برای صفحه */
internal fun problemText(p: StockTransfer.Problem): String = when (p) {
  StockTransfer.Problem.SAME_PLACE, StockTransfer.Problem.BAD_PLACE -> StrTransfer.samePlace
  StockTransfer.Problem.EMPTY -> StrTransfer.needLines
  StockTransfer.Problem.TOO_MANY -> StrTransfer.tooMany(StockTransfer.MAX_LINES)
  StockTransfer.Problem.BAD_QTY -> StrTransfer.badQty
  StockTransfer.Problem.OVER -> StrTransfer.over
  StockTransfer.Problem.NOTHING -> StrTransfer.nothing
  StockTransfer.Problem.REASON -> StrTransfer.needReason
  StockTransfer.Problem.CLOSED -> StrTransfer.closed
  StockTransfer.Problem.NO_KEY -> StrTransfer.notSaved
}

/** نوارِ «بی اینترنت / هنوز خوانده نشده» */
@Composable
private fun OfflineNote(at: String?, live: Boolean) {
  if (live) return
  V3Message(V3Tone.INFO, if (at == null) StrTransfer.neverLoaded else StrTransfer.offline(at), Modifier.padding(top = 8.dp))
}

/**
 *  `stock/transfers` — در مسیر (و نیمه‌رسیده) بالا، بسته‌شده‌ها پایین؛ گامِ در صف نشانِ «در صف» دارد و ارسالِ ردشده
 *  هرگز پنهان نمی‌شود («سرور نپذیرفت» با دلیل).
 */
@Composable
internal fun TransferListScreen(
  view: StockTransfer.View,
  names: Map<String, String>,
  /** زمانِ آخرین نمای سرور به متن؛ `null` ⇒ هرگز */
  at: String?,
  live: Boolean,
  hasDraft: Boolean,
  onRefresh: () -> Unit,
  onNew: () -> Unit,
  onOpen: (String) -> Unit,
  onBack: (() -> Unit)?,
) {
  val open = view.transfers.filter { it.status.open }
  val closed = view.transfers.filter { !it.status.open }
  V3Scaffold(
    title = StrTransfer.title,
    subtitle = StrTransfer.subtitle,
    onBack = onBack,
    actions = listOf(
      V3Action(Icons.Outlined.Refresh, StrTransfer.refresh, onClick = onRefresh),
      V3Action(Icons.Outlined.Add, StrTransfer.newTransfer, dot = hasDraft, onClick = onNew),
    ),
  ) { pad ->
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 16.dp),
    ) {
      item(key = "note") { OfflineNote(at, live) }
      for (r in view.refusals.takeLast(5)) {
        item(key = "refused-${r.step.key}") {
          V3Message(V3Tone.ERROR, StrTransfer.refusedBecause(r.outcome.message.ifBlank { r.outcome.code }), Modifier.padding(top = 8.dp))
        }
      }
      if (view.transfers.isEmpty()) {
        item(key = "empty") { V3EmptyState(Icons.Outlined.SwapHoriz, StrTransfer.empty, body = StrTransfer.emptyBody, action = StrTransfer.newTransfer, onAction = onNew) }
      }
      if (open.isNotEmpty()) item(key = "open") { TransferGroup(StrTransfer.openSection, open, names, onOpen) }
      if (closed.isNotEmpty()) item(key = "closed") { TransferGroup(StrTransfer.closedSection, closed, names, onOpen) }
    }
  }
}

@Composable
private fun TransferGroup(title: String, ts: List<StockTransfer.Transfer>, names: Map<String, String>, onOpen: (String) -> Unit) {
  V3Section(title) {
    V3Card {
      ts.forEachIndexed { i, t ->
        if (i > 0) V3Divider(start = 16.dp)
        V3ListRow(
          title = StrTransfer.route(placeLabel(t.from), placeLabel(t.to)),
          subtitle = listOfNotNull(
            t.lines.joinToString("، ") { names[it.productId] ?: it.productId }.take(80),
            if (t.status.open) StrTransfer.lineCount(t.lines.size, milliText(t.pendingMilli)) else null,
            if (t.queued) StrTransfer.queued else null,
          ).joinToString("\n"),
          subtitleLines = 3,
          tile = false,
          trailing = {
            Column(horizontalAlignment = Alignment.End) {
              V3StatusPill(statusPill(t.status), statusLabel(t.status))
              if (t.queued) { Spacer(Modifier.height(4.dp)); V3StatusPill(V3Status.REVIEW, StrTransfer.queuedPill) }
            }
          },
          onClick = { onOpen(t.sendKey) },
        )
      }
    }
  }
}

/**
 *  `stock/transfers/new` — پیش‌نویس روی همین دستگاه (پس از بستنِ برنامه هم می‌ماند): مبدأ/مقصد، کالا با جست‌وجو یا
 *  اسکن (هر اسکن یک عدد)، مقدارِ هر ردیف و یادداشت؛ «ارسال» گامِ دفتر را می‌نویسد (بی اینترنت هم).
 */
@Composable
internal fun TransferDraftScreen(
  data: ShopData,
  draft: StockTransfer.Draft,
  message: Pair<V3Tone, String>?,
  onChange: (StockTransfer.Draft) -> Unit,
  onScanCode: (String) -> Unit,
  onCamera: (() -> Unit)?,
  onSend: () -> Unit,
  onDiscard: () -> Unit,
  onBack: () -> Unit,
) {
  val byId = remember(data) { data.products.associateBy { it.id } }
  var scanText by remember { mutableStateOf("") }
  var picking by remember { mutableStateOf(false) }
  var texts by remember(draft.key) { mutableStateOf(draft.lines.associate { it.productId to CountSheetModel.text(it.qtyMilli) }) }
  V3Scaffold(
    title = StrTransfer.newTransfer,
    onBack = onBack,
    largeTitle = false,
    actions = listOf(V3Action(Icons.Outlined.DeleteOutline, StrTransfer.discardDraft, onClick = onDiscard)),
    bottomBar = {
      V3FormBar(
        primary = StrTransfer.send,
        onPrimary = onSend,
        primaryEnabled = draft.lines.isNotEmpty(),
        secondary = StrTransfer.addProduct,
        onSecondary = { picking = true },
        summary = StrTransfer.draftSummary(draft.lines.size),
      )
    },
  ) { pad ->
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "places") {
        Column {
          Text(StrTransfer.draftKept, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
          Spacer(Modifier.height(10.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(StrTransfer.from, style = V3Type.callout, color = PhoneV3.colors.inkMuted, modifier = Modifier.width(36.dp))
            V3Segmented(
              listOf(StrTransfer.shopFloor, StrTransfer.warehouse),
              StockTransfer.ENDPOINTS.indexOf(draft.from).coerceAtLeast(0),
              { i -> val f = StockTransfer.ENDPOINTS[i]; onChange(draft.copy(from = f, to = StockTransfer.ENDPOINTS.first { it != f })) },
              Modifier.weight(1f),
            )
          }
          Spacer(Modifier.height(6.dp))
          Text(StrTransfer.route(placeLabel(draft.from), placeLabel(draft.to)), style = V3Type.callout, color = PhoneV3.colors.ink)
          Spacer(Modifier.height(10.dp))
          V3SearchField(
            scanText, { scanText = it }, StrTransfer.scanHint,
            onScan = onCamera,
            onSubmit = { onScanCode(scanText); scanText = "" },
          )
          message?.let { (tone, text) -> V3Message(tone, text, Modifier.padding(top = 8.dp)) }
        }
      }
      items(draft.lines, key = { it.productId }) { l ->
        val p = byId[l.productId]
        val text = texts[l.productId] ?: CountSheetModel.text(l.qtyMilli)
        val bad = CountSheetModel.parse(text) !is CountSheetModel.Cell.Ok
        V3Card(padding = PaddingValues(0.dp)) {
          V3ListRow(
            title = p?.name ?: l.productId,
            subtitle = p?.unit,
            tile = false,
            chevron = false,
            trailing = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                DraftCountField(text, { v ->
                  texts = texts + (l.productId to v)
                  (CountSheetModel.parse(v) as? CountSheetModel.Cell.Ok)?.let { onChange(draft.with(l.productId, it.milli.coerceAtLeast(1))) }
                }, p?.name ?: l.productId, bad)
                Spacer(Modifier.width(4.dp))
                ir.vil3ntec.tohid.ui.v3.V3IconButton(Icons.Outlined.DeleteOutline, StrTransfer.remove, onClick = { texts = texts - l.productId; onChange(draft.with(l.productId, 0)) })
              }
            },
          )
        }
      }
      item(key = "note") {
        V3TextField(draft.note, { onChange(draft.copy(note = it.take(StockTransfer.MAX_NOTE))) }, StrTransfer.note, Modifier.fillMaxWidth())
      }
    }
  }
  if (picking) {
    V3Sheet(onDismiss = { picking = false }, title = StrTransfer.addProduct, scroll = false) { closer ->
      ProductPickerList(data, null, onPick = { p ->
        if (draft.qty(p.id) == 0L) texts = texts + (p.id to "1")
        onChange(if (draft.qty(p.id) == 0L) draft.with(p.id, 1000) else draft)
        closer.close()
      }, onNew = null, modifier = Modifier.heightIn(max = 520.dp))
    }
  }
}

/**
 *  `stock/transfers/{key}` — دریافت در مقصد: هر اسکن یک واحد به «رسیده» ِ همان کالا (بیش از ماندهٔ در مسیر، کالای
 *  بیرون از انتقال و کالای وزنی پذیرفته نمی‌شود)، یا عددِ دستی؛ «همه رسید»؛ ثبتِ ناقص (بقیه در مسیر) یا بستن (کسری
 *  فقط با دلیل)؛ رد و برگشت به مبدأ با دلیل. انتقالِ بسته فقط خواندنی است.
 */
@Composable
internal fun TransferReceiveScreen(
  data: ShopData,
  t: StockTransfer.Transfer,
  texts: Map<String, String>,
  message: Pair<V3Tone, String>?,
  /** گامِ پایانیِ همین انتقال در صف است ⇒ کارِ تازه نه */
  queuedFinal: Boolean,
  onText: (String, String) -> Unit,
  onScanCode: (String) -> Unit,
  onCamera: (() -> Unit)?,
  onAll: () -> Unit,
  onPartial: () -> Unit,
  onFinal: (String) -> Unit,
  onReject: (String) -> Unit,
  onBack: () -> Unit,
) {
  val byId = remember(data) { data.products.associateBy { it.id } }
  var scanText by remember { mutableStateOf("") }
  var reason by remember(t.sendKey) { mutableStateOf("") }
  var rejecting by remember { mutableStateOf(false) }
  val actionable = t.status.open && !queuedFinal
  val counted = remember(texts) { CountSheetModel.own(texts).first }
  val short = if (actionable) StockTransfer.shortages(t, counted) else emptyList()
  V3Scaffold(
    title = StrTransfer.receiveTitle,
    subtitle = StrTransfer.route(placeLabel(t.from), placeLabel(t.to)),
    onBack = onBack,
    largeTitle = false,
    bottomBar = {
      if (actionable) {
        V3FormBar(
          primary = StrTransfer.finalReceive,
          onPrimary = { onFinal(reason) },
          secondary = StrTransfer.partialReceive,
          onSecondary = onPartial,
          summary = if (short.isEmpty()) null else StrTransfer.shortages(short.size),
        )
      }
    },
  ) { pad ->
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "head") {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            V3StatusPill(statusPill(t.status), statusLabel(t.status))
            if (t.queued) { Spacer(Modifier.width(6.dp)); V3StatusPill(V3Status.REVIEW, StrTransfer.queuedPill) }
          }
          if (t.note.isNotBlank()) Text(t.note, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 6.dp))
          if (t.reason.isNotBlank()) Text(t.reason, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 4.dp))
          if (actionable) {
            Spacer(Modifier.height(10.dp))
            V3SearchField(scanText, { scanText = it }, StrTransfer.scanHint, onScan = onCamera, onSubmit = { onScanCode(scanText); scanText = "" })
          }
          message?.let { (tone, text) -> V3Message(tone, text, Modifier.padding(top = 8.dp)) }
        }
      }
      items(t.lines, key = { it.productId }) { l ->
        val p = byId[l.productId]
        val unit = p?.unit.orEmpty()
        val text = texts[l.productId].orEmpty()
        val bad = text.isNotBlank() && CountSheetModel.parse(text).let { it !is CountSheetModel.Cell.Ok || it.milli > l.pendingMilli }
        V3Card(padding = PaddingValues(0.dp)) {
          V3ListRow(
            title = p?.name ?: l.productId,
            subtitle = StrTransfer.lineState(milliText(l.sentMilli, unit), milliText(l.receivedMilli, unit), milliText(l.pendingMilli, unit)) +
              (if (l.shortMilli > 0) "\n" + StrTransfer.shortLine(milliText(l.shortMilli, unit)) else ""),
            subtitleLines = 3,
            tile = false,
            chevron = false,
            trailing = { if (actionable && l.pendingMilli > 0) DraftCountField(text, { onText(l.productId, it) }, p?.name ?: l.productId, bad) },
          )
        }
      }
      if (actionable) {
        item(key = "actions") {
          Column {
            V3TextField(reason, { reason = it.take(StockTransfer.MAX_REASON) }, StrTransfer.reason, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              V3Button(StrTransfer.allArrived, onAll, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
              V3Button(StrTransfer.reject, { rejecting = true }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
            }
          }
        }
      }
    }
  }
  if (rejecting) {
    var why by remember { mutableStateOf(reason) }
    V3Sheet(onDismiss = { rejecting = false }, title = StrTransfer.reject, detents = false) { closer ->
      V3TextField(why, { why = it.take(StockTransfer.MAX_REASON) }, StrTransfer.rejectReason, Modifier.fillMaxWidth(), required = true)
      Spacer(Modifier.height(16.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3Button(StrKit.cancel, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
        V3Button(StrTransfer.reject, { closer.close { onReject(why) } }, Modifier.weight(1f), enabled = why.isNotBlank(), haptic = true)
      }
    }
  }
}
