package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status

/** شعاع‌های خانوادهٔ کارت‌ها (THEME-V2): کارت ۲۰، ردیف/کاشی ۱۴ */
internal object HomeCardSpec {
  val card = 20.dp
  val tile = 14.dp
}

private class Shortcut(val label: String, val icon: ImageVector, val route: String, val accent: HomeAccent, val haptic: Boolean = false)

/** نشانِ زندهٔ کاشیِ سفارش‌ها از صفِ همین برنامه (`OrdersLive`، هر ۲۰ ثانیه جلوی چشم)؛ بخشِ خاموش ⇒ null */
@Composable
internal fun rememberHomeOrders(): HomeTiles.Orders = HomeTiles.orders(OrdersLive.enabled, OrdersLive.counts.value)

/**
 *  چهار میان‌بر در یک ردیف (HOME-V2 §۲٫۳) — بی کادرِ دورِ ردیف. هر کدام رنگِ همراهِ خودش
 *  (آیکنِ رنگی روی پس‌زمینهٔ نرمِ همان رنگ، کاشیِ ۱۴) و مستقیم فرمِ خودش (`ShellTargets`).
 *  نخستین همیشه «سفارش‌های آنلاین» ([HomeOrdersTile]) به جای «فروشِ تازه» (خواستِ صاحب‌کار؛ `HomeTiles`): بخشِ روشن ⇒ صف،
 *  خاموش ⇒ «تنظیمات ← سفارشِ آنلاین». صندوق از نوار یا دکمهٔ شناورِ خانه ([HomeSaleFab]) می‌ماند.
 */
@Composable
internal fun HomeShortcuts(onOpen: (String) -> Unit, modifier: Modifier = Modifier, orders: HomeTiles.Orders = rememberHomeOrders()) {
  val p = PhoneV3.colors
  val s = Shop.colors
  val items = listOf(
    Shortcut(StrHome.receive, Icons.Outlined.CallReceived, ShellTargets.RECEIVE, HomeAccents.RECEIVE),
    Shortcut(StrHome.stockEntry, Icons.Outlined.MoveToInbox, ShellTargets.STOCK_ENTRY, HomeAccents.STOCK_ENTRY),
    Shortcut(StrHome.history, Icons.Outlined.History, ShellTargets.HISTORY, HomeAccents.HISTORY),
  )
  Row(modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
    HomeOrdersTile(orders, { onOpen(if (orders.off) ShellTargets.ORDERS_SETUP else ShellTargets.ORDERS) }, Modifier.weight(1f))
    items.forEach { sc ->
      Column(
        Modifier
          .weight(1f)
          .clip(RoundedCornerShape(HomeCardSpec.tile))
          .v3Pressable(label = sc.label, haptic = sc.haptic) { onOpen(sc.route) }
          .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          Modifier.size(52.dp).clip(RoundedCornerShape(HomeCardSpec.tile)).background(HomeAccents.soft(s, sc.accent)),
          contentAlignment = Alignment.Center,
        ) { Icon(sc.icon, contentDescription = null, tint = HomeAccents.color(s, sc.accent), modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(
          sc.label,
          color = p.ink,
          style = v3Text(12, FontWeight.Medium, 17),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
      }
    }
  }
}

/**
 *  کاشیِ «سفارش‌های آنلاین»: جدا از سه کاشیِ دیگر — کادرِ نرمِ رنگِ سفارش با خطِ دور، آیکنِ پُر روی همان رنگ، نشانِ
 *  شمار (تازه‌ها به رنگِ «در انتظارِ تأیید»، در-کارها خاکستری) و نقطهٔ تپنده فقط وقتی سفارشِ تازه هست (حرکتِ خاموش ⇒
 *  نقطهٔ ثابت). نامِ دسترس‌پذیر شمار را هم می‌گوید. بخشِ خاموش ([HomeTiles.Orders.off]) ⇒ همان کاشی کم‌رنگ و بی نشان.
 */
@Composable
internal fun HomeOrdersTile(o: HomeTiles.Orders, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val p = PhoneV3.colors
  val s = Shop.colors
  val accent = HomeAccents.color(s, HomeAccents.ORDERS)
  val awaiting = PhoneV3Roles.status(p, V3Status.AWAITING)
  val quiet = PhoneV3Roles.status(p, V3Status.INACTIVE)
  val a11y = HomeTiles.a11y(o)
  val shape = RoundedCornerShape(HomeCardSpec.tile)
  Column(
    modifier
      .padding(horizontal = 2.dp)
      .clip(shape)
      .background(HomeAccents.soft(s, HomeAccents.ORDERS))
      .border(1.dp, accent.copy(alpha = if (o.pulse) 0.70f else if (o.off) 0.20f else 0.35f), shape)
      .semantics(mergeDescendants = true) { contentDescription = a11y }
      .v3Pressable(label = a11y, haptic = true, onClick = onClick)
      .padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(52.dp)) {
      Box(
        Modifier.size(52.dp).graphicsLayer { alpha = if (o.off) 0.45f else 1f }.clip(RoundedCornerShape(HomeCardSpec.tile))
          .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.80f)))),
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Outlined.ShoppingBasket, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp)) }
      o.badgeText?.let { n ->
        val pair = if (o.pulse) awaiting else quiet
        Box(
          Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp).widthIn(min = 20.dp).height(20.dp)
            .clip(RoundedCornerShape(10.dp)).background(pair.bg).border(1.5.dp, p.bg, RoundedCornerShape(10.dp)).padding(horizontal = 5.dp),
          contentAlignment = Alignment.Center,
        ) { Text(n, color = pair.fg, style = v3Text(11, FontWeight.Bold, 13), maxLines = 1) }
      }
      if (o.pulse) PulseDot(awaiting.fg, Modifier.align(Alignment.TopStart).offset(x = (-7).dp, y = (-7).dp))
    }
    Spacer(Modifier.height(8.dp))
    Text(
      StrOrdersHub.tile,
      color = p.ink,
      style = v3Text(12, FontWeight.SemiBold, 15),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
  }
}

