package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.EventsRepository
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.shell.DraftNoticeBar
import ir.vil3ntec.tohid.ui.v3.shell.HomeScreen
import ir.vil3ntec.tohid.ui.v3.shell.HomeShot
import ir.vil3ntec.tohid.ui.v3.shell.NotificationsScreen
import ir.vil3ntec.tohid.ui.v3.shell.PaletteBody
import ir.vil3ntec.tohid.ui.v3.shell.SearchIndex
import ir.vil3ntec.tohid.ui.v3.shell.ShopFeed
import ir.vil3ntec.tohid.ui.v3.shell.TxSheet
import ir.vil3ntec.tohid.ui.v3.shell.TransactionsScreen
import ir.vil3ntec.tohid.ui.v3.shell.Transactions

/** شش هشدارِ نمایشی (همهٔ گونه‌ها) — عکسِ «۶ هشدار» */
private val SIX = HomeAlerts.build(
  HomeAlerts.Input(
    ordersLive = true, newOrders = 3, subscriptionDaysLeft = 2, sync = HomeAlerts.Sync.ERROR,
    syncError = "سرور پاسخ نداد (HTTP 503) — دوباره تلاش می‌شود", outOfStock = 2, lowStock = 5,
    overdueDebtors = 4, overdueTotal = "18,400 ؋", expiring = 3,
  ),
)

/** خبرهای نمایشیِ دکان (بی دادهٔ واقعی) */
private fun demoNews(): List<EventsRepository.Event> {
  val now = System.currentTimeMillis()
  val m = 60_000L
  return listOf(
    EventsRepository.Event("n1", "sale", "فروش تازه", "1,200 افغانی — 3 قلم", "u2", "احمد", now - 4 * m),
    EventsRepository.Event("n2", "stock_out", "چای سبز تمام شد", "موجودی صفر است", "u3", "کریم", now - 52 * m),
    EventsRepository.Event("n3", "sale", "فروش تازه", "640 افغانی — 2 قلم", "u2", "احمد", now - 26 * 60 * m),
    EventsRepository.Event("n4", "sale", "فروش تازه", "3,050 افغانی — 7 قلم", "u3", "کریم", now - 3 * 24 * 60 * m),
  )
}

private fun withNews() {
  ShopFeed.events.value = demoNews()
  ShopFeed.seenAt.longValue = System.currentTimeMillis() - 60 * 60_000L
}

private fun noNews() {
  ShopFeed.events.value = emptyList()
  ShopFeed.state.value = ShopFeed.State.OFFLINE
}

@Composable
private fun Data(store: ShopStore, content: @Composable (ShopData) -> Unit) {
  val data by store.data.collectAsState()
  content(data)
}

/** نوارهای پوسته در همهٔ گونه‌ها: اشتراک (هشدار/پایان) و نسخهٔ تازه (تازه/در حالِ گرفتن/اجباری) */
@Composable
private fun Notices() {
  Column(
    Modifier.fillMaxSize().background(PhoneV3.colors.bg).statusBarsPadding().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    DraftNoticeBar(V3Tone.WARNING, StrHome.subNoticeDays(5), onClick = {}, action = Str.mySubscription)
    DraftNoticeBar(V3Tone.ERROR, StrHome.subNoticeEnded, onClick = {}, action = Str.mySubscription)
    DraftNoticeBar(V3Tone.INFO, StrHome.updateNew("2.15.0"), onClick = {}, icon = Icons.Outlined.SystemUpdate, action = StrHome.install, onClose = {})
    DraftNoticeBar(V3Tone.INFO, StrHome.updateGetting("2.15.0", 42), onClick = {}, icon = Icons.Outlined.SystemUpdate, progress = 0.42f)
    DraftNoticeBar(V3Tone.ERROR, StrHome.updateRequired("2.15.0"), onClick = {}, icon = Icons.Outlined.SystemUpdate, action = StrHome.update)
  }
}

/**
 *  عکس‌های گروهِ G10 پوسته: خانه (r7، ۰ و ۶ هشدار، کارتِ کششی با ورقِ صندوق/گاوصندوق، آمار،
 *  نامِ بلند)، تراکنش‌ها و برگهٔ اقلام، مرکزِ هشدارها (هر دو تب: پر/خالی/بی‌اتصال)، جست‌وجو
 *  (خالی/نتیجه/هیچ)، فهرستِ فرمانِ کامپیوتر و نوارهای اشتراک/نسخه.
 */
object ShellShots : GroupShots {
  override val group = "shell"
  override val shots = listOf(
    Shot("home", route = "home"),
    Shot("home-6-alerts", content = { s -> Data(s) { HomeScreen(it, {}, PhoneNav.RESERVE, HomeShot(alerts = SIX)) } }),
    Shot("home-0-alerts", content = { s -> Data(s) { HomeScreen(it, {}, PhoneNav.RESERVE, HomeShot(alerts = emptyList())) } }),
    Shot("home-pull", content = { s -> Data(s) { HomeScreen(it, {}, PhoneNav.RESERVE, HomeShot(pull = true)) } }),
    Shot("home-pull-safe", content = { s -> Data(s) { HomeScreen(it, {}, PhoneNav.RESERVE, HomeShot(pull = true, pullPage = 1)) } }),
    Shot("home-stats", content = { s -> Data(s) { HomeScreen(it, {}, PhoneNav.RESERVE, HomeShot(page = 1)) } }),
    Shot("home-big", content = { s -> Data(s) { HomeScreen(it, {}, PhoneNav.RESERVE, HomeShot(big = true, pull = true)) } }),
    Shot("transactions", route = "transactions"),
    Shot("transaction-sheet", content = { s ->
      Data(s) { d ->
        TransactionsScreen(d, null, onOpen = {}, onBack = {}, bottomReserve = 0.dp)
        Transactions.list(d).firstOrNull { it.itemCount > 0 }?.let { tx -> TxSheet(d, tx, onDismiss = {}, onOpen = {}) }
      }
    }),
    Shot("notifications", route = "notifications", prepare = ::withNews),
    Shot("notifications-news", route = "notifications?tab=news", prepare = ::withNews),
    Shot("notifications-news-offline", route = "notifications?tab=news", prepare = ::noNews),
    Shot("notifications-empty", prepare = ::noNews, content = { NotificationsScreen(ShopData(), null, onOpen = {}, onBack = {}) }),
    Shot("search", route = "search"),
    Shot("search-results", route = "search?q=" + ir.vil3ntec.tohid.core.nav.RoutePaths.encode("چ")),
    Shot("search-none", route = "search?q=zzzz"),
    Shot("palette", content = { s -> Data(s) { PaletteBody(it, SearchIndex.sections(), {}, {}, initialQuery = "چ") } }),
    Shot("notices", content = { Notices() }),
  )
}
