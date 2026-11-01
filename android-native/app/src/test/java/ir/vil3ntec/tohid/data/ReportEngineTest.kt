package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  گزارشِ سود.
 *
 *  اثرِ مرجوعی روی سود سه حالتِ جدا دارد و هر سه در نسخهٔ وب با دقت
 *  نوشته شده بود — چون اشتباهش سودِ ماه را دو بار جریمه می‌کند یا اصلاً
 *  حساب نمی‌کند، و هیچ‌کدام روی صفحه به چشم نمی‌آید. هر سه حالت اینجا
 *  سنجیده می‌شود.
 */
class ReportEngineTest {

  private var seq = 0
  private fun newId(): String = "g${seq++}"

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0)
  private val oil = Product(id = "p2", name = "روغن", unit = "عدد", purchasePrice = 400.0, salePrice = 500.0)

  private fun ledger() = ShopData(
    products = listOf(rice, oil),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 1000.0),
      WarehouseEntry(id = "w2", productId = "p2", units = 1000.0),
    ),
  )

  private fun sell(d: ShopData, productId: String, quantity: Double, date: String, discount: Double = 0.0) =
    (SalesEngine.record(
      d, listOf(SalesEngine.CartLine(productId, quantity)),
      SalesEngine.Checkout(discountType = SalesEngine.DiscountType.AMOUNT, discountValue = discount),
      date, 1_700_000_000_000, ::newId,
    ) as SalesEngine.Result.Ok)

  /* --------------------------- سود پایه --------------------------- */

  @Test
  fun `سود ناخالص از قیمت خرید ثبت شده روی قلم فروش حساب می شود`() {
    val sold = sell(ledger(), "p1", 10.0, "2026-08-10")
    val r = ReportEngine.sales(sold.data, "2026-08-01", "2026-08-31")

    assertEquals(1, r.count)
    assertEquals(3000.0, r.gross, 0.0)         // ۱۰ × ۳۰۰
    assertEquals(0.0, r.discount, 0.0)
    assertEquals(3000.0, r.net, 0.0)
    assertEquals(2000.0, r.cogs, 0.0)          // ۱۰ × ۲۰۰
    assertEquals(1000.0, r.grossProfit, 0.0)
    assertEquals(1000.0, r.netProfit, 0.0)
  }

  @Test
  fun `تخفیف از سود کم می شود`() {
    val sold = sell(ledger(), "p1", 10.0, "2026-08-10", discount = 500.0)
    val r = ReportEngine.sales(sold.data, "2026-08-01", "2026-08-31")
    assertEquals(3000.0, r.gross, 0.0)
    assertEquals(500.0, r.discount, 0.0)
    assertEquals(2500.0, r.net, 0.0)
    assertEquals(500.0, r.grossProfit, 0.0)    // ۲۵۰۰ − ۲۰۰۰
  }

  @Test
  fun `مصارف از سود ناخالص کم می شوند`() {
    var d = sell(ledger(), "p1", 10.0, "2026-08-10").data
    d = (LedgerEngine.addExpense(
      d, LedgerEngine.ExpenseDraft(title = "کرایه", category = "کرایه", amount = 400.0, date = "2026-08-05"),
      "2026-08-28", 0, ::newId,
    ) as LedgerEngine.Result.Ok).data

    val r = ReportEngine.sales(d, "2026-08-01", "2026-08-31")
    assertEquals(1000.0, r.grossProfit, 0.0)
    assertEquals(400.0, r.expenses, 0.0)
    assertEquals(600.0, r.netProfit, 0.0)
  }

  @Test
  fun `فروش خارج از بازه در گزارش نمی آید`() {
    var d = sell(ledger(), "p1", 10.0, "2026-07-20").data
    d = sell(d, "p1", 5.0, "2026-08-10").data
    val r = ReportEngine.sales(d, "2026-08-01", "2026-08-31")
    assertEquals(1, r.count)
    assertEquals(1500.0, r.net, 0.0)
  }

  @Test
  fun `فروش لغوشده در گزارش نمی آید`() {
    val sold = sell(ledger(), "p1", 10.0, "2026-08-10")
    val cancelled = (SalesEngine.cancel(sold.data, sold.saleId, "2026-08-11", 0, ::newId) as SalesEngine.Result.Ok).data
    val r = ReportEngine.sales(cancelled, "2026-08-01", "2026-08-31")
    assertEquals(0, r.count)
    assertEquals(0.0, r.net, 0.0)
    assertEquals(0.0, r.netProfit, 0.0)
  }

  /* ------------------- سه حالتِ اثرِ مرجوعی روی سود ------------------- */

  @Test
  fun `حالت یک — مرجوعیِ فروشِ لغوشده سود را دوبار جریمه نمی کند`() {
    val sold = sell(ledger(), "p1", 10.0, "2026-08-10")
    val itemId = sold.data.saleItems.single().id
    var d = (SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 4.0), "", "2026-08-12", 0, ::newId,
    ) as SalesEngine.Result.Ok).data
    d = (SalesEngine.cancel(d, sold.saleId, "2026-08-13", 0, ::newId) as SalesEngine.Result.Ok).data

    val r = ReportEngine.sales(d, "2026-08-01", "2026-08-31")
    // فروش اصلاً شمرده نمی‌شود، پس مرجوعی‌اش هم نباید جایی کم شود
    assertEquals(0.0, r.returnAmount, 0.0)
    assertEquals(0.0, r.netProfit, 0.0)
  }

  @Test
  fun `حالت دو — مرجوعیِ فروشِ همان بازه، کل مبلغش از سود کم می شود`() {
    val sold = sell(ledger(), "p1", 10.0, "2026-08-10")
    val itemId = sold.data.saleItems.single().id
    val d = (SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 4.0), "", "2026-08-12", 0, ::newId,
    ) as SalesEngine.Result.Ok).data

    val r = ReportEngine.sales(d, "2026-08-01", "2026-08-31")
    assertEquals(3000.0, r.net, 0.0)           // مبلغِ اولیهٔ فاکتور
    assertEquals(2000.0, r.cogs, 0.0)          // کلِ ۱۰ کیلوی فروخته‌شده × ۲۰۰
    assertEquals(1200.0, r.returnAmount, 0.0)  // ۴ × ۳۰۰
    // ۳۰۰۰ − ۲۰۰۰ − (۱۲۰۰ − ۸۰۰) = ۶۰۰ ، یعنی سودِ همان ۶ کیلو
    assertEquals(600.0, r.grossProfit, 0.0)
    assertEquals(6 * (300.0 - 200.0), r.grossProfit, 0.0)
  }

  @Test
  fun `حالت سه — مرجوعیِ فروشِ بازه ی قبل، فقط سودِ از دست رفته کم می شود`() {
    // فروش در ماه قبل، مرجوعی در این ماه
    val sold = sell(ledger(), "p1", 10.0, "2026-07-20")
    val itemId = sold.data.saleItems.single().id
    var d = (SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 4.0), "", "2026-08-12", 0, ::newId,
    ) as SalesEngine.Result.Ok).data
    // و یک فروشِ تازه در همین ماه
    d = sell(d, "p2", 10.0, "2026-08-15").data

    val r = ReportEngine.sales(d, "2026-08-01", "2026-08-31")
    assertEquals(5000.0, r.net, 0.0)           // فقط فروشِ روغن
    assertEquals(4000.0, r.cogs, 0.0)
    assertEquals(1200.0, r.returnAmount, 0.0)
    // مرجوعیِ ماهِ قبل: ۱۲۰۰ مبلغ − ۸۰۰ بهای تمام‌شده = ۴۰۰ سودِ از‌دست‌رفته
    assertEquals(1000.0 - 400.0, r.grossProfit, 0.0)
  }

  @Test
  fun `مرجوعیِ خارج از بازه اصلاً حساب نمی شود`() {
    val sold = sell(ledger(), "p1", 10.0, "2026-08-10")
    val itemId = sold.data.saleItems.single().id
    val d = (SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 4.0), "", "2026-09-05", 0, ::newId,
    ) as SalesEngine.Result.Ok).data

    val r = ReportEngine.sales(d, "2026-08-01", "2026-08-31")
    assertEquals(0.0, r.returnAmount, 0.0)
    // ماهِ فروش دست نمی‌خورد؛ اثرِ مرجوعی در ماهِ خودش (شهریور) می‌نشیند
    assertEquals(2000.0, r.cogs, 0.0)
    assertEquals(1000.0, r.grossProfit, 0.0)
  }

  @Test
  fun `مرجوعیِ کامل در ماه بعد — جمع ماه ها با کل بازه یکی است و صفر`() {
    val sold = sell(ledger(), "p1", 10.0, "2026-08-10")
    val itemId = sold.data.saleItems.single().id
    val d = (SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 10.0), "", "2026-09-05", 0, ::newId,
    ) as SalesEngine.Result.Ok).data

    val aug = ReportEngine.sales(d, "2026-08-01", "2026-08-31").grossProfit
    val sep = ReportEngine.sales(d, "2026-09-01", "2026-09-30").grossProfit
    val all = ReportEngine.sales(d, "2026-01-01", "2026-12-31").grossProfit
    assertEquals(1000.0, aug, 0.0)
    assertEquals(-1000.0, sep, 0.0)
    assertEquals(0.0, all, 0.0)
    assertEquals(all, aug + sep, 0.0)
  }

  /* ----------------------------- خرید ----------------------------- */

  @Test
  fun `گزارش خرید جمع و بدهی و سهم هر تامین کننده را می دهد`() {
    val d = ledger().copy(
      suppliers = listOf(Supplier(id = "s1", name = "نور"), Supplier(id = "s2", name = "آریا")),
      purchases = listOf(
        Purchase(id = "a", supplierId = "s1", totalAmount = 1000.0, paidAmount = 600.0, debt = 400.0, date = "2026-08-05"),
        Purchase(id = "b", supplierId = "s2", totalAmount = 3000.0, paidAmount = 3000.0, date = "2026-08-20"),
        Purchase(id = "c", supplierId = "s1", totalAmount = 500.0, date = "2026-07-30"),   // بیرونِ بازه
      ),
    )
    val r = ReportEngine.purchases(d, "2026-08-01", "2026-08-31")
    assertEquals(2, r.count)
    assertEquals(4000.0, r.total, 0.0)
    assertEquals(400.0, r.debt, 0.0)
    assertEquals("آریا", r.bySupplier.first().first)
  }

  /* --------------------------- محصولات --------------------------- */

  @Test
  fun `سود هر کالا از قیمت همان فاکتور می آید نه قیمت امروز`() {
    var d = sell(ledger(), "p1", 10.0, "2026-08-10").data
    // قیمت‌ها بعد از فروش عوض می‌شوند
    d = d.copy(products = d.products.map {
      if (it.id == "p1") it.copy(salePrice = 900.0, purchasePrice = 50.0) else it
    })

    val (quantity, profit) = ReportEngine.productStat(d, "p1")
    assertEquals(10.0, quantity, 0.0)
    assertEquals(1000.0, profit, 0.0)   // ۱۰ × (۳۰۰ − ۲۰۰)، نه قیمت‌های تازه
  }

  @Test
  fun `مرجوعی از فروش و سود کالا کم می شود`() {
    val sold = sell(ledger(), "p1", 10.0, "2026-08-10")
    val itemId = sold.data.saleItems.single().id
    val d = (SalesEngine.recordReturn(
      sold.data, sold.saleId, mapOf(itemId to 4.0), "", "2026-08-12", 0, ::newId,
    ) as SalesEngine.Result.Ok).data

    val (quantity, profit) = ReportEngine.productStat(d, "p1")
    assertEquals(6.0, quantity, 0.0)
    assertEquals(600.0, profit, 0.0)
  }

  @Test
  fun `پرفروش ها و کم فروش ها درست مرتب می شوند`() {
    var d = sell(ledger(), "p1", 3.0, "2026-08-10").data
    d = sell(d, "p2", 20.0, "2026-08-10").data

    val report = ReportEngine.products(d)
    assertEquals("روغن", report.topSelling.first().product.name)
    // «کم‌فروش» فقط بین آن‌هایی که فروش داشته‌اند
    assertEquals("برنج", report.slowest.first().product.name)
    assertTrue(report.slowest.all { it.quantity > 0 })
  }

  @Test
  fun `ارزش موجودی به بهای خرید حساب می شود نه فروش`() {
    val d = sell(ledger(), "p1", 100.0, "2026-08-10").data
    // ۹۰۰ کیلو برنج × ۲۰۰ + ۱۰۰۰ روغن × ۴۰۰
    assertEquals(900 * 200.0 + 1000 * 400.0, ReportEngine.products(d).inventoryValue, 0.0)
  }

  /* --------------------- تحلیلِ موجودی (INV-15، INV-16) --------------------- */

  @Test
  fun `تحلیل موجودی ارزش به بها و به فروش و سود بالقوه را از core می گیرد`() {
    val d = sell(ledger(), "p1", 100.0, "2026-08-10").data
    val s = ReportEngine.stockInsight(d, "2026-08-01", "2026-08-31")
    //  ۹۰۰ برنج و ۱۰۰۰ روغن، به پولِ خرد (صدمِ افغانی)
    assertEquals((900 * 200 + 1000 * 400) * 100L, s.valuation.costMinor)
    assertEquals((900 * 300 + 1000 * 500) * 100L, s.valuation.retailMinor)
    assertEquals((900 * 100 + 1000 * 100) * 100L, s.valuation.potentialProfitMinor)
    assertTrue(s.valuation.complete)
    //  جدولِ کامپیوتر/اکسل همان عددها را ردیف به ردیف دارد
    val sheet = ReportCsv.of("insight", d, "2026-08-01", "2026-08-31", "2026-08-31")
    assertEquals("ردهٔ ABC", sheet.rows[0][3])
    assertEquals(listOf("برنج", "A", "30000", "100.0٪", "180000", "270000"), sheet.rows[1].let { listOf(it[1], it[3], it[4], it[5], it[8], it[9]) })
    assertEquals(listOf("روغن", "C", "0", "400000", "500000"), sheet.rows[2].let { listOf(it[1], it[3], it[4], it[8], it[9]) })
  }

  @Test
  fun `تحلیل ABC فقط فروش خالص همان بازه را می شمارد و لغوشده را نه`() {
    var d = sell(ledger(), "p1", 10.0, "2026-08-10").data            // ۳۰۰۰ برنج در بازه
    d = sell(d, "p2", 1.0, "2026-08-11").data                       // ۵۰۰ روغن در بازه
    d = sell(d, "p2", 100.0, "2026-07-01").data                     // بیرون از بازه
    val cancelled = sell(d, "p2", 50.0, "2026-08-12")
    d = cancelled.data.copy(sales = cancelled.data.sales.map { if (it.id == cancelled.saleId) it.copy(status = "cancelled") else it })
    val s = ReportEngine.stockInsight(d, "2026-08-01", "2026-08-31")
    assertEquals(listOf("p1", "p2"), s.rows.map { it.product.id })
    assertEquals(3000.0, s.rows[0].sales, 0.0)
    assertEquals(500.0, s.rows[1].sales, 0.0)
    assertEquals(ir.vil3ntec.tohid.core.stock.StockAnalysis.Abc.A, s.rows[0].abc)  // ۸۶٪ ⇒ A
    assertEquals(ir.vil3ntec.tohid.core.stock.StockAnalysis.Abc.B, s.rows[1].abc)  // پیش از آن ۸۶٪ (زیرِ ۹۵٪) ⇒ B
    assertEquals(3_500L * 100, s.abc.totalMinor)
  }

  @Test
  fun `تحلیل موجودی موجودی منفی و کالای بی قیمت خرید را جدا نشان می دهد`() {
    val noCost = Product(id = "p3", name = "صابون", unit = "عدد", purchasePrice = 0.0, salePrice = 50.0)
    //  روغن: ۱۰۰۰ ورود، همه فروخته، سپس اصلاحِ «−۱» ⇒ موجودیِ منفی (مثلِ فروشِ آفلاینِ بیش از موجودی)
    val d1 = ledger().let {
      it.copy(
        products = it.products + noCost,
        warehouseEntries = it.warehouseEntries + WarehouseEntry(id = "w3", productId = "p3", units = 10.0),
      )
    }
    val sold = sell(d1, "p2", 1000.0, "2026-08-10").data
    val d = sold.copy(warehouseEntries = sold.warehouseEntries + WarehouseEntry(id = "w4", productId = "p2", units = -1.0, isAdjustment = true))
    val s = ReportEngine.stockInsight(d, "2026-08-01", "2026-08-31")
    assertEquals(listOf("p2"), s.valuation.negative)
    assertEquals(listOf("p3"), s.valuation.missingCost)
    assertEquals(listOf("صابون"), s.names(s.valuation.missingCost))
    //  روغنِ منفی در هیچ جمعی نیست؛ صابونِ بی بها فقط در ارزشِ فروش
    assertEquals(1000 * 200 * 100L, s.valuation.costMinor)
    assertEquals((1000 * 300 + 10 * 50) * 100L, s.valuation.retailMinor)
    assertEquals(1000 * 100 * 100L, s.valuation.potentialProfitMinor)
  }

  /* --------------------------- قرض‌داران --------------------------- */

  @Test
  fun `گزارش قرض داران داده و گرفته را جدا نشان می دهد`() {
    val d = ShopData(
      debtors = listOf(Debtor(id = "d1", name = "احمد")),
      transactions = listOf(
        DebtTransaction(id = "t1", debtorId = "d1", type = "give", amount = 5000.0),
        DebtTransaction(id = "t2", debtorId = "d1", type = "receive", amount = 2000.0),
        DebtTransaction(id = "t3", debtorId = "d1", type = "give", amount = 1000.0),
      ),
    )
    val row = ReportEngine.debtors(d).single()
    assertEquals(6000.0, row.given, 0.0)
    assertEquals(2000.0, row.received, 0.0)
    assertEquals(4000.0, row.remaining, 0.0)
  }

  /* --------------------------- بازه‌ها --------------------------- */

  @Test
  fun `بازه های آماده درست حساب می شوند`() {
    assertEquals("2026-08-28" to "2026-08-28", ReportEngine.rangeOf(ReportEngine.Range.TODAY, "2026-08-28"))
    assertEquals("2026-08-22" to "2026-08-28", ReportEngine.rangeOf(ReportEngine.Range.WEEK, "2026-08-28"))
    //  ماه و سالِ خورشیدی — میزان ۳۰ روز، حوتِ ۱۴۰۳ (کبیسه) ۳۰ روز
    assertEquals("2026-09-23" to "2026-10-22", ReportEngine.rangeOf(ReportEngine.Range.JMONTH, "2026-10-12", 1405, 7))
    assertEquals("2025-02-19" to "2025-03-20", ReportEngine.jalaliMonth(1403, 12))
    assertEquals("2026-03-21" to "2027-03-20", ReportEngine.rangeOf(ReportEngine.Range.JYEAR, "2026-10-12", 1405))
    assertEquals("2026-08-01" to "2026-08-28", ReportEngine.rangeOf(ReportEngine.Range.MONTH, "2026-08-28"))
    // هفته‌ای که از مرزِ ماه رد می‌شود
    assertEquals("2026-07-29" to "2026-08-04", ReportEngine.rangeOf(ReportEngine.Range.WEEK, "2026-08-04"))
  }

  @Test
  fun `گردش موجودی تازه ترین را اول می آورد`() {
    var d = sell(ledger(), "p1", 2.0, "2026-08-10").data
    d = d.copy(stockMovements = d.stockMovements.map { it.copy(createdAt = 100) } +
      StockMovement(id = "m9", productId = "p1", type = "adjustment", qty = -1.0, createdAt = 999))

    val rows = ReportEngine.stockLedger(d, "p1")
    assertEquals("m9", rows.first().id)
    assertEquals(2, rows.size)
    assertTrue(ReportEngine.stockLedger(d, "p2").isEmpty())
  }
}
