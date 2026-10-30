package ir.vil3ntec.tohid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.ui.screens.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.theme.AuroraBackground
import ir.vil3ntec.tohid.ui.theme.AuroraTone
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell

private data class Tab(val id: String, val label: String, val icon: ImageVector)

/** نامِ صفحه‌ها برای سربرگ — همان عنوان‌هایی که وب بالای صفحه می‌نویسد */
private val PAGE_TITLES: Map<String, String> get() = mapOf(
  "dashboard" to Str.dashboard,
  "sale" to Str.sale,
  "debtors" to Str.debtors,
  "warehouse" to Str.warehouse,
  "expenses" to Str.expenses,
  "products" to Str.products,
  "more" to Str.more,
  "purchasing" to Str.purchasing,
  "admin" to Str.adminCenter,
  "devices" to Str.devices,
  "sales" to Str.salesHistory,
  "reports" to Str.reportsAlt,
  "receipts" to Str.receipts,
  "audit" to Str.auditLog,
  "settings" to Str.settings,
  "quick" to Str.pickProduct,
  "product" to Str.product,
  "vip" to Str.plans,
  //  «اشتراکِ من» با «اشتراک و قیمت‌ها» یکی نیست: آن یکی پلن‌ها و
  //  قیمت‌های سرور را نشان می‌دهد، این یکی حالِ اشتراکِ خودِ این حساب.
  "myplan" to Str.mySubscription,
  "support" to Str.support,
  //  چتِ مشتری ↔ دکان (v3 §۳۳) — فقط وقتی سرور پرچمِ `shop.chat` را روشن گفته
  "chat" to Str.customerChat,
  "profile" to Str.myAccount,
  "team" to Str.staff,
  "orders" to Str.orders,
  "storefront" to Str.storefront,
  "promos" to StrShell.promos,
  //  phone v3 §F: جست‌وجوی کامل و ویرایشگرِ نوارِ پایین
  ir.vil3ntec.tohid.ui.components.common.PhoneNav.SEARCH to "جست‌وجو",
  ir.vil3ntec.tohid.ui.components.common.PhoneNav.EDITOR to "نوارِ پایین",
  //  میزِ تحویل (C01) — فقط وقتی سرور پرچمِ `shop.delivery` را روشن گفته
  "delivery" to StrNav.deliveryDesk,
)

/**
 *  نوارِ پایین — پنج تب و بس.
 *
 *  قبلاً هفت تب بود و اسمِ هرکدام آن‌قدر تنگ می‌شد که خوانده نمی‌شد.
 *
 *  جای انبار و گزارش با قرض‌داران و محصولات عوض شد. دلیلش ساده است: در
 *  یک روزِ دکان، قرض‌دار و کالا ده‌ها بار باز می‌شوند و انبار و گزارش
 *  چند بار — آن دو جای نوارِ پایین را می‌خواهند. انبار و گزارش سرِ
 *  جایشان هستند، از «بیشتر».
 */
private val TABS: List<Tab> get() = listOf(
  Tab("dashboard", Str.home, Icons.Filled.GridView),
  Tab("sale", Str.sale, Icons.Filled.PointOfSale),
  Tab("debtors", Str.debtors, Icons.Filled.Groups),
  Tab("products", Str.products, Icons.Filled.ShoppingBag),
  Tab("more", Str.more, Icons.Filled.MoreHoriz),
)


