package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.core.i18n.StrSupplierBills
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.CustomerCrm
import ir.vil3ntec.tohid.data.DebtReminderSend
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.DeskCustomersScreen
import ir.vil3ntec.tohid.ui.screens.DeskPurchasingScreen
import ir.vil3ntec.tohid.ui.screens.VipGate
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerModel.PendingDelete
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import kotlinx.coroutines.launch

/** مسیرهای گروهِ پول (G4) */
object MoneyPaths {
  const val DEBTORS = "debtors"
  fun debtors(sort: String? = null, q: String? = null) = RoutePaths.withQuery(DEBTORS, "sort" to sort, "q" to q)
  fun debtor(id: String) = "debtors/" + RoutePaths.encode(id)
  const val NEW_DEBTOR = "debtors/new"
  fun editDebtor(id: String) = debtor(id) + "/edit"
  fun deleteDebtor(id: String) = debtor(id) + "/delete"
  const val RECEIVE = "debtors/receive"
  const val MERGE = "debtors/merge"
  fun mergeConfirm(keep: String, drop: String) = MERGE + "/" + RoutePaths.encode(keep) + "/" + RoutePaths.encode(drop)
  fun tx(id: String, kind: String? = null) = RoutePaths.withQuery(debtor(id) + "/tx", "kind" to kind)
  fun txDetail(id: String, txId: String) = debtor(id) + "/tx/" + RoutePaths.encode(txId)
  const val RECEIPTS = "receipts"
  fun receipts(tab: String? = null, year: Int? = null, month: Int? = null) =
    RoutePaths.withQuery(RECEIPTS, "tab" to tab, "y" to year?.toString(), "m" to month?.toString())
  const val PURCHASING = "purchasing"
  const val NEW_SUPPLIER = "purchasing/suppliers/new"
  fun supplier(id: String) = "purchasing/suppliers/" + RoutePaths.encode(id)
  fun editSupplier(id: String) = supplier(id) + "/edit"
  fun paySupplier(id: String) = supplier(id) + "/pay"
  fun returnSupplier(id: String) = supplier(id) + "/return"
  fun newPurchase(supplier: String? = null) = RoutePaths.withQuery("purchasing/new", "supplier" to supplier)
  /** PUR-14: فاکتورهای تأمین‌کننده در گوشی — درش فقط با پرچمِ `shop.purchase_orders` و برای مدیر/صاحب (`rememberSupplierBillsDoor`) */
  const val SUPPLIER_BILLS = "purchasing/bills"
  fun supplierBill(orderId: String) = SUPPLIER_BILLS + "/" + RoutePaths.encode(orderId)
  fun newSupplierBill(orderId: String) = supplierBill(orderId) + "/new"
  /**
   *  PUR-02/03/04/14: سفارش‌های خرید در گوشی (`PurchaseOrderPages.kt`) — درش فقط با پرچمِ `shop.purchase_orders`
   *  (`rememberPurchaseOrdersDoor`)؛ کارِ هر نقش همان سرور (`PoForms.actions`)
   */
  const val PURCHASE_ORDERS = "purchasing/orders"
  fun purchaseOrders(status: String? = null) = RoutePaths.withQuery(PURCHASE_ORDERS, "status" to status)
  const val NEW_PURCHASE_ORDER = "purchasing/orders/new"
  fun newPurchaseOrder(supplier: String? = null) = RoutePaths.withQuery(NEW_PURCHASE_ORDER, "supplier" to supplier)
  const val PURCHASE_ORDER_SUGGEST = "purchasing/orders/suggest"
  fun purchaseOrder(id: String) = PURCHASE_ORDERS + "/" + RoutePaths.encode(id)
  fun editPurchaseOrder(id: String) = purchaseOrder(id) + "/edit"
  fun receivePurchaseOrder(id: String) = purchaseOrder(id) + "/receive"
  fun matchPurchaseOrder(id: String) = purchaseOrder(id) + "/match"
  const val EXPENSES = "expenses"
  fun expenses(month: String? = null, category: String? = null) = RoutePaths.withQuery(EXPENSES, "month" to month, "category" to category)
  const val NEW_EXPENSE = "expenses/new"
  fun expense(id: String) = "expenses/" + RoutePaths.encode(id)
  fun expenseMonth(month: String? = null) = RoutePaths.withQuery("expenses/month", "month" to month)
  fun breakdown(month: String, category: String? = null) = RoutePaths.withQuery("expenses/breakdown", "month" to month, "category" to category)
}

