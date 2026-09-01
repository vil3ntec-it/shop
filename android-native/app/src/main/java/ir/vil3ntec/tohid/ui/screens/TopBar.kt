package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice

/**
 *  سربرگِ همیشگی — همان نواری که در نسخهٔ وب بالای هر صفحه است.
 *
 *  تا حالا این دکمه‌ها فقط روی داشبورد بودند و در بقیهٔ صفحه‌ها ناپدید
 *  می‌شدند؛ کاربری که وسط فروش می‌خواست تم را عوض کند یا هشدارها را ببیند،
 *  باید اول به داشبورد برمی‌گشت. حالا مثل وب، همه‌جا هست: نام صفحه در یک
 *  طرف، و اشتراک، زنگ، روشن/تاریک، حساب، تنظیمات و نشانِ کاربر در طرف دیگر.
 *
 *  روی صفحه‌های باریک، ردیفِ دکمه‌ها افقی اسکرول می‌شود تا هیچ‌کدام بریده
 *  نشوند.
 */

/** نارنجیِ هشدار — از تم نمی‌آید چون در تمِ روشن و تاریک باید همین باشد */
private val ALERT_ORANGE = Color(0xFFF08A24)

/**
 *  آبیِ سربرگ.
 *
 *  ── چرا در تمِ روشن هم آبیِ تیره ────────────────────────────────────
 *  سربرگ تا دیروز هم‌رنگِ زمینه بود: در تمِ روشن یک نوارِ سفید با شش
 *  دکمهٔ ریز که هیچ‌کدام از زمینه جدا نمی‌شدند، و نامِ صفحه هم وسطِ
 *  همان سفیدی گم می‌شد. آبیِ تیره دو کار می‌کند: بالای صفحه یک «سر»
 *  پیدا می‌کند که چشم اول سراغش می‌رود، و دکمه‌های رویش — که سفیدِ
 *  نیم‌شفاف‌اند — بی‌نیاز از خطِ دور دیده می‌شوند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  در شب کمی تیره‌تر است تا کنارِ زمینهٔ سرمه‌ای، پله‌اش زیاد نباشد.
 */
private val BAR_DAY = listOf(Color(0xFF073E78), Color(0xFF0F62B4), Color(0xFF1E86D6))
private val BAR_NIGHT = listOf(Color(0xFF04121F), Color(0xFF0B2E52), Color(0xFF11487A))

/** جوهرِ روی سربرگ — همیشه روشن، چون زیرش همیشه آبیِ تیره است */
private val BAR_INK = Color(0xFFF4FAFF)
private val BAR_INK_SOFT = Color(0xFFC7DEF4)

/** نارنجیِ روی آبی — روشن‌تر از `ALERT_ORANGE` که برای زمینهٔ روشن است */
private val BAR_ALERT = Color(0xFFFFC06A)

/** قرمزِ «اشتراک دارد تمام می‌شود» — در هر دو تم همین */
private val URGENT_RED = listOf(Color(0xFFF06A62), Color(0xFFD8352C), Color(0xFFB0231C))

/** قهوه‌ایِ تیرهٔ روی طلا — همان که در صفحهٔ اشتراک است */
private val GOLD_INK = Color(0xFF3A2705)

/** یک هشدارِ زنگ */
data class Alert(
  val value: String,
  val title: String,
  val detail: String,
  val tint: Color,
  /** صفحه‌ای که این هشدار به آن مربوط است — با زدن روی هشدار همان‌جا باز می‌شود */
  val target: String,
)

/**
 *  هشدارِ اشتراک — فقط وقتی کاری از دستِ کاربر برمی‌آید.
 *
 *  اشتراکی که هفته‌ها اعتبار دارد خبری ندارد؛ شلوغ کردنِ زنگ با چیزی
 *  که کاری نمی‌شود کرد، باعث می‌شود کاربر دیگر زنگ را باز نکند.
 */