/** نقطهٔ «سفارشِ تازه»: موجِ گسترنده هر ۱٫۴ ثانیه؛ حرکتِ کم (`Motion.enabled` خاموش) ⇒ فقط نقطه */
@Composable
private fun PulseDot(color: Color, modifier: Modifier = Modifier) {
  val ring = PhoneV3.colors.bg
  Box(modifier.size(14.dp), contentAlignment = Alignment.Center) {
    if (Motion.enabled) {
      val wave = rememberInfiniteTransition(label = "ordersPulse")
      val f by wave.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing), RepeatMode.Restart), label = "ordersPulseF")
      Box(Modifier.size(10.dp).graphicsLayer { scaleX = 1f + 1.3f * f; scaleY = 1f + 1.3f * f; alpha = 1f - f }.clip(CircleShape).background(color))
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(color).border(1.5.dp, ring, CircleShape))
  }
}

/**
 *  «فروشِ تازه» ِ شناور روی خانه — فقط وقتی کاشیِ فروش جایش را به سفارش‌ها داده و نوارِ پایینِ همین کاربر راهِ صندوق
 *  ندارد (`HomeTiles.saleFab`). صندوق هرگز بی‌راه نمی‌ماند (`V3ReachabilityTest`).
 */
@Composable
internal fun HomeSaleFab(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  val pair = PhoneV3Roles.button(PhoneV3.colors, V3ButtonKind.PRIMARY)
  val shape = RoundedCornerShape(18.dp)
  Row(
    modifier
      .v3Pressable(label = StrOrdersHub.saleFab, scale = 0.94f, haptic = true, role = Role.Button) { onOpen(ShellTargets.NEW_SALE) }
      .height(56.dp)
      .shadow(10.dp, shape, ambientColor = pair.bg.copy(alpha = 0.5f), spotColor = pair.bg.copy(alpha = 0.5f))
      .clip(shape)
      .background(pair.bg)
      .padding(horizontal = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, tint = pair.fg, modifier = Modifier.size(24.dp))
    Spacer(Modifier.size(8.dp))
    Text(StrOrdersHub.saleFab, color = pair.fg, style = v3Text(15, FontWeight.SemiBold, 20), maxLines = 1)
  }
}