/**
 *  گروهِ پول (G4، `money.md`): قرض‌داران، رسیدها، خرید و تأمین‌کننده، مصارف — همه روی کیتِ نسخهٔ ۳
 *  (`ui/v3/money`). این فایل فقط سیم‌کشی است: مسیر ⇐ صفحه، کارِ صفحه ⇐ مسیرِ دیگر یا موتورِ پول
 *  (`DebtorEngine`، `LedgerEngine` از راهِ `MoneyWriter`/`MoneyUndo`). پیوند به گروه‌های دیگر فقط
 *  با مسیر (`SalesPaths`، `ProductsPaths`). دسترسی همان امروز: کلِ قرض‌داران پشتِ `VipGate`.
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
    /* ------------------------------ قرض‌داران ------------------------------ */
    V3Route(MoneyPaths.DEBTORS, RouteKind.TAB, { Str.debtors }, chrome = Chrome.OWN) {
      //  CRM-10: «ادغامِ تکراری‌ها» فقط با «گزارش‌های مشتری» روشن (پیش‌فرض خاموش)
      val crm = ShopPolicy.crmReports(LocalContext.current)
      val duplicates = remember(data, crm, MoneyUndo.hiddenDebtors) { DebtorMergeModel.groups(data, crm, MoneyUndo.hiddenDebtors).size }
      VipGate(Str.debtors) {
        //  CRM-07: سفارشِ باز/لغوشدهٔ پروندهٔ مشتری ⇐ همان صفحهٔ سفارش
        if (desk) DeskCustomersScreen(store, data, snackbar, onOpenOrder = { nav.open(OnlinePaths.order(it)) })
        else DebtorsListScreen(
          d = data,
          sortArg = args.query("sort"),
          queryArg = args.query("q"),
          duplicates = duplicates,
          actions = DebtorsListActions(
            onBack = backOrNull,
            onOpen = { nav.open(MoneyPaths.debtor(it)) },
            onEdit = { nav.open(MoneyPaths.editDebtor(it)) },
            onDelete = { nav.open(MoneyPaths.deleteDebtor(it)) },
            onAdd = { nav.open(MoneyPaths.NEW_DEBTOR) },
            onMerge = if (crm) ({ nav.open(MoneyPaths.MERGE) }) else null,
          ),
        )
      }
    },
    V3Route("debtors/{id}", RouteKind.PAGE, { Str.debtor }, parent = MoneyPaths.DEBTORS, chrome = Chrome.OWN) {
      //  پیوندِ کهنه به مشتریِ ادغام‌شده (#330) همان مشتریِ ماندگار را باز می‌کند
      val id = CustomerCrm.resolve(data, args["id"].orEmpty())
      val remindScope = rememberCoroutineScope()
      val viewed = ir.vil3ntec.tohid.ui.screens.rememberCustomerAccessNote(store) // CRM-15
      val chainOn = rememberDocChainOn() // S89: «حذف» با کلیدِ `shop.doc_chain` سندِ معکوس است
      VipGate(Str.debtors) {
        DebtorLedgerScreen(
          id = id,
          d = data,
          wide = desk || wide,
          actions = DebtorLedgerActions(
            onBack = backOrNull,
            onCompose = { kind -> nav.open(MoneyPaths.tx(id, if (kind == DebtorEngine.Kind.RECEIVE) "receive" else "give")) },
            onEdit = { nav.open(MoneyPaths.editDebtor(id)) },
            onDelete = { nav.open(MoneyPaths.deleteDebtor(id)) },
            onOpenTx = { nav.open(MoneyPaths.txDetail(id, it)) },
            onDeleteTx = { MoneyUndo.removeTxLater(store, snackbar, it, chainOn) },
            onOpenSale = { nav.open(SalesPaths.invoice(it)) },
            onOlderSales = { nav.open(SalesPaths.history(q = data.debtors.find { d -> d.id == id }?.name)) },
            //  CRM-06: یک ردِ `debt_reminder` در روز برای هر راه (زمان‌بندیِ «یادآوریِ امروز»)
            onReminded = { channel -> remindScope.launch { DebtReminderSend.sent(store, id, channel) } },
            //  CRM-15: «چه کسی پرونده را دید» در دفترِ همین دستگاه (همگام می‌شود)
            onViewed = { kind -> viewed(id, kind) },
          ),
          extras = { DebtorFileExtras(data, store, id, onOpenOrder = { nav.open(OnlinePaths.order(it)) }) { toasts.show(it) } },
        )
      }
    },
    //  `debtors/new` نتیجه دارد: شناسهٔ قرض‌دارِ تازه به صدازننده (صندوق ⇐ `openForResult`)
    V3Route(MoneyPaths.NEW_DEBTOR, RouteKind.SHEET, { StrMoney.newDebtor }, parent = MoneyPaths.DEBTORS, chrome = Chrome.OWN) { DebtorFormRoute(null) },
    V3Route("debtors/{id}/edit", RouteKind.SHEET, { StrMoney.editDebtor }, parent = "debtors/{id}", chrome = Chrome.OWN) { DebtorFormRoute(args["id"].orEmpty()) },
    V3Route("debtors/{id}/tx", RouteKind.SHEET, { StrMoney.newTx }, parent = "debtors/{id}", chrome = Chrome.OWN) { TxFormRoute() },
    V3Route(MoneyPaths.RECEIVE, RouteKind.SHEET, { StrMoney.receiveTitlePick }, parent = MoneyPaths.DEBTORS, chrome = Chrome.OWN) {
      VipGate(Str.debtors) {
        ReceivePickerSheet(
          d = data,
          //  گامِ ۲ روی پشته؛ ثبت شد ⇒ هر دو گام بسته؛ «برگشت» ⇒ همین فهرست
          onPick = { id -> nav.openForResult(MoneyPaths.tx(id, "receive")) { r -> if (r != null) back() } },
          onClose = { back() },
        )
      }
    },
    V3Route(MoneyPaths.MERGE, RouteKind.SHEET, { StrMoney.mergeTitle }, parent = MoneyPaths.DEBTORS, chrome = Chrome.OWN) { MergeRoute() },
    V3Route("debtors/merge/{keep}/{drop}", RouteKind.SHEET, { StrMoney.mergeTitle }, parent = MoneyPaths.MERGE, chrome = Chrome.OWN) { MergeConfirmRoute() },
    V3Route("debtors/{id}/tx/{txId}", RouteKind.SHEET, { StrMoney.details }, parent = "debtors/{id}", chrome = Chrome.OWN) { DebtorTxRoute() },
    V3Route("debtors/{id}/delete", RouteKind.SHEET, { StrMoney.deleteDebtorTitle }, parent = "debtors/{id}", chrome = Chrome.OWN) { DeleteDebtorRoute() },

    /* ------------------------------ رسیدها ------------------------------ */
    V3Route(MoneyPaths.RECEIPTS, RouteKind.PAGE, { Str.receipts }, chrome = Chrome.OWN) {
      ReceiptsScreen(
        d = data,
        tabArg = args.query("tab"),
        yearArg = args.int("y"),
        monthArg = args.int("m"),
        onBack = backOrNull,
        onOpenDebtor = { nav.open(MoneyPaths.debtor(it)) },
        onOpenExpense = { nav.open(MoneyPaths.expense(it)) },
      )
    },

    /* --------------------------- خرید و تأمین‌کننده --------------------------- */
    V3Route(MoneyPaths.PURCHASING, RouteKind.TAB, { Str.purchasing }, chrome = Chrome.OWN) {
      if (desk) DeskPurchasingScreen(store, data, snackbar)
      else {
        //  PUR-14: فاکتورِ تأمین‌کننده و «باطل» روی گوشی هم (پیش‌تر فقط پنجرهٔ «سفارش‌های خرید» ِ کامپیوتر)
        val billsDoor = rememberSupplierBillsDoor()
        //  PUR-02/03/04: «سفارش‌های خرید» روی گوشی هم (پیش‌تر فقط پنجرهٔ کامپیوتر) — همان پرچم
        val ordersDoor = rememberPurchaseOrdersDoor()
        SuppliersScreen(
          d = data,
          actions = SuppliersActions(
            onBack = backOrNull,
            onOpen = { nav.open(MoneyPaths.supplier(it)) },
            onPay = { nav.open(MoneyPaths.paySupplier(it)) },
            onEdit = { nav.open(MoneyPaths.editSupplier(it)) },
            onDelete = { id -> MoneyWriter.ledger(store, toasts, null, data, LedgerEngine.deleteSupplier(data, id), StrMoney.deleted) },
            onNewSupplier = { nav.open(MoneyPaths.NEW_SUPPLIER) },
            onNewPurchase = { nav.open(MoneyPaths.newPurchase()) },
            onSupplierBills = if (billsDoor) ({ nav.open(MoneyPaths.SUPPLIER_BILLS) }) else null,
            onPurchaseOrders = if (ordersDoor) ({ nav.open(MoneyPaths.PURCHASE_ORDERS) }) else null,
          ),
        )
      }
    },
    V3Route(MoneyPaths.SUPPLIER_BILLS, RouteKind.PAGE, { StrSupplierBills.title }, parent = MoneyPaths.PURCHASING) { SupplierBillsRoute() },
    V3Route("purchasing/bills/{id}", RouteKind.PAGE, { StrSupplierBills.order }, parent = MoneyPaths.SUPPLIER_BILLS) { SupplierBillRoute(args["id"].orEmpty()) },
    V3Route("purchasing/bills/{id}/new", RouteKind.PAGE, { StrSupplierBills.newBill }, parent = "purchasing/bills/{id}") { SupplierBillNewRoute(args["id"].orEmpty()) },
    V3Route(MoneyPaths.PURCHASE_ORDERS, RouteKind.PAGE, { StrPurchasePages.purchaseOrders }, parent = MoneyPaths.PURCHASING) { PurchaseOrdersRoute() },
    V3Route(MoneyPaths.NEW_PURCHASE_ORDER, RouteKind.PAGE, { StrPurchasePages.newPoTitle }, parent = MoneyPaths.PURCHASE_ORDERS) { PurchaseOrderNewRoute() },
    V3Route(MoneyPaths.PURCHASE_ORDER_SUGGEST, RouteKind.PAGE, { StrPurchasePages.reorderSuggest }, parent = MoneyPaths.PURCHASE_ORDERS) { PurchaseOrderSuggestRoute() },
    V3Route("purchasing/orders/{id}", RouteKind.PAGE, { StrSupplierBills.order }, parent = MoneyPaths.PURCHASE_ORDERS) { PurchaseOrderRoute(args["id"].orEmpty()) },
    V3Route("purchasing/orders/{id}/edit", RouteKind.PAGE, { StrMoney.edit }, parent = "purchasing/orders/{id}") { PurchaseOrderEditRoute(args["id"].orEmpty()) },
    V3Route("purchasing/orders/{id}/receive", RouteKind.PAGE, { StrPurchasePages.receiveGoods }, parent = "purchasing/orders/{id}") { PurchaseOrderReceiveRoute(args["id"].orEmpty()) },
    V3Route("purchasing/orders/{id}/match", RouteKind.PAGE, { StrPurchaseOrders.match }, parent = "purchasing/orders/{id}") { PurchaseOrderMatchRoute(args["id"].orEmpty()) },
    V3Route(MoneyPaths.NEW_SUPPLIER, RouteKind.SHEET, { StrMoney.newSupplier }, parent = MoneyPaths.PURCHASING, chrome = Chrome.OWN) { SupplierFormRoute(null) },
    V3Route("purchasing/suppliers/{id}", RouteKind.PAGE, { Str.purchasing }, parent = MoneyPaths.PURCHASING, chrome = Chrome.OWN) {
      val id = args["id"].orEmpty()
      SupplierLedgerScreen(
        id = id,
        d = data,
        actions = SupplierLedgerActions(
          onBack = backOrNull,
          onPay = { nav.open(MoneyPaths.paySupplier(id)) },
          onNewPurchase = { nav.open(MoneyPaths.newPurchase(id)) },
          onEdit = { nav.open(MoneyPaths.editSupplier(id)) },
          onDelete = { if (MoneyWriter.ledger(store, toasts, null, data, LedgerEngine.deleteSupplier(data, id), StrMoney.deleted) != null) back() },
          onReturn = { nav.open(MoneyPaths.returnSupplier(id)) },
        ),
      )
    },
    V3Route("purchasing/suppliers/{id}/edit", RouteKind.SHEET, { StrMoney.editSupplier }, parent = "purchasing/suppliers/{id}", chrome = Chrome.OWN) {
      SupplierFormRoute(args["id"].orEmpty())
    },
    V3Route("purchasing/suppliers/{id}/pay", RouteKind.SHEET, { StrMoney.pay }, parent = "purchasing/suppliers/{id}", chrome = Chrome.OWN) { PayRoute() },
    V3Route("purchasing/suppliers/{id}/return", RouteKind.SHEET, { StrReach.supplierReturn }, parent = "purchasing/suppliers/{id}", chrome = Chrome.OWN) { SupplierReturnRoute() },
    V3Route("purchasing/new", RouteKind.PAGE, { StrMoney.newPurchase }, parent = MoneyPaths.PURCHASING, chrome = Chrome.OWN) {
      val context = LocalContext.current
      //  PUR-05: یک کلید برای همین فرم — دو لمسِ «ثبت» پیش از بسته شدن یک خرید است (`PurchaseBook.record(key)`)
      val saveKey = remember { moneyId() }
      PurchaseFormScreen(
        d = data,
        supplierArg = args.query("supplier"),
        onBack = { back() },
        onNewProduct = { nav.open(ProductsPaths.new()) },
        onSave = { draft ->
          MoneyWriter.ledger(store, toasts, context, data, LedgerEngine.addPurchase(data, draft, todayIso(), System.currentTimeMillis(), ir.vil3ntec.tohid.data.PurchaseBook.keyedIds(saveKey)), StrMoney.purchaseSaved) != null
        },
        //  PUR-05: همان فاکتورِ چندردیفهٔ کامپیوتر (`DeskPurchasing`)، با هزینهٔ جانبی (PUR-06) و سررسید (PUR-07)
        onSaveMany = { supplierId, lines, paid, date, notes, extras ->
          MoneyWriter.purchase(
            store, toasts, context,
            data,
            ir.vil3ntec.tohid.data.PurchaseBook.record(data, supplierId, lines, paid, date, notes, todayIso(), System.currentTimeMillis(), ::moneyId, landedTotal = extras.landedTotal, dueDate = extras.dueDate, key = saveKey),
            StrMoney.purchaseSaved,
          ) != null
        },
        //  همان شرطِ کامپیوتر (`DeskPurchasing.stockOn`)
        extrasOn = remember(data.auditLog, ir.vil3ntec.tohid.data.StockLedger.serverFlag) { ir.vil3ntec.tohid.data.StockLedger.isOn(data) },
        notify = { tone, text -> toasts.show(text, tone) },
      )
    },

    /* ------------------------------ مصارف ------------------------------ */
    V3Route(MoneyPaths.EXPENSES, RouteKind.TAB, { Str.expenses }, chrome = Chrome.OWN) {
      ExpensesScreen(
        d = data,
        monthArg = args.query("month"),
        categoryArg = args.query("category"),
        actions = ExpensesActions(
          onBack = backOrNull,
          onNew = { nav.open(MoneyPaths.NEW_EXPENSE) },
          onOpen = { nav.open(MoneyPaths.expense(it)) },
          onDelete = { id -> MoneyWriter.ledger(store, toasts, null, data, LedgerEngine.deleteExpense(data, id), StrMoney.deleted) },
          onPickMonth = { current, then -> nav.openForResult(MoneyPaths.expenseMonth(current)) { m -> m?.let(then) } },
          onBreakdown = { month, category, then -> nav.openForResult(MoneyPaths.breakdown(month, category)) { c -> if (c != null) then(c) } },
        ),
      )
    },
    V3Route(MoneyPaths.NEW_EXPENSE, RouteKind.SHEET, { StrMoney.newExpense }, parent = MoneyPaths.EXPENSES, chrome = Chrome.OWN) { ExpenseFormRoute(null) },
    V3Route("expenses/month", RouteKind.SHEET, { StrMoney.pickMonth }, parent = MoneyPaths.EXPENSES, chrome = Chrome.OWN) {
      val picked = remember { arrayOfNulls<String>(1) }
      ExpenseMonthSheet(
        d = data,
        current = args.query("month"),
        onPick = { picked[0] = it },
        onDismiss = { picked[0]?.let { nav.finish(it) } ?: back() },
      )
    },
    V3Route("expenses/breakdown", RouteKind.SHEET, { StrMoney.byCategory }, parent = MoneyPaths.EXPENSES, chrome = Chrome.OWN) {
      val picked = remember { arrayOfNulls<String>(1) }
      ExpenseBreakdownSheet(
        d = data,
        month = ExpensesModel.parseMonth(args.query("month")) ?: todayIso().take(7),
        selected = args.query("category")?.takeIf { it.isNotBlank() },
        onPick = { picked[0] = it },
        onDismiss = { picked[0]?.let { nav.finish(it) } ?: back() },
      )
    },
    V3Route("expenses/{id}", RouteKind.SHEET, { StrMoney.editExpense }, parent = MoneyPaths.EXPENSES, chrome = Chrome.OWN) { ExpenseFormRoute(args["id"].orEmpty()) },
  ),
)

