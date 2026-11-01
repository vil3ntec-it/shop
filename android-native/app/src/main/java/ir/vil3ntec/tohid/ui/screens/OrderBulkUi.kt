package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderBulk
import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.print.OrderSlipPrint
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  ORD-19 — انتخابِ چندتایی در صفِ سفارش‌ها (برنامهٔ مدیریتِ اندروید و کامپیوتر): «چاپِ برگه‌ها» و گذارِ گروهی.
 *  فقط با `bulk` ِ `/config` (پرچمِ خاموشِ `shop.orders.bulk`)؛ خاموش ⇒ هیچ دکمه و همان فهرستِ امروز.
 *  گذار فقط برای `orders.manage`، فقط گذارهای امنِ `OrderBulk.TARGETS` و فقط وقتی همهٔ انتخاب‌شده‌ها یک وضعیت دارند؛
 *  سرور هر سفارش را جدا می‌سنجد و نتیجهٔ هر کدام گفته می‌شود. تحویل و پول تک‌تک می‌مانند.
 */

@Stable
internal class OrderBulkState {
  var on by mutableStateOf(false)
  var ids by mutableStateOf<Set<String>>(emptySet())

  /** کلیک روی سفارش: در حالتِ انتخاب ⇒ انتخاب یا برداشتن (true)؛ بیرونِ آن false (جزئیات باز می‌شود) */
  fun tap(id: String): Boolean {
    if (!on) return false
    ids = OrderBulk.toggle(ids, id)
    return true
  }
}

/** گذارهای گروهیِ مجاز برای انتخابِ همین لحظه (فقط `orders.manage`) — همان در هر دو رابط */
internal fun OrderBulkState.targets(cfg: OrdersConfigDto, selected: List<OrderDto>): List<String> =
  if (cfg.permissions.manage) OrderBulk.targets(selected.map { it.status to orderPickActions(it, cfg).first }) else emptyList()

/** برگه جزئیات (اقلام) می‌خواهد؛ کارتِ فهرست ندارد ⇒ هر سفارش تازه از سرور */
private suspend fun fullOrders(repo: OrdersRepository, ids: List<String>): List<OrderDto> =
  ids.mapNotNull { (repo.get(it) as? ApiResult.Success)?.value }

/** «چاپِ برگه‌ها» ِ انتخاب‌شده‌ها ⇒ پیامِ صفحه */
internal suspend fun OrderBulkState.print(repo: OrdersRepository, printer: OrderSlipPrint.Bulk, selected: List<OrderDto>): String {
  val got = fullOrders(repo, selected.map { it.id })
  return printer.print(got) + if (got.size < selected.size) "\n" + StrOrderOps.bulkFetchFailed(selected.size - got.size) else ""
}

/**
 *  گذارِ گروهی: سرور هر سفارش را جدا می‌سنجد؛ پیامِ نتیجه (با چرایی تا سه ناموفق) و برگهٔ «قبول» (ORD-10) با
 *  [onMessage]. انجام‌شده‌ها از انتخاب برداشته می‌شوند. `true` ⇒ سرور پاسخ داد.
 */
internal suspend fun OrderBulkState.send(
  repo: OrdersRepository,
  printer: OrderSlipPrint.Bulk,
  items: List<OrderDto>,
  selected: List<OrderDto>,
  to: String,
  reason: String,
  note: String,
  ready: Int?,
  onMessage: (String) -> Unit,
  onChanged: () -> Unit,
): Boolean {
  val from = selected.firstOrNull()?.status ?: return false
  var ok = false
  repo.bulkTransition(selected.map { it.id }, to, from, reason, note, ready)
    .onSuccess { out ->
      ok = true
      val results = out.results.map { OrderBulk.Result(it.id, it.ok, it.unchanged, it.orderNo, it.code) }
      val sum = OrderBulk.summarize(results)
      val why = out.results.filter { !it.ok }.take(3).joinToString("\n") { r ->
        val no = items.firstOrNull { it.id == r.id }?.orderNo ?: r.orderNo
        StrOrderOps.bulkFailed(no.toString(), when (r.code) {
          "status_changed" -> StrOrderOps.bulkWhyChanged
          "order_not_found" -> StrOrderOps.bulkWhyMissing
          else -> r.message.ifBlank { r.code }
        })
      }
      onMessage(StrOrderOps.bulkResult(sum.changed, sum.unchanged, sum.failed.size) + if (why.isEmpty()) "" else "\n$why")
      ids = OrderBulk.remaining(ids, results)
      onChanged()
      //  ORD-10: «یک قبول، یک برگه» برای هر سفارشی که همین حالا پذیرفته شد (تنظیمِ خاموش ⇒ هیچ پرسشی)
      if (to == "accepted") {
        printer.accepted(from, results.filter { it.ok && !it.unchanged }.map { it.id }) { id -> fullOrders(repo, listOf(id)).firstOrNull() }?.let(onMessage)
      }
    }
    .onFailure { f -> onMessage(f.userMessage) }
  return ok
}

/** حالتِ انتخابِ صفحه؛ عوض شدنِ برگه انتخاب را پاک می‌کند، پرچمِ خاموش حالت را می‌بندد */
@Composable
internal fun rememberOrderBulk(cfg: OrdersConfigDto, tab: String): OrderBulkState {
  val s = remember { OrderBulkState() }
  LaunchedEffect(tab, cfg.bulk) {
    s.ids = emptySet()
    if (!cfg.bulk) s.on = false
  }
  return s
}

