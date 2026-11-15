package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Section
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3DataRow
import ir.vil3ntec.tohid.ui.v3.V3RowAction
import ir.vil3ntec.tohid.ui.v3.V3RowMeta
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3RowTone
import ir.vil3ntec.tohid.ui.v3.online.DraftTag
import ir.vil3ntec.tohid.ui.v3.online.status.OnlineStatus

/**
 *  کارتِ سفارش در صف (online.md) — ۶۸c: همان کالبدِ آرامِ ردیفِ داده (`V3DataRow`، صاحب‌کار: «این شلوغی‌ها … دقیق و منظم»):
 *  کاشیِ کره (سفارشِ آنلاین، جدا از فروشِ صندوق)، نامِ مشتری (وگرنه «#۱۰۴۲»)، خطِ دومِ آرامِ «سفارشِ آنلاین · #۱۰۴۲ · ۶ دقیقه
 *  پیش · ۳ قلم · با پیک»، و خطِ مبلغِ «۱۴۵٬۰۰۰ ؋ • تازه • پرداخت‌نشده» — وضعیت واژهٔ آرام، نه قرصِ رنگی؛ تنها نشانِ رنگی
 *  هشدارِ کارخواه (سابقهٔ مشتری RSK-03، بازبینیِ موجودی O-06). یک قرصِ خنثیِ گامِ بعدی؛ همهٔ نشان‌ها، اقلام و شمارهٔ پوشیده
 *  در سرِ جزئیات ([OrderTags]). سفارشِ تازه‌رسیده (`flash`) یک بار به رنگِ «در انتظارِ تأیید» می‌درخشد.
 */
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
  /** حالتِ انتخابِ چندتایی (ORD-19): null ⇒ نیست؛ وگرنه نشانِ تیک در آغازِ ردیف، نه فقط رنگِ مرز */
  checked: Boolean? = null,
  /** RSK-03 «کارت در صف»: نخستین هشدارِ سابقهٔ این مشتری (`OrderRisk.cardFlags`)؛ null ⇒ بی نشان */
  risk: String? = null,
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
  val open = selected
  val lang = ir.vil3ntec.tohid.core.i18n.I18n.lang
  val secondary = remember(o, now, lang) { OrderCardText.secondary(o, now) }
  val meta = remember(o, risk, lang) { OrderCardText.meta(o, risk) }
  Column(
    modifier
      .fillMaxWidth()
      //  کارتِ بازِ کنار (کامپیوتر) برای صفحه‌خوان هم «انتخاب‌شده»؛ در حالتِ چندتایی تیکِ خودِ ردیف
      .semantics { if (checked == null && open) this.selected = true }
      .clip(shape)
      .background(c.card)
      .border(if (selected || flash) 2.dp else 1.dp, glow.value, shape),
  ) {
    V3DataRow(
      title = o.customer.name.ifBlank { OnlineFmt.orderNo(o.orderNo) },
      secondary = secondary,
      meta = meta,
      icon = Icons.Outlined.Language,
      hue = V3Section.ORDERS.hue,
      action = step?.let { to -> V3RowAction(StrOnline.orderAction(to), OrderCardText.stepIcon(to)) { onStep(to) } },
      selected = checked,
      onClick = onOpen,
    )
  }
}

/**
 *  متن‌های کارتِ صف (کالبدِ ۶۸c): خطِ دومِ «سفارشِ آنلاین · #۱۰۴۲ · ۶ دقیقه پیش · ۳ قلم · با پیک · برای ۱۸:۰۰» و خطِ مبلغِ
 *  «۱۴۵٬۰۰۰ ؋ • تازه • پرداخت‌نشده»؛ هشدارِ کارخواه (سابقهٔ مشتری، بازبینیِ موجودی) جای پرداخت می‌نشیند و تنها واژهٔ رنگیِ ردیف
 *  است، «آزمایشی» واژهٔ آرام. سرِ جزئیات همان [OrderTags] ِ کامل را دارد.
 */
