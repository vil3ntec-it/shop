package ir.vil3ntec.tohid.ui.screens
// i18n: سه‌زبانه (متنِ وضعیتِ قلم و پیامِ ردِ سرور هنوز از `PickText` ِ فارسی‌اند)

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPick
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.model.PickBatchDto
import ir.vil3ntec.tohid.core.model.PickIssueDto
import ir.vil3ntec.tohid.core.model.PickMetricsDto
import ir.vil3ntec.tohid.core.model.PickShelfItemDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.pick.PickDesk
import ir.vil3ntec.tohid.core.pick.PickText
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.data.repo.PickingRepository
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 *  میزِ جمع‌آوری (PCK-10، PCK-12؛ §۶.۱۷) — پشتِ پرچمِ `shop.orders.picking` و فقط با `orders.manage`.
 *
 *  - قفسهٔ انتظار: جای قفسه یک بار نوشته می‌شود و هر کیسهٔ بسته با برچسبِ QR ِ خودش اسکن می‌شود
 *    (بارکدخوانِ صفحه‌کلیدی؛ Enter می‌فرستد). همهٔ کیسه‌ها ⇒ «آماده» (و برای دریافت از دکان «آمادهٔ دریافت»)
 *    — تصمیم با سرور؛ `PickDesk.parseLabel` فقط برچسبِ اشتباه را پیش از رفتن می‌گیرد.
 *  - جمع‌آوریِ گروهی: ۲ تا ۱۰ سفارشِ قبول‌شده ⇒ یک دسته (کلیدِ یکتایی: تکرار همان دسته) و فهرستِ یک‌جا به
 *    ترتیبِ قفسه با «سبدِ» هر سفارش. هر کار رویدادِ همان سفارش است (همان `eventId` پس از قطعِ شبکه)؛ وزن و
 *    بسته‌بندی در برگهٔ خودِ سفارش (`PickSheet`).
 *
 *  ⛔ این فایل بی Context است (راهِ آیفون)؛ `api` و لرزش از صفحهٔ سفارش‌ها می‌آیند.
 */
@Composable
internal fun PickDeskEntry(cfg: OrdersConfigDto, api: ApiClient, buzz: (Boolean) -> Unit, onChanged: () -> Unit) {
  if (!cfg.picking || !cfg.permissions.manage) return
  var open by remember { mutableStateOf(false) }
  Spacer(Modifier.width(8.dp))
  TohidSecondaryButton(StrPick.desk, onClick = { open = true })
  if (open) PickDeskSheet(api, cfg.permissions, buzz, onClose = { open = false }, onChanged = onChanged)
}

@Composable
private fun PickDeskSheet(api: ApiClient, perms: OrderPermsDto, buzz: (Boolean) -> Unit, onClose: () -> Unit, onChanged: () -> Unit) {
  val repo = remember(api) { PickingRepository(api) }
  val orders = remember(api) { OrdersRepository(api) }
  var tab by remember { mutableStateOf("shelf") }
  var sheetFor by remember { mutableStateOf<Pair<String, Long>?>(null) }

  TohidBottomSheet(title = StrPick.title, onDismiss = onClose) {
    SheetBody {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TohidFilterChip(label = StrPick.tabShelf, selected = tab == "shelf", onClick = { tab = "shelf" })
        TohidFilterChip(label = StrPick.tabBatch, selected = tab == "batch", onClick = { tab = "batch" })
        //  PCK-13/14: فقط با اجازهٔ همان درها (`/orders/config`)
        if (perms.pickMetrics) TohidFilterChip(label = StrPick.tabMetrics, selected = tab == "metrics", onClick = { tab = "metrics" })
        if (perms.pickIssues) TohidFilterChip(label = StrPick.tabIssues, selected = tab == "issues", onClick = { tab = "issues" })
      }
      when (tab) {
        "shelf" -> ShelfPane(repo, buzz, onChanged)
        "metrics" -> MetricsPane(repo)
        "issues" -> IssuesPane(repo, onChanged)
        else -> BatchPane(repo, orders, buzz, onChanged, onOpenSheet = { id, no -> sheetFor = id to no })
      }
    }
  }
  sheetFor?.let { (id, no) ->
    PickSheet(orderId = id, orderNo = no, repo = repo, buzz = buzz, onClose = { sheetFor = null }, onChanged = onChanged)
  }
}

// ── شاخص‌ها (PCK-13) ─────────────────────────────────────────────────

