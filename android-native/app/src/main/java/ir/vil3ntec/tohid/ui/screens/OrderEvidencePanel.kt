package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrEvidence
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.orders.OrderEvidence
import ir.vil3ntec.tohid.data.repo.OrderEvidenceRepository
import ir.vil3ntec.tohid.data.repo.OrderEvidenceSource
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  RSK-08 — «پروندهٔ مدرک» در جزئیاتِ سفارشِ آنلاین (رابطِ امروز، `Orders.kt` ⇐ `OrderDetailSheet`). دکمه فقط با
 *  `orders.manage` و پاسخِ روشنِ سرور (`OrderEvidenceLive`)؛ برگه همهٔ بخش‌های پرونده را نشان می‌دهد و اختلاف را
 *  باز می‌کند، یادداشت می‌گذارد و می‌بندد. حالت و نوشتن فقط در `OrderEvidenceController`؛ چاپ از راهِ چاپِ
 *  گزارش (`OrderEvidencePrintAndroid`).
 */

/** مبلغِ «کوچک‌ترین واحد» ⇒ متنِ پول (همان `money` ِ برنامه) */
internal fun evidenceMoney(minor: Long): String = ir.vil3ntec.tohid.money(minor / 100.0)

/** دکمهٔ «پروندهٔ مدرک» و برگه‌اش؛ [canManage] = `permissions.manage` ِ `/config` (سرور هم می‌سنجد) */
@Composable
internal fun OrderEvidenceEntry(orderId: String, canManage: Boolean, api: ApiClient, stamp: (Long) -> String) {
  if (!canManage || !OrderEvidence.validOrderId(orderId)) return
  val src = remember(api) { OrderEvidenceRepository(api) }
  LaunchedEffect(api) { OrderEvidenceLive.ensure(src) }
  if (!OrderEvidenceLive.gate.visible) return
  var open by remember(orderId) { mutableStateOf(false) }
  Spacer(Modifier.height(8.dp))
  TohidSecondaryButton(StrEvidence.entry, onClick = { open = true }, icon = Icons.Filled.Description)
  if (open) OrderEvidenceSheet(src, orderId, stamp, onClose = { open = false })
}

@Composable
private fun OrderEvidenceSheet(src: OrderEvidenceSource, orderId: String, stamp: (Long) -> String, onClose: () -> Unit) {
  val scope = rememberCoroutineScope()
  val c = remember(orderId) { OrderEvidenceController(src, orderId, scope) }
  LaunchedEffect(orderId) { c.load() }
  val print = rememberEvidencePrint()
  val f = c.file
  val colors = Shop.colors
  TohidBottomSheet(title = StrEvidence.title(f?.order?.orderNo?.toString() ?: "…"), onDismiss = onClose) {
    SheetBody {
      when {
        c.refused -> Text(StrEvidence.refused, color = colors.muted)
        f == null -> {
          val err = c.error
          if (err != null) TohidErrorState(title = StrEvidence.loadFailed, description = err, onRetry = { c.reload() })
          else TohidLoadingState(rows = 3)
        }
        else -> {
          Text(StrEvidence.generated(stamp(f.generatedAt)), style = MaterialTheme.typography.bodySmall, color = colors.muted)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TohidSecondaryButton(StrEvidence.print, onClick = { print(f, stamp) }, icon = Icons.Filled.Print)
            TohidSecondaryButton(StrEvidence.reload, onClick = { c.reload() }, enabled = !c.loading && !c.busy, icon = Icons.Filled.Refresh)
            TohidSecondaryButton(StrEvidence.close, onClick = onClose)
          }
          Text(StrEvidence.readOnlyNote, style = MaterialTheme.typography.labelSmall, color = colors.muted2)
          DisputeBlock(c, f, stamp)
          for ((title, lines) in evidenceSections(f, stamp, ::evidenceMoney)) {
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.text)
            for (l in lines) Text(l, style = MaterialTheme.typography.bodySmall, color = colors.text)
          }
        }
      }
    }
  }
}

