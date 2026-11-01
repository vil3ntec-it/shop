package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3InvoiceItems
import ir.vil3ntec.tohid.ui.v3.V3InvoiceLine
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.status.OrderStatusPill
import ir.vil3ntec.tohid.ui.v3.v3Shake

/** آنچه صفحهٔ جزئیات نشان می‌دهد */
data class DetailState(
  val order: OrderDto? = null,
  val loading: Boolean = true,
  val error: String? = null,
  /** وضعیت جای دیگری عوض شد (`status_changed`) */
  val stale: Boolean = false,
  val perms: OrderPermsDto = OrderPermsDto(),
  val now: Long = 0L,
  /** گذاری که همین حالا فرستاده می‌شود (دکمه «در حالِ کار») */
  val busy: String? = null,
  /** خطای آخرین گذارِ مستقیم — با لرزش زیرِ نوار */
  val failure: String? = null,
  /** فروشِ تحویل (`OrderFulfillNotes.detail`) */
  val notes: List<OrderFulfillNotes.Note> = emptyList(),
)

class DetailActions(
  val onRetry: () -> Unit,
  val onReload: () -> Unit,
  /** گذار: برگهٔ کار یا (بی پرسش) فرستادنِ مستقیم — تصمیم با `OrdersModel.needsSheet` */
  val onStep: (String) -> Unit,
  val onCall: (String) -> Unit,
  val onCopy: (String) -> Unit,
  val onMap: (String) -> Unit,
  val onNote: (OrderFulfillNotes.Note) -> Unit = {},
)

/**
 *  جزئیاتِ سفارش (`orders/{id}`): سرِ صفحه با قرصِ وضعیت و برچسب‌ها، مشتری (تماس و کپیِ
 *  شماره فقط با شمارهٔ کاملِ سرور)، نشانی و پین، اقلام و جمع‌ها، یادداشت و خطِ زمانی؛ نوارِ
 *  چسبانِ پایین فقط گذارهای مجازِ `OrderStates` را دارد. روی پهن همین در ستونِ دوم.
 */
@Composable
fun OrderDetailScreen(
  state: DetailState,
  actions: DetailActions,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  /** زیرِ سرِ صفحه: «عقب انداختن» (ORD-07) */
  extra: (@Composable () -> Unit)? = null,
) {
  val o = state.order
  val bar = o?.let { OrdersModel.bar(it, state.perms) }
  V3Scaffold(
    title = o?.let { StrOnline.orderTitle(it.orderNo) } ?: StrOnline.ordersTitle,
    subtitle = o?.let { StrOnline.orderSource(it.source) + " · " + OnlineFmt.ago(it.createdAt, state.now) },
    modifier = modifier,
    onBack = onBack,
    bottomBar = if (bar != null && !bar.isEmpty) ({ OrderActionBar(bar, state.busy, state.failure, actions.onStep) }) else null,
  ) { pad ->
    when {
      o == null && state.error != null -> Column(Modifier.fillMaxSize()) {
        V3EmptyState(Icons.Outlined.ErrorOutline, StrOnline.orderFailed, body = state.error, action = StrKit.retry, onAction = actions.onRetry)
      }
      o == null -> Column(Modifier.fillMaxSize().padding(16.dp)) { V3Skeleton(rows = 5) }
      else -> Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
      ) {
        if (state.stale) {
          V3Message(V3Tone.WARNING, StrOnline.staleStatus, Modifier.padding(top = 8.dp), action = StrOnline.reload, onAction = actions.onReload)
        }
        HeaderCard(o, state.now)
        extra?.invoke()
        state.notes.forEach { n ->
          V3Message(
            if (n.warn) V3Tone.WARNING else V3Tone.INFO, n.title, Modifier.padding(top = 8.dp),
            body = n.body.takeIf { it.isNotBlank() }, action = n.actLabel.takeIf { n.act != null }, onAction = { actions.onNote(n) },
          )
        }
        CustomerSection(o, actions)
        if (o.fulfillment == "delivery") AddressSection(o, actions)
        Spacer(Modifier.height(16.dp))
        V3InvoiceItems(
          o.lines.map { l ->
            V3InvoiceLine(
              name = l.name,
              detail = OrderText.qty(l.qtyMilli) + (if (l.unit.isNotEmpty()) " ${l.unit}" else "") + " × " + OnlineFmt.afn(l.unitPriceMinor),
              total = OnlineFmt.afn(l.lineTotalMinor),
              note = l.note.takeIf { it.isNotEmpty() },
            )
          },
          title = StrOnline.itemsTitle,
        )
        TotalsSection(o)
        if (o.note.isNotEmpty()) {
          V3Section(StrOnline.note) { V3ListRow(o.note, icon = Icons.Outlined.StickyNote2, subtitleLines = 6, chevron = false) }
        }
        if (o.events.isNotEmpty()) V3Section(StrOnline.timeline) { OrderTimeline(OrderDetailModel.timeline(o)) }
      }
    }
  }
}

