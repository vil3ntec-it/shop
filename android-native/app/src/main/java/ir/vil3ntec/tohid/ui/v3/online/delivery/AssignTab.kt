package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.DraftTag
import ir.vil3ntec.tohid.ui.v3.online.status.MissionStatusPill
import ir.vil3ntec.tohid.ui.v3.online.status.OrderStatusPill
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Stagger

class AssignActions(
  val onAssign: (OrderDto) -> Unit,
  val onAuto: (List<OrderDto>) -> Unit,
  val onAct: (Mission, DeskActions.Act) -> Unit,
)

/**
 *  برگهٔ «سپردن»: سفارش‌های آمادهٔ بی پیک (نقدِ گرفتنی، «خودکار» و «سپردن…»، و «سپردنِ خودکار (n)»
 *  برای همه با پیکِ دکان) و مأموریت‌های زنده با روش، کرایه، یادداشت‌ها و فقط کارهای مجازِ
 *  `DeskActions` (بی اشتراک ⇐ هیچ کاری). روی کامپیوترِ پهن (`table`) همان دو فهرست جدول با سرستون‌اند.
 */
@Composable
internal fun AssignTab(
  d: DeskData,
  cfg: DeskConfig,
  writable: Boolean,
  actions: AssignActions,
  pad: PaddingValues,
  header: @Composable () -> Unit,
  table: Boolean = false,
) {
  val canAssign = cfg.perms.assign && writable
  val stagger = rememberV3Stagger()
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
    item(key = "head") { header() }
    item(key = "ready-head") {
      SectionHead(StrOnline.dlReadyTitle) {
        if (canAssign && d.toAssign.isNotEmpty() && cfg.shopCourier) {
          V3Button(StrOnline.dlAutoAll(d.toAssign.size), { actions.onAuto(d.toAssign) }, kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.AutoMode, height = 38.dp)
        }
      }
    }
    if (d.toAssign.isEmpty()) item(key = "ready-empty") { Note(StrOnline.dlNoReady) }
    else {
      if (table) item(key = "ready-th") { DeskTableHead(READY_COLS) }
      itemsIndexed(d.toAssign, key = { _, o -> "o-" + o.id }) { i, o ->
        val m = Modifier.v3AnimateItem(this).v3Stagger(stagger, "o-" + o.id, i)
        if (table) ReadyRow(o, canAssign, actions, m) else ReadyCard(o, canAssign, actions, m)
      }
    }
    item(key = "live-head") { SectionHead(StrOnline.dlLiveTitle) {} }
    missions(d, cfg, writable, actions, table)
  }
}

private fun LazyListScope.missions(d: DeskData, cfg: DeskConfig, writable: Boolean, actions: AssignActions, table: Boolean) {
  if (d.missions.isEmpty()) item(key = "live-empty") { Note(StrOnline.dlNoLive) }
  else {
    if (table) item(key = "live-th") { DeskTableHead(MISSION_COLS) }
    itemsIndexed(d.missions, key = { _, m -> "m-" + m.id }) { _, m ->
      val acts = if (writable) DeskActions.of(m, cfg.perms) else emptyList()
      if (table) MissionRow(m, d, acts, { a -> actions.onAct(m, a) }, Modifier.v3AnimateItem(this))
      else MissionCard(m, d, acts, { a -> actions.onAct(m, a) }, Modifier.v3AnimateItem(this))
    }
  }
}

@Composable
internal fun SectionHead(title: String, action: @Composable () -> Unit) {
  Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = V3Type.headline, color = PhoneV3.colors.ink, modifier = Modifier.weight(1f))
    action()
  }
}

