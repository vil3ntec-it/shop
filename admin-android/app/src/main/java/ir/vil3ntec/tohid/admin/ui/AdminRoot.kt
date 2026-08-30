package ir.vil3ntec.tohid.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.screens.*
import kotlinx.coroutines.launch

/**
 *  ریشهٔ برنامه: تا وارد نشده‌اید، هیچ‌چیزِ دیگری نیست.
 *
 *  چهار بخش، چون بیشتر از این روی گوشی جا نمی‌شود و لازم هم نیست:
 *  داشبورد برای دیدن، کاربران و دکان‌ها برای کار کردن، سابقه برای
 *  پیگیری.
 */
@Composable
fun AdminRoot() {
  val context = LocalContext.current
  val session = remember { Session(context) }
  val scope = rememberCoroutineScope()

  var signedIn by remember { mutableStateOf(session.signedIn) }
  var tab by rememberSaveable { mutableStateOf(0) }

  if (!signedIn) {
    LoginScreen(session) { signedIn = true }
    return
  }

  BackHandler(enabled = tab != 0) { tab = 0 }

  val c = Admin.colors
  Scaffold(
    containerColor = c.bg,
    bottomBar = {
      NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
        Nav(tab == 0, "خانه", Icons.Filled.Dashboard) { tab = 0 }
        Nav(tab == 1, "کاربران", Icons.Filled.People) { tab = 1 }
        Nav(tab == 2, "دکان‌ها", Icons.Filled.Storefront) { tab = 2 }
        Nav(tab == 3, "سابقه", Icons.Filled.History) { tab = 3 }
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
      when (tab) {
        0 -> DashboardScreen(session)
        1 -> UsersScreen(session)
        2 -> ShopsScreen(session)
        else -> AuditScreen(session)
      }
    }
  }
}

@Composable
private fun RowScope.Nav(
  selected: Boolean,
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
) {
  val c = Admin.colors
  NavigationBarItem(
    selected = selected,
    onClick = onClick,
    icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
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
