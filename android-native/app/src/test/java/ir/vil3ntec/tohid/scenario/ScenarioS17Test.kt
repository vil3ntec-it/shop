package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.PriceCheckData
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S17 — قیمت‌پرسی (POS-15): Ctrl+I و اسکن ⇒ `PriceCheckData.of` (همان دادهٔ پنجرهٔ `PriceCheckDialog`). در کامپیوتر پشتِ
 *  تنظیمِ خاموشِ همین دستگاه (`PosPriceCheckPrefs`).
 *
 *  ماندهٔ سناریو (pending، نه سرخ):
 *   • «موجودیِ انبارها»: دستگاه یک عددِ موجودی دارد و موجودیِ هر انبار جدا نشان داده نمی‌شود.
 *   • «Esc فوکوس را به کادرِ اسکن برمی‌گرداند» رفتارِ صفحه است (`DeskSale`) و آزمونِ خودکار ندارد.
 */
class ScenarioS17Test {

  private val milk = Product(
    id = "milk", name = "شیر", unit = "دانه", salePrice = 50.0, wholesalePrice = 45.0, purchasePrice = 40.0,
    barcodes = listOf("6260000000200"),
  )
  private val d = ShopData(
    products = listOf(milk),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "milk", units = 4.0, date = "2026-10-01", expiryDate = "2026-11-12"),
      WarehouseEntry(id = "w2", productId = "milk", units = 6.0, date = "2026-10-20", expiryDate = "2026-12-20"),
    ),
  )

  @Test
  fun `S17 — قیمت‌ها، موجودی و نزدیک‌ترین انقضا؛ فاکتور دست نمی‌خورد`() {
    //  اسکن در پنجرهٔ قیمت‌پرسی همان بارکدِ کامل را می‌شناسد
    val hit = BarcodeMatch.find("6260000000200", ShopStore.barcodeIndex(d)) as BarcodeMatch.Hit.Product
    val v = PriceCheckData.of(d, d.products.single { it.id == hit.id }, "2026-11-02")
    assertEquals(50.0, v.product.salePrice, 0.0)
    assertEquals(45.0, v.product.wholesalePrice, 0.0)
    assertEquals(10.0, v.stock, 0.0)
    assertEquals("2026-11-12", v.card.nearest?.expiry)
    assertEquals(10, v.card.nearestDays)
    assertTrue(v.card.near)
    //  فاکتور دست نمی‌خورد: `PriceCheckData.of` سبد نمی‌گیرد و دفتر را فقط می‌خواند (چیزی برنمی‌گرداند که نوشته شود)
  }
}
