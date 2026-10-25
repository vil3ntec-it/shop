package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.v3.money.DebtorFormSheet
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerScreen
import ir.vil3ntec.tohid.ui.v3.money.DebtorTxSheet
import ir.vil3ntec.tohid.ui.v3.money.DebtorTxSheetActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorsListActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorsListScreen
import ir.vil3ntec.tohid.ui.v3.money.ExpenseBreakdownSheet
import ir.vil3ntec.tohid.ui.v3.money.ExpenseFormSheet
import ir.vil3ntec.tohid.ui.v3.money.ExpenseMonthSheet
import ir.vil3ntec.tohid.ui.v3.money.ExpensesActions
import ir.vil3ntec.tohid.ui.v3.money.ExpensesScreen
import ir.vil3ntec.tohid.ui.v3.money.PurchaseFormScreen
import ir.vil3ntec.tohid.ui.v3.money.ReceiptsScreen
import ir.vil3ntec.tohid.ui.v3.money.ReceivePickerSheet
import ir.vil3ntec.tohid.ui.v3.money.SupplierFormSheet
import ir.vil3ntec.tohid.ui.v3.money.SupplierLedgerActions
import ir.vil3ntec.tohid.ui.v3.money.SupplierLedgerScreen
import ir.vil3ntec.tohid.ui.v3.money.SuppliersActions
import ir.vil3ntec.tohid.ui.v3.money.SupplierPaySheet
import ir.vil3ntec.tohid.ui.v3.money.SuppliersScreen
import ir.vil3ntec.tohid.ui.v3.money.TxFormSheet

/**
 *  عکس‌های گروهِ G4 پول (`money.md`) — روی دفترِ نمایشیِ `MoneyDemo` (نه دادهٔ دکانِ واقعی)؛
 *  صفحه‌ها مستقیم با همین دفتر کشیده می‌شوند و کارها بی‌اثرند. «route-*» همان مسیر درونِ پوسته
 *  روی دفترِ نمایشیِ CI است (نوارِ پایین، پشتهٔ پدرها).
 */
object MoneyShots : GroupShots {
  override val group = "money"
  override val shots = listOf(
    Shot("supplier-new", content = { SupplierFormSheet(null, onClose = {}, onSave = { false }, onSaved = {}) }),
    Shot("supplier-pay", content = { MoneyDemo.full().let { d -> SupplierPaySheet(d.suppliers.first(), ShopStore.supplierDebt(d, MoneyDemo.SUPPLIER), {}, { false }, {}) } }),
    Shot("purchase-form", content = { PurchaseFormScreen(MoneyDemo.full(), MoneyDemo.SUPPLIER, onBack = {}, onNewProduct = {}, onSave = { false }) }),
    Shot("purchase-form-empty-catalog", content = { PurchaseFormScreen(ShopData(), null, onBack = {}, onNewProduct = {}, onSave = { false }) }),
    Shot("expenses", content = { ExpensesShot(MoneyDemo.full()) }),
    Shot("expenses-category", content = { ExpensesShot(MoneyDemo.full(), category = "برق") }),
    Shot("expenses-empty", content = { ExpensesShot(ShopData()) }),
    Shot("expenses-month", content = { ExpenseMonthSheet(MoneyDemo.full(), todayIso().take(7), onPick = {}, onDismiss = {}) }),
    Shot("expenses-breakdown", content = { ExpenseBreakdownSheet(MoneyDemo.full(), todayIso().take(7), "برق", onPick = {}, onDismiss = {}) }),
    Shot("expense-new", content = { ExpenseFormSheet(null, MoneyDemo.full().expenseCategories, {}, { false }, {}, null) }),
    Shot("expense-edit", content = { MoneyDemo.full().let { d -> ExpenseFormSheet(d.expenses.first(), d.expenseCategories, {}, { false }, {}, onDelete = {}) } }),
    Shot("route-debtors", route = "debtors"),
    Shot("route-expenses", route = "expenses"),
    Shot("route-purchasing", route = "purchasing"),
    Shot("route-receipts", route = "receipts"),
    Shot("debtors", content = { DebtorsShot(MoneyDemo.full()) }),
    Shot("debtors-60", content = { DebtorsShot(MoneyDemo.many()) }),
    Shot("debtors-empty", content = { DebtorsShot(ShopData()) }),
    Shot("debtors-oldest", content = { DebtorsShot(MoneyDemo.full(), sort = "oldest") }),
    Shot("ledger-owing", content = { LedgerShot(MoneyDemo.OWING) }),
    Shot("ledger-drawer", content = { LedgerShot(MoneyDemo.OWING, drawer = true) }),
    Shot("ledger-overlimit", content = { LedgerShot(MoneyDemo.OVER, drawer = true) }),
    Shot("ledger-settled", content = { LedgerShot(MoneyDemo.SETTLED) }),
    Shot("tx-sheet", content = { TxShot("t1") }),
    Shot("tx-sheet-receive", content = { TxShot("t2") }),
    Shot("debtor-new", content = { DebtorFormSheet(null, onClose = {}, onSave = { false }, onSaved = {}) }),
    Shot("debtor-edit", content = { DebtorFormSheet(MoneyDemo.full().debtors.first(), onClose = {}, onSave = { false }, onSaved = {}) }),
    Shot("tx-give", content = { TxFormShot(DebtorEngine.Kind.GIVE) }),
    Shot("tx-receive", content = { TxFormShot(DebtorEngine.Kind.RECEIVE) }),
    Shot("receive-picker", content = { ReceivePickerSheet(MoneyDemo.full(), onPick = {}, onClose = {}) }),
    Shot("receipts-debtors", content = { ReceiptsShot("debtors") }),
    Shot("receipts-expenses", content = { ReceiptsShot("expenses") }),
    Shot("suppliers", content = { SuppliersScreen(MoneyDemo.full(), noSuppliers) }),
    Shot("suppliers-empty", content = { SuppliersScreen(ShopData(), noSuppliers) }),
    Shot("supplier-ledger", content = { SupplierLedgerScreen(MoneyDemo.SUPPLIER, MoneyDemo.full(), noSupplier) }),
  )
}