@Composable
internal fun Note(text: String) {
  Text(text, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun ReadyCard(o: OrderDto, canAssign: Boolean, actions: AssignActions, modifier: Modifier) {
  val c = PhoneV3.colors
  val expected = DeliveryModel.expected(o)
  V3Card(modifier.padding(bottom = 10.dp), padding = PaddingValues(16.dp)) {
    OrderHead(o.orderNo, o.customer.name) { OrderStatusPill(o.status) }
    DeliveryModel.landmark(o).takeIf { it.isNotEmpty() }?.let {
      Spacer(Modifier.height(4.dp))
      Text(it, style = V3Type.footnote, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (expected > 0) StrOnline.dlCollect(StrOnline.afn(expected)) else StrOnline.dlPaidNoCash,
        style = V3Type.callout, color = c.ink, modifier = Modifier.weight(1f),
      )
      if (canAssign) {
        V3Button(StrOnline.dlAuto, { actions.onAuto(listOf(o)) }, kind = V3ButtonKind.SECONDARY, height = 38.dp)
        Spacer(Modifier.width(8.dp))
        V3Button(StrOnline.dlAssign, { actions.onAssign(o) }, height = 38.dp, haptic = true)
      }
    }
  }
}

@Composable
private fun ReadyRow(o: OrderDto, canAssign: Boolean, actions: AssignActions, modifier: Modifier) {
  val expected = DeliveryModel.expected(o)
  val landmark = DeliveryModel.landmark(o)
  DeskTableRow(modifier) {
    DeskCell(StrOnline.orderNo(o.orderNo), READY_COLS[0], strong = true)
    DeskCell(o.customer.name, READY_COLS[1])
    DeskSlot(READY_COLS[2]) { OrderStatusPill(o.status) }
    DeskCell(landmark.ifEmpty { "—" }, READY_COLS[3], muted = landmark.isEmpty())
    DeskCell(if (expected > 0) StrOnline.afn(expected) else StrOnline.pay("paid", ""), READY_COLS[4], strong = expected > 0, muted = expected == 0L)
    DeskSlot(READY_COLS[5], end = true) {
      if (canAssign) DeskButtons {
        V3Button(StrOnline.dlAuto, { actions.onAuto(listOf(o)) }, kind = V3ButtonKind.SECONDARY, height = 38.dp)
        V3Button(StrOnline.dlAssign, { actions.onAssign(o) }, height = 38.dp, haptic = true)
      }
    }
  }
}

@Composable
private fun MissionRow(m: Mission, d: DeskData, acts: List<DeskActions.Act>, onAct: (DeskActions.Act) -> Unit, modifier: Modifier) {
  val c = PhoneV3.colors
  val notes = DeliveryModel.notes(m)
  DeskTableRow(modifier) {
    DeskCell(StrOnline.orderNo(m.orderNo), MISSION_COLS[0], strong = true)
    DeskCell(m.customerName.ifEmpty { "—" }, MISSION_COLS[1], muted = m.customerName.isEmpty())
    DeskCell(DeliveryModel.how(m, d), MISSION_COLS[2])
    DeskSlot(MISSION_COLS[3]) {
      Column {
        MissionStatusPill(m.status)
        if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = V3Type.caption, color = PhoneV3Roles.status(c, V3Status.WARNING).fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    DeskCell(if (m.expectedCollectMinor > 0) StrOnline.afn(m.expectedCollectMinor) else "—", MISSION_COLS[4], muted = m.expectedCollectMinor == 0L)
    DeskCell(if (m.fareMinor > 0) StrOnline.afn(m.fareMinor) else "—", MISSION_COLS[5], muted = m.fareMinor == 0L)
    DeskSlot(MISSION_COLS[6], end = true) {
      if (acts.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        acts.forEach { a ->
          V3Button(
            StrOnline.deskAction(a), { onAct(a) },
            kind = if (DeliveryModel.secondary(a)) V3ButtonKind.SECONDARY else V3ButtonKind.PRIMARY,
            height = 38.dp, haptic = !DeliveryModel.secondary(a),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MissionCard(m: Mission, d: DeskData, acts: List<DeskActions.Act>, onAct: (DeskActions.Act) -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  V3Card(modifier.padding(bottom = 10.dp), padding = PaddingValues(16.dp)) {
    OrderHead(m.orderNo, m.customerName) { MissionStatusPill(m.status) }
    Spacer(Modifier.height(4.dp))
    Text(DeliveryModel.how(m, d), style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(6.dp))
    Row {
      Text(if (m.expectedCollectMinor > 0) StrOnline.dlCash(StrOnline.afn(m.expectedCollectMinor)) else StrOnline.dlNoCash, style = V3Type.callout, color = c.ink, modifier = Modifier.weight(1f))
      Text(if (m.fareMinor > 0) StrOnline.dlFare(StrOnline.afn(m.fareMinor)) else StrOnline.dlNoFare, style = V3Type.footnote, color = c.inkMuted)
    }
    val notes = DeliveryModel.notes(m)
    if (notes.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        notes.forEach { DraftTag(it, tone = V3Tone.WARNING) }
      }
    }
    if (acts.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        acts.forEach { a ->
          V3Button(
            StrOnline.deskAction(a), { onAct(a) },
            kind = if (DeliveryModel.secondary(a)) V3ButtonKind.SECONDARY else V3ButtonKind.PRIMARY,
            height = 38.dp, haptic = !DeliveryModel.secondary(a),
          )
        }
      }
    }
  }
}

/** سرِ کارتِ سفارش/مأموریت: «#۱۰۴۲» نامِ مشتری … قرصِ وضعیت */
@Composable
internal fun OrderHead(orderNo: Long, name: String, pill: @Composable () -> Unit) {
  val c = PhoneV3.colors
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(StrOnline.orderNo(orderNo), style = V3Type.headline, color = c.ink)
    Spacer(Modifier.width(8.dp))
    Text(name, style = V3Type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    pill()
  }
}

/** ستونِ برچسب و عددِ کوچکِ کارت (پیک‌ها، صورت‌حساب) */
@Composable
internal fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  Column(modifier.padding(end = 6.dp)) {
    Text(label, style = V3Type.caption, color = c.inkMuted, maxLines = 2)
    Text(value, style = V3Type.callout, color = c.ink)
  }
}
