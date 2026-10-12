package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  انقضا و FEFO — ماندهٔ هر محموله همان است که اگر اول زودتر-منقضی‌ها
 *  فروخته شده باشند می‌ماند. همان `expiryLots`ِ نسخهٔ وب.
 */
class ExpiryEngineTest {

  private var seq = 0
  private fun newId(): String = "e${seq++}"

  private val milk = Product(id = "p1", name = "شیر", unit = "عدد", purchasePrice = 50.0, salePrice = 70.0)

  private fun ledger(vararg entries: WarehouseEntry) = ShopData(products = listOf(milk), warehouseEntries = entries.toList())

  @Test
  fun `تاریخ درست و نادرست`() {
    assertTrue(ExpiryEngine.validDate(""))
    assertTrue(ExpiryEngine.validDate("2024-02-29"))
    assertFalse(ExpiryEngine.validDate("2023-02-29"))
    assertFalse(ExpiryEngine.validDate("2026-13-01"))
    assertFalse(ExpiryEngine.validDate("1405/07/20"))
  }

  @Test
  fun `روزهای مانده از مرز ماه و سال کبیسه می گذرد`() {
    assertEquals(1L, ExpiryEngine.daysLeft("2024-03-01", "2024-02-29"))
    assertEquals(-1L, ExpiryEngine.daysLeft("2026-10-11", "2026-10-12"))
    assertEquals(366L, ExpiryEngine.daysLeft("2025-01-01", "2024-01-01"))
  }

  @Test
  fun `فروش اول از محموله ی زودتر منقضی کم می شود`() {
    val d = ledger(
      WarehouseEntry(id = "a", productId = "p1", units = 10.0, expiryDate = "2026-10-20"),
      WarehouseEntry(id = "b", productId = "p1", units = 10.0, expiryDate = "2026-12-01"),
    )
    // ۱۲ تا فروخته شد ⇒ «a» تمام، از «b» هشت مانده
    val sold = (SalesEngine.record(
      d, listOf(SalesEngine.CartLine("p1", 12.0)), SalesEngine.Checkout(), "2026-10-12", 0, ::newId,
    ) as SalesEngine.Result.Ok).data

    val lots = ExpiryEngine.lots(sold, "2026-10-12")
    assertEquals(1, lots.size)
    assertEquals("b", lots.single().entry.id)
    assertEquals(8.0, lots.single().remaining, 0.0)
  }

  @Test
  fun `محموله ی بی انقضا آخر از همه است و در فهرست نمی آید`() {
    val d = ledger(
      WarehouseEntry(id = "a", productId = "p1", units = 5.0, expiryDate = "2026-10-01"),
      WarehouseEntry(id = "n", productId = "p1", units = 5.0),
    )
    val lots = ExpiryEngine.lots(d, "2026-10-12")
    assertEquals(1, lots.size)
    assertTrue(lots.single().expired)
    assertEquals(5.0, lots.single().remaining, 0.0)
  }

  @Test
  fun `هشدار فقط منقضی و نزدیک به انقضا`() {
    val d = ledger(
      WarehouseEntry(id = "near", productId = "p1", units = 1.0, expiryDate = "2026-10-30"),
      WarehouseEntry(id = "far", productId = "p1", units = 1.0, expiryDate = "2027-06-01"),
    )
    val alerts = ExpiryEngine.alerts(d, "2026-10-12")
    assertEquals(listOf("near"), alerts.map { it.entry.id })
  }
}
