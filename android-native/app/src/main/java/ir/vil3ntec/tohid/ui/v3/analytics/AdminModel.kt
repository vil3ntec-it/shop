package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths

/** کارت‌های «مرکزِ مدیریت» — هر کدام درِ صفحهٔ دقیقِ خودش */
enum class AdminKpi(val money: Boolean) {
  TODAY_SALES(false), MONTH_SALES(false), MONTH_PROFIT(true), STOCK_VALUE(true),
  RECEIVABLE(false), PAYABLE(true), CASH(false), STOCK_ALERT(false),
}

/** عددهای مرکزِ مدیریت — همان موتورهای صفحه‌های خودشان، پس عددِ این‌جا با آن‌جا یکی است */
data class AdminNumbers(
  val todayNet: Double,
  val todayCount: Int,
  val monthNet: Double,
  val monthCount: Int,
  val monthProfit: Double,
  val stockValue: Double,
  val stockProducts: Int,
  val owed: Double,
  val owingPeople: Int,
  val weOwe: Double,
  val suppliers: Int,
  /** null ⇒ صندوق بسته */
  val cashExpected: Double?,
  val cashOpenedAt: Long?,
  val out: Int,
  val low: Int,
)

/** کارمندانِ دکان از سرور */
sealed interface AdminMembers {
  data object Loading : AdminMembers
  data object Offline : AdminMembers
  data object SignedOut : AdminMembers
  data class Count(val n: Int) : AdminMembers
}

/** حالتِ خالصِ مرکزِ مدیریت، با آزمون (`AdminModelTest`) */
object AdminModel {
  fun numbers(d: ShopData, today: String, now: Long): AdminNumbers {
    val monthFrom = today.take(7) + "-01"
    val todayR = ReportEngine.sales(d, today, today)
    val monthR = ReportEngine.sales(d, monthFrom, today)
    val stock = WarehouseEngine.summary(d)
    val shift = CashShift.current(d)
    return AdminNumbers(
      todayNet = todayR.net,
      todayCount = todayR.count,
      monthNet = monthR.net,
      monthCount = monthR.count,
      monthProfit = monthR.netProfit,
      stockValue = stock.value,
      stockProducts = stock.products,
      owed = d.debtors.sumOf { ShopStore.debt(d, it.id).coerceAtLeast(0.0) },
      owingPeople = d.debtors.count { ShopStore.debt(d, it.id) > 0 },
      weOwe = d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id).coerceAtLeast(0.0) },
      suppliers = d.suppliers.size,
      cashExpected = shift?.let { CashShift.totalsOf(d, it, now).expected },
      cashOpenedAt = shift?.open?.createdAt,
      out = stock.out,
      low = stock.low,
    )
  }

  /** کارت‌های دیدنی: کارتِ پولی فقط با `canSeeMoney` (همان سدِ امروز) */
  fun kpis(canSeeMoney: Boolean): List<AdminKpi> = AdminKpi.entries.filter { canSeeMoney || !it.money }

  fun route(k: AdminKpi): String = when (k) {
    AdminKpi.TODAY_SALES -> SalesPaths.HISTORY
    AdminKpi.MONTH_SALES, AdminKpi.MONTH_PROFIT -> AnalyticsPaths.reports(ReportTab.PROFIT.id, "month")
    AdminKpi.STOCK_VALUE -> StockPaths.LIST
    AdminKpi.RECEIVABLE -> MoneyPaths.debtors(sort = "most")
    AdminKpi.PAYABLE -> MoneyPaths.PURCHASING
    AdminKpi.CASH -> SalesPaths.DRAWER
    AdminKpi.STOCK_ALERT -> StockPaths.list(filter = "out")
  }
}
