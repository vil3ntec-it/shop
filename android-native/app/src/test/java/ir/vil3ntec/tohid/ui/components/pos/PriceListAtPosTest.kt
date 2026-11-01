package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.core.sale.PriceList
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** POS-18 روی سبدِ صندوق: عمده فقط ردیفِ غیرِدستی، قلمِ آزاد نه، بی تغییر ⇒ همان سبد */
class PriceListAtPosTest {
  private val d = ShopData(
    products = listOf(
      Product(id = "a", name = "چای", salePrice = 100.0, wholesalePrice = 90.0),
      Product(id = "b", name = "بوره", salePrice = 50.0),
    ),
  )
  private val cart = listOf(
    CartLine("a", 1.0),
    CartLine("b", 2.0),
    CartLine("a", 1.0, unitPrice = 85.0),
    CartLine("", 1.0, unitPrice = 30.0, label = "خدمت"),
  )

  @Test fun wholesaleRepricesOnlyListLines() {
    val w = repriceCart(d, cart, PriceList.WHOLESALE)
    assertEquals(listOf(90.0, null, 85.0, 30.0), w.map { it.unitPrice })
    //  برگشت به خرده ⇒ همان قیمتِ کالا (null) و فیِ دستی همان
    assertEquals(listOf(null, null, 85.0, 30.0), repriceCart(d, w, PriceList.RETAIL).map { it.unitPrice })
  }

  @Test fun unchangedCartIsTheSameObject() {
    assertSame(cart, repriceCart(d, cart, PriceList.RETAIL))
  }
}
