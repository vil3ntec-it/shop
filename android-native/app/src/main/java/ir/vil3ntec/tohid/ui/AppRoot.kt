package ir.vil3ntec.tohid.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.screens.AppLockScreen
import ir.vil3ntec.tohid.ui.screens.CommandPalette
import ir.vil3ntec.tohid.ui.screens.DESK_RAIL_COMPACT
import ir.vil3ntec.tohid.ui.screens.DESK_RAIL_WIDTH
import ir.vil3ntec.tohid.ui.screens.DeliveryGate
import ir.vil3ntec.tohid.ui.screens.DeskHeader
import ir.vil3ntec.tohid.ui.screens.DeskRail
import ir.vil3ntec.tohid.ui.screens.DeskStatusBar
import ir.vil3ntec.tohid.ui.screens.DraftRecoveryPrompt
import ir.vil3ntec.tohid.ui.screens.LockSetupScreen
import ir.vil3ntec.tohid.ui.screens.PageWidth
import ir.vil3ntec.tohid.ui.screens.PhoneLinkHost
import ir.vil3ntec.tohid.ui.screens.PhotoPrefs
import ir.vil3ntec.tohid.ui.screens.RailMode
import ir.vil3ntec.tohid.ui.screens.Screen
import ir.vil3ntec.tohid.ui.screens.ShopSetupScreen
import ir.vil3ntec.tohid.ui.screens.StorefrontGate
import ir.vil3ntec.tohid.ui.screens.TohidLoadingState
import ir.vil3ntec.tohid.ui.screens.screenSize
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.LocalV3Notice
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.nav.ModuleFlags
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3RouteHost
import ir.vil3ntec.tohid.ui.v3.nav.ShellFrame
import ir.vil3ntec.tohid.ui.v3.nav.rememberV3Nav
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell

/**
 *  ریشهٔ برنامهٔ دکان (حالتِ «دکان») — پوسته، نه صفحه.
 *
 *  ناوبری یک پشتهٔ نوع‌دار است (`V3Nav`) و هر صفحه از فهرستِ ماژول‌ها
 *  (`V3Modules`) کشیده می‌شود (`V3RouteHost`)؛ این‌جا فقط کارهای سراسری می‌ماند:
 *  همگام‌سازی، دروازه‌ها (قفل، ساختِ دکان، پیشنهادِ رمز)، اعلان‌ها و لوکیشن،
 *  پیام‌های سراسری، منوی کناری و سربرگِ کامپیوتر، و نوارِ شناورِ پایینِ گوشی.
 *  سربرگِ آبیِ قدیم (`TohidTopBar`) روی گوشی دیگر نیست: هر صفحه `V3Scaffold`
 *  خودش را دارد و نوارهای اشتراک/به‌روزرسانی در جای `notice` ِ همان سربرگ‌اند.
 */