/** شناسهٔ تازه — همان `Ulid` ِ همهٔ دفتر */
internal fun moneyId(): String = ir.vil3ntec.tohid.ui.screens.newId()

/** `debtors/{id}/tx/{txId}`: جزئیاتِ یک ردیف، رسید، سررسید و حذف (با برگرداندن) */
@Composable
private fun RouteScope.DebtorTxRoute() {
  val id = args["id"].orEmpty()
  val txId = args["txId"].orEmpty()
  val tx = data.transactions.find { it.id == txId && it.debtorId == id }
  if (tx == null || txId in MoneyUndo.hiddenTx) {
    LandOn(MoneyPaths.debtor(id))
    return
  }
  val context = LocalContext.current
  val book = remember(data, id) { ir.vil3ntec.tohid.data.DebtBook.book(data, id, todayIso()) }
  val receipt = remember(data, id, txId) { receiptOf(context, data, id, txId) }
  //  S89: کلیدِ `shop.doc_chain` روشن ⇒ دکمه «سندِ معکوس» است و ردیفی که معکوس نمی‌شود دکمه ندارد
  val chainOn = rememberDocChainOn()
  val removable = remember(data, txId, chainOn) { !chainOn || ir.vil3ntec.tohid.data.DebtReverse.refusalOf(data, txId) == null }
  val removeLabel = if (!removable) null else if (chainOn) StrMoney.reverse else StrMoney.delete
  VipGate(Str.debtors) {
    DebtorTxSheet(
      tx = tx,
      book = book,
      receipt = receipt,
      desk = desk,
      removeLabel = removeLabel,
      actions = DebtorTxSheetActions(
        onClose = { back() },
        onDelete = { MoneyUndo.removeTxLater(store, snackbar, txId, chainOn) },
        onSaveDue = { due -> MoneyWriter.debtor(store, toasts, context, data, DebtorEngine.setDueDate(data, txId, due), StrMoney.dueSaved) },
      ),
    )
  }
}

