package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ShoppingBag
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.common.NavTab
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.screens.DashboardScreen
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme

/**
 *  پیش‌نمایشِ گوشی برای عکس‌های خودکار — فقط با فرمانِ `phone:<صفحه>`ِ `DevShot`.
 *
 *  صفحه‌های گوشی (نوارِ پایینِ قرصی، …) روی کامپیوتر هرگز دیده نمی‌شوند، چون
 *  کامپیوتر منوی کناری و چیدمانِ پهن دارد. این پنجرهٔ بی‌قابِ ۳۹۰×۸۴۴ همان
 *  کامپوننت‌های گوشی را با `LocalConfiguration`ِ گوشی (`Screen.PHONE`) و دفترِ
 *  نمایشی می‌کشد تا `desktop-shots.yml` از آن عکس بگیرد. در نسخهٔ دستِ کاربر
 *  هیچ‌کس `screen` را نمی‌گذارد و پنجره ساخته نمی‌شود.
 *
 *  پسوندِ `-dark` همان صفحه را در تمِ شب می‌کشد (`phone:nav-dark`).
 */
object PhonePreview {
  const val TITLE = "پیش‌نمایشِ گوشی"
  val screen = mutableStateOf<String?>(null)
}

private val PREVIEW_TABS = listOf(
  NavTab("dashboard", "خانه", Icons.Filled.GridView),
  NavTab("sale", "فروش", Icons.Filled.PointOfSale),
  NavTab("debtors", "قرض‌داران", Icons.Filled.Groups),
  NavTab("products", "محصولات", Icons.Filled.ShoppingBag),
  NavTab("more", "بیشتر", Icons.Filled.MoreHoriz),
)

@Composable
fun PhonePreviewWindow(store: ShopStore, context: android.content.Context) {
  val requested = PhonePreview.screen.value ?: return
  val dark = requested.endsWith("-dark")
  val screen = requested.removeSuffix("-dark")
  DialogWindow(
    onCloseRequest = { PhonePreview.screen.value = null },
    title = PhonePreview.TITLE,
    undecorated = true,
    resizable = false,
    state = rememberDialogState(position = WindowPosition(0.dp, 0.dp), size = DpSize(390.dp, 844.dp)),
  ) {
    CompositionLocalProvider(
      LocalContext provides context,
      LocalView provides remember { android.view.View(context) },
      LocalConfiguration provides Configuration(390, 844),
    ) {
      TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          PhonePreviewContent(screen, store)
        }
      }
    }
  }
}

@Composable
private fun PhonePreviewContent(screen: String, store: ShopStore) {
  val data by store.data.collectAsState()
  Column(Modifier.fillMaxSize().background(Shop.colors.bg)) {
    when (screen) {
      else -> {
        //  «nav» و هر نامِ ناشناس: خانه با نوارِ پایینِ قرصی
        Box(Modifier.weight(1f).fillMaxWidth()) { DashboardScreen(data) {} }
        PhoneNavBar(tabs = PREVIEW_TABS, current = "dashboard", onPick = {})
      }
    }
  }
}
