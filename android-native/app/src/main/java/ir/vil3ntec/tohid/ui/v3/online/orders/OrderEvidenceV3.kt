package ir.vil3ntec.tohid.ui.v3.online.orders

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrEvidence
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.orders.OrderEvidence
import ir.vil3ntec.tohid.data.repo.OrderEvidenceRepository
import ir.vil3ntec.tohid.data.repo.OrderEvidenceSource
import ir.vil3ntec.tohid.ui.screens.OrderEvidenceController
import ir.vil3ntec.tohid.ui.screens.OrderEvidenceLive
import ir.vil3ntec.tohid.ui.screens.disputeLines
import ir.vil3ntec.tohid.ui.screens.evidenceKind
import ir.vil3ntec.tohid.ui.screens.evidenceMoney
import ir.vil3ntec.tohid.ui.screens.evidenceOutcome
import ir.vil3ntec.tohid.ui.screens.evidenceSections
import ir.vil3ntec.tohid.ui.screens.rememberEvidencePrint
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  RSK-08 — «پروندهٔ مدرک» در جزئیاتِ سفارشِ نسخهٔ ۳. همان شرط و همان حالتِ رابطِ امروز (`OrderEvidenceEntry` ِ برداشته‌شده،
 *  `OrderEvidenceController`): فقط `orders.manage` و پاسخِ روشنِ سرور؛ این‌جا فقط کشیده می‌شود.
 */

