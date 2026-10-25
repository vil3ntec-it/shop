package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.v3.V3HeroCard
import ir.vil3ntec.tohid.ui.v3.V3HeroChip
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3PagerItem
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** صفحه‌های کادرِ پنهان */
internal object DrawerPages {
  const val DRAWER = 0
  const val SAFE = 1
  const val COUNT = 2
}

/**
 *  سرخطِ خانه (HOME-V2 §۲٫۱): نامِ دکان با ˅ (⇐ حساب و انتخابِ دکان) و زنگ با نقطهٔ هشدار.
 *  بی هیچ چیزِ دیگر.
 */
@Composable
internal fun HomeHeader(view: HomeView, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  val p = PhoneV3.colors
  Row(modifier.fillMaxWidth().padding(horizontal = 12.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
    Row(
      Modifier.weight(1f, fill = false).clip(RoundedCornerShape(12.dp)).v3Pressable(label = view.shopName) { onOpen(ShellTargets.PROFILE) }
        .padding(horizontal = 6.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(view.shopName, color = p.ink, style = v3Text(20, FontWeight.Bold, 28), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
      Spacer(Modifier.width(2.dp))
      Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = p.inkMuted, modifier = Modifier.size(22.dp))
    }
    Spacer(Modifier.weight(1f))
    Box(
      Modifier.v3Pressable(label = StrKit.notifications, scale = 0.92f) { onOpen(ShellTargets.notifications()) }
        .size(42.dp).clip(CircleShape).background(p.card).border(1.dp, p.hairline, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Outlined.Notifications, contentDescription = StrKit.notifications, tint = p.ink, modifier = Modifier.size(22.dp))
      if (view.bellDot) Box(
        Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 10.dp).size(9.dp).clip(CircleShape)
          .background(Shop.colors.danger).border(1.5.dp, p.card, CircleShape),
      )
    }
  }
}

/**
 *  نشانهٔ کادرِ پنهان: دستگیرهٔ ۳۶×۴ زیرِ سرخط و تا نخستین باز شدن یک بار «برای صندوق پایین
 *  بکشید». با کشیدن دستگیره کمی پهن می‌شود و راهنما محو (`progress`، فقط در draw). زدن هم
 *  کادر را باز/بسته می‌کند — برای کسی که کشیدن برایش سخت است.
 */
@Composable
internal fun PullHandle(shown: Boolean, hint: Boolean, progress: () -> Float, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  val p = PhoneV3.colors
  Column(
    modifier
      .fillMaxWidth()
      .v3Pressable(label = if (shown) StrHome.hideDrawer else StrHome.showDrawer, role = Role.Button, scale = 1f, onClick = onToggle)
      .padding(top = 2.dp, bottom = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      Modifier
        .graphicsLayer { scaleX = 1f + 0.35f * progress() }
        .size(width = 36.dp, height = 4.dp)
        .clip(CircleShape)
        .background(p.chevron),
    )
    if (hint && !shown) {
      Spacer(Modifier.height(6.dp))
      Text(
        StrHome.pullHint,
        style = v3Text(12, FontWeight.Medium, 16),
        color = p.inkMuted,
        maxLines = 1,
        modifier = Modifier.graphicsLayer { alpha = 1f - progress() },
      )
    }
  }
}

/**
 *  محتوای کادرِ پنهان (HOME-V2 §۱): ورق‌خورِ افقیِ «صندوق» و «گاوصندوق»، هر صفحه همان
 *  `V3HeroCard` ِ کادرِ اصلی (شعاعِ ۲۸، گرادیانِ تم، دو دایرهٔ نرم) با نشانگرِ صفحه پایینِ کادر.
 *  ورق با انگشت ۱:۱ و نشستنِ فنری؛ صفحهٔ کناری کمی کوچک و کم‌رنگ (`v3PagerItem`).
 */
@Composable
internal fun HomeDrawerPager(view: HomeView, state: PagerState, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  HorizontalPager(
    state = state,
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = 16.dp),
    pageSpacing = 10.dp,
    flingBehavior = PagerDefaults.flingBehavior(state, snapAnimationSpec = MotionSpec.sheet()),
    key = { it },
  ) { page ->
    val m = Modifier.v3PagerItem(state, page)
    if (page == DrawerPages.DRAWER) DrawerFace(view, onOpen, m) else SafeFace(m)
  }
}

/** «صندوق»: نقدِ دخل همین حالا (همان قاعدهٔ `CashShift`)، سودِ امروز (برای کسی که می‌بیند)، دریافت / + / پرداخت */
@Composable
private fun DrawerFace(view: HomeView, onOpen: (String) -> Unit, modifier: Modifier) {
  val cash = rememberCountUp(view.cash)
  val profit = view.profit
  V3HeroCard(
    title = StrHome.drawer,
    label = if (view.shiftOpen) StrHome.drawerCash else StrHome.todayCashClosed,
    value = "${money(cash)} ؋",
    caption = when {
      profit == null -> StrHome.invoicesToday(view.todayCount)
      profit < 0 -> StrHome.todayLoss + " " + "${money(kotlin.math.abs(profit))} ؋"
      else -> StrHome.todayProfit + " " + "${money(profit)} ؋"
    },
    chips = listOf(
      V3HeroChip(StrHome.receive, Icons.Outlined.SouthWest) { onOpen(ShellTargets.RECEIVE) },
      V3HeroChip(StrHome.newSale, Icons.Rounded.Add) { onOpen(ShellTargets.NEW_SALE) },
      V3HeroChip(StrHome.pay, Icons.Outlined.NorthEast) { onOpen(ShellTargets.PAY) },
    ),
    pages = DrawerPages.COUNT,
    page = DrawerPages.DRAWER,
    modifier = modifier,
  )
}

/** «گاوصندوق»: جای‌نگهدار تا `SafeEngine` (SAFE-V1) — همان قالب، بی عددِ ساختگی */
@Composable
private fun SafeFace(modifier: Modifier) {
  V3HeroCard(
    title = StrHome.safe,
    value = "—",
    caption = StrHome.safeSoon,
    pages = DrawerPages.COUNT,
    page = DrawerPages.SAFE,
    modifier = modifier,
  )
}
