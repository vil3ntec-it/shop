package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.CustomerCrm
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.common.Selection
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.screens.DebtorListModel
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerModel
import ir.vil3ntec.tohid.ui.v3.money.ExpensesModel
import ir.vil3ntec.tohid.ui.v3.money.ReceiptsModel
import ir.vil3ntec.tohid.ui.v3.money.SuppliersModel
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerModel
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryBook
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryFilter
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryQuery
import ir.vil3ntec.tohid.ui.v3.sales.history.filteredOf
import ir.vil3ntec.tohid.ui.v3.shell.DashboardNumbers
import ir.vil3ntec.tohid.ui.v3.shell.HomeHero
import ir.vil3ntec.tohid.ui.v3.shell.SearchIndex
import ir.vil3ntec.tohid.ui.v3.shell.Transactions
import ir.vil3ntec.tohid.ui.v3.shell.shopAlertExtras
import ir.vil3ntec.tohid.ui.v3.stock.StockFilter
import ir.vil3ntec.tohid.ui.v3.stock.StockListModel
import ir.vil3ntec.tohid.data.SalesPeriods

/**
 *  PERF-SCREEN (الف) — آنچه هر بخش با باز شدن از دفتر می‌سازد، **همان فراخوانی‌ها به همان ترتیب** (نامِ صفحه کنارِ هر
 *  کدام)، بی Compose: هر `remember(d…)` ِ نخستین ترکیب و هر `produceState` ِ پس‌زمینه‌ای که صفحه منتظرش می‌ماند.
 *  «جست‌وجو» = یک حرفِ تازه در کادر (`SearchScreen` ⇐ `SearchIndex.search`)، نه کادرِ خالی.
 *
 *  هر بخش از جدول‌های یک‌خانه‌ایِ `ShopStore` (`index`، `salesIndex`، …) همان‌قدر بهره می‌برد که صفحه: جدولِ گرمِ همان دفتر.
 */
internal object ScreenLogic {

  class Ctx(val d: ShopData, val today: String, val now: Long) {
    val debtorId: String = d.transactions.groupingBy { it.debtorId }.eachCount().maxByOrNull { it.value }?.key ?: d.debtors.first().id
  }

  /**
   *  بخش‌هایی که حسابشان **بیرون از رشتهٔ صفحه** است (`produceState` روی `Dispatchers.Default`): صفحه همان دم باز می‌شود و
   *  محتوا پس از این زمان می‌رسد. سقفِ این‌ها نگهبانِ پس‌رفت است، نه بودجهٔ ۱۰۰ میلی‌ثانیه — هر سه هنوز بالای آن‌اند و در
   *  `docs/PERFORMANCE-fa.md` (کارِ مانده) ثبت شده‌اند. سقف فقط پایین می‌آید.
   */
  val OFF_MAIN_BUDGET_MS: Map<String, Double> = mapOf("stock (گدام)" to 300.0, "sales history" to 300.0, "reports" to 2_500.0)

