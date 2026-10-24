package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** کارهای کوچکِ سبد (زدنِ کاشی، «−»، بارکد) روی همان موتورِ فروش */
class CartModelTest {

  private val index = ShopStore.barcodeIndex(ledger)

  @Test
  fun `زدنِ کاشی یک پلهٔ واحد می افزاید`() {
    val soap = CartModel.addOne(ledger, emptyList(), "soap")
    assertEquals(listOf(CartLine("soap", 1.0)), soap.lines)
    assertFalse(soap.capped)
    assertNull(soap.note)
    //  کیلوگرم ⇐ پلهٔ ۰٫۱
    assertEquals(0.1, CartModel.addOne(ledger, emptyList(), "rice").lines!!.single().quantity, 1e-9)
  }

  @Test
  fun `تمام شده سبد را دست نمی زند و پیامِ موتور را می دهد`() {
    val tea = CartModel.addOne(ledger, emptyList(), "tea")
    assertNull(tea.lines)
    assertTrue(tea.capped)
    assertTrue(tea.note!!.isNotBlank())
  }

  @Test
  fun `از موجودی جلو نمی زند`() {
    val full = listOf(CartLine("soap", 3.0))
    val more = CartModel.addOne(ledger, full, "soap")
    assertTrue(more.capped)
    assertNull(more.lines)
    assertTrue(more.note!!.contains("3"))
  }

  @Test
  fun `منها یک پله کم می کند و پلهٔ آخر ردیف را برمی دارد`() {
    assertEquals(listOf(CartLine("soap", 1.0)), CartModel.minusOne(ledger, listOf(CartLine("soap", 2.0)), "soap"))
    assertEquals(emptyList<CartLine>(), CartModel.minusOne(ledger, listOf(CartLine("soap", 1.0)), "soap"))
    assertNull(CartModel.minusOne(ledger, emptyList(), "soap"))
  }

  @Test
  fun `مقدارِ هر کالا در سبد جمع می شود`() {
    val lines = listOf(CartLine("soap", 2.0), CartLine("rice", 0.5))
    assertEquals(mapOf("soap" to 2.0, "rice" to 0.5), CartModel.inCart(lines))
  }

  @Test
  fun `بارکدِ کامل کالا را با ضریب می افزاید`() {
    val s = CartModel.scan(ledger, index, emptyList(), " 626001003 ", multiplier = 2.0) as CartModel.Scan.Added
    assertEquals(listOf(CartLine("soap", 2.0)), s.lines)
    assertTrue(s.whole)
    assertFalse(s.capped)
    assertTrue(CartModel.scanNote(s).contains("صابون"))
  }

  @Test
  fun `بارکد تا موجودی و بیشتر نه`() {
    val s = CartModel.scan(ledger, index, emptyList(), "626001003", multiplier = 10.0) as CartModel.Scan.Added
    assertEquals(3.0, s.lines.single().quantity, 1e-9)
    assertTrue(s.capped)
  }

  @Test
  fun `بارکدِ ناشناس و غیرفعال و خالی`() {
    assertEquals(CartModel.Scan.Unknown("999"), CartModel.scan(ledger, index, emptyList(), "999"))
    assertTrue(CartModel.scan(ledger, index, emptyList(), "111222333") is CartModel.Scan.Inactive)
    assertNull(CartModel.scan(ledger, index, emptyList(), "   "))
  }

  @Test
  fun `موجودیِ مانده با واحد`() {
    assertEquals("3 عدد", CartModel.stockLeft(SalesTestLedger.soap, 3.0))
    assertEquals("2.5 کیلوگرم", CartModel.stockLeft(SalesTestLedger.rice, 2.5))
  }
}