private fun subscriptionAlert(
  status: ir.vil3ntec.tohid.sync.License.Status?,
  danger: Color,
  warning: Color,
): Alert? {
  if (status == null) return null
  return when (status.state) {
    ir.vil3ntec.tohid.sync.License.State.EXPIRED,
    ir.vil3ntec.tohid.sync.License.State.GRACE ->
      Alert("اشتراک", "اشتراک تمام شده", "برای باز شدن دوباره، تمدید کنید", danger, "vip")

    ir.vil3ntec.tohid.sync.License.State.ACTIVE -> {
      val left = status.daysLeft()
      if (left in 0..SUBSCRIPTION_WARN_DAYS) {
        Alert(
          if (left <= 0) "امروز" else "${left.fa()} روز",
          "اشتراک رو به پایان است",
          "پیش از تمام شدن تمدید کنید تا صندوق فروش بسته نشود",
          if (left <= 3) danger else warning,
          "vip",
        )
      } else null
    }

    //  NONE یعنی هنوز اشتراکی نبوده و INVALID یعنی مجوز خراب است؛
    //  هیچ‌کدام «رو به پایان» نیستند و جایشان اینجا نیست
    else -> null
  }
}

/**
 *  مرزِ «رو به پایان» — یک هفته.
 *
 *  یک جا نوشته شده و سه جا خوانده می‌شود: هشدارِ زنگ، رنگِ نشانِ سربرگ،
 *  و کارتِ وضعیت در صفحهٔ اشتراک. اگر هرکدام عددِ خودش را داشت، کاربر
 *  نشانِ قرمز می‌دید و در صفحهٔ اشتراک «همه‌چیز مرتب است».
 */
const val SUBSCRIPTION_WARN_DAYS = 7

