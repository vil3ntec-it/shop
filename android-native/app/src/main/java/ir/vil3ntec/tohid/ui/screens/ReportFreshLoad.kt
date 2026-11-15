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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** یک حسابِ سنگین در هر لحظه: هر کدام کلِ دفتر را در حافظه بازمی‌سازد؛ چند تا با هم (رفت‌وبرگشتِ تند، فروش پشتِ فروش) حافظهٔ گوشی را پر می‌کرد */
private val heavy = Mutex()

/**
 *  PERF-SCREEN — حسابِ سنگین بیرون از رشتهٔ صفحه: [compute] روی `Dispatchers.Default` با همان کلیدها، و تا آماده شدن
 *  `null` (بارِ نخست) یا همان نتیجهٔ پیشین (کلیدِ تازه، مثلاً دفتر پس از یک فروش) — صفحه هرگز منتظرِ حساب نمی‌ماند.
 *  یکی‌یکی ([heavy])؛ حسابی که دیگر خواسته نیست (صفحه رفت، کلید عوض شد) سرِ گامِ بعدی (`step`) می‌ایستد.
 */
@Composable
internal fun <T : Any> rememberOffMain(vararg keys: Any?, compute: (step: () -> Unit) -> T?): T? =
  produceState<T?>(null, *keys) {
    value = withContext(Dispatchers.Default) {
      heavy.withLock {
        val ctx = currentCoroutineContext()
        ctx.ensureActive()
        compute { ctx.ensureActive() }
      }
    }
  }.value

/**
 *  PERF-DESK — همان [rememberOffMain]، ولی نتیجه فقط وقتی نشان داده می‌شود که برای همین [match] ساخته شده باشد (زبانه، بازه،
 *  تفکیک…): نتیجهٔ زبانهٔ پیشین هرگز زیرِ زبانهٔ تازه دیده نمی‌شود (تا آماده شدن `null`). عوض شدنِ [keys] ِ دیگر (دفترِ تازه
 *  پس از یک فروش) همان نتیجهٔ پیشینِ همین [match] را تا آماده شدنِ تازه نگه می‌دارد، مثلِ [rememberOffMain].
 */
@Composable
internal fun <T : Any> rememberOffMainFor(match: Any?, vararg keys: Any?, compute: (step: () -> Unit) -> T?): T? =
  rememberOffMain(match, *keys) { step -> compute(step)?.let { match to it } }?.takeIf { it.first == match }?.second

/**
 *  PERF-DESK — هرچه «گزارشِ تازه» ِ کامپیوتر (`DeskFreshReports`) از درختِ گزارشِ دفتر می‌سازد: نما و دو زبانهٔ
 *  دفتری (تخفیف، بهای میانگین) — یک‌جا بیرون از رشتهٔ صفحه، همان فراخوانی‌ها با همان ورودی‌ها. پیش‌تر هر کدام در ترکیبِ
 *  صفحه بود و باز کردنِ «گزارش‌ها» با ۵۰ هزار فاکتور پنجره را چند ثانیه می‌ایستاند (`docs/PERFORMANCE-fa.md`).
 */
internal class DeskFresh(
  /** درختی که [view] از آن ساخته شد — ریزِ هر ردیف (`drill`) از همین، نه از درختِ تازه‌تری که هنوز نما ندارد */
  val ledger: ir.vil3ntec.tohid.core.report.ReportLedger,
  val view: ReportFreshModel.View,
  val promo: PromoFresh.View?,
  val stockCost: ReportCore.StockCost?,
)

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
    /** [step] میانِ گام‌ها: حسابی که دیگر خواسته نیست همان‌جا می‌ایستد (`rememberOffMain`) */
    fun build(d: ShopData, today: String, clock: ReportClock, money: Boolean, cost: CostMethod, now: Long, offsetMinutes: Int, step: () -> Unit = {}): PhoneFresh {
      val ledger = ReportBridge.ledgerOf(d)
      step()
      val home = ReportBoard.build(ledger, today, ReportBridge.deviceClock(), ReportFreshModel.costingOf(cost), CostPolicy.approved(cost), money = money)
      step()
      val debts = ReportFreshModel.view(ledger, "debts", "all", "", today, clock)
      step()
      val todayTables = if (money) ReportFreshModel.view(ledger, "today", "today", "", today, clock, ReportFreshModel.costingOf(cost)).tables else emptyList()
      step()
      //  RPT-04: پایانِ امروز فقط برای کسی که پول را می‌بیند
      val dayEnd = if (money) DayEndModel.build(d, today, clock, now, offsetMinutes) else null
      step()
      //  PRD-15 / PRM-16: فقط برای کسی که پول را می‌بیند
      val stockCost = if (money) ReportCore.stockCost(ledger) else null
      step()
      val promo = if (money && ir.vil3ntec.tohid.data.PromoEffectBook.has(d)) PromoFresh.view(d, ledger, "month", today, clock, cost) else null
      return PhoneFresh(home, debts, todayTables, dayEnd, stockCost, promo)
    }
  }
}
