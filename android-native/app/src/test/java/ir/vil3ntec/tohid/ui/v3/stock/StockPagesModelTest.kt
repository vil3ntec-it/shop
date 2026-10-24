package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleReturn
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** صفحهٔ یک کالا، حرکاتِ انبار و ورود از فایل */
class StockPagesModelTest {

  private val rice = Product(id = "rice", name = "برنج", unit = "کیلو", minStock = 1.0)

  @Test
  fun `ورودهای کالا تازه ترین اول با محموله و شش تای اول`() {
    val entries = (1..8).map { WarehouseEntry(id = "e$it", productId = "rice", units = 1.0, createdAt = it.toLong(), expiryDate = if (it == 8) "2026-11-01" else "") }
    val d = ShopData(products = listOf(rice), warehouseEntries = entries + WarehouseEntry(id = "other", productId = "x", units = 5.0))
    val item = StockItemModel.of(d, "rice", "2026-10-20")!!
    assertEquals(8, item.entries.size)
    assertEquals("e8", item.entries.first().entry.id)
    assertEquals(1.0, item.entries.first().lot!!.remaining, 0.0)
    assertNull(item.entries[1].lot)
    assertEquals(6, StockItemModel.shown(item.entries, false).size)
    assertEquals(2, StockItemModel.hiddenCount(item.entries, false))
    assertEquals(0, StockItemModel.hiddenCount(item.entries, true))
    assertEquals(8.0, item.stock, 0.0)
    assertNull(StockItemModel.of(d, "gone", "2026-10-20"))
  }

  @Test
  fun `حرکت ها روزبه روز و مقصد هر کدام`() {
    val d = ShopData(
      products = listOf(rice),
      sales = listOf(Sale(id = "s1")),
      saleReturns = listOf(SaleReturn(id = "r1", saleId = "s1")),
      suppliers = listOf(Supplier(id = "sup", name = "عمده")),
      purchases = listOf(Purchase(id = "pu1", productId = "rice", supplierId = "sup")),
      warehouseEntries = listOf(WarehouseEntry(id = "we1", productId = "rice", units = 3.0, purchaseId = "pu1")),
      stockMovements = listOf(
        StockMovement(id = "m1", productId = "rice", type = "purchase_in", qty = 10.0, date = "2026-10-19", refId = "pu1", createdAt = 1),
        StockMovement(id = "m2", productId = "rice", type = "sale", qty = -2.0, date = "2026-10-20", refId = "s1", createdAt = 2),
        StockMovement(id = "m3", productId = "rice", type = "customer_return", qty = 1.0, date = "2026-10-20", refId = "r1", createdAt = 3),
        StockMovement(id = "m4", productId = "rice", type = "adjustment", qty = -1.0, date = "2026-10-20", refId = "x", createdAt = 4),
        StockMovement(id = "m5", productId = "rice", type = "purchase_in", qty = 3.0, date = "2026-10-18", refId = "we1", createdAt = 0),
        StockMovement(id = "m6", productId = "other", type = "sale", qty = -1.0, date = "2026-10-20", refId = "s1", createdAt = 5),
      ),
    )
    val days = MovementsModel.days(d, "rice")
    assertEquals(listOf("2026-10-20", "2026-10-19", "2026-10-18"), days.map { it.date })
    val today = days[0].moves
    assertEquals(listOf("m4", "m3", "m2"), today.map { it.move.id })
    assertEquals(MoveKind.ADJUSTMENT, today[0].kind)
    assertNull(today[0].link)
    assertEquals("sales/s1", today[1].link)
    assertEquals("sales/s1", today[2].link)
    assertEquals("purchasing/suppliers/sup", days[1].moves[0].link)
    assertEquals("purchasing/suppliers/sup", days[2].moves[0].link)
    assertEquals(MoveKind.OTHER, MoveKind.of("weird"))
  }

  @Test
  fun `دیروز از مرز ماه و سال می گذرد`() {
    assertEquals("2026-09-30", MovementsModel.yesterday("2026-10-01"))
    assertEquals("2025-12-31", MovementsModel.yesterday("2026-01-01"))
    assertEquals("2024-02-29", MovementsModel.yesterday("2024-03-01"))
  }

  @Test
  fun `ورود از فایل خطا یا پیش نمایش`() {
    val d = ShopData(products = listOf(rice))
    assertTrue(ImportModel.read(d, null) is ImportState.Failed)
    assertTrue(ImportModel.read(d, "foo,bar\n1,2") is ImportState.Failed)
    val ready = ImportModel.read(d, "نام محصول,قیمت فروش\nعدس,50\nبرنج,60\n,10") as ImportState.Ready
    assertEquals(listOf("عدس"), ready.plan.drafts.map { it.name })
    assertEquals(2, ready.plan.skipped.size)
    assertEquals(2, ImportModel.shownSkips(ready.plan).size)
    assertEquals(0, ImportModel.hiddenSkips(ready.plan))
    assertEquals(0, ImportModel.hiddenDrafts(ready.plan))
  }
}
