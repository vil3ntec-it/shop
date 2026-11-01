package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.core.i18n.StrProduct
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.KeypadModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-09 در صندوقِ نسخهٔ ۳ — کالای خدماتی (`Product.stockless`) همان `ProductInfoMap.sellable` ِ
 *  صندوقِ پیشین را می‌گیرد: سبد سقف ندارد، کاشی «تمام شد» نیست و «خدماتی» می‌گوید. «چای» ِ دفترِ
 *  آزمون موجودی ندارد؛ بی پرچم همان ردِ پیشین می‌ماند.
 */
class StocklessCartTest {

  private val service = ledger.copy(products = ledger.products.map { if (it.id == "tea") it.copy(stockless = true) else it })

  @Test
  fun `کالای خدماتی بی موجودی به سبد می رود و کالای عادی همان رد`() {
    val ok = CartModel.addOne(service, emptyList(), "tea")
    assertEquals(listOf(CartLine("tea", 1.0)), ok.lines)
    assertFalse(ok.capped)
    assertNull(ok.note)
    //  بی پرچم: همان «تمام شد» ِ پیشین
    assertTrue(CartModel.addOne(ledger, emptyList(), "tea").capped)
  }

  @Test
  fun `کاشیِ خدماتی تمام شده نیست و خدماتی می گوید`() {
    val tile = CatalogModel.item(service, service.products.first { it.id == "tea" }, 0.0)
    assertTrue(tile.stockless)
    assertFalse(tile.soldOut)
    assertEquals(StrProduct.serviceTag, PhoneSaleText.stock(tile))
    val plain = CatalogModel.item(ledger, ledger.products.first { it.id == "tea" }, 0.0)
    assertTrue(plain.soldOut)
    assertEquals(StrSale.soldOut, PhoneSaleText.stock(plain))
  }

  @Test
  fun `صفحه کلید برای خدماتی خطای موجودی نمی دهد`() {
    val item = KeypadModel.item(service, "tea", emptyList())
    assertNotNull(item)
    assertNull(KeypadModel.problem(item!!, 5.0))
    assertNotNull(KeypadModel.problem(KeypadModel.item(ledger, "tea", emptyList())!!, 5.0))
  }
}
