package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.sync.v1.SyncDot
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.HomeAlerts

/**
 *  حالت‌های ثابت برای پیش‌نمایش و عکسِ خودکار — برنامهٔ دستِ کاربر همیشه `NONE` می‌فرستد.
 *  `alerts` = هشدارهای نمایشی (عکسِ «۶ هشدار»/«بی هشدار»)، `null` یعنی همان دادهٔ واقعی.
 */
data class HomeShot(
  /** کادرِ پنهان: ۰ بسته، بینِ ۰ و ۱ نیمه‌کشیده (همان کسر از بلندیِ باز)، ۱ باز */
  val reveal: Float = 0f,
  /** صفحهٔ کادرِ پنهان: ۰ صندوق، ۱ گاوصندوق */
  val drawerPage: Int = 0,
  val page: Int = 0,
  /** نامِ بلندِ دکان و عددهای درشت — آزمونِ جا شدن */
  val big: Boolean = false,
  val alerts: List<HomeAlerts.Alert>? = null,
) {
  companion object { val NONE = HomeShot() }
}

/** هر چیزی که خانه و کادرِ بزرگ نشان می‌دهند — یک بار به ازای هر دفتر */
data class HomeView(
  val shopName: String,
  val todaySales: Double,
  val todayCount: Int,
  val cash: Double,
  val shiftOpen: Boolean,
  /** `null` = این نقش عددِ سود را نمی‌بیند */
  val profit: Double?,
  val alerts: List<HomeAlerts.Alert>,
  val sync: HomeAlerts.Sync,
  val queued: Int,
  val canSeeMoney: Boolean,
  /** نقطهٔ زنگ: هشدارِ فوری یا خبرِ دیده‌نشده */
  val bellDot: Boolean,
) {
  fun big(): HomeView = copy(
    shopName = "سوپرمارکت و عمده‌فروشیِ برادرانِ محمدی و شرکا",
    todaySales = 12_345_678.0,
    cash = 98_765_432.0,
    profit = profit?.let { 4_321_987.0 },
  )
}

/**
 *  عددها و هشدارهای خانه از موتورهای امروز (`DashboardNumbers`، `CashShift`،
 *  `SyncV1Engine.status`، `SoftLock`، `OrdersLive`) — هیچ حسابِ تازه‌ای این‌جا نیست.
 */
@Composable
fun rememberHomeView(d: ShopData): HomeView {
  val context = LocalContext.current
  val today = ir.vil3ntec.tohid.todayIso()
  val shopName = remember(d) {
    context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE).getString("store_name", "").orEmpty()
  }
  val canSeeMoney = remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }
  val nums = remember(d, today) { DashboardNumbers.of(d, today) }
  //  نقدِ دخل: صندوقِ باز ⇐ «باید باشد»ِ همان صندوق؛ بسته ⇐ گردشِ نقدِ امروز
  val drawer = remember(d, today) {
    val now = System.currentTimeMillis()
    val shift = CashShift.current(d)
    if (shift != null) CashShift.totalsOf(d, shift, now).expected to true
    else {
      val start = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
      }.timeInMillis
      CashShift.totals(d, start, now, 0.0).expected to false
    }
  }
  val signedIn = remember { ir.vil3ntec.tohid.data.repo.Backend.isReady(context) }
  val status by ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context).status.collectAsState()
  val sync = when {
    !signedIn -> HomeAlerts.Sync.LOCAL_ONLY
    status.dot == SyncDot.RED -> HomeAlerts.Sync.ERROR
    status.dot == SyncDot.YELLOW -> HomeAlerts.Sync.QUEUED
    status.dot == SyncDot.GREY -> HomeAlerts.Sync.OFFLINE
    else -> HomeAlerts.Sync.OK
  }
  val locked by ir.vil3ntec.tohid.sync.v1.SoftLock.locked.collectAsState()
  LaunchedEffect(Unit) { runCatching { ir.vil3ntec.tohid.sync.v1.SoftLock.refresh(context) } }
  val warnDays = remember(locked) { runCatching { ir.vil3ntec.tohid.sync.v1.SoftLock.warnDays(context) }.getOrNull() }
  val ordersLive = OrdersLive.enabled
  val newOrders = OrdersLive.newCount
  val alerts = HomeAlerts.build(
    HomeAlerts.Input(
      ordersLive = ordersLive, newOrders = newOrders,
      subscriptionLocked = locked, subscriptionDaysLeft = warnDays,
      sync = sync, queued = status.queued, syncError = status.lastError,
      outOfStock = nums.outOfStock.size, lowStock = nums.lowStock.size,
      overdueDebtors = nums.overdue.size, overdueTotal = "${money(nums.overdueTotal)} ؋",
      expiring = nums.expiring.size,
    ),
  )
  LaunchedEffect(signedIn) { if (signedIn) ShopFeed.refresh(context) else ShopFeed.load(context) }
  return HomeView(
    shopName = shopName.ifBlank { StrHome.myShop },
    todaySales = nums.todayTotal,
    todayCount = nums.todaySales.size,
    cash = drawer.first,
    shiftOpen = drawer.second,
    profit = if (canSeeMoney) nums.todayProfit else null,
    alerts = alerts,
    sync = sync,
    queued = status.queued,
    canSeeMoney = canSeeMoney,
    bellDot = alerts.any { it.urgent } || ShopFeed.unseen,
  )
}
