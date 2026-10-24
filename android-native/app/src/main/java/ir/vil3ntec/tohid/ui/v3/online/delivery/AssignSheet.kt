package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.AutoAssign
import ir.vil3ntec.tohid.core.delivery.CourierCash
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.DraftTag
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.launch

/**
 *  سپردنِ دستی (DLV-03، `delivery/assign/{orderId}`): پیکِ دکان (کارتِ انتخاب با شمارِ زنده، نوارِ
 *  «نقدِ درگیر از سقف»، هشدارِ گذشتن از سقف و تیکِ آگاهانه) یا شرکتِ بیرونی (نام، رهگیری)، با
 *  کرایهٔ همین تحویل. یک کلیدِ یکتایی برای همین فرم — پاسخِ گم‌شده با دوباره زدن همان کار است.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssignSheet(
  order: OrderDto,
  d: DeskData,
  cfg: DeskConfig,
  repo: DeliveryRepository,
  onDismiss: () -> Unit,
  onDone: (Mission, String?) -> Unit,
) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  val expected = DeliveryModel.expected(order)
  val suggested = remember(order.id, d.couriers) {
    AutoAssign.plan(listOf(AutoAssign.Candidate(order.id, order.orderNo, expected, order.createdAt)), d.couriers).picks.firstOrNull()?.courierId
  }
  val modes = DeliveryForms.modes(cfg)
  var form by remember { mutableStateOf(DeliveryForms.Assign(DeliveryForms.startMode(cfg, d.couriers), courierId = suggested.orEmpty())) }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  val keys = remember { DeliveryRepository.OnceKey() }
  val fareMinor = minorOf(form.fare)
  val over = DeliveryForms.overCap(form, d.couriers, expected)

  V3Sheet(onDismiss = onDismiss, title = StrOnline.dlAssignTitle(order.orderNo)) { _ ->
    Text(
      order.customer.name + " · " + (if (expected > 0) StrOnline.dlCollect(StrOnline.afn(expected)) else StrOnline.pay("paid", "")),
      style = V3Type.callout, color = c.ink,
    )
    DeliveryModel.landmark(order).takeIf { it.isNotEmpty() }?.let { Text(it, style = V3Type.footnote, color = c.inkMuted) }
    if (modes.size > 1) {
      Spacer(Modifier.height(14.dp))
      V3Segmented(modes.map(StrOnline::deliveryMode), modes.indexOf(form.mode).coerceAtLeast(0), { form = form.copy(mode = modes[it]); err = null })
    }
    Spacer(Modifier.height(14.dp))
    if (form.mode == "shop_courier") {
      if (d.couriers.isEmpty()) V3Message(V3Tone.INFO, StrOnline.dlNoCouriersInline)
      else V3Card(padding = PaddingValues(vertical = 4.dp)) {
        d.couriers.forEachIndexed { i, cr ->
          if (i > 0) V3Divider(start = 16.dp)
          val fits = CourierCash.fits(cr, expected)
          V3ListRow(
            cr.name,
            subtitle = if (cr.active) StrOnline.dlCourierLine(cr.liveCount, StrOnline.afn(CourierCash.committed(cr)), StrOnline.afn(cr.cashCapMinor), StrOnline.afn(CourierCash.headroom(cr))) else StrOnline.dlInactive,
            icon = Icons.Outlined.DeliveryDining,
            hue = V3Hue.SKY,
            selected = cr.id == form.courierId,
            trailing = if (cr.id == suggested) ({ DraftTag(StrOnline.dlSuggest, tone = V3Tone.SUCCESS) }) else null,
            onClick = if (cr.active) ({ form = form.copy(courierId = cr.id, override = false) }) else null,
          )
          if (cr.active) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
            DraftCapBar(CourierCash.usePercent(cr), CourierCash.atCap(cr))
            if (!fits) { Spacer(Modifier.height(6.dp)); DraftTag(StrOnline.dlWouldExceed, Icons.Outlined.WarningAmber, V3Tone.WARNING) }
          }
        }
      }
      if (over) {
        Spacer(Modifier.height(10.dp))
        V3Card(padding = PaddingValues(0.dp)) {
          V3ToggleRow(StrOnline.dlOverride, form.override, { form = form.copy(override = it) }, icon = Icons.Outlined.WarningAmber, hue = V3Hue.AMBER)
        }
      }
    } else {
      if (d.companies.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          d.companies.forEach { co ->
            V3Chip(co.name, form.company == co.name, {
              form = form.copy(company = co.name, fare = if (form.fare.isBlank() && co.lastFareMinor > 0) fieldOf(co.lastFareMinor) else form.fare)
            })
          }
        }
        Spacer(Modifier.height(12.dp))
      }
      V3TextField(form.company, { form = form.copy(company = it.take(DeliveryForms.NAME_MAX)) }, StrOnline.dlCompanyName, required = true, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(12.dp))
      V3TextField(form.ref, { form = form.copy(ref = it.take(DeliveryForms.NAME_MAX)) }, StrOnline.dlTracking, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(12.dp))
    V3TextField(
      form.fare, { form = form.copy(fare = it.take(DeliveryForms.NUMBER_MAX)) }, StrOnline.dlFareField, kind = V3FieldKind.MONEY,
      helper = if (form.mode == "external") StrOnline.dlFareExternal else StrOnline.dlFareCourier,
      error = if (fareMinor == null) StrOnline.dlBadNumber else null, modifier = Modifier.fillMaxWidth(),
    )
    err?.let { Spacer(Modifier.height(12.dp)); V3Message(V3Tone.ERROR, it, Modifier.v3Shake(it)) }
    Spacer(Modifier.height(20.dp))
    V3Button(
      StrOnline.dlAssign,
      {
        busy = true
        err = null
        scope.launch {
          val body = DeliveryWire.assignJson(
            mode = form.mode, courierId = form.courierId, externalName = form.company, externalRef = form.ref,
            fareMinor = fareMinor ?: 0L, overrideCashCap = form.override,
          )
          repo.assign(order.id, body, keys.of(body)).onSuccess { onDone(it.mission, it.code) }.onFailure { err = it.userMessage }
          busy = false
        }
      },
      Modifier.fillMaxWidth(),
      enabled = DeliveryForms.assignValid(form, d.couriers, expected) && !busy,
      loading = busy,
      haptic = true,
    )
  }
}