@Composable
fun AppRoot(
  store: ShopStore,
  theme: ir.vil3ntec.tohid.ui.theme.ThemeChoice,
  onTheme: (ir.vil3ntec.tohid.ui.theme.ThemeChoice) -> Unit,
) {
  //  پرچمِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز (`ui/legacy`، `UiV3`)
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { AppRootLegacy(store, theme, onTheme); return }
  val context = LocalContext.current
  //  پرچمِ موتورِ تازهٔ موجودی (`shop.stock_v2`، پیش‌فرض خاموش) پیش از نخستین صفحه
  remember(context) { ir.vil3ntec.tohid.data.StockFlag.load(context) }
  val scope = rememberCoroutineScope()
  val data by store.data.collectAsState()
  val loaded by store.loaded.collectAsState()
  val cartStore = remember { CartStore(context) }
  val nav = rememberV3Nav(V3Modules.table, "home")
  //  OPS-06: صفحهٔ باز برای راهنمای F1 (پنجرهٔ کامپیوتر بیرونِ همین درخت است)؛ شناسهٔ راهنما همان
  //  مسیرِ رابطِ امروز است (`ScreenGuides`)، پس «خانه» ِ نسخهٔ ۳ همان `dashboard`
  LaunchedEffect(nav.current) { AppNav.screen.value = RoutePaths.base(nav.current).let { if (it == "home") "dashboard" else it } }
  var migration by remember { mutableStateOf<String?>(null) }

  //  «رمز را دوباره بزنید» برای هر کارِ حساس (`reauth_required`، بندِ ۲۱.۱)
  ReauthHost()

  /*
   *  دکانِ حساب — پرسیدنش فقط وقتی معلوم شود که ندارد. `askShop` یعنی «یک بار
   *  دیگر بپرس»، `needsShop` یعنی «سرور گفت ندارد» — جدا، تا خطای شبکه صفحهٔ
   *  ساختِ دکان را به‌اشتباه باز نکند.
   */
  var askShop by rememberSaveable { mutableStateOf(true) }
  var needsShop by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(askShop) {
    if (!askShop) return@LaunchedEffect
    askShop = false
    if (!ir.vil3ntec.tohid.data.repo.Backend.isReady(context)) return@LaunchedEffect
    ir.vil3ntec.tohid.data.repo.Backend.shop(context).current()
      .onSuccess { state ->
        needsShop = !state.hasShop
        state.role?.let { ir.vil3ntec.tohid.data.ShopRole.remember(context, it) }
        if (state.hasShop) ir.vil3ntec.tohid.data.ShopRole.rememberPolicy(context, state.policy?.toString())
        if (state.hasShop) ir.vil3ntec.tohid.data.ShopRole.rememberShopId(context, state.shop?.id)
        //  حساب از دکانی به دکانِ دیگر رفته ⇒ دفترِ روی گوشی هم همین‌جا جابه‌جا می‌شود
        if (state.hasShop) {
          runCatching { ir.vil3ntec.tohid.data.LedgerOwner.shopChanged(context, store, state.shop?.id.orEmpty()) }
        }
      }
      .onFailure { failure ->
        if ((failure as? ir.vil3ntec.tohid.core.net.ApiFailure)?.code == "no_shop") needsShop = true
        //  این حساب حالا «پیک» است (DLV-01) ⇒ حالتِ پیک
        if ((failure as? ir.vil3ntec.tohid.core.net.ApiFailure)?.code == "courier_scope") {
          ir.vil3ntec.tohid.data.ShopRole.remember(context, ir.vil3ntec.tohid.data.ShopRole.COURIER)
        }
      }
  }
  //  همگام‌سازی گفت دکان نیست ⇒ همان‌جا بپرس
  LaunchedEffect(ir.vil3ntec.tohid.sync.AutoSync.needsShop) {
    if (ir.vil3ntec.tohid.sync.AutoSync.needsShop) needsShop = true
  }

  //  قفلِ برنامه؛ و قفلِ خودکار پس از بی‌کاری (STF-05، پیش‌فرض خاموش)
  val lockStore = remember { ir.vil3ntec.tohid.data.LockStore(context) }
  var unlocked by rememberSaveable { mutableStateOf(false) }
  val lastActivity = remember { java.util.concurrent.atomic.AtomicLong(System.nanoTime() / 1_000_000) }
  LaunchedEffect(unlocked) {
    if (!unlocked) return@LaunchedEffect
    lastActivity.set(System.nanoTime() / 1_000_000)
    while (true) {
      kotlinx.coroutines.delay(15_000)
      if (ir.vil3ntec.tohid.core.perm.IdleLock.shouldLock(
          lockStore.enabled, lockStore.idleMinutes, lastActivity.get(), System.nanoTime() / 1_000_000,
        )) {
        unlocked = false
        break
      }
    }
  }

  //  یک بار: دفترِ دکان از نسخهٔ قبلی آورده می‌شود
  LaunchedEffect(Unit) {
    if (store.hasData()) return@LaunchedEffect
    val legacy = runCatching { ir.vil3ntec.tohid.data.Migration.readLegacyData(context) }.getOrNull()
    if (legacy.isNullOrBlank()) return@LaunchedEffect
    store.importJson(legacy)
      .onSuccess { migration = StrNav.migrationOk }
      .onFailure { migration = StrNav.migrationFailed }
  }

  //  همگام‌سازیِ خودکار هر بار که برنامه جلوی چشم می‌آید؛ در پس‌زمینه خاموش
  LifecycleEventEffect(Lifecycle.Event.ON_START) {
    if (loaded) {
      ir.vil3ntec.tohid.sync.AutoSync.now(context, store)
      ir.vil3ntec.tohid.sync.AutoSync.startPolling(context, store)
      ir.vil3ntec.tohid.sync.ShopNews.flush(context)
    }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
    ir.vil3ntec.tohid.sync.AutoSync.stopPolling()
  }
  //  بخشِ «سفارش‌ها»: فقط اگر سرور برای این دکان روشن گفته، و فقط وقتی برنامه جلوی چشم است
  OrdersLive.Host(store)
  //  تخفیف‌های دکان (PRM): پیش‌فرض خاموش؛ نسخهٔ همین دستگاه برای کارِ آفلاین؛ کدها با پیش‌نویسِ سبد
  PromoLive.Host(cartStore)

  //  اگر دفتر دیرتر از ON_START آماده شد، همان‌جا شروع می‌کنیم
  LaunchedEffect(loaded) {
    if (loaded) {
      ir.vil3ntec.tohid.sync.AutoSync.now(context, store)
      ir.vil3ntec.tohid.sync.AutoSync.startPolling(context, store)
      ir.vil3ntec.tohid.sync.ShopNews.flush(context)
    }
  }

  //  یادآوریِ روزانه، خبرِ فوری و پشتیبانِ شبانه؛ اجازهٔ اعلان (اندروید ۱۳+) یک بار
  val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  //  لوکیشنِ دکان — فقط با رضایتِ صریح (F0-07)
  val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted.values.any { it }) scope.launch { ir.vil3ntec.tohid.sync.LocationPing.send(context) }
  }
  LaunchedEffect(Unit) {
    ir.vil3ntec.tohid.data.Reminders.schedule(context)
    ir.vil3ntec.tohid.data.Watchman.schedule(context)
    ir.vil3ntec.tohid.data.AutoBackup.schedule(context)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      val granted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.POST_NOTIFICATIONS,
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      if (!granted) runCatching { askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    if (ir.vil3ntec.tohid.data.DeviceLocation.granted(context)) {
      ir.vil3ntec.tohid.sync.LocationPing.send(context)
    } else if (ir.vil3ntec.tohid.sync.LocationPing.shouldAsk(context)) {
      runCatching { askLocation.launch(ir.vil3ntec.tohid.data.DeviceLocation.PERMISSIONS) }
    }
    //  «من آمدم» — هیچ داده‌ای از دفترِ دکان نمی‌رود
    ir.vil3ntec.tohid.sync.VisitPing.send(context)
  }
  LaunchedEffect(data) {
    if (loaded) {
      ir.vil3ntec.tohid.sync.AutoSync.nudge(context, store)
      ir.vil3ntec.tohid.sync.ShopNews.checkStock(context, data)
    }
  }

  val snackbar = remember { SnackbarHostState() }
  val toasts = rememberV3Toasts(snackbar)
  LaunchedEffect(migration) { migration?.let { toasts.show(it) } }
  //  POS-13: فاکتورِ نیمه‌کارهٔ بارِ پیش — ادامه یا دور انداختن
  DraftRecoveryPrompt(cartStore, data, loaded, snackbar)
  //  HW-02/03: کشوی بی فروش (`no_sale`) و چاپِ دوباره (`reprint`) از صفِ چاپ ⇐ سابقهٔ دکان.
  //  `appendAudit` روی دفترِ همان لحظه و زیرِ قفلِ نوشتن می‌سازد (نه تصویرِ کهنهٔ `data.value`)
  //  تا فروشِ هم‌زمان گم نشود؛ پس از بارِ دفتر، و مثلِ هر نوشتنی همگام می‌شود
  LaunchedEffect(store, loaded) {
    if (loaded) ir.vil3ntec.tohid.print.PrintSpooler.attachAudit { row -> store.appendAudit(row) }
  }
  //  نوشتنی که قفلِ نرمِ پایانِ اشتراک ردش کرد ⇐ راستش گفته می‌شود، با راهِ تمدید
  LaunchedEffect(store) {
    store.blocked.collect {
      snackbar.currentSnackbarData?.dismiss()
      val r = snackbar.showSnackbar(StrNav.subscriptionBlocked, actionLabel = ir.vil3ntec.tohid.core.i18n.Str.renew, duration = SnackbarDuration.Long)
      if (r == SnackbarResult.ActionPerformed) nav.open("subscription/plans")
    }
  }
  //  دفتر روی دیسک ننشست (F0-01) ⇐ پیامِ «ثبت شد» برداشته و راستش گفته می‌شود
  LaunchedEffect(store) {
    store.diskError.collect { reason ->
      snackbar.currentSnackbarData?.dismiss()
      snackbar.showSnackbar(StrNav.diskError(reason), duration = SnackbarDuration.Long)
    }
  }

  //  SEC-01: دفترِ رمزدار باز نشد ⇐ نوشتن بسته است (`ShopStore.unreadable`)؛ پنجره با
  //  «بازسازی از سرور» و راهِ «تنظیمات ← امنیت» (بازبینیِ #331). با دفترِ ساده هرگز پیش نمی‌آید.
  val unreadable by store.unreadable.collectAsState()
  var unreadableOpen by remember { mutableStateOf(false) }
  LaunchedEffect(unreadable) { unreadableOpen = unreadable != null }
  //  پشتِ قفلِ برنامه نه: پنجره رویِ صفحهٔ قفل باز نمی‌شود
  if (unreadableOpen && unreadable != null && !(lockStore.enabled && !unlocked)) {
    ir.vil3ntec.tohid.ui.screens.UnreadableLedgerDialog(
      store = store,
      onOpenSecurity = { unreadableOpen = false; AppNav.go("settings") },
      onDismiss = { unreadableOpen = false },
      toast = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg, duration = SnackbarDuration.Long) } },
    )
  }
  //  نتیجهٔ بازیابیِ پشتیبان وقتی دفترِ رمزدار باز نمی‌شود (`ShopStore.notice`)
  LaunchedEffect(store) {
    store.notice.collect { msg ->
      snackbar.currentSnackbarData?.dismiss()
      snackbar.showSnackbar(msg, duration = SnackbarDuration.Long)
    }
  }

  //  دروازه‌های خودکار: تا یکی باز است پیوندهای بیرونی نگه داشته می‌شوند
  val syncState = remember { ir.vil3ntec.tohid.sync.SyncStore(context) }
  var lockAsked by rememberSaveable { mutableStateOf(false) }
  val hasAccount = !syncState.accessToken.isNullOrBlank()
  val locked = lockStore.enabled && !unlocked
  val askLock = hasAccount && !lockStore.enabled && !lockAsked && !syncState.lockDeclined
  val held = !loaded || locked || needsShop || askLock
  SideEffect { nav.held = held }
  if (locked) { AppLockScreen { unlocked = true }; return }
  //  «دکانت کدام است؟» — فقط وقتی مطمئنیم دکانی نیست
  if (needsShop) { ShopSetupScreen(store) { needsShop = false }; return }
  //  رمزِ برنامه، یک بار، بعد از ساختنِ حساب
  if (askLock) {
    LockSetupScreen {
      lockAsked = true
      syncState.lockDeclined = true
      unlocked = true
    }
    return
  }

  //  کامپیوتر همیشه چیدمانِ کامپیوتری دارد (منوی کناری، نوارِ وضعیت) — حتی پنجرهٔ باریک
  val wide = Platform.DESKTOP || screenSize() == Screen.WIDE
  val uiPrefs = remember { context.getSharedPreferences("tohid-ui", 0) }
  var railMode by remember { mutableStateOf(uiPrefs.getString("desk_rail", RailMode.FULL) ?: RailMode.FULL) }
  var railOverlay by remember { mutableStateOf(false) }
  fun setRail(m: String) {
    railMode = m
    railOverlay = false
    uiPrefs.edit().putString("desk_rail", m).apply()
  }
  val railRequest = AppNav.rail.value
  LaunchedEffect(railRequest) { if (railRequest != null) { setRail(railRequest); AppNav.rail.value = null } }
  /*
   *  نسخهٔ ۲ — منو در صندوق (بازبینیِ ۱۴۰۵/۰۷/۲۷): صندوق حالتِ منوی **خودش** را دارد
   *  (`desk_rail_pos`، پیش‌فرض «فقط آیکن») تا کاتالوگ جا بگیرد؛ بقیهٔ صفحه‌ها همان انتخابِ
   *  کاربر (`desk_rail`). «پنهان» در همه‌جا پنهان است.
   */
  val onSale = Platform.DESKTOP && RoutePaths.base(nav.current) == "sale"
  var posRail by remember { mutableStateOf(uiPrefs.getString("desk_rail_pos", RailMode.COMPACT) ?: RailMode.COMPACT) }
  fun setPosRail(m: String) {
    posRail = m
    railOverlay = false
    uiPrefs.edit().putString("desk_rail_pos", m).apply()
  }
  val shownRail = if (onSale && railMode != RailMode.HIDDEN) posRail else railMode
  /** حالتِ منوی همین صفحه را عوض می‌کند (صندوق: `desk_rail_pos`؛ بقیه: `desk_rail`) */
  fun setShownRail(m: String) { if (onSale && m != RailMode.HIDDEN) setPosRail(m) else setRail(m) }
  /** ☰: ثابت ⇄ آیکن (برای همین صفحه)؛ در حالتِ پنهان، منو را روی صفحه باز/بسته می‌کند */
  fun onMenuPress() {
    when (shownRail) {
      RailMode.FULL -> setShownRail(RailMode.COMPACT)
      RailMode.COMPACT -> setShownRail(RailMode.FULL)
      else -> railOverlay = !railOverlay
    }
  }

  //  نوارِ پایینِ گوشی قابلِ تنظیم است (`NavLayouts`): هر چه در نوار نشسته «تب» است
  val phoneNav = !wide
  val canManage = ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context)
  val navAllowed = NavCatalog.allowed(canManage = canManage, ordersLive = OrdersLive.enabled)
  val navUser = runCatching { syncState.accountId }.getOrDefault("")
  LaunchedEffect(navUser, navAllowed) { PhoneNav.layout.value = PhoneNav.load(context, navAllowed) }
  LaunchedEffect(Unit) { ModuleFlags.load(context) }
  val navLayout = NavLayouts.normalize(PhoneNav.layout.value, navAllowed)
  val navRoutes = navLayout.ids.mapNotNull { NavCatalog.of(it)?.route?.substringBefore('?') }.toSet() + "home"
  nav.wide = wide
  nav.sectionsRoot = if (phoneNav) NavCatalog.MORE else "home"
  nav.tabOrder = if (phoneNav) listOf("home") + navLayout.ids.mapNotNull { NavCatalog.of(it)?.route?.substringBefore('?') } else DESK_TABS.toList()
  nav.isTab = { base -> if (phoneNav) base in navRoutes else base in DESK_TABS }
  BackHandler(enabled = nav.canBack) { nav.back() }

  //  اشتراکی که تا ۳ روزِ دیگر تمام می‌شود ⇒ قرصِ سیاهِ «تمدیدِ اشتراک» بالای نوار (r10)
  var renewDays by remember { mutableStateOf<Int?>(null) }
  LaunchedEffect(nav.current, loaded) {
    renewDays = runCatching { ir.vil3ntec.tohid.sync.v1.SoftLock.warnDays(context) }.getOrNull()?.takeIf { it in 0..3 }
  }
  val current = nav.current
  val base = RoutePaths.normalize(current.substringBefore('?'))
  //  نوار فقط روی ریشهٔ تب‌های خودش (`ShellFrame`)؛ صندوق و ویرایشگرِ نوار هرگز
  val page = RoutePaths.normalize(nav.page.substringBefore('?'))
  val navVisible = ShellFrame.navVisible(
    phone = phoneNav, page = page, barTabs = navRoutes, gate = nav.gate != null,
    cartOpen = ir.vil3ntec.tohid.ui.components.pos.PhoneCartUi.sheetOpen.value,
  )
  val navReserve = PhoneNav.RESERVE + (if (renewDays != null) PhoneNav.RENEW_RESERVE else 0.dp)

  //  ویترین و میزِ تحویل: فقط وقتی سرور می‌گوید روشن است، بخششان دیده می‌شود
  LaunchedEffect(Unit) { runCatching { StorefrontGate.refresh(context) } }
  LaunchedEffect(Unit) { runCatching { DeliveryGate.refresh(context) } }
  LaunchedEffect(Unit) {
    ir.vil3ntec.tohid.sync.PhoneScanner.load(context)
    PhotoPrefs.load(context)
    ir.vil3ntec.tohid.data.ShopChatSync.refreshConfig(context)
  }
  //  گوشیِ جفت‌شده بارکد فرستاد ⇒ صندوق باز می‌شود و کالا به سبد می‌رود
  val phoneScan = ir.vil3ntec.tohid.sync.PhoneScanner.lastReceived.value
  LaunchedEffect(phoneScan) {
    if (!Platform.DESKTOP && phoneScan != null && base != "sale") nav.open("sale")
  }
  if (Platform.DESKTOP) {
    LaunchedEffect(Unit) {
      for (code in ir.vil3ntec.tohid.sync.PhoneScanner.inbox) {
        ir.vil3ntec.tohid.scan.ScanRouter.dispatch(code) { nav.open("sale") }
      }
    }
    //  انبار: کالای ثبت‌شده ⇐ ورودِ انبارِ همان؛ ناشناس ⇐ فرمِ کالای تازه با همین بارکد
    val stockOwner = remember { Any() }
    val latestData by rememberUpdatedState(data)
    DisposableEffect(base) {
      if (base == StockPaths.LIST) {
        ir.vil3ntec.tohid.scan.ScanRouter.register(stockOwner) { code ->
          val id = ShopStore.barcodeIndex(latestData)[code]
          nav.open(if (id != null) StockPaths.entry(product = id) else ProductsPaths.new(barcode = code))
          true
        }
      }
      onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(stockOwner) }
    }
  }

  //  میان‌برها و پیوندهای درونی (`AppNav.go`) — نامِ قدیمی خودکار به مسیرِ امروز
  val navRequest = AppNav.request.value
  LaunchedEffect(navRequest) {
    if (navRequest != null) {
      //  پهن: بخشِ سرِ زنجیره (میان‌برِ Ctrl+…) هم‌سطح باز می‌شود، جزئیات روی پشته (AUDIT-NAV N3)
      if (wide && V3Modules.table.chain(navRequest).size <= 1) nav.openFromRail(navRequest) else nav.open(navRequest)
      AppNav.request.value = null
    }
  }
  //  پیوندِ بیرونی (اعلان، `vill3n://app/…`) — پس از دروازه‌ها باز می‌شود
  val link = ir.vil3ntec.tohid.ui.v3.modes.ModeState.pendingLink.value
  LaunchedEffect(link) {
    if (link != null && link.mode == AppMode.SHOP) {
      ir.vil3ntec.tohid.ui.v3.modes.ModeState.pendingLink.value = null
      nav.openDeep(link.path)
    }
  }

  val access = ir.vil3ntec.tohid.core.nav.Access(
    granted = buildSet {
      if (canManage) add(Need.MANAGE)
      if (ir.vil3ntec.tohid.data.ShopRole.canSeeAudit(context)) add(Need.AUDIT)
      if (OrdersLive.enabled) add(Need.ORDERS_LIVE)
      if (StorefrontGate.visible) add(Need.STOREFRONT)
      if (ir.vil3ntec.tohid.data.ShopChatSync.config.value?.enabled == true) add(Need.CHAT)
      if (DeliveryGate.visible) add(Need.DELIVERY)
    },
    flags = ModuleFlags.on.value,
    mode = AppMode.SHOP,
  )
  val shell = ShellContext(
    store = store, data = data, cart = cartStore, snackbar = snackbar, toasts = toasts,
    theme = theme, onTheme = onTheme, bottomReserve = navReserve, wide = wide, access = access,
    navAllowed = navAllowed,
    afterSignIn = {
      askShop = true
      ir.vil3ntec.tohid.sync.AutoSync.now(context, store)
    },
  )
  val title = nav.resolve(current)?.route?.title?.invoke().orEmpty().ifBlank { ir.vil3ntec.tohid.core.i18n.Str.appName }

  Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
  Scaffold(
    //  STF-05: هر لمس یا کلید (بی آنکه مصرفش کند) ساعتِ بی‌کاری را صفر می‌کند
    modifier = Modifier
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            lastActivity.set(System.nanoTime() / 1_000_000)
          }
        }
      }
      .onPreviewKeyEvent { lastActivity.set(System.nanoTime() / 1_000_000); false },
    containerColor = androidx.compose.ui.graphics.Color.Transparent,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      //  فقط کامپیوتر: سربرگِ باریکِ خودش، نوارِ اشتراک و نسخهٔ تازه. گوشی سربرگ ندارد —
      //  هر صفحه `V3Scaffold` ِ خودش را دارد
      if (Platform.DESKTOP) Column {
        DeskHeader(
          title = title,
          d = data,
          onBack = if (nav.canBack) ({ nav.back() }) else null,
          onMenu = { onMenuPress() },
          onSearch = { AppNav.palette.value = true },
          onOpen = { nav.open(it) },
          page = V3Modules.legacyId(current),
        )
        CompositionLocalProvider(ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav provides nav) { PreviewBanner(); ir.vil3ntec.tohid.ui.v3.shell.ShellNotice() }
      }
    },
    //  پیام‌ها بالای صفحه می‌آیند، سرِ راهِ هیچ‌چیز نیستند
    snackbarHost = {
      Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        V3SnackbarHost(snackbar)
      }
    },
    bottomBar = { if (Platform.DESKTOP) DeskStatusBar(data) },
  ) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
      if (!loaded) {
        //  دفتر هنوز از دیسک خوانده نشده؛ «چیزی نیست» گفتن در این لحظه دروغ است
        TohidLoadingState(rows = 5, modifier = Modifier.padding(16.dp))
        return@Box
      }
      if (wide && shownRail != RailMode.HIDDEN) {
        val railBoard = remember(data) { ir.vil3ntec.tohid.data.DashboardEngine.board(data, ir.vil3ntec.tohid.todayIso(), 7) }
        DeskRail(
          current = V3Modules.railId(current), onPick = { nav.openFromRail(it) }, compact = shownRail == RailMode.COMPACT, mode = shownRail, onMode = ::setShownRail,
          badges = mapOf(
            "products" to railBoard.outOfStock.size + railBoard.lowStock.size,
            "warehouse" to railBoard.expiring.size,
            "debtors" to railBoard.debtOverdue.size,
            "orders" to OrdersLive.newCount,
          ),
        )
      }
      //  دوربینِ گوشی برای کامپیوتر — درخواست، فرستادن و استودیو
      PhoneLinkHost(store, data, snackbar)
      //  جست‌وجوی سریع (Ctrl+K)
      if (AppNav.palette.value) {
        CommandPalette(data, onDismiss = { AppNav.palette.value = false }) { hit ->
          AppNav.palette.value = false
          when (hit.kind) {
            "product" -> nav.open(ProductsPaths.item(hit.id))
            "debtor" -> nav.open(ir.vil3ntec.tohid.ui.v3.money.MoneyPaths.debtor(hit.id))
            else -> nav.openFromRail(hit.id)
          }
        }
      }
      if (wide && railOverlay) {
        androidx.compose.ui.window.Dialog(
          onDismissRequest = { railOverlay = false },
          properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
          Box(Modifier.fillMaxSize().clickable { railOverlay = false }) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight()) {
              DeskRail(current = V3Modules.railId(current), onPick = { nav.openFromRail(it); railOverlay = false }, mode = shownRail, onMode = ::setShownRail)
            }
          }
        }
      }
      val railPad = when {
        !wide -> 0.dp
        shownRail == RailMode.FULL -> DESK_RAIL_WIDTH
        shownRail == RailMode.COMPACT -> DESK_RAIL_COMPACT
        else -> 0.dp
      }
      //  جای نوارِ شناور: خانه خودش زیرِ نوار می‌کشد؛ صفحهٔ بی‌نوار (صندوق) فقط نوارِ سیستم
      val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
      val pageBottom = ShellFrame.pageBottom(phoneNav, navVisible, page, navReserve, navInset)
      CompositionLocalProvider(
        LocalV3Toasts provides toasts,
        //  نوارِ سیستم را پوسته همین‌جا حساب کرده است
        LocalV3SystemBottom provides 0.dp,
        LocalV3Notice provides { Column { PreviewBanner(); ir.vil3ntec.tohid.ui.v3.shell.ShellNotice() } },
      ) {
        PageWidth(Modifier.padding(start = railPad, bottom = pageBottom)) {
          V3RouteHost(nav, shell, Modifier.fillMaxSize())
        }
      }
      //  نوارِ شناورِ r10 — دایرهٔ شروع، قرص، دایرهٔ پایان؛ نگه‌داشتن = ویرایشگر
      if (navVisible) PhoneNavBar(
        layout = navLayout,
        //  فعال از ریشهٔ پشته (تبی که صفحه درونش است)، نه بالای پشته (AUDIT-NAV N1)
        current = NavCatalog.of(NavCatalog.activeForStack(navLayout, nav.stack.entries, V3Modules.table::canonical) ?: NavCatalog.MORE)?.route,
        onPick = { item -> nav.open(item.route) },
        onEdit = { nav.open("settings/general/navbar") },
        modifier = Modifier.align(Alignment.BottomCenter),
        renewDays = renewDays,
        onRenew = { nav.open("subscription/plans") },
      )
    }
  }
  }
}

/** تب‌های کامپیوتر (منوی کناری ریشه‌هایش را هم‌سطح باز می‌کند) */
private val DESK_TABS = setOf("home", "sale", "debtors", "products", "more")

