package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.report.QuarterTax
import ir.vil3ntec.tohid.ui.v3.analytics.SheetModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 *  LOC-06 — تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳ (#۲۴): سرستون و نامِ برگه‌های اکسل/CSV **به زبانِ برنامه**.
 *
 *  ۱) فارسی بایت‌به‌بایت همان پیش از این کار: اثرِ SHA-256 ِ همهٔ برگه‌ها (نام + متنِ CSV) روی همین دفتر با کدِ پیشین
 *     (`claude/e-rows-57x` با کدِ LOC-06 برگردانده، پیش از `StrSheets`) گرفته شد و این‌جا ثابت است.
 *  ۲) انگلیسی و پشتو: هیچ سرستون، نامِ برگه یا خانهٔ ثابتی حرفِ فارسی ندارد (انگلیسی) و همه از فارسی جدا‌اند.
 *  ۳) جمع‌پذیریِ هر ستون در صفحهٔ «گزارش‌ها» (`SheetModel`) در هر چهار زبان یکی است.
 *  ۴) برگهٔ «محصولات» به هر زبان دوباره وارد می‌شود (همان ستون‌ها شناخته می‌شوند).
 *  ⛔ فقط راه‌هایی صدا زده می‌شوند که پیش از این کار هم بودند (تا همین پرونده روی کدِ پیشین بدود).
 */
class SheetLangTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private val today = "2026-10-20"
  private val from = "2026-09-01"
  private val to = "2026-10-20"

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "برنج", category = "خوراکی", unit = "کیلو", purchasePrice = 100.0, salePrice = 130.0, minStock = 5.0, maxStock = 50.0, reorderPoint = 8.0),
      Product(id = "p2", name = "روغن", category = "خوراکی", unit = "عدد", purchasePrice = 500.0, salePrice = 560.0, minStock = 20.0),
      Product(id = "p3", name = "صابون", category = "شوینده", unit = "عدد", purchasePrice = 40.0, salePrice = 55.0),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "عمده‌فروشی")),
    debtors = listOf(
      Debtor(id = "c1", name = "احمد", phone = "+93700123456", notes = "دکانِ کنار", createdAt = 10),
      Debtor(id = "c2", name = "احمد کابلی", phone = "+93799555444", phone2 = "0700123456", createdAt = 20),
      Debtor(id = "c3", name = "ولی", phone = "+93788000000", createdAt = 30),
    ),
    transactions = listOf(
      DebtTransaction(id = "t1", debtorId = "c1", type = "give", amount = 300.0, date = "2026-06-01", notes = "فروش نسیه — فاکتور #1", createdAt = 1),
      DebtTransaction(id = "t2", debtorId = "c1", type = "receive", amount = 100.0, date = "2026-09-10", createdAt = 2),
      DebtTransaction(id = "t3", debtorId = "c3", type = "give", amount = 50.0, date = "2026-10-01", createdAt = 3),
    ),
    sales = listOf(
      Sale(id = "a1", invoiceNumber = 1, total = 300.0, finalTotal = 300.0, paidAmount = 0.0, remaining = 300.0, paymentMethod = "credit", debtorId = "c1", date = "2026-09-02", seller = "کریم", createdAt = 100),
      Sale(id = "a2", invoiceNumber = 2, total = 650.0, discount = 10.0, finalTotal = 640.0, paidAmount = 640.0, paymentMethod = "cash", date = "2026-09-20", seller = "کریم", createdAt = 200),
      Sale(id = "a3", invoiceNumber = 3, total = 55.0, finalTotal = 55.0, paidAmount = 55.0, paymentMethod = "card", date = "2026-10-05", status = "cancelled", cancelReason = "اشتباه", seller = "نوید", createdAt = 300),
    ),
    saleItems = listOf(
      SaleItem(id = "i1", saleId = "a1", productId = "p1", name = "برنج", quantity = 2.0, unitPrice = 130.0, totalPrice = 260.0, purchasePrice = 100.0),
      SaleItem(id = "i2", saleId = "a2", productId = "p2", name = "روغن", quantity = 1.0, unitPrice = 560.0, totalPrice = 560.0, purchasePrice = 500.0),
      SaleItem(id = "i3", saleId = "a3", productId = "p3", name = "صابون", quantity = 1.0, unitPrice = 55.0, totalPrice = 55.0, purchasePrice = 40.0),
    ),
    saleReturns = listOf(SaleReturn(id = "r1", saleId = "a2", saleItemId = "i2", productId = "p2", quantity = 1.0, amount = 100.0, date = "2026-09-25")),
    expenses = listOf(Expense(id = "e1", title = "کرایه", category = "دکان", amount = 1000.0, date = "2026-09-30", notes = "ماهِ میزان")),
    warehouseEntries = listOf(WarehouseEntry(id = "w0", productId = "p3", units = 4.0, price = 40.0, date = "2026-08-01", batch = "B7", productionDate = "2026-01-01", expiryDate = "2026-10-25")),
    stockMovements = listOf(
      StockMovement(id = "m1", productId = "p3", type = "damage", qty = -1.0, date = "2026-09-03", notes = "شکست", userId = "کریم", reason = "افتاد", createdAt = 5),
      StockMovement(id = "m2", productId = "gone", type = "transfer_out", qty = -2.0, date = "2026-09-04", createdAt = 6),
      StockMovement(id = "m3", productId = "p1", type = "adjustment", qty = 1.0, date = "2026-09-05", createdAt = 7),
    ),
  )

  private val ledger: ShopData by lazy {
    (PurchaseBook.record(
      base, "s1", listOf(PurchaseBook.Line("p1", 10.0, 120.0, expiryDate = "2027-01-01"), PurchaseBook.Line("p2", 2.0, 550.0)),
      paid = 1500.0, date = "2026-09-15", notes = "", today = "2026-09-15", now = 50, newId = { "x" + (seqn++) },
    ) as PurchaseBook.Result.Ok).data
  }
  private var seqn = 0

  private val sections = listOf(
    "sales", "products", "debtors", "purchases", "expiry", "profit", "expenses", "sellers", "aging", "rfm", "duplicates",
    "reminders", "gaps", "shrink", "insight", "reorder", "stock",
  )

  /** همهٔ برگه‌های اکسل/CSV ِ این کار روی همین دفتر، به زبانِ همین لحظه */
  private fun sheets(): List<ReportCsv.Sheet> {
    val d = ledger
    val c1 = d.debtors.first { it.id == "c1" }
    val inv = PurchaseBook.invoices(d).first()
    val credit = PurchaseBook.Invoice(
      "#9", 9, "s1", "2026-10-02",
      listOf(Purchase(id = "q1", supplierId = "s1", productId = "p1", quantity = -2.0, unit = "کیلو", purchasePrice = 120.0, totalAmount = -240.0, date = "2026-10-02", notes = "برگشت به تأمین‌کننده: خراب")),
    )
    val tax = TaxQuarter.report(d, QuarterTax.Config(rateBp = 1_000), TaxQuarter.initial(today, QuarterTax.Config(rateBp = 1_000))!!)!!
    val rows = StockLedger.exportRows(emptyList()) { it }
    return sections.map { ReportCsv.of(it, d, from, to, today, now = NOW) } +
      ReportCsv.of("sellers", d, from, to, today, lineSeller = true) +
      ReportCsv.statement(d, "c1", today) +
      ReportCsv.stockCount(listOf(WarehouseEngine.CountLine(d.products[0], 12.0, 11.0)), today) +
      ReportCsv.moves(d, d.stockMovements, "حرکات") +
      CustomerEngine.ledgerSheet(d, c1, today) + CustomerEngine.invoicesSheet(d, c1, today) + CustomerEngine.itemsSheet(d, c1, today) +
      PurchaseBook.invoiceSheet(d, inv) + PurchaseBook.invoiceSheet(d, credit) + PurchaseBook.invoicesSheet(d, PurchaseBook.invoices(d), today) +
      PurchaseBook.ledgerSheet(d, d.suppliers[0], today) +
      TaxQuarter.sheet(tax, 1) +
      ReportCsv.Sheet("stock-ledger", listOf(StockLedger.EXPORT_HEADER) + rows) +
      listOf("sales", "profit", "products", "debtors").mapNotNull { ReportDrillSheet.of(it, d, from, to, 0, today = today) }
  }

  private fun text(all: List<ReportCsv.Sheet>): String = all.joinToString("\n\u0000\n") { it.name + "\n" + ReportCsv.text(it) }

  private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

  private fun at(lang: Lang): List<ReportCsv.Sheet> { I18n.source = { lang }; return sheets().also { I18n.source = { Lang.FA } } }

  @Test fun `فارسی بایت‌به‌بایت همان پیش از این کار — اثرِ همهٔ برگه‌ها`() {
    val fa = text(at(Lang.FA))
    assertEquals(PERSIAN_SHA, sha(fa))
  }

  @Test fun `انگلیسی و پشتو — سرستون و نامِ برگه و خانهٔ ثابت به زبانِ برنامه`() {
    val fa = at(Lang.FA)
    val arabic = Regex("[\\u0621-\\u064A\\u066E-\\u06D3\\u06FA-\\u06FF]")
    for (lang in listOf(Lang.EN, Lang.PS)) {
      val x = at(lang)
      assertEquals(fa.size, x.size)
      fa.zip(x).forEach { (a, b) ->
        val head = b.rows.firstOrNull().orEmpty()
        assertEquals(a.rows.firstOrNull().orEmpty().size, head.size)
        if (lang == Lang.EN) head.forEach { h -> assertFalse("$lang: سرستونِ «$h» در «${b.name}» فارسی است", arabic.containsMatchIn(h)) }
        if (head.any { it.isNotEmpty() } && a.rows.first().any { arabic.containsMatchIn(it) }) assertTrue("$lang: سرستونِ «${b.name}» همان فارسی است: $head", head != a.rows.first())
      }
    }
    //  نامِ برگه‌های خودِ `ReportCsv` (نامِ پروندهٔ برگه‌های دیگر از پیش «داده» است)
    val own = listOf("sales", "products", "debtors", "purchases", "expiry", "profit", "expenses", "sellers", "insight", "reorder", "stock")
    for (lang in listOf(Lang.EN, Lang.PS)) for (k in own) {
      I18n.source = { Lang.FA }
      val a = ReportCsv.of(k, ledger, from, to, today).name
      I18n.source = { lang }
      val b = ReportCsv.of(k, ledger, from, to, today).name
      assertTrue("$lang $k: $b", a != b && (b.contains(today) || b.contains(from)))
      if (lang == Lang.EN) assertFalse(b, arabic.containsMatchIn(b))
    }
    //  خانه‌های ثابتِ برگه هم: وضعیتِ فاکتور، نوعِ حرکت، «برد/رسید»، بندِ مالیات
    I18n.source = { Lang.EN }
    val sales = ReportCsv.of("sales", ledger, from, to, today)
    assertEquals(listOf("Recorded", "Recorded", "Cancelled"), sales.rows.drop(1).map { it[8] })
    assertEquals("Credit", sales.rows[1][7])
    assertEquals("Waste", ReportCsv.of("stock", ledger, from, to, today).rows.first { it[1] == "صابون" }[2])
    assertEquals("(deleted)", ReportCsv.of("stock", ledger, from, to, today).rows.first { it[2] == "Transfer out" }[1])
  }

  @Test fun `جمع‌پذیریِ ستون‌های صفحهٔ «گزارش‌ها» در هر چهار زبان یکی است`() {
    val fa = at(Lang.FA).map { SheetModel.columns(it).summable }
    for (lang in listOf(Lang.PRS, Lang.EN, Lang.PS)) {
      val x = at(lang)
      fa.zip(x).forEach { (flags, sheet) -> assertEquals("$lang «${sheet.name}» ${sheet.rows.first()}", flags, SheetModel.columns(sheet).summable) }
    }
  }

  @Test fun `برگهٔ «محصولات» به هر زبان دوباره وارد می‌شود — همان ستون‌ها`() {
    for (lang in Lang.entries) {
      I18n.source = { lang }
      val sheet = ReportCsv.of("products", ledger, "", "", today)
      val plan = ProductImport.plan(ShopData(), ReportCsv.text(sheet))
      assertFalse("$lang: ستونِ نام شناخته نشد ${sheet.rows.first()}", plan.missingName)
      assertEquals(lang.code, ledger.products.map { it.name }, plan.drafts.map { it.name })
      assertEquals(lang.code, ledger.products.map { it.purchasePrice }, plan.drafts.map { it.purchasePrice })
      assertEquals(lang.code, ledger.products.map { it.minStock }, plan.drafts.map { it.minStock })
    }
  }

  private companion object {
    /** «اکنون» ِ ثابتِ برگهٔ «یادآوری» (روزهای بدهی از زمانِ ثبت؛ بی آن اثرِ انگشت هر روز عوض می‌شد) — همان لحظه‌ای که PERSIAN_SHA گرفته شد */
    const val NOW = 1_794_657_600_000L   //  2026-11-14T12:00:00Z
    /** اثرِ برگه‌های فارسی با کدِ پیشین (همین شاخه بی LOC-06) — نگاه کنید به سرِ پرونده */
    const val PERSIAN_SHA = "ea86b342ec5ec79600dfa480b4581d2c38419aa8cdacaf3b367b26ae5037a227"
  }
}