/** `debtors/{id}/delete`: پرسشِ حذف با هشدارِ موتور (`DebtorEngine.deleteWarning`)، بعد حذف با برگرداندن */
@Composable
private fun RouteScope.DeleteDebtorRoute() {
  val id = args["id"].orEmpty()
  val debtor = data.debtors.find { it.id == id }
  if (debtor == null) {
    LandOn(MoneyPaths.DEBTORS)
    return
  }
  //  S89: با کلیدِ `shop.doc_chain` قرض‌دارِ دارای ردیف پاک نمی‌شود (پاک شدنش یعنی حذفِ اسنادِ ثبت‌شده) ⇒ فقط دلیل و «بستن»
  val rows = data.transactions.count { it.debtorId == id }
  val deletable = ir.vil3ntec.tohid.core.debt.DebtReversal.canDeleteDebtor(rememberDocChainOn(), rows)
  VipGate(Str.debtors) {
    if (!deletable) {
      V3ConfirmSheet(
        title = StrMoney.deleteDebtorTitle,
        message = ir.vil3ntec.tohid.core.i18n.StrLedger.debtorHasDocs(debtor.name, rows),
        confirm = ir.vil3ntec.tohid.core.i18n.StrKit.close,
        icon = Icons.Outlined.DeleteOutline,
        danger = false,
        onConfirm = { back() },
        onDismiss = { back() },
      )
      return@VipGate
    }
    V3ConfirmSheet(
      title = StrMoney.deleteDebtorTitle,
      message = DebtorEngine.deleteWarning(data, id),
      confirm = StrMoney.delete,
      icon = Icons.Outlined.DeleteOutline,
      onConfirm = {
        //  برگه پیش از این بسته شد؛ حسابِ همان قرض‌دار (اگر زیرش بود) هم بسته می‌شود
        nav.backFrom(MoneyPaths.debtor(id))
        MoneyUndo.deleteLater(store, snackbar, PendingDelete.Debtor(id), StrMoney.debtorDeleted(debtor.name))
      },
      onDismiss = { back() },
    )
  }
}

