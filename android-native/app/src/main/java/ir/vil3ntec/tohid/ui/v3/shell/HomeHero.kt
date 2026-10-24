package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.PagerDots
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** اندازه‌های r7 (همان که صاحب محصول دیده و پسندیده) */
private object Hero {
  val content = 214.dp           // ردیفِ آواتار تا نقطه‌ها
  val card = 82.dp
  val overlap = 38.dp
  val sheetRadius = 26.dp
}

/**
 *  سربرگِ برندِ r7: آواتار (⇐ «حساب من»)، زنگ (⇐ مرکزِ هشدارها، نقطه = فوری یا خبرِ تازه)،
 *  «فروشِ امروز» با شمارندهٔ عدد، نقطه‌های ورقه، و کارتِ سفیدِ چهار کارِ سریع که لبه‌اش
 *  روی برگهٔ سفید می‌نشیند. هر کار مستقیم فرمِ خودش را باز می‌کند (`ShellTargets`).
 */
@Composable
internal fun HomeHero(view: HomeView, top: Dp, pages: Int, currentPage: Int, onOpen: (String) -> Unit) {
  val p = PhoneV3.colors
  val zone = top + Hero.content + Hero.card
  val brand = Brush.linearGradient(listOf(p.accent, p.accentDeep), start = Offset(Float.POSITIVE_INFINITY, 0f), end = Offset(0f, Float.POSITIVE_INFINITY))
  Box(Modifier.fillMaxWidth().height(zone)) {
    Box(Modifier.fillMaxWidth().height(zone - Hero.overlap + Hero.sheetRadius).background(brand))
    Box(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(Hero.overlap)
        .background(p.card, RoundedCornerShape(topStart = Hero.sheetRadius, topEnd = Hero.sheetRadius)),
    )
    Column(Modifier.fillMaxWidth().padding(top = top + 14.dp, start = 20.dp, end = 20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ShopLetter(view.shopName, 46.dp, ring = p.onAccent.copy(alpha = 0.85f), onClick = { onOpen(ShellTargets.PROFILE) })
        Spacer(Modifier.weight(1f))
        GlassBell(dot = view.bellDot) { onOpen(ShellTargets.notifications()) }
      }
      Spacer(Modifier.height(12.dp))
      Text(StrHome.todaySales, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = p.onAccent.copy(alpha = 0.72f), style = v3Text(15, FontWeight.Medium, 22))
      Spacer(Modifier.height(2.dp))
      val shown = rememberCountUp(view.todaySales)
      val final = money(view.todaySales)
      Text(
        "${money(shown)} ؋",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = p.onAccent,
        maxLines = 1,
        style = v3Text(if (final.length > 9) 34 else 42, FontWeight.Bold, 52),
      )
      Spacer(Modifier.height(14.dp))
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PagerDots(pages, currentPage, p.onAccent, p.onAccent.copy(alpha = 0.35f))
      }
    }
    QuickActions(Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).fillMaxWidth().height(Hero.card), onOpen)
  }
}

/** آواتارِ دکان: حرفِ اولِ نام روی دایرهٔ برند با حلقهٔ روشن */
@Composable
internal fun ShopLetter(name: String, size: Dp, ring: Color, onClick: () -> Unit) {
  val p = PhoneV3.colors
  val letter = name.trim().firstOrNull()?.toString() ?: "؟"
  Box(
    Modifier
      .v3Pressable(label = name, scale = 0.92f, onClick = onClick)
      .size(size)
      .clip(CircleShape)
      .background(ring)
      .padding(2.dp)
      .clip(CircleShape)
      .background(Brush.linearGradient(listOf(p.accent, p.accentDeep))),
    contentAlignment = Alignment.Center,
  ) {
    Text(letter, color = p.onAccent, style = v3Text((size.value * 0.4f).toInt(), FontWeight.Bold, (size.value * 0.5f).toInt()))
  }
}

/** زنگِ نیمه‌شفاف روی برند؛ نقطهٔ «تازه» با فنر پیدا می‌شود */
@Composable
private fun GlassBell(dot: Boolean, onClick: () -> Unit) {
  val p = PhoneV3.colors
  val shape = RoundedCornerShape(16.dp)
  Box(
    Modifier
      .v3Pressable(label = StrKit.notifications, scale = 0.92f, onClick = onClick)
      .size(46.dp)
      .clip(shape)
      .background(p.onAccent.copy(alpha = 0.12f))
      .border(1.dp, p.onAccent.copy(alpha = 0.28f), shape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(Icons.Outlined.Notifications, contentDescription = StrKit.notifications, tint = p.onAccent, modifier = Modifier.size(24.dp))
    androidx.compose.animation.AnimatedVisibility(
      dot,
      modifier = Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 12.dp),
      enter = androidx.compose.animation.scaleIn(MotionSpec.morph()) + androidx.compose.animation.fadeIn(),
      exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
    ) {
      Box(Modifier.size(9.dp).clip(CircleShape).background(p.negative).border(1.5.dp, p.accentDeep, CircleShape))
    }
  }
}

private class Quick(val label: String, val icon: ImageVector, val route: String)

/** کارتِ سفیدِ چهار کارِ سریع (r7) — هر کدام مستقیم فرمِ خودش */
@Composable
private fun QuickActions(modifier: Modifier, onOpen: (String) -> Unit) {
  val p = PhoneV3.colors
  val items = listOf(
    Quick(StrHome.newSale, Icons.Outlined.AddShoppingCart, ShellTargets.NEW_SALE),
    Quick(StrHome.receive, Icons.Outlined.CallReceived, ShellTargets.RECEIVE),
    Quick(StrHome.stockEntry, Icons.Outlined.MoveToInbox, ShellTargets.STOCK_ENTRY),
    Quick(StrHome.history, Icons.Outlined.History, ShellTargets.HISTORY),
  )
  //  بارِ اول ۸dp بالا می‌آید و پیدا می‌شود
  val rise = remember { Animatable(if (Motion.enabled) 1f else 0f) }
  LaunchedEffect(Unit) { if (Motion.enabled) rise.animateTo(0f, MotionSpec.tweenOf(MotionTokens.EMPHASIZED, MotionTokens.EmphasizedDecelerate)) }
  val shape = RoundedCornerShape(20.dp)
  Row(
    modifier
      .graphicsLayer { translationY = rise.value * 8.dp.toPx(); alpha = 1f - rise.value * 0.6f }
      .shadow(18.dp, shape, clip = false, ambientColor = p.accentDeep.copy(alpha = 0.18f), spotColor = p.accentDeep.copy(alpha = 0.18f))
      .clip(shape)
      .background(p.card)
      .border(1.dp, p.hairline, shape)
      .padding(horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items.forEach { q ->
      Column(
        Modifier.weight(1f).fillMaxSize().v3Pressable(label = q.label, scale = 0.94f, haptic = q.route == ShellTargets.NEW_SALE) { onOpen(q.route) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(q.icon, contentDescription = null, tint = p.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(q.label, color = p.ink, style = v3Text(12, FontWeight.Medium, 17), maxLines = 1)
      }
    }
  }
}
