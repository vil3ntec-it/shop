package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.sale.PriceList
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-12 — قیمتِ عمده از فرمِ کالا نوشته می‌شود. تا امروز هیچ فرمی آن را نداشت (`addProduct` همیشه ۰ می‌گذاشت و
 *  `editProduct` دست نمی‌زد) ⇒ لیستِ «عمده» ِ صندوق (POS-18، `PriceList.unitMinor`: فقط عمدهٔ مثبت) همیشه همان خرده بود.
 *  `null` در پیش‌نویس ⇒ دست نمی‌خورد (فرم‌هایی که کادرِ عمده ندارند، یعنی تنظیمِ «لیستِ قیمت» خاموش)؛ خالی در فرم ⇒ ۰ = ندارد.
 */
class WholesalePriceTest {

  private var seq = 0
  private fun newId(): String = "q${seq++}"
  private val today = "2026-11-09"

  private val tea = Product(id = "tea", name = "چای", category = "خوراکی", unit = "بسته", purchasePrice = 80.0, salePrice = 120.0, wholesalePrice = 100.0)
  private val d = ShopData(products = listOf(tea))

  private fun data(r: WarehouseEngine.Result): ShopData = (r as? WarehouseEngine.Result.Ok)?.data ?: error("رد شد: $r")
  private fun ok(r: WarehouseEngine.Result): Product = data(r).products.single { it.id == "tea" }
  private fun added(r: WarehouseEngine.Result): Product = data(r).products.single { it.name == "قند" }
  private fun failed(r: WarehouseEngine.Result): String = (r as? WarehouseEngine.Result.Failed)?.message ?: error("پذیرفته شد")
  private fun edit(wholesale: Double?, name: String = "چای") =
    WarehouseEngine.editProduct(d, "tea", WarehouseEngine.ProductDraft(name = name, unit = "بسته", purchasePrice = 80.0, salePrice = 120.0, wholesalePrice = wholesale), today, 0, ::newId)

  @Test
  fun addProduct_writesWholesale_orZeroWhenAbsent() {
    val draft = WarehouseEngine.ProductDraft(name = "قند", unit = "کیلو", purchasePrice = 50.0, salePrice = 70.0)
    assertEquals(60.0, added(WarehouseEngine.addProduct(d, draft.copy(wholesalePrice = 60.0), 0, ::newId)).wholesalePrice, 0.0)
    assertEquals(0.0, added(WarehouseEngine.addProduct(d, draft, 0, ::newId)).wholesalePrice, 0.0)
    for (bad in listOf(-1.0, Double.NaN)) {
      assertEquals(ir.vil3ntec.tohid.core.i18n.StrGoods.wholesaleBad, failed(WarehouseEngine.addProduct(d, draft.copy(wholesalePrice = bad), 0, ::newId)))
    }
  }

  @Test
  fun editProduct_setsClearsOrKeepsWholesale() {
    assertEquals(95.0, ok(edit(95.0)).wholesalePrice, 0.0)
    assertEquals(0.0, ok(edit(0.0)).wholesalePrice, 0.0)
    //  فرمی که کادرِ عمده ندارد (`null`) ⇒ همان ۱۰۰ ِ پیشین؛ تغییرِ نام آن را پاک نمی‌کند
    assertEquals(100.0, ok(edit(null, name = "چای سبز")).wholesalePrice, 0.0)
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrGoods.wholesaleBad, failed(edit(-5.0)))
  }

  @Test
  fun formRoundTrip_andInlineError() {
    val row = ProductFormModel.of(tea)
    assertEquals("100", row.wholesale)
    assertEquals(100.0, ProductFormModel.draftEdit(row).wholesalePrice!!, 0.0)
    assertEquals(0.0, ProductFormModel.draftEdit(row.copy(wholesale = "")).wholesalePrice!!, 0.0)
    assertEquals("", ProductFormModel.of(tea.copy(wholesalePrice = 0.0)).wholesale)
    assertEquals(ProductFormModel.Problem.NOT_A_NUMBER, ProductFormModel.errors(row.copy(wholesale = "x"), ProductFormModel.Mode.EDIT)[ProductFormModel.Field.WHOLESALE])
    assertNull(ProductFormModel.errors(row.copy(wholesale = ""), ProductFormModel.Mode.EDIT)[ProductFormModel.Field.WHOLESALE])
    assertTrue(ProductFormModel.draftEdit(row.copy(wholesale = "x")).wholesalePrice!!.isNaN())
  }

  @Test
  fun wholesaleWrittenByTheForm_reachesTheTillPriceList() {
    val saved = ok(WarehouseEngine.editProduct(d, "tea", ProductFormModel.draftEdit(ProductFormModel.of(tea).copy(wholesale = "90")), today, 0, ::newId))
    val prices = PriceList.Prices(Math.round(saved.salePrice * 100.0), Math.round(saved.wholesalePrice * 100.0))
    assertEquals(9000L, PriceList.unitMinor(prices, PriceList.WHOLESALE))
    assertEquals(12000L, PriceList.unitMinor(prices, PriceList.RETAIL))
  }
}
