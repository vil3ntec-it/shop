package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.TillMethods
import ir.vil3ntec.tohid.data.CreditDebtor
import ir.vil3ntec.tohid.data.GiftCards
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData

/**
 *  e-paystep — «گامِ پرداخت» ِ صندوق (صاحب‌کار ۱۴۰۵/۰۸/۲۵: «وقتی ثبتِ فروش می‌زنم نباید پرداخت بشه؛ این باید ببره پیشِ بخشِ
 *  پرداخت یا تأییدِ پرداخت که اون‌جا کیو‌آر رو نشون بده یا کارت‌خوان تأیید کنه»). مثلِ صندوق‌های حرفه‌ای دو گام: تسویه (مشتری،
 *  تخفیف، جمع) ⇒ «پرداخت»؛ در این گام هر پرداخت جدا **تأیید** می‌شود و فروش فقط وقتی ثبت می‌شود که همهٔ فاکتور پرداخت یا به
 *  حسابِ مشتری رفته باشد ([Result.Done]). گوشی (`PayStepRoute`) و کامپیوتر (`PayStepDialog`) همین منطق را دارند.
 *
 *  پرداخت‌های تأییدشده همان [PayPanelState] اند (نقد = `cash`، بقیه = `rows`)؛ حساب همه `PayFlow` ⇐ هستهٔ `Pay.settle` (پولِ
 *  پس فقط از نقد، بیش‌پرداختِ غیرنقد نه، گرد فقط سهمِ نقد). [PayEntry] فقط آنچه صندوقدار همین حالا می‌نویسد.
 *  ⛔ هیچ چیزی این‌جا ثبت نمی‌شود: [Result.Done] فقط تسویه را به صفحه می‌دهد و صفحه از همان راهِ همیشگی ثبت می‌کند
 *  (`SalesEngine.record` با `settlement`، کلیدِ یکتای فاکتور). تأییدِ دوباره پس از بسته شدنِ پرداخت پرداختی نمی‌افزاید.
 */
class PayEntry {
  /** کاشیِ بازِ همین لحظه؛ `null` ⇒ هیچ (فقط کاشی‌ها) */
  var tile by mutableStateOf<TillMethods.Tile?>(null)
  /** روشِ ردیفِ غیرنقد (کارت، کیف پول، حواله، کارتِ هدیه …) */
  var method by mutableStateOf("")
  /** نقدِ دریافتی یا مبلغِ روشِ غیرنقد (متنِ کادر) */
  var amount by mutableStateOf("")
  /** شمارهٔ تأیید/مرجع */
  var ref by mutableStateOf("")

  fun clear() { tile = null; method = ""; amount = ""; ref = "" }
}

object PayStep {

  sealed interface Result {
    /** همهٔ فاکتور پرداخت یا به حسابِ مشتری رفت ⇒ ثبت با همین تسویه */
    data class Done(val settlement: Settlement) : Result
    /** پرداخت نشست؛ باقی مانده */
    data object More : Result
    /** هیچ چیز عوض نشد؛ [needCustomer] ⇒ صفحه گزینشِ مشتری را باز کند */
    data class Bad(val message: String, val needCustomer: Boolean = false) : Result
  }

  /** پرداختی که تأیید شده (برای فهرستِ «پرداخت‌شده‌ها»): [row] = شمارهٔ ردیفِ `rows`، ‎-1 = نقد */
  data class Paid(val row: Int, val method: String, val name: String, val amountMinor: Long, val ref: String)

  /** متنِ سادهٔ مبلغ برای کادرها (همان `PayFlow.amountOf` می‌خواند) */
  fun plain(minor: Long): String = if (minor % 100 == 0L) (minor / 100).toString() else "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"

  /** باقیِ پرداخت‌نشده پس از پرداخت‌های تأییدشده (بی حسابِ مشتری)، به پول */
  fun dueMinor(panel: PayPanelState, total: Double): Long =
    PayFlow.preview(panel.draft(total, null)).valueOrNull()?.remaining
      ?: (PayFlow.minor(total) - PayFlow.minor(PayFlow.amountOf(panel.cash) ?: 0.0) - panel.rows.sumOf { PayFlow.minor(PayFlow.amountOf(it.amount) ?: 0.0) }).coerceAtLeast(0L)