/** زمانِ جمع‌آوری، درصدِ پیدا شدن و نرخِ جایگزینی — همه از سرور (`GET /pick/metrics`، ۷ روزِ گذشته) */
@Composable
private fun MetricsPane(repo: PickingRepository) {
  val c = Shop.colors
  var m by remember { mutableStateOf<PickMetricsDto?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) { repo.metrics().onSuccess { m = it }.onFailure { error = it.userMessage } }
  Text(StrPick.metricsHint, style = MaterialTheme.typography.bodySmall, color = c.muted)
  val x = m
  if (x == null) {
    if (error != null) ErrorLine(error) else TohidLoadingState(rows = 3)
    return
  }
  TohidCard {
    Text(StrPick.mOrders(x.orders), fontWeight = FontWeight.SemiBold, color = c.text)
    Text(StrPick.mAvg(PickDesk.minutes(x.avgPickMs)), style = MaterialTheme.typography.bodyMedium, color = c.text)
    Text(StrPick.mMedian(PickDesk.minutes(x.medianPickMs)), style = MaterialTheme.typography.bodySmall, color = c.muted)
    Text(StrPick.mLines(x.found, x.lines), style = MaterialTheme.typography.bodyMedium, color = c.text)
    Text(StrPick.mFound(PickDesk.percent(x.foundRateBp)), style = MaterialTheme.typography.bodyMedium, color = c.text)
    Text(StrPick.mSubst(PickDesk.percent(x.substitutionRateBp)), style = MaterialTheme.typography.bodyMedium, color = c.text)
    Text(StrPick.mWrong(x.wrongScans), style = MaterialTheme.typography.bodySmall, color = c.muted)
  }
}

// ── رسیدگی به «پیدا نکردم» (PCK-14) ─────────────────────────────────────

/** وظیفه‌های باز؛ «رسیدگی شد» کالا را به ویترین برمی‌گرداند، «کنار گذاشته شد» نه — هر دو با توضیح (سرور) */
@Composable
private fun IssuesPane(repo: PickingRepository, onChanged: () -> Unit) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var list by remember { mutableStateOf<List<PickIssueDto>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var open by remember { mutableStateOf<String?>(null) }
  var note by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  fun load() {
    scope.launch { repo.issues().onSuccess { list = it.issues; error = null }.onFailure { error = it.userMessage } }
  }
  LaunchedEffect(Unit) { load() }

  fun act(id: String, action: String) {
    if (busy || !PickDesk.canResolve(action, note)) return
    busy = true
    scope.launch {
      repo.resolveIssue(id, action, note)
        .onSuccess { open = null; note = ""; onChanged(); load() }
        .onFailure { error = it.userMessage }
      busy = false
    }
  }

  Text(StrPick.issuesHint, style = MaterialTheme.typography.bodySmall, color = c.muted)
  ErrorLine(error)
  val l = list
  if (l == null) {
    if (error == null) TohidLoadingState(rows = 3)
    return
  }
  if (l.isEmpty()) {
    Text(StrPick.issuesEmpty, style = MaterialTheme.typography.bodyMedium, color = c.muted)
    return
  }
  for (i in l) {
    TohidCard(onClick = { open = if (open == i.id) null else i.id; note = "" }) {
      Text(StrPick.issueRow(i.detail.name.ifBlank { i.productId }, i.hits), fontWeight = FontWeight.SemiBold, color = c.text)
      if (open == i.id) {
        TohidTextField(note, { note = it.take(300) }, label = StrPick.issueNote, singleLine = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TohidButton(StrPick.resolve, onClick = { act(i.id, "resolved") }, enabled = !busy && note.isNotBlank())
          TohidSecondaryButton(StrPick.dismiss, onClick = { act(i.id, "dismissed") }, enabled = !busy && note.isNotBlank())
        }
      }
    }
  }
}

// ── قفسهٔ انتظار (PCK-10) ─────────────────────────────────────────────

