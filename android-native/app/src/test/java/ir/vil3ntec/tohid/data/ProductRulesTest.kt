package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  قاعده‌های کالا — نامِ تکراری، واحدِ فروشِ دوم، کالای غیرفعال و سیاستِ
 *  «کالای منقضی فروخته نشود».
 */
class ProductRulesTest {

  private var seq = 0
  private fun newId(): String = "r${seq++}"

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلوگرم", purchasePrice = 200.0, salePrice = 300.0)
  private val base = ShopData(products = listOf(rice), warehouseEntries = listOf(WarehouseEntry(id = "w", productId = "p1", units = 10.0)))

  private fun failed(r: WarehouseEngine.Result) = (r as WarehouseEngine.Result.Failed).message

  @Test
  fun `نام تکراری با فاصله و حروف عربی هم تکراری است`() {
    val msg = failed(WarehouseEngine.addProduct(base, WarehouseEngine.ProductDraft(name = "  برنج ", salePrice = 1.0), 0, ::newId))
    assertTrue(msg, msg.contains("از قبل هست"))
    assertTrue(WarehouseEngine.duplicateName(base, "برنج") != null)
    assertTrue(WarehouseEngine.duplicateName(base, "برنج", exceptId = "p1") == null)
  }

  @Test
  fun `واحد فروش دوم ضریب معتبر می خواهد`() {
    val bad = failed(WarehouseEngine.addProduct(base,
      WarehouseEngine.ProductDraft(name = "عدس", unit = "کیلوگرم", salePrice = 1.0, saleUnit = "گرم", unitFactor = 0.0), 0, ::newId))
    assertTrue(bad, bad.contains("ضریب"))
    val same = failed(WarehouseEngine.addProduct(base,
      WarehouseEngine.ProductDraft(name = "عدس", unit = "کیلوگرم", salePrice = 1.0, saleUnit = "کیلوگرم", unitFactor = 1.0), 0, ::newId))
    assertTrue(same, same.contains("همان واحد"))
    val ok = WarehouseEngine.addProduct(base,
      WarehouseEngine.ProductDraft(name = "عدس", unit = "کیلوگرم", salePrice = 1.0, saleUnit = "گرم", unitFactor = 0.001), 0, ::newId)
    assertTrue(ok is WarehouseEngine.Result.Ok)
  }

  @Test
  fun `۲۵۰ گرم از کالای کیلویی ۰٫۲۵ کیلوگرم است`() {
    val p = rice.copy(saleUnit = "گرم", unitFactor = 0.001)
    assertEquals(0.25, SalesEngine.toStockQty(p, 250.0), 0.0)
    assertEquals(3.0, SalesEngine.toStockQty(rice, 3.0), 0.0)   // واحدِ دوم ندارد
  }

  @Test
  fun `ویرایش بی فیلدهای تازه آن ها را پاک نمی کند`() {
    val withExtra = base.copy(products = listOf(rice.copy(subcategory = "ایرانی", active = false, saleUnit = "گرم", unitFactor = 0.001)))
    val r = WarehouseEngine.editProduct(withExtra, "p1",
      WarehouseEngine.ProductDraft(name = "برنج", unit = "کیلوگرم", purchasePrice = 200.0, salePrice = 310.0),
      "2026-10-12", 0, ::newId) as WarehouseEngine.Result.Ok
    val p = r.data.products.single()
    assertEquals("ایرانی", p.subcategory)
    assertFalse(p.active)
    assertEquals(0.001, p.unitFactor, 0.0)
  }

  @Test
  fun `کالای غیرفعال فروخته نمی شود`() {
    val d = base.copy(products = listOf(rice.copy(active = false)))
    val r = SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 1.0)), SalesEngine.Checkout(), "2026-10-12", 0, ::newId)
    assertTrue(r is SalesEngine.Result.Failed)
  }

  @Test
  fun `با سیاست ردِ منقضی، فقط مقدار سالم فروخته می شود`() {
    val d = base.copy(warehouseEntries = listOf(
      WarehouseEntry(id = "old", productId = "p1", units = 4.0, expiryDate = "2026-10-01"),
      WarehouseEntry(id = "new", productId = "p1", units = 6.0, expiryDate = "2027-01-01"),
    ))
    val strict = SalesEngine.Checkout(blockExpired = true)
    assertTrue(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 7.0)), strict, "2026-10-12", 0, ::newId) is SalesEngine.Result.Failed)
    assertTrue(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 6.0)), strict, "2026-10-12", 0, ::newId) is SalesEngine.Result.Ok)
    // بی سیاست، فقط هشدار است و فروش انجام می‌شود
    assertTrue(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 7.0)), SalesEngine.Checkout(), "2026-10-12", 0, ::newId) is SalesEngine.Result.Ok)
  }
}
