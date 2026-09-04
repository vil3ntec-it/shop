package ir.vil3ntec.tohid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.ui.screens.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import ir.vil3ntec.tohid.ui.theme.ArcticBackground
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

private data class Tab(val id: String, val label: String, val icon: ImageVector)

/** نامِ صفحه‌ها برای سربرگ — همان عنوان‌هایی که وب بالای صفحه می‌نویسد */
private val PAGE_TITLES = mapOf(
  "dashboard" to "داشبورد",
  "sale" to "فروش",
  "debtors" to "قرض‌داران",
  "warehouse" to "انبار",
  "expenses" to "مصارف",
  "products" to "محصولات",
  "more" to "بیشتر",
  "purchasing" to "خرید و تأمین‌کننده",
  "sales" to "تاریخچه فروش",
  "reports" to "گزارشات",
  "receipts" to "رسیدها",
  "audit" to "سابقه عملیات",
  "settings" to "تنظیمات",
  "quick" to "انتخاب محصول",
  "product" to "کالا",
  "vip" to "اشتراک و قیمت‌ها",
  "profile" to "حساب من",
  "team" to "کارمندان دکان",
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
private val TABS = listOf(
  Tab("dashboard", "خانه", Icons.Filled.GridView),
  Tab("sale", "فروش", Icons.Filled.PointOfSale),
  Tab("debtors", "قرض‌داران", Icons.Filled.Groups),
  Tab("products", "محصولات", Icons.Filled.ShoppingBag),
  Tab("more", "بیشتر", Icons.Filled.MoreHoriz),
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

  // یک بار، هنگام اولین اجرا: دفترِ دکان از نسخهٔ قبلی آورده می‌شود
  LaunchedEffect(Unit) {
    if (store.hasData()) return@LaunchedEffect
    val legacy = runCatching { ir.vil3ntec.tohid.data.Migration.readLegacyData(context) }.getOrNull()
    if (legacy.isNullOrBlank()) return@LaunchedEffect
    store.importJson(legacy)
      .onSuccess { migration = "اطلاعات نسخهٔ قبلی آورده شد" }
      .onFailure { migration = "اطلاعات نسخهٔ قبلی خوانده نشد" }
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
   *  لوکیشنِ دکان — همان اولِ کار، حتی بی‌حساب.
   *
   *  قرارِ صاحب مخزن: «بدون اینکه برنامه برود ثبت‌نام کند هم لوکیشن باید
   *  روشن باشد و لوکیشنِ طرف ثبت بشود و بیاید به سرور.» پس اینجاست، نه
   *  داخلِ ثبت‌نام: یک بار پرسیده می‌شود و جوابش — چه بله چه نه — کارِ
   *  هیچ بخشی از برنامه را بند نمی‌آورد.
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

    //  لوکیشن: اگر اجازه هست همان‌جا فرستاده می‌شود، وگرنه یک بار پرسیده
    //  می‌شود. پرسشِ دوباره در هر بار باز شدنِ برنامه، آزار است — همان
    //  یک بارِ سیستمیِ اندروید بس است.
    if (ir.vil3ntec.tohid.data.DeviceLocation.granted(context)) {
      ir.vil3ntec.tohid.sync.LocationPing.send(context)
    } else if (ir.vil3ntec.tohid.sync.LocationPing.shouldAsk(context)) {
      runCatching { askLocation.launch(ir.vil3ntec.tohid.data.DeviceLocation.PERMISSIONS) }
    }
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
  fun open(target: String) {
    if (TABS.any { it.id == target }) { tab = target; sub = null } else sub = target
  }

  ArcticBackground(animated = Motion.enabled) {
  Scaffold(
    containerColor = Color.Transparent,
    // سربرگ و نوارِ پایین خودشان فاصلهٔ نوارهای سیستم را می‌گیرند
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TohidTopBar(
        title = PAGE_TITLES[sub ?: tab] ?: "توحید",
        d = data,
        theme = theme,
        onTheme = onTheme,
        onSettings = { sub = "settings" },
        //  کلیدِ حساب، صفحهٔ حساب را باز می‌کند نه صفحهٔ ورود را.
        //  کسی که سال‌ها وارد بوده، با زدنش فرمِ ورود می‌دید.
        onAccount = { sub = "profile" },
        onOpen = ::open,
        //  زیرصفحه که باز است، سربرگ راهِ برگشت نشان می‌دهد؛ روی تبِ
        //  اصلی جایی برای برگشتن نیست و دکمه هم ساخته نمی‌شود
        onBack = if (sub != null) ({ sub = null }) else null,
      )
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
    bottomBar = {
      TohidNavBar(
        tabs = TABS,
        current = if (sub == null) tab else null,
        onPick = { tab = it; sub = null },
      )
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

      PageWidth {
      AnimatedContent(
        targetState = sub ?: tab,
        transitionSpec = {
          (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 22 })
            .togetherWith(fadeOut(tween(150)))
        },
        label = "page",
      ) { current ->
      when (current) {
        "purchasing" -> PurchasingScreen(store, data, snackbar)
        "sales" -> SalesHistoryScreen(store, cartStore, data, snackbar) { sub = null; tab = "sale" }
        "reports" -> ReportsScreen(data)
        "receipts" -> ReceiptsScreen(data)
        //  مثلِ تنظیمات، سدِ دوم: اگر از هر راهِ دیگری به این مسیر
        //  برسد هم برای شاگرد باز نمی‌شود
        "audit" ->
          if (ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context)) AuditLogScreen(data)
          else LaunchedEffect(Unit) { open("more") }
        //  شاگرد اصلاً واردِ تنظیمات نمی‌شود. ردیفش هم در «بیشتر»
        //  نشان داده نمی‌شود، ولی این یکی سدِ دوم است: اگر از هر راهِ
        //  دیگری به این مسیر برسد، باز هم باز نمی‌شود.
        "settings" ->
          if (ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context)) {
            SettingsScreen(store, data, snackbar, theme, onTheme) { open("more") }
          } else {
            LaunchedEffect(Unit) { open("more") }
          }
        "expenses" -> ExpensesScreen(store, data, snackbar)
        "dashboard" -> DashboardScreen(data, ::open)
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
        "quick" -> VipGate("فروش (صندوق)") {
          QuickSaleScreen(store, cartStore, data, snackbar) { sub = null; tab = "sale" }
        }
        "sale" -> VipGate("فروش (صندوق)") {
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
        "debtors" -> VipGate("قرض‌داران") { DebtorsScreen(store, data, snackbar) }
        "products" -> ProductsScreen(
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
        "profile" -> ProfileScreen(
          store = store,
          snackbar = snackbar,
          onBack = { sub = null },
          onSignIn = { authOpen = true },
          onSubscription = { sub = "vip" },
        )
        "team" -> TeamScreen(snackbar)
        "more" -> MoreScreen(store, data, ::open)
      }
      }
      }
    }
  }
  }
}

/* ============================ نوارِ پایین ============================ */

/**
 *  نوارِ پایین — قرصِ تیره، و یک چراغ بالای تبِ باز.
 *
 *  کارِ نشانگر این است: یک نوارِ باریکِ روشن بالای همان تب می‌نشیند و از
 *  آن، نور مثلِ یک چراغِ سقفی به پایین می‌تابد — نزدیکِ نوار پررنگ، و
 *  هرچه پایین‌تر نرم‌تر و محوتر. آیکن و برچسبِ همان تب زیرِ این نور
 *  روشن‌اند و بقیه تاریک.
 *
 *  **هیچ خطی بین تب‌ها کشیده نمی‌شود.** نه موج، نه بریدگی، نه مسیرِ
 *  تزئینی. فقط همان نوارِ کوچک و نورش.
 *
 *  **جای نوار از خودِ چیدمان می‌آید.** هر تب موقعیت و پهنای واقعی‌اش را
 *  گزارش می‌کند و نور از روی همان حساب می‌شود؛ پس روی گوشی و تبلت و هر
 *  نسبتِ صفحه‌ای سرِ جایش است و راست‌به‌چپ هم خود‌به‌خود درست درمی‌آید.
 */
@Composable
private fun TohidNavBar(
  tabs: List<Tab>,
  current: String?,
  onPick: (String) -> Unit,
) {
  val colors = Shop.colors

  /*
   *  ارتفاع‌ها ثابت‌اند، نه کسری از قدِ نوار — وگرنه با هر تغییرِ قد،
   *  نور و محتوا روی هم می‌افتند.
   */
  val barHeight = 76.dp
  val lampTop = 5.dp        // نوارِ نور
  val lampThick = 3.dp
  val contentTop = 18.dp
  val contentBottom = 10.dp
  val iconCenter = 32.dp    // مرکزِ آیکن، برای هالهٔ دورش
  val shape = RoundedCornerShape(percent = 50)

  // مختصاتِ واقعیِ تب‌ها؛ ترتیبِ رسیدنِ گزارش‌ها مهم نیست چون همه در
  // دستگاهِ مختصاتِ ریشه‌اند
  var barLeft by remember { mutableStateOf(0f) }
  val slots = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }   // شاخص ← (مرکز، پهنا)

  val activeIndex = tabs.indexOfFirst { it.id == current }
  val target = slots[activeIndex]

  /*
   *  چراغ نرم جابه‌جا می‌شود، نه اینکه خاموش و روشن شود: همان حسِ
   *  «منبعِ نور از این تب کنده شد و رفت روی آن یکی».
   */
  val centerX by animateFloatAsState(
    targetValue = (target?.first ?: 0f) - barLeft,
    animationSpec = tween(if (Motion.enabled) 380 else 0),
    label = "lampX",
  )
  val lampWidth by animateFloatAsState(
    targetValue = target?.second ?: 0f,
    animationSpec = tween(if (Motion.enabled) 380 else 0),
    label = "lampW",
  )
  val on by animateFloatAsState(
    targetValue = if (activeIndex >= 0) 1f else 0f,
    animationSpec = tween(if (Motion.enabled) 300 else 0),
    label = "lampOn",
  )

  Box(
    Modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 10.dp, vertical = 10.dp)
      .height(barHeight)
      .clip(shape)
      .background(colors.surface)
      .border(1.dp, colors.fieldBorder.copy(alpha = 0.35f), shape)
      .onGloballyPositioned { barLeft = it.positionInRoot().x },
  ) {
    /* --------------------------- چراغ --------------------------- */
    if (target != null && lampWidth > 0f && on > 0.01f) {
      val glow = colors.primary
      Canvas(Modifier.matchParentSize()) {
        val h = size.height
        val cx = centerX
        val yLamp = lampTop.toPx()
        val yEnd = yLamp + lampThick.toPx()
        val halfLamp = lampWidth * 0.20f

        /*
         *  مخروطِ نور.
         *
         *  هر لایه یک ذوزنقه است که از نوار شروع می‌شود و رو به پایین
         *  پهن‌تر می‌شود، ولی رنگش **شعاعی** است — از خودِ نوار به بیرون
         *  کم می‌شود، نه فقط از بالا به پایین. نور واقعی همین‌طور است:
         *  هرچه از منبع دورتر، کم‌رنگ‌تر، به هر سویی که باشد.
         *
         *  شش لایه، چون یک ذوزنقهٔ تنها لبهٔ تیز دارد. با هر لایه که
         *  پهن‌تر و کم‌رنگ‌تر می‌شود، لبه‌ها در هم می‌روند و آنچه می‌ماند
         *  یک شیبِ نرم است، نه چند تا خط.
         */
        val layers = listOf(
          1.00f to 0.030f,
          0.78f to 0.038f,
          0.58f to 0.046f,
          0.42f to 0.055f,
          0.29f to 0.065f,
          0.18f to 0.080f,
        )
        layers.forEach { (spread, alpha) ->
          val bottomHalf = lampWidth * (0.16f + 0.62f * spread)
          val topHalf = halfLamp * (0.75f + 0.55f * spread)
          val path = Path().apply {
            moveTo(cx - topHalf, yEnd)
            lineTo(cx + topHalf, yEnd)
            lineTo(cx + bottomHalf, h)
            lineTo(cx - bottomHalf, h)
            close()
          }
          drawPath(
            path,
            Brush.radialGradient(
              colors = listOf(
                glow.copy(alpha = alpha * on),
                glow.copy(alpha = alpha * 0.55f * on),
                glow.copy(alpha = 0f),
              ),
              center = Offset(cx, yEnd),
              radius = h * (0.62f + 0.5f * spread),
            ),
          )
        }

        /*
         *  حبابِ خودِ چراغ — یک بیضیِ پهن و کوتاه، درست زیرِ نوار.
         *
         *  همان چیزی است که «روشنایی» را می‌سازد. بدونش، نوار فقط یک خط
         *  است و مخروط از هوا شروع می‌شود.
         */
        withTransform({ scale(1.9f, 0.85f, pivot = Offset(cx, yEnd)) }) {
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                glow.copy(alpha = 0.34f * on),
                glow.copy(alpha = 0.10f * on),
                glow.copy(alpha = 0f),
              ),
              center = Offset(cx, yEnd),
              radius = lampWidth * 0.40f,
            ),
            radius = lampWidth * 0.40f,
            center = Offset(cx, yEnd),
          )
        }

        // هالهٔ نرمِ دورِ آیکن — تا آیکن هم زیرِ نور باشد، نه فقط کنارش
        val rIcon = 22.dp.toPx()
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = 0.20f * on), glow.copy(alpha = 0f)),
            center = Offset(cx, iconCenter.toPx()),
            radius = rIcon,
          ),
          radius = rIcon,
          center = Offset(cx, iconCenter.toPx()),
        )

        /*
         *  نوارِ چراغ — **یکی**، نه دوتا.
         *
         *  دو مستطیلِ روی هم (یکی پررنگ، یکی کم‌رنگ‌تر) از دور دو خط
         *  دیده می‌شدند. حالا یک مستطیل است با شیبِ افقی: وسطش سفیدِ
         *  داغ، دو سرش در هوا محو می‌شود. رشتهٔ یک لامپ هم همین‌طور
         *  است — سرهایش دیده نمی‌شوند.
         */
        drawRoundRect(
          brush = Brush.horizontalGradient(
            colorStops = arrayOf(
              0f to glow.copy(alpha = 0f),
              0.22f to glow.copy(alpha = 0.75f * on),
              0.5f to Color.White.copy(alpha = 0.92f * on),
              0.78f to glow.copy(alpha = 0.75f * on),
              1f to glow.copy(alpha = 0f),
            ),
            startX = cx - halfLamp,
            endX = cx + halfLamp,
          ),
          topLeft = Offset(cx - halfLamp, yLamp),
          size = Size(halfLamp * 2f, lampThick.toPx()),
          cornerRadius = CornerRadius(lampThick.toPx() / 2f),
        )
      }
    }

    /* ---------------------------- تب‌ها ---------------------------- */
    Row(
      Modifier.fillMaxSize().padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      tabs.forEachIndexed { index, t ->
        val active = current == t.id
        val fade by animateFloatAsState(
          targetValue = if (active) 1f else 0f,
          animationSpec = tween(if (Motion.enabled) 300 else 0),
          label = "tabFade",
        )
        // تبِ باز روشن است، بقیه تاریک — همان چیزی که نور می‌کند
        val tint = androidx.compose.ui.graphics.lerp(colors.muted, colors.primary, fade)

        Column(
          Modifier
            .weight(1f)
            .fillMaxHeight()
            // همین یک خط جای چراغ را تعیین می‌کند
            .onGloballyPositioned {
              slots[index] = (it.positionInRoot().x + it.size.width / 2f) to it.size.width.toFloat()
            }
            .clip(RoundedCornerShape(22.dp))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = { onPick(t.id) },
            )
            // فاصله‌ها ثابت‌اند و به فعال بودن ربطی ندارند، پس روشن شدنِ
            // یک تب جای بقیه را تکان نمی‌دهد
            .padding(top = contentTop, bottom = contentBottom),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            t.icon,
            contentDescription = t.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
          )
          Spacer(Modifier.height(5.dp))
          Text(
            t.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (active) FontWeight.Bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