/** همان هشدارهایی که نسخهٔ وب در زنگ نشان می‌دهد */
@Composable
fun rememberAlerts(d: ShopData): List<Alert> {
  val context = LocalContext.current
  val backupStale = remember { BackupClock.isStale(context) }

  /*
   *  اشتراک، پیش از آنکه تمام شود.
   *
   *  تا امروز هیچ خبری نبود: فروشنده یک روز صبح می‌آمد و صندوقِ فروشش
   *  قفل بود. برای کسی که دکانش با همین برنامه می‌چرخد، این یعنی یک
   *  روزِ کاری از دست رفته و یک تماسِ عصبانی — نه یک ناراحتیِ کوچک.
   *
   *  یک بار حساب می‌شود، نه با هر بار کشیده شدنِ صفحه: سنجیدنِ مجوز
   *  یعنی بررسیِ امضای رمزنگاری.
   */
  /*
   *  خبرهای دکان — آنچه بقیهٔ اعضا کرده‌اند.
   *
   *  هشدارهای زیر از دفترِ **محلی** حساب می‌شوند و فقط همین گوشی را
   *  می‌بینند. اینها از سرور می‌آیند: فروشی که کریم زد، کالایی که
   *  دستِ او تمام شد. برای صاحب دکانی که خانه است، همین‌ها مهم‌اند.
   */
  var news by remember { mutableStateOf<List<ir.vil3ntec.tohid.data.repo.EventsRepository.Event>>(emptyList()) }
  LaunchedEffect(Unit) {
    if (!ir.vil3ntec.tohid.data.repo.Backend.isReady(context)) return@LaunchedEffect
    ir.vil3ntec.tohid.data.repo.Backend.events(context).feed()
      .onSuccess { feed ->
        //  خبرِ خودم برای خودم خبر نیست
        val me = ir.vil3ntec.tohid.sync.SyncStore(context).accountId
        news = feed.events.filter { it.userId != me }.take(12)
      }
    //  شکست بی‌صداست: نبودنِ اینترنت نباید زنگ را خالی نشان دهد
  }

  val subscription = remember {
    runCatching {
      ir.vil3ntec.tohid.sync.LicenseGuard.status(
        context, ir.vil3ntec.tohid.sync.SyncStore(context),
      )
    }.getOrNull()
  }
  // رنگ‌ها از CompositionLocal می‌آیند و خواندنشان فقط داخل بدنهٔ کامپوزبل
  // مجاز است، نه داخل لامبدای remember — پس همین‌جا گرفته می‌شوند.
  val danger = Shop.colors.danger
  val warning = Shop.colors.warning
  val accent = Shop.colors.accent
  return remember(d, backupStale, danger, warning, accent, subscription, news) {
    buildList {
      //  اول از همه، چون از هر موجودیِ کمی مهم‌تر است
      subscriptionAlert(subscription, danger, warning)?.let { add(it) }

      //  بعد خبرهای بقیهٔ اعضا — تازه‌ترین بالا
      news.forEach { event ->
        add(
          Alert(
            value = event.userName.ifBlank { "دکان" },
            title = event.title,
            detail = event.body.ifBlank { sinceText(event.at) },
            tint = if (event.kind == "stock_out") danger else accent,
            target = if (event.kind == "stock_out") "products" else "sales",
          )
        )
      }
      d.products.filter { ShopStore.stockStatus(d, it) == "out" }.forEach {
        add(Alert("تمام شد", it.name, "کالا موجود نیست", danger, "products"))
      }
      d.products.filter { ShopStore.stockStatus(d, it) == "low" }.forEach {
        add(Alert("موجودی کم", it.name, "${qty(ShopStore.stock(d, it.id))} مانده", warning, "products"))
      }
      val supplierDebt = d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id) }
      if (supplierDebt > 0) {
        add(Alert("بدهی به تأمین‌کننده", "${money(supplierDebt)} افغانی", "پرداخت‌نشده", warning, "purchasing"))
      }
      if (backupStale) {
        add(Alert("پشتیبان", "از اطلاعات دکان پشتیبان بگیرید", BackupClock.text(context), warning, "settings"))
      }
      d.debtors
        .map { it to ShopStore.debt(d, it.id) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(3)
        .forEach { (debtor, amount) ->
          add(Alert("قرض‌دار", debtor.name, "${money(amount)} افغانی", danger, "debtors"))
        }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TohidTopBar(
  title: String,
  d: ShopData,
  theme: ThemeChoice,
  onTheme: (ThemeChoice) -> Unit,
  onSettings: () -> Unit,
  onAccount: () -> Unit,
  onOpen: (String) -> Unit,
  /**
   *  راهِ برگشت از زیرصفحه — `null` یعنی همین حالا صفحهٔ اصلیِ یک تب
   *  باز است و جایی برای برگشتن نیست.
   */
  onBack: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val alerts = rememberAlerts(d)
  var alertsOpen by remember { mutableStateOf(false) }

  //  ساعت و باتریِ گوشی روی آبیِ تیره می‌نشینند، پس باید سفید باشند.
  //  با رفتنِ سربرگ از صفحه، همان‌طور که بود برمی‌گردد — صفحهٔ قفل و
  //  صفحهٔ ورود زمینهٔ روشن دارند و آنجا آیکنِ سفید دیده نمی‌شود.
  val view = LocalView.current
  DisposableEffect(view) {
    val bars = hostWindow(view.context)?.let { WindowCompat.getInsetsController(it, view) }
    val before = bars?.isAppearanceLightStatusBars
    bars?.isAppearanceLightStatusBars = false
    onDispose { if (bars != null && before != null) bars.isAppearanceLightStatusBars = before }
  }

  /*
   *  نامِ صفحه از یک جایی به بعد پهن‌تر نمی‌شود.
   *
   *  ردیفِ دکمه‌ها دومین فرزندِ این `Row` است و آنچه از پهنا مانده باشد
   *  را می‌گیرد. اگر نام سقف نداشت، روی گوشیِ باریک نامِ بلند («خرید و
   *  تأمین‌کننده») تمامِ عرض را می‌گرفت و ردیفِ دکمه‌ها بیرونِ صفحه
   *  می‌ماند؛ اسکرولِ افقی هم چیزی را که پهنا نگرفته نمی‌تواند نشان
   *  بدهد.
   */
  val screenWidth = LocalConfiguration.current.screenWidthDp
  //  دکمهٔ برگشت هم از همین سهم برمی‌دارد، وگرنه روی گوشیِ باریک
  //  مجموعِ «دکمه + نام» از سقف می‌زد بیرون
  val titleMax: Dp = ((screenWidth * if (isTablet()) 0.5f else 0.38f).dp -
    (if (onBack != null) 42.dp else 0.dp)).coerceAtLeast(56.dp)

  Box(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
      .background(Brush.linearGradient(if (isNightBar()) BAR_NIGHT else BAR_DAY))
      .barGlow()
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        // سربرگ زیرِ نوارِ وضعیتِ گوشی می‌رفت و ساعت و باتری روی دکمه‌ها
        // می‌افتاد. این padding همان بلندیِ نوارِ وضعیت را کنار می‌گذارد،
        // هر اندازه‌ای که روی آن دستگاه باشد — بریدگیِ دوربین هم همین‌طور.
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        /*
         *  دکمهٔ برگشت — تنها راهِ روی صفحه.
         *
         *  ── چه چیزی را می‌بندد ──────────────────────────────────────
         *  از «بیشتر» که واردِ مصارف یا گزارش‌ها یا خرید می‌شدی، هیچ
         *  دکمه‌ای برای برگشتن نبود: فقط دکمهٔ سختِ خودِ گوشی کار
         *  می‌کرد. روی گوشی‌هایی که با اشاره کار می‌کنند — و برای
         *  کسی که آن اشاره را نمی‌داند — یعنی گیر افتادن در صفحه و
         *  زدنِ یکی از تب‌های پایین برای فرار.
         *  ────────────────────────────────────────────────────────────
         *
         *  در راست‌به‌چپ، فلشِ برگشت به راست است — همان `ArrowForward`
         *  که بقیهٔ صفحه‌های برنامه هم برای «بازگشت» می‌گذارند.
         */
        if (onBack != null) {
          Box(
            Modifier
              .size(34.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.14f))
              .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Filled.ArrowForward,
              contentDescription = "بازگشت",
              tint = BAR_INK,
              modifier = Modifier.size(18.dp),
            )
          }
          Spacer(Modifier.width(8.dp))
        }
        Text(
          title,
          style = MaterialTheme.typography.titleMedium,
          color = BAR_INK,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = titleMax),
        )
      }

      Row(
        Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        /*
         *  کلیدهای سربرگ از هم جدا شده‌اند.
         *
         *  تا حالا هر پنج کلید یک کادرِ خاکستریِ یکسان داشتند و کاربر
         *  باید آیکنِ ریزشان را می‌خواند تا بفهمد کدام است. حالا هرکدام
         *  رنگ و شکلِ کارِ خودش را دارد: اشتراک طلایی — و قرمز وقتی رو
         *  به پایان است — حساب و تنظیمات شیشه‌ای، و هشدارها نارنجی.
         */
        //  نقطهٔ همگام‌سازی، اولین چیزِ ردیف: کوچک، ولی همیشه سرِ جایش
        SyncDot()

        VipChip { onOpen("vip") }

        AccountChip(onClick = onAccount)

        AlertChip(count = alerts.size) { alertsOpen = true }

        BarButton(
          if (theme == ThemeChoice.DARK) Icons.Filled.LightMode else Icons.Filled.DarkMode,
          "روشن یا تاریک",
        ) {
          onTheme(if (theme == ThemeChoice.DARK) ThemeChoice.LIGHT else ThemeChoice.DARK)
        }

        BarButton(Icons.Filled.Settings, "تنظیمات", onClick = onSettings)
      }
    }
  }

  if (alertsOpen) {
    ModalBottomSheet(onDismissRequest = { alertsOpen = false }, containerColor = Shop.colors.bg) {
      Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
        Text("هشدارهای فروشگاه", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(10.dp))
        if (alerts.isEmpty()) {
          EmptyNote("همه‌چیز مرتب است — هشداری نیست.")
        } else {
          alerts.forEach { alert ->
            // هشداری که فقط خبر می‌دهد، نصفِ کار است: با زدن روی آن،
            // همان صفحه‌ای باز می‌شود که کار را می‌شود در آن درست کرد
            Row(
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { alertsOpen = false; onOpen(alert.target) }
                .padding(horizontal = 6.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(Modifier.weight(1f)) {
                Text(alert.title, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
                Text(alert.detail, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
              }
              Text(
                alert.value,
                style = MaterialTheme.typography.labelLarge,
                color = alert.tint,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }
  }
}

/**
 *  پنجرهٔ اکتیویتی، از دلِ هر `Context`ی.
 *
 *  `view.context` همیشه خودِ اکتیویتی نیست: کامپوز آن را در یک یا چند
 *  `ContextWrapper` می‌پیچد (تم، زبان، اندازهٔ قلم). تبدیلِ مستقیم به
 *  اکتیویتی روی همان دستگاه‌ها `null` می‌داد و رنگِ نوارِ وضعیت بی‌صدا
 *  عوض نمی‌شد.
 */
private fun hostWindow(context: android.content.Context): android.view.Window? {
  var cursor: android.content.Context? = context
  while (cursor != null) {
    if (cursor is android.app.Activity) return cursor.window
    cursor = (cursor as? android.content.ContextWrapper)?.baseContext
  }
  return null
}

/** شب است یا روز — از روشناییِ زمینهٔ تم فهمیده می‌شود، نه از تنظیمِ جدا */
@Composable
private fun isNightBar(): Boolean = Shop.colors.bg.luminance() < 0.5f

/**
 *  افکتِ سربرگ — نورِ ملایم و **ساکن**.
 *
 *  دو لکهٔ نورِ کم‌رنگ روی آبی می‌نشیند: سفید در بالای یک سر و
 *  فیروزه‌ای در پایینِ سرِ دیگر — همان دو رنگی که کلِ برنامه دارد.
 *
 *  ── چه چیزی از اینجا برداشته شد ───────────────────────────────────
 *  یک نوارِ نورِ مورب هم بود که بی‌وقفه این‌طرف و آن‌طرف می‌رفت. روی
 *  سربرگی که در همهٔ صفحه‌ها هست، این یعنی یک انیمیشنِ همیشه‌روشن در
 *  گوشهٔ چشمِ کاربر و باتری‌ای که برای تزئین می‌رفت. جایش نورهای
 *  نقطه‌ای است که فقط با زدنِ خودِ کاربر می‌آیند (`Sparks.kt`).
 *  ──────────────────────────────────────────────────────────────────
 *
 *  خطِ نازکِ پایین تزئین نیست: بدونش، سربرگ و محتوای پشتش در تمِ شب به
 *  هم می‌چسبیدند و لبهٔ گِردِ پایین دیده نمی‌شد.
 */
private fun Modifier.barGlow(): Modifier =
  drawBehind {
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
        center = Offset(size.width * 0.14f, 0f),
        radius = size.height * 1.5f,
      ),
      radius = size.height * 1.5f,
      center = Offset(size.width * 0.14f, 0f),
    )
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color(0xFF6EEDE2).copy(alpha = 0.18f), Color.Transparent),
        center = Offset(size.width * 0.92f, size.height),
        radius = size.height * 1.3f,
      ),
      radius = size.height * 1.3f,
      center = Offset(size.width * 0.92f, size.height),
    )
    drawLine(
      color = Color.White.copy(alpha = 0.22f),
      start = Offset(0f, size.height - 0.75f),
      end = Offset(size.width, size.height - 0.75f),
      strokeWidth = 1.5f,
    )
  }

/**
 *  نقطهٔ همگام‌سازی — سبز، زرد، قرمز.
 *
 *  ── چه چیزی را می‌بندد ────────────────────────────────────────────
 *  `AutoSync` وضعیتش را همیشه داشت (`lastOk`، `lastError`، `running`)
 *  ولی هیچ صفحه‌ای آن را نشان نمی‌داد. فروشنده‌ای که در زیرزمینِ
 *  بی‌آنتن کار می‌کرد، نمی‌دانست ۳۰ فروشش هنوز روی گوشی است و روی
 *  سرور ننشسته. تا وقتی گوشی سالم بود کسی نمی‌فهمید؛ روزی که گوشی
 *  گم می‌شد، تازه معلوم می‌شد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  فقط وقتی دیده می‌شود که حسابی در کار باشد: برنامه بدونِ سرور هم
 *  کامل کار می‌کند و آنجا این نقطه معنایی ندارد و فقط سؤال می‌سازد.
 *
 *  زدن روی آن، جزئیات را در یک برگهٔ کوچک باز می‌کند: آخرین همگام‌سازی
 *  کِی بود، چند مورد در انتظار است، و اگر تغییری اعمال نشده چرا.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncDot() {
  val context = LocalContext.current
  //  «حساب دارد یا نه» با هر بار کشیده شدنِ سربرگ پرسیده نمی‌شود
  val active = remember { ir.vil3ntec.tohid.data.repo.Backend.isReady(context) }
  if (!active) return

  var open by remember { mutableStateOf(false) }
  val health = AutoSync.health
  //  روی آبیِ سربرگ، سبز و نارنجیِ تیره دیده نمی‌شدند؛ همین رنگ‌ها
  //  روشن‌تر شده‌اند. داخلِ برگه، رنگِ متن‌ها از تم می‌آید.
  val tint = when (health) {
    AutoSync.Health.OK -> Color(0xFF5BE39B)
    AutoSync.Health.WAITING -> BAR_ALERT
    AutoSync.Health.FAILED -> Color(0xFFFF8B84)
  }

  Row(
    Modifier
      .clip(RoundedCornerShape(999.dp))
      .background(Color.White.copy(alpha = 0.14f))
      .clickable { open = true }
      .padding(horizontal = 9.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Box(Modifier.size(9.dp).clip(RoundedCornerShape(999.dp)).background(tint))
    //  عدد فقط وقتی می‌آید که واقعاً چیزی مانده باشد — نقطهٔ خالی
    //  آرام‌تر است و «همه‌چیز رفته» را بهتر می‌گوید
    if (AutoSync.pendingCount > 0) {
      Text(
        AutoSync.pendingCount.fa(),
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        fontWeight = FontWeight.Bold,
      )
    }
  }

  if (open) {
    ModalBottomSheet(onDismissRequest = { open = false }, containerColor = Shop.colors.bg) {
      Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
        Text("همگام‌سازی", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(10.dp))

        val headline = when (health) {
          AutoSync.Health.OK -> "همه‌چیز روی سرور نشسته"
          AutoSync.Health.WAITING ->
            if (AutoSync.running) "در حال فرستادن…"
            else "${AutoSync.pendingCount.fa()} مورد در انتظار"
          AutoSync.Health.FAILED -> "آخرین تلاش ناموفق بود"
        }
        //  رنگِ داخلِ برگه از تم می‌آید، نه از رنگِ روی سربرگ: زمینهٔ
        //  برگه روشن است و سبزِ روشن رویش خوانده نمی‌شد
        val sheetTint = when (health) {
          AutoSync.Health.OK -> Shop.colors.success
          AutoSync.Health.WAITING -> Shop.colors.warning
          AutoSync.Health.FAILED -> Shop.colors.danger
        }
        Text(headline, style = MaterialTheme.typography.bodyMedium, color = sheetTint, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(6.dp))
        Text(
          if (AutoSync.lastOk > 0) "آخرین همگام‌سازی: ${sinceText(AutoSync.lastOk)}"
          else "هنوز همگام‌سازیِ موفقی انجام نشده",
          style = MaterialTheme.typography.labelMedium,
          color = Shop.colors.muted,
        )

        AutoSync.lastError?.let {
          Spacer(Modifier.height(8.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFFD64545))
        }

        //  تعارض‌ها: خطا نیستند، ولی کاربر باید بداند کدام تغییرش
        //  اعمال نشد — همان چیزی که تا دیروز بی‌صدا گم می‌شد
        AutoSync.rejectionNote?.let {
          Spacer(Modifier.height(8.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = ALERT_ORANGE)
        }

        Spacer(Modifier.height(6.dp))
        Text(
          "تغییرها روی گوشی ثبت می‌شوند و خودشان می‌روند؛ نبودنِ اینترنت " +
            "چیزی را از بین نمی‌برد.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )
      }
    }
  }
}

/** «۳ دقیقه پیش» — نه ساعتِ خام */
private fun sinceText(at: Long): String {
  val minutes = ((System.currentTimeMillis() - at) / 60000L).coerceAtLeast(0)
  return when {
    minutes < 1 -> "همین حالا"
    minutes < 60 -> "${minutes.fa()} دقیقه پیش"
    minutes < 24 * 60 -> "${(minutes / 60).fa()} ساعت پیش"
    else -> "${(minutes / (24 * 60)).fa()} روز پیش"
  }
}

/**
 *  کلیدِ اشتراک — روزهای مانده، و قرمز وقتی کم مانده.
 *
 *  ── چه چیزی را می‌بندد ────────────────────────────────────────────
 *  تا دیروز روی این کلید فقط «VIP» نوشته بود؛ یعنی کسی که اشتراکش
 *  فردا تمام می‌شد و کسی که سه ماه اعتبار داشت، هر دو همان یک نشانِ
 *  طلایی را می‌دیدند. خبرِ تمام شدن فقط داخلِ زنگ بود و زنگ باز
 *  نمی‌شد. حالا عددِ روز روی خودِ نشان است.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  از یک هفته به پایین (`SUBSCRIPTION_WARN_DAYS`) طلایی جایش را به قرمز
 *  می‌دهد و نشان آرام نفس می‌کشد. قرمز در تمِ روشن و تاریک همان قرمز است:
 *  «تمام شدن» چیزی نیست که با تمِ گوشی عوض شود.
 *
 *  تا انگشت رویش است، نور از کناره‌هایش بیرون می‌آید — جای آن برقی که
 *  تا دیروز بی‌وقفه روی نشان می‌لغزید.
 *
 *  وضعیت یک بار سنجیده می‌شود، نه با هر بار کشیده شدنِ سربرگ —
 *  سنجیدنِ مجوز یعنی بررسیِ امضای رمزنگاری.
 */
@Composable
private fun VipChip(onClick: () -> Unit) {
  val context = LocalContext.current
  val status = remember {
    runCatching {
      ir.vil3ntec.tohid.sync.LicenseGuard.status(
        context, ir.vil3ntec.tohid.sync.SyncStore(context),
      )
    }.getOrNull()
  }
  val days = status?.daysLeft() ?: 0
  val state = status?.state

  //  «کم مانده» یعنی یا تمام شده، یا از یک هفته کمتر مانده
  val urgent = when (state) {
    ir.vil3ntec.tohid.sync.License.State.EXPIRED,
    ir.vil3ntec.tohid.sync.License.State.GRACE -> true
    ir.vil3ntec.tohid.sync.License.State.ACTIVE -> days <= SUBSCRIPTION_WARN_DAYS
    else -> false
  }

  /*
   *  متنِ کلید.
   *
   *  «۰ روز» نوشته نمی‌شود: کسی که آن را ببیند فکر می‌کند همین حالا
   *  بسته شده. آخرین روز «امروز» است.
   */
  val label = when {
    state == ir.vil3ntec.tohid.sync.License.State.EXPIRED -> "تمدید"
    state == ir.vil3ntec.tohid.sync.License.State.GRACE -> "مهلت"
    state == ir.vil3ntec.tohid.sync.License.State.ACTIVE && days > 0 -> "${days.fa()} روز"
    state == ir.vil3ntec.tohid.sync.License.State.ACTIVE -> "امروز"
    else -> "VIP"
  }

  //  نور تا وقتی می‌آید که انگشت روی نشان است
  val press = remember { MutableInteractionSource() }
  val touched by press.collectIsPressedAsState()

  /*
   *  تپشِ قرمز — تنها حرکتِ همیشگیِ این نشان.
   *
   *  و فقط وقتی اشتراک رو به پایان است: آن‌جا دیده شدن خودش کارِ نشان
   *  است. در حالتِ طلایی هیچ ساعتی کار نمی‌کند — انیمیشن ساخته هم
   *  نمی‌شود، نه اینکه ساخته شود و دیده نشود.
   */
  val beat = if (urgent) urgentBeat() else 1f

  val ink = if (urgent) Color.White else GOLD_INK
  Row(
    Modifier
      .height(36.dp)
      .edgeSparks(touched, if (urgent) Color(0xFFFF9A92) else Color(0xFFFBE08A))
      .clip(RoundedCornerShape(13.dp))
      .background(
        Brush.linearGradient(
          if (urgent) URGENT_RED
          else listOf(Color(0xFFFBE08A), Color(0xFFF6C93F), Color(0xFFFFF3C4), Color(0xFFD79A14))
        )
      )
      .border(
        1.4.dp,
        if (urgent) Color.White.copy(alpha = beat * 0.8f) else Color.Transparent,
        RoundedCornerShape(13.dp),
      )
      .clickable(interactionSource = press, indication = null, onClick = onClick)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      if (urgent) Icons.Filled.HourglassBottom else Icons.Filled.WorkspacePremium,
      contentDescription = if (urgent) "اشتراک رو به پایان — تمدید" else "اشتراک و قیمت‌ها",
      tint = ink,
      modifier = Modifier.size(15.dp),
    )
    Text(
      label,
      style = MaterialTheme.typography.labelLarge,
      color = ink,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
    )
  }
}

