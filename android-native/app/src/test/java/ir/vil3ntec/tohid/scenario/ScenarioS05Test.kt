package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductPack
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  S05 — فروش با واحدِ بزرگ (POS-09، PRD-07): «روغن» با واحدِ پایهٔ «دانه» و «کارتن = ۱۲ دانه»، دو کارتن.
 *  دو راهِ امروز: برگهٔ ردیفِ گوشی با «واحدِ فروشِ دوم» (`LineModel.switchUnit`) و بارکدِ کارتن با «۲*» در
 *  کامپیوتر (PRD-04، `PackBarcodes`).
 *
 *  ماندهٔ سناریو (pending، نه سرخ):
 *   • قیمتِ کارتن از لیستِ قیمت نمی‌آید: کارتن قیمتِ جدا ندارد و همیشه ۱۲ × قیمتِ دانه است.
 *   • «فروشِ بخشی از کارتن مجاز نیست» تنظیم ندارد: ۱٫۵ کارتن ۱۸ دانه می‌شود و رد نمی‌شود.
 *   • کلیدِ Ctrl+U برای انتخابِ واحد نیست (گوشی کلیدِ «واحد» در برگهٔ ردیف دارد، کامپیوتر بارکدِ کارتن یا «۲*»).
 */
class ScenarioS05Test {

  private var seq = 0
  private fun newId() = "u${seq++}"

  private val oil = Product(
    id = "oil", name = "روغن", unit = "دانه", salePrice = 150.0, purchasePrice = 120.0,
    barcodes = listOf("6260000000101"), saleUnit = "کارتن", unitFactor = 12.0,
    packs = listOf(ProductPack("کارتن", 12_000, listOf("6260000000118"))),
  )
  private val d = ShopData(products = listOf(oil), warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "oil", units = 60.0)))

  private fun sell(cart: List<SalesEngine.CartLine>): ShopData =
    (SalesEngine.record(d, cart, SalesEngine.Checkout(), "2026-11-02", 1_000, ::newId) as SalesEngine.Result.Ok).data

  @Test
  fun `S05 — گوشی دو کارتن در برگهٔ ردیف ⇒ ۲۴ دانه از موجودی و قیمت ۱۲ × دانه`() {
    val line = SalesEngine.CartLine("oil", 1.0)
    val info = LineModel.info(d, line)
    val form = LineModel.switchUnit(LineModel.start(info), info, toSale = true).copy(qtyText = "2")
    assertEquals("کارتن", LineModel.shownUnit(form, info))
    val qty = LineModel.quantity(form, info)!!
    assertEquals(24.0, qty, 0.0)
    val cart = SalesEngine.setCartQty(listOf(line), "oil", qty)
    assertEquals(2 * 12 * 150.0, SalesEngine.cartTotal(d, cart), 0.0)
    val after = sell(cart)
    assertEquals(36.0, ShopStore.stock(after, "oil"), 0.0)
    assertEquals(3600.0, after.sales.single().finalTotal, 0.0)
  }

  @Test
  fun `S05 — کامپیوتر «۲*» و بارکدِ کارتن ⇒ همان ۲۴ دانه`() {
    val hit = PosSearch.resolve("۲*6260000000118", ShopStore.barcodeIndex(d), d.products, emptyList(), 0, ShopStore.packIndex(d))
    assertEquals(PosHit.Add(oil, 24.0), hit)
    val cart = SalesEngine.addToCart(emptyList(), "oil", 24.0)
    val after = sell(cart)
    assertEquals(36.0, ShopStore.stock(after, "oil"), 0.0)
    assertEquals(3600.0, after.sales.single().finalTotal, 0.0)
  }
}
