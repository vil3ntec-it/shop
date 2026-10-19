package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartShot
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartUi
import ir.vil3ntec.tohid.ui.screens.QuickSaleScreen
import ir.vil3ntec.tohid.ui.components.common.NavTab
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.DashboardScreen
import ir.vil3ntec.tohid.ui.screens.DebtorsScreen
import ir.vil3ntec.tohid.ui.screens.ProfileScreen
import ir.vil3ntec.tohid.ui.screens.SettingsScreen
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
          PhonePreviewContent(screen, store, dark)
        }
      }
    }
  }
}

@Composable
private fun PhonePreviewContent(screen: String, store: ShopStore, dark: Boolean) {
  val data by store.data.collectAsState()
  val snackbar = remember { SnackbarHostState() }
  Column(Modifier.fillMaxSize().background(Shop.colors.bg)) {
    when (screen) {
      //  تنظیماتِ گوشی: گروه‌ها و کارت‌های گرد (`SettingsGroup`)
      "settings" -> Box(Modifier.weight(1f).fillMaxWidth()) {
        SettingsScreen(store, data, snackbar, if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, {}, {})
      }
      //  «حساب من»: کارتِ عکس، نقش، آمار و گروه‌ها
      "profile" -> Box(Modifier.weight(1f).fillMaxWidth()) {
        ProfileScreen(store = store, snackbar = snackbar, onBack = {}, onSignIn = {}, onSubscription = {})
      }
      //  سبدِ گوشی: نوارِ تیره (cart) و برگهٔ باز (cart-open) روی «انتخاب محصول» با چهار کالای دفترِ نمایشی
      "cart", "cart-open" -> {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        val context = LocalContext.current
        val cartStore = remember { CartStore(context) }
        LaunchedEffect(data.products.size, screen) {
          val picks = data.products.filter { it.active }.take(4)
          if (picks.isNotEmpty()) cartStore.set(picks.mapIndexed { i, p -> SalesEngine.CartLine(p.id, if (i == 0) 2.0 else 1.0) })
          PhoneCartShot.open.value = screen == "cart-open"
        }
        QuickSaleScreen(store, cartStore, data, snackbar) {}
        }
        //  مثلِ `AppRoot`: نوارِ پایین زیرِ نوارِ سبد، و تا برگه باز است کنار می‌رود
        if (!PhoneCartUi.sheetOpen.value) PhoneNavBar(tabs = PREVIEW_TABS, current = "sale", onPick = {})
      }
      //  قرض‌دارانِ گوشی (r9): سه نفرِ r9، هشت نفر با نام و مبلغِ بلند، و خالی — با دفترِ ساختگیِ خودش
      "debtors-r9", "debtors-many", "debtors-empty" -> Box(Modifier.weight(1f).fillMaxWidth()) {
        val demo = remember(screen) { debtorsDemo(screen, System.currentTimeMillis()) }
        DebtorsScreen(store, demo, snackbar)
      }
      else -> {
        //  «nav» و هر نامِ ناشناس: خانه با نوارِ پایینِ قرصی
        Box(Modifier.weight(1f).fillMaxWidth()) { DashboardScreen(data) {} }
        PhoneNavBar(tabs = PREVIEW_TABS, current = "dashboard", onPick = {})
      }
    }
  }
}

/**
 *  دفترِ ساختگیِ عکس‌های قرض‌داران — فقط قرض‌دار و تراکنش، بی‌ربط به دفترِ نمایشی.
 *
 *  • r9: همان سه نفرِ r9 — «جلیل» ۱٬۰۰۰ بی شماره، «haroon» ۶۰۰ با شماره (هر دو
 *    شش روز)، و «haroon» ِ صاف با رسیدِ ۱۰٬۰۰۰.
 *  • many: هشت نفر با نامِ بلند، مبلغِ درشت، قرضِ کهنه، موجودی‌دار و صاف.
 *  • empty: هیچ.
 */
private fun debtorsDemo(screen: String, now: Long): ShopData {
  val day = 86_400_000L
  var seq = 0
  fun tx(debtorId: String, type: String, amount: Double, daysAgo: Long) =
    DebtTransaction(id = "demo-t${seq++}", debtorId = debtorId, type = type, amount = amount, createdAt = now - daysAgo * day)
  return when (screen) {
    "debtors-r9" -> ShopData(
      debtors = listOf(
        Debtor(id = "r9-j", name = "جلیل"),
        Debtor(id = "r9-h6", name = "haroon", phone = "0792236008"),
        Debtor(id = "r9-h0", name = "haroon"),
      ),
      transactions = listOf(
        tx("r9-j", "give", 1000.0, 6),
        tx("r9-h6", "give", 600.0, 6),
        tx("r9-h0", "give", 10000.0, 9),
        tx("r9-h0", "receive", 10000.0, 2),
      ),
    )
    "debtors-many" -> ShopData(
      debtors = listOf(
        Debtor(id = "m1", name = "محمد عبدالرحیم صدیقی"),
        Debtor(id = "m2", name = "عبدالرحمن نورزاد", phone = "+93700123456"),
        Debtor(id = "m3", name = "Mohammad Haroon Rahimi"),
        Debtor(id = "m4", name = "شرکت ترانسپورتی برادران حیدری", phone = "0799111222"),
        Debtor(id = "m5", name = "زرغونه"),
        Debtor(id = "m6", name = "گل‌احمد"),
        Debtor(id = "m7", name = "نجیب‌الله", phone = "0788000111"),
        Debtor(id = "m8", name = "فریدون"),
      ),
      transactions = listOf(
        tx("m1", "give", 12_345_678.0, 12),
        tx("m2", "give", 4_500_000.0, 3),
        tx("m3", "give", 987_654.0, 64),
        tx("m4", "give", 250_000.0, 45),
        tx("m5", "give", 8_000.0, 20),
        tx("m5", "receive", 8_000.0, 5),
        tx("m6", "receive", 1_500.0, 2),
        tx("m7", "give", 75.0, 0),
        tx("m8", "give", 3_000_000.0, 400),
        tx("m8", "receive", 120_000.0, 30),
      ),
    )
    else -> ShopData()
  }
}
