package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PeopleOutline
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.DebtorsScreen
import ir.vil3ntec.tohid.ui.screens.DeskCustomersScreen
import ir.vil3ntec.tohid.ui.screens.DeskPurchasingScreen
import ir.vil3ntec.tohid.ui.screens.ExpensesScreen
import ir.vil3ntec.tohid.ui.screens.PurchasingScreen
import ir.vil3ntec.tohid.ui.screens.ReceiptsScreen
import ir.vil3ntec.tohid.ui.screens.VipGate
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** مسیرهای گروهِ پول (G4) */
object MoneyPaths {
  const val DEBTORS = "debtors"
  fun debtors(sort: String? = null, q: String? = null) = RoutePaths.withQuery(DEBTORS, "sort" to sort, "q" to q)
  fun debtor(id: String) = "debtors/" + RoutePaths.encode(id)
  const val NEW_DEBTOR = "debtors/new"
  const val RECEIVE = "debtors/receive"
  fun tx(id: String, kind: String? = null) = RoutePaths.withQuery(debtor(id) + "/tx", "kind" to kind)
  const val RECEIPTS = "receipts"
  const val PURCHASING = "purchasing"
  fun supplier(id: String) = "purchasing/suppliers/" + RoutePaths.encode(id)
  fun newPurchase(supplier: String? = null) = RoutePaths.withQuery("purchasing/new", "supplier" to supplier)
  const val EXPENSES = "expenses"
  const val NEW_EXPENSE = "expenses/new"
}

/**
 *  گروهِ پول (G4، `money.md`): قرض‌داران، رسیدها، خرید و تأمین‌کننده، مصارف.
 *  برگه‌هایی که امروز درونِ صفحه‌ها‌اند پیوندشان کار می‌کند و روی همان صفحه
 *  می‌نشیند تا G4 برگهٔ خودش را بسازد.
 */
val MoneyModule = AppModule(
  id = "money",
  title = { Str.debtors },
  icon = Icons.Outlined.PeopleOutline,
  entries = listOf(
    ModuleEntry("debtors", { Str.debtors }, MoneyPaths.DEBTORS, Icons.Outlined.PeopleOutline, setOf(Place.NAV, Place.MORE, Place.HOME), MoreGroup.SALES, V3Hue.BLUE, order = 20),
    ModuleEntry("receipts", { Str.receipts }, MoneyPaths.RECEIPTS, Icons.AutoMirrored.Outlined.ReceiptLong, setOf(Place.MORE), MoreGroup.SALES, V3Hue.TEAL, order = 40),
    ModuleEntry("purchasing", { Str.purchasing }, MoneyPaths.PURCHASING, Icons.Outlined.LocalShipping, setOf(Place.NAV, Place.MORE, Place.HOME), MoreGroup.STOCK, V3Hue.TEAL, order = 20),
    ModuleEntry("expenses", { Str.expenses }, MoneyPaths.EXPENSES, Icons.Outlined.Payments, setOf(Place.NAV, Place.MORE, Place.HOME), MoreGroup.MONEY, V3Hue.PURPLE, order = 10),
  ),
  routes = listOf(
    V3Route(MoneyPaths.DEBTORS, RouteKind.TAB, { Str.debtors }, chrome = Chrome.OWN) {
      VipGate(Str.debtors) {
        if (desk) DeskCustomersScreen(store, data, snackbar)
        else DebtorsScreen(store, data, snackbar, showHeader = true, onBack = if (nav.stack.canPop) ({ back() }) else null)
      }
    },
    V3Route("debtors/{id}", RouteKind.PAGE, { Str.debtor }, parent = MoneyPaths.DEBTORS) { LandOn(MoneyPaths.DEBTORS) },
    V3Route(MoneyPaths.NEW_DEBTOR, RouteKind.SHEET, { Str.debtor }, parent = MoneyPaths.DEBTORS) { LandOn(MoneyPaths.DEBTORS) },
    V3Route("debtors/{id}/edit", RouteKind.SHEET, { Str.debtor }, parent = "debtors/{id}") { LandOn(MoneyPaths.DEBTORS) },
    V3Route("debtors/{id}/tx", RouteKind.SHEET, { Str.debtor }, parent = "debtors/{id}") { LandOn(MoneyPaths.DEBTORS) },
    V3Route(MoneyPaths.RECEIVE, RouteKind.SHEET, { Str.debtors }, parent = MoneyPaths.DEBTORS) { LandOn(MoneyPaths.DEBTORS) },
    V3Route("debtors/{id}/tx/{txId}", RouteKind.SHEET, { Str.debtor }, parent = "debtors/{id}") { LandOn(MoneyPaths.DEBTORS) },
    V3Route("debtors/{id}/delete", RouteKind.SHEET, { Str.debtor }, parent = "debtors/{id}") { LandOn(MoneyPaths.DEBTORS) },
    V3Route(MoneyPaths.RECEIPTS, RouteKind.PAGE, { Str.receipts }) { ReceiptsScreen(data) },
    V3Route(MoneyPaths.PURCHASING, RouteKind.TAB, { Str.purchasing }) {
      if (desk) DeskPurchasingScreen(store, data, snackbar) else PurchasingScreen(store, data, snackbar)
    },
    V3Route("purchasing/suppliers/new", RouteKind.SHEET, { Str.purchasing }, parent = MoneyPaths.PURCHASING) { LandOn(MoneyPaths.PURCHASING) },
    V3Route("purchasing/suppliers/{id}", RouteKind.PAGE, { Str.purchasing }, parent = MoneyPaths.PURCHASING) { LandOn(MoneyPaths.PURCHASING) },
    V3Route("purchasing/suppliers/{id}/edit", RouteKind.SHEET, { Str.purchasing }, parent = "purchasing/suppliers/{id}") { LandOn(MoneyPaths.PURCHASING) },
    V3Route("purchasing/suppliers/{id}/pay", RouteKind.SHEET, { Str.purchasing }, parent = "purchasing/suppliers/{id}") { LandOn(MoneyPaths.PURCHASING) },
    V3Route("purchasing/new", RouteKind.PAGE, { Str.purchases }, parent = MoneyPaths.PURCHASING) { LandOn(MoneyPaths.PURCHASING) },
    V3Route(MoneyPaths.EXPENSES, RouteKind.TAB, { Str.expenses }) { ExpensesScreen(store, data, snackbar) },
    V3Route(MoneyPaths.NEW_EXPENSE, RouteKind.SHEET, { Str.expenses }, parent = MoneyPaths.EXPENSES) { LandOn(MoneyPaths.EXPENSES) },
    V3Route("expenses/month", RouteKind.SHEET, { Str.expenses }, parent = MoneyPaths.EXPENSES) { LandOn(MoneyPaths.EXPENSES) },
    V3Route("expenses/breakdown", RouteKind.SHEET, { Str.expenses }, parent = MoneyPaths.EXPENSES) { LandOn(MoneyPaths.EXPENSES) },
    V3Route("expenses/{id}", RouteKind.SHEET, { Str.expenses }, parent = MoneyPaths.EXPENSES) { LandOn(MoneyPaths.EXPENSES) },
  ),
)