/**
 *  `debtors/merge` (CRM-10) — پشتِ «گزارش‌های مشتری» (پیش‌فرض خاموش): خاموش ⇒ همان فهرست.
 *  روی همگام‌سازیِ v3 برگه باز است ولی فقط دلیل را می‌گوید (`ShopStore.mergeBlockedReason`).
 */
@Composable
private fun RouteScope.MergeRoute() {
  if (!ShopPolicy.crmReports(LocalContext.current)) { LandOn(MoneyPaths.DEBTORS); return }
  val hidden = MoneyUndo.hiddenDebtors
  val groups = remember(data, hidden) { DebtorMergeModel.groups(data, true, hidden) }
  VipGate(Str.debtors) {
    DebtorMergeSheet(
      groups = groups,
      blocked = store.mergeBlockedReason(),
      onMerge = { keep, drop -> nav.open(MoneyPaths.mergeConfirm(keep, drop)) },
      onClose = { back() },
    )
  }
}

/** `debtors/merge/{keep}/{drop}` — تأییدِ ادغام با پیش‌نمایشِ `CustomerMerge.preview`؛ ادغام فقط از `ShopStore.mergeCustomers` */
@Composable
private fun RouteScope.MergeConfirmRoute() {
  val context = LocalContext.current
  if (!ShopPolicy.crmReports(context)) { LandOn(MoneyPaths.DEBTORS); return }
  val keepId = args["keep"].orEmpty()
  val dropId = args["drop"].orEmpty()
  val p = remember(data, keepId, dropId) { DebtorMergeModel.preview(data, keepId, dropId) }
  val limitRules = ir.vil3ntec.tohid.ui.components.rememberCreditLimitRules() // CRM-03: ادغامی که سقفِ ماندگار را عوض می‌کند
  if (p == null) { LandOn(MoneyPaths.MERGE); return }
  VipGate(Str.debtors) {
    V3ConfirmSheet(
      title = StrMoney.mergeConfirmTitle(p.drop.name, p.keep.name),
      message = StrMoney.mergeConfirmBody(p.transactions, p.sales, p.drop.name, p.keep.name, MoneyText.afn(p.balanceAfter)),
      confirm = StrMoney.mergeAction,
      icon = Icons.Outlined.PeopleOutline,
      danger = false,
      onConfirm = {
        back()
        MoneyWriter.merge(store, toasts, context, keepId, dropId, limitRules) { r -> StrMoney.merged(r.drop.name, r.keep.name) }
      },
      onDismiss = { back() },
    )
  }
}

