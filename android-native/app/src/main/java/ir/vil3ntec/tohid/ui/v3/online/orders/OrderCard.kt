package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.animation.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.DraftTag
import ir.vil3ntec.tohid.ui.v3.online.status.OrderStatusPill
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  کارتِ سفارش در صف (online.md): «#۱۰۴۲» و قرصِ وضعیت، مشتری با شمارهٔ پوشیده و
 *  منبع، برچسب‌های ارسال/حضوری، زمان‌دار، شمارِ اقلام، پرداخت و «آزمایشی»، و پایینِ
 *  کارت جمعِ کل با دکمهٔ گامِ بعدی. سفارشِ تازه‌رسیده (`flash`) یک بار به رنگِ «در انتظارِ تأیید» می‌درخشد.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderCard(
  o: OrderDto,
  now: Long,
  step: String?,
  onOpen: () -> Unit,
  onStep: (String) -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  flash: Boolean = false,
) {
  val c = PhoneV3.colors
  val awaiting = PhoneV3Roles.status(c, V3Status.AWAITING)
  val edge = if (selected) PhoneV3Roles.focus(c) else c.hairline
  val glow = remember(o.id) { Animatable(if (flash && Motion.enabled) awaiting.fg else edge) }
  LaunchedEffect(o.id, flash, edge) {
    if (flash && Motion.enabled) glow.animateTo(edge, MotionSpec.tweenOf(MotionTokens.COUNT_UP, MotionTokens.EmphasizedDecelerate))
    else glow.snapTo(edge)
  }
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  Column(
    modifier
      .fillMaxWidth()
      .v3Pressable(label = StrOnline.orderTitle(o.orderNo), onClick = onOpen)
      .clip(shape)
      .background(c.card)
      .border(if (selected || flash) 2.dp else 1.dp, glow.value, shape)
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrOnline.orderNo(o.orderNo), style = V3Type.headline, color = c.ink)
      Spacer(Modifier.weight(1f))
      OrderStatusPill(o.status)
    }
    Spacer(Modifier.height(6.dp))
    Text(
      listOfNotNull(o.customer.name.ifBlank { null }, o.customer.phoneMasked.ifBlank { null }?.let(StrOnline::ltr), StrOnline.orderSource(o.source)).joinToString(" · "),
      style = V3Type.subhead,
      color = c.inkMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(10.dp))
    OrderTags(o, now)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(StrOnline.afn(o.totalMinor), style = V3Type.headline, color = c.ink, maxLines = 1)
        Text(StrOnline.ago(o.createdAt, now), style = V3Type.caption, color = c.inkMuted)
      }
      if (step != null) {
        Spacer(Modifier.width(8.dp))
        V3Button(
          StrOnline.orderAction(step),
          { onStep(step) },
          kind = if (OrdersModel.danger(step)) V3ButtonKind.DANGER else V3ButtonKind.PRIMARY,
          height = 40.dp,
          haptic = true,
        )
      }
    }
  }
}

/** برچسب‌های کارت و سرِ جزئیات — یک جا تا هر دو یکی باشند */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderTags(o: OrderDto, now: Long) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (o.fulfillment == "delivery") DraftTag(StrOnline.delivery, Icons.Outlined.TwoWheeler)
    else DraftTag(StrOnline.pickup, Icons.Outlined.Storefront)
    o.scheduledFor?.let { DraftTag(StrOnline.forTime(OrdersModel.scheduleLabel(it, now)), Icons.Outlined.Schedule) }
    DraftTag(StrOnline.items(o.itemsCount), Icons.Outlined.ShoppingBag)
    DraftTag(StrOnline.pay(o.paymentStatus, o.paymentMethod), Icons.Outlined.Payments)
    if (o.isTest) DraftTag(StrOnline.test, Icons.Outlined.Science, tone = V3Tone.WARNING)
  }
}