@Composable
internal fun OrderBulkToggle(s: OrderBulkState, cfg: OrdersConfigDto) {
  if (!cfg.bulk) return
  TextButton(onClick = { s.on = !s.on; if (!s.on) s.ids = emptySet() }) {
    Text(if (s.on) StrOrderOps.bulkDone else StrOrderOps.bulkSelect, color = Shop.colors.primary)
  }
}

/** ردیفِ فهرست با جعبهٔ انتخاب در حالتِ انتخاب؛ بیرونِ آن همان ردیفِ امروز */
@Composable
internal fun OrderBulkRow(s: OrderBulkState, id: String, content: @Composable () -> Unit) {
  if (!s.on) {
    content()
    return
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked = id in s.ids, onCheckedChange = { s.tap(id) })
    Box(Modifier.weight(1f)) { content() }
  }
}

/**
 *  نوارِ کارِ گروهی بالای فهرست. [printer] چاپگرِ همین دستگاه (`OrderSlipPrint.Bulk`)، [onMessage] پیامِ صفحه،
 *  [onChanged] فهرست و شمارش‌ها تازه شوند.
 */
@Composable
internal fun OrderBulkBar(
  s: OrderBulkState,
  cfg: OrdersConfigDto,
  items: List<OrderDto>,
  repo: OrdersRepository,
  printer: OrderSlipPrint.Bulk,
  onMessage: (String) -> Unit,
  onChanged: () -> Unit,
) {
  if (!cfg.bulk || !s.on) return
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var confirm by remember { mutableStateOf<String?>(null) }
  val selected = items.filter { it.id in s.ids }
  val targets = s.targets(cfg, selected)

  fun send(to: String, reason: String, note: String, ready: Int?) {
    if (busy || selected.isEmpty()) return
    busy = true
    scope.launch {
      //  مثلِ پیش: برگهٔ تأیید پیش از تازه‌شدنِ فهرست (و چاپِ برگهٔ قبول) بسته می‌شود
      s.send(repo, printer, items, selected, to, reason, note, ready, onMessage) { confirm = null; onChanged() }
      confirm = null
      busy = false
    }
  }

  Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      StatusPill(StrOrderOps.bulkCount(selected.size), PillTone.NEUTRAL)
      TextButton(onClick = { s.ids = OrderBulk.all(s.ids, items.map { it.id }) }) { Text(StrOrderOps.bulkAll, color = c.primary) }
      TextButton(onClick = { s.ids = emptySet() }) { Text(StrOrderOps.bulkNone, color = c.primary) }
      TohidSecondaryButton(StrOrderOps.bulkPrint(selected.size), enabled = selected.isNotEmpty() && !busy, onClick = {
        if (!busy) {
          busy = true
          scope.launch {
            onMessage(s.print(repo, printer, selected))
            busy = false
          }
        }
      })
      for (to in targets) {
        val label = StrOrderOps.bulkMove(OrderText.action(to), selected.size)
        if (to in OrderText.DANGER) TohidDangerButton(label, onClick = { confirm = to }, enabled = !busy)
        else TohidButton(label, onClick = { confirm = to }, enabled = !busy)
      }
    }
    when {
      selected.isEmpty() -> Text(StrOrderOps.bulkPick(OrderBulk.MAX), style = MaterialTheme.typography.labelSmall, color = c.muted)
      cfg.permissions.manage && targets.isEmpty() && selected.map { it.status }.distinct().size > 1 ->
        Text(StrOrderOps.bulkMixed, style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
  }
  val to = confirm
  val from = selected.firstOrNull()?.status
  if (to != null && from != null) {
    OrderBulkConfirm(to, from, selected.size, busy, onDismiss = { confirm = null }) { reason, note, ready -> send(to, reason, note, ready) }
  }
}

/** تأییدِ گذارِ گروهی: دلیل (همان فهرستِ گذارِ تکی)، زمانِ آماده برای «قبول» */
@Composable
private fun OrderBulkConfirm(to: String, from: String, n: Int, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String, String, Int?) -> Unit) {
  val c = Shop.colors
  val choices = remember(from, to) { OrderStates.reasonChoices(from, to) }
  var reason by remember(to) { mutableStateOf(if (choices.size == 1) choices[0] else "") }
  var note by remember(to) { mutableStateOf("") }
  var ready by remember(to) { mutableStateOf<Int?>(15) }
  val need = OrderStates.needsReason(to)
  val valid = !need || (reason.isNotEmpty() && (reason != "other" || note.isNotBlank()))
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text(StrOrderOps.bulkConfirmTitle(OrderText.action(to), n), color = c.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (need) {
          Text(StrOrderOps.bulkReason, style = MaterialTheme.typography.labelLarge, color = c.muted)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in choices) TohidFilterChip(label = OrderText.reason(r), selected = reason == r, onClick = { reason = r })
          }
          TohidTextField(note, { if (it.length <= 300) note = it }, label = StrOrderOps.bulkNote, singleLine = false)
        }
        if (to == "accepted") {
          Text(StrOrderOps.bulkReady, style = MaterialTheme.typography.labelLarge, color = c.muted)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TohidFilterChip(label = StrOrderOps.bulkNoTime, selected = ready == null, onClick = { ready = null })
            for (m in OrderText.READY_MINUTES) TohidFilterChip(label = StrOrderOps.bulkMinutes(m), selected = ready == m, onClick = { ready = m })
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(reason, note, if (to == "accepted") ready else null) }, enabled = valid && !busy) { Text(StrOrderOps.bulkConfirm) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(StrOrderOps.bulkCancel) } },
  )
}
