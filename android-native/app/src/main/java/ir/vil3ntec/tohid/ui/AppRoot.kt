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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
 *  یک ظرفِ مستقل: یک قرصِ گرد که نشانِ برنامه و هر پنج مقصد داخلِ خودش‌اند
 *  و نشانگرِ نوری هم مالِ همین ظرف است، نه چیزی که رویش کشیده شده باشد.
 *
 *  **جای نشانگر از خودِ چیدمان خوانده می‌شود، نه از حساب دستی.** هر تب
 *  موقعیت و پهنای واقعی‌اش را با `onGloballyPositioned` گزارش می‌کند و
 *  منحنی از روی همان کشیده می‌شود. نسخهٔ قبلی جای تب‌ها را خودش حساب
 *  می‌کرد — پهنای نشان، فاصله‌های ظرف، وزنِ هر تب — و هر بار که یکی از
 *  آن‌ها عوض می‌شد، حساب با واقعیت فرق پیدا می‌کرد. حالا چیزی برای فرق
 *  کردن نمانده: مقصدِ منحنی همان مستطیلی است که تب واقعاً اشغال کرده.
 *
 *  و منحنی از دو سرِ قرص فاصله می‌گیرد. سرِ قرص گِرد است؛ خطی که تا
 *  آخرین پیکسل برود، از زیرِ آن گِردی بیرون می‌زند — همان چیزی که در
 *  عکس‌ها بیرونِ نوار دیده می‌شد.
 */
