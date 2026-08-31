package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
      if (left in 0..WARN_DAYS) {
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

/** از این تعداد روز به بعد، خبر داده می‌شود */
private const val WARN_DAYS = 7

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
) {
  val context = LocalContext.current
  val alerts = rememberAlerts(d)
  var alertsOpen by remember { mutableStateOf(false) }

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
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      color = Shop.colors.text,
      fontWeight = FontWeight.Bold,
    )

    Row(
      Modifier.horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      /*
       *  سه کلیدِ اولِ سربرگ از هم جدا شده‌اند.
       *
       *  تا حالا هر پنج کلید یک کادرِ خاکستریِ یکسان داشتند و کاربر باید
       *  آیکنِ ریزشان را می‌خواند تا بفهمد کدام است. حالا هرکدام رنگ و
       *  شکلِ کارِ خودش را دارد: اشتراک طلایی و با نوشتهٔ VIP، حساب آبی و
       *  با تپش، و هشدارها نارنجی.
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
  val tint = when (health) {
    AutoSync.Health.OK -> Color(0xFF29A745)
    AutoSync.Health.WAITING -> ALERT_ORANGE
    AutoSync.Health.FAILED -> Color(0xFFD64545)
  }

  Row(
    Modifier
      .clip(RoundedCornerShape(999.dp))
      .background(tint.copy(alpha = 0.12f))
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
        Text(headline, style = MaterialTheme.typography.bodyMedium, color = tint, fontWeight = FontWeight.Bold)

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
 *  کلیدِ اشتراک — طلایی، با نوشتهٔ VIP.
 *
 *  از بقیه کمی بزرگ‌تر است و به‌جای آیکنِ تنها، خودِ کلمه را می‌نویسد:
 *  «اشتراک» چیزی است که کاربر باید پیدایش کند، نه چیزی که دنبالش
 *  بگردد. برقِ روی آن همان برقِ صفحهٔ اشتراک است تا یکی بودنشان
 *  فهمیده شود.
 */
@Composable
private fun VipChip(onClick: () -> Unit) {
  val motion = rememberInfiniteTransition(label = "vipChip")
  val shine by motion.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) 2600 else 1, easing = EaseInOutSine),
      RepeatMode.Reverse,
    ),
    label = "shine",
  )
  Row(
    Modifier
      .height(36.dp)
      .clip(RoundedCornerShape(13.dp))
      .background(
        Brush.linearGradient(
          listOf(Color(0xFFFBE08A), Color(0xFFF6C93F), Color(0xFFFFF3C4), Color(0xFFD79A14))
        )
      )
      .drawWithContent {
        drawContent()
        if (!Motion.enabled) return@drawWithContent
        val x = size.width * shine
        drawRect(
          brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent),
            start = Offset(x - size.width * 0.35f, 0f),
            end = Offset(x + size.width * 0.35f, size.height),
          ),
          size = size,
        )
      }
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      Icons.Filled.WorkspacePremium,
      contentDescription = "اشتراک و قیمت‌ها",
      tint = GOLD_INK,
      modifier = Modifier.size(15.dp),
    )
    Text(
      "VIP",
      style = MaterialTheme.typography.labelLarge,
      color = GOLD_INK,
      fontWeight = FontWeight.Bold,
    )
  }
}

/** کلیدِ حساب — آبی، با هاله‌ای که نفس می‌کشد */
@Composable
private fun AccountChip(onClick: () -> Unit) {
  val colors = Shop.colors
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
  Box(
    Modifier
      .size(34.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(colors.primary.copy(alpha = 0.16f))
      .border(1.2.dp, colors.primary.copy(alpha = breathe), RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      Icons.Filled.Person,
      contentDescription = "حساب",
      tint = colors.primary,
      modifier = Modifier.size(18.dp),
    )
  }
}

/** کلیدِ هشدارها — نارنجی، تا در ردیفِ خاکستری دیده شود */
@Composable
private fun AlertChip(count: Int, onClick: () -> Unit) {
  Box(contentAlignment = Alignment.TopEnd) {
    Box(
      Modifier
        .size(34.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(ALERT_ORANGE.copy(alpha = 0.18f))
        .border(1.2.dp, ALERT_ORANGE.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.Notifications,
        contentDescription = "هشدارها",
        tint = ALERT_ORANGE,
        modifier = Modifier.size(18.dp),
      )
    }
    if (count > 0) {
      Box(
        Modifier.size(15.dp).clip(RoundedCornerShape(8.dp)).background(Shop.colors.danger),
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

/** دکمهٔ کوچکِ گردِ سربرگ، با شمارندهٔ اختیاری */
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
        .background(Shop.colors.surface)
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = description, tint = Shop.colors.muted, modifier = Modifier.size(17.dp))
    }
    if (badge > 0) {
      Box(
        Modifier.size(15.dp).clip(RoundedCornerShape(8.dp)).background(Shop.colors.danger),
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
