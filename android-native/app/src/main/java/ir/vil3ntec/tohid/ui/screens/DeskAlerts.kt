package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ir.vil3ntec.tohid.data.DashboardEngine
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 *  PERF-DESK — هشدارهای پوستهٔ کامپیوتر (زنگِ سربرگ `DeskHeader` و شمارِ کنارِ «کالاها»، «گدام» و «قرض‌داران» ِ
 *  `DeskRail`): یک بار برای هر دفتر و **بیرون از رشتهٔ صفحه**.
 *
 *  پیش‌تر هر دو جا با هر عوض شدنِ دفتر (هر فروش، هر همگام‌سازی) کلِ `DashboardEngine.board` را درونِ ترکیب می‌ساختند —
 *  دو بار، روی رشتهٔ صفحه، با گشتنِ همهٔ فاکتورها و قلم‌ها؛ با دفترِ چندساله پنجره در همان لحظه می‌ایستاد. حالا فقط
 *  [DashboardEngine.alerts] (همان چهار فهرست، همان فرمول) و یک بار: دومی همان نتیجهٔ اولی را می‌گیرد.
 *  تا نتیجهٔ دفترِ تازه برسد، شمارِ دفترِ پیشین می‌ماند (چند ده میلی‌ثانیه)، نه صفر و نه ایست.
 */
internal object DeskAlerts {
  private class Entry(val data: ShopData, val today: String, val alerts: DashboardEngine.Alerts)

  @Volatile private var last: Entry? = null

  /** همان دفتر (همان نمونه) و همان روز ⇒ نتیجهٔ آماده */
  fun cached(d: ShopData, today: String): DashboardEngine.Alerts? = last?.takeIf { it.data === d && it.today == today }?.alerts

  /** یک بار برای هر دفتر؛ دو صدازنندهٔ هم‌زمان پشتِ [gate] (`rememberDeskAlerts`) می‌مانند و دومی همان نتیجه را می‌گیرد */
  @OptIn(kotlin.time.ExperimentalTime::class)
  fun of(d: ShopData, today: String): DashboardEngine.Alerts = cached(d, today)
    ?: DashboardEngine.alerts(d, today, DAYS, kotlin.time.Clock.System.now().toEpochMilliseconds()).also { last = Entry(d, today, it) }

  /** قفلِ هم‌روالی (چندسکویی، نه `synchronized` ِ JVM) برای سربرگ و منو که با هم می‌پرسند */
  val gate = Mutex()

  /** بازهٔ تخته‌ای که سربرگ و منو پیش‌تر می‌ساختند (`board(d, today, 7)`) */
  const val DAYS = 7
}

/** هشدارهای همین دفتر برای سربرگ و منوی کناری — بیرون از رشتهٔ صفحه ([DeskAlerts]) */
@Composable
internal fun rememberDeskAlerts(d: ShopData): DashboardEngine.Alerts {
  val today = ir.vil3ntec.tohid.todayIso()
  val state = remember { mutableStateOf(DeskAlerts.cached(d, today) ?: DashboardEngine.Alerts.NONE) }
  LaunchedEffect(d, today) {
    state.value = DeskAlerts.cached(d, today) ?: withContext(Dispatchers.Default) { DeskAlerts.gate.withLock { DeskAlerts.of(d, today) } }
  }
  return state.value
}
