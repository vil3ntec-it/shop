package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.Settlement
import ir.vil3ntec.tohid.core.delivery.SettlementPreview
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.DraftTag
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersModel
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.launch

/**
 *  تسویهٔ پایانِ روز (`delivery/couriers/{id}/settle`، DLV-12/S40/DEL-02): تحویل‌های تسویه‌نشده، موردِ
 *  انتظار و گرفته، اختلافِ مشتری (جدا)، کرایه‌ها و «از همین نقد»، «پیک باید تحویل دهد»، گفتهٔ پیک و
 *  شمارشِ مدیر، کسری/اضافه و دلیلِ لازم — جمع‌ها فقط از `SettlementMath`. یک کلیدِ یکتا برای سند؛
 *  «فهرست عوض شد» ⇐ پیش‌نمایش و کلیدِ تازه (همان رفتارِ پیشین).
 */
@Composable
internal fun SettlementSheet(cr: CourierRow, repo: DeliveryRepository, onDismiss: () -> Unit, onDone: (Settlement) -> Unit, start: SettlementPreview? = null) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  var preview by remember { mutableStateOf(start) }
  var loadErr by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableIntStateOf(0) }
  var key by remember { mutableStateOf(DeliveryRepository.newKey()) }
  var s by remember { mutableStateOf(DeliveryForms.Settle()) }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(reload) {
    if (start != null && reload == 0) return@LaunchedEffect
    repo.settlementPreview(cr.id).onSuccess { preview = it; loadErr = null; key = DeliveryRepository.newKey() }.onFailure { loadErr = it.userMessage }
  }

  V3Sheet(onDismiss = onDismiss, title = StrOnline.dlSettleTitle(cr.name)) { _ ->
    val p = preview
    when {
      p == null && loadErr != null -> V3EmptyState(Icons.Outlined.CloudOff, StrOnline.dlPreviewFailed, body = loadErr, action = StrKit.retry, onAction = { reload++ })
      p == null -> V3Skeleton(rows = 3)
      p.rows.isEmpty() -> V3EmptyState(Icons.Outlined.Payments, StrOnline.dlNothingToSettle)
      else -> {
        val t = DeliveryForms.totals(p.rows, s)
        val handed = DeliveryForms.handedMinor(s)
        val received = DeliveryForms.receivedMinor(s)
        Text(StrOnline.dlDeliveries(p.rows.size), style = V3Type.subhead, color = c.ink)
        Spacer(Modifier.height(8.dp))
        V3Card(padding = PaddingValues(vertical = 4.dp)) {
          p.rows.forEachIndexed { i, r ->
            if (i > 0) V3Divider(start = 16.dp)
            V3ListRow(
              OnlineFmt.orderNo(r.orderNo), subtitle = r.deliveredAt?.let(OrdersModel::stamp) ?: "—", tile = false, chevron = false,
              value = OnlineFmt.afn(r.cashCollectedMinor),
              trailing = if (r.collectionDiffMinor != 0L) ({ DraftTag(StrOnline.dlDiff(OrderText.money(r.collectionDiffMinor)), tone = V3Tone.WARNING) }) else null,
            )
          }
        }
        Spacer(Modifier.height(12.dp))
        V3Card(padding = PaddingValues(vertical = 4.dp)) {
          Sum(StrOnline.dlExpectedFromCustomers, OnlineFmt.afn(t.expectedCashMinor))
          Sum(StrOnline.dlCourierGot, OnlineFmt.afn(t.collectedCashMinor))
          if (t.collectionDiffMinor != 0L) Sum(StrOnline.dlCustomerDiff, OnlineFmt.afn(t.collectionDiffMinor), tone = PhoneV3Roles.status(c, V3Status.WARNING).fg)
          Sum(StrOnline.dlFares, OnlineFmt.afn(t.fareTotalMinor))
          V3Divider()
          V3ToggleRow(StrOnline.dlFareFromCash, s.farePaid, { s = s.copy(farePaid = it) }, icon = Icons.Outlined.Payments, hue = V3Hue.MINT)
          V3Divider()
          Sum(StrOnline.dlHandoverDue, OnlineFmt.afn(t.handoverDueMinor), strong = true)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          V3TextField(
            s.handed, { s = s.copy(handed = it.take(DeliveryForms.NUMBER_MAX)) }, StrOnline.dlHandedClaim, kind = V3FieldKind.MONEY,
            error = if (s.handed.isNotBlank() && handed == null) StrOnline.dlBadNumber else null, modifier = Modifier.fillMaxWidth(),
          )
          V3TextField(
            s.received, { s = s.copy(received = it.take(DeliveryForms.NUMBER_MAX)) }, StrOnline.dlManagerCount, kind = V3FieldKind.MONEY, required = true,
            error = if (s.received.isNotBlank() && received == null) StrOnline.dlBadNumber else null, modifier = Modifier.fillMaxWidth(),
          )
          if (received != null) {
            when {
              t.shortageMinor > 0 -> V3Message(V3Tone.ERROR, StrOnline.dlShort + ": " + OnlineFmt.afn(t.shortageMinor))
              t.excessMinor > 0 -> V3Message(V3Tone.WARNING, StrOnline.dlOver + ": " + OnlineFmt.afn(t.excessMinor))
              else -> V3Message(V3Tone.SUCCESS, StrOnline.dlDiffLabel + ": " + StrOnline.dlNone)
            }
            if (handed != null && t.handedOverMismatch) V3Message(V3Tone.WARNING, StrOnline.dlClaimMismatch)
          }
          val needReason = DeliveryForms.settleNeedsReason(p.rows, s)
          V3TextField(s.reason, { s = s.copy(reason = it.take(DeliveryForms.REASON_MAX)) }, if (needReason) StrOnline.dlDiffReasonReq else StrOnline.dlReasonOpt, required = needReason, modifier = Modifier.fillMaxWidth())
          V3TextField(s.note, { s = s.copy(note = it.take(DeliveryForms.NOTE_MAX)) }, StrOnline.dlNoteOpt, singleLine = false, modifier = Modifier.fillMaxWidth())
        }
        err?.let { Spacer(Modifier.height(12.dp)); V3Message(V3Tone.ERROR, it, Modifier.v3Shake(it)) }
        Spacer(Modifier.height(20.dp))
        V3Button(
          StrOnline.dlConfirmSettle,
          {
            busy = true
            err = null
            scope.launch {
              val body = DeliveryWire.settleJson(
                clientKey = key, assignmentIds = p.rows.map { it.assignmentId },
                handedOverMinor = handed ?: (received ?: 0L), receivedMinor = received ?: 0L,
                farePaidFromCash = s.farePaid, reason = s.reason, note = s.note,
              )
              repo.settle(cr.id, body)
                .onSuccess { onDone(it.settlement) }
                .onFailure { f ->
                  err = f.userMessage
                  //  فهرست عوض شده (تحویلِ تازه یا تسویه از دستگاهِ دیگر) ⇒ پیش‌نمایشِ تازه، کلیدِ تازه
                  if (f.code == "settlement_changed" || f.code == "idempotency_key_reused") reload++
                }
              busy = false
            }
          },
          Modifier.fillMaxWidth(),
          enabled = DeliveryForms.settleValid(p.rows, s) && !busy,
          loading = busy,
          haptic = true,
        )
      }
    }
  }
}

/** یک خطِ جمع: برچسب و مبلغ (پررنگ برای «باید تحویل دهد») */
@Composable
internal fun Sum(label: String, value: String, strong: Boolean = false, tone: Color? = null) {
  val c = PhoneV3.colors
  V3ListRow(
    label, tile = false, chevron = false, minHeight = 44.dp,
    trailing = { Text(value, style = if (strong) V3Type.headline else V3Type.callout, color = tone ?: c.ink) },
  )
}