  /** نقدی که باقی را دقیق می‌بندد — افزون بر نقدِ تأییدشده، با گردکردنِ دکان */
  fun exactCashMinor(panel: PayPanelState, total: Double): Long {
    if (dueMinor(panel, total) <= 0) return 0L
    val all = PayFlow.exactCash(panel.draft(total, null)).valueOrNull()?.let(PayFlow::minor) ?: return dueMinor(panel, total)
    return (all - PayFlow.minor(PayFlow.amountOf(panel.cash) ?: 0.0)).coerceAtLeast(0L)
  }

  /** دکمه‌های تندِ نقد: دقیق، سپس گردِ بالای ۵۰، ۱۰۰، ۵۰۰ و ۱۰۰۰ افغانی (بی تکرار، حداکثر چهار) */
  fun cashChips(exactMinor: Long): List<Long> {
    if (exactMinor <= 0) return emptyList()
    val ups = listOf(5_000L, 10_000L, 50_000L, 100_000L).map { step -> ((exactMinor + step - 1) / step) * step }
    return (listOf(exactMinor) + ups).distinct().filter { it >= exactMinor }.take(4)
  }

  /** پولِ پسِ نقدی که الان در کادر است (همراهِ نقدِ تأییدشده)؛ ۰ ⇒ هیچ */
  fun changeMinor(entry: PayEntry, panel: PayPanelState, total: Double): Long {
    val add = PayFlow.minor(PayFlow.amountOf(entry.amount) ?: 0.0)
    if (add <= 0) return 0L
    val d = panel.draft(total, null).copy(cash = plain(PayFlow.minor(PayFlow.amountOf(panel.cash) ?: 0.0) + add))
    return PayFlow.preview(d).valueOrNull()?.change ?: 0L
  }

  /** پرداخت‌های تأییدشده به ترتیب: نقد (اگر هست) و سپس ردیف‌ها */
  fun paid(panel: PayPanelState): List<Paid> = buildList {
    PayFlow.minor(PayFlow.amountOf(panel.cash) ?: 0.0).takeIf { it > 0 }?.let { add(Paid(-1, PayFlow.CASH, PayFlow.methodName(PayFlow.CASH), it, "")) }
    panel.rows.forEachIndexed { i, r -> PayFlow.minor(PayFlow.amountOf(r.amount) ?: 0.0).takeIf { it > 0 }?.let { add(Paid(i, r.method, PayFlow.methodName(r.method), it, r.ref.trim())) } }
  }

  /** باز کردنِ کاشی: نقد ⇒ کادرِ خالی («دقیق» با زدنِ تأیید)؛ غیرنقد ⇒ همان باقی‌مانده، روشِ پیش‌فرضِ کاشی */
  fun open(entry: PayEntry, tile: TillMethods.Tile, panel: PayPanelState, total: Double) {
    entry.tile = tile
    entry.method = tile.method?.takeIf { tile.kind != TillMethods.Kind.CASH }.orEmpty()
    entry.ref = ""
    entry.amount = when (tile.kind) {
      TillMethods.Kind.CARD, TillMethods.Kind.WALLET, TillMethods.Kind.TRANSFER -> dueMinor(panel, total).takeIf { it > 0 }?.let(::plain).orEmpty()
      else -> ""
    }
  }

  /** برداشتنِ یک پرداختِ تأییدشده (پیش از ثبت): ‎-1 = نقد */
  fun remove(panel: PayPanelState, row: Int) {
    if (row < 0) { panel.cash = ""; panel.fxAmount = "" } else if (row in panel.rows.indices) panel.rows.removeAt(row)
  }

