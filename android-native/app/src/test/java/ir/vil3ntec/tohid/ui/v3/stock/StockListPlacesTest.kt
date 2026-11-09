package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  INV-01 — «موجودی به تفکیکِ محل» در فهرستِ انبارِ نسخهٔ ۳ (`StockListModel.atPlace`): هر محل فقط کالاهای خودش و موجودیِ همان
 *  محل را نشان می‌دهد، جمعِ محل‌ها همان موجودیِ کالاست، و تنظیمِ خاموش یعنی همان فهرستِ پیشین.
 */
class StockListPlacesTest {
  private val today = "2026-11-09"
  private val back = "انبارِ پشتی"

  private val d = ShopData(
    products = listOf(
      Product(id = "rice", name = "برنج", unit = "کیلو", salePrice = 120.0),
      Product(id = "tea", name = "چای", unit = "بسته", salePrice = 90.0),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "rice", units = 10.0, date = "2026-11-01", createdAt = 1),
      WarehouseEntry(id = "w2", productId = "tea", units = 5.0, date = "2026-11-01", createdAt = 2, locationId = back),
    ),
    //  ۴ کیلو برنج به انبارِ پشتی (جفتِ جمعِ صفر) و فروشِ ۱ کیلو از محلِ اصلی
    stockMovements = listOf(
      StockMovement(id = "m1", productId = "rice", type = "transfer_out", qty = -4.0, refId = "x1", locationId = "", toLocationId = back, createdAt = 3),
      StockMovement(id = "m2", productId = "rice", type = "transfer_in", qty = 4.0, refId = "x1", locationId = back, createdAt = 3),
    ),
    sales = listOf(Sale(id = "s1", date = "2026-11-02", createdAt = 4)),
    saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "rice", quantity = 1.0)),
  )

  @Test
  fun eachPlaceShowsItsOwnStock_andPlacesSumToTheTotal() {
    val o = StockListModel.overview(d, today, places = true)
    assertEquals(listOf("", back), StockListModel.placeChips(o))
    assertEquals(mapOf("rice" to 5.0), StockListModel.atPlace(o, "").associate { it.product.id to it.stock })
    assertEquals(mapOf("rice" to 4.0, "tea" to 5.0), StockListModel.atPlace(o, back).associate { it.product.id to it.stock })
    for (p in d.products) {
      val sum = o.places.sumOf { loc -> StockListModel.atPlace(o, loc).firstOrNull { it.product.id == p.id }?.stock ?: 0.0 }
      assertEquals(p.name, ShopStore.stock(d, p.id), sum, 1e-9)
    }
    //  همهٔ محل‌ها یا محلی که دیگر نیست ⇒ همان فهرست با موجودیِ کل
    assertEquals(o.rows, StockListModel.atPlace(o, null))
    assertEquals(o.rows, StockListModel.atPlace(o, "محلِ پاک‌شده"))
  }

  @Test
  fun settingOff_sameListAsBefore_noPlaceChips() {
    val off = StockListModel.overview(d, today)
    assertTrue(off.places.isEmpty() && off.placeStock.isEmpty())
    assertTrue(StockListModel.placeChips(off).isEmpty())
    assertEquals(off.rows, StockListModel.atPlace(off, back))
    assertEquals(off.rows, StockListModel.overview(d, today, places = true).rows)
  }

  @Test
  fun onlyMainPlace_noChips() {
    val o = StockListModel.overview(d.copy(stockMovements = emptyList(), warehouseEntries = d.warehouseEntries.map { it.copy(locationId = null) }), today, places = true)
    assertTrue(StockListModel.placeChips(o).isEmpty())
  }
}
