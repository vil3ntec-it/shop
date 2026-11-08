package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.screens.ProductListModel
import ir.vil3ntec.tohid.ui.v3.products.ProductDetailModel.MoveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** عددهای صفحهٔ کالا و حرکت‌های اخیر */
class ProductDetailModelTest {
  private val rice = Product("r", name = "برنج", unit = "کیلوگرم", purchasePrice = 100.0, salePrice = 130.0, minStock = 5.0)

  @Test
  fun `موجودی، سودِ هر واحد و سودِ موجودی`() {
    val d = ShopData(products = listOf(rice), warehouseEntries = listOf(WarehouseEntry("e1", productId = "r", units = 4.0)))
    val f = ProductDetailModel.figures(d, rice)
    assertEquals(4.0, f.stock, 0.0)
    assertEquals(ProductListModel.Status.LOW, f.status)
    assertEquals(30.0, f.unitProfit, 0.0)
    assertEquals(120.0, f.stockProfit, 0.0)
    assertEquals(0.0, f.sold, 0.0)
    //  موجودیِ صفر یا منفی سودِ موجودی ندارد
    val empty = ProductDetailModel.figures(ShopData(products = listOf(rice)), rice)
    assertEquals(0.0, empty.stockProfit, 0.0)
    assertEquals(ProductListModel.Status.OUT, empty.status)
  }

  @Test
  fun `پنج حرکتِ تازه‌ترِ همین کالا`() {
    val moves = (1..8).map { StockMovement("m$it", productId = "r", type = "sale", qty = -1.0, createdAt = it.toLong()) } +
      StockMovement("x", productId = "other", createdAt = 99)
    val d = ShopData(products = listOf(rice), stockMovements = moves)
    assertEquals(listOf("m8", "m7", "m6", "m5", "m4"), ProductDetailModel.recentMoves(d, "r").map { it.id })
    assertEquals(8, ProductDetailModel.moveCount(d, "r"))
  }

  @Test
  fun `گونهٔ حرکت از نوعِ ثبت‌شده`() {
    assertEquals(MoveKind.PURCHASE_IN, ProductDetailModel.kindOf("purchase_in"))
    assertEquals(MoveKind.CUSTOMER_RETURN, ProductDetailModel.kindOf("customer_return"))
    assertEquals(MoveKind.SALE_CANCEL, ProductDetailModel.kindOf("sale_cancel"))
    assertEquals(MoveKind.ADJUSTMENT, ProductDetailModel.kindOf("adjustment"))
    assertEquals(MoveKind.OTHER, ProductDetailModel.kindOf("??"))
  }

  /*
   *  PRD-15 — بهای میانگینِ موزون روی صفحهٔ کالا، از همان بازپخشِ گزارش‌ها (`ReportBridge.stockCostOf`)؛ فقط خواندنی.
   *  ۱۰ × ۱۰۰ و ۱۰ × ۱۳۰ ⇒ فروشِ ۵ = ۵۷۵ ⇒ ۱۵ عدد به ۱۷۲۵ ⇒ یک واحد ۱۱۵ (قیمتِ خریدِ کالا ۱۰۰ است و دست نمی‌خورد).
   */
  @Test
  fun `بهای میانگینِ موزون فقط خواندنی و از همان بازپخش`() {
    val d = ShopData(
      products = listOf(rice),
      warehouseEntries = listOf(
        WarehouseEntry("e1", productId = "r", units = 10.0, price = 100.0, createdAt = 1L),
        WarehouseEntry("e2", productId = "r", units = 10.0, price = 130.0, createdAt = 2L),
      ),
      sales = listOf(Sale("s1", total = 650.0, finalTotal = 650.0, date = "2026-11-01", createdAt = 3L)),
      saleItems = listOf(SaleItem("i1", saleId = "s1", productId = "r", quantity = 5.0, unitPrice = 130.0, purchasePrice = 100.0, totalPrice = 650.0)),
    )
    val a = ProductDetailModel.figures(d, rice).avgCost!!
    assertEquals(15_000L, a.qtyMilli)
    assertEquals(172_500L, a.valueMinor)
    assertEquals(11_500L, a.unitCostMinor)
    assertEquals(false, a.approximate)
    assertEquals(100.0, rice.purchasePrice, 0.0)
    //  همهٔ موجودی رفت ⇒ آخرین بهای معلوم، «تقریبی»
    val gone = d.copy(saleItems = d.saleItems.map { it.copy(quantity = 20.0) })
    val g = ProductDetailModel.figures(gone, rice).avgCost!!
    assertEquals(0L, g.qtyMilli)
    assertEquals(11_500L, g.unitCostMinor)
    assertEquals(true, g.approximate)
    //  هیچ ورود و فروشی ⇒ هیچ؛ کیت ⇒ هیچ
    assertNull(ProductDetailModel.figures(ShopData(products = listOf(rice)), rice).avgCost)
    val kit = rice.copy(kit = listOf(ir.vil3ntec.tohid.data.KitPart("x", 1.0)))
    assertNull(ProductDetailModel.avgCostOf(d.copy(products = listOf(kit)), kit))
  }
}