internal object OrderCardText {
  /** خطِ روش، زمان، شمار و پرداخت (صفحه‌های دیگر و جزئیات) */
  fun line(o: OrderDto, now: Long): String = listOfNotNull(
    OrdersHubModel.method(o),
    o.scheduledFor?.let { StrOnline.forTime(OrdersModel.scheduleLabel(it, now)) },
    StrOnline.items(o.itemsCount),
    OnlineFmt.pay(o.paymentStatus, o.paymentMethod),
  ).joinToString(" · ")

  fun secondary(o: OrderDto, now: Long): String = listOfNotNull(
    StrOrdersHub.onlineBadge,
    OnlineFmt.orderNo(o.orderNo).takeIf { o.customer.name.isNotBlank() },
    OnlineFmt.ago(o.createdAt, now).ifBlank { null },
    StrOnline.items(o.itemsCount),
    OrdersHubModel.method(o),
    o.scheduledFor?.let { StrOnline.forTime(OrdersModel.scheduleLabel(it, now)) },
  ).joinToString(" · ")

  fun meta(o: OrderDto, risk: String?): V3RowMeta {
    val status = V3RowStatus.of(StrOnline.orderStatus(o.status), OnlineStatus.order(o.status))
    val alert = when {
      risk != null -> V3RowStatus(risk, V3RowTone.DANGER)
      OrderStockHold.review(o) -> V3RowStatus(StrOrdersHub.stockReview, V3RowTone.DANGER)
      o.isTest -> V3RowStatus(StrOnline.test)
      else -> null
    }
    return V3RowMeta(OnlineFmt.afn(o.totalMinor), status, alert ?: V3RowStatus(OnlineFmt.pay(o.paymentStatus, o.paymentMethod)))
  }

  /** نشانهٔ قرصِ گامِ بعدی */
  fun stepIcon(to: String): ImageVector = when {
    OrdersModel.danger(to) -> Icons.Outlined.Block
    to == "accepted" -> Icons.Outlined.CheckCircle
    else -> Icons.AutoMirrored.Outlined.ArrowForward
  }

  /** نشان‌های توجه: (برچسب، بازبینیِ موجودی؟) */
  fun alerts(o: OrderDto): List<Pair<String, Boolean>> = listOfNotNull(
    (StrOrdersHub.stockReview to true).takeIf { OrderStockHold.review(o) },
    (StrOnline.test to false).takeIf { o.isTest },
  )
}

/** برچسب‌های سرِ جزئیات (همهٔ نشان‌ها) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderTags(o: OrderDto, now: Long) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    //  O-06 = ب: فروشِ حضوری موجودیِ نگه‌داشتهٔ این سفارش را برد — نخست، تا پیش از قبول یا آماده‌سازی دیده شود
    if (OrderStockHold.review(o)) DraftTag(StrOrdersHub.stockReview, Icons.Outlined.Inventory2, tone = V3Tone.WARNING)
    //  روشِ تحویل به زبانِ صاحب‌کار: «با پیک» یا «خودش می‌آید»
    if (OrdersHubModel.courier(o)) DraftTag(OrdersHubModel.method(o), Icons.Outlined.TwoWheeler)
    else DraftTag(OrdersHubModel.method(o), Icons.Outlined.Storefront)
    o.scheduledFor?.let { DraftTag(StrOnline.forTime(OrdersModel.scheduleLabel(it, now)), Icons.Outlined.Schedule) }
    DraftTag(StrOnline.items(o.itemsCount), Icons.Outlined.ShoppingBag)
    DraftTag(OnlineFmt.pay(o.paymentStatus, o.paymentMethod), Icons.Outlined.Payments)
    if (o.isTest) DraftTag(StrOnline.test, Icons.Outlined.Science, tone = V3Tone.WARNING)
  }
}
