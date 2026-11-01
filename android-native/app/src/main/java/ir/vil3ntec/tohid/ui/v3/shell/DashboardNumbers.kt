package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.isoMillis

/**
 *  عددهای صفحهٔ خانه، یک‌جا و یک‌بار.
 *
 *  بیرونِ `@Composable` نوشته شده تا هم داخلِ `remember` بنشیند و هم
 *  بشود بدونِ گوشی سنجیدش. همهٔ فرمول‌ها همان‌اند که بودند؛ فقط جایشان
 *  عوض شده و از جدول‌های آمادهٔ `ShopStore` استفاده می‌کنند به‌جای
 *  پیمایشِ دوباره.
 */
data class DashboardNumbers(
  val todaySales: List<ir.vil3ntec.tohid.data.Sale>,
  val todayTotal: Double,
  val todayExpense: Double,
  val todayProfit: Double,
  val owing: List<Pair<ir.vil3ntec.tohid.data.Debtor, Double>>,
  val totalDebt: Double,
  val expenseMonth: Double,
  val supplierDebt: Double,
  val lowStock: List<ir.vil3ntec.tohid.data.Product>,
  val outOfStock: List<ir.vil3ntec.tohid.data.Product>,
  /** محموله‌های منقضی و نزدیک به انقضا — شرح سرِ `ExpiryEngine` */
  val expiring: List<ir.vil3ntec.tohid.data.ExpiryEngine.Lot> = emptyList(),
  val warehouse: WarehouseEngine.Summary,
  val monthSalesTotal: Double,
  val bestDay: Double,
  /** فروشِ هفت روزِ گذشته و هفت روزِ پیش از آن — برای جمله‌ی روایتِ داده */
  val weekTotal: Double,
  val prevWeekTotal: Double,
  /** سودِ خالصِ همین ماه */
  val monthNet: Double,
  /** قرض‌هایی که بیش از یک ماه است حرکتی نکرده‌اند */
  val overdue: List<Pair<ir.vil3ntec.tohid.data.Debtor, Double>>,
  val overdueTotal: Double,
  val overdueOldestDays: Long,
) {
  companion object {
    fun of(d: ShopData, today: String): DashboardNumbers {
      val monthPrefix = today.take(7)
      val stock = ShopStore.index(d)

      val todaySales = d.sales.filter { it.date == today && it.status != "cancelled" }

      //  یک گذر روی تراکنش‌ها، نه یک پیمایش به ازای هر قرض‌دار
      val byDebtor = d.transactions.groupBy { it.debtorId }
      val owing = d.debtors
        .map { debtor ->
          var amount = 0.0
          byDebtor[debtor.id]?.forEach { amount += if (it.type == "give") it.amount else -it.amount }
          debtor to amount
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }

      /*
       *  دو هفته‌ی گذشته، برای جمله‌ای که زیرِ عددِ قهرمان می‌نشیند.
       *
       *  محلی حساب می‌شود و از همان فاکتورهای روی گوشی — نه اینترنت،
       *  نه موتورِ تازه.
       */
      val nowMs = isoMillis(today)
      val dayMs = 24L * 60 * 60 * 1000
      var weekTotal = 0.0
      var prevWeekTotal = 0.0
      d.sales.filter { it.status != "cancelled" }.forEach { sale ->
        val age = (nowMs - isoMillis(sale.date)) / dayMs
        when {
          age in 0..6 -> weekTotal += sale.finalTotal
          age in 7..13 -> prevWeekTotal += sale.finalTotal
        }
      }

      /*
       *  «عقب‌افتاده» یعنی چه، وقتی وعده‌ای ثبت نمی‌شود؟
       *
       *  دفترِ قرض تاریخِ وعده ندارد. پس ملاک، سکوت است: قرضی که هنوز
       *  باز است و بیش از سی روز هیچ حرکتی — نه قرضِ تازه، نه پرداخت —
       *  روی آن نبوده. همان چیزی که دکاندار هم با آن حساب می‌کند.
       */
      val lastMove = d.transactions
        .groupBy { it.debtorId }
        .mapValues { (_, list) -> list.maxOf { isoMillis(it.date) } }
      val overdue = owing.filter { (debtor, _) ->
        val seen = lastMove[debtor.id] ?: 0L
        seen > 0L && (nowMs - seen) / dayMs > 30
      }
      val overdueOldest = overdue.maxOfOrNull { (debtor, _) ->
        ((nowMs - (lastMove[debtor.id] ?: nowMs)) / dayMs).coerceAtLeast(0)
      } ?: 0L

      //  فروشِ ماه یک بار گروه‌بندی می‌شود و هم جمعش از آن در می‌آید هم
      //  بهترین روزش — نه دو پیمایشِ جدا
      val monthByDay = d.sales
        .filter { it.status != "cancelled" && it.date.startsWith(monthPrefix) }
        .groupBy { it.date }
        .mapValues { (_, list) -> list.sumOf { it.finalTotal } }

      return DashboardNumbers(
        todaySales = todaySales,
        todayTotal = todaySales.sumOf { it.finalTotal },
        todayExpense = d.expenses.filter { it.date == today }.sumOf { it.amount },
        todayProfit = ReportEngine.sales(d, today, today).netProfit,
        owing = owing,
        totalDebt = owing.sumOf { it.second },
        expenseMonth = d.expenses.filter { it.date.startsWith(monthPrefix) }.sumOf { it.amount },
        supplierDebt = d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id) }.coerceAtLeast(0.0),
        lowStock = d.products.filter { stock.status(it) == "low" },
        outOfStock = d.products.filter { stock.status(it) == "out" },
        expiring = ir.vil3ntec.tohid.data.ExpiryEngine.alerts(d, today),
        warehouse = WarehouseEngine.summary(d),
        monthSalesTotal = monthByDay.values.sum(),
        bestDay = monthByDay.values.maxOrNull() ?: 0.0,
        weekTotal = weekTotal,
        prevWeekTotal = prevWeekTotal,
        monthNet = ReportEngine.sales(d, monthPrefix + "-01", today).netProfit,
        overdue = overdue,
        overdueTotal = overdue.sumOf { it.second },
        overdueOldestDays = overdueOldest,
      )
    }
  }
}