/**
 *  کلیدِ حساب — سفیدِ نیم‌شفاف، با هاله‌ای که نفس می‌کشد.
 *
 *  رنگش از تم نمی‌آید: زیرش همیشه آبیِ تیرهٔ سربرگ است و آبیِ تم روی
 *  آبیِ تیره دیده نمی‌شد.
 */
@Composable
private fun AccountChip(onClick: () -> Unit) {
  val motion = rememberInfiniteTransition(label = "accountChip")
  val breathe by motion.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.85f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) 2000 else 1, easing = EaseInOutSine),
      RepeatMode.Reverse,
    ),
    label = "breathe",
  )
  val press = remember { MutableInteractionSource() }
  val touched by press.collectIsPressedAsState()
  Box(
    Modifier
      .size(34.dp)
      .edgeSparks(touched, Color.White)
      .clip(RoundedCornerShape(12.dp))
      .background(Color.White.copy(alpha = 0.16f))
      .border(1.2.dp, Color.White.copy(alpha = breathe * 0.75f), RoundedCornerShape(12.dp))
      .clickable(interactionSource = press, indication = LocalIndication.current, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      Icons.Filled.Person,
      contentDescription = "حساب",
      tint = BAR_INK,
      modifier = Modifier.size(18.dp),
    )
  }
}

