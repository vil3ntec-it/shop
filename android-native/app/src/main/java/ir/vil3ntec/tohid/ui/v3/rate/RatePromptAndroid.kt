package ir.vil3ntec.tohid.ui.v3.rate

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.rate.RateChoice
import ir.vil3ntec.tohid.core.rate.RateMoment
import ir.vil3ntec.tohid.core.rate.RatePrompt
import ir.vil3ntec.tohid.core.rate.RateState
import ir.vil3ntec.tohid.core.rate.RateTarget
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.TrainingMode
import ir.vil3ntec.tohid.rate.RateStoreAndroid
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.v3.V3Depth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** همین بارِ باز بودنِ برنامه (فرآیند): آغاز (پس از بارِ دفتر) و آخرین خطای دیده‌شده — فقط در حافظه */
private object RateSessionAndroid {
  var startedAt = 0L
  var lastErrorAt = 0L
}

private fun now(): Long = System.currentTimeMillis()

/** `Sale.status` ِ فروشِ کامل (نه لغوشده) */
private const val COMPLETED = "completed"

/**
 *  RATE-01 — میزبانِ برگهٔ «ستاره بدهید» ِ برنامهٔ دکان (`AppRoot`، پس از همهٔ دروازه‌ها). قاعده‌ها همه در
 *  `core/rate/RatePrompt.kt`؛ این‌جا فقط «لحظه» ساخته می‌شود:
 *   • فقط نسخهٔ Play و کافه‌بازار ([RateStoreAndroid.target]): APK ِ سایت/گیت‌هاب، پیش‌نمایش و کامپیوتر همان خطِ اول برمی‌گردند
 *     و هیچ تنظیمی هم نمی‌خوانند؛
 *   • فقط روی «خانه»، با سبدِ خالی و بی برگهٔ باز ([onHome]، `V3Depth`) — هرگز وسطِ فروش یا پرداخت؛
 *   • نه در حالتِ آموزشی، نه تا نیم ساعت پس از خطای دیده‌شده (دفتر ننشست، فروش بسته شد، دفترِ ناخوانا، همگام‌سازیِ ناکام)؛
 *   • نه در آغازِ همین بار: سه دقیقه و دست‌کم یک فروشِ کاملِ تازه؛ دست‌کم ۷ روز و ۲۰ فروشِ کامل؛ حداکثر ۳ بار، ۳۰ روز فاصله.
 *  حال در `tohid-ui` ِ همین دستگاه (کلیدِ [RateStoreAndroid.KEY]). هیچ سنجه‌ای فرستاده نمی‌شود.
 */
@Composable
fun RatePromptHost(store: ShopStore, data: ShopData, loaded: Boolean, cart: CartStore, onHome: Boolean) {
  val target = remember { RateStoreAndroid.target(BuildConfig.STORE, BuildConfig.PREVIEW, Platform.DESKTOP) }
  if (target == RateTarget.NONE) return
  val context = LocalContext.current
  val prefs = remember(context) { context.getSharedPreferences(RateStoreAndroid.PREFS, Context.MODE_PRIVATE) }
  var state by remember { mutableStateOf(RatePrompt.decode(prefs.getString(RateStoreAndroid.KEY, null))) }
  fun save(s: RateState) {
    state = s
    prefs.edit().putString(RateStoreAndroid.KEY, RatePrompt.encode(s)).apply()
  }
  LaunchedEffect(Unit) { if (state.firstSeenAt <= 0) save(RatePrompt.seen(state, now())) }
  if (state.stop != null) return

  LaunchedEffect(loaded) { if (loaded && RateSessionAndroid.startedAt <= 0) RateSessionAndroid.startedAt = now() }
  val usage = remember(data) { data.sales.count { it.status == COMPLETED } }
  //  فروشِ کاملی که در همین بار ثبت شد (همان ساعتِ دستگاه که آغاز را نوشت)
  val usedNow = remember(data) { RateSessionAndroid.startedAt > 0 && data.sales.any { it.status == COMPLETED && it.createdAt >= RateSessionAndroid.startedAt } }
  LaunchedEffect(store) { merge(store.diskError.map { }, store.blocked.map { }).collect { RateSessionAndroid.lastErrorAt = now() } }
  val unreadable by store.unreadable.collectAsState()
  val lines by cart.lines.collectAsState()
  val busy = !onHome || lines.isNotEmpty() || V3Depth.sheets.intValue > 0
  val errorNow = unreadable != null || AutoSync.lastError != null
  var open by remember { mutableStateOf(false) }
  LaunchedEffect(busy, errorNow, usage, usedNow, loaded) {
    if (open || busy || !loaded) return@LaunchedEffect
    delay(RatePrompt.SETTLE_MS)
    val startedAt = RateSessionAndroid.startedAt.takeIf { it > 0 } ?: return@LaunchedEffect
    val at = now()
    if (errorNow) RateSessionAndroid.lastErrorAt = at
    val m = RateMoment(
      now = at, target = target, usage = usage, minUsage = RatePrompt.SHOP_MIN_SALES, sessionStartedAt = startedAt,
      usedThisSession = usedNow, busy = busy, lastErrorAt = RateSessionAndroid.lastErrorAt, training = TrainingMode.on,
    )
    if (RatePrompt.shouldAsk(state, m)) {
      save(RatePrompt.shown(state, at))
      open = true
    }
  }
  if (open) {
    RateSheet(target) { choice ->
      open = false
      save(RatePrompt.chose(state, choice, now()))
      if (choice == RateChoice.RATE) RateStoreAndroid.open(context, target)
    }
  }
}
