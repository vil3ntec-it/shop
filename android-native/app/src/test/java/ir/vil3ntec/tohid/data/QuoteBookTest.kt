package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** POS-25 — دفترِ پیش‌فاکتورِ همین دستگاه (`Quotes`): قیمتِ ثابت، اعتبار، تبدیل به سبد و «فروخته شد» */
class QuoteBookTest {

  private val d = ShopData(products = listOf(Product(id = "p1", name = "برنج", salePrice = 250.0), Product(id = "p2", name = "روغن", salePrice = 120.0)))
  private val cart = listOf(
    SalesEngine.CartLine("p1", 2.0),
    SalesEngine.CartLine("p2", 1.0, unitPrice = 100.0, lotId = "lot-1"),
    SalesEngine.CartLine("free-1", 1.0, unitPrice = 30.0, label = "بسته‌بندی"),
    SalesEngine.CartLine("gone", 5.0),
  )

  private fun made(book: QuoteBook = QuoteBook(), days: Int = 7): Quotes.Result =
    Quotes.create(book, d, cart, "c1", "  آقای کریمی ", "2026-11-01", days, 1000, "q1", " علی ")

  @Test fun `قیمتِ همین لحظه ثابت می‌شود؛ سری و کالای حذف‌شده نمی‌آیند`() {
    val q = (made() as Quotes.Result.Ok).quote
    assertEquals(1, q.number)
    assertEquals("2026-11-08", q.validUntil)
    assertEquals(listOf(250.0, 100.0, 30.0), q.lines.map { it.unitPrice })
    assertTrue(q.lines.all { it.lotId == null })
    assertEquals(63_000L, q.totalMinor)
    assertEquals("آقای کریمی", q.note)
    assertEquals("علی", q.by)
    //  قیمتِ کالا بعداً عوض شد ⇒ پیش‌فاکتور همان
    val later = d.copy(products = d.products.map { it.copy(salePrice = it.salePrice * 2) })
    assertEquals(listOf(250.0, 100.0, 30.0), q.lines.map(Quotes::cartLine).map { SalesEngine.linePrice(later, it) })
  }

  @Test fun `ردِ ساختن`() {
    assertEquals("BAD_DAYS", (made(days = 0) as Quotes.Result.Refused).code)
    assertEquals("EMPTY", (Quotes.create(QuoteBook(), d, emptyList(), null, null, "2026-11-01", 7, 1, "q", "") as Quotes.Result.Refused).code)
    val full = QuoteBook(items = (1..Quote.MAX_OPEN).map { SavedQuote(id = "x$it", number = it) })
    assertEquals("TOO_MANY", (made(full) as Quotes.Result.Refused).code)
  }

  @Test fun `به سبد، در سبد، فروخته و گذشته`() {
    val book = (made() as Quotes.Result.Ok).book
    assertTrue(Quotes.convert(book, "q1", d, null, cartEmpty = true, today = "2026-11-08") is Quotes.Result.Ok)
    assertEquals("CART_BUSY", (Quotes.convert(book, "q1", d, "k0", cartEmpty = false, today = "2026-11-02") as Quotes.Result.Refused).code)
    assertEquals("EXPIRED", (Quotes.convert(book, "q1", d, null, cartEmpty = true, today = "2026-11-09") as Quotes.Result.Refused).code)
    assertEquals("NOT_FOUND", (Quotes.convert(book, "nope", d, null, cartEmpty = true, today = "2026-11-02") as Quotes.Result.Refused).code)
    val inCart = Quotes.markInCart(book, "q1", "k1")
    assertEquals(Quote.State.IN_CART, Quotes.state(inCart.items.single(), d, "k1", "2026-11-02"))
    //  سبد رها شد ⇒ دوباره معتبر و دوباره به سبد می‌رود
    assertEquals(Quote.State.VALID, Quotes.state(inCart.items.single(), d, "k2", "2026-11-02"))
    val sold = d.copy(sales = listOf(Sale(id = "k1", finalTotal = 630.0)))
    assertEquals(Quote.State.SOLD, Quotes.state(inCart.items.single(), sold, null, "2026-11-30"))
    assertEquals("SOLD", (Quotes.convert(inCart, "q1", sold, null, cartEmpty = true, today = "2026-11-02") as Quotes.Result.Refused).code)
    //  فروشِ لغوشده فروخته حساب نمی‌شود
    val cancelled = d.copy(sales = listOf(Sale(id = "k1", status = "cancelled")))
    assertEquals(Quote.State.VALID, Quotes.state(inCart.items.single(), cancelled, null, "2026-11-02"))
    assertTrue(Quotes.remove(inCart, "q1").items.isEmpty())
  }

  @Test fun `پرونده خوانده و نوشته می‌شود؛ خراب ⇒ null`() {
    val book = (made() as Quotes.Result.Ok).book
    assertEquals(book, QuoteBookCodec.decode(QuoteBookCodec.encode(book)))
    assertEquals(QuoteBook(), QuoteBookCodec.decode(""))
    assertNull(QuoteBookCodec.decode("{خراب"))
    assertEquals(2, (Quotes.create(book, d, cart, null, null, "2026-11-01", 7, 2, "q2", "") as Quotes.Result.Ok).quote.number)
  }
}
