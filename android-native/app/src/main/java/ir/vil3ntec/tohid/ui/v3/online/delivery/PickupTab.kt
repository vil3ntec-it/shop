package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.status.OrderStatusPill
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem

class PickupActions(
  val onMakeCode: (OrderDto) -> Unit,
  val onHandover: (OrderDto, Mission) -> Unit,
  val onNewCode: (Mission) -> Unit,
)

/**
 *  برگهٔ «دریافتِ حضوری» (DLV-15): جست‌وجوی شمارهٔ سفارش یا نامِ مشتری، هر سفارشِ «دریافت از دکان» با
 *  نقدِ صندوق، حالتِ کد (ساخته‌نشده / داده‌شده / قفل) و کارهای مجاز: «ساختنِ کدِ دریافت»، «تحویل با کد»
 *  و «کدِ تازه». تحویل فقط وقتی سفارش «آمادهٔ دریافت» است.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PickupTab(
  d: DeskData,
  cfg: DeskConfig,
  writable: Boolean,
  busyId: String?,
  actions: PickupActions,
  pad: PaddingValues,
  header: @Composable () -> Unit,
) {
  val c = PhoneV3.colors
  var query by rememberSaveable { mutableStateOf("") }
  val list = d.pickups.filter { DeliveryModel.matches(it, query) }
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
    item(key = "head") { header() }
    item(key = "search") { V3SearchField(query, { query = it }, StrOnline.dlPickupSearch, Modifier.padding(top = 8.dp, bottom = 4.dp)) }
    if (list.isEmpty()) item(key = "empty") {
      V3EmptyState(Icons.Outlined.Storefront, StrOnline.dlNoPickups, body = if (query.isBlank()) StrOnline.dlNoPickupsBody else StrOnline.dlNoPickupMatch(query))
    }
    items(list, key = { it.id }) { o ->
      val m = d.missionOf(o.id)?.takeIf { it.mode == "pickup" }
      V3Card(Modifier.padding(top = 10.dp).v3AnimateItem(this), padding = PaddingValues(16.dp)) {
        OrderHead(o.orderNo, o.customer.name) { OrderStatusPill(o.status) }
        Spacer(Modifier.height(6.dp))
        val expected = DeliveryModel.expected(o)
        Text(
          (if (expected > 0) StrOnline.dlTakeAtTill(OnlineFmt.afn(expected)) else OnlineFmt.pay("paid", "")) + " · " + StrOnline.items(o.itemsCount),
          style = V3Type.callout, color = c.ink,
        )
        Text(
          DeliveryModel.pickupState(m), style = V3Type.footnote,
          color = if (m?.codeLocked == true) PhoneV3Roles.status(c, V3Status.WARNING).fg else c.inkMuted,
        )
        if (o.status != "ready_for_pickup") Text(StrOnline.dlNotReadyYet, style = V3Type.caption, color = c.inkMuted)
        if (writable) {
          val acts = m?.let { DeskActions.of(it.copy(orderStatus = o.status), cfg.perms) }.orEmpty()
          val makeCode = m == null && cfg.perms.assign && cfg.pickup
          if (makeCode || acts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (makeCode) V3Button(StrOnline.dlMakeCode, { actions.onMakeCode(o) }, height = 38.dp, loading = busyId == o.id, enabled = busyId == null, haptic = true)
              if (m != null) acts.forEach { a ->
                when (a) {
                  DeskActions.Act.PICKUP_HANDOVER -> V3Button(OnlineFmt.deskAction(a), { actions.onHandover(o, m) }, height = 38.dp, haptic = true)
                  DeskActions.Act.NEW_CODE -> V3Button(OnlineFmt.deskAction(a), { actions.onNewCode(m) }, kind = V3ButtonKind.SECONDARY, height = 38.dp)
                  else -> Unit
                }
              }
            }
          }
        }
      }
    }
  }
}
