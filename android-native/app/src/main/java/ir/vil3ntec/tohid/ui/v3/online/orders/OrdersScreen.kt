package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.online.CountTab
import ir.vil3ntec.tohid.ui.v3.online.DraftCountTabs
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** آنچه صف نشان می‌دهد — بی شبکه؛ `OrdersQueueController` پرش می‌کند، عکس‌ها دستی */
data class QueueState(
  val tab: String = "new",
  val counts: Map<String, Int> = emptyMap(),
  val items: List<OrderDto> = emptyList(),
  val loading: Boolean = true,
  val error: String? = null,
  val hasMore: Boolean = false,
  val perms: OrderPermsDto = OrderPermsDto(),
  val now: Long = 0L,
  /** تازه‌رسیده‌ها (درخششِ «در انتظارِ تأیید») */
  val fresh: Set<String> = emptySet(),
)

class QueueActions(
  val onTab: (String) -> Unit,
  val onOpen: (OrderDto) -> Unit,
  val onStep: (OrderDto, String) -> Unit,
  val onNew: () -> Unit,
  val onRetry: () -> Unit,
  val onMore: () -> Unit,
)

/**
 *  صفِ سفارش‌ها (`orders?tab=`) — online.md: تب‌های `OrderStates.TABS` با شمار (تازه ⇐ نقطهٔ
 *  «در انتظارِ تأیید»، THEME-V2)، کارت‌ها با ورودِ پلکانی و جابه‌جاییِ نرم (`v3AnimateItem`)، اسکلت، خطا با «دوباره»،
 *  خالیِ هر برگه، و «سفارش‌های بیشتر». «+ سفارشِ دستی» فقط با `permissions.create`.
 */
@Composable
fun OrdersScreen(
  state: QueueState,
  actions: QueueActions,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  selectedId: String? = null,
) {
  val stagger = rememberV3Stagger(state.tab)
  V3Scaffold(
    title = StrOnline.ordersTitle,
    subtitle = StrOnline.ordersCaption,
    modifier = modifier,
    onBack = onBack,
    actions = if (state.perms.create) listOf(V3Action(Icons.Outlined.Add, StrOnline.manualOrder, onClick = actions.onNew)) else emptyList(),
    overflow = listOf(V3MenuItem(Icons.Outlined.Refresh, StrOnline.reload, onClick = actions.onRetry)),
    chips = {
      DraftCountTabs(
        tabs = OrderStates.TABS.map { t -> CountTab(t, StrOnline.orderTab(t), state.counts[t] ?: 0, attention = t == "new") },
        selected = state.tab,
        onSelect = actions.onTab,
      )
    },
  ) { pad ->
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "live-note") { V3Message(V3Tone.INFO, StrOnline.liveNote) }
      when {
        state.loading && state.items.isEmpty() -> item(key = "skeleton") { V3Skeleton(rows = 4) }
        state.error != null && state.items.isEmpty() -> item(key = "error") {
          V3EmptyState(Icons.Outlined.CloudOff, StrOnline.listFailed, body = state.error, action = StrKit.retry, onAction = actions.onRetry)
        }
        state.items.isEmpty() -> item(key = "empty") {
          val create = state.tab == "new" && state.perms.create
          V3EmptyState(
            Icons.Outlined.ShoppingBasket, StrOnline.noOrders, body = StrOnline.emptyTab(state.tab),
            action = if (create) StrOnline.manualOrder else null, onAction = if (create) actions.onNew else null,
          )
        }
        else -> {
          itemsIndexed(state.items, key = { _, o -> o.id }) { i, o ->
            OrderCard(
              o = o,
              now = state.now,
              step = OrdersModel.nextStep(o, state.perms),
              onOpen = { actions.onOpen(o) },
              onStep = { to -> actions.onStep(o, to) },
              selected = o.id == selectedId,
              flash = o.id in state.fresh,
              modifier = Modifier.v3AnimateItem(this).v3Stagger(stagger, o.id, i),
            )
          }
          if (state.hasMore) item(key = "more") {
            V3Button(
              StrOnline.moreOrders, actions.onMore, Modifier.fillMaxWidth().padding(top = 4.dp),
              kind = V3ButtonKind.SECONDARY, loading = state.loading,
            )
          }
        }
      }
    }
  }
}