private val noDebtors = DebtorsListActions(onBack = null, onOpen = {}, onEdit = {}, onDelete = {}, onAdd = {})
private val noLedger = DebtorLedgerActions({}, {}, {}, {}, {}, {}, {}, {})
private val noSuppliers = SuppliersActions(null, {}, {}, {}, {}, {}, {})
private val noSupplier = SupplierLedgerActions({}, {}, {}, {}, {})
private val noExpenses = ExpensesActions(null, {}, {}, {}, { _, _ -> }, { _, _, _ -> })

@Composable
private fun DebtorsShot(d: ShopData, sort: String? = null) = DebtorsListScreen(d, sort, null, noDebtors)

@Composable
private fun LedgerShot(id: String, drawer: Boolean = false) =
  DebtorLedgerScreen(id, MoneyDemo.full(), wide = false, actions = noLedger, drawerOpen = drawer)

@Composable
private fun TxShot(txId: String) {
  val d = MoneyDemo.full()
  val tx = d.transactions.first { it.id == txId }
  val book = DebtBook.book(d, tx.debtorId, todayIso())
  DebtorLedgerScreen(tx.debtorId, d, wide = false, actions = noLedger)
  DebtorTxSheet(tx, book, DebtBook.receipt(d, tx.debtorId, txId, "دکانِ نمایشی", todayIso()), desk = false, actions = DebtorTxSheetActions({}, {}, {}))
}

@Composable
private fun TxFormShot(kind: DebtorEngine.Kind) {
  val d = MoneyDemo.full()
  val debtor = d.debtors.first { it.id == MoneyDemo.OWING }
  DebtorLedgerScreen(debtor.id, d, wide = false, actions = noLedger)
  TxFormSheet(debtor, ShopStore.debt(d, debtor.id), kind, overLimitOf = { null }, onClose = {}, onSubmit = { _, _ -> false }, onDone = {})
}

@Composable
private fun ReceiptsShot(tab: String) =
  ReceiptsScreen(MoneyDemo.full(), tab, null, null, onBack = {}, onOpenDebtor = {}, onOpenExpense = {})

@Composable
private fun ExpensesShot(d: ShopData, category: String? = null) = ExpensesScreen(d, null, category, noExpenses)
