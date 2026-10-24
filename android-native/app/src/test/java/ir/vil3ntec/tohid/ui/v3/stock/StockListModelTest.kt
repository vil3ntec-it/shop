package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.data.WarehouseEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** فهرستِ انبار: عددها همان موتورند، فیلترها و جست‌وجو و مقصدِ اسکن درست‌اند */
class StockListModelTest {

  private val today = "2026-10-20"

  private val rice = Product(id = "rice", name = "برنج", unit = "کیلو", minStock = 5.0, salePrice = 120.0, category = "خوراکی", barcodes = listOf("626001"))
  private val milk = Product(id = "milk", name = "شیر", unit = "عدد", minStock = 2.0, category = "لبنیات")
  private val soap = Product(id = "soap", name = "صابون", unit = "عدد", minStock = 1.0)

  private val ledger = ShopData(
    products = listOf(rice, milk, soap),
    productCategories = listOf("لبنیات", "خوراکی"),
    warehouseEntries = listOf(
      WarehouseEntry(id = "e1", productId = "rice", cartons = 2.0, perCarton = 10.0, units = 20.0, price = 100.0, expiryDate = "2026-10-25"),
      WarehouseEntry(id = "e2", productId = "milk", units = 3.0, price = 40.0, expiryDate = "2026-10-01"),
      WarehouseEntry(id = "e3", productId = "soap", units = 4.0, price = 10.0),
    ),
    sales = listOf(Sale(id = "s1")),
    saleItems = listOf(
      SaleItem(id = "i1", saleId = "s1", productId = "rice", quantity = 16.0),
      SaleItem(id = "i2", saleId = "s1", productId = "soap", quantity = 4.0),
    ),
  )

  private val overview = StockListModel.overview(ledger, today)
  private fun row(id: String) = overview.rows.first { it.product.id == id }

  @Test
  fun `خلاصه همان موتور است`() {
    assertEquals(WarehouseEngine.summary(ledger), overview.summary)
  }

  @Test
  fun `موجودی و کارتن و حال از دفتر`() {
    assertEquals(4.0, row("rice").stock, 0.0)
    assertEquals(2.0, row("rice").cartons, 0.0)
    assertEquals(StockLevel.LOW, row("rice").level)
    assertEquals(StockLevel.OUT, row("soap").level)
    assertEquals(StockLevel.OK, row("milk").level)
  }

  @Test
  fun `انقضا از هشدارهای موتور`() {
    assertEquals(5L, row("rice").expiresInDays)
    assertFalse(row("rice").expired)
    assertTrue(row("milk").expired)
    assertNull(row("milk").expiresInDays)
    assertNull(row("soap").expiresInDays)
  }

  @Test
  fun `شمار هر فیلتر`() {
    assertEquals(3, overview.counts[StockFilter.ALL])
    assertEquals(1, overview.counts[StockFilter.LOW])
    assertEquals(1, overview.counts[StockFilter.OUT])
    assertEquals(1, overview.counts[StockFilter.EXPIRING])
    assertEquals(1, overview.counts[StockFilter.EXPIRED])
  }

  @Test
  fun `فیلتر و جست وجو در نام و بارکد و دسته`() {
    assertEquals(listOf("soap"), StockListModel.visible(overview.rows, StockFilter.OUT, "").map { it.product.id })
    assertEquals(listOf("rice"), StockListModel.visible(overview.rows, StockFilter.ALL, "6260").map { it.product.id })
    assertEquals(listOf("milk"), StockListModel.visible(overview.rows, StockFilter.ALL, "شیر").map { it.product.id })
    assertEquals(listOf("milk"), StockListModel.visible(overview.rows, StockFilter.ALL, "", "لبنیات").map { it.product.id })
    assertTrue(StockListModel.visible(overview.rows, StockFilter.EXPIRED, "برنج").isEmpty())
  }

  @Test
  fun `کلید فیلتر ناشناس یعنی همه`() {
    assertEquals(StockFilter.EXPIRING, StockFilter.of("expiring"))
    assertEquals(StockFilter.ALL, StockFilter.of(null))
    assertEquals(StockFilter.ALL, StockFilter.of("nope"))
  }

  @Test
  fun `فیلترهای انقضا فقط با چیزی برای نشان دادن`() {
    val none = mapOf(StockFilter.EXPIRING to 0, StockFilter.EXPIRED to 0)
    assertEquals(listOf(StockFilter.ALL, StockFilter.LOW, StockFilter.OUT), StockListModel.shownFilters(none, StockFilter.ALL))
    assertTrue(StockFilter.EXPIRED in StockListModel.shownFilters(none, StockFilter.EXPIRED))
    assertEquals(StockFilter.entries, StockListModel.shownFilters(overview.counts, StockFilter.ALL))
  }

  @Test
  fun `دسته های به کار رفته به ترتیب دکان`() {
    assertEquals(listOf("لبنیات", "خوراکی"), StockListModel.usedCategories(ledger))
  }

  @Test
  fun `اسکن کالای شناخته به ورود و ناشناس به کالای تازه`() {
    assertEquals("stock/entry?product=rice", StockListModel.scanTarget(ledger, " 626001 "))
    assertTrue(StockListModel.scanTarget(ledger, "999").startsWith("products/new?barcode=999"))
  }
}
