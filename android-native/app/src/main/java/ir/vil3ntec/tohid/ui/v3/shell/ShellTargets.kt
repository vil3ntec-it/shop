package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths

/**
 *  هر مقصدی که پوسته (خانه، کارتِ کششی، هشدارها، جست‌وجو) باز می‌کند — یک‌جا و نوع‌دار،
 *  از روی مسیرهای خودِ هر گروه (`<Group>Paths`). «هر کار به کادرِ خودش»: دکمهٔ «دریافت»
 *  فرمِ دریافت را باز می‌کند نه فهرستِ قرض‌داران، و هشدارِ همگام‌سازی صفحهٔ همگام‌سازی را
 *  نه دستگاه‌های جانبی (NAV-MAP، فهرستِ «Direct-landing fixes»). آزمون: `ShellTargetsTest`.
 */
object ShellTargets {
  /* ------------------------------ کارهای سریعِ r7 و کارتِ کششیِ r6 ------------------------------ */
  val NEW_SALE: String = SalesPaths.SALE
  val RECEIVE: String = MoneyPaths.RECEIVE
  val STOCK_ENTRY: String = StockPaths.entry()
  val HISTORY: String = SalesPaths.HISTORY
  val PAY: String = MoneyPaths.NEW_EXPENSE
  val PROFILE: String = AccountPaths.PROFILE

  /** صفحهٔ همگام‌سازی (حال، صف، آخرین خطا) — جای پنلِ نقطهٔ همگام‌سازیِ سربرگِ قدیم */
  val SYNC: String = SettingsPaths.ROOT + "/data/sync"
  val BACKUP: String = SettingsPaths.ROOT + "/data/backup"
  val ABOUT: String = SettingsPaths.ABOUT

  /* ------------------------------------ هشدارها ------------------------------------ */
  val NEW_ORDERS: String = OnlinePaths.orders(tab = "new")
  val SUBSCRIPTION: String = AccountPaths.SUBSCRIPTION
  val PLANS: String = AccountPaths.PLANS
  fun stock(anyOut: Boolean): String = ProductsPaths.list(filter = if (anyOut) "out" else "low")
  val OVERDUE_DEBTS: String = MoneyPaths.debtors(sort = "oldest")
  val MOST_DEBT: String = MoneyPaths.debtors(sort = "most")
  val EXPIRING: String = StockPaths.list(filter = "expiring")
  val PURCHASING: String = MoneyPaths.PURCHASING

  fun product(id: String): String = ProductsPaths.item(id)
  fun newProduct(barcode: String): String = ProductsPaths.new(barcode = barcode)
  fun debtor(id: String): String = MoneyPaths.debtor(id)
  fun supplier(id: String): String = MoneyPaths.supplier(id)
  fun invoice(saleId: String): String = SalesPaths.invoice(saleId)

  /** اسکنِ بارکد برای جست‌وجو (`nav.openForResult`) */
  val SCAN: String = SalesPaths.scan("search")

  /* ----------------------------------- خودِ پوسته ----------------------------------- */
  fun notifications(tab: String? = null): String = ShellPaths.notifications(tab)
  fun search(q: String? = null): String = ShellPaths.search(q)

  /** مسیرِ بی پرسش — برای مقایسه با مسیرِ ثبت‌شده */
  fun base(path: String): String = RoutePaths.normalize(path.substringBefore('?'))
}