/* ------------------------------ برگه‌های فرم ------------------------------ */

/** `debtors/new` (نتیجه: شناسهٔ تازه) و `debtors/{id}/edit` */
@Composable
private fun RouteScope.DebtorFormRoute(id: String?) {
  val context = LocalContext.current
  val debtor = id?.let { k -> data.debtors.find { it.id == k } }
  if (id != null && debtor == null) { LandOn(MoneyPaths.DEBTORS); return }
  val saved = remember { arrayOfNulls<String>(1) }
  //  CRM-03: «تغییرِ سقفِ اعتبار» (`customers.credit_limit`) پیش از ذخیره — فقط با پرچمِ `shop.credit_limit_perm` ِ دکان
  val limitRules = ir.vil3ntec.tohid.ui.components.rememberCreditLimitRules()
  VipGate(Str.debtors) {
    DebtorFormSheet(
      debtor = debtor,
      onClose = { back() },
      onSave = { f ->
        saved[0] = MoneyWriter.debtor(store, toasts, context, data, DebtorFormsModel.save(data, id, f, System.currentTimeMillis(), limitRules, ::moneyId), if (id == null) StrMoney.saved else StrMoney.edited)
        saved[0] != null
      },
      onSaved = { if (id == null) nav.finish(saved[0]) else back() },
      limitRules = limitRules,
    )
  }
}