@Composable
internal fun OrderEvidenceEntryV3(orderId: String, canManage: Boolean, api: ApiClient) {
  if (!canManage || !OrderEvidence.validOrderId(orderId)) return
  val src = remember(api) { OrderEvidenceRepository(api) }
  LaunchedEffect(api) { OrderEvidenceLive.ensure(src) }
  if (!OrderEvidenceLive.gate.visible) return
  var open by remember(orderId) { mutableStateOf(false) }
  V3Button(
    StrEvidence.entry, { open = true }, Modifier.fillMaxWidth().padding(top = 12.dp),
    kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Description,
  )
  if (open) EvidenceSheet(src, orderId, onClose = { open = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EvidenceSheet(src: OrderEvidenceSource, orderId: String, onClose: () -> Unit) {
  val scope = rememberCoroutineScope()
  val c = remember(orderId) { OrderEvidenceController(src, orderId, scope) }
  LaunchedEffect(orderId) { c.load() }
  val print = rememberEvidencePrint()
  val stamp: (Long) -> String = OrdersModel::stamp
  val f = c.file
  val ink = PhoneV3.colors.ink
  val muted = PhoneV3.colors.inkMuted
  V3Sheet(onDismiss = onClose, title = StrEvidence.title(f?.order?.orderNo?.toString() ?: "…")) { _ ->
    when {
      c.refused -> V3Message(V3Tone.INFO, StrEvidence.refused)
      f == null -> {
        val err = c.error
        if (err != null) V3Message(V3Tone.ERROR, StrEvidence.loadFailed, body = err, action = StrEvidence.reload, onAction = { c.reload() })
        else V3Skeleton(rows = 3)
      }
      else -> {
        Text(StrEvidence.generated(stamp(f.generatedAt)), style = V3Type.subhead, color = muted)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          V3Button(StrEvidence.print, { print(f, stamp) }, kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Print)
          V3Button(StrEvidence.reload, { c.reload() }, kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.Refresh, enabled = !c.loading && !c.busy)
        }
        Spacer(Modifier.height(8.dp))
        Text(StrEvidence.readOnlyNote, style = V3Type.footnote, color = muted)
        V3Section(StrEvidence.sDisputes, footer = StrEvidence.appendOnly) {
          val cur = f.current
          if (cur == null) Text(StrEvidence.noDispute, style = V3Type.callout, color = muted, modifier = Modifier.padding(12.dp))
          else Lines(disputeLines(f, cur, stamp))
          val d = c.draft
          if (d == null) {
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (cur == null) V3Button(StrEvidence.openDispute, { c.begin(OrderEvidence.Act.OPEN) }, enabled = !c.busy)
              else {
                V3Button(StrEvidence.addNote, { c.begin(OrderEvidence.Act.NOTE) }, kind = V3ButtonKind.SECONDARY, enabled = !c.busy)
                V3Button(StrEvidence.assign, { c.begin(OrderEvidence.Act.ASSIGN) }, kind = V3ButtonKind.SECONDARY, enabled = !c.busy)
                V3Button(StrEvidence.resolve, { c.begin(OrderEvidence.Act.RESOLVE) }, enabled = !c.busy)
              }
            }
          } else {
            DraftV3(c, d, cur, f.assignees)
          }
        }
        c.note?.let { (ok, msg) ->
          Spacer(Modifier.height(8.dp))
          V3Message(if (ok) V3Tone.SUCCESS else V3Tone.ERROR, msg)
        }
        val past = f.past
        if (past.isNotEmpty()) {
          V3Section(StrEvidence.past(past.size)) { for (p in past.reversed()) Lines(disputeLines(f, p, stamp)) }
        }
        for ((title, lines) in evidenceSections(f, stamp, ::evidenceMoney)) {
          V3Section(title) {
            for (l in lines) Text(l, style = V3Type.callout, color = ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun Lines(block: Pair<String, List<String>>) {
  Text(block.first, style = V3Type.headline, color = PhoneV3.colors.ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
  for (l in block.second) Text(l, style = V3Type.callout, color = PhoneV3.colors.ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftV3(c: OrderEvidenceController, d: OrderEvidence.Draft, current: OrderEvidence.Dispute?, people: List<OrderEvidence.Assignee>) {
  val muted = PhoneV3.colors.inkMuted
  val pad = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
  when (d.act) {
    OrderEvidence.Act.OPEN -> {
      Text(StrEvidence.kindLabel, style = V3Type.subhead, color = muted, modifier = pad)
      FlowRow(pad, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (k in OrderEvidence.KINDS) V3Chip(evidenceKind(k), selected = d.choice == k, onClick = { c.edit(d.copy(choice = k)) })
      }
    }
    OrderEvidence.Act.RESOLVE -> {
      Text(StrEvidence.outcomeLabel, style = V3Type.subhead, color = muted, modifier = pad)
      FlowRow(pad, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in OrderEvidence.OUTCOMES) V3Chip(evidenceOutcome(o), selected = d.choice == o, onClick = { c.edit(d.copy(choice = o)) })
      }
    }
    OrderEvidence.Act.ASSIGN -> {
      //  §۱۹.۹ بندِ ۵: مسئول از فهرستِ سرور و مهلتِ آماده (ساعت از همین لحظه)
      Text(StrEvidence.assigneeLabel, style = V3Type.subhead, color = muted, modifier = pad)
      if (people.isEmpty()) Text(StrEvidence.noAssignees, style = V3Type.footnote, color = muted, modifier = pad)
      FlowRow(pad, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in people) V3Chip(p.name.ifBlank { p.userId.take(10) }, selected = d.choice == p.userId, onClick = { c.edit(d.copy(choice = p.userId)) })
      }
      Text(StrEvidence.dueLabel, style = V3Type.subhead, color = muted, modifier = pad)
      FlowRow(pad, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (h in OrderEvidence.DUE_HOURS) V3Chip(StrEvidence.dueIn(h), selected = d.dueHours == h,
          onClick = { c.edit(d.copy(dueAt = OrderEvidence.dueIn(h), dueHours = h)) })
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
  V3TextField(d.text, { c.edit(d.copy(text = it)) }, label, pad.fillMaxWidth(), singleLine = false)
  FlowRow(pad, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    V3Button(StrEvidence.send, { c.submit() }, enabled = d.ready(current) && !c.busy, loading = c.busy)
    V3Button(StrEvidence.cancel, { c.cancel() }, kind = V3ButtonKind.NEUTRAL, enabled = !c.busy)
  }
}
