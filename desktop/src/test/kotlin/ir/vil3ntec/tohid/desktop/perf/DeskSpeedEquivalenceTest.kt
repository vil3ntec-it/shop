package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.data.DashboardEngine
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.DeskAlerts
import ir.vil3ntec.tohid.ui.v3.shell.SearchIndex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PERF-DESK — هم‌ارزیِ راه‌های تندِ پوستهٔ کامپیوتر با راهِ پیشین، روی دفترِ قطعیِ [ScreenSpeed] (کوچک‌تر، همان شکل) و
 *  ردیف‌های دشوار (ی/ک ِ عربی، رقمِ فارسی در نام، فاکتورِ سری‌دار، مشتریِ بی‌تلفن، کالای خدماتی و کم‌موجود، قرضِ کهنه):
 *
 *  - هشدارهای سربرگ و منوی کناری ([DashboardEngine.alerts]) همان چهار فهرستِ `board` (همان ردیف‌ها، همان ترتیب)؛
 *  - جست‌وجوی Ctrl+K ([SearchIndex.search]) با جدول‌های یک‌باره همان نتیجه‌های راهِ پیشین (فیلترِ پیشین این‌جا رونویسی
 *    شده)، پیش و پس از عوض شدنِ دفتر.
 */
class DeskSpeedEquivalenceTest {

  @After fun release() { ShopStore.index(ShopData()); ShopStore.salesIndex(ShopData()) }

  private val today = todayIso()

  private val d: ShopData by lazy {
    val base = ScreenSpeed.shop(ScreenSpeed.Size(products = 1_500, sales = 4_000, suppliers = 20, purchases = 300, today = 80))
    val extraProducts = listOf(
      Product(id = "p-arabic", name = "شير كاكائو ۲۵۰ گرم", barcodes = listOf("9990001"), salePrice = 45.0, minStock = 5.0),
      Product(id = "p-service", name = "خدمات نصب", stockless = true, salePrice = 100.0),
      Product(id = "p-off", name = "کالای غیرفعال", active = false),
    )
    val extraDebtors = listOf(
      Debtor(id = "d-arabic", name = "علي كريمي", phone = "+93700123456"),
      Debtor(id = "d-nophone", name = "بی‌تلفن"),
    )
    val series = base.sales.take(30).mapIndexed { i, s -> s.copy(id = "series-$i", series = "B1", invoiceNumber = 154 + i, debtorId = if (i % 2 == 0) "d-arabic" else s.debtorId) }
    //  دو کالای موجود با سقفِ «کم» بالای موجودی‌شان ⇒ فهرستِ «کم‌موجود» هم ردیف دارد
    val stock = ShopStore.index(base)
    val low = base.products.filter { stock.stock(it.id) > 0.0 }.take(2).associate { it.id to stock.stock(it.id) + 1.0 }
    val products = base.products.map { p -> low[p.id]?.let { p.copy(minStock = it) } ?: p }
    base.copy(products = products + extraProducts, debtors = base.debtors + extraDebtors, sales = base.sales + series)
  }

  /* ------------------------------ هشدارها ------------------------------ */

  @Test fun `هشدارهای سربرگ و منو همان چهار فهرستِ تختهٔ کامل‌اند`() {
    val now = System.currentTimeMillis()
    for (data in listOf(d, ShopData(), d.copy(debtors = emptyList()))) {
      val board = DashboardEngine.board(data, today, 7, now)
      val a = DashboardEngine.alerts(data, today, 7, now)
      assertEquals(board.outOfStock, a.outOfStock)
      assertEquals(board.lowStock, a.lowStock)
      assertEquals(board.expiring, a.expiring)
      assertEquals(board.debtOverdue, a.debtOverdue)
      assertEquals(board.alertCount, a.alertCount)
    }
    //  دفترِ آزمون واقعاً هر چهار گونه را دارد — وگرنه هم‌ارزیِ فهرستِ خالی چیزی نمی‌سنجید
    val a = DashboardEngine.alerts(d, today, 7, now)
    assertTrue("out=${a.outOfStock.size} low=${a.lowStock.size} overdue=${a.debtOverdue.size}", a.outOfStock.isNotEmpty() && a.lowStock.isNotEmpty() && a.debtOverdue.isNotEmpty())
  }