/** `debtors/{id}/tx?kind=give|receive` — بیش از سقف گامِ دومِ همین برگه است؛ ثبت ⇒ نتیجهٔ «ok» (گامِ ۲ ِ «دریافت») */
@Composable
private fun RouteScope.TxFormRoute() {
  val context = LocalContext.current
  val id = args["id"].orEmpty()
  val debtor = data.debtors.find { it.id == id }
  if (debtor == null) { LandOn(MoneyPaths.DEBTORS); return }
  VipGate(Str.debtors) {
    TxFormSheet(
      debtor = debtor,
      balance = ir.vil3ntec.tohid.data.ShopStore.debt(data, id),
      initialKind = if (args.query("kind") == "receive") DebtorEngine.Kind.RECEIVE else DebtorEngine.Kind.GIVE,
      overLimitOf = { f -> DebtorFormsModel.overLimit(data, id, f) },
      onClose = { back() },
      onSubmit = { f, over ->
        MoneyWriter.debtor(store, toasts, context, data, DebtorFormsModel.submit(data, id, f, todayIso(), System.currentTimeMillis(), ::moneyId, over), StrMoney.saved) != null
      },
      onDone = { nav.finish("ok") },
    )
  }
}

/** `purchasing/suppliers/new` و `purchasing/suppliers/{id}/edit` */
@Composable
private fun RouteScope.SupplierFormRoute(id: String?) {
  val context = LocalContext.current
  val supplier = id?.let { k -> data.suppliers.find { it.id == k } }
  if (id != null && supplier == null) { LandOn(MoneyPaths.PURCHASING); return }
  SupplierFormSheet(
    supplier = supplier,
    onClose = { back() },
    onSave = { draft ->
      val result = if (id == null) LedgerEngine.addSupplier(data, draft, System.currentTimeMillis(), ::moneyId) else LedgerEngine.editSupplier(data, id, draft)
      MoneyWriter.ledger(store, toasts, context, data, result, if (id == null) StrMoney.saved else StrMoney.edited) != null
    },
    onSaved = { back() },
  )
}

