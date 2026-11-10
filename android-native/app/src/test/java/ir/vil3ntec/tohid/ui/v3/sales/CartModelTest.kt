package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.Product
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

  @Test
  fun `پیشوندِ ۳ستاره در جست‌وجو هر لمس را سه تا می‌کند و بی پیشوند همان یک پله`() {
    val q = ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel.query("۲*صابون")
    assertEquals(2.0, q.count, 1e-9)
    assertEquals("صابون", q.term)
    assertEquals(1.0, ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel.query("صابون").count, 1e-9)
    //  کاتالوگ با بخشِ پس از ستاره جور می‌شود
    val index = ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel.index(ledger)
    val all = ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel.items(ledger, index, "", null, emptyMap()).map { it.id }
    val first = ledger.products.first { it.id == "soap" }.name
    val withPrefix = ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel.items(ledger, index, "3×$first", null, emptyMap()).map { it.id }
    assertTrue(withPrefix.contains("soap"))
    assertTrue(all.containsAll(withPrefix))

    assertEquals(listOf(CartLine("soap", 2.0)), CartModel.addCount(ledger, emptyList(), "soap", 2.0).lines)
    //  کالای وزنی: همان مقدار، بی صفحه‌کلید
    assertEquals(1.5, CartModel.addCount(ledger, emptyList(), "rice", 1.5).lines!!.single().quantity, 1e-9)
    //  اعشار برای کالای شمردنی، صفر یا منفی ⇒ همان یک پله
    assertEquals(listOf(CartLine("soap", 1.0)), CartModel.addCount(ledger, emptyList(), "soap", 1.5).lines)
    assertEquals(listOf(CartLine("soap", 1.0)), CartModel.addCount(ledger, emptyList(), "soap", 0.0).lines)
    //  از موجودی جلو نمی‌زند (صابون ۳)
    val capped = CartModel.addCount(ledger, emptyList(), "soap", 5.0)
    assertTrue(capped.capped)
    assertTrue(capped.note!!.isNotBlank())
  }

  @Test
  fun `گوشی — متنی که خودش بارکدِ کالاست («12x34») بی پیشوند می‌ماند و کاتالوگ همان کالا را نشان می‌دهد`() {
    val battery = Product(id = "battery", name = "باتری", unit = "عدد", salePrice = 40.0, barcodes = listOf("12x34"))
    val p34 = Product(id = "p34", name = "پیچ", unit = "عدد", salePrice = 2.0, barcodes = listOf("34"))
    val d = ledger.copy(products = ledger.products + battery + p34)
    val q = ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel.query("12x34", d)
    //  پیش از این (۱۲، «34») ⇒ کاشیِ «پیچ» و هر لمس دوازده تا
    assertEquals(1.0, q.count, 1e-9)
    assertEquals("12x34", q.term)
    assertEquals(2.0, ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel.query("2*12x34", d).count, 1e-9)
    assertEquals(12.0, ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel.query("12x35", d).count, 1e-9)
    val index = ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel.index(d)
    assertEquals(listOf("battery"), ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel.items(d, index, "12x34", null, emptyMap()).map { it.id })
  }
}
