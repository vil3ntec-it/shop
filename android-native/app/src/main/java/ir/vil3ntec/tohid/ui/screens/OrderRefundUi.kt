package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDevices
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrOrderPages
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.data.OrderFulfillment
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  بازپرداختِ سفارشِ تحویل‌شده در صفحهٔ «سفارش‌ها» (RET-11): سندِ برگشت خودش می‌نشیند
 *  (`OrdersLive.postById`/`reconcile` ⇐ `OrderFulfillment.refund`)؛ این‌جا فقط
 *  «کالا به انبار برگشت؟» و هشدارِ «ننشست» است. پیش‌فرض «نه»: کالای سفارشِ تحویل‌شده
 *  پیشِ مشتری است و فقط قلمی که کاربر می‌زند به موجودی برمی‌گردد.
 */

/** پرسشِ «کالا به انبار برگشت؟» برای هر قلم — همان پرسشِ پنجرهٔ برگشتِ صندوق */
@Composable
private fun RestockChecks(lines: List<Pair<Int, String>>, chosen: Set<Int>, onToggle: (Int) -> Unit) {
  val c = Shop.colors
  Text(StrOrderPages.restockAsk, style = MaterialTheme.typography.labelLarge, color = c.muted)
  Text(
    StrOrderPages.restockNote,
    style = MaterialTheme.typography.bodySmall, color = c.muted,
  )
  lines.forEach { (lineNo, name) ->
    Row(
      Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(selected = lineNo in chosen, role = Role.Checkbox) { onToggle(lineNo) },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Checkbox(checked = lineNo in chosen, onCheckedChange = null)
      Spacer(Modifier.width(6.dp))
      Text(name, color = c.text)
    }
  }
}

/**
 *  «کالا به انبار برگشت؟» — همان دمِ بازپرداخت در همین دستگاه، یا دیرتر برای بازپرداختی که بی پرسش
 *  در دفتر نشست (سرور، دستگاهِ دیگر). «بعداً» ⇒ پرسش می‌ماند.
 */
@Composable
internal fun RestockDialog(
  order: OrderDto,
  lines: List<Pair<Int, String>>,
  busy: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (Set<Int>) -> Unit,
) {
  val c = Shop.colors
  var chosen by remember(order.id) { mutableStateOf<Set<Int>>(emptySet()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text(StrOrderPages.restockTitle(order.orderNo), color = c.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        RestockChecks(lines, chosen) { n -> chosen = if (n in chosen) chosen - n else chosen + n }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(chosen) }, enabled = !busy) {
        Text(if (chosen.isEmpty()) StrOrderPages.restockNone else StrOrderPages.restockDo)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(StrDevices.later) } },
  )
}

/** هشدارهای فهرستِ «سفارش‌ها»: «برگشتِ کالا؟» ِ بازپرداخت‌های نشسته، و بازپرداختی که در دفتر ننشست */
@Composable
internal fun RefundListNotices(ledger: ShopData, cfg: OrdersConfigDto, onOpen: (String) -> Unit, onRetry: () -> Unit) {
  val pending = remember(ledger) { OrderFulfillment.restockPendingSales(ledger) }
  if (pending.isNotEmpty() && cfg.permissions.manage) {
    Spacer(Modifier.height(8.dp))
    FulfillNotice(
      title = StrOrderPages.restockPending(pending.size),
      body = pending.take(3).joinToString("\n") { StrOrderPages.refundLine(it.orderNo ?: 0L, it.invoiceLabel() ?: "—") },
      action = StrHome.actionCheck,
      onAction = { pending.first().orderId?.let(onOpen) },
    )
  }
  val issues = OrdersLive.refundIssues.value
  if (issues.isNotEmpty()) {
    Spacer(Modifier.height(8.dp))
    FulfillNotice(
      title = StrOrderPages.refundUnposted(issues.size),
      body = issues.take(3).joinToString("\n") { it.message },
      action = Str.again,
      onAction = onRetry,
    )
  }
}

/** هشدارهای جزئیاتِ یک سفارش — همان دو هشدار، فقط برای [o] */
@Composable
internal fun RefundDetailNotices(ledger: ShopData, cfg: OrdersConfigDto, o: OrderDto, onRestock: () -> Unit, onRetry: () -> Unit) {
  if (OrderFulfillment.restockPending(ledger, o.id).isNotEmpty()) {
    Spacer(Modifier.height(8.dp))
    val able = cfg.permissions.manage && OrdersLive.canRefund(cfg, o)
    FulfillNotice(
      title = StrOrderPages.restockPosted,
      body = if (able) StrOrderPages.restockPostedNote
      else OrdersLive.SKIPPED_REFUND_NOTE,
      action = if (able) StrOrderPages.restockAction else null,
      onAction = onRestock,
    )
  }
  OrdersLive.refundIssues.value.firstOrNull { it.orderId == o.id }?.let { issue ->
    Spacer(Modifier.height(8.dp))
    FulfillNotice(title = StrOrderPages.refundNotPosted, body = issue.message, action = Str.again, onAction = onRetry)
  }
}

/**
 *  پس از گذارِ «بازپرداخت» در همین دستگاه (یا «دوباره»): سندِ برگشتِ همان فروش با [post]
 *  (`OrdersLive.postById`، پس از پرسیدن از سرور). نتیجه گفته می‌شود.
 */
internal suspend fun postRefundNotice(snackbar: SnackbarHostState, o: OrderDto, post: suspend () -> OrdersLive.Posted) {
  when (val r = post()) {
    OrdersLive.Posted.Saved -> snackbar.showSnackbar(StrOrderPages.refundPosted(o.orderNo))
    OrdersLive.Posted.Already -> Unit
    is OrdersLive.Posted.Skipped -> snackbar.showSnackbar(r.message)
    is OrdersLive.Posted.Failed -> snackbar.showSnackbar(r.message)
    is OrdersLive.Posted.Review -> snackbar.showSnackbar(r.message)
  }
}

/** جوابِ «برگشتِ کالا؟» ِ دیرتر ⇒ `OrdersLive.restock`؛ نتیجه گفته می‌شود */
internal suspend fun restockNotice(snackbar: SnackbarHostState, chosen: Set<Int>, save: suspend () -> String?) {
  val err = save()
  snackbar.showSnackbar(err ?: if (chosen.isEmpty()) StrOrderPages.restockNoneSaved else StrOrderPages.restockSaved)
}