/** تپشِ آرامِ هشدار — فقط برای نشانِ اشتراکی که رو به پایان است */
@Composable
private fun urgentBeat(): Float {
  val motion = rememberInfiniteTransition(label = "vipUrgent")
  val beat by motion.animateFloat(
    initialValue = 0.55f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) 1200 else 1, easing = EaseInOutSine),
      RepeatMode.Reverse,
    ),
    label = "beat",
  )
  return beat
}

/** کلیدِ هشدارها — نارنجیِ روشن، تا روی آبیِ سربرگ دیده شود */
@Composable
private fun AlertChip(count: Int, onClick: () -> Unit) {
  Box(contentAlignment = Alignment.TopEnd) {
    Box(
      Modifier
        .size(34.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(BAR_ALERT.copy(alpha = 0.26f))
        .border(1.2.dp, BAR_ALERT.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.Notifications,
        contentDescription = "هشدارها",
        tint = BAR_ALERT,
        modifier = Modifier.size(18.dp),
      )
    }
    if (count > 0) {
      Box(
        Modifier.size(15.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFD8352C)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          count.coerceAtMost(9).fa(),
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
        )
      }
    }
  }
}

/**
 *  دکمهٔ کوچکِ گردِ سربرگ، با شمارندهٔ اختیاری.
 *
 *  کارتِ شیشه‌ای: سفیدِ نیم‌شفاف روی آبی. رنگِ `surface` تم اینجا کار
 *  نمی‌کرد — در تمِ روشن سفیدِ پُر بود و روی آبی مثل یک لکه می‌نشست.
 */
@Composable
private fun BarButton(
  icon: ImageVector,
  description: String,
  badge: Int = 0,
  onClick: () -> Unit,
) {
  Box(contentAlignment = Alignment.TopEnd) {
    Box(
      Modifier
        .size(34.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color.White.copy(alpha = 0.14f))
        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = description, tint = BAR_INK_SOFT, modifier = Modifier.size(17.dp))
    }
    if (badge > 0) {
      Box(
        Modifier.size(15.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFD8352C)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          badge.coerceAtMost(9).fa(),
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
        )
      }
    }
  }
}
