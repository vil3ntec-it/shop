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
        val yLampEnd = yLamp + lampThick.toPx()

        // نوارِ نور: کمی از خودِ تب باریک‌تر، وسطِ آن
        val halfLamp = lampWidth * 0.21f

        /*
         *  مخروطِ نور.
         *
         *  یک ذوزنقه که از نوار شروع می‌شود و رو به پایین پهن‌تر
         *  می‌شود، با شیبِ رنگی که پایین به شفافیت می‌رسد. سه لایه روی
         *  هم — پهن و کم‌رنگ، میانه، باریک و پررنگ — چون یک ذوزنقهٔ
         *  تنها لبهٔ تیز دارد و نورِ واقعی لبهٔ تیز ندارد.
         */
        fun cone(topHalf: Float, bottomHalf: Float, alpha: Float) {
          val path = Path().apply {
            moveTo(cx - topHalf, yLampEnd)
            lineTo(cx + topHalf, yLampEnd)
            lineTo(cx + bottomHalf, h)
            lineTo(cx - bottomHalf, h)
            close()
          }
          drawPath(
            path,
            Brush.verticalGradient(
              colors = listOf(
                glow.copy(alpha = alpha * on),
                glow.copy(alpha = alpha * 0.45f * on),
                glow.copy(alpha = 0f),
              ),
              startY = yLampEnd,
              endY = h,
            ),
          )
        }
        cone(halfLamp * 1.15f, lampWidth * 0.62f, 0.07f)
        cone(halfLamp * 1.00f, lampWidth * 0.44f, 0.09f)
        cone(halfLamp * 0.85f, lampWidth * 0.28f, 0.11f)

        // هالهٔ گردِ زیرِ نوار — روشن‌ترین جای نور، درست زیرِ چراغ
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = 0.30f * on), glow.copy(alpha = 0f)),
            center = Offset(cx, yLampEnd),
            radius = lampWidth * 0.55f,
          ),
          radius = lampWidth * 0.55f,
          center = Offset(cx, yLampEnd),
        )

        // هالهٔ نرمِ دورِ آیکن
        val rIcon = 20.dp.toPx()
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = 0.22f * on), glow.copy(alpha = 0f)),
            center = Offset(cx, iconCenter.toPx()),
            radius = rIcon,
          ),
          radius = rIcon,
          center = Offset(cx, iconCenter.toPx()),
        )

        // خودِ نوار: هالهٔ کوتاهش، بعد خطِ روشن
        drawRoundRect(
          brush = Brush.horizontalGradient(
            colors = listOf(
              glow.copy(alpha = 0f),
              glow.copy(alpha = 0.55f * on),
              glow.copy(alpha = 0f),
            ),
            startX = cx - halfLamp * 1.9f,
            endX = cx + halfLamp * 1.9f,
          ),
          topLeft = Offset(cx - halfLamp * 1.9f, yLamp - 1.dp.toPx()),
          size = Size(halfLamp * 3.8f, lampThick.toPx() + 2.dp.toPx()),
          cornerRadius = CornerRadius(lampThick.toPx()),
        )
        drawRoundRect(
          color = glow.copy(alpha = on),
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