@Composable
private fun TohidNavBar(
  tabs: List<Tab>,
  current: String?,
  onPick: (String) -> Unit,
) {
  val colors = Shop.colors
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val barHeight = 78.dp
  val shape = RoundedCornerShape(percent = 50)

  // آنچه چیدمان گزارش می‌کند — همه در دستگاهِ مختصاتِ ریشه، تا ترتیبِ
  // رسیدنِ گزارش‌ها مهم نباشد
  var barLeft by remember { mutableStateOf(0f) }
  var brandInner by remember { mutableStateOf(0f) }
  val slots = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }   // شاخص ← (مرکز، پهنا)

  val activeIndex = tabs.indexOfFirst { it.id == current }
  val target = slots[activeIndex]

  // جای نشانگر نرم جابه‌جا می‌شود، نه اینکه بپرد
  val centerX by animateFloatAsState(
    targetValue = (target?.first ?: 0f) - barLeft,
    animationSpec = tween(if (Motion.enabled) 320 else 0),
    label = "navCenter",
  )
  val slotWidth by animateFloatAsState(
    targetValue = target?.second ?: 0f,
    animationSpec = tween(if (Motion.enabled) 320 else 0),
    label = "navWidth",
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
    if (activeIndex >= 0 && target != null && slotWidth > 0f) {
      val line = colors.primary
      val brandEdge = brandInner - barLeft
      Canvas(Modifier.matchParentSize()) {
        val w = size.width
        val h = size.height
        val radius = h / 2f

        /*
         *  فاصله از دو سرِ قرص.
         *
         *  در فاصلهٔ `0.55r` از لبه، بلندترین و کوتاه‌ترین نقطهٔ خودِ قرص
         *  حدودِ `0.11r` از بالا و پایین فاصله دارند؛ پس خطی که در این
         *  محدوده کشیده شود، از گِردیِ سر بیرون نمی‌زند.
         */
        val inset = radius * 0.55f

        // جهتِ جریان: از نشانِ برنامه به سرِ دیگر
        val fromRight = brandEdge > w / 2f
        val dir = if (fromRight) -1f else 1f
        val head = brandEdge.coerceIn(inset, w - inset)
        val tail = if (fromRight) inset else w - inset

        val cx = centerX.coerceIn(inset, w - inset)
        // پهنای برجستگی از پهنای واقعیِ همان تب می‌آید
        val crest = slotWidth * 0.30f
        val rise = slotWidth * 0.28f

        val yBot = h * 0.86f
        val yTop = h * 0.17f

        // برجستگی باید کامل داخلِ نوار بماند، حتی برای تبِ اولی و آخری
        val lo = minOf(head, tail) + crest + rise
        val hi = maxOf(head, tail) - crest - rise
        val center = if (lo <= hi) cx.coerceIn(lo, hi) else (lo + hi) / 2f

        val inFoot = center - dir * (crest + rise)
        val inTop = center - dir * crest
        val outTop = center + dir * crest
        val outFoot = center + dir * (crest + rise)

        /*
         *  نشکستنِ منحنی یک قاعده دارد: در هر چهار نقطهٔ اتصال، مماسِ
         *  ورودی و خروجی هر دو افقی باشند — یعنی نقطه‌های کنترلِ هر خم
         *  روی همان ارتفاعِ خودِ نقطه بنشینند.
         */
        val path = Path().apply {
          moveTo(head, yBot)
          lineTo(inFoot, yBot)
          cubicTo(
            inFoot + dir * rise * 0.55f, yBot,
            inTop - dir * rise * 0.55f, yTop,
            inTop, yTop,
          )
          lineTo(outTop, yTop)
          cubicTo(
            outTop + dir * rise * 0.55f, yTop,
            outFoot - dir * rise * 0.55f, yBot,
            outFoot, yBot,
          )
          lineTo(tail, yBot)
        }

        // دورِ تبِ باز پررنگ، و هرچه دورتر محو‌تر
        val axis = tail - head
        fun frac(x: Float) = if (axis == 0f) 0f else ((x - head) / axis).coerceIn(0f, 1f)
        val f0 = (frac(inFoot) - 0.06f).coerceIn(0.02f, 0.90f)
        val f1 = (frac(outFoot) + 0.06f).coerceIn(f0 + 0.02f, 0.96f)

        fun brush(alpha: Float) = Brush.linearGradient(
          colorStops = arrayOf(
            0f to line.copy(alpha = alpha * 0.30f),
            f0 to line.copy(alpha = alpha),
            f1 to line.copy(alpha = alpha),
            1f to line.copy(alpha = 0f),
          ),
          start = Offset(head, 0f),
          end = Offset(tail, 0f),
        )

        // هالهٔ کم‌رنگ زیرِ خط: خطِ یک‌نقطه‌ای روی زمینهٔ تیره گم می‌شود
        drawPath(path, brush(0.20f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, brush(1f), style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round))
      }
    }

    /* -------------------- نشانِ برنامه و تب‌ها -------------------- */
    Row(
      Modifier.fillMaxSize().padding(horizontal = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        Modifier
          .size(54.dp)
          .onGloballyPositioned {
            // لبهٔ داخلیِ نشان — خط از همین‌جا شروع می‌شود، نه از زیرش.
            // نشان اولین چیزِ ردیف است، پس در راست‌به‌چپ سمتِ راست
            // می‌نشیند و لبهٔ داخلی‌اش چپِ خودش است؛ در چپ‌به‌راست
            // برعکس.
            val left = it.positionInRoot().x
            brandInner = if (rtl) left else left + it.size.width
          }
          .clip(RoundedCornerShape(27.dp))
          .background(colors.surface2),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Filled.Storefront,
          contentDescription = "توحید",
          tint = colors.primary,
          modifier = Modifier.size(21.dp),
        )
      }

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
            // همین یک خط، جای نشانگر را تعیین می‌کند
            .onGloballyPositioned {
              slots[index] = (it.positionInRoot().x + it.size.width / 2f) to it.size.width.toFloat()
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = { onPick(t.id) },
            )
            // تبِ باز کمی بالا می‌آید تا داخلِ برجستگی بنشیند، نه رویش.
            // این فاصله داخلِ خانهٔ خودِ تب است، پس جای تب‌های دیگر تکان
            // نمی‌خورد.
            .padding(bottom = (9 * fade).dp),
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
          // نقطهٔ زیرِ آیکنِ تبِ باز
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
