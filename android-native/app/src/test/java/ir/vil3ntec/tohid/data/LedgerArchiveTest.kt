package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  فشرده کردنِ دفتر.
 *
 *  خطِ سرخِ این کار یک چیز است: **هیچ ردیفی که کارِ امروزِ دکان به آن
 *  بند است نباید تکان بخورد.** فاکتور، کالا، قرض‌دار و موجودی سرِ
 *  جایشان می‌مانند و فقط سابقهٔ کهنه کنار می‌رود.
 */
class LedgerArchiveTest {

  /** یک لحظهٔ ثابت، تا آزمون با گذشتِ زمان نشکند */
  private val now = 1_800_000_000_000L
  private val day = 24L * 60L * 60L * 1000L

  private fun move(id: String, at: Long) =
    StockMovement(id = id, productId = "p1", type = "sale", qty = -1.0, createdAt = at)

  private fun audit(id: String, at: Long) =
    AuditEntry(id = id, type = "sale", createdAt = at)

  private fun price(id: String, at: Long) =
    PriceChange(id = id, productId = "p1", oldPrice = 1.0, newPrice = 2.0, createdAt = at)

  private fun ledger() = ShopData(
    products = listOf(Product(id = "p1", name = "برنج")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 10.0)),
    sales = listOf(Sale(id = "s1", invoiceNumber = 1, finalTotal = 100.0, createdAt = now)),
    saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "p1", quantity = 1.0)),
    stockMovements = listOf(
      move("old1", now - 500 * day),
      move("old2", now - 401 * day),
      move("new1", now - 399 * day),
      move("new2", now - day),
      move("noDate", 0),
    ),
    auditLog = listOf(audit("a-old", now - 500 * day), audit("a-new", now)),
    priceHistory = listOf(price("pr-old", now - 500 * day), price("pr-new", now)),
  )

  @Test
  fun `فقط ردیف‌های کهنه‌تر از مرز کنار می‌روند`() {
    val (kept, archive) = LedgerArchive.split(ledger(), now)

    assertEquals(setOf("old1", "old2"), archive.stockMovements.map { it.id }.toSet())
    assertEquals(setOf("new1", "new2", "noDate"), kept.stockMovements.map { it.id }.toSet())
    assertEquals(listOf("a-old"), archive.auditLog.map { it.id })
    assertEquals(listOf("pr-old"), archive.priceHistory.map { it.id })
  }

  @Test
  fun `ردیفِ بی‌تاریخ دست نمی‌خورد`() {
    //  نمی‌دانیم کِی ساخته شده؛ حدس زدن یعنی بایگانی کردنِ چیزی که شاید
    //  امروز ساخته شده باشد
    val (kept, archive) = LedgerArchive.split(ledger(), now)
    assertTrue(kept.stockMovements.any { it.id == "noDate" })
    assertTrue(archive.stockMovements.none { it.id == "noDate" })
  }

  @Test
  fun `فاکتور و کالا و موجودی دست‌نخورده می‌مانند`() {
    val before = ledger()
    val (kept, _) = LedgerArchive.split(before, now)

    assertEquals(before.sales, kept.sales)
    assertEquals(before.saleItems, kept.saleItems)
    assertEquals(before.products, kept.products)
    assertEquals(before.warehouseEntries, kept.warehouseEntries)
    assertEquals(before.debtors, kept.debtors)
    assertEquals(before.transactions, kept.transactions)
    //  و همان عددِ موجودی
    assertEquals(ShopStore.stock(before, "p1"), ShopStore.stock(kept, "p1"), 0.001)
  }

  @Test
  fun `شمارش پیش از انجام، با آنچه واقعاً کنار می‌رود یکی است`() {
    val d = ledger()
    val plan = LedgerArchive.plan(d, now)
    val (_, archive) = LedgerArchive.split(d, now)

    assertEquals(archive.stockMovements.size, plan.movements)
    assertEquals(archive.auditLog.size, plan.audits)
    assertEquals(archive.priceHistory.size, plan.prices)
    assertEquals(4, plan.total)
  }

  @Test
  fun `دفترِ تازه چیزی برای بایگانی ندارد`() {
    val fresh = ShopData(stockMovements = listOf(move("m", now)))
    assertEquals(0, LedgerArchive.plan(fresh, now).total)
  }
}
