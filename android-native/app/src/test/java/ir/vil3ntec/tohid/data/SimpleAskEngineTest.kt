package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.report.SimpleAsk
import ir.vil3ntec.tohid.core.report.SimpleAsk.Param
import ir.vil3ntec.tohid.core.report.SimpleAsk.Period
import ir.vil3ntec.tohid.core.report.SimpleAsk.Problem
import ir.vil3ntec.tohid.core.report.SimpleAsk.Query
import ir.vil3ntec.tohid.core.report.SimpleAsk.Span
import ir.vil3ntec.tohid.core.report.SimpleAsk.Template
import ir.vil3ntec.tohid.data.SimpleAskEngine.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  RPT-16: هر پاسخِ «پرسشِ ساده» همان عددِ `ReportEngine` / `ShopStore` در همان بازهٔ «گزارش‌ها» است، مرزِ ماه و سالِ
 *  خورشیدی همان `ReportEngine.jalaliMonth`، پرسشِ پولی برای نقشِ بی `reports.money` بسته، و کلیدِ خاموش زبانه‌ای نمی‌سازد.
 */
class SimpleAskEngineTest {

  private fun s(y: Int, m: Int, d: Int): String = SolarHijri.fromSolar(y, m, d)!!

  private val today = s(1405, 8, 11)
  private val hoot1403 = Period(Span.JMONTH, 1403, 12)
  private val hamal1404 = Period(Span.JMONTH, 1404, 1)

