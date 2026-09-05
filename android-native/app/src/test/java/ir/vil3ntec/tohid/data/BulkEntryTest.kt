package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ثبتِ دسته‌جمعی — «یا همه یا هیچ‌کدام».
 *
 *  این تست‌ها سرِ همان چیزی هستند که اگر بشکند، کاربر نمی‌فهمد: ثبتِ
 *  نیمه‌کاره. اگر ردیفِ سوم رد شود و دو ردیفِ اول ثبت شده باشند، کاربر
 *  دوباره می‌زند و آن دو تا دو بار در دفتر می‌نشینند.
 */
class BulkEntryTest {

  private var seq = 0
  private fun newId(): String = "b${seq++}"

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0,
        barcodes = listOf("111")),
    ),
  )

  private fun ok(r: WarehouseEngine.Result): WarehouseEngine.Result.Ok {
    assertTrue("انتظار می‌رفت ثبت شود، ولی: $r", r is WarehouseEngine.Result.Ok)
    return r as WarehouseEngine.Result.Ok
  }

  private fun failed(r: WarehouseEngine.Result): String {
    assertTrue("انتظار می‌رفت رد شود", r is WarehouseEngine.Result.Failed)
    return (r as WarehouseEngine.Result.Failed).message
  }

  /* -------------------------- چند کالا یک‌جا -------------------------- */

  private fun product(name: String, barcode: String = "") = WarehouseEngine.ProductDraft(
    name = name, category = "خوراکی", unit = "عدد",
    purchasePrice = 10.0, salePrice = 15.0, barcode = barcode,
  )

  @Test
  fun `سه کالا با هم ثبت می شوند`() {
    val r = ok(WarehouseEngine.addProducts(
      base, listOf(product("چای"), product("قند"), product("روغن")), 1L, ::newId,
    ))
    assertEquals(4, r.data.products.size)
    assertTrue(r.data.products.any { it.name == "روغن" })
  }

  @Test
  fun `ردیف خراب، کلِ دسته را رد می کند`() {
    val r = failed(WarehouseEngine.addProducts(
      base,
      listOf(product("چای"), product(""), product("روغن")),
      1L, ::newId,
    ))
    assertTrue(r, r.startsWith("ردیف 2"))
  }

  @Test
  fun `بارکد تکراری داخل خودِ دسته هم گرفته می شود`() {
    val r = failed(WarehouseEngine.addProducts(
      base,
      listOf(product("چای", "999"), product("قند", "999")),
      1L, ::newId,
    ))
    assertTrue(r, r.startsWith("ردیف 2"))
  }

  @Test
  fun `بارکد تکراری با کالای قدیمی هم گرفته می شود`() {
    val r = failed(WarehouseEngine.addProducts(base, listOf(product("چای", "111")), 1L, ::newId))
    assertTrue(r, r.startsWith("ردیف 1"))
  }

  @Test
  fun `دستهٔ خالی رد می شود`() {
    failed(WarehouseEngine.addProducts(base, emptyList(), 1L, ::newId))
  }

  /* ------------------------ چند ورودِ انبار یک‌جا ------------------------ */

  private fun entry(productId: String, units: Double, price: Double = 100.0) =
    WarehouseEngine.BulkEntry(
      WarehouseEngine.EntryDraft(productId = productId, units = units, unit = "کیلو", price = price)
    )

  @Test
  fun `دو ورودِ انبار موجودی را بالا می برند`() {
    val r = ok(WarehouseEngine.addEntries(
      base, listOf(entry("p1", 10.0), entry("p1", 5.0)), "2026-01-01", 1L, ::newId,
    ))
    assertEquals(15.0, ShopStore.stock(r.data, "p1"), 0.001)
    assertEquals(2, r.data.warehouseEntries.size)
    assertEquals(2, r.data.stockMovements.size)
  }

  @Test
  fun `ردیفِ خرابِ ورودی، هیچ موجودی ای اضافه نمی کند`() {
    val r = failed(WarehouseEngine.addEntries(
      base,
      listOf(entry("p1", 10.0), entry("p1", 0.0)),
      "2026-01-01", 1L, ::newId,
    ))
    assertTrue(r, r.startsWith("ردیف 2"))
    // دفترِ اصلی دست‌نخورده مانده
    assertEquals(0.0, ShopStore.stock(base, "p1"), 0.001)
    assertEquals(0, base.warehouseEntries.size)
  }

  @Test
  fun `ردیفی که کالای تازه می سازد، هم کالا هم موجودی را ثبت می کند`() {
    val r = ok(WarehouseEngine.addEntries(
      base,
      listOf(
        WarehouseEngine.BulkEntry(
          entry = WarehouseEngine.EntryDraft(units = 8.0, unit = "عدد", price = 50.0),
          newProduct = product("نبات"),
        )
      ),
      "2026-01-01", 1L, ::newId,
    ))
    val fresh = r.data.products.find { it.name == "نبات" }!!
    assertEquals(8.0, ShopStore.stock(r.data, fresh.id), 0.001)
    assertEquals(1, r.data.warehouseEntries.size)
    assertEquals(fresh.id, r.data.warehouseEntries.first().productId)
  }

  @Test
  fun `کالای تازهٔ بی نام، کلِ دسته را رد می کند`() {
    val r = failed(WarehouseEngine.addEntries(
      base,
      listOf(
        entry("p1", 3.0),
        WarehouseEngine.BulkEntry(
          entry = WarehouseEngine.EntryDraft(units = 8.0, unit = "عدد", price = 50.0),
          newProduct = product(""),
        ),
      ),
      "2026-01-01", 1L, ::newId,
    ))
    assertTrue(r, r.startsWith("ردیف 2"))
    assertEquals(1, base.products.size)
  }
}