@Composable
private fun ShelfPane(repo: PickingRepository, buzz: (Boolean) -> Unit, onChanged: () -> Unit) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var items by remember { mutableStateOf<List<PickShelfItemDto>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var slot by remember { mutableStateOf("") }
  var scan by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

  fun load() {
    scope.launch {
      repo.shelf().onSuccess { items = it.shelf; error = null }.onFailure { error = it.userMessage }
    }
  }
  LaunchedEffect(Unit) { load() }

  fun submit() {
    val code = scan.trim()
    if (code.isEmpty() || busy) return
    if (slot.isBlank()) { note = false to StrPick.slotFirst; return }
    if (PickDesk.parseLabel(code) == null) { scan = ""; buzz(false); note = false to StrPick.notLabel; return }
    scan = ""
    busy = true
    note = null
    scope.launch {
      repo.shelfScan(code, slot)
        .onSuccess { r ->
          buzz(true)
          note = true to (if (r.readyNow) StrPick.readyNow else StrPick.bagOn(r.orderNo, r.bag, r.bags, r.slot))
          if (r.readyNow) onChanged()
          load()
        }
        .onFailure { buzz(false); note = false to it.userMessage }
      busy = false
    }
  }

  Text(StrPick.shelfHint, style = MaterialTheme.typography.bodySmall, color = c.muted)
  TohidTextField(slot, { slot = it.take(20) }, label = StrPick.slot)
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
      value = scan,
      onValueChange = { scan = it.take(64) },
      label = { Text(StrPick.scanBag) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { submit() }, onDone = { submit() }, onGo = { submit() }),
      modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(8.dp))
    TohidSecondaryButton(StrPick.send, onClick = { submit() }, enabled = !busy && scan.isNotBlank())
  }
  note?.let { (ok, text) -> Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) c.success else c.danger) }

  val list = items
  if (list == null) {
    if (error != null) { ErrorLine(error); TohidButton(StrPick.refresh, onClick = { load() }) } else TohidLoadingState(rows = 3)
  } else if (list.isEmpty()) {
    Text(StrPick.shelfEmpty, style = MaterialTheme.typography.bodyMedium, color = c.muted)
  } else {
    for (row in list) {
      TohidCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(StrPick.shelfRow(row.orderNo, row.slot), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = c.text)
          StatusPill(if (row.ready) StrPick.ready else StrPick.waitingBags, if (row.ready) PillTone.SUCCESS else PillTone.WARNING)
        }
        Text(StrPick.bags(row.scanned, row.bags), style = MaterialTheme.typography.bodySmall, color = c.text)
      }
    }
  }
}

// ── جمع‌آوریِ گروهی (PCK-12) ───────────────────────────────────────────

