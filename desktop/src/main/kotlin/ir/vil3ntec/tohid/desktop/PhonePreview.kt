package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.screens.ProfileScreen
import ir.vil3ntec.tohid.ui.screens.QuickSaleScreen
import ir.vil3ntec.tohid.ui.screens.SettingsScreen
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme

/**
 *  «پیش»ِ پیش‌نمایشِ گوشی — همان پنجرهٔ ۳۹۰×۸۴۴ِ phone-ui روی main، برای عکسِ
 *  «پیش/پس». شاخهٔ موقتِ عکس است، نه برای ادغام.
 */
object PhonePreview {
  const val TITLE = "پیش‌نمایشِ گوشی"
  val screen = mutableStateOf<String?>(null)
}

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
          val data by store.data.collectAsState()
          val snackbar = remember { SnackbarHostState() }
          Column(Modifier.fillMaxSize().background(Shop.colors.bg)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
              when (screen) {
                "settings" -> SettingsScreen(store, data, snackbar, if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, {}, {})
                "profile" -> ProfileScreen(store = store, snackbar = snackbar, onBack = {}, onSignIn = {}, onSubscription = {})
                else -> {
                  val ctx = LocalContext.current
                  val cartStore = remember { CartStore(ctx) }
                  LaunchedEffect(data.products.size) {
                    val picks = data.products.filter { it.active }.take(4)
                    if (picks.isNotEmpty()) cartStore.set(picks.mapIndexed { i, p -> SalesEngine.CartLine(p.id, if (i == 0) 2.0 else 1.0) })
                  }
                  QuickSaleScreen(store, cartStore, data, snackbar) {}
                }
              }
            }
          }
        }
      }
    }
  }
}
