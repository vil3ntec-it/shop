package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.ui.components.pos.KeypadInput
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.cart.FreeLineForm
import ir.vil3ntec.tohid.ui.v3.sales.cart.FreeLineModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.KeypadAction
import ir.vil3ntec.tohid.ui.v3.sales.cart.KeypadModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «قلمِ آزاد» و صفحه‌کلیدِ مقدار — پیش از موتور، با همان قاعده‌های پیشین */
class CartSheetsModelTest {

  /* ------------------------------ قلمِ آزاد ------------------------------ */

  @Test
  fun `قلمِ آزاد نام و قیمت و تعدادِ بیش از صفر می خواهد`() {
    assertEquals(setOf(FreeLineModel.LABEL, FreeLineModel.PRICE), FreeLineModel.errors(FreeLineForm()).keys)
    assertEquals(setOf(FreeLineModel.PRICE), FreeLineModel.errors(FreeLineForm("کیسه", "0")).keys)
    assertEquals(setOf(FreeLineModel.QTY), FreeLineModel.errors(FreeLineForm("کیسه", "5", "0")).keys)
    assertTrue(FreeLineModel.errors(FreeLineForm("کیسه", "5", "2")).isEmpty())
  }

  @Test
  fun `قلمِ آزاد با شناسهٔ تازه و جمعِ موتور`() {
    val form = FreeLineForm(" کیسه ", "5", "3")
    assertEquals(15.0, FreeLineModel.total(form), 0.0)
    val next = FreeLineModel.add(listOf(CartLine("soap", 1.0)), form) { "n1" }
    assertEquals(CartLine("n1", 3.0, unitPrice = 5.0, label = "کیسه"), next.last())
    assertTrue(next.last().free)
    //  نادرست ⇒ همان سبد
    assertEquals(emptyList<CartLine>(), FreeLineModel.add(emptyList(), FreeLineForm(), { "n2" }))
  }

  /* ------------------------------ صفحه‌کلید ------------------------------ */

  @Test
  fun `کالای وزنی اعشار و مقدارهای آمادهٔ کوچک دارد`() {
    val rice = KeypadModel.item(ledger, "rice", emptyList())!!
    val soap = KeypadModel.item(ledger, "soap", emptyList())!!
    assertTrue(rice.weighed)
    assertFalse(soap.weighed)
    assertEquals(listOf("0.25", "0.5", "1", "2"), KeypadModel.presets(rice))
    assertEquals(listOf("1", "2", "5", "10"), KeypadModel.presets(soap))
    assertEquals(12, KeypadModel.keys(soap).size)
    assertEquals('.', KeypadModel.keys(rice)[9])
    assertEquals(' ', KeypadModel.keys(soap)[9])
    assertEquals(KeypadInput.BACKSPACE, KeypadModel.keys(soap).last())
    assertNull(KeypadModel.item(ledger, "gone", emptyList()))
  }

  @Test
  fun `خطای اعشار و موجودی پیش از ثبت`() {
    val soap = KeypadModel.item(ledger, "soap", emptyList())!!
    assertNotNull(KeypadModel.problem(soap, 1.5))
    assertNotNull(KeypadModel.problem(soap, 4.0))
    assertNull(KeypadModel.problem(soap, 3.0))
    val tea = KeypadModel.item(ledger, "tea", emptyList())!!
    assertNotNull(KeypadModel.problem(tea, 1.0))
    assertFalse(KeypadModel.canConfirm(soap, 0.0))
  }

  @Test
  fun `ثبت مقدارِ همان کالا را همین عدد می کند`() {
    val empty = KeypadModel.item(ledger, "soap", emptyList())!!
    assertEquals(KeypadAction.ADD, KeypadModel.action(empty, 2.0))
    assertEquals(listOf(CartLine("soap", 2.0)), KeypadModel.apply(emptyList(), empty, 2.0))

    val lines = listOf(CartLine("soap", 2.0))
    val held = KeypadModel.item(ledger, "soap", lines)!!
    assertEquals("2", KeypadModel.start(held))
    assertEquals(KeypadAction.SET, KeypadModel.action(held, 3.0))
    assertEquals(listOf(CartLine("soap", 3.0)), KeypadModel.apply(lines, held, 3.0))
    assertEquals(KeypadAction.REMOVE, KeypadModel.action(held, 0.0))
    assertTrue(KeypadModel.canConfirm(held, 0.0))
    assertEquals(emptyList<CartLine>(), KeypadModel.apply(lines, held, 0.0))
  }

  @Test
  fun `مبلغِ همان مقدار با موتور`() {
    val rice = KeypadModel.item(ledger, "rice", emptyList())!!
    assertEquals(30.0, KeypadModel.amount(rice, 0.25), 0.0)
    assertEquals("0.", KeypadModel.press("", '.', rice))
    assertEquals("", KeypadModel.press("", '.', KeypadModel.item(ledger, "soap", emptyList())!!))
  }
}