@Composable
private fun BatchPane(
  repo: PickingRepository,
  orders: OrdersRepository,
  buzz: (Boolean) -> Unit,
  onChanged: () -> Unit,
  onOpenSheet: (String, Long) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var candidates by remember { mutableStateOf<List<OrderDto>?>(null) }
  val chosen = remember { mutableStateListOf<String>() }
  var batch by remember { mutableStateOf<PickBatchDto?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
  var scan by remember { mutableStateOf("") }
  var selected by remember { mutableStateOf<Pair<String, Int>?>(null) }
  //  کارِ در راه: امضا ⇒ شناسهٔ رویداد (پاسخِ گم‌شده ⇒ همان شناسه، سرور یک بار حساب می‌کند)
  var inflight by remember { mutableStateOf<Pair<String, String>?>(null) }
  val batchKey = remember { DeliveryRepository.OnceKey { PickingRepository.newEventId() } }

  fun loadCandidates() {
    scope.launch {
      orders.list("preparing")
        .onSuccess { page -> candidates = page.items.filter { PickDesk.batchable(it.status) }; error = null }
        .onFailure { error = it.userMessage }
    }
  }
  LaunchedEffect(Unit) { loadCandidates() }

  fun reload(id: String) {
    scope.launch { repo.batch(id).onSuccess { batch = it }.onFailure { note = false to it.userMessage } }
  }

  fun act(orderId: String, sig: String, build: (String) -> JsonObject) {
    if (busy) return
    val b = batch ?: return
    val id = inflight?.takeIf { it.first == sig }?.second ?: PickingRepository.newEventId()
    inflight = sig to id
    busy = true
    note = null
    scope.launch {
      repo.send(orderId, listOf(build(id)))
        .onSuccess { out ->
          inflight = null
          val r = out.results.firstOrNull()
          if (r != null && r.status != "accepted") {
            buzz(false)
            note = false to r.message.ifBlank { PickText.reject(r.code) }
          } else {
            buzz(true)
          }
          reload(b.batchId)
        }
        .onFailure { note = false to "${it.userMessage} — ${StrPick.retry}" }
      busy = false
    }
  }

  val b = batch
  if (b == null) {
    val list = candidates
    Text(StrPick.choose, style = MaterialTheme.typography.bodySmall, color = c.muted)
    if (list == null) {
      if (error != null) { ErrorLine(error); TohidButton(StrPick.refresh, onClick = { loadCandidates() }) } else TohidLoadingState(rows = 3)
    } else if (list.isEmpty()) {
      Text(StrPick.noOrders, style = MaterialTheme.typography.bodyMedium, color = c.muted)
    } else {
      run {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          for (o in list) {
            val on = o.id in chosen
            TohidFilterChip(
              label = "#${o.orderNo}",
              selected = on,
              onClick = { if (on) chosen.remove(o.id) else if (chosen.size < PickDesk.MAX_BATCH) chosen.add(o.id) },
            )
          }
        }
        TohidButton(
          StrPick.make(chosen.size),
          busy = busy,
          enabled = !busy && PickDesk.canBatch(chosen.size),
          modifier = Modifier.fillMaxWidth(),
          onClick = {
            busy = true
            note = null
            val ids = chosen.toList()
            scope.launch {
              repo.createBatch(ids, batchKey.of(PickingRepository.batchJson(ids)))
                .onSuccess { batch = it; onChanged() }
                .onFailure { note = false to it.userMessage }
              busy = false
            }
          },
        )
      }
    }
    note?.let { (ok, text) -> Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) c.success else c.danger) }
    return
  }

  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(StrPick.progress(b.progress.done, b.progress.total), Modifier.weight(1f), fontWeight = FontWeight.Bold, color = c.text)
    TohidSecondaryButton(StrPick.newBatch, onClick = { batch = null; chosen.clear(); selected = null; loadCandidates() }, enabled = !busy)
  }
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (o in b.orders) TohidSecondaryButton(StrPick.orderSheet(o.orderNo), onClick = { onOpenSheet(o.orderId, o.orderNo) })
  }
  Text(StrPick.packHint, style = MaterialTheme.typography.bodySmall, color = c.muted)

  val pending = b.lines.filter { it.status == "pending" }
  val current = selected?.takeIf { s -> pending.any { it.orderId == s.first && it.lineNo == s.second } }
    ?: pending.firstOrNull()?.let { it.orderId to it.lineNo }
  if (current != null) {
    val line = b.lines.first { it.orderId == current.first && it.lineNo == current.second }
    fun submitScan() {
      val code = scan.trim()
      if (code.isEmpty()) return
      scan = ""
      act(line.orderId, "scan:${line.orderId}:${line.lineNo}:$code:${line.pickedQtyMilli}") {
        PickingRepository.event(it, "scan", line.lineNo, barcode = code)
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = scan,
        onValueChange = { scan = it.take(64) },
        label = { Text("${StrPick.scanItem} — ${StrPick.bin(line.bin, line.orderNo)} · ${line.name}") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { submitScan() }, onDone = { submitScan() }, onGo = { submitScan() }),
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(8.dp))
      TohidSecondaryButton(StrPick.send, onClick = { submitScan() }, enabled = !busy && scan.isNotBlank())
    }
  }
  note?.let { (ok, text) -> Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) c.success else c.danger) }

  for (l in b.lines) {
    val isCurrent = current != null && l.orderId == current.first && l.lineNo == current.second
    val open = l.status == "pending"
    TohidCard(onClick = if (open) ({ selected = l.orderId to l.lineNo }) else null, glow = isCurrent) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(l.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = c.text)
        StatusPill(
          PickText.lineStatus(l.status),
          when (l.status) {
            "picked" -> PillTone.SUCCESS
            "short" -> PillTone.WARNING
            "not_found", "removed" -> PillTone.DANGER
            else -> if (isCurrent) PillTone.INFO else PillTone.NEUTRAL
          },
        )
      }
      Text(
        "${StrPick.bin(l.bin, l.orderNo)} · ${PickText.qty(l.orderedQtyMilli, l.unit)} · " +
          PickText.location(l.location.aisle, l.location.shelf, l.location.level),
        style = MaterialTheme.typography.bodySmall, color = c.text,
      )
      if (open) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (!l.weighed) {
            TohidSecondaryButton(StrPick.picked, enabled = !busy, onClick = {
              val rest = (l.orderedQtyMilli - l.pickedQtyMilli).coerceAtLeast(0)
              act(l.orderId, "pick:${l.orderId}:${l.lineNo}:${l.pickedQtyMilli}") { PickingRepository.event(it, "pick", l.lineNo, qtyMilli = rest) }
            })
          }
          //  PCK-03: «پیدا نکردم» همیشه روی قلمِ باز
          TohidDangerButton(StrPick.notFound, enabled = !busy, onClick = {
            act(l.orderId, "not_found:${l.orderId}:${l.lineNo}") { PickingRepository.event(it, "not_found", l.lineNo) }
          })
        }
      }
    }
  }
}
