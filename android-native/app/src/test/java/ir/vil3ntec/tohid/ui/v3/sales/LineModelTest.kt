package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineForm
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** برگهٔ «تعداد و قیمتِ این ردیف» — همان قاعدهٔ کادرِ پیشین روی `setCartQty`/`setLinePrice` */
class LineModelTest {

  private val soapLine = CartLine("soap", 2.0)
  private val soap = LineModel.info(ledger, soapLine)

  @Test
  fun `آغاز با مقدار و قیمتِ امروزِ ردیف`() {
    assertEquals(LineForm("2", "50"), LineModel.start(soap))
    val manual = LineModel.info(ledger, CartLine("soap", 1.0, unitPrice = 45.0))
    assertEquals("45", LineModel.start(manual).priceText)
    assertTrue(manual.custom)
    assertFalse(soap.custom)
  }

  @Test
  fun `قیمتِ دست نخورده همان قیمتِ ردیف می ماند`() {
    val start = LineModel.start(soap)
    val next = LineModel.apply(listOf(soapLine), soap, start.copy(qtyText = "3"), start)
    assertEquals(listOf(CartLine("soap", 3.0, unitPrice = null)), next)
  }

  @Test
  fun `قیمتِ نوشته شده فقط روی همین ردیف و جمع با موتور`() {
    val start = LineModel.start(soap)
    val form = start.copy(priceText = "40")
    assertEquals(80.0, LineModel.total(form, soap, start), 0.0)
    assertEquals(listOf(CartLine("soap", 2.0, unitPrice = 40.0)), LineModel.apply(listOf(soapLine), soap, form, start))
  }

  @Test
  fun `برگشت به قیمتِ کالا قیمتِ دستی را برمی دارد`() {
    val line = CartLine("soap", 1.0, unitPrice = 45.0)
    val info = LineModel.info(ledger, line)
    val start = LineModel.start(info)
    val reset = start.copy(priceText = "50", resetPrice = true)
    assertNull(LineModel.wantedPrice(reset, info, start))
    assertEquals(listOf(CartLine("soap", 1.0)), LineModel.apply(listOf(line), info, reset, start))
  }

  @Test
  fun `مقدارِ نادرست و صفر و اعشارِ کالای دانه ای خطاست`() {
    val start = LineModel.start(soap)
    assertNotNull(LineModel.qtyError(start.copy(qtyText = ""), soap))
    assertNotNull(LineModel.qtyError(start.copy(qtyText = "0"), soap))
    assertNotNull(LineModel.qtyError(start.copy(qtyText = "1.5"), soap))
    assertNull(LineModel.qtyError(start.copy(qtyText = "4"), soap))
    val rice = LineModel.info(ledger, CartLine("rice", 1.0))
    assertNull(LineModel.qtyError(LineModel.start(rice).copy(qtyText = "0.25"), rice))
    assertFalse(LineModel.valid(start.copy(priceText = "x"), soap))
  }

  @Test
  fun `واحدِ فروشِ دوم به واحدِ موجودی ثبت می شود`() {
    val line = CartLine("carton", 1.0)
    val info = LineModel.info(ledger, line)
    assertTrue(info.hasSaleUnit)
    val start = LineModel.start(info)
    val bottles = LineModel.switchUnit(start, info, toSale = true)
    assertEquals("12", bottles.qtyText)
    assertTrue(bottles.inSaleUnit)
    assertEquals(0.5, LineModel.quantity(bottles.copy(qtyText = "6"), info)!!, 1e-9)
    assertEquals("1", LineModel.switchUnit(bottles, info, toSale = false).qtyText)
  }

  @Test
  fun `قلمِ آزاد بی قیمت نمی ماند`() {
    val free = CartLine("x1", 1.0, unitPrice = 20.0, label = "کیسه")
    val info = LineModel.info(ledger, free)
    val start = LineModel.start(info)
    assertEquals("کیسه", info.name)
    assertEquals(20.0, LineModel.wantedPrice(start.copy(priceText = ""), info, start)!!, 0.0)
  }

  @Test
  fun `برداشتن و برگرداندن سرِ همان جا`() {
    val lines = listOf(CartLine("rice", 1.0), soapLine, CartLine("tea", 1.0))
    val removed = LineModel.remove(lines, soap)
    assertEquals(listOf("rice", "tea"), removed.map { it.productId })
    assertEquals(lines, LineModel.restore(removed, soapLine, 1))
    //  همان کالا دوباره افزوده شد ⇒ برگرداندن دست نمی‌زند
    val again = removed + CartLine("soap", 1.0)
    assertEquals(again, LineModel.restore(again, soapLine, 1))
  }
}
