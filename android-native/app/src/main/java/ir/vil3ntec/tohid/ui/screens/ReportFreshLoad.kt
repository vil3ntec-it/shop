package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import ir.vil3ntec.tohid.core.cost.CostMethod
import ir.vil3ntec.tohid.core.cost.CostPolicy
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  PERF-SCREEN — حسابِ سنگین بیرون از رشتهٔ صفحه: [compute] روی `Dispatchers.Default` با همان کلیدها، و تا آماده شدن
 *  `null` (بارِ نخست) یا همان نتیجهٔ پیشین (کلیدِ تازه، مثلاً دفتر پس از یک فروش) — صفحه هرگز منتظرِ حساب نمی‌ماند.
 */
@Composable
internal fun <T : Any> rememberOffMain(vararg keys: Any?, compute: () -> T?): T? =
  produceState<T?>(null, *keys) { value = withContext(Dispatchers.Default) { compute() } }.value

/**
 *  هرچه «گزارشِ تازه» ِ گوشی (`PhoneFreshReports`) از دفتر می‌سازد، همان فراخوانی‌ها با همان ورودی‌ها، یک‌جا بیرون از رشتهٔ
 *  صفحه. پیش‌تر همهٔ این‌ها در ترکیبِ صفحه بود: با ۵۰ هزار فاکتور چند ثانیه صفحه یخ می‌زد (`docs/PERFORMANCE-fa.md`).
 */
internal class PhoneFresh(
  /** کارت‌های بالا (`HomeKpis`) از همان دفترِ هسته، نه یک بار JSON ِ دیگر */
  val home: ReportBoard.Home,
  val debts: ReportFreshModel.View,
  val todayTables: List<ReportTables.Table>,
  val dayEnd: DayEndModel.Built?,
  val stockCost: ReportCore.StockCost?,
  val promo: PromoFresh.View?,
) {
  companion object {
    fun build(d: ShopData, today: String, clock: ReportClock, money: Boolean, cost: CostMethod, now: Long, offsetMinutes: Int): PhoneFresh {
      val ledger = ReportBridge.ledgerOf(d)
      return PhoneFresh(
        home = ReportBoard.build(ledger, today, ReportBridge.deviceClock(), ReportFreshModel.costingOf(cost), CostPolicy.approved(cost), money = money),
        debts = ReportFreshModel.view(ledger, "debts", "all", "", today, clock),
        todayTables = if (money) ReportFreshModel.view(ledger, "today", "today", "", today, clock, ReportFreshModel.costingOf(cost)).tables else emptyList(),
        //  RPT-04: پایانِ امروز فقط برای کسی که پول را می‌بیند
        dayEnd = if (money) DayEndModel.build(d, today, clock, now, offsetMinutes) else null,
        //  PRD-15 / PRM-16: فقط برای کسی که پول را می‌بیند
        stockCost = if (money) ReportCore.stockCost(ledger) else null,
        promo = if (money && ir.vil3ntec.tohid.data.PromoEffectBook.has(d)) PromoFresh.view(d, ledger, "month", today, clock, cost) else null,
      )
    }
  }
}
