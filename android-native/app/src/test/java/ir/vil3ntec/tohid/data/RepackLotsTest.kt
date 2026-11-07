package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  INV-21 — سری و انقضا در بسته‌بندیِ دوباره (`Repack` ⇐ `ExpiryEngine.take`): دانه‌های کارتنِ شکسته سری و انقضای همان
 *  محمولهٔ کارتن را می‌گیرند (به FEFO، زودترین اول)، بستهٔ ساخته‌شده زودترین انقضای اجزا را، و خروجیِ بسته‌بندی محموله‌ای است
 *  که `ExpiryEngine.lots` (هشدارِ انقضا، قیمت‌پرسی، «کالای منقضی فروخته نشود») می‌بیند. بی انقضا ⇒ همان یک ردیفِ پیشین.
 */
class RepackLotsTest {

  private var seq = 0
  private val newId: () -> String = { "n${(++seq).toString().padStart(3, '0')}" }
  private val today = "2026-11-04"

  private val base = ShopData(
    products = listOf(
      Product(id = "can", name = "قوطیِ شیر", unit = "عدد", purchasePrice = 50.0),
      Product(id = "ctn", name = "کارتنِ شیر", unit = "کارتن", purchasePrice = 1200.0),
      Product(id = "rib", name = "روبان", unit = "عدد", purchasePrice = 30.0),
      Product(id = "box", name = "جعبهٔ هدیه", unit = "عدد", purchasePrice = 0.0),
    ),
    warehouseEntries = listOf(
      //  دو محمولهٔ کارتن: A زودتر منقضی می‌شود
      WarehouseEntry(id = "wa", productId = "ctn", units = 2.0, price = 1200.0, expiryDate = "2026-12-01", batch = "A1"),
      WarehouseEntry(id = "wb", productId = "ctn", units = 3.0, price = 1200.0, expiryDate = "2027-03-01", batch = "B1"),
      WarehouseEntry(id = "wc", productId = "can", units = 6.0, price = 50.0, expiryDate = "2027-06-01"),
      WarehouseEntry(id = "wr", productId = "rib", units = 4.0, price = 30.0),
    ),
  )

  private fun ok(r: StockPlaces.Result): ShopData = (r as StockPlaces.Result.Ok).data

  @Test fun `شکستنِ کارتن سری و انقضای هر محموله را به دانه‌ها می‌برد، زودترین اول`() {
    val d = ok(Repack.breakPack(base, "op1", "ctn", 3_000, "can", 12_000, null, "", "", today, 500, newId))
    val out = d.warehouseEntries.filter { it.refId == "op1" && it.units > 0 }
    //  ۲ کارتن از A و ۱ از B ⇒ ۲۴ دانه با انقضای A و ۱۲ با انقضای B؛ جمع همان ۳۶
    assertEquals(listOf(Triple(24.0, "2026-12-01", "A1"), Triple(12.0, "2027-03-01", "B1")), out.map { Triple(it.units, it.expiryDate, it.batch) })
    assertEquals(42.0, ShopStore.stock(d, "can"), 0.0)
    assertEquals(2.0, ShopStore.stock(d, "ctn"), 0.0)
    //  بها همان دانه = کارتن ÷ ۱۲ روی هر ردیف
    assertEquals(listOf(100.0, 100.0), out.map { it.price })
    //  انقضای دانه‌ها در محموله‌ها؛ کارتن فقط ۲ تا از B مانده
    val lots = ExpiryEngine.lots(d, today)
    assertEquals(
      listOf(Triple("can", "2026-12-01", 24.0), Triple("can", "2027-03-01", 12.0), Triple("can", "2027-06-01", 6.0)),
      lots.filter { it.product.id == "can" }.map { Triple(it.product.id, it.entry.expiryDate, it.remaining) },
    )
    assertEquals(listOf("2027-03-01" to 2.0), lots.filter { it.product.id == "ctn" }.map { it.entry.expiryDate to it.remaining })
    //  سندِ کار همهٔ ردیف‌ها را دارد
    assertEquals(d.warehouseEntries.filter { it.refId == "op1" }.map { "warehouseEntries/${it.id}" }, d.auditLog.single { it.id == "op1" }.refIds)
  }

  @Test fun `فروشِ دانه‌ها به FEFO از دانه‌های زودتر منقضی‌شونده کم می‌کند`() {
    val d0 = ok(Repack.breakPack(base, "op1", "ctn", 1_000, "can", 12_000, null, "", "", today, 500, newId))
    //  ۸ دانه فروخته شد ⇒ ۱۰ مانده: ۶ ِ دیرتر و ۴ از دانه‌های کارتنِ A
    val d = d0.copy(sales = listOf(Sale(id = "s1")), saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "can", quantity = 8.0)))
    assertEquals(10.0, ShopStore.stock(d, "can"), 0.0)
    assertEquals(
      listOf("2026-12-01" to 4.0, "2027-06-01" to 6.0),
      ExpiryEngine.lots(d, today).filter { it.product.id == "can" }.map { it.entry.expiryDate to it.remaining },
    )
  }

  @Test fun `ساختِ بسته زودترین انقضای اجزا را می‌گیرد`() {
    val parts = listOf(Repack.Part("can", 2_000), Repack.Part("rib", 1_000))
    val d = ok(Repack.build(base, "op2", "box", 2_000, parts, null, "", "", today, 500, newId))
    val out = d.warehouseEntries.single { it.refId == "op2" && it.units > 0 }
    assertEquals("2027-06-01", out.expiryDate)
    assertEquals("", out.batch)
    assertEquals(listOf("2027-06-01" to 2.0), ExpiryEngine.lots(d, today).filter { it.product.id == "box" }.map { it.entry.expiryDate to it.remaining })
    assertEquals(parts, Repack.lastRecipe(d, "box"))
  }

  @Test fun `بی انقضا همان یک ردیفِ پیشین`() {
    val plain = base.copy(warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "ctn", units = 5.0, price = 1200.0)))
    val d = ok(Repack.breakPack(plain, "op3", "ctn", 2_000, "can", 12_000, null, "", "", today, 500, newId))
    val out = d.warehouseEntries.filter { it.refId == "op3" && it.units > 0 }
    assertEquals(listOf(Triple(24.0, "", "")), out.map { Triple(it.units, it.expiryDate, it.batch) })
    //  نیمی از کارتنِ A و موجودیِ بی محموله: انقضادار اول، بقیه بی انقضا
    val mixed = base.copy(warehouseEntries = listOf(
      WarehouseEntry(id = "wa", productId = "ctn", units = 1.0, price = 1200.0, expiryDate = "2026-12-01", batch = "A1"),
      WarehouseEntry(id = "wn", productId = "ctn", units = 2.0, price = 1200.0),
    ))
    assertEquals(
      listOf(ExpiryEngine.Take("2026-12-01", "A1", 1_000), ExpiryEngine.Take("", "", 1_500)),
      ExpiryEngine.take(mixed, "ctn", 2_500),
    )
  }
}