  @Test fun `هشدارهای پوسته یک بار برای هر دفتر — دفترِ تازه نتیجهٔ تازه`() {
    val first = DeskAlerts.of(d, today)
    assertSame("همان دفتر ⇒ همان نتیجه (دومی از جدول)", first, DeskAlerts.of(d, today))
    assertEquals(DashboardEngine.board(d, today, DeskAlerts.DAYS).alertCount, first.alertCount)
    val next = d.copy(products = d.products.dropLast(1))
    //  `now` ِ دو فراخوانی چند میلی‌ثانیه فرق دارد ⇒ روزهای قرض مقایسه نمی‌شود، فقط ردیف‌ها
    val want = DashboardEngine.alerts(next, today, 7, System.currentTimeMillis())
    val got = DeskAlerts.of(next, today)
    assertEquals(want.outOfStock, got.outOfStock)
    assertEquals(want.lowStock, got.lowStock)
    assertEquals(want.expiring, got.expiring)
    assertEquals(want.debtOverdue.map { it.debtor.id }, got.debtOverdue.map { it.debtor.id })
  }

  /* ------------------------------ جست‌وجوی Ctrl+K ------------------------------ */

  /** فیلترهای `SearchIndex` پیش از PERF-DESK، خط به خط */
  private fun oldProducts(d: ShopData, q: String) = d.products.filter { SearchIndex.norm(it.name).contains(q) || it.barcodes.any { b -> b.contains(q) } }
  private fun oldDebtors(d: ShopData, q: String) = d.debtors.filter { SearchIndex.norm(it.name).contains(q) || SearchIndex.norm(it.phone).contains(q) || LocaleText.phoneMatches(it.phone, q) }
  private fun oldInvoices(d: ShopData, q: String): List<Sale> {
    val names = d.debtors.associate { it.id to it.name }
    val digits = q.all { it.isDigit() || it == ',' }
    return d.sales.filter { s ->
      s.invoiceLabel()?.lowercase()?.contains(q) == true ||
        (s.debtorId?.let { names[it] }?.let { SearchIndex.norm(it).contains(q) } == true) ||
        (digits && money(s.finalTotal).replace(",", "") == q.replace(",", ""))
    }.sortedByDescending { it.createdAt }
  }

  private fun same(data: ShopData, query: String) {
    val r = SearchIndex.search(data, query)
    val q = SearchIndex.norm(query).trim()
    if (q.isEmpty()) return
    fun check(kind: SearchIndex.Kind, old: List<String>) {
      val g = r.of(kind)!!
      assertEquals("$kind «$query» شمار", old.size, g.total)
      assertEquals("$kind «$query» ردیف‌ها", old.take(SearchIndex.CAP), g.hits.map { it.id })
    }
    check(SearchIndex.Kind.PRODUCT, oldProducts(data, q).map { it.id })
    check(SearchIndex.Kind.DEBTOR, oldDebtors(data, q).map { it.id })
    check(SearchIndex.Kind.INVOICE, oldInvoices(data, q).map { it.id })
  }

  private val queries = listOf(
    "", "  ", "ش", "شیر", "شير", "کاکائو", "كاكائو", "۲۵۰", "250", "9990001", "626", "علی", "علي کریمی", "0700123", "+93700", "۰۷۰۰",
    "154", "b1", "B1-15", "b1-154", "1,000", "100", "خدمات", "نیست-هیچ‌جا",
  ) + d.products.take(5).map { it.name.take(4) } + d.debtors.take(5).map { it.name.takeLast(3) } + d.sales.take(5).map { money(it.finalTotal) }

  @Test fun `جست‌وجو با جدول‌های یک‌باره همان نتیجهٔ راهِ پیشین — پیش و پس از عوض شدنِ دفتر`() {
    for (q in queries) same(d, q)
    //  دفترِ تازه (کالا و مشتریِ تازه، فاکتورِ تازه) ⇒ جدولِ تازه، نه نتیجهٔ دفترِ پیشین
    val next = d.copy(
      products = d.products + Product(id = "p-new", name = "شیرینی تازه", barcodes = listOf("1540001")),
      debtors = d.debtors.map { if (it.id == "d-nophone") it.copy(name = "نامِ تازهٔ شیر") else it },
      sales = d.sales + d.sales.first().copy(id = "s-new", invoiceNumber = 999_154, debtorId = "d-nophone"),
    )
    for (q in queries + listOf("شیرینی", "تازه", "999154")) same(next, q)
    //  و برگشت به همان دفترِ پیشین
    for (q in queries) same(d, q)
  }
}
