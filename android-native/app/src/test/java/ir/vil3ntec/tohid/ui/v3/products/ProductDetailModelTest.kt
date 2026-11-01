package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.screens.ProductListModel
import ir.vil3ntec.tohid.ui.v3.products.ProductDetailModel.MoveKind
import org.junit.Assert.assertEquals
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
}
