package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.purchase.PurchaseFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PUR-11 — فاکتورِ خرید به ارزِ دیگر روی همان دفتر: بهای تمام‌شدهٔ افغانی (کالا، انبار، میانگینِ موزون)، بدهیِ تأمین‌کننده به همان
 *  ارز با ارزشِ دفتری، پرداخت به همان ارز یا افغانی با نرخِ خودش، اختلافِ نرخ، دخلِ افغانی، گزارش و سندِ کسر. عددها دستی.
 */
class SupplierFxTest {
  private var seq = 0
  private fun newId() = "x${seq++}"
  private val d0 = ShopData(
    products = listOf(
      Product(id = "p1", name = "چای", unit = "بسته", purchasePrice = 700.0),
      Product(id = "p2", name = "شکر", unit = "کیلو", purchasePrice = 60.0),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "دوبی‌تریدرز"), Supplier(id = "s2", name = "بازار")),
  )
  private val usd70 = PurchaseFx.Rate("USD", 7_000, 1)

  private fun ok(r: PurchaseBook.Result) = (r as PurchaseBook.Result.Ok).data
  private fun ok(r: LedgerEngine.Result) = (r as LedgerEngine.Result.Ok).data

  /** خریدِ ۱۰ بستهٔ چای به ۱۰ دالر و ۲۰ کیلو شکر به ۰٫۸۵ دالر با نرخِ ۷۰ (جمع ۱۱۷ دالر = ۸٬۱۹۰ افغانی) */
  private fun usdBill(d: ShopData = d0, paid: Long = 0, key: String? = "k1") = ok(
    PurchaseBook.record(
      d, "s1",
      listOf(PurchaseBook.Line("p1", 10.0, 0.0, fxPrice = 1_000), PurchaseBook.Line("p2", 20.0, 0.0, fxPrice = 85)),
      paid = 0.0, date = "2026-11-10", notes = "", today = "2026-11-10", now = 10, newId = ::newId,
      key = key, fx = usd70, fxPaid = paid,
    ),
  )

  @Test fun `فاکتورِ دالری ⇒ ردیف‌های افغانیِ دقیق، کالا و انبار به معادلِ افغانی، ارز و نرخ روی ردیف`() {
    val d = usdBill()
    val (tea, sugar) = d.purchases.sortedBy { it.productId }
    assertEquals(listOf(7_000.0, 1_190.0), listOf(tea.totalAmount, sugar.totalAmount))
    assertEquals(listOf("USD", "USD"), listOf(tea.currency, sugar.currency))
    assertEquals(listOf(7_000L, 7_000L), listOf(tea.fxRate, sugar.fxRate))
    assertEquals(listOf(10_000L, 1_700L), listOf(tea.fxTotalMinor, sugar.fxTotalMinor))
    assertEquals(listOf(1_000L, 85L), listOf(tea.fxPriceMinor, sugar.fxPriceMinor))
    //  بهای کالا = معادلِ افغانی ÷ مقدار: ۷٬۰۰۰ ÷ ۱۰ = ۷۰۰ و ۱۱۹۰ ÷ ۲۰ = ۵۹٫۵۰
    assertEquals(700.0, d.products.first { it.id == "p1" }.purchasePrice, 0.0)
    assertEquals(59.5, d.products.first { it.id == "p2" }.purchasePrice, 0.0)
    val entry = d.warehouseEntries.first { it.productId == "p2" }
    assertEquals(119_000L, entry.costMinor)
    assertEquals(20.0, ShopStore.stock(d, "p2"), 0.0)
    //  بدهیِ افغانیِ دفتر (همهٔ صفحه‌های پیشین) = ارزشِ دفتری؛ حسابِ دالری ۱۱۷ دالر
    assertEquals(8_190.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    assertEquals(listOf(PurchaseFx.Account("USD", 11_700, 819_000)), SupplierFx.accounts(d, "s1"))
    val inv = PurchaseBook.invoices(d).single()
    assertEquals("USD", inv.currency)
    assertEquals(11_700L, inv.fxTotal)
    assertEquals(usd70, inv.fxRate)
  }

  @Test fun `میانگینِ موزونِ موجودی با خریدِ افغانی و دالری همان معادلِ ثبت‌شده است`() {
    //  ۱۰ بسته به ۶۶۰ افغانی (۶٬۶۰۰) + ۱۰ بسته به ۱۰ دالر با ۷۰ (۷٬۰۰۰) ⇒ میانگینِ ۶۸۰ افغانی
    var d = ok(PurchaseBook.record(d0, "s2", listOf(PurchaseBook.Line("p1", 10.0, 660.0)), 0.0, "2026-11-09", "", "2026-11-09", 5, ::newId, key = "a1"))
    d = usdBill(d)
    val view = StockLedger.replay(d)
    val p = StockLedger.productView(view, "p1")
    assertEquals(20_000L, p.qtyMilli)
    assertEquals(1_360_000L, p.valueMinor)
    assertEquals(68_000L, p.unitCostMinor)
  }

  @Test fun `پرداخت به دالر با نرخِ دیگر ⇒ اختلافِ نرخ ثابت، بدهیِ افغانی و دالری هم‌گام، دخلِ افغانی دست‌نخورده`() {
    var d = usdBill()
    //  ۴۰ دالر به ۷۲: دفتری ۴۰ × ۷۰ = ۲٬۸۰۰، ارزش ۲٬۸۸۰ ⇒ ۸۰ افغانی زیان
    d = ok(SupplierFx.pay(d, "s1", "USD", 4_000, inAfn = false, rate = PurchaseFx.Rate("USD", 7_200, 1), date = "2026-11-12", notes = "", today = "2026-11-12", now = 20, newId = ::newId, key = "pay1"))
    val pay = d.supplierPayments.single()
    assertEquals(2_800.0, pay.amount, 0.0)
    assertEquals(4_000L, pay.fxAmountMinor)
    assertEquals(8_000L, pay.fxDiffMinor)
    assertNull("به دالر داده شد ⇒ پولِ افغانی ندارد", pay.afnPaidMinor)
    assertEquals(0.0, SupplierFx.drawerOut(pay), 0.0)
    assertEquals(PurchaseFx.Account("USD", 7_700, 539_000), SupplierFx.account(d, "s1", "USD"))
    assertEquals(5_390.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    //  ماندهٔ ۷۷ دالر به افغانی با نرخِ ۶۹: «همه» ۵٬۳۱۳ افغانی ⇒ حساب صفر، ۷۷ افغانی سود
    d = ok(SupplierFx.pay(d, "s1", "USD", 531_300, inAfn = true, rate = PurchaseFx.Rate("USD", 6_900, 1), date = "2026-11-13", notes = "", today = "2026-11-13", now = 30, newId = ::newId, key = "pay2"))
    val second = d.supplierPayments.last()
    assertEquals(7_700L, second.fxAmountMinor)
    assertEquals(531_300L, second.afnPaidMinor)
    assertEquals(-7_700L, second.fxDiffMinor)
    assertEquals(5_313.0, SupplierFx.drawerOut(second), 0.0)
    assertEquals(PurchaseFx.Account("USD", 0, 0), SupplierFx.account(d, "s1", "USD"))
    assertEquals(0.0, ShopStore.supplierDebt(d, "s1"), 1e-9)
    //  گزارش: جمعِ اختلاف ۸۰ − ۷۷ = ۳ افغانی زیان؛ خریدِ ارزی ۱۱۷ دالر
    val rep = ReportEngine.purchases(d, "2026-11-01", "2026-11-30")
    assertEquals(300L, rep.fxDiff)
    assertEquals(listOf("USD" to 11_700L), rep.byCurrency)
    assertEquals(8_190.0, rep.total, 0.0)
    //  همان کلید دوباره ⇒ یک پرداخت
    assertTrue(SupplierFx.pay(d, "s1", "USD", 100, false, usd70, "2026-11-13", "", "2026-11-13", 31, ::newId, key = "pay2") is LedgerEngine.Result.Failed)
  }

  @Test fun `پرداختِ هنگامِ خرید به دالر ⇒ بدهیِ هر ردیف دقیق، از دخلِ افغانی نیست`() {
    val d = usdBill(paid = 11_700)
    assertTrue(d.purchases.all { it.debt == 0.0 })
    assertEquals(11_700L, d.purchases.sumOf { it.fxPaidMinor ?: 0L })
    assertEquals(0.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    assertEquals(PurchaseFx.Account("USD", 0, 0), SupplierFx.account(d, "s1", "USD"))
    assertTrue(d.purchases.all { SupplierFx.drawerPaid(it) == 0.0 })
    //  بخشی: ۱۰۰ دالر ⇒ ردیفِ نخست کامل، ماندهٔ ۱۷ دالر روی ردیفِ دوم
    val part = usdBill(paid = 10_000, key = "k2")
    val due = PurchaseBook.open(part).single()
    assertEquals(1_700L, due.fxRemaining)
    assertEquals(1_190.0, due.remaining, 0.0)
  }

  @Test fun `حسابِ افغانی و دالریِ یک تأمین‌کننده جدا صاف می‌شوند`() {
    var d = ok(PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p2", 10.0, 60.0)), 0.0, "2026-11-01", "", "2026-11-01", 1, ::newId, key = "afn"))
    d = usdBill(d)
    //  پرداختِ افغانیِ ۶۰۰ فقط فاکتورِ افغانی را صاف می‌کند؛ فاکتورِ دالری همان ۱۱۷ دالر
    d = ok(LedgerEngine.paySupplier(d, "s1", 600.0, "2026-11-14", "", "2026-11-14", 40, ::newId, key = "p-afn"))
    val open = PurchaseBook.open(d).associateBy { it.invoice.currency }
    assertEquals(0.0, open.getValue(null).remaining, 1e-9)
    assertEquals(11_700L, open.getValue("USD").fxRemaining)
    assertEquals(8_190.0, open.getValue("USD").remaining, 0.0)
    //  پرداختِ پیشین بیت‌به‌بیت همان: از دخلِ افغانی
    assertEquals(600.0, SupplierFx.drawerOut(d.supplierPayments.single()), 0.0)
  }

  @Test fun `دخلِ شیفت — پرداختِ دالری کم نمی‌شود، پرداختِ افغانیِ حسابِ دالری همان پولِ داده‌شده`() {
    var d = usdBill(paid = 1_000)
    d = ok(SupplierFx.pay(d, "s1", "USD", 70_000, inAfn = true, rate = usd70, date = "2026-11-10", notes = "", today = "2026-11-10", now = 50, newId = ::newId, key = "x"))
    d = ok(LedgerEngine.paySupplier(d, "s2", 250.0, "2026-11-10", "", "2026-11-10", 60, ::newId, key = "y"))
    val t = CashShift.totals(d, 0, 100, opening = 0.0)
    assertEquals(700.0 + 250.0, t.suppliers, 0.0)
  }

  @Test fun `سندِ کسرِ کالای دالری به همان ارز و نرخ، بدهیِ دالری کم می‌شود`() {
    var d = usdBill()
    d = ok(PurchaseBook.supplierReturn(d, "s1", "p1", 2.0, 0.0, "خراب", "2026-11-11", "2026-11-11", 70, ::newId, key = "ret", fx = usd70, fxPrice = 1_000))
    val ret = d.purchases.single { it.id == "ret" }
    assertEquals(-1_400.0, ret.totalAmount, 0.0)
    assertEquals(-2_000L, ret.fxTotalMinor)
    assertEquals("USD", ret.currency)
    assertEquals(PurchaseFx.Account("USD", 9_700, 679_000), SupplierFx.account(d, "s1", "USD"))
    assertEquals(8.0, ShopStore.stock(d, "p1"), 0.0)
    assertEquals(usd70, SupplierFx.returnRate(d, "s1", "p1")?.let(SupplierFx::rateOf))
  }

  @Test fun `بی ارز همان خریدِ پیشین — هیچ فیلدِ تازه‌ای نوشته نمی‌شود`() {
    val d = ok(PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 2.0, 700.0)), 0.0, "2026-11-01", "", "2026-11-01", 1, ::newId))
    val p = d.purchases.single()
    assertNull(p.currency); assertNull(p.fxRate); assertNull(p.fxTotalMinor)
    assertTrue(SupplierFx.accounts(d, "s1").isEmpty())
    val json = kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(Purchase.serializer(), p)
    assertTrue(json, "currency" !in json && "fx" !in json)
    val rep = ReportEngine.purchases(d, "2026-11-01", "2026-11-30")
    assertTrue(rep.byCurrency.isEmpty())
    assertEquals(0L, rep.fxDiff)
  }

  @Test fun `فاکتورِ ارزیِ نادرست رد می‌شود و دفتر دست نمی‌خورد`() {
    val bad = PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 1.0, 0.0)), 0.0, "", "", "2026-11-01", 1, ::newId, fx = usd70)
    assertTrue("بی فیِ ارزی", bad is PurchaseBook.Result.Failed)
    val neg = PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 1.0, 0.0, fxPrice = 10)), 0.0, "", "", "2026-11-01", 1, ::newId, fx = usd70, fxPaid = -1)
    assertTrue(neg is PurchaseBook.Result.Failed)
    assertTrue(SupplierFx.pay(d0, "s1", "USD", 100, false, PurchaseFx.Rate("EUR", 7_600, 1), "", "", "2026-11-01", 1, ::newId) is LedgerEngine.Result.Failed)
  }

  @Test fun `صورت‌حساب و برگهٔ خرید ارز و نرخ را نشان می‌دهند`() {
    var d = usdBill()
    d = ok(SupplierFx.pay(d, "s1", "USD", 4_000, false, PurchaseFx.Rate("USD", 7_200, 1), "2026-11-12", "", "2026-11-12", 20, ::newId, key = "p1"))
    val led = PurchaseBook.ledger(d, "s1")
    assertTrue(led.first().text, "117 USD" in led.first().text && "1 USD = 70 AFN" in led.first().text)
    assertTrue(led.last().text, "40 USD" in led.last().text && "1 USD = 72 AFN" in led.last().text)
    val sheet = PurchaseBook.invoiceSheet(d, PurchaseBook.invoices(d).single())
    assertTrue(sheet.rows.any { r -> r.any { "USD" in it } })
    val csv = ReportCsv.of("purchases", d, "2026-11-01", "2026-11-30", "2026-11-30")
    assertTrue(!csv.rows.first().contains("USD") && csv.rows.drop(1).dropLast(1).all { "USD" in it })
    //  ردیفِ پایانی: اختلافِ نرخِ پرداخت‌های بازه (۸۰ افغانی زیان)
    assertEquals(listOf(ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.fxDiffCol, "80"), csv.rows.last().filter { it.isNotEmpty() })
  }

  @Test fun `کلیدِ دکان خاموش ⇒ فاکتورِ تازه فقط افغانی؛ بدهیِ دالریِ پیشین همچنان دیده و پرداخت می‌شود`() {
    val all = listOf("AFN", "USD", "PKR")
    assertEquals(listOf("AFN"), SupplierFx.offered(false, all))
    assertEquals(all, SupplierFx.offered(true, all))
    //  حساب و پرداخت از کلید نمی‌گذرند: ۱۱۷ دالر بدهی، پرداختِ همهٔ آن به افغانی با نرخِ ۷۲ ⇒ صاف، اختلاف ۲۳۴ افغانی زیان
    val d = usdBill()
    assertEquals(PurchaseFx.Account("USD", 11_700, 819_000), SupplierFx.account(d, "s1", "USD"))
    val paid = ok(SupplierFx.pay(d, "s1", "USD", 842_400, inAfn = true, rate = PurchaseFx.Rate("USD", 7_200, 1), date = "2026-11-12", notes = "", today = "2026-11-12", now = 20, newId = ::newId, key = "pay"))
    assertEquals(PurchaseFx.Account("USD", 0, 0), SupplierFx.account(paid, "s1", "USD"))
    assertEquals(23_400L, paid.supplierPayments.single().fxDiffMinor)
    assertEquals(0.0, ShopStore.supplierDebt(paid, "s1"), 0.0)
  }
}
