package af.tohid.shop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import af.tohid.shop.ui.screens.*

/** صفحه‌های اصلی که در نوار پایین می‌آیند. */
enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "داشبورد", Icons.Filled.Dashboard),
    Sale("sale", "فروش", Icons.Filled.PointOfSale),
    Products("products", "محصولات", Icons.Filled.Inventory2),
    Debtors("debtors", "قرض‌داران", Icons.Filled.People),
    More("more", "بیشتر", Icons.Filled.MoreHoriz),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TohidRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevel.entries.forEach { item ->
                    val selected = current?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = TopLevel.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevel.Dashboard.route) { DashboardScreen() }
            composable(TopLevel.Sale.route) { SaleScreen() }
            composable(TopLevel.Products.route) { ProductsScreen() }
            composable(TopLevel.Debtors.route) { DebtorsScreen() }
            composable(TopLevel.More.route) { MoreScreen(onOpen = { nav.navigate(it) }) }
            composable("shop") { ShopScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