  /**
   *  تأییدِ همان چیزی که [entry] می‌گوید. [ledger] (اختیاری) ⇒ پیش‌سنجیِ کارتِ هدیه/اعتبار (`GiftCards`) و نسیهٔ بی مشتریِ درست
   *  (`CreditDebtor`) روی همان دفترِ صفحه. فاکتورِ پرداخت‌شده ⇒ هیچ پرداختِ تازه‌ای افزوده نمی‌شود (دو زدن، دو ثبت نمی‌سازد).
   */
  fun confirm(entry: PayEntry, panel: PayPanelState, total: Double, customerId: String?, ledger: ShopData? = null): Result {
    val tile = entry.tile ?: return Result.Bad(StrTillPay.pickMethod)
    val due = dueMinor(panel, total)
    if (due <= 0 && tile.kind != TillMethods.Kind.CREDIT) return finish(panel, total, customerId, ledger)
    return when (tile.kind) {
      TillMethods.Kind.CASH -> {
        val typed = PayFlow.amountOf(entry.amount)
        if (typed != null && typed.isNaN()) return Result.Bad(StrLedger.amountInvalid)
        val add = typed?.let(PayFlow::minor) ?: exactCashMinor(panel, total)
        if (add <= 0) return Result.Bad(StrLedger.amountInvalid)
        val before = panel.cash
        panel.cash = plain(PayFlow.minor(PayFlow.amountOf(before) ?: 0.0) + add)
        when (val p = PayFlow.preview(panel.draft(total, null))) {
          is PayFlow.Outcome.Bad -> { panel.cash = before; Result.Bad(PayFlow.message(p.problem)) }
          is PayFlow.Outcome.Ok -> { entry.amount = ""; if (p.value.remaining == 0L) finish(panel, total, customerId, ledger) else Result.More }
        }
      }
      TillMethods.Kind.CREDIT -> {
        if (customerId.isNullOrBlank()) return Result.Bad(StrTillPay.creditNeedsCustomer, needCustomer = true)
        finish(panel, total, customerId, ledger)
      }
      TillMethods.Kind.HESABPAY -> Result.Bad(StrTillPay.pickMethod)
      else -> {
        val method = entry.method.ifBlank { tile.method.orEmpty() }
        val m = PayFlow.method(method) ?: return Result.Bad(StrLedger.methodUnknown)
        val typed = PayFlow.amountOf(entry.amount)
        if (typed != null && typed.isNaN()) return Result.Bad(StrLedger.amountInvalid)
        val amount = typed?.let(PayFlow::minor) ?: due
        if (amount <= 0) return Result.Bad(StrLedger.amountInvalid)
        val ref = entry.ref.trim()
        if (m.needsRef && ref.isEmpty()) return Result.Bad(StrLedger.refRequired("«${m.name}»"))
        panel.rows += PayFlow.DraftRow(m.id, ref, plain(amount))
        when (val p = PayFlow.preview(panel.draft(total, customerId))) {
          is PayFlow.Outcome.Bad -> { panel.rows.removeAt(panel.rows.lastIndex); Result.Bad(PayFlow.message(p.problem)) }
          is PayFlow.Outcome.Ok -> {
            //  PAY-24: خرجِ بیش از ماندهٔ کارتِ هدیه یا اعتبار همین‌جا (`SalesEngine.record` هم دوباره می‌سنجد)
            val stored = if (panel.stored && ledger != null) GiftCards.problem(ledger, p.value, customerId)?.let(GiftCards::message) else null
            if (stored != null) { panel.rows.removeAt(panel.rows.lastIndex); return Result.Bad(stored) }
            entry.amount = ""; entry.ref = ""
            if (p.value.remaining == 0L) finish(panel, total, customerId, ledger) else Result.More
          }
        }
      }
    }
  }

  /** تسویهٔ نهایی؛ باقیِ پرداخت‌نشده فقط با مشتریِ موجود و فعال به حساب می‌رود (`CreditDebtor`) */
  fun finish(panel: PayPanelState, total: Double, customerId: String?, ledger: ShopData? = null): Result =
    when (val r = PayFlow.checkout(panel.draft(total, customerId))) {
      is PayFlow.Outcome.Bad -> Result.Bad(PayFlow.message(r.problem), needCustomer = r.problem.code == "SHORT")
      is PayFlow.Outcome.Ok -> {
        val block = ledger?.let { CreditDebtor.tillBlock(it, customerId, CreditDebtor.splitOwed(r.value), split = true) }
        val stored = if (panel.stored && ledger != null) GiftCards.problem(ledger, r.value, customerId)?.let(GiftCards::message) else null
        when {
          block != null -> Result.Bad(block, needCustomer = true)
          stored != null -> Result.Bad(stored)
          else -> Result.Done(r.value)
        }
      }
    }
}
