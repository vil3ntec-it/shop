package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtReverse
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseSuggest
import ir.vil3ntec.tohid.core.purchase.SupplierPricing
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.isoMillis
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.v3.money.ReceiptsModel
import ir.vil3ntec.tohid.ui.v3.shell.DashboardNumbers
import ir.vil3ntec.tohid.ui.v3.shell.Transactions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PERF-SCREEN — هم‌ارزیِ راه‌های تندِ صفحه‌ها با راهِ پیشین، روی دفترِ قطعیِ [ScreenSpeed] (کوچک‌تر، همان شکل) به‌علاوهٔ
 *  ردیف‌های دشوار: مشتریِ تکراری‌شناسه، رسیدِ مشتریِ حذف‌شده، خریدِ چندردیفه (یک فاکتور) و تکی، فاکتورِ لغوشدهٔ امروز.
 *  راهِ پیشین این‌جا عیناً رونویسی شده (`old…`) تا هر ناهمسانی سرخ شود.
 */
class ScreenSpeedEquivalenceTest {

  @After fun release() { ShopStore.index(ShopData()); ShopStore.salesIndex(ShopData()) }

  private val today = todayIso()

  private val d: ShopData by lazy {
    val base = ScreenSpeed.shop(ScreenSpeed.Size(products = 1_500, sales = 4_000, suppliers = 40, purchases = 1_200, today = 120))
    val dup = base.debtors[3].copy(name = "تکراری")
    val gone = DebtTransaction(id = "gone-rx", debtorId = "no-such-debtor", type = "receive", amount = 75.0, date = today, createdAt = 1L)
    val bill = List(3) { k ->
      Purchase(id = "bill-$k", productId = base.products[k].id, supplierId = base.suppliers[0].id, quantity = 5.0, totalAmount = 100.0 + k, date = today, createdAt = 10L + k, invoiceNo = 77)
    }
    val single = Purchase(id = "single", productId = base.products[9].id, supplierId = base.suppliers[1].id, quantity = 2.0, totalAmount = 40.0, date = today, createdAt = 0L)
    val cancelled = base.sales.last().copy(id = "cancelled-today", status = "cancelled")
    base.copy(
      debtors = base.debtors + dup,
      transactions = base.transactions + gone,
      purchases = base.purchases + bill + single,
      sales = base.sales + cancelled,
    )
  }

  /* ------------------------------ قرض‌داران: مدتِ قرض ------------------------------ */

  /** `DebtorEngine.debtSince` پیش از PERF-SCREEN، خط به خط */
  private fun oldDebtSince(d: ShopData, debtorId: String): Long? {
    val own = ir.vil3ntec.tohid.data.CustomerAlias.of(d, debtorId)
    val mine = DebtReverse.effective(d.transactions.filter { it.debtorId in own })
      .sortedWith(compareBy<DebtTransaction>({ it.date }, { it.createdAt }))
    if (mine.isEmpty()) return null
    val open = ArrayDeque<Pair<Long, Double>>()
    var credit = 0.0
    for (t in mine) {
      if (t.amount <= 0) continue
      if (t.type == "give") {
        var left = t.amount
        if (credit > 0) {
          val used = minOf(credit, left)
          credit -= used
          left -= used
        }
        if (left > 0.001) open.addLast((if (t.createdAt > 0) t.createdAt else isoMillis(t.date)) to left)
      } else {
        var pay = t.amount
        while (pay > 0.001 && open.isNotEmpty()) {
          val (at, left) = open.removeFirst()
          if (left - pay > 0.001) {
            open.addFirst(at to (left - pay))
            pay = 0.0
          } else {
            pay -= left
          }
        }
        if (pay > 0.001) credit += pay
      }
    }
    return open.firstOrNull()?.first
  }

  @Test fun `مدتِ قرضِ هر مشتری همان راهِ پیشین`() {
    val ids = d.debtors.map { it.id }.distinct() + "no-such-debtor"
    var owing = 0
    for (id in ids) {
      val want = oldDebtSince(d, id)
      if (want != null) owing++
      assertEquals(id, want, DebtorEngine.debtSince(d, id))
    }
    assertTrue("دفترِ آزمون بدهکار ندارد", owing > 100)
  }

  /* ------------------------------ خانه: «امروز» ------------------------------ */

  @Test fun `امروزِ خانه همان فهرستِ کامل صاف‌شده`() {
    val busiest = d.sales.groupingBy { it.date }.eachCount().maxByOrNull { it.value }!!.key
    val days = listOf(today, busiest, d.purchases.first().date, "1999-01-01")
    for (day in days) for (n in listOf(1, 5, 10_000)) for (purchases in listOf(true, false)) {
      val want = Transactions.list(d, purchases = purchases).filter { it.date == day }.take(n)
      assertEquals("$day n=$n purchases=$purchases", want, Transactions.today(d, day, n, purchases))
    }
    assertTrue("امروز ردیف ندارد", Transactions.today(d, today, 10_000, true).size > 120)
  }

  /* ------------------------------ رسیدها ------------------------------ */

