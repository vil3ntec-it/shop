package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderAmend
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  ORD-14 — «ویرایشِ اقلام» و «تسویهٔ تفاوت» در جزئیاتِ سفارش (برنامهٔ مدیریتِ اندروید و کامپیوتر).
 *  فقط با `amend` ِ `/config` (پرچمِ خاموشِ `shop.orders.amend`)؛ سفارشِ پرداخت‌شده فقط با `amend.paid`
 *  (`shop.orders.amend_paid`). مقدارِ تازه می‌رود، نه قیمت؛ جمعِ نهایی را سرور می‌سازد و این‌جا فقط پیش‌نمایش است.
 *  سفارشِ کارمند با رضایتِ ثبت‌شده همان دم؛ سفارشِ ویترین پیشنهادی است که خودِ مشتری می‌پذیرد. تسویه فقط نقد.
 */

private fun amendReasonText(r: String): String = when (r) {
  "out_of_stock" -> StrOrderOps.amendReasonStock
  "customer_request" -> StrOrderOps.amendReasonCustomer
  "price_error" -> StrOrderOps.amendReasonPrice
  else -> StrOrderOps.amendReasonOther
}

private fun consentText(v: String): String = when (v) {
  "phone" -> StrOrderOps.amendViaPhone
  "in_person" -> StrOrderOps.amendViaInPerson
  "telegram" -> StrOrderOps.amendViaTelegram
  else -> StrOrderOps.amendViaWhatsapp
}

private fun signed(minor: Long): String = (if (minor > 0) "+" else if (minor < 0) "-" else "") + OrderText.money(kotlin.math.abs(minor))

/** دکمهٔ «ویرایشِ اقلام» در ردیفِ کارها؛ [onDone] پیامِ صفحه پس از ثبت (جزئیات تازه می‌شود) */
@Composable
internal fun OrderAmendEntry(o: OrderDto, cfg: OrdersConfigDto, repo: OrdersRepository, onDone: (String) -> Unit) {
  val a = cfg.amend ?: return
  if (!OrderAmend.can(o.status, o.paymentStatus, a.statuses, a.paid, cfg.permissions.manage)) return
  if (o.amendment?.status == "pending") return   // یک پیشنهادِ باز برای هر سفارش (`amend_pending`)
  var open by remember(o.id) { mutableStateOf(false) }
  TohidSecondaryButton(StrOrderOps.amendAction, onClick = { open = true })
  if (open) OrderAmendSheet(o, a.ttlMinutes, repo, onClose = { open = false }) { msg -> open = false; onDone(msg) }
}

@Composable
private fun OrderAmendSheet(o: OrderDto, ttlMinutes: Int, repo: OrdersRepository, onClose: () -> Unit, onDone: (String) -> Unit) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val key = remember(o.id) { OrdersRepository.newKey() }
  val texts = remember(o.id) { mutableStateMapOf<Int, String>().apply { o.lines.forEach { put(it.lineNo, OrderText.qty(it.qtyMilli)) } } }
  var reason by remember(o.id) { mutableStateOf("") }
  var note by remember(o.id) { mutableStateOf("") }
  var via by remember(o.id) { mutableStateOf("") }
  var viaNote by remember(o.id) { mutableStateOf("") }
  var busy by remember(o.id) { mutableStateOf(false) }
  var error by remember(o.id) { mutableStateOf<String?>(null) }
  val staff = OrderAmend.needsConsent(o.source)
  val lines = o.lines.map { OrderAmend.Line(it.lineNo, it.qtyMilli, it.unitPriceMinor, it.lineTotalMinor) }
  val edited = o.lines.associate { it.lineNo to texts[it.lineNo]?.let { t -> OrderDraft.scaled(t.trim(), 3) } }
  val plan = OrderAmend.plan(lines, edited, o.discountMinor, o.deliveryFeeMinor, o.totalMinor)
  val valid = plan.problem == OrderAmend.Problem.NONE && OrderAmend.valid(reason, note, via, staff)

  TohidBottomSheet(title = StrOrderOps.amendTitle(o.orderNo.toString()), onDismiss = onClose) {
    SheetBody {
      for (l in o.lines) {
        val removed = edited[l.lineNo] == 0L
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Column(Modifier.weight(1f)) {
            Text(l.name, color = c.text, fontWeight = FontWeight.SemiBold)
            Text("${OrderText.qty(l.qtyMilli)} ${l.unit} × ${OrderText.money(l.unitPriceMinor)}", style = MaterialTheme.typography.labelSmall, color = c.muted)
          }
          TohidTextField(
            texts[l.lineNo].orEmpty(), { texts[l.lineNo] = it.take(12) }, label = StrOrderOps.amendQty,
            modifier = Modifier.width(110.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          )
          TohidFilterChip(
            label = if (removed) StrOrderOps.amendKeep else StrOrderOps.amendRemove, selected = removed,
            onClick = { texts[l.lineNo] = if (removed) OrderText.qty(l.qtyMilli) else "0" },
          )
        }
      }
      val problem = when (plan.problem) {
        OrderAmend.Problem.NONE -> null
        OrderAmend.Problem.NO_CHANGE -> StrOrderOps.amendNoChange
        OrderAmend.Problem.BAD_QTY -> StrOrderOps.amendBadQty
        OrderAmend.Problem.ALL_REMOVED -> StrOrderOps.amendAllRemoved
        OrderAmend.Problem.DISCOUNT -> StrOrderOps.amendDiscount
      }
      if (problem != null) Text(problem, style = MaterialTheme.typography.bodySmall, color = c.muted)
      else Text(StrOrderOps.amendPreview(OrderText.money(plan.totalMinor), signed(plan.differenceMinor)), color = c.text, fontWeight = FontWeight.Bold)
      if (o.paymentStatus == "paid") Text(StrOrderOps.amendPaidInfo, style = MaterialTheme.typography.bodySmall, color = c.warning)
      Text(StrOrderOps.amendReason, style = MaterialTheme.typography.labelLarge, color = c.muted)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in OrderAmend.REASONS) TohidFilterChip(label = amendReasonText(r), selected = reason == r, onClick = { reason = if (reason == r) "" else r })
      }
      TohidTextField(note, { note = it.take(300) }, label = StrOrderOps.amendNote, singleLine = false)
      if (staff) {
        Text(StrOrderOps.amendConsent, style = MaterialTheme.typography.labelLarge, color = c.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          for (v in OrderAmend.CONSENT_VIA) TohidFilterChip(label = consentText(v), selected = via == v, onClick = { via = v })
        }
        TohidTextField(viaNote, { viaNote = it.take(300) }, label = StrOrderOps.amendConsentNote)
      } else {
        Text(StrOrderOps.amendLinkInfo(ttlMinutes), style = MaterialTheme.typography.bodySmall, color = c.muted)
      }
      error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger) }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TohidButton(StrOrderOps.amendSend, enabled = valid && !busy, busy = busy, onClick = {
          if (!busy) {
            busy = true
            error = null
            scope.launch {
              repo.amend(o.id, key, plan.changes.map { it.lineNo to it.qtyMilli }, reason, note, if (staff) via else "", if (staff) viaNote else "")
                .onSuccess { onDone(if (staff) StrOrderOps.amendDone else StrOrderOps.amendProposed) }
                .onFailure { f -> error = if (f.retryable) StrOrderOps.amendRetry else f.userMessage }
              busy = false
            }
          }
        })
        TohidSecondaryButton(StrOrderOps.amendCancel, onClick = onClose)
      }
    }
  }
}