@Composable
fun AppRoot(
  store: ShopStore,
  theme: ir.vil3ntec.tohid.ui.theme.ThemeChoice,
  onTheme: (ir.vil3ntec.tohid.ui.theme.ThemeChoice) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val data by store.data.collectAsState()
  val loaded by store.loaded.collectAsState()
  // کالایی که صفحهٔ خودش باز است
  var openProduct by rememberSaveable { mutableStateOf<String?>(null) }
  var editProduct by remember { mutableStateOf<ProductFormState?>(null) }
  val cartStore = remember { CartStore(context) }
  var tab by rememberSaveable { mutableStateOf("dashboard") }
  var migration by remember { mutableStateOf<String?>(null) }
  // بارکدی که در فروش خوانده شد ولی کالایش ثبت نبود
  var pendingBarcode by remember { mutableStateOf<String?>(null) }
  // کالایی که از صفحهٔ محصولات، در انبار باز می‌شود
  var pendingProduct by remember { mutableStateOf<String?>(null) }
  // صفحهٔ فرعیِ باز، اگر باز باشد
  var sub by rememberSaveable { mutableStateOf<String?>(null) }
  // صفحهٔ ورود، وقتی از دکمهٔ حسابِ سربرگ باز شود
  var authOpen by rememberSaveable { mutableStateOf(false) }

  //  «رمز را دوباره بزنید» برای هر کارِ حساس (`reauth_required`، بندِ ۲۱.۱):
  //  یک پنجره برای همهٔ صفحه‌ها؛ `ApiClient` پس از تأیید همان درخواست را دوباره می‌فرستد
  ReauthHost()

  /*
   *  دکانِ حساب — پرسیدنش فقط وقتی معلوم شود که ندارد.
   *
   *  `askShop` یعنی «یک بار دیگر از سرور بپرس»؛ `needsShop` یعنی
   *  «سرور گفت ندارد». این دو جدا هستند تا خطای شبکه به‌اشتباه صفحهٔ
   *  ساختِ دکان را باز نکند.
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
        //  نقش از همین‌جا می‌آید؛ درخواستِ جدا لازم نیست
        state.role?.let { ir.vil3ntec.tohid.data.ShopRole.remember(context, it) }
        //  لایه‌های مجوزِ دکان (STF-01..03) — نبودنش یعنی همان رفتارِ امروزِ نقش
        if (state.hasShop) ir.vil3ntec.tohid.data.ShopRole.rememberPolicy(context, state.policy?.toString())
        if (state.hasShop) ir.vil3ntec.tohid.data.ShopRole.rememberShopId(context, state.shop?.id)
        //  دکان دارد: مطمئن شویم دفترِ روی گوشی هم مالِ همین دکان است.
        //  اگر حساب از دکانی به دکانِ دیگر رفته باشد، همین‌جا جابه‌جا
        //  می‌شود — وگرنه دفترِ دکانِ قبلی داخلِ دکانِ تازه می‌رفت.
        if (state.hasShop) {
          runCatching {
            ir.vil3ntec.tohid.data.LedgerOwner.shopChanged(
              context, store, state.shop?.id.orEmpty(),
            )
          }
        }
      }
      .onFailure { failure ->
        //  شکستِ شبکه یعنی «نمی‌دانیم»، نه «ندارد» — ولی اگر خودِ سرور
        //  گفته باشد این حساب دکانی ندارد، همان جوابِ «ندارد» است
        if ((failure as? ir.vil3ntec.tohid.core.net.ApiFailure)?.code == "no_shop") {
          needsShop = true
        }
        //  این حساب حالا «پیک» است (DLV-01): سرور فقط درِ پیک را باز می‌کند ⇒ حالتِ پیک
        if ((failure as? ir.vil3ntec.tohid.core.net.ApiFailure)?.code == "courier_scope") {
          ir.vil3ntec.tohid.data.ShopRole.remember(context, ir.vil3ntec.tohid.data.ShopRole.COURIER)
        }
      }
  }

  /*
   *  و اگر همگام‌سازی گفت دکان نیست، همان‌جا بپرس.
   *
   *  تا دیروز دکان فقط یک بار، هنگام باز شدنِ برنامه، پرسیده می‌شد. اگر
   *  آن یک بار جواب نمی‌گرفت — نت نبود، یا کاربر بعدِ آن وارد شد — دیگر
   *  هیچ‌وقت پرسیده نمی‌شد و کاربر می‌ماند با همگام‌سازیِ سرخ و راهی
   *  برای ساختنِ دکان نداشت. حالا هر بار سرور `no_shop` بدهد، همین‌جا
   *  گرفته می‌شود.
   */
  LaunchedEffect(ir.vil3ntec.tohid.sync.AutoSync.needsShop) {
    if (ir.vil3ntec.tohid.sync.AutoSync.needsShop) needsShop = true
  }

  /*
   *  قفلِ برنامه.
   *
   *  اگر رمز گذاشته شده باشد، تا زده نشود هیچ‌چیزِ دکان دیده نمی‌شود.
   *  `rememberSaveable` است تا چرخاندنِ گوشی دوباره قفلش نکند، ولی با
   *  بسته شدنِ برنامه از بین می‌رود و دفعهٔ بعد دوباره می‌پرسد.
   */
  val lockStore = remember { ir.vil3ntec.tohid.data.LockStore(context) }
  var unlocked by rememberSaveable { mutableStateOf(false) }

  /*
   *  قفلِ خودکار پس از بی‌کاری — STF-05. آخرین لمس/کلید روی ساعتِ یکنوا؛
   *  هر ۱۵ ثانیه سنجیده می‌شود. پیش‌فرض خاموش (`LockStore.idleMinutes = 0`)،
   *  یعنی همان رفتارِ پیشین: فقط هنگامِ باز شدنِ برنامه می‌پرسد.
   */
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

  // یک بار، هنگام اولین اجرا: دفترِ دکان از نسخهٔ قبلی آورده می‌شود
  LaunchedEffect(Unit) {
    if (store.hasData()) return@LaunchedEffect
    val legacy = runCatching { ir.vil3ntec.tohid.data.Migration.readLegacyData(context) }.getOrNull()
    if (legacy.isNullOrBlank()) return@LaunchedEffect
    store.importJson(legacy)
      .onSuccess { migration = StrNav.migrationOk }
      .onFailure { migration = StrNav.migrationFailed }
  }

  /*
   *  همگام‌سازی خودکار.
   *
   *  هر بار که برنامه جلوی چشم می‌آید — چه سرِ باز شدن، چه برگشت از
   *  پس‌زمینه — یک بار همه‌چیز تازه می‌شود و بعد تا وقتی باز است،
   *  تغییرهای دیگران هم گرفته می‌شود. با رفتن به پس‌زمینه خاموش
   *  می‌شود تا باتری و دیتا الکی نرود.
   *
   *  تا دیروز فقط یک بار سرِ باز شدنِ سرد بود: کسی که برنامه را در
   *  جیبش نگه می‌داشت و دوباره بازش می‌کرد، تغییرِ شریکش را نمی‌دید.
   */
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

  /*
   *  یادآوریِ روزانه.
   *
   *  از اندروید ۱۳ اجازهٔ اعلان جدا پرسیده می‌شود. یک بار می‌پرسیم و اگر
   *  کاربر نخواست، اصراری نیست — کارِ روزانه هم بی‌اجازه چیزی نشان
   *  نمی‌دهد.
   */
  val askNotify = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { }

  /*
   *  لوکیشنِ دکان — فقط با رضایتِ صریح (F0-07، تا تصمیمِ D-04).
   *
   *  `LocationPing` خودش دروازه است: بی رضایت نه چیزی می‌فرستد و نه
   *  `shouldAsk` بله می‌گوید. با رضایت، یک بار پرسیده می‌شود و جوابش —
   *  چه بله چه نه — کارِ هیچ بخشی از برنامه را بند نمی‌آورد.
   */
  val askLocation = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { granted ->
    if (granted.values.any { it }) scope.launch { ir.vil3ntec.tohid.sync.LocationPing.send(context) }
  }
  LaunchedEffect(Unit) {
    ir.vil3ntec.tohid.data.Reminders.schedule(context)
    //  خلاصهٔ روزانه یک چیز است و خبرِ فوری چیزِ دیگر: کالایی که تمام
    //  شده، قرضی که از حد گذشته و اشتراکی که رو به پایان است، تا فردا
    //  صبح صبر نمی‌کنند
    ir.vil3ntec.tohid.data.Watchman.schedule(context)
    //  و پشتیبانِ شبانه — تا مسئولیتِ کارِ چند سالِ دکان روی حافظهٔ آدمی
    //  نباشد که سرش شلوغ است
    ir.vil3ntec.tohid.data.AutoBackup.schedule(context)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      val granted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.POST_NOTIFICATIONS
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      if (!granted) runCatching { askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }

    //  لوکیشن: هر دو شاخه پشتِ رضایتِ صریح است (F0-07). با رضایت، اگر
    //  اجازه هست فرستاده می‌شود، وگرنه یک بار پرسیده می‌شود. پرسشِ دوباره
    //  در هر بار باز شدنِ برنامه، آزار است.
    if (ir.vil3ntec.tohid.data.DeviceLocation.granted(context)) {
      ir.vil3ntec.tohid.sync.LocationPing.send(context)
    } else if (ir.vil3ntec.tohid.sync.LocationPing.shouldAsk(context)) {
      runCatching { askLocation.launch(ir.vil3ntec.tohid.data.DeviceLocation.PERMISSIONS) }
    }

    //  «من آمدم» — تا صاحب سامانه بداند چند نفر آمده‌اند، نه فقط چند
    //  نفر ثبت‌نام کرده‌اند. هیچ داده‌ای از دفترِ دکان نمی‌رود.
    ir.vil3ntec.tohid.sync.VisitPing.send(context)
  }
  LaunchedEffect(data) {
    if (loaded) {
      ir.vil3ntec.tohid.sync.AutoSync.nudge(context, store)
      //  کالایی که تازه تمام شده، خبرش برای بقیهٔ اعضا می‌رود. فقط
      //  «تازه»ها — وگرنه هر بار باز کردنِ برنامه همان فهرست را
      //  دوباره می‌فرستاد.
      ir.vil3ntec.tohid.sync.ShopNews.checkStock(context, data)
    }
  }

  // دکمهٔ برگشتِ گوشی از صفحهٔ فرعی برمی‌گردد، نه اینکه برنامه را ببندد
  BackHandler(enabled = sub != null) { sub = null }

  val snackbar = remember { SnackbarHostState() }
  LaunchedEffect(migration) {
    migration?.let { scope.launch { snackbar.showSnackbar(it) } }
  }
  //  POS-13: فاکتورِ نیمه‌کارهٔ بارِ پیش — ادامه یا دور انداختن
  DraftRecoveryPrompt(cartStore, data, loaded, snackbar)
  //  HW-02/03: کشوی بی فروش (`no_sale`) و چاپِ دوباره (`reprint`) از صفِ چاپ ⇐ سابقهٔ دکان.
  //  `appendAudit` روی دفترِ همان لحظه و زیرِ قفلِ نوشتن می‌سازد (نه تصویرِ کهنهٔ `data.value`)
  //  تا فروشِ هم‌زمان گم نشود؛ پس از بارِ دفتر، و مثلِ هر نوشتنی همگام می‌شود
  LaunchedEffect(store, loaded) {
    if (loaded) ir.vil3ntec.tohid.print.PrintSpooler.attachAudit { row -> store.appendAudit(row) }
  }
  //  نوشتنی که قفلِ نرمِ پایانِ اشتراک ردش کرد ⇐ پیامِ «ثبت شد»ِ صفحه برداشته
  //  می‌شود و راستش گفته می‌شود، با راهِ تمدید
  LaunchedEffect(store) {
    store.blocked.collect {
      snackbar.currentSnackbarData?.dismiss()
      val r = snackbar.showSnackbar(
        StrNav.subscriptionBlocked,
        actionLabel = Str.renew, duration = SnackbarDuration.Long,
      )
      if (r == SnackbarResult.ActionPerformed) AppNav.go("vip")
    }
  }
  //  دفتر روی دیسک ننشست (F0-01) ⇐ پیامِ «ثبت شد»ِ صفحه برداشته و راستش
  //  گفته می‌شود. صفحه همچنان همان دفترِ روی دیسک را نشان می‌دهد.
  LaunchedEffect(store) {
    store.diskError.collect { reason ->
      snackbar.currentSnackbarData?.dismiss()
      snackbar.showSnackbar(
        StrNav.diskError(reason),
        duration = SnackbarDuration.Long,
      )
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

  if (lockStore.enabled && !unlocked) {
    AppLockScreen { unlocked = true }
    return
  }

  if (authOpen) {
    //  تازه وارد شده: دفترِ همین حساب باید همان لحظه پایین بیاید، نه
    //  دفعهٔ بعد که برنامه باز می‌شود. کسی که تازه نصب کرده و وارد
    //  شده، وگرنه دکانِ خالی می‌دید.
    WelcomeScreen(store) {
      authOpen = false
      askShop = true                 // شاید حسابِ تازه هنوز دکانی ندارد
      ir.vil3ntec.tohid.sync.AutoSync.now(context, store)
    }
    return
  }

  /*
   *  «دکانت کدام است؟»
   *
   *  سرور بدونِ دکان هیچ داده‌ای نمی‌گیرد و نمی‌دهد — هر دو مسیرِ
   *  همگام‌سازی `no_shop` می‌دهند و مجوزِ اشتراک هم صادر نمی‌شود. ولی
   *  هیچ‌جای برنامه دکان ساخته نمی‌شد، پس هر کسی که تازه ثبت‌نام می‌کرد
   *  بی‌آنکه بفهمد، دفترش هیچ‌وقت روی سرور نمی‌نشست.
   *
   *  فقط وقتی پرسیده می‌شود که **مطمئن** باشیم دکانی نیست: خطای شبکه
   *  جوابِ «نه» نیست و کسی را پشتِ این صفحه گیر نمی‌اندازد.
   */
  if (needsShop) {
    ShopSetupScreen(store) { needsShop = false }
    return
  }

  /*
   *  رمزِ برنامه، یک بار، بعد از ساختنِ حساب.
   *
   *  تا حالا قفل فقط برای کسی بود که خودش سراغِ تنظیمات می‌رفت — یعنی
   *  عملاً هیچ‌کس. حالا کسی که حساب دارد یک بار پرسیده می‌شود، و اگر
   *  نخواست دیگر پرسیده نمی‌شود.
   */
  val syncState = remember { ir.vil3ntec.tohid.sync.SyncStore(context) }
  var lockAsked by rememberSaveable { mutableStateOf(false) }
  val hasAccount = !syncState.accessToken.isNullOrBlank()
  if (hasAccount && !lockStore.enabled && !lockAsked && !syncState.lockDeclined) {
    LockSetupScreen {
      lockAsked = true
      syncState.lockDeclined = true
      unlocked = true
    }
    return
  }

  /**
   *  رفتن به یک صفحه، از هر جای برنامه.
   *
   *  یک جا تصمیم می‌گیرد که مقصد تب است یا زیرصفحه — وگرنه هر بار که تبی
   *  از نوار پایین بیرون می‌رود، باید چند جای دیگر هم عوض شود و یکی‌شان
   *  فراموش می‌شود.
   */
  //  کامپیوتر همیشه چیدمانِ کامپیوتری دارد (منوی کناری، نوارِ وضعیت) — حتی پنجرهٔ باریک
  val wide = Platform.DESKTOP || ir.vil3ntec.tohid.ui.screens.screenSize() == ir.vil3ntec.tohid.ui.screens.Screen.WIDE
  //  حالتِ منوی کناریِ کامپیوتر — انتخابِ کاربر، روی همین دستگاه
  val uiPrefs = remember { context.getSharedPreferences("tohid-ui", 0) }
  var railMode by remember { mutableStateOf(uiPrefs.getString("desk_rail", RailMode.FULL) ?: RailMode.FULL) }
  var railOverlay by remember { mutableStateOf(false) }
  fun setRail(m: String) {
    railMode = m
    railOverlay = false
    uiPrefs.edit().putString("desk_rail", m).apply()
  }
  //  نوارِ منوی کامپیوتر (نما ⇐ منوی کناری) همین حالت را عوض می‌کند
  val railRequest = AppNav.rail.value
  LaunchedEffect(railRequest) {
    if (railRequest != null) { setRail(railRequest); AppNav.rail.value = null }
  }
  /*
   *  نسخهٔ ۲ — منو در صندوق (تصمیم، بازبینیِ ۱۴۰۵/۰۷/۲۷): صندوق حالتِ منوی **خودش** را
   *  دارد (`desk_rail_pos`، پیش‌فرض «فقط آیکن») تا کاتالوگ جا بگیرد؛ بقیهٔ صفحه‌ها همان
   *  انتخابِ کاربر (`desk_rail`، پیش‌فرض «کامل»). همان آیکن‌ها به همان ترتیب، نامِ هر بخش در
   *  راهنمای هاور، و کلیدِ بازکردن/جمع‌کردنِ بالای منو (و ☰) انتخاب را برای همان صفحه نگه می‌دارد.
   *  «پنهان» در همه‌جا پنهان است.
   */
  val onSale = Platform.DESKTOP && (sub ?: tab) == "sale"
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

  /*
   *  phone v3 §F/§F2: نوارِ پایینِ گوشی قابلِ تنظیم است (`NavLayouts`). هر چه در نوار
   *  نشسته «تب» است (بی دکمهٔ برگشت)، بقیه زیرصفحه. کامپیوتر و صفحهٔ پهن همان `TABS`.
   */
  val phoneNav = !wide
  val navAllowed = ir.vil3ntec.tohid.ui.components.common.NavCatalog.allowed(
    canManage = ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context),
    ordersLive = OrdersLive.enabled,
  )
  val navUser = runCatching { syncState.accountId }.getOrDefault("")
  LaunchedEffect(navUser, navAllowed) {
    ir.vil3ntec.tohid.ui.components.common.PhoneNav.layout.value =
      ir.vil3ntec.tohid.ui.components.common.PhoneNav.load(context, navAllowed)
  }
  val navLayout = ir.vil3ntec.tohid.ui.components.common.NavLayouts.normalize(
    ir.vil3ntec.tohid.ui.components.common.PhoneNav.layout.value, navAllowed,
  )
  val navRoutes = navLayout.ids.mapNotNull { ir.vil3ntec.tohid.ui.components.common.NavCatalog.of(it)?.route }.toSet() + "dashboard"

  fun open(target: String) {
    val isTab = if (phoneNav) target in navRoutes else TABS.any { it.id == target }
    if (isTab) { tab = target; sub = null } else sub = target
  }
  //  گوشی: برگشت از تبِ دیگر به خانه، نه بستنِ برنامه
  BackHandler(enabled = phoneNav && sub == null && tab != "dashboard") { tab = "dashboard" }
  //  اشتراکی که تا ۳ روزِ دیگر تمام می‌شود ⇐ قرصِ سیاهِ «تمدیدِ اشتراک» بالای نوار (r10)
  var renewDays by remember { mutableStateOf<Int?>(null) }
  LaunchedEffect(tab, sub, loaded) {
    renewDays = runCatching { ir.vil3ntec.tohid.sync.v1.SoftLock.warnDays(context) }.getOrNull()?.takeIf { it in 0..3 }
  }
  val current = sub ?: tab
  //  نوار روی صفحهٔ فروش نیست (نوارِ سیاهِ سبد جایش است) و در ویرایشگرِ خودش هم پیش‌نمایشِ خودش را دارد
  val navVisible = phoneNav && current !in setOf("sale", "quick", ir.vil3ntec.tohid.ui.components.common.PhoneNav.EDITOR) &&
    !ir.vil3ntec.tohid.ui.components.pos.PhoneCartUi.sheetOpen.value
  val navReserve = ir.vil3ntec.tohid.ui.components.common.PhoneNav.RESERVE +
    (if (renewDays != null) ir.vil3ntec.tohid.ui.components.common.PhoneNav.RENEW_RESERVE else 0.dp)
  //  صفحه‌هایی که سربرگِ خودشان را دارند (r7 خانه، جست‌وجو، ویرایشگرِ نوار)
  val ownHeader = phoneNav && current in setOf(
    "dashboard", ir.vil3ntec.tohid.ui.components.common.PhoneNav.SEARCH, ir.vil3ntec.tohid.ui.components.common.PhoneNav.EDITOR,
  )

  //  ویترین (فاز ۴): فقط وقتی سرور می‌گوید روشن است، بخشش دیده می‌شود
  LaunchedEffect(Unit) { runCatching { StorefrontGate.refresh(context) } }
  LaunchedEffect(Unit) { runCatching { DeliveryGate.refresh(context) } }

  //  گوشیِ جفت‌شده بارکد فرستاد ⇒ صفحهٔ فروش باز می‌شود و کالا به سبد می‌رود
  LaunchedEffect(Unit) {
    ir.vil3ntec.tohid.sync.PhoneScanner.load(context)
    PhotoPrefs.load(context)
    //  چتِ دکان: پرچم را بپرس و پیام‌های مانده در صف را (اگر هست) بفرست
    ir.vil3ntec.tohid.data.ShopChatSync.refreshConfig(context)
  }
  val phoneScan = ir.vil3ntec.tohid.sync.PhoneScanner.lastReceived.value
  LaunchedEffect(phoneScan) {
    //  کامپیوتر: بارکدِ گوشی به صفحهٔ باز می‌رود (`ScanRouter`)، نه همیشه به فروش
    if (!Platform.DESKTOP && phoneScan != null && (sub ?: tab) != "sale") open("sale")
  }
  if (Platform.DESKTOP) {
    LaunchedEffect(Unit) {
      for (code in ir.vil3ntec.tohid.sync.PhoneScanner.inbox) {
        ir.vil3ntec.tohid.scan.ScanRouter.dispatch(code) { open("sale") }
      }
    }
    //  انبار و خرید: کالای ثبت‌شده ⇐ ورودِ انبارِ همان؛ ناشناس ⇐ فرمِ کالای تازه با همین بارکد
    val here = sub ?: tab
    val stockOwner = remember { Any() }
    val latestData by rememberUpdatedState(data)
    DisposableEffect(here) {
      //  خریدِ کامپیوتر گیرندهٔ خودش را دارد (`DeskPurchasingScreen`: بارکد ⇐ ردیفِ فاکتور)
      if (here == "warehouse" || (here == "purchasing" && !Platform.DESKTOP)) {
        ir.vil3ntec.tohid.scan.ScanRouter.register(stockOwner) { code ->
          val id = ShopStore.barcodeIndex(latestData)[code]
          if (id != null) pendingProduct = id else pendingBarcode = code
          if (here != "warehouse") open("warehouse")
          true
        }
      }
      onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(stockOwner) }
    }
  }

  //  میان‌برهای کامپیوتر — شرح سرِ `AppNav`
  val navRequest = AppNav.request.value
  LaunchedEffect(navRequest) {
    if (navRequest != null) {
      open(navRequest)
      AppNav.request.value = null
    }
  }

  //  رنگِ غالبِ زمینه از خودِ صفحه می‌آید: قرض‌داران صورتی، پول نعنایی،
  //  انبار نارنجی، بقیه بنفشِ برند. رنگ‌ها یکی‌اند، فقط ترتیبشان عوض می‌شود.
  val tone = when (sub ?: tab) {
    "debtors" -> AuroraTone.Debt
    "reports", "expenses", "sales" -> AuroraTone.Cash
    "warehouse", "products", "purchasing" -> AuroraTone.Stock
    else -> AuroraTone.Brand
  }

  AuroraBackground(tone = tone, animated = Motion.enabled) {
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
    containerColor = Color.Transparent,
    // سربرگ و نوارِ پایین خودشان فاصلهٔ نوارهای سیستم را می‌گیرند
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      /*
       *  ⚠️ **Column لازم است.** جایگاهِ `topBar` یک محتوا را سرِ جایش
       *  می‌گذارد؛ دو تا روی هم می‌افتند. یک بار بنر را همین‌طور کنارِ
       *  سربرگ گذاشتم و روی دکمه‌های سربرگ نشست.
       */
      Column {
      //  کامپیوتر: سربرگِ باریکِ خودش (عنوان، جست‌وجو، هشدارها، فروشِ تازه) — نه نوارِ آبیِ گوشی
      if (Platform.DESKTOP) DeskHeader(
        title = PAGE_TITLES[sub ?: tab] ?: Str.appName,
        d = data,
        onBack = if (sub != null) ({ sub = null }) else null,
        onMenu = { onMenuPress() },
        onSearch = { AppNav.palette.value = true },
        onOpen = ::open,
        page = sub ?: tab,
      ) else if (!ownHeader)
      TohidTopBar(
        title = PAGE_TITLES[sub ?: tab] ?: Str.appName,
        d = data,
        theme = theme,
        onTheme = onTheme,
        onSettings = { sub = "settings" },
        //  کلیدِ حساب، صفحهٔ حساب را باز می‌کند نه صفحهٔ ورود را.
        //  کسی که سال‌ها وارد بوده، با زدنش فرمِ ورود می‌دید.
        onAccount = { open("profile") },
        onOpen = ::open,
        //  زیرصفحه که باز است، سربرگ راهِ برگشت نشان می‌دهد؛ روی تبِ
        //  اصلی جایی برای برگشتن نیست و دکمه هم ساخته نمی‌شود
        onBack = if (sub != null) ({ sub = null }) else null,
        onMenu = if (wide) ({ onMenuPress() }) else null,
        onSearch = { AppNav.palette.value = true },
      )
      /*
       *  بنرِ اشتراک — بندِ ۲۱٫۸.
       *
       *  زرد از هفت روز پیش از پایان، سرخ پس از آن. ⛔ هیچ داده‌ای پاک
       *  نمی‌شود و خروجی و چاپ باز می‌مانند؛ همین را هم صریح می‌گوید،
       *  وگرنه کاربر گمان می‌کند دفترش رفته.
       */
      //  فقط نسخهٔ پیش‌نمایش (`BuildConfig.PREVIEW`)؛ در بقیه هیچ چیز نمی‌کشد
      PreviewBanner()
      //  خانهٔ گوشی همین را در کارت‌های «هشدارها و پیام‌ها» دارد
      if (!(phoneNav && current == "dashboard")) SubscriptionBanner { sub = "myplan" }
      //  نسخهٔ تازه — بررسیِ خودکار و نصب با یک دکمه. کامپیوتر (نسخهٔ ۲): تراشهٔ
      //  کوچکِ سربرگ (`UpdateChip` در `DeskHeader`)، نه نوارِ تمام‌عرض
      if (!Platform.DESKTOP) UpdateBanner()
      }
    },
    /*
     *  دکمهٔ شناورِ سراسری برداشته شد.
     *
     *  هر صفحه از قبل دکمهٔ افزودنِ خودش را داشت — «محصول جدید»،
     *  «قرض‌دار تازه»، «مصرف تازه». دکمهٔ مشترک روی همان‌ها می‌نشست و در
     *  یک صفحه دو یا سه «ثبت» دیده می‌شد، و روی کارت‌های پایینِ فهرست هم
     *  می‌افتاد. دکمهٔ خودِ هر صفحه، هم جایش درست است هم کارش معلوم.
     */
    /*
     *  پیام‌ها بالای صفحه می‌آیند، نه پایین.
     *
     *  کارتِ پایینِ صفحه روی نوارِ ناوبری و روی دکمه‌های خودِ صفحه
     *  می‌نشست و تا وقتی نرفته بود، زیرش قابلِ زدن نبود. بالا، سرِ راهِ
     *  هیچ‌چیز نیست.
     */
    snackbarHost = {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        TohidSnackbar(
          host = snackbar,
          modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp, start = 12.dp, end = 12.dp),
        )
      }
    },
    //  کامپیوتر (صفحهٔ پهن): منوی کناری به‌جای نوارِ پایینِ گوشی — بندِ ۹ِ ممیزی:
    //  «نسخهٔ دسکتاپ نباید نسخهٔ موبایلِ بزرگ‌شده باشد»
    bottomBar = {
      if (Platform.DESKTOP) DeskStatusBar(data)
      //  گوشی: نوارِ شناورِ r10 پایینِ محتوا می‌نشیند (پایین‌تر، درونِ صفحه) تا خانه زیرش دیده شود
    },
  ) { padding ->
    editProduct?.let { form ->
      ProductDialog(
        d = data,
        state = form,
        onDismiss = { editProduct = null },
        onSave = { draft ->
          val id = form.editingId
          val result = if (id == null) {
            WarehouseEngine.addProduct(data, draft, System.currentTimeMillis(), ::newId)
          } else {
            WarehouseEngine.editProduct(
              data, id, draft, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId,
            )
          }
          when (result) {
            is WarehouseEngine.Result.Failed ->
              scope.launch { snackbar.showSnackbar(result.message) }
            is WarehouseEngine.Result.Ok -> {
              scope.launch { store.save(result.data) }
              editProduct = null
            }
          }
        },
      )
    }

    Box(
      Modifier
        .padding(padding)
        .fillMaxSize()
    ) {
      if (!loaded) {
        // دفتر هنوز از دیسک خوانده نشده؛ «چیزی نیست» گفتن در این لحظه
        // دروغ است — کاربری که صد قلم کالا دارد نباید «خالی» ببیند
        TohidLoadingState(rows = 5, modifier = Modifier.padding(16.dp))
        return@Box
      }

      if (wide && shownRail != RailMode.HIDDEN) {
        val railBoard = remember(data) { ir.vil3ntec.tohid.data.DashboardEngine.board(data, ir.vil3ntec.tohid.todayIso(), 7) }
        DeskRail(
          current = sub ?: tab, onPick = ::open, compact = shownRail == RailMode.COMPACT, mode = shownRail, onMode = ::setShownRail,
          badges = mapOf(
            "products" to railBoard.outOfStock.size + railBoard.lowStock.size,
            "warehouse" to railBoard.expiring.size,
            "debtors" to railBoard.debtOverdue.size,
            "orders" to OrdersLive.newCount,
          ),
        )
      }
      //  حالتِ «پنهان»: ☰ منو را روی صفحه باز می‌کند؛ زدنِ بیرونش می‌بندد
      //  دوربینِ گوشی برای کامپیوتر — درخواست، فرستادن و استودیو
      PhoneLinkHost(store, data, snackbar)
      //  جست‌وجوی سریع (Ctrl+K یا ذره‌بینِ سربرگ) — همهٔ دستگاه‌ها
      if (AppNav.palette.value) {
        CommandPalette(data, onDismiss = { AppNav.palette.value = false }) { hit ->
          AppNav.palette.value = false
          when (hit.kind) {
            "product" -> { openProduct = hit.id; sub = "product" }
            "debtor" -> open("debtors")
            else -> open(hit.id)
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
              DeskRail(current = sub ?: tab, onPick = { open(it); railOverlay = false }, mode = railMode, onMode = ::setRail)
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
      //  جای نوارِ شناور: خانه خودش زیرِ نوار می‌کشد؛ صفحهٔ بی‌نوار (فروش) فقط نوارِ سیستم
      val pageBottom = when {
        !phoneNav -> 0.dp
        navVisible && current == "dashboard" -> 0.dp
        navVisible -> navReserve + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        else -> WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
      }
      //  §G: «حساب»، «تنظیمات»، «بیشتر»، جست‌وجو و ویرایشگرِ نوار از سمتِ پایان می‌لغزند
      val sideRoutes = setOf(
        "more", "profile", "settings",
        ir.vil3ntec.tohid.ui.components.common.PhoneNav.SEARCH, ir.vil3ntec.tohid.ui.components.common.PhoneNav.EDITOR,
      )
      val endSign = if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) -1 else 1
      PageWidth(Modifier.padding(start = railPad, bottom = pageBottom)) {
      AnimatedContent(
        targetState = sub ?: tab,
        transitionSpec = {
          when {
            !Motion.enabled -> androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            phoneNav && targetState in sideRoutes ->
              (androidx.compose.animation.slideInHorizontally(tween(220)) { w -> endSign * w / 3 } + fadeIn(tween(220)))
                .togetherWith(fadeOut(tween(140)))
            phoneNav && initialState in sideRoutes ->
              fadeIn(tween(200)).togetherWith(
                androidx.compose.animation.slideOutHorizontally(tween(200)) { w -> endSign * w / 3 } + fadeOut(tween(200)),
              )
            else -> (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 22 })
              .togetherWith(fadeOut(tween(150)))
          }
        },
        label = "page",
      ) { current ->
      when (current) {
        "purchasing" -> if (Platform.DESKTOP) DeskPurchasingScreen(store, data, snackbar) else PurchasingScreen(store, data, snackbar)
        "admin" -> ManagementScreen(data, ::open)
        "sales" -> if (Platform.DESKTOP) DeskInvoicesScreen(store, cartStore, data, snackbar) { sub = null; tab = "sale" }
          else SalesHistoryScreen(store, cartStore, data, snackbar) { sub = null; tab = "sale" }
        "reports" -> if (Platform.DESKTOP) DeskReportsScreen(data, snackbar) else ReportsScreen(data)
        "receipts" -> ReceiptsScreen(data)
        //  مثلِ تنظیمات، سدِ دوم: اگر از هر راهِ دیگری به این مسیر
        //  برسد هم برای شاگرد باز نمی‌شود
        "audit" ->
          if (ir.vil3ntec.tohid.data.ShopRole.canSeeAudit(context)) AuditLogScreen(data)
          else LaunchedEffect(Unit) { open("more") }
        //  شاگرد اصلاً واردِ تنظیمات نمی‌شود. ردیفش هم در «بیشتر»
        //  نشان داده نمی‌شود، ولی این یکی سدِ دوم است: اگر از هر راهِ
        //  دیگری به این مسیر برسد، باز هم باز نمی‌شود.
        "settings" ->
          if (ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context)) {
            if (Platform.DESKTOP) DeskSettings(
              listOf(
                StrSettings.language to androidx.compose.material.icons.Icons.Filled.Language,
                Str.alerts to androidx.compose.material.icons.Icons.Filled.NotificationsActive,
                Str.appearance to androidx.compose.material.icons.Icons.Filled.Palette,
                Str.dateAndDigits to androidx.compose.material.icons.Icons.Filled.Event,
                Str.expiredGoods to androidx.compose.material.icons.Icons.Filled.EventBusy,
                ir.vil3ntec.tohid.ui.components.pos.HeldInvoicePrefs.SECTION to androidx.compose.material.icons.Icons.Filled.Layers,
                ir.vil3ntec.tohid.ui.screens.RETURN_SETTINGS_TITLE to androidx.compose.material.icons.Icons.Filled.SwapHoriz,
                //  این دو در `Settings.kt` بودند ولی در این فهرست نه، پس روی کامپیوتر هیچ‌وقت باز نمی‌شدند
                StrShell.payAndRegister to androidx.compose.material.icons.Icons.Filled.PointOfSale,
                StrShell.crmReports to androidx.compose.material.icons.Icons.Filled.TableChart,
                Str.appLock to androidx.compose.material.icons.Icons.Filled.Lock,
                ir.vil3ntec.tohid.ui.screens.SECURITY_SECTION_TITLE to androidx.compose.material.icons.Icons.Filled.Security,
                Str.changeAccountPassword to androidx.compose.material.icons.Icons.Filled.Password,
                Str.signedInDevices to androidx.compose.material.icons.Icons.Filled.Devices,
                Str.staffCode to androidx.compose.material.icons.Icons.Filled.Key,
                //  همگام‌سازی (گوشی نسخهٔ ۳ از «حساب من» به تنظیمات آورد)
                ir.vil3ntec.tohid.core.i18n.StrNav.sync to androidx.compose.material.icons.Icons.Filled.Sync,
                Str.data to androidx.compose.material.icons.Icons.Filled.Storage,
                Str.aboutApp to androidx.compose.material.icons.Icons.Filled.Info,
              ),
            ) { SettingsScreen(store, data, snackbar, theme, onTheme) { open("more") } }
            else SettingsScreen(store, data, snackbar, theme, onTheme) { open("more") }
          } else {
            LaunchedEffect(Unit) { open("more") }
          }
        "expenses" -> ExpensesScreen(store, data, snackbar)
        "dashboard" -> if (Platform.DESKTOP) DeskDashboardScreen(data, ::open) { id -> openProduct = id; sub = "product" }
          else DashboardScreen(data, bottomReserve = navReserve, onOpen = ::open)
        //  phone v3 §F: جست‌وجوی کامل (کالا، مشتری، فاکتور، بخش) با بارکد
        ir.vil3ntec.tohid.ui.components.common.PhoneNav.SEARCH -> PhoneSearchScreen(
          d = data,
          onOpen = ::open,
          onProduct = { id -> openProduct = id; sub = "product" },
          onBack = { if (sub != null) sub = null else open("dashboard") },
          bottomReserve = 0.dp,
        )
        //  §F2: ویرایشگرِ نوار — از نگه‌داشتنِ نوار و از تنظیمات ← عمومی ← «نوارِ پایین»
        ir.vil3ntec.tohid.ui.components.common.PhoneNav.EDITOR -> NavEditorScreen(navAllowed) {
          if (sub != null) sub = null else open("dashboard")
        }
        "product" -> {
          val id = openProduct
          if (id == null) sub = null
          else ProductDetailScreen(
            store = store,
            d = data,
            productId = id,
            onBack = { sub = null },
            onEdit = { editProduct = data.products.find { it.id == id }?.let { ProductFormState.of(it) } },
            onEntry = { pendingProduct = id; open("warehouse") },
          )
        }
        "quick" -> VipGate(Str.saleRegister) {
          QuickSaleScreen(store, cartStore, data, snackbar) { sub = null; tab = "sale" }
        }
        //  کامپیوتر صندوقِ خودش را دارد (صفحه‌کلید و بارکدخوان، بی دوربین)؛ گوشی همان دوربین
        "sale" -> if (Platform.DESKTOP) VipGate(Str.saleRegister) {
          DeskSaleScreen(store = store, cartStore = cartStore, d = data, snackbar = snackbar) { code ->
            pendingBarcode = code
            open("warehouse")
          }
        } else VipGate(Str.saleRegister) {
          SaleScreen(
            store = store,
            cartStore = cartStore,
            d = data,
            snackbar = snackbar,
            onQuickSale = { sub = "quick" },
          ) { code ->
            pendingBarcode = code
            open("warehouse")
          }
        }
        "debtors" -> VipGate(Str.debtors) {
          if (Platform.DESKTOP) DeskCustomersScreen(store, data, snackbar) else DebtorsScreen(store, data, snackbar)
        }
        "products" -> if (Platform.DESKTOP) DeskProductsScreen(
          store = store,
          d = data,
          snackbar = snackbar,
          onOpenWarehouse = { productId -> pendingProduct = productId; open("warehouse") },
          onOpenProduct = { productId -> openProduct = productId; sub = "product" },
        ) else ProductsScreen(
          store = store,
          d = data,
          snackbar = snackbar,
          onOpenWarehouse = { productId ->
            pendingProduct = productId
            open("warehouse")
          },
          onOpenProduct = { productId ->
            openProduct = productId
            sub = "product"
          },
        )
        "warehouse" -> WarehouseScreen(
          store = store,
          d = data,
          snackbar = snackbar,
          openProductId = pendingProduct,
          newBarcode = pendingBarcode,
          onConsumed = { pendingBarcode = null; pendingProduct = null },
        )
        "vip" -> VipScreen { sub = null }
        "myplan" -> MySubscriptionScreen()
        "support" -> SupportScreen { sub = null }
        "chat" -> ShopChatScreen { sub = null }
        "profile" -> ProfileScreen(
          store = store,
          snackbar = snackbar,
          onBack = { if (sub != null) sub = null else open("dashboard") },
          onSignIn = { authOpen = true },
          onSubscription = { sub = "vip" },
        )
        "team" -> TeamScreen(snackbar)
        "orders" -> OrdersScreen(store, snackbar) { sub = null }
        "storefront" -> if (StorefrontGate.visible) StorefrontScreen(snackbar) else LaunchedEffect(Unit) { open("more") }
        //  مثلِ تنظیمات: فقط صاحب و مدیر (`settings.write`)؛ سرور هم همین را می‌سنجد
        "promos" ->
          if (ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context)) PromosScreen(data, snackbar)
          else LaunchedEffect(Unit) { open("more") }
        "delivery" -> DeliveryScreen(snackbar) { open("more") }
        "devices" -> DevicesScreen(data, snackbar)
        "more" -> MoreScreen(store, data, ::open)
      }
      }
      }
      //  phone v3 §F/§F2: نوارِ شناورِ r10 — دایرهٔ شروع، قرص، دایرهٔ پایان؛ نگه‌داشتن = ویرایشگر
      if (navVisible) ir.vil3ntec.tohid.ui.components.common.PhoneNavBar(
        layout = navLayout,
        current = current,
        onPick = { item ->
          if (item.id == ir.vil3ntec.tohid.ui.components.common.NavCatalog.SCAN) {
            ir.vil3ntec.tohid.ui.components.common.PhoneNav.scanRequest.value = true
          }
          open(item.route)
        },
        onEdit = { open(ir.vil3ntec.tohid.ui.components.common.PhoneNav.EDITOR) },
        modifier = Modifier.align(Alignment.BottomCenter),
        renewDays = renewDays,
        onRenew = { open("vip") },
      )
    }
  }
  }
}