/** `purchasing/suppliers/{id}/pay` */
@Composable
private fun RouteScope.PayRoute() {
  val context = LocalContext.current
  val id = args["id"].orEmpty()
  val supplier = data.suppliers.find { it.id == id }
  if (supplier == null) { LandOn(MoneyPaths.PURCHASING); return }
  //  PUR-07: یک کلید برای همین برگه — دو لمسِ «ثبتِ پرداخت» پیش از بسته شدن یک پرداخت است
  val payKey = remember(id) { moneyId() }
  SupplierPaySheet(
    supplier = supplier,
    debt = ir.vil3ntec.tohid.data.ShopStore.supplierDebt(data, id),
    onClose = { back() },
    onSave = { f ->
      val result = LedgerEngine.paySupplier(data, id, LedgerFormsModel.payAmount(f), f.date, f.notes, todayIso(), System.currentTimeMillis(), ::moneyId, key = payKey)
      MoneyWriter.ledger(store, toasts, context, data, result, StrMoney.saved) != null
    },
    onSaved = { back() },
  )
}

/** `expenses/new` (از «مصرف تازه» و «پرداخت» ِ کارتِ کشیدنی) و `expenses/{id}` (ویرایش و حذف) */
@Composable
private fun RouteScope.ExpenseFormRoute(id: String?) {
  val context = LocalContext.current
  val expense = id?.let { k -> data.expenses.find { it.id == k } }
  if (id != null && expense == null) { LandOn(MoneyPaths.EXPENSES); return }
  ExpenseFormSheet(
    expense = expense,
    categories = data.expenseCategories,
    onClose = { back() },
    onSave = { draft ->
      val result = if (id == null) LedgerEngine.addExpense(data, draft, todayIso(), System.currentTimeMillis(), ::moneyId)
      else LedgerEngine.editExpense(data, id, draft, todayIso())
      MoneyWriter.ledger(store, toasts, context, data, result, if (id == null) StrMoney.saved else StrMoney.edited) != null
    },
    onSaved = { back() },
    onDelete = if (id == null) null else ({
      MoneyWriter.ledger(store, toasts, context, data, LedgerEngine.deleteExpense(data, id), StrMoney.deleted)
      back()
    }),
  )
}