@Composable
private fun HeaderCard(o: OrderDto, now: Long) {
  val c = PhoneV3.colors
  V3Card(Modifier.padding(top = 8.dp), padding = PaddingValues(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OrderStatusPill(o.status)
      Spacer(Modifier.weight(1f))
      Text(OnlineFmt.afn(o.totalMinor), style = V3Type.title, color = c.ink)
    }
    Spacer(Modifier.height(10.dp))
    OrderTags(o, now)
    val notes = listOfNotNull(
      o.statusReason.takeIf { it.isNotEmpty() }?.let { StrOnline.reasonLine(StrOnline.orderReason(it)) },
      o.readyAt?.let { StrOnline.readyBy(OrdersModel.stamp(it)) },
    )
    if (notes.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      Text(notes.joinToString(" · "), style = V3Type.footnote, color = c.inkMuted)
    }
  }
}

@Composable
private fun CustomerSection(o: OrderDto, actions: DetailActions) {
  val phone = OrderDetailModel.fullPhone(o)
  V3Section(StrOnline.customer, footer = if (phone == null && o.customer.phoneMasked.isNotEmpty()) StrOnline.fullNumberHelper else null) {
    V3ListRow(
      o.customer.name.ifBlank { StrOnline.customer },
      subtitle = (phone ?: o.customer.phoneMasked).ifBlank { null }?.let(OnlineFmt::ltr) ?: StrOnline.noNumber,
      icon = Icons.Outlined.Person,
      hue = V3Hue.CORAL,
      chevron = false,
    )
    if (phone != null) {
      V3Divider(start = 16.dp)
      Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3Button(StrOnline.call, { actions.onCall(phone) }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Call, haptic = true)
        V3Button(StrOnline.copyNumber, { actions.onCopy(phone) }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.ContentCopy)
      }
    }
  }
}

@Composable
private fun AddressSection(o: OrderDto, actions: DetailActions) {
  val map = OrderDetailModel.mapUri(o)
  val rows = buildList<@Composable () -> Unit> {
    if (o.address.text.isNotEmpty()) add { V3ListRow(o.address.text, icon = Icons.Outlined.Place, hue = V3Hue.SKY, subtitleLines = 3, chevron = false) }
    if (o.address.landmark.isNotEmpty()) add { V3ListRow(o.address.landmark, subtitle = StrOnline.landmark, icon = Icons.Outlined.Flag, hue = V3Hue.AMBER, chevron = false) }
    if (map != null) add {
      V3ListRow(StrOnline.openMap, subtitle = OnlineFmt.ltr("${o.address.lat}, ${o.address.lng}"), icon = Icons.Outlined.Map, hue = V3Hue.MINT, onClick = { actions.onMap(map) })
    }
  }
  V3Section(StrOnline.address) {
    if (rows.isEmpty()) V3ListRow("—", chevron = false, tile = false)
    rows.forEachIndexed { i, row -> if (i > 0) V3Divider(start = 64.dp); row() }
  }
}

@Composable
private fun TotalsSection(o: OrderDto) {
  V3Section(null) {
    V3ListRow(StrOnline.subtotal, value = OnlineFmt.afn(o.subtotalMinor), chevron = false, tile = false)
    if (o.discountMinor > 0) { V3Divider(start = 22.dp); V3ListRow(StrOnline.discount, value = "−" + OnlineFmt.afn(o.discountMinor), chevron = false, tile = false) }
    if (o.deliveryFeeMinor > 0) { V3Divider(start = 22.dp); V3ListRow(StrOnline.deliveryFee, value = OnlineFmt.afn(o.deliveryFeeMinor), chevron = false, tile = false) }
    V3Divider(start = 22.dp)
    V3ListRow(StrOnline.total, chevron = false, tile = false, trailing = { Text(OnlineFmt.afn(o.totalMinor), style = V3Type.headline, color = PhoneV3.colors.ink) })
  }
}

/**
 *  نوارِ چسبانِ کارها: «…» برای گذارهای دیگر، کارِ خطر (رد/لغو) و گامِ اصلی. خطای گذارِ
 *  مستقیم بالای دکمه‌ها با لرزش (کیت) می‌آید.
 */
@Composable
private fun OrderActionBar(bar: OrdersModel.Bar, busy: String?, failure: String?, onStep: (String) -> Unit) {
  OrderActionBarContent(bar, busy, failure, onStep, Modifier.v3Shake(failure))
}
