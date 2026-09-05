package ir.vil3ntec.tohid.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.screens.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 *  ریشهٔ برنامه: تا وارد نشده‌اید، هیچ‌چیزِ دیگری نیست.
 *
 *  ── چرا چیدمان عوض شد ──────────────────────────────────────────────
 *  پنج تبِ قبلی برای پنج بخش بس بود؛ حالا نُه بخش هست. اگر همه را به
 *  نوارِ پایین می‌چپاندیم، هیچ‌کدام خوانده نمی‌شد.
 *
 *  پس چهار کارِ **هر روزه** پایین می‌مانند — خانه، کاربران، دکان‌ها،
 *  پشتیبانی — و بقیه زیرِ «بیشتر» می‌روند: نرخ‌ها، بازدیدکننده‌ها،
 *  برنامه‌ها، ایمیل، پیامک و سابقه. کارهایی که هفته‌ای یک بار لازم
 *  می‌شوند، نباید جای کارِ روزانه را بگیرند.
 *
 *  ── نقطهٔ قرمزِ پشتیبانی ────────────────────────────────────────────
 *  عددِ خوانده‌نشده‌ها هر دقیقه از سرور خوانده می‌شود، حتی وقتی تبِ
 *  پشتیبانی باز نیست. وگرنه پیامِ کسی می‌ماند تا وقتی که خودتان یادتان
 *  بیفتد سر بزنید.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRoot() {
  val context = LocalContext.current
  val session = remember { Session(context) }
  val scope = rememberCoroutineScope()

  var signedIn by remember { mutableStateOf(session.signedIn) }
  var tab by rememberSaveable { mutableStateOf(0) }
  var more by rememberSaveable { mutableStateOf<String?>(null) }
  var unread by remember { mutableIntStateOf(0) }

  if (!signedIn) {
    LoginScreen(session) { signedIn = true }
    return
  }

  //  عددِ خوانده‌نشده‌ها، مستقل از اینکه کدام تب باز است
  LaunchedEffect(signedIn) {
    while (signedIn) {
      val token = session.token
      if (token != null) {
        runCatching { AdminApi(session.serverUrl).supportThreads(token) }
          .onSuccess { unread = it.optInt("unread") }
      }
      delay(60_000)
    }
  }

  BackHandler(enabled = more != null || tab != 0) {
    if (more != null) more = null else tab = 0
  }

  val c = Admin.colors
  Scaffold(
    containerColor = c.bg,
    bottomBar = {
      NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
        Nav(tab == 0 && more == null, "خانه", Icons.Filled.Dashboard) { tab = 0; more = null }
        Nav(tab == 1 && more == null, "کاربران", Icons.Filled.People) { tab = 1; more = null }
        Nav(tab == 2 && more == null, "دکان‌ها", Icons.Filled.Storefront) { tab = 2; more = null }
        Nav(
          tab == 3 && more == null, "پشتیبانی", Icons.AutoMirrored.Filled.Chat,
          badge = if (unread > 0) unread else null,
        ) { tab = 3; more = null }
        Nav(tab == 4, "بیشتر", Icons.Filled.MoreHoriz) { tab = 4 }
      }
    },
    topBar = {
      if (tab == 0) {
        TopAppBar(
          title = { Text("پنل مدیریت", style = MaterialTheme.typography.titleMedium) },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.bg,
            titleContentColor = c.text,
          ),
          actions = {
            IconButton(onClick = {
              val token = session.token
              scope.launch {
                if (token != null) runCatching { AdminApi(session.serverUrl).logout(token) }
                session.signOut()
                signedIn = false
              }
            }) {
              Icon(Icons.Filled.Logout, contentDescription = "خروج", tint = c.muted)
            }
          },
        )
      }
    },
  ) { padding ->
    Box(Modifier.padding(padding).fillMaxSize().background(c.bg)) {
      //  صفحه‌ای که از «بیشتر» باز شده، بر تب می‌چربد
      val page = more
      if (tab == 4 && page != null) {
        when (page) {
          "pricing" -> PricingScreen(session)
          "visitors" -> VisitorsScreen(session)
          "apps" -> AppsScreen(session)
          "email" -> EmailScreen(session)
          "sms" -> SmsScreen(session)
          else -> AuditScreen(session)
        }
      } else {
        when (tab) {
          0 -> DashboardScreen(session)
          1 -> UsersScreen(session)
          2 -> ShopsScreen(session)
          3 -> SupportScreen(session) { unread = it }
          else -> MoreScreen { more = it }
        }
      }
    }
  }
}

/**
 *  «بیشتر» — کارهایی که هر روز لازم نمی‌شوند.
 *
 *  عمداً فهرستِ ساده و توضیح‌دار است، نه شبکه‌ای از آیکون: هر کدام از
 *  این‌ها کاری است که ممکن است ماهی یک بار سراغش بروید و آن وقت باید
 *  یادتان بیاید کدام چه می‌کند.
 */
@Composable
private fun MoreScreen(onOpen: (String) -> Unit) {
  val c = Admin.colors
  Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text("بیشتر", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))

    Panel {
      MoreRow(
        Icons.Filled.LocalOffer, "نرخ‌ها و تخفیف",
        "قیمت اشتراک‌ها، تخفیف، و کدهای شش‌رقمیِ اشتراک",
      ) { onOpen("pricing") }
      HorizontalDivider(color = c.border)
      MoreRow(
        Icons.Filled.Visibility, "بازدیدکننده‌ها",
        "هر کس که آمده — حتی بدون حساب — با لوکیشنش",
      ) { onOpen("visitors") }
      HorizontalDivider(color = c.border)
      MoreRow(
        Icons.Filled.Apps, "برنامه‌ها",
        "برنامه‌ها و سایت‌های دیگرتان، و اینکه بالا هستند یا نه",
      ) { onOpen("apps") }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("تنظیمات")
    Panel {
      MoreRow(
        Icons.Filled.Email, "ایمیل و پوش",
        "کدِ ثبت‌نام و کدِ اشتراک از این راه می‌روند",
      ) { onOpen("email") }
      HorizontalDivider(color = c.border)
      MoreRow(Icons.Filled.Sms, "پیامک", "برای ورود با شماره") { onOpen("sms") }
      HorizontalDivider(color = c.border)
      MoreRow(Icons.Filled.History, "سابقه", "چه کسی کِی چه کاری کرد") { onOpen("audit") }
    }
    Spacer(Modifier.height(30.dp))
  }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
  val c = Admin.colors
  ClickRow(
    title = title,
    subtitle = subtitle,
    trailing = { Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(22.dp)) },
    onClick = onClick,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.Nav(
  selected: Boolean,
  label: String,
  icon: ImageVector,
  badge: Int? = null,
  onClick: () -> Unit,
) {
  val c = Admin.colors
  NavigationBarItem(
    selected = selected,
    onClick = onClick,
    icon = {
      if (badge != null) {
        BadgedBox(badge = { Badge(containerColor = c.danger) { Text(badge.fa()) } }) {
          Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
      } else {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
      }
    },
    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    colors = NavigationBarItemDefaults.colors(
      selectedIconColor = c.primary,
      selectedTextColor = c.primary,
      unselectedIconColor = c.muted,
      unselectedTextColor = c.muted,
      indicatorColor = c.primary.copy(alpha = 0.14f),
    ),
  )
}
