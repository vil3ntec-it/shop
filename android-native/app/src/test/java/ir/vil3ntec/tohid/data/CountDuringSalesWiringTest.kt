package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** INV-10 روی دفترِ دکان (S20): ۲۰ شمرده، سپس صندوق ۳ فروخت ⇒ نه کسریِ ساختگی، نه اضافهٔ ساختگی */
class CountDuringSalesWiringTest {
  private var seq = 0
  private fun id() = "n${seq++}"

  private val d = ShopData(
    products = listOf(Product(id = "x", name = "نوشابه", unit = "عدد", purchasePrice = 40.0)),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "x", units = 20.0, price = 40.0, date = "2026-10-26", createdAt = 100)),
    sales = listOf(Sale(id = "s1", date = "2026-10-26", status = "completed", createdAt = 2_000)),
    saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "x", quantity = 3.0)),
    stockMovements = listOf(
      StockMovement(id = "m0", productId = "x", type = "transfer_out", qty = -5.0, createdAt = 1_500),
      StockMovement(id = "m1", productId = "x", type = "sale", qty = -3.0, createdAt = 2_000, refId = "s1"),
    ),
  )

  @Test fun saleDuringCountMakesNoDifference() {
    assertEquals(17.0, ShopStore.stock(d, "x"), 0.0)
    //  انتقالِ ساعتِ ۱۵۰۰ جمع را عوض نمی‌کند و بیرون است؛ فقط فروشِ ساعتِ ۲۰۰۰
    val shift = CountShift.of(d, mapOf("x" to 1_000L))
    assertEquals(mapOf("x" to -3.0), shift)
    assertTrue(WarehouseEngine.countLines(d, mapOf("x" to 20.0), shift).isEmpty())
    val r = WarehouseEngine.stockCount(d, mapOf("x" to 20.0), "", "2026-10-26", 3_000, ::id, shift)
    assertTrue(r is WarehouseEngine.Result.Failed)
  }

  @Test fun withoutInstantTheSaleLooksLikeASurplus() {
    val lines = WarehouseEngine.countLines(d, mapOf("x" to 20.0))
    assertEquals(3.0, lines.single().diff, 1e-9)
    assertEquals(emptyMap<String, Double>(), CountShift.of(d, emptyMap()))
  }

  @Test fun realShortageIsOnlyTheMissingUnit() {
    val shift = CountShift.of(d, mapOf("x" to 1_000L))
    val lines = WarehouseEngine.countLines(d, mapOf("x" to 19.0), shift)
    assertEquals(20.0, lines.single().system, 1e-9)
    assertEquals(-1.0, lines.single().diff, 1e-9)
    val r = WarehouseEngine.stockCount(d, mapOf("x" to 19.0), "", "2026-10-26", 3_000, ::id, shift) as WarehouseEngine.Result.Ok
    assertEquals(16.0, ShopStore.stock(r.data, "x"), 1e-9)
  }
}
