package af.tohid.shop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import af.tohid.shop.TohidApp
import af.tohid.shop.ui.components.AppHeader
import af.tohid.shop.ui.components.BottomNav
import af.tohid.shop.ui.components.Fab
import af.tohid.shop.ui.components.NavEntry
import af.tohid.shop.ui.screens.*
import af.tohid.shop.ui.theme.T

/** صفحه‌های نوار پایین — همان هفت‌تای نسخه‌ی وب و به همان ترتیب. */
private val bottomEntries = listOf(
    NavEntry("dashboard", "داشبورد", Icons.Outlined.Dashboard),
    NavEntry("sale", "فروش", Icons.Outlined.ShoppingCart),
    NavEntry("debtors", "قرض‌داران", Icons.Outlined.People),
    NavEntry("warehouse", "انبار", Icons.Outlined.Inventory),
    NavEntry("expenses", "مصارف", Icons.Outlined.ReceiptLong),
    NavEntry("products", "محصولات", Icons.Outlined.Inventory2),
    NavEntry("more", "بیشتر", Icons.Outlined.MoreHoriz),
)

private val titles = mapOf(
    "dashboard" to "داشبورد",
    "sale" to "فروش",
    "debtors" to "قرض‌داران",
    "warehouse" to "انبار",
    "expenses" to "مصارف",
    "products" to "محصولات",
    "more" to "بیشتر",
    "sales" to "تاریخچه فروش",
    "purchasing" to "خریداری",
    "reports" to "گزارش‌ها",
    "audit" to "دفتر رویدادها",
    "settings" to "تنظیمات",
    "shop" to "دکان و همگام‌سازی",
)

/** صفحه‌هایی که دکمه‌ی شناور «افزودن» دارند. */
private val fabRoutes = setOf("debtors", "warehouse", "expenses", "products")

@Composable
fun TohidRoot() {
    val app = TohidApp.instance
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTopLevel = bottomEntries.any { it.route == route }

    // درخواست «افزودن» از دکمه‌ی شناور به صفحه‌ی فعال می‌رسد
    var addTick by remember { mutableIntStateOf(0) }

    val initial = app.session.userLabel().trim().firstOrNull()?.toString()
        ?: app.session.shopName().trim().firstOrNull()?.toString() ?: "ک"

    Scaffold(
        containerColor = T.surface,
        topBar = {
            AppHeader(
                title = titles[route] ?: "توحید",
                userInitial = initial,
                onNotifications = { nav.navigate("more") },
                onSettings = { nav.navigate("settings") },
                onAccount = { nav.navigate("shop") },
                onBack = if (isTopLevel) null else ({ nav.popBackStack(); Unit }),
            )
        },
        bottomBar = {
            BottomNav(bottomEntries, route) { target ->
                nav.navigate(target) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        floatingActionButton = {
            if (route in fabRoutes) {
                Box(Modifier.padding(bottom = 4.dp)) {
                    Fab(onClick = { addTick++ }, icon = Icons.Outlined.Add)
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(T.surface),
            contentAlignment = Alignment.TopStart,
        ) {
            NavHost(navController = nav, startDestination = "dashboard") {
                composable("dashboard") { DashboardScreen(onOpen = { nav.navigate(it) }) }
                composable("sale") { SaleScreen() }
                composable("debtors") {
                    DebtorsScreen(addTick, onOpenDebtor = { nav.navigate("debtor/$it") })
                }
                composable("warehouse") { WarehouseScreen(addTick) }
                composable("expenses") { ExpensesScreen(addTick) }
                composable("products") { ProductsScreen(addTick) }
                composable("more") { MoreScreen(onOpen = { nav.navigate(it) }) }

                composable("debtor/{id}") { entry ->
                    DebtorAccountScreen(entry.arguments?.getString("id").orEmpty())
                }
                composable("sales") { SalesHistoryScreen() }
                composable("purchasing") { PurchasingScreen() }
                composable("reports") { ReportsScreen() }
                composable("audit") { AuditLogScreen() }
                composable("settings") { SettingsScreen() }
                composable("shop") { ShopScreen() }
            }
        }
    }
}