@Composable
private fun DisputeBlock(c: OrderEvidenceController, f: OrderEvidence.File, stamp: (Long) -> String) {
  val colors = Shop.colors
  Text(StrEvidence.sDisputes, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.text)
  val cur = f.current
  if (cur == null) Text(StrEvidence.noDispute, style = MaterialTheme.typography.bodySmall, color = colors.muted)
  else DisputeLines(f, cur, stamp, open = true)
  val d = c.draft
  if (d == null) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (cur == null) TohidButton(StrEvidence.openDispute, onClick = { c.begin(OrderEvidence.Act.OPEN) }, enabled = !c.busy)
      else {
        TohidSecondaryButton(StrEvidence.addNote, onClick = { c.begin(OrderEvidence.Act.NOTE) }, enabled = !c.busy)
        TohidSecondaryButton(StrEvidence.assign, onClick = { c.begin(OrderEvidence.Act.ASSIGN) }, enabled = !c.busy)
        TohidButton(StrEvidence.resolve, onClick = { c.begin(OrderEvidence.Act.RESOLVE) }, enabled = !c.busy)
      }
    }
  } else {
    DraftForm(c, d, f.current, f.assignees)
  }
  c.note?.let { (ok, msg) -> Text(msg, style = MaterialTheme.typography.bodySmall, color = if (ok) colors.success else colors.danger) }
  Text(StrEvidence.appendOnly, style = MaterialTheme.typography.labelSmall, color = colors.muted2)
  val past = f.past
  if (past.isNotEmpty()) {
    Text(StrEvidence.past(past.size), style = MaterialTheme.typography.labelLarge, color = colors.muted)
    for (p in past.reversed()) DisputeLines(f, p, stamp, open = false)
  }
}

@Composable
private fun DisputeLines(f: OrderEvidence.File, d: OrderEvidence.Dispute, stamp: (Long) -> String, open: Boolean) {
  val colors = Shop.colors
  val (head, lines) = disputeLines(f, d, stamp)
  Column {
    Text(head, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (open) colors.danger else colors.text)
    for (l in lines) Text(l, style = MaterialTheme.typography.bodySmall, color = colors.text)
  }
}

@Composable
private fun DraftForm(c: OrderEvidenceController, d: OrderEvidence.Draft, current: OrderEvidence.Dispute?, people: List<OrderEvidence.Assignee>) {
  val colors = Shop.colors
  when (d.act) {
    OrderEvidence.Act.OPEN -> {
      Text(StrEvidence.kindLabel, style = MaterialTheme.typography.labelLarge, color = colors.muted)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in OrderEvidence.KINDS) TohidFilterChip(label = evidenceKind(k), selected = d.choice == k, onClick = { c.edit(d.copy(choice = k)) })
      }
    }
    OrderEvidence.Act.RESOLVE -> {
      Text(StrEvidence.outcomeLabel, style = MaterialTheme.typography.labelLarge, color = colors.muted)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (o in OrderEvidence.OUTCOMES) TohidFilterChip(label = evidenceOutcome(o), selected = d.choice == o, onClick = { c.edit(d.copy(choice = o)) })
      }
    }
    OrderEvidence.Act.ASSIGN -> {
      //  §۱۹.۹ بندِ ۵: مسئول از فهرستِ سرور و مهلتِ آماده (ساعت از همین لحظه)
      Text(StrEvidence.assigneeLabel, style = MaterialTheme.typography.labelLarge, color = colors.muted)
      if (people.isEmpty()) Text(StrEvidence.noAssignees, style = MaterialTheme.typography.bodySmall, color = colors.muted)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in people) TohidFilterChip(label = p.name.ifBlank { p.userId.take(10) }, selected = d.choice == p.userId, onClick = { c.edit(d.copy(choice = p.userId)) })
      }
      Text(StrEvidence.dueLabel, style = MaterialTheme.typography.labelLarge, color = colors.muted)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (h in OrderEvidence.DUE_HOURS) TohidFilterChip(label = StrEvidence.dueIn(h), selected = d.dueHours == h,
          onClick = { c.edit(d.copy(dueAt = OrderEvidence.dueAt(System.currentTimeMillis(), h), dueHours = h)) })
      }
    }
    OrderEvidence.Act.NOTE -> Unit
  }
  val label = when (d.act) {
    OrderEvidence.Act.OPEN -> StrEvidence.reasonLabel
    OrderEvidence.Act.NOTE -> StrEvidence.noteLabel
    OrderEvidence.Act.RESOLVE -> StrEvidence.resolveLabel
    OrderEvidence.Act.ASSIGN -> StrEvidence.assignNoteLabel
  }
  TohidTextField(d.text, { c.edit(d.copy(text = it)) }, label = label, singleLine = false)
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    TohidButton(StrEvidence.send, onClick = { c.submit() }, enabled = d.ready(current) && !c.busy, busy = c.busy)
    TohidSecondaryButton(StrEvidence.cancel, onClick = { c.cancel() }, enabled = !c.busy)
  }
}
