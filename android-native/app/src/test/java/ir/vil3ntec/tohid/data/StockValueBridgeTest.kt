package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ۳-۱۶ ِ صاحب‌کار (پرسشِ ۲ الف): «ارزش موجودی» ِ گزارش‌ها و مرکزِ مدیریت (`ReportEngine.stockValue`) = همان «ارزش موجودی» ِ
 *  گزارشِ تازه — دوقلوی `fromLedger` ِ `license/stock-value-core.js`. همان دفتر و همان عددهای دستیِ
 *  `server/test/stock-value-core.test.js` («fromLedger با دست»): ارزش = جمعِ دفترِ میانگینِ موزون (۱ به ۱۰ + ۲ به ۵ ⇒ ۲۰٫۰۰،
 *  نه ۳ × ۶٫۶۷)، پایهٔ نامعلوم ⇒ `purchasePrice` و «تقریبی»، کیت و موجودیِ منفی جدا، غیرفعالِ بی موجودی و کالای پاک‌شده نه.
 */
class StockValueBridgeTest {
  private val t0 = 1_790_000_000_000L
  private fun entry(id: String, productId: String, units: Double, price: Double, adjustment: Boolean = false) =
    WarehouseEntry(id = id, productId = productId, units = units, price = price, date = "2026-10-01",
      createdAt = t0 + id.drop(1).toLong() * 1000, isAdjustment = adjustment)

  private val ledger = ShopData(
    products = listOf(
      Product(id = "A", name = "صابون", salePrice = 9.0, purchasePrice = 5.0),
      Product(id = "B", name = "شکر", salePrice = 4.0, purchasePrice = 5.0),
      Product(id = "C", name = "نمک", salePrice = 0.0, purchasePrice = 3.0),
      Product(id = "N", name = "بی ورود", salePrice = 10.0, purchasePrice = 2.0),
      Product(id = "K", name = "بستهٔ هدیه", salePrice = 30.0, kit = listOf(KitPart("A", 1.0))),
      Product(id = "I", name = "کهنه", salePrice = 5.0, purchasePrice = 1.0, active = false),
      Product(id = "U", name = "شمرده", salePrice = 8.0, purchasePrice = 6.0),
    ),
    warehouseEntries = listOf(
      entry("e1", "A", 1.0, 10.0), entry("e2", "A", 2.0, 5.0),
      entry("e3", "B", 2.0, 5.0),
      entry("e4", "C", 1.0, 3.0),
      //  شمارشِ مثبتِ بی بها ⇒ پایهٔ نامعلوم
      entry("e5", "U", 2.0, 0.0, adjustment = true),
    ),
    sales = listOf(Sale(id = "s1", status = "completed", date = "2026-10-02", createdAt = t0 + 86_400_000)),
    saleItems = listOf(
      SaleItem(id = "i1", saleId = "s1", productId = "N", quantity = 2.0, purchasePrice = 2.0, totalPrice = 20.0),
      SaleItem(id = "i2", saleId = "s1", productId = "K", quantity = 1.0, purchasePrice = 0.0, totalPrice = 30.0),
      SaleItem(id = "i3", saleId = "s1", productId = "I", quantity = 1.0, purchasePrice = 1.0, totalPrice = 5.0),
      //  کالای پاک‌شده: در دفتر هست، در فهرستِ کالاها نه
      SaleItem(id = "i4", saleId = "s1", productId = "X", quantity = 1.0, purchasePrice = 1.0, totalPrice = 5.0),
    ),
  )

  @Test fun `همان عددهای دستیِ هم‌زادِ وب`() {
    val sv = ReportEngine.stockValue(ledger)
    val v = sv.valuation
    //  A ۲۰٫۰۰ + B ۱۰٫۰۰ + C ۳٫۰۰ + U (تقریبی، ۲ × ۶) ۱۲٫۰۰
    assertEquals(2000L + 1000 + 300 + 1200, v.costMinor)
    assertEquals(2700L + 800 + 0 + 1600, v.retailMinor)
    assertEquals(700L - 200 + 400, v.potentialProfitMinor)
    assertEquals(4, v.inStock)
    assertEquals(listOf("N"), v.negative)
    assertEquals(listOf("C"), v.missingPrice)
    assertEquals(listOf("B"), v.belowCost)
    assertTrue("U بی بهای بازپخش ⇒ تقریبی", sv.approximate)
    assertEquals("کیت جدا شمرده می‌شود", 1, sv.kits)
    assertEquals(listOf("A", "B", "C", "U"), v.lines.map { it.id })
  }

  @Test fun `گزارشِ محصولات و مرکزِ مدیریت همان عدد را می‌گیرند`() {
    val r = ReportEngine.products(ledger)
    assertEquals(45.0, r.inventoryValue, 0.0)
    assertTrue(r.inventoryApprox)
    assertEquals(1, r.inventoryNegative)
    assertEquals(1, r.inventoryKits)
    val n = ir.vil3ntec.tohid.ui.v3.analytics.AdminModel.numbers(ledger, "2026-10-03", t0 + 2 * 86_400_000)
    assertEquals(45.0, n.stockValue, 0.0)
    //  همان دفتر ⇒ همان نتیجه، بی بازپخشِ دوباره
    assertSame(ReportEngine.stockValue(ledger), ReportEngine.stockValue(ledger))
  }

  @Test fun `بی کالای بی بها تقریبی نیست؛ دفترِ خالی صفر`() {
    val exact = ledger.copy(products = ledger.products.filter { it.id != "U" }, warehouseEntries = ledger.warehouseEntries.filter { it.productId != "U" })
    val sv = ReportEngine.stockValue(exact)
    assertFalse(sv.approximate)
    assertEquals(3300L, sv.valuation.costMinor)
    val empty = ReportEngine.stockValue(ShopData())
    assertEquals(0L, empty.valuation.costMinor)
    assertEquals(0, empty.kits)
  }
}