  private val data = ShopData(
    products = listOf(
      Product(id = "p1", name = "برنج", category = "خوراکی", purchasePrice = 200.0, salePrice = 300.0, minStock = 5.0),
      Product(id = "p2", name = "روغن", category = " خوراکی ", purchasePrice = 400.0, salePrice = 500.0, minStock = 2000.0),
      Product(id = "p3", name = "صابون", category = "شوینده", purchasePrice = 50.0, salePrice = 80.0),
    ),
    productCategories = listOf("خوراکی", "ابزار"),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 1000.0),
      WarehouseEntry(id = "w2", productId = "p2", units = 1000.0),
    ),
    sales = listOf(
      //  ۳۰ حوتِ ۱۴۰۳ (کبیسه) — روزِ آخرِ ماه
      Sale(id = "s1", total = 3000.0, discount = 500.0, finalTotal = 2500.0, date = s(1403, 12, 30), seller = "علی"),
      //  ۱ حملِ ۱۴۰۴، بی نامِ فروشنده ⇒ «نامعلوم»
      Sale(id = "s2", total = 1000.0, finalTotal = 1000.0, date = s(1404, 1, 1)),
      Sale(id = "s3", total = 900.0, finalTotal = 900.0, date = s(1403, 12, 15), seller = "علی", status = "cancelled"),
      Sale(id = "s4", total = 800.0, finalTotal = 800.0, date = s(1403, 12, 1), seller = "رضا"),
    ),
    saleItems = listOf(
      SaleItem(id = "i1", saleId = "s1", productId = "p1", name = "برنج", quantity = 10.0, unitPrice = 300.0, purchasePrice = 200.0, totalPrice = 3000.0, returnedQty = 2.0),
      SaleItem(id = "i2", saleId = "s2", productId = "p2", name = "روغن", quantity = 2.0, unitPrice = 500.0, purchasePrice = 400.0, totalPrice = 1000.0),
      SaleItem(id = "i3", saleId = "s3", productId = "p1", name = "برنج", quantity = 3.0, unitPrice = 300.0, purchasePrice = 200.0, totalPrice = 900.0),
      SaleItem(id = "i4", saleId = "s4", productId = "p3", name = "صابون", quantity = 10.0, unitPrice = 80.0, purchasePrice = 50.0, totalPrice = 800.0),
    ),
    //  برگشت در روزِ خودش (حملِ ۱۴۰۴)، نه روزِ فاکتور
    saleReturns = listOf(SaleReturn(id = "r1", saleId = "s1", saleItemId = "i1", productId = "p1", quantity = 2.0, amount = 600.0, date = s(1404, 1, 2))),
    expenses = listOf(
      Expense(id = "e1", amount = 150.0, date = s(1403, 12, 10)),
      Expense(id = "e2", amount = 99.0, date = s(1404, 1, 5)),
    ),
    suppliers = listOf(Supplier(id = "sup", name = "تأمین")),
    purchases = listOf(Purchase(id = "u1", supplierId = "sup", totalAmount = 640.0, paidAmount = 600.0, debt = 40.0, date = s(1403, 12, 20))),
    debtors = listOf(Debtor(id = "c1", name = "احمد"), Debtor(id = "c2", name = "کریم")),
    transactions = listOf(
      DebtTransaction(id = "t1", debtorId = "c1", type = "give", amount = 1000.0),
      DebtTransaction(id = "t2", debtorId = "c1", type = "take", amount = 400.0),
      DebtTransaction(id = "t3", debtorId = "c2", type = "give", amount = 100.0),
      DebtTransaction(id = "t4", debtorId = "c2", type = "take", amount = 150.0),
    ),
  )

  private fun ok(q: Query, money: Boolean = true): SimpleAskEngine.Answer {
    val o = SimpleAskEngine.answer(data, q, today, money)
    assertTrue("$q ⇒ $o", o is SimpleAskEngine.Outcome.Ok)
    return (o as SimpleAskEngine.Outcome.Ok).answer
  }

  private fun rejected(q: Query, money: Boolean = true): List<SimpleAsk.Issue> {
    val o = SimpleAskEngine.answer(data, q, today, money)
    assertTrue("$q ⇒ $o", o is SimpleAskEngine.Outcome.Rejected)
    return (o as SimpleAskEngine.Outcome.Rejected).issues
  }

  private fun near(expected: Double, actual: Double?) = assertEquals(expected, actual!!, 1e-9)

  @Test fun `هر پرسشِ بازه‌دار همان عددِ ReportEngine در همان بازه است`() {
    val (from, to) = ReportEngine.jalaliMonth(1403, 12)
    val sales = ReportEngine.sales(data, from, to)
    val a = ok(Query(Template.SALES, hoot1403))
    assertEquals(from to to, a.from to a.to)
    near(sales.net, a.amount(Key.NET)); near(sales.count.toDouble(), a.amount(Key.INVOICES))
    near(sales.discount, a.amount(Key.DISCOUNT)); near(sales.returnAmount, a.amount(Key.RETURNS))
    //  s1 + s4؛ s3 لغو شده و برگشتِ r1 در حمل است
    near(3300.0, a.amount(Key.NET)); near(2.0, a.amount(Key.INVOICES))

    val p = ok(Query(Template.PROFIT, hoot1403))
    near(sales.grossProfit, p.amount(Key.GROSS_PROFIT)); near(sales.cogs, p.amount(Key.COGS))
    near(sales.expenses, p.amount(Key.EXPENSES)); near(sales.netProfit, p.amount(Key.NET_PROFIT))
    near(650.0, p.amount(Key.NET_PROFIT))
    near(150.0, ok(Query(Template.EXPENSES, hoot1403)).amount(Key.EXPENSES))

    val buy = ReportEngine.purchases(data, from, to)
    val b = ok(Query(Template.PURCHASES, hoot1403))
    near(buy.total, b.amount(Key.PURCHASE_TOTAL)); near(buy.paid, b.amount(Key.PURCHASE_PAID))
    near(buy.debt, b.amount(Key.PURCHASE_DEBT)); near(1.0, b.amount(Key.PURCHASES))

    val seller = ReportEngine.bySeller(data, from, to).first { it.seller == "علی" }
    val c = ok(Query(Template.CASHIER_SALES, hoot1403, cashier = "علی"))
    near(seller.net, c.amount(Key.NET)); near(seller.count.toDouble(), c.amount(Key.INVOICES)); near(seller.discount, c.amount(Key.DISCOUNT))
    near(2500.0, c.amount(Key.NET))
    //  فروشنده‌ای که در این بازه فاکتوری ندارد ⇒ صفر، نه خطا
    near(0.0, ok(Query(Template.CASHIER_SALES, hoot1403, cashier = "نامعلوم")).amount(Key.NET))
    near(1000.0, ok(Query(Template.CASHIER_SALES, hamal1404, cashier = "نامعلوم")).amount(Key.NET))

    val insight = ReportEngine.stockInsight(data, from, to)
    val food = ok(Query(Template.CATEGORY_SALES, hoot1403, category = "خوراکی"))
    near(insight.rows.filter { it.product.category.trim() == "خوراکی" }.sumOf { it.sales }, food.amount(Key.CATEGORY_NET))
    //  (۱۰ − ۲) × ۳۰۰؛ روغن در حمل فروخته شده
    near(2400.0, food.amount(Key.CATEGORY_NET)); near(1.0, food.amount(Key.CATEGORY_PRODUCTS))
    near(insight.rows.first { it.product.id == "p3" }.sales, ok(Query(Template.PRODUCT_SALES, hoot1403, product = "p3")).amount(Key.PRODUCT_NET))
    near(0.0, ok(Query(Template.PRODUCT_SALES, hoot1403, product = "p2")).amount(Key.PRODUCT_NET))
  }

  @Test fun `پرسش‌های همین حالا همان عددِ گزارش‌های کالا و قرض‌داران است`() {
    val stock = ok(Query(Template.PRODUCT_STOCK, product = "p2"))
    near(ShopStore.stock(data, "p2"), stock.amount(Key.ON_HAND))
    assertEquals(ShopStore.stockStatus(data, data.products[1]), stock.status)
    assertEquals(null to null, stock.from to stock.to)

    val products = ReportEngine.products(data)
    val low = ok(Query(Template.LOW_STOCK))
    near(products.out.size.toDouble(), low.amount(Key.OUT)); near(products.low.size.toDouble(), low.amount(Key.LOW))
    assertEquals((products.out + products.low).map { it.name }, low.names)
    assertTrue(low.names.containsAll(listOf("صابون", "روغن")))

    assertEquals(products.topSelling.map { it.product.name to it.quantity }, ok(Query(Template.TOP_SELLING)).values.map { it.name to it.amount })
    assertEquals(products.slowest.map { it.product.name to it.quantity }, ok(Query(Template.SLOW_SELLING)).values.map { it.name to it.amount })

    val rows = ReportEngine.debtors(data)
    val ahmad = ok(Query(Template.CUSTOMER_DEBT, customer = "c1"))
    near(rows.first { it.debtor.id == "c1" }.remaining, ahmad.amount(Key.DEBT_REMAINING))
    near(600.0, ahmad.amount(Key.DEBT_REMAINING)); near(1000.0, ahmad.amount(Key.DEBT_GIVEN)); near(400.0, ahmad.amount(Key.DEBT_RECEIVED))
    near(-50.0, ok(Query(Template.CUSTOMER_DEBT, customer = "c2")).amount(Key.DEBT_REMAINING))
    //  همان کاشیِ «جمع طلب از مشتریان»: پیش‌پرداختِ کریم از طلبِ احمد کم نمی‌شود
    val total = ok(Query(Template.DEBT_TOTAL))
    near(rows.sumOf { it.remaining.coerceAtLeast(0.0) }, total.amount(Key.DEBT_TOTAL))
    near(600.0, total.amount(Key.DEBT_TOTAL)); near(1.0, total.amount(Key.DEBTORS))
  }

  @Test fun `مرزِ ماه و سالِ خورشیدی همان بازهٔ زبانه‌های گزارش است`() {
    //  حوتِ ۱۴۰۳ کبیسه ۳۰ روز، حوتِ ۱۴۰۴ ۲۹ روز
    assertEquals(s(1403, 12, 1) to s(1403, 12, 30), SimpleAskEngine.range(hoot1403, today))
    assertEquals(s(1404, 12, 1) to s(1404, 12, 29), SimpleAskEngine.range(Period(Span.JMONTH, 1404, 12), today))
    assertEquals(s(1404, 1, 1) to s(1404, 12, 29), SimpleAskEngine.range(Period(Span.JYEAR, 1404), today))
    for ((span, r) in listOf(
      Span.TODAY to ReportEngine.Range.TODAY, Span.WEEK to ReportEngine.Range.WEEK, Span.MONTH to ReportEngine.Range.MONTH,
      Span.ALL to ReportEngine.Range.ALL, Span.JMONTH to ReportEngine.Range.JMONTH, Span.JYEAR to ReportEngine.Range.JYEAR,
    )) assertEquals(span.name, ReportEngine.rangeOf(r, today, 1404, 7), SimpleAskEngine.range(Period(span, 1404, 7), today))
    //  ۳۰ حوت در حوت است و ۱ حمل در حملِ سالِ بعد؛ برگشتِ ۲ حمل همان‌جا
    near(2500.0 + 800.0, ok(Query(Template.SALES, hoot1403)).amount(Key.NET))
    val hamal = ok(Query(Template.SALES, hamal1404))
    near(1000.0, hamal.amount(Key.NET)); near(600.0, hamal.amount(Key.RETURNS))
    near(1000.0, ok(Query(Template.SALES, Period(Span.JYEAR, 1404))).amount(Key.NET))
    near(3300.0, ok(Query(Template.SALES, Period(Span.JYEAR, 1403))).amount(Key.NET))
  }

  @Test fun `نقشِ بی reports money پرسشِ پولی نمی‌گیرد و بقیه را همان‌طور می‌گیرد`() {
    for (t in Template.entries.filter { it.money }) {
      val q = Query(t, hoot1403, product = "p1", category = "خوراکی", cashier = "علی")
      assertEquals(t.id, listOf(SimpleAsk.Issue(Problem.NOT_ALLOWED)), rejected(q, money = false))
    }
    near(600.0, ok(Query(Template.CUSTOMER_DEBT, customer = "c1"), money = false).amount(Key.DEBT_REMAINING))
    ok(Query(Template.LOW_STOCK), money = false)
    ok(Query(Template.PRODUCT_STOCK, product = "p1"), money = false)
  }

  @Test fun `نامِ ناشناخته یا جای خالی هیچ عددی نمی‌سازد`() {
    assertEquals(listOf(SimpleAsk.Issue(Problem.UNKNOWN, Param.PRODUCT)), rejected(Query(Template.PRODUCT_STOCK, product = "p404")))
    assertEquals(listOf(SimpleAsk.Issue(Problem.MISSING, Param.CASHIER)), rejected(Query(Template.CASHIER_SALES, hoot1403)))
    assertEquals(listOf(SimpleAsk.Issue(Problem.UNKNOWN, Param.CUSTOMER)), rejected(Query(Template.CUSTOMER_DEBT, customer = "c404")))
    assertEquals(listOf(SimpleAsk.Issue(Problem.BAD_PERIOD, Param.PERIOD)), rejected(Query(Template.SALES, Period(Span.JMONTH, 1404, 0))))
  }

  @Test fun `فهرستِ نام‌ها از خودِ دفتر است و جمله تا عددِ گزارش می‌رسد`() {
    val c = SimpleAskEngine.catalog(data)
    assertEquals(listOf("p1", "p2", "p3"), c.products.map { it.id })
    //  فهرستِ دسته‌های دکان و دستهٔ خودِ کالاها، بی فاصلهٔ اضافه و بی تکرار
    assertEquals(listOf("خوراکی", "ابزار", "شوینده"), c.categories)
    assertEquals(listOf("c1", "c2"), c.customers.map { it.id })
    assertEquals(setOf("علی", "رضا", "نامعلوم"), c.cashiers.toSet())

    val q = SimpleAsk.parse("فروشِ علي در حوت ۱۴۰۳", c, 1405, 8, null).query!!
    assertEquals(Query(Template.CASHIER_SALES, hoot1403, cashier = "علی"), q)
    near(2500.0, ok(q).amount(Key.NET))
    val debt = SimpleAsk.parse("قرض احمد چقدر است؟", c, 1405, 8, null).query!!
    near(600.0, ok(debt, money = false).amount(Key.DEBT_REMAINING))
  }

  /* -------------------- کلیدِ خاموش ⇒ «گزارش‌ها» همان امروز -------------------- */

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")

  //  تنظیماتِ کم (تصمیمِ صاحب‌کار، ۱۴۰۵/۰۸/۱۱): «پرسشِ ساده» فقط خواندنی است و چیزی را عوض نمی‌کند ⇒ پیش‌فرض روشن
  //  و بی ردیف در تنظیمات (`SettingsTiers.ALWAYS_ON`)؛ «خاموش» ِ دستیِ کاربر می‌ماند و بی آن هنوز زبانه‌ای نیست
  @Test fun `کلید پیش‌فرض روشن است و بی آن زبانهٔ پرسش ساخته نمی‌شود`() {
    val pref = src("data/SimpleAskPrefAndroid.kt")
    assertTrue(pref.contains("getBoolean(KEY, true) }.getOrDefault(false)"))
    assertFalse(pref.contains("putBoolean(KEY, true)"))
    //  گوشی: «گزارش‌ها» ِ نسخهٔ ۳ — زبانه فقط با کلید و بخش فقط از همان زبانه (صفحهٔ قدیمیِ `ui/screens/Reports.kt`
    //  بی‌راه بود و در LOC-06 برداشته شد)
    val phone = src("ui/v3/analytics/ReportsScreen.kt")
    assertTrue(phone.contains("if (SimpleAskPref.on(context)) add(ReportExtra.ASK)"))
    assertTrue(phone.contains("ReportExtra.ASK -> ReportList(list) { simpleAskSection(this, d, canSeeMoney) }"))
    assertEquals(1, Regex("simpleAskSection\\(").findAll(phone).count())
    assertEquals(1, Regex(Regex.escape("add(ReportExtra.ASK)")).findAll(phone).count())
    //  کامپیوتر: گزارش‌های رابطِ امروز (`DeskReports.kt`، زبانهٔ `ASK`) بی‌مرجع از نسخهٔ ۳ بود و در «پاک‌سازی، گامِ ۳»
    //  پاک شد؛ `DeskSimpleAsk` تا جای کامپیوتریِ نسخهٔ ۳ می‌ماند (STATUS، یادداشت‌ها) و هیچ صفحه‌ای آن را بی کلید نمی‌کشد
    val ui = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui") }.first { it.isDirectory }
    ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.name to it.readText() }
      .filter { (_, s) -> Regex("""(?<!fun )\bDeskSimpleAsk\(""").containsMatchIn(s) }
      .forEach { (name, s) -> assertTrue("$name زبانهٔ پرسش را بی کلید می‌کشد", s.contains("SimpleAskPref.on(context)")) }
    //  صفحه و ردیفِ تنظیماتِ نسخهٔ ۳ (گوشی و کامپیوتر) — جای `SettingsLegacy` و فهرستِ کامپیوترِ رابطِ قدیمِ پاک‌شده
    assertEquals(1, Regex(Regex.escape("SimpleAskPref.set(context, it)")).findAll(src("ui/v3/settings/sections/OpsToggleScreensAndroid.kt")).count())
    assertTrue(src("ui/screens/SettingsLayout.kt").contains("SIMPLE_ASK -> ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.settingsTitle"))
    //  پاسخ فقط خواندن است: نه دفتر می‌نویسد، نه به شبکه می‌رود
    for (rel in listOf("data/SimpleAskEngine.kt", "ui/screens/SimpleAsk.kt")) {
      val s = src(rel)
      for (x in listOf("store.save", "store.update", "Backend.", "http", "SalesEngine.record")) assertFalse("$rel ⇒ $x", s.contains(x))
    }
  }
}
