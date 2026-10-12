package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** انبارگردانی — اختلاف‌ها اصلاحِ موجودی با دلیل می‌شوند؛ شمرده‌نشده دست نمی‌خورد */
class StockCountTest {

  private var seq = 0
  private fun newId(): String = "id${seq++}"

  private val d = ShopData(
    products = listOf(
      Product(id = "rice", name = "برنج", unit = "کیلوگرم", purchasePrice = 100.0),
      Product(id = "soap", name = "صابون", unit = "عدد", purchasePrice = 20.0),
      Product(id = "tea", name = "چای", unit = "بسته", purchasePrice = 50.0),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "rice", units = 10.0),
      WarehouseEntry(id = "w2", productId = "soap", units = 5.0),
      WarehouseEntry(id = "w3", productId = "tea", units = 3.0),
    ),
  )

  @Test
  fun `فقط اختلاف ها ثبت می شوند و موجودی برابر شمارش می شود`() {
    val counted = mapOf("rice" to 9.5, "soap" to 5.0, "tea" to 4.0)
    val lines = WarehouseEngine.countLines(d, counted)
    assertEquals(listOf("rice", "tea"), lines.map { it.product.id })
    assertEquals(-50.0 + 50.0, lines.sumOf { it.value }, 1e-9)

    val out = (WarehouseEngine.stockCount(d, counted, "قفسه ۱", "2026-10-12", 1L, ::newId) as WarehouseEngine.Result.Ok).data
    assertEquals(9.5, ShopStore.stock(out, "rice"), 1e-9)
    assertEquals(5.0, ShopStore.stock(out, "soap"), 1e-9)
    assertEquals(4.0, ShopStore.stock(out, "tea"), 1e-9)
    assertEquals(2, out.stockMovements.size)
    assertTrue(out.warehouseEntries.filter { it.isAdjustment }.all { it.notes == "انبارگردانی — قفسه ۱" })
  }

  @Test
  fun `بی اختلاف و شمارش منفی رد می شوند`() {
    assertTrue(WarehouseEngine.stockCount(d, mapOf("soap" to 5.0), "", "2026-10-12", 1L, ::newId) is WarehouseEngine.Result.Failed)
    assertTrue(WarehouseEngine.stockCount(d, mapOf("soap" to -1.0), "", "2026-10-12", 1L, ::newId) is WarehouseEngine.Result.Failed)
  }

  @Test
  fun `تاریخ تولید بعد از انقضا رد می شود`() {
    val bad = WarehouseEngine.EntryDraft(productId = "rice", units = 1.0, price = 1.0, expiryDate = "2026-01-01", productionDate = "2026-02-01")
    assertTrue(WarehouseEngine.addEntry(d, bad, "2026-10-12", 1L, ::newId) is WarehouseEngine.Result.Failed)
    val ok = bad.copy(productionDate = "2025-12-01")
    val out = (WarehouseEngine.addEntry(d, ok, "2026-10-12", 1L, ::newId) as WarehouseEngine.Result.Ok).data
    assertEquals("2025-12-01", out.warehouseEntries.last().productionDate)
  }
}