  /** `ReceiptsModel.rows(DEBTORS)` پیش از PERF-SCREEN */
  private fun oldReceipts(d: ShopData): List<ReceiptsModel.Row> =
    DebtReverse.effective(d.transactions).filter { it.type == "receive" && !DebtReverse.isReversal(it) }.mapNotNull { t ->
      val j = Jalali.ofIso(t.date) ?: return@mapNotNull null
      val who = ir.vil3ntec.tohid.data.CustomerAlias.keeperOf(d, t.debtorId)
      val debtor: Debtor? = d.debtors.find { it.id == who }
      ReceiptsModel.Row(t.id, debtor?.name ?: StrMoney.deletedDebtor, t.notes, t.amount, t.date, who, debtor != null, j.year, j.month)
    }.sortedByDescending { it.date }

  @Test fun `رسیدها همان ردیف‌ها، مشتریِ تکراری نخستین و حذف‌شده بی پیوند`() {
    val want = oldReceipts(d)
    assertTrue("رسیدی نیست", want.size > 1_000)
    assertEquals(want, ReceiptsModel.rows(d, ReceiptsModel.Tab.DEBTORS))
    assertTrue(want.any { !it.linked })
  }

  /* ------------------------------ خانه: عددها ------------------------------ */

  @Test fun `عددهای هفته و قرضِ عقب‌افتادهٔ خانه همان`() {
    val got = DashboardNumbers.of(d, today)
    val nowMs = isoMillis(today)
    val dayMs = 86_400_000L
    var week = 0.0
    var prev = 0.0
    d.sales.filter { it.status != "cancelled" }.forEach { s ->
      val age = (nowMs - isoMillis(s.date)) / dayMs
      if (age in 0..6) week += s.finalTotal else if (age in 7..13) prev += s.finalTotal
    }
    assertEquals(week, got.weekTotal, 0.0)
    assertEquals(prev, got.prevWeekTotal, 0.0)
    val last = ir.vil3ntec.tohid.data.DebtRows.of(d).mapValues { (_, l) -> l.maxOf { isoMillis(it.date) } }
    val overdue = got.owing.filter { (debtor, _) -> (last[debtor.id] ?: 0L).let { it > 0L && (nowMs - it) / dayMs > 30 } }
    assertEquals(overdue, got.overdue)
    assertEquals(overdue.maxOfOrNull { (debtor, _) -> ((nowMs - (last[debtor.id] ?: nowMs)) / dayMs).coerceAtLeast(0) } ?: 0L, got.overdueOldestDays)
    assertTrue(got.overdue.isNotEmpty() && week > 0)
  }

  /* ------------------------------ انبار: پیشنهادِ خرید ------------------------------ */

  /** `PurchaseSuggest.rows` حالا به هر کالا فقط خریدهای همان کالا را می‌دهد؛ هر دو تابع با همه‌ی خریدها همان جواب را بدهند */
  @Test fun `پیشنهادِ خرید با خریدهای هر کالا همان جوابِ همهٔ خریدها`() {
    val all = PurchaseOrderBook.buys(d)
    val byProduct = all.groupBy { it.productId }
    var named = 0
    for (p in d.products) {
      val mine = byProduct[p.id].orEmpty()
      val latest = SupplierPricing.latestSupplier(all, p.id)
      assertEquals(p.id, latest, SupplierPricing.latestSupplier(mine, p.id))
      if (latest != null) named++
      for (sid in d.suppliers.take(4).map { it.id } + listOfNotNull(latest)) {
        assertEquals("${p.id} $sid", PurchaseOrderBook.lastPriceMinor(d, sid, p, all), PurchaseOrderBook.lastPriceMinor(d, sid, p, mine))
      }
    }
    assertTrue("کالای خریده‌شده نیست", named > 300)
    assertTrue(PurchaseSuggest.rows(d, emptyList(), today).isNotEmpty())
  }

  /* ------------------------------ تأمین‌کننده: بدهی ------------------------------ */

  @Test fun `بدهیِ هر تأمین‌کننده بیت‌به‌بیت همان جمعِ پیشین`() {
    val extra = ir.vil3ntec.tohid.data.SupplierPayment(id = "pay-only", supplierId = "pay-only-supplier", amount = 12.5, date = today)
    val dd = d.copy(supplierPayments = d.supplierPayments + extra)
    val ids = dd.suppliers.map { it.id } + listOf("pay-only-supplier", "no-such-supplier", "")
    for (id in ids) {
      val mine = dd.purchases.filter { it.supplierId == id }
      val want = mine.sumOf { it.totalAmount } - (mine.sumOf { it.paidAmount } + dd.supplierPayments.filter { it.supplierId == id }.sumOf { it.amount })
      assertEquals(id, want, ShopStore.supplierDebt(dd, id), 0.0)
      assertEquals(id, java.lang.Double.doubleToLongBits(want), java.lang.Double.doubleToLongBits(ShopStore.supplierDebt(dd, id)))
    }
    assertTrue(dd.suppliers.count { ShopStore.supplierDebt(dd, it.id) > 0 } > 10)
  }
}
