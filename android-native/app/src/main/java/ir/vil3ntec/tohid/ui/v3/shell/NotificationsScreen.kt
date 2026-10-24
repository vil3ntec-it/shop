package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import kotlinx.coroutines.launch

/** تب‌های مرکزِ هشدارها (`notifications?tab=alerts|news`) */
object NotificationTabs {
  const val ALERTS = "alerts"
  const val NEWS = "news"
  fun index(tab: String?): Int = if (tab == NEWS) 1 else 0
}

/**
 *  مرکزِ هشدارها (`notifications`، PAGE) — **یک** صفحه به‌جای سه فهرستِ پیشین؛ هر زنگ
 *  همین را باز می‌کند. «هشدارها»: دو گروهِ «فوری» و «بقیه» (`ShopAlerts`)؛ ردیف و کارش
 *  مستقیم سرِ همان سند. «خبرِ دکان»: کارهای بقیهٔ اعضا روزبه‌روز (`NewsModel`)؛ کشیدن به
 *  پایین دوباره می‌پرسد و شکست بی‌صدا «اتصال نیست» می‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(d: ShopData, initialTab: String?, onOpen: (String) -> Unit, onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var tab by rememberSaveable { mutableIntStateOf(NotificationTabs.index(initialTab)) }
  val home = rememberHomeView(d)
  val groups = rememberShopAlerts(d, home.alerts)
  val feedState by ShopFeed.state
  val events by ShopFeed.events
  //  «دیده‌نشده» همان لحظهٔ باز شدن سنجیده می‌شود تا نقطه‌ها همین بار دیده شوند
  val seenAtOpen = remember { ShopFeed.load(context); ShopFeed.seenAt.longValue.coerceAtLeast(0L) }
  val me by ShopFeed.me
  val now = remember(events) { System.currentTimeMillis() }
  val days = remember(events, seenAtOpen, me, now) {
    NewsModel.days(events, me, now, seenAtOpen, ::dayOf) { iso -> formatDate(iso) }
  }
  val unseen = days.sumOf { day -> day.rows.count { it.unseen } }
  LaunchedEffect(tab, events) { if (tab == 1) ShopFeed.markSeen(context) }

  val refreshing = feedState == ShopFeed.State.LOADING
  val pull = rememberPullToRefreshState()
  V3Scaffold(
    title = StrKit.notifications,
    onBack = onBack,
    chips = {
      V3Segmented(
        options = listOf(StrHome.tabAlerts, if (unseen > 0) "${StrHome.tabNews} · $unseen" else StrHome.tabNews),
        selected = tab,
        onSelect = { tab = it },
        modifier = Modifier.padding(horizontal = 16.dp),
      )
    },
  ) { pad ->
    PullToRefreshBox(
      isRefreshing = refreshing,
      onRefresh = { scope.launch { ShopFeed.refresh(context) } },
      state = pull,
      modifier = Modifier.fillMaxSize(),
      indicator = {
        PullToRefreshDefaults.Indicator(
          state = pull,
          isRefreshing = refreshing,
          modifier = Modifier.align(Alignment.TopCenter),
          containerColor = PhoneV3.colors.card,
          color = PhoneV3Palette.iconActive(PhoneV3.colors),
        )
      },
    ) {
      V3PageSwitch(tab, depth = { it }, modifier = Modifier.fillMaxSize()) { t ->
        if (t == 0) AlertsTab(groups, pad, onOpen) else NewsTab(days, feedState, pad)
      }
    }
  }
}

@Composable
private fun AlertsTab(groups: ShopAlerts.Groups, pad: PaddingValues, onOpen: (String) -> Unit) {
  if (groups.isEmpty) {
    Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
      V3EmptyState(Icons.Outlined.CheckCircle, StrHome.alertsEmpty)
    }
    return
  }
  val stagger = rememberV3Stagger()
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
    item(key = "urgent") { AlertGroup(StrHome.groupUrgent, groups.urgent, stagger, 0, onOpen) }
    item(key = "rest") { AlertGroup(StrHome.others, groups.rest, stagger, groups.urgent.size, onOpen) }
  }
}

@Composable
private fun NewsTab(days: List<NewsModel.Day>, state: ShopFeed.State, pad: PaddingValues) {
  when {
    days.isEmpty() && (state == ShopFeed.State.LOADING || state == ShopFeed.State.IDLE) ->
      Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp)) { V3Skeleton(rows = 4) }
    days.isEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
      if (state == ShopFeed.State.OFFLINE) V3EmptyState(Icons.Outlined.WifiOff, StrHome.newsEmpty, body = StrHome.noConnection)
      else V3EmptyState(Icons.Outlined.Forum, StrHome.newsEmpty, body = StrHome.newsEmptyBody)
    }
    else -> {
      val stagger = rememberV3Stagger()
      LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
        var offset = 0
        days.forEach { day ->
          val start = offset
          item(key = "day:" + day.label) { NewsDay(day, stagger, start) }
          offset += day.rows.size
        }
      }
    }
  }
}

/** روزِ محلیِ یک لحظه به شکلِ `YYYY-MM-DD` */
internal fun dayOf(at: Long): String {
  val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
  val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
  val dd = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
  return "${c.get(java.util.Calendar.YEAR)}-$m-$dd"
}