  /** نام (همان نامِ [ScreenSpeed.routes]) ⇒ کارِ نخستین بازِ آن بخش */
  val SECTIONS: List<Pair<String, (Ctx) -> Any?>> = listOf(
    //  HomeView.rememberHomeView + HomeToday + rememberShopAlerts + rememberHeroFaces
    "home" to { c: Ctx ->
      val d = c.d
      DashboardNumbers.of(d, c.today)
      CashShift.current(d)?.let { CashShift.totalsOf(d, it, c.now) } ?: CashShift.totals(d, c.now - 3_600_000L, c.now, 0.0)
      Transactions.today(d, c.today, purchases = true)
      shopAlertExtras(d, false, "")
      HomeHero.sales(d.sales, HomeHero.Range.TODAY, c.today, c.now - 12 * HomeHero.HOUR_MS, c.now, List(7) { "$it" })
    },
    //  SaleRoute + SaleScreen
    "sale" to { c: Ctx ->
      val d = c.d
      ShopStore.barcodeIndex(d)
      val index = CatalogModel.index(d, false)
      val inCart = CartModel.inCart(emptyList())
      CatalogModel.items(d, index, "", null, inCart)
      CatalogModel.bestsellers(d, index, inCart)
      CartSummary.rows(emptyList(), d.products)
    },
    //  QuickSaleRoute + QuickSaleScreen
    "quick sale" to { c: Ctx ->
      val d = c.d
      ShopStore.barcodeIndex(d)
      val index = CatalogModel.index(d, false)
      CatalogModel.items(d, index, "", null, CartModel.inCart(emptyList()))
      CartSummary.of(emptyList(), d.products, 0.0)
    },
    //  ProductsListScreen
    "products" to { c: Ctx ->
      val d = c.d
      val stock = ShopStore.index(d)
      val stockOf: (String) -> Double = { stock.stock(it) }
      val rows = ProductsListModel.rows(d.products, stockOf, ProductsListModel.Query())
      ProductsListModel.stats(d.products, stockOf)
      ProductsListModel.counts(d.products, stockOf)
      ProductsListModel.empty(d.products.size, rows.size)
      Selection.prune(emptySet(), d.products.mapTo(HashSet()) { it.id })
    },
    //  StockListScreen (produceState ⇐ overview) + StockRows
    "stock (گدام)" to { c: Ctx ->
      val d = c.d
      val o = StockListModel.overview(d, c.today, false)
      StockListModel.usedCategories(d)
      StockListModel.visible(StockListModel.atPlace(o, null), StockFilter.ALL, "", null)
    },
    //  DebtorsListScreen
    "debtors" to { c: Ctx ->
      val d = c.d
      val tag = DebtorListModel.keptTag(d, null)
      DebtorListModel.tagChips(d)
      DebtorListModel.rows(d, "", DebtorListModel.Sort.parse(null), c.now, tag)
      DebtorListModel.summary(d)
    },
    //  DebtorLedgerScreen
    "debtor file" to { c: Ctx ->
      val d = c.d
      val account = DebtorEngine.account(d, c.debtorId)
      DebtBook.book(d, c.debtorId, c.today)
      DebtorLedgerModel.rows(account?.transactions.orEmpty(), emptySet())
    },
    //  HistoryHostAndroid (produceState ⇐ HistoryBook.build، DrawerModel.line، reviewCount ⇐ PayStatusFlow.review) + HistoryScreen
    "sales history" to { c: Ctx ->
      val d = c.d
      val book = HistoryBook.build(d, ReportBridge.deviceClock(c.now), c.now, false)
      DrawerModel.line(d, c.now, false)
      ir.vil3ntec.tohid.data.PayStatusFlow.review(d).size // reviewCount (صاحب)
      val q = HistoryQuery(HistoryFilter.ALL, "")
      val unit = SalesPeriods.Unit.DAY
      filteredOf(book.current(unit), q)
      book.older(unit).map { it to filteredOf(it, q) }
    },
    //  ReceiptsScreen
    "receipts" to { c: Ctx ->
      val d = c.d
      val all = ReceiptsModel.rows(d, ReceiptsModel.Tab.DEBTORS)
      ReceiptsModel.years(all)
      CustomerCrm.active(d)
    },
    //  ReportsScreen ⇐ «گزارشِ تازه» (پیش‌فرض روشن، `ReportFresh.on`) ⇐ PhoneFreshReports: کارت‌ها، بدهی، امروز، پایانِ روز،
    //  بهای میانگین، اثرِ تخفیف (`PhoneFresh.build`) و «ساعت و روز» (`HeatmapModel`) — پس از PERF-SCREEN بیرون از رشتهٔ صفحه
    "reports" to { c: Ctx ->
      val d = c.d
      ir.vil3ntec.tohid.ui.screens.PhoneFresh.build(d, c.today, ReportBridge.deviceClock(c.now), true, ir.vil3ntec.tohid.core.cost.CostPolicy.DEFAULT, c.now, ReportBridge.offsetMinutes())
      ir.vil3ntec.tohid.ui.screens.HeatmapModel.build(
        d, ir.vil3ntec.tohid.core.report.ReportBoard.range(ir.vil3ntec.tohid.ui.screens.HeatmapModel.PHONE_SPANS.first(), c.today), ReportBridge.offsetMinutes(),
        ir.vil3ntec.tohid.core.calendar.ShopDay.dayStartMinutes(ir.vil3ntec.tohid.ShopDayChoice.hours), ir.vil3ntec.tohid.ShopDayChoice.weekStart,
      )
    },
    //  ExpensesScreen
    "expenses" to { c: Ctx ->
      val d = c.d
      val month = c.today.take(7)
      ExpensesModel.shown(d, month, null)
      ExpensesModel.byCategory(d, month)
      ExpensesModel.shown(d, month, null).size
      ExpensesModel.total(d, month)
    },
    //  SuppliersScreen (تبِ «خرید» ِ گوشی)
    "purchasing" to { c: Ctx ->
      val d = c.d
      SuppliersModel.rows(d)
      SuppliersModel.owedTotal(d)
    },
    //  SearchScreen: یک حرفِ تازه (کادرِ پُر) ⇐ SearchIndex.search
    "search" to { c: Ctx -> SearchIndex.search(c.d, "برن") },
  )
}
