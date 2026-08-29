package ir.vil3ntec.tohid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

  // یک بار، هنگام اولین اجرا: دفترِ دکان از نسخهٔ قبلی آورده می‌شود
  LaunchedEffect(Unit) {
    if (store.hasData()) return@LaunchedEffect
    val legacy = runCatching { ir.vil3ntec.tohid.data.Migration.readLegacyData(context) }.getOrNull()
    if (legacy.isNullOrBlank()) return@LaunchedEffect
    store.importJson(legacy)
      .onSuccess { migration = "اطلاعات نسخهٔ قبلی آورده شد" }
      .onFailure { migration = "اطلاعات نسخهٔ قبلی خوانده نشد" }
  }

  // دکمهٔ برگشتِ گوشی از صفحهٔ فرعی برمی‌گردد، نه اینکه برنامه را ببندد
  BackHandler(enabled = sub != null) { sub = null }

  val snackbar = remember { SnackbarHostState() }
  LaunchedEffect(migration) {
    migration?.let { scope.launch { snackbar.showSnackbar(it) } }
  }

  if (authOpen) {
    WelcomeScreen { authOpen = false }
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
        onAccount = {
          val lic = ir.vil3ntec.tohid.sync.SyncStore(context)
          if (lic.accessToken.isNullOrBlank()) authOpen = true else sub = "settings"
        },
        onOpen = ::open,
      )
    },
    /*
     *  دکمهٔ شناور — یک بار، برای همهٔ صفحه‌ها.
     *
     *  در `floatingActionButton` اسکافولد می‌نشیند، پس خودِ اسکافولد
     *  جایش را کنار می‌گذارد و روی کارت‌ها و دکمه‌های صفحه نمی‌افتد.
     */
    floatingActionButton = {
      TohidSpeedDial(
        onNewProduct = { editProduct = ProductFormState() },
        onOpen = ::open,
        modifier = Modifier.padding(bottom = 96.dp),
      )
    },
    // پیام‌ها از پایینِ صفحه بالا می‌آیند و بالای نوارِ ناوبری می‌ایستند
    snackbarHost = {
      TohidSnackbar(
        host = snackbar,
        modifier = Modifier
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(bottom = 84.dp),
      )
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
        "sales" -> SalesHistoryScreen(store, data, snackbar)
        "reports" -> ReportsScreen(data)
        "receipts" -> ReceiptsScreen(data)
        "audit" -> AuditLogScreen(data)
        "settings" -> SettingsScreen(store, data, snackbar, theme, onTheme) { open("more") }
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
 *  نوارِ پایینِ شناور.
 *
 *  یک ظرفِ مستقل: پنج مقصد و نشانگرِ نوری، همه داخلِ همین یک قرص.
 *
 *  **جای نشانگر از خودِ چیدمان خوانده می‌شود، نه از حساب دستی.** هر تب
 *  موقعیت و پهنای واقعی‌اش را گزارش می‌کند و منحنی از روی همان کشیده
 *  می‌شود؛ پس با هر پهنای صفحه و هر تعداد تب سرِ جایش است.
 *
 *  **ارتفاع‌ها دقیق‌اند، نه کسری از قد نوار.** ریلِ خط و تاجِ منحنی روی
 *  عددهای مشخصی می‌نشینند و محتوای هر تب هم بینِ همان دو نوار جا
 *  می‌گیرد. با کسر گرفتن از قدِ نوار، تاج روی آیکن‌ها می‌افتاد — همان
 *  چیزی که دیده می‌شد.
 */
@Composable
private fun TohidNavBar(
  tabs: List<Tab>,
  current: String?,
  onPick: (String) -> Unit,
) {
  val colors = Shop.colors

  /*
   *  چهار ارتفاعِ ثابت، و محتوا بینِ دوتای وسطی:
   *    ۰ ────────────────────  لبهٔ بالای نوار
   *   ۱۴ ── تاجِ منحنی
   *   ۲۶ ── بالای آیکن
   *   ۷۰ ── پایینِ برچسب
   *   ۷۸ ── ریلِ خط
   *   ۹۰ ────────────────────  لبهٔ پایین
   */
  val barHeight = 90.dp
  val crestY = 14.dp
  val railY = 78.dp
  val contentTop = 26.dp
  val contentBottom = barHeight - railY + 8.dp
  val shape = RoundedCornerShape(percent = 50)

  var barLeft by remember { mutableStateOf(0f) }
  val slots = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }   // شاخص ← (مرکز، پهنا)

  val activeIndex = tabs.indexOfFirst { it.id == current }
  val target = slots[activeIndex]

  val centerX by animateFloatAsState(
    targetValue = (target?.first ?: 0f) - barLeft,
    animationSpec = tween(if (Motion.enabled) 340 else 0),
    label = "navCenter",
  )
  val slotWidth by animateFloatAsState(
    targetValue = target?.second ?: 0f,
    animationSpec = tween(if (Motion.enabled) 340 else 0),
    label = "navWidth",
  )
  // بارِ اول، خط از کفِ نوار بالا می‌آید و سرِ جایش می‌نشیند
  var arrived by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { arrived = true }
  val rise by animateFloatAsState(
    targetValue = if (arrived) 1f else 0f,
    animationSpec = tween(if (Motion.enabled) 520 else 0),
    label = "navRise",
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
    /* ----------------------- نشانگرِ نوری ----------------------- */
    if (activeIndex >= 0 && target != null && slotWidth > 0f && rise > 0.01f) {
      val line = colors.primary
      Canvas(Modifier.matchParentSize()) {
        val w = size.width
        val h = size.height
        val radius = h / 2f
        // خط تا سرِ گِردِ قرص نمی‌رود، وگرنه از زیرش بیرون می‌زند
        val inset = radius * 0.55f

        val yRail = railY.toPx()
        // تاج از کفِ نوار بالا می‌آید — همان بالا آمدنِ بارِ اول
        val yCrest = yRail - (yRail - crestY.toPx()) * rise

        val cx = centerX
        val crest = slotWidth * 0.30f
        val ramp = slotWidth * 0.28f

        val lo = inset + crest + ramp
        val hi = w - inset - crest - ramp
        val center = if (lo <= hi) cx.coerceIn(lo, hi) else w / 2f

        val inFoot = center - (crest + ramp)
        val inTop = center - crest
        val outTop = center + crest
        val outFoot = center + (crest + ramp)

        /*
         *  در هر چهار نقطهٔ اتصال، مماسِ ورودی و خروجی هر دو افقی‌اند —
         *  نقطه‌های کنترلِ هر خم روی همان ارتفاعِ خودِ نقطه می‌نشینند.
         *  همین است که منحنی را بی‌زاویه نگه می‌دارد.
         */
        val path = Path().apply {
          moveTo(inset, yRail)
          lineTo(inFoot, yRail)
          cubicTo(
            inFoot + ramp * 0.55f, yRail,
            inTop - ramp * 0.55f, yCrest,
            inTop, yCrest,
          )
          lineTo(outTop, yCrest)
          cubicTo(
            outTop + ramp * 0.55f, yCrest,
            outFoot - ramp * 0.55f, yRail,
            outFoot, yRail,
          )
          lineTo(w - inset, yRail)
        }

        // دورِ تبِ باز پررنگ، و هرچه دورتر محو‌تر — به هر دو سو
        val span = (w - 2 * inset).coerceAtLeast(1f)
        fun frac(x: Float) = ((x - inset) / span).coerceIn(0f, 1f)
        val f0 = (frac(inFoot) - 0.10f).coerceIn(0f, 1f)
        val f1 = (frac(outFoot) + 0.10f).coerceIn(f0 + 0.02f, 1f)

        fun brush(alpha: Float) = Brush.linearGradient(
          colorStops = arrayOf(
            0f to line.copy(alpha = alpha * 0.12f),
            f0 to line.copy(alpha = alpha),
            f1 to line.copy(alpha = alpha),
            1f to line.copy(alpha = alpha * 0.12f),
          ),
          start = Offset(inset, 0f),
          end = Offset(w - inset, 0f),
        )

        // سه لایه: هالهٔ پهن، هالهٔ نزدیک، و خودِ خط. نور از همین
        // لایه‌لایه بودن می‌آید؛ یک خطِ تنها فقط یک خط است.
        drawPath(path, brush(0.10f * rise), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, brush(0.22f * rise), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, brush(1f * rise), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
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
          animationSpec = tween(if (Motion.enabled) 260 else 0),
          label = "navFade",
        )
        val lift by animateFloatAsState(
          targetValue = if (active) 1f else 0.9f,
          animationSpec = spring(dampingRatio = 0.5f, stiffness = 620f),
          label = "navLift",
        )
        val tint = androidx.compose.ui.graphics.lerp(colors.muted, colors.primary, fade)

        Column(
          Modifier
            .weight(1f)
            .fillMaxHeight()
            // همین یک خط جای نشانگر را تعیین می‌کند
            .onGloballyPositioned {
              slots[index] = (it.positionInRoot().x + it.size.width / 2f) to it.size.width.toFloat()
            }
            .clip(RoundedCornerShape(22.dp))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = { onPick(t.id) },
            )
            // محتوا بینِ تاج و ریل می‌نشیند، پس خط هیچ‌وقت روی آیکن
            // نمی‌افتد — چه تبِ اول باشد چه آخر
            .padding(top = contentTop, bottom = contentBottom),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            t.icon,
            contentDescription = t.label,
            tint = tint,
            modifier = Modifier
              .size(21.dp)
              .graphicsLayer { scaleX = lift; scaleY = lift },
          )
          Spacer(Modifier.height(4.dp))
          Box(
            Modifier
              .size(4.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(colors.primary.copy(alpha = fade))
          )
          Spacer(Modifier.height(3.dp))
          Text(
            t.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/* ========================== دکمهٔ شناور ========================== */

/**
 *  کارهای پرتکرار، از هر صفحه‌ای.
 *
 *  تا حالا هر صفحه دکمهٔ افزودنِ خودش را داشت و صفحه‌هایی که نداشتند —
 *  گزارش، رسیدها، سابقه — هیچ راهِ کوتاهی نداشتند. حالا یک دکمه هست که
 *  همه‌جا هست و باز که شود، پنج کارِ روزمره را نشان می‌دهد.
 *
 *  جایش را اسکافولد تعیین می‌کند، نه ما: به همین دلیل روی کارت‌ها و
 *  دکمه‌های خودِ صفحه نمی‌افتد و بالای نوارِ پایین می‌ایستد.
 */
@Composable
private fun TohidSpeedDial(
  onNewProduct: () -> Unit,
  onOpen: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  val spin by animateFloatAsState(
    targetValue = if (open) 135f else 0f,
    animationSpec = tween(if (Motion.enabled) 220 else 0),
    label = "fabSpin",
  )
  val colors = Shop.colors

  Column(modifier, horizontalAlignment = Alignment.End) {
    AnimatedVisibility(
      visible = open,
      enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 3 },
      exit = fadeOut(tween(120)),
    ) {
      Column(horizontalAlignment = Alignment.End) {
        SpeedAction("فروش سریع", Icons.Filled.PointOfSale, colors.primary) { open = false; onOpen("sale") }
        SpeedAction("محصول جدید", Icons.Filled.ShoppingBag, colors.accent) { open = false; onNewProduct() }
        SpeedAction("ورود کالا به انبار", Icons.Filled.Inventory2, colors.success) { open = false; onOpen("warehouse") }
        SpeedAction("قرض‌دار تازه", Icons.Filled.Groups, colors.warning) { open = false; onOpen("debtors") }
        SpeedAction("ثبت مصرف", Icons.Filled.Payments, colors.danger) { open = false; onOpen("expenses") }
        Spacer(Modifier.height(10.dp))
      }
    }
    FloatingActionButton(
      onClick = { open = !open },
      containerColor = colors.primary,
      contentColor = Color.White,
    ) {
      Icon(
        Icons.Filled.Add,
        contentDescription = if (open) "بستن" else "افزودن",
        modifier = Modifier.graphicsLayer { rotationZ = spin },
      )
    }
  }
}

@Composable
private fun SpeedAction(
  text: String,
  icon: ImageVector,
  tint: Color,
  onClick: () -> Unit,
) {
  val colors = Shop.colors
  Row(
    Modifier
      .padding(bottom = 10.dp)
      .clip(RoundedCornerShape(24.dp))
      .background(colors.surface)
      .border(1.dp, colors.fieldBorder.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
      .clickable(onClick = onClick)
      .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = colors.text)
    Spacer(Modifier.width(10.dp))
    Box(
      Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = 0.18f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
  }
}
