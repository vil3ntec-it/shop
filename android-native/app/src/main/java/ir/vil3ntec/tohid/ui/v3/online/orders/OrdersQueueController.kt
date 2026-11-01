package ir.vil3ntec.tohid.ui.v3.online.orders

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.ui.screens.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 *  بارِ صفِ سفارش‌ها — همان کارِ `OrdersScreen` ِ پیشین (`repo.list(tab, cursor)`، صفحه‌به‌صفحه)،
 *  بیرون از نمایش تا صفحه فقط `QueueState` بکشد. سفارشی که در برگهٔ «جدید» تازه پیدا شود
 *  (نه در بارِ نخست) درخشش و یک لرزشِ تأیید می‌گیرد — فقط تا صف جلوی چشم است.
 */
@Stable
internal class OrdersQueueController(private val context: Context, private val scope: CoroutineScope, tab: String) {
  var state by mutableStateOf(QueueState(tab = tab))
    private set
  private var cursor: String? = null
  private var seen: Set<String>? = null
  private var job: Job? = null

  fun selectTab(tab: String) {
    if (tab == state.tab) return
    job?.cancel()
    cursor = null
    seen = null
    state = QueueState(tab = tab)
    reload()
  }

  fun reload() {
    job?.cancel()
    job = scope.launch { load(reset = true) }
  }

  fun more() {
    if (state.loading || cursor == null) return
    job = scope.launch { load(reset = false) }
  }

  private suspend fun load(reset: Boolean) {
    val tab = state.tab
    state = state.copy(loading = true)
    OrdersHub.repo(context).list(tab, if (reset) null else cursor)
      .onSuccess { page ->
        if (tab != state.tab) return@onSuccess
        page.items.forEach(OrdersHub::put)
        val items = if (reset) page.items else (state.items + page.items).distinctBy { it.id }
        val fresh = if (reset && tab == "new") OrdersModel.arrivals(seen, items) else emptySet()
        seen = items.mapTo(HashSet()) { it.id }
        cursor = page.nextCursor
        state = state.copy(
          items = items, loading = false, error = null, hasMore = cursor != null,
          now = System.currentTimeMillis(), fresh = fresh,
        )
        if (fresh.isNotEmpty()) Haptics.confirm(context)
      }
      .onFailure { state = state.copy(loading = false, error = it.userMessage) }
  }
}
