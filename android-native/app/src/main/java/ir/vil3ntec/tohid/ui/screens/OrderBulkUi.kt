package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderBulk
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.print.OrderSlipPrint

/*
 *  ORD-19 — انتخابِ چندتایی در صفِ سفارش‌ها (برنامهٔ مدیریتِ اندروید و کامپیوتر): «چاپِ برگه‌ها» و گذارِ گروهی.
 *  فقط با `bulk` ِ `/config` (پرچمِ خاموشِ `shop.orders.bulk`)؛ خاموش ⇒ هیچ دکمه و همان فهرستِ امروز.
 *  گذار فقط برای `orders.manage`، فقط گذارهای امنِ `OrderBulk.TARGETS` و فقط وقتی همهٔ انتخاب‌شده‌ها یک وضعیت دارند؛
 *  سرور هر سفارش را جدا می‌سنجد و نتیجهٔ هر کدام گفته می‌شود. تحویل و پول تک‌تک می‌مانند. رابط فقط `OrderBulkV3` ِ نسخهٔ ۳
 *  (نوار و برگهٔ رابطِ امروز بی‌راه بودند و برداشته شدند، `claude/e-rows-39m`).
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

/** گذارهای گروهیِ مجاز برای انتخابِ همین لحظه (فقط `orders.manage`) — همان قاعدهٔ `OrderBulkV3` */
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
