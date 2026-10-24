package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.screens.isDark
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.analytics.AnalyticsPaths
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** شعاعِ کارت‌های خانواده (THEME-V2): کارت ۲۰، ردیف/کاشی ۱۴ */
internal object HomeCardSpec {
  val card = 20.dp
  val tile = 14.dp
}

/**
 *  سه صفحهٔ کادرِ بزرگ با یک قالب (owner-feedback-3 B + THEME-V2): «فروشِ امروز»، «صندوق»
 *  (نقدِ دخل، همان قاعدهٔ `CashShift`) و «گاوصندوق» (جای‌نگهدار تا `SafeEngine`).
 */
internal fun heroFaces(view: HomeView): List<HeroFace> {
  val profit = view.profit
  val syncLabel = when (view.sync) {
    HomeAlerts.Sync.OK -> StrHome.synced
    HomeAlerts.Sync.QUEUED -> StrHome.syncWaiting
    HomeAlerts.Sync.OFFLINE -> StrHome.offline
    HomeAlerts.Sync.ERROR -> StrHome.syncFailed
    HomeAlerts.Sync.LOCAL_ONLY -> StrHome.localOnly
  }
  return listOf(
    HeroFace(
      key = "sales",
      label = StrHome.todaySales,
      amount = view.todaySales,
      caption = StrHome.invoicesToday(view.todayCount),
      chips = listOf(
        HeroChip(StrHome.newSale, Icons.Outlined.AddShoppingCart, ShellTargets.NEW_SALE, haptic = true),
        HeroChip(Str.reports, Icons.Outlined.InsertChart, AnalyticsPaths.REPORTS),
      ),
    ),
    HeroFace(
      key = "drawer",
      label = StrHome.drawer + " · " + if (view.shiftOpen) StrHome.drawerCash else StrHome.todayCashClosed,
      amount = view.cash,
      caption = when {
        profit == null -> StrHome.invoicesToday(view.todayCount)
        profit < 0 -> StrHome.todayLoss + " " + "${money(kotlin.math.abs(profit))} ؋"
        else -> StrHome.todayProfit + " " + "${money(profit)} ؋"
      },
      chips = listOf(
        HeroChip(StrHome.receive, Icons.Outlined.SouthWest, ShellTargets.RECEIVE),
        HeroChip(StrHome.pay, Icons.Outlined.NorthEast, ShellTargets.PAY),
        HeroChip(syncLabel, Icons.Outlined.CloudSync, ShellTargets.SYNC),
      ),
    ),
    HeroFace(key = "safe", label = StrHome.safe, amount = null, caption = StrHome.safeSoon, chips = listOf(HeroChip(StrHome.soon, null, null)), live = false),
  )
}

private class Shortcut(val label: String, val icon: ImageVector, val route: String, val accent: HomeAccent, val haptic: Boolean = false)

/**
 *  کارتِ میان‌برها (خانوادهٔ کارت‌ها: شعاعِ ۲۰، مرزِ ۱dp) — هر میان‌بر رنگِ همراهِ خودش،
 *  آیکنِ رنگی روی پس‌زمینهٔ نرمِ همان رنگ؛ هر کدام مستقیم فرمِ خودش (`ShellTargets`).
 */
@Composable
internal fun HomeShortcuts(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  val p = PhoneV3.colors
  val items = listOf(
    Shortcut(StrHome.newSale, Icons.Outlined.AddShoppingCart, ShellTargets.NEW_SALE, HomeAccents.NEW_SALE, haptic = true),
    Shortcut(StrHome.receive, Icons.Outlined.CallReceived, ShellTargets.RECEIVE, HomeAccents.RECEIVE),
    Shortcut(StrHome.stockEntry, Icons.Outlined.MoveToInbox, ShellTargets.STOCK_ENTRY, HomeAccents.STOCK_ENTRY),
    Shortcut(StrHome.history, Icons.Outlined.History, ShellTargets.HISTORY, HomeAccents.HISTORY),
  )
  V3Card(modifier.padding(horizontal = HeroSpec.margin), radius = HomeCardSpec.card, padding = PaddingValues(vertical = 12.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
      items.forEach { s ->
        Column(
          Modifier.weight(1f).v3Pressable(label = s.label, scale = 0.94f, haptic = s.haptic) { onOpen(s.route) },
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(HomeCardSpec.tile)).background(HomeAccents.soft(p, s.accent, p.isDark)),
            contentAlignment = Alignment.Center,
          ) { Icon(s.icon, contentDescription = null, tint = HomeAccents.color(p, s.accent), modifier = Modifier.size(23.dp)) }
          Spacer(Modifier.height(6.dp))
          Text(s.label, color = p.ink, style = v3Text(12, FontWeight.Medium, 17), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