/** کارتِ آخرین ویرایش زیرِ کارها: حال، جمع، و برای پرداخت‌شده بدهی و «ثبتِ تسویهٔ نقدی»؛ پیشنهادِ باز ⇒ «پس گرفتن» */
@Composable
internal fun OrderAmendCard(o: OrderDto, cfg: OrdersConfigDto, repo: OrdersRepository, stamp: (Long) -> String, onDone: (String) -> Unit) {
  if (cfg.amend == null) return
  val a = o.amendment ?: return
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var busy by remember(a.id) { mutableStateOf(false) }
  val key = remember(a.id) { OrdersRepository.newKey() }
  fun run(call: suspend () -> ApiResult<Unit>, ok: String) {
    if (busy) return
    busy = true
    scope.launch {
      call().onSuccess { onDone(ok) }.onFailure { f -> onDone(f.userMessage) }
      busy = false
    }
  }
  Spacer(Modifier.height(10.dp))
  Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.surface2).padding(12.dp)) {
    val state = when (a.status) {
      "pending" -> StrOrderOps.amendPending(a.expiresAt?.let(stamp).orEmpty())
      "applied" -> StrOrderOps.amendApplied
      "rejected" -> StrOrderOps.amendRejected
      "withdrawn" -> StrOrderOps.amendWithdrawn
      "expired" -> StrOrderOps.amendExpired
      else -> StrOrderOps.amendStale
    }
    Text("${StrOrderOps.amendCardTitle} — $state", color = c.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    Text(StrOrderOps.amendTotals(OrderText.money(a.prevTotalMinor), OrderText.money(a.totalMinor)), style = MaterialTheme.typography.bodySmall, color = c.muted)
    val s = a.settlement
    if (s != null && s.kind != "none" && s.amountMinor > 0) {
      val due = if (s.kind == "refund") StrOrderOps.amendRefundDue(OrderText.money(s.amountMinor)) else StrOrderOps.amendChargeDue(OrderText.money(s.amountMinor))
      val settled = s.settledAt?.let { StrOrderOps.amendSettled(stamp(it)) } ?: StrOrderOps.amendNotSettled
      Text("$due · $settled", style = MaterialTheme.typography.bodySmall, color = if (s.settledAt == null) c.warning else c.success)
    }
    val settle = s != null && OrderAmend.canSettle(a.status, s.kind, s.amountMinor, s.settledAt, cfg.permissions.refund)
    val withdraw = OrderAmend.canWithdraw(a.status, cfg.permissions.manage)
    if (settle || withdraw) {
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (settle) TohidButton(StrOrderOps.amendSettle, enabled = !busy, onClick = { run({ repo.settleAmend(o.id, a.id, key, "") }, StrOrderOps.amendSettleDone) })
        if (withdraw) TohidSecondaryButton(StrOrderOps.amendWithdraw, enabled = !busy, onClick = { run({ repo.withdrawAmend(o.id, a.id) }, StrOrderOps.amendWithdrawn) })
      }
    }
  }
}

/** عنوانِ رویدادهای `amend_*` در خطِ زمانی، یا null (پرچمِ خاموش [on] یا رویدادِ دیگر: همان نوشتهٔ امروز) */
internal fun orderAmendEventTitle(e: OrderEventDto, on: Boolean): String? {
  if (!on || e.kind != "note" || !e.detail.type.startsWith(OrderAmend.EVENT_PREFIX)) return null
  return when (e.detail.type) {
    "amend_proposed" -> StrOrderOps.amendEventProposed
    "amend_applied" -> StrOrderOps.amendEventApplied
    "amend_settled" -> StrOrderOps.amendEventSettled
    else -> StrOrderOps.amendEventClosed
  }
}
