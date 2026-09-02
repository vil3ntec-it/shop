package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  قیمتِ آزادِ ردیف، قلمِ آزاد، و مشتریِ فاکتورِ نقدی.
 *
 *  هر سه به پولِ دکان دست می‌زنند، پس هر سه باید سنجیده شوند — به‌ویژه
 *  اینکه **بدهی نسازند** جایی که نباید.
 */
class CartLineTest {

  private var seq = 0
  private fun newId(): String = "g${seq++}"

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0)

  private fun ledger() = ShopData(
    products = listOf(rice),
    debtors = listOf(Debtor(id = "d1", name = "احمد")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 100.0)),
  )

  private fun ok(r: SalesEngine.Result) = r as SalesEngine.Result.Ok
  private fun failed(r: SalesEngine.Result) = (r as SalesEngine.Result.Failed).message

  private fun sell(d: ShopData, cart: List<SalesEngine.CartLine>, checkout: SalesEngine.Checkout) =
    SalesEngine.record(d, cart, checkout, "2026-09-01", 1_700_000_000_000, ::newId)

  /* ------------------------ قیمتِ آزادِ ردیف ------------------------ */

  @Test
  fun `قیمتِ دستیِ ردیف در جمعِ سبد می‌نشیند`() {
    val d = ledger()
    val cart = listOf(SalesEngine.CartLine("p1", 2.0, unitPrice = 250.0))
    assertEquals(500.0, SalesEngine.cartTotal(d, cart), 0.001)
  }

  @Test
  fun `بدونِ قیمتِ دستی، قیمتِ خودِ کالا — مثلِ همیشه`() {
    val d = ledger()
    val cart = listOf(SalesEngine.CartLine("p1", 2.0))
    assertEquals(600.0, SalesEngine.cartTotal(d, cart), 0.001)
  }

  @Test
  fun `قیمتِ دستی در قلمِ فاکتور ثبت می‌شود، پس سود درست در می‌آید`() {
    val d = ledger()
    val r = ok(sell(d, listOf(SalesEngine.CartLine("p1", 2.0, unitPrice = 250.0)), SalesEngine.Checkout()))
    val item = r.data.saleItems.single()
    assertEquals(250.0, item.unitPrice, 0.001)
    assertEquals(500.0, item.totalPrice, 0.001)
    //  بهای خرید دست نمی‌خورد، پس سود = (۲۵۰ − ۲۰۰) × ۲
    val (quantity, profit) = ReportEngine.productStat(r.data, "p1")
    assertEquals(2.0, quantity, 0.001)
    assertEquals(100.0, profit, 0.001)
  }

  @Test
  fun `برداشتنِ قیمتِ دستی، ردیف را به قیمتِ کالا برمی‌گرداند`() {
    val cart = listOf(SalesEngine.CartLine("p1", 2.0, unitPrice = 250.0))
    val back = SalesEngine.setLinePrice(cart, "p1", null)
    assertNull(back.single().unitPrice)
    assertEquals(600.0, SalesEngine.cartTotal(ledger(), back), 0.001)
  }

  @Test
  fun `عوض کردنِ تعداد، قیمتِ دستی را نمی‌خورد`() {
    val cart = listOf(SalesEngine.CartLine("p1", 2.0, unitPrice = 250.0))
    val next = SalesEngine.setCartQty(cart, "p1", 5.0)
    assertEquals(250.0, next.single().unitPrice!!, 0.001)
    assertEquals(5.0, next.single().quantity, 0.001)
  }

  /* --------------------------- قلمِ آزاد --------------------------- */

  @Test
  fun `قلمِ آزاد در جمعِ سبد می‌آید، بی‌آنکه کالایی داشته باشد`() {
    val cart = SalesEngine.addFreeLine(emptyList(), "کیسه", 20.0, 3.0, ::newId)
    val line = cart.single()
    assertTrue(line.free)
    assertEquals(60.0, SalesEngine.cartTotal(ledger(), cart), 0.001)
  }

  @Test
  fun `دو قلمِ آزادِ هم‌نام با هم جمع نمی‌شوند`() {
    var cart = SalesEngine.addFreeLine(emptyList(), "کیسه", 20.0, 1.0, ::newId)
    cart = SalesEngine.addFreeLine(cart, "کیسه", 35.0, 1.0, ::newId)
    assertEquals(2, cart.size)
    assertEquals(55.0, SalesEngine.cartTotal(ledger(), cart), 0.001)
  }

  @Test
  fun `قلمِ بی‌نام اصلاً به سبد نمی‌رود`() {
    assertTrue(SalesEngine.addFreeLine(emptyList(), "   ", 20.0, 1.0, ::newId).isEmpty())
  }

  @Test
  fun `قلمِ آزاد نه موجودی کم می‌کند نه حرکتِ انبار می‌سازد`() {
    val d = ledger()
    val cart = SalesEngine.addFreeLine(emptyList(), "کیسه", 20.0, 2.0, ::newId)
    val r = ok(sell(d, cart, SalesEngine.Checkout()))

    assertTrue("قلمِ آزاد نباید حرکتِ انبار بسازد", r.data.stockMovements.isEmpty())
    //  موجودیِ کالای واقعی هم دست‌نخورده
    assertEquals(100.0, ShopStore.stock(r.data, "p1"), 0.001)

    val item = r.data.saleItems.single()
    assertEquals("", item.productId)
    assertEquals("کیسه", item.name)
    assertEquals(40.0, r.data.sales.single().finalTotal, 0.001)
  }

  @Test
  fun `نامِ قلمِ آزاد روی فاکتور خوانده می‌شود`() {
    val d = ledger()
    val cart = SalesEngine.addFreeLine(emptyList(), "کرایه موتر", 500.0, 1.0, ::newId)
    val r = ok(sell(d, cart, SalesEngine.Checkout()))
    assertEquals("کرایه موتر", SalesEngine.itemName(r.data, r.data.saleItems.single()))
  }

  @Test
  fun `نامِ قلمِ معمولی از خودِ کالا می‌آید، نه از قلم`() {
    val d = ledger()
    val r = ok(sell(d, listOf(SalesEngine.CartLine("p1", 1.0)), SalesEngine.Checkout()))
    assertEquals("برنج", SalesEngine.itemName(r.data, r.data.saleItems.single()))
  }

  /* ----------------------- مشتریِ فاکتورِ نقدی ----------------------- */

  @Test
  fun `مشتری روی فاکتورِ نقدی می‌نشیند ولی بدهی نمی‌سازد`() {
    val d = ledger()
    val r = ok(
      sell(
        d,
        listOf(SalesEngine.CartLine("p1", 1.0)),
        SalesEngine.Checkout(payment = SalesEngine.Payment.CASH, debtorId = "d1"),
      )
    )
    val sale = r.data.sales.single()
    assertEquals("d1", sale.debtorId)
    assertEquals(0.0, sale.debtGiven, 0.001)
    assertEquals(0.0, sale.remaining, 0.001)
    assertTrue("فروشِ نقدی نباید تراکنشِ قرض بسازد", r.data.transactions.isEmpty())
    assertEquals(0.0, ShopStore.debt(r.data, "d1"), 0.001)
  }

  @Test
  fun `فروشِ نسیه مثلِ همیشه بدهی می‌سازد`() {
    val d = ledger()
    val r = ok(
      sell(
        d,
        listOf(SalesEngine.CartLine("p1", 1.0)),
        SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 100.0, debtorId = "d1"),
      )
    )
    assertEquals(200.0, ShopStore.debt(r.data, "d1"), 0.001)
  }

  @Test
  fun `نسیه بدونِ قرض‌دار همچنان رد می‌شود`() {
    val d = ledger()
    val message = failed(
      sell(
        d,
        listOf(SalesEngine.CartLine("p1", 1.0)),
        SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0),
      )
    )
    assertTrue(message.contains("قرض‌دار"))
  }

  @Test
  fun `مشتریِ ناشناس رد می‌شود، حتی در فروشِ نقدی`() {
    val d = ledger()
    val message = failed(
      sell(d, listOf(SalesEngine.CartLine("p1", 1.0)), SalesEngine.Checkout(debtorId = "نیست"))
    )
    assertTrue(message.contains("پیدا نشد"))
  }

  @Test
  fun `نسیه‌ای که کامل پرداخت شده، بدهی نمی‌سازد`() {
    val d = ledger()
    val r = ok(
      sell(
        d,
        listOf(SalesEngine.CartLine("p1", 1.0)),
        SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 300.0, debtorId = "d1"),
      )
    )
    assertTrue(r.data.transactions.isEmpty())
    assertEquals(0.0, ShopStore.debt(r.data, "d1"), 0.001)
  }
}
