package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.StockBadge
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.line
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** سبدِ برنامهٔ خرید: همان پله، سقف و گردکردنِ وب و سرور. */
class ShopperCartTest {

  @Test fun lineTotalRoundsLikeTheServer() {
    //  همان `order-states.lineTotalMinor`: floorDiv(q×p + 50000, 100000) × 100
    assertEquals(24100, CartRules.lineTotal(2000, 12050))
    assertEquals(18100, CartRules.lineTotal(1500, 12050), "۱۸۰٫۷۵ ⇒ ۱۸۱ افغانی")
    assertEquals(100, CartRules.lineTotal(1, 50000), "نیمه به سمتِ بالا")
    assertEquals(0, CartRules.lineTotal(1, 49999))
    val cart = Cart("s", listOf(line("a", 12050, 2000), line("b", 4000, 1000)))
    assertEquals(28100, CartRules.subtotal(cart))
    assertEquals(0, CartRules.subtotal(null))
  }

  @Test fun addStepsByOneUnitAndStopsAtTheProductCap() {
    val rice = product("rice", 12050, max = 2000)
    var c = Cart("golestan")
    c = CartRules.add(c, rice, 1)
    c = CartRules.add(c, rice, 2)
    assertEquals(2000, CartRules.qtyOf(c, "rice"))
    assertSame(c, CartRules.add(c, rice, 3), "سقفِ هر سفارش ⇒ همان سبد")
    assertEquals(2, c.updatedAt)
    assertEquals(1, CartRules.count(c))
  }

  @Test fun unpricedOutOfStockAndUnavailableProductsAreNotAdded() {
    val c = Cart("s")
    assertSame(c, CartRules.add(c, product("x", null), 1))
    assertSame(c, CartRules.add(c, product("y", 100, stock = StockBadge.OUT_OF_STOCK), 1))
    assertSame(c, CartRules.add(c, product("z", 100, stock = StockBadge.UNAVAILABLE), 1))
    //  ۳۲.۳: موجودیِ نامعلوم «در حالِ بررسی» است، نه «ناموجود»
    assertEquals(1000, CartRules.qtyOf(CartRules.add(c, product("w", 100, stock = StockBadge.UNVERIFIED), 1), "w"))
  }

  @Test fun cartHoldsAtMostOneHundredLines() {
    val full = Cart("s", List(CartRules.MAX_LINES) { line("p$it", 100, 1000) })
    assertSame(full, CartRules.add(full, product("new", 100), 1))
    assertEquals(2000, CartRules.qtyOf(CartRules.add(full, product("p3", 100), 1), "p3"), "قلمِ موجود هنوز بیشتر می‌شود")
  }

  @Test fun stepDownToZeroRemovesTheLineAndSetQtyIsCapped() {
    var c = Cart("s", listOf(line("a", 100, 1000), line("b", 100, 3000).copy(maxQtyMilli = 4000)))
    c = CartRules.step(c, "a", up = false, at = 5)
    assertEquals(listOf("b"), c.lines.map { it.productId })
    c = CartRules.setQty(c, "b", 9000, 6)
    assertEquals(4000, CartRules.qtyOf(c, "b"))
    assertSame(c, CartRules.setQty(c, "missing", 1000, 7))
    assertTrue(CartRules.atMax(4000, 4000))
  }

  @Test fun refreshRepricesMarksGoneAndLowersOverCapQuantities() {
    val c = Cart("s", listOf(line("a", 100, 1000), line("b", 200, 5000), line("c", 300, 1000)))
    val fresh = listOf(product("a", 150), product("b", 200, max = 2000))
    val r = CartRules.refresh(c, fresh, 9)
    assertEquals(1, r.repriced)
    assertEquals(1, r.gone)
    assertEquals(150, r.cart.lines[0].priceMinor)
    assertEquals(2000, r.cart.lines[1].qtyMilli, "سقفِ تازهٔ کالا")
    assertTrue(r.cart.lines[2].unavailable, "کالای نیامده خط‌خورده می‌ماند، حذفِ بی‌صدا نه")
    assertTrue(CartRules.hasUnavailable(r.cart))
    val again = CartRules.refresh(r.cart, fresh, 10)
    assertEquals(0, again.gone, "خط‌خورده دوباره شمرده نمی‌شود")
    assertSame(r.cart, again.cart, "بی تغییر ⇒ همان سبد")
  }

  @Test fun quoteRepricesOnlyChangedLines() {
    val c = Cart("s", listOf(line("rice", 12050, 2000), line("oil", 30000, 1000)))
    val q = PriceQuote(lines = listOf(QuoteLine("rice", unitPriceMinor = 13000), QuoteLine("oil", unitPriceMinor = 30000)), totalMinor = 56000)
    val (next, changed) = CartRules.applyQuote(c, q, 3)
    assertEquals(listOf(Triple("rice", 12050L, 13000L)), changed)
    assertEquals(13000, next.lines[0].priceMinor)
    val (same, none) = CartRules.applyQuote(next, q, 4)
    assertSame(next, same)
    assertTrue(none.isEmpty())
  }

  @Test fun unavailableAndQtyLimitErrorsUpdateTheCart() {
    val c = Cart("s", listOf(line("a", 100, 3000), line("b", 100, 1000)))
    val marked = CartRules.markUnavailable(c, listOf("b"), 2)
    assertEquals(listOf(false, true), marked.lines.map { it.unavailable })
    assertSame(marked, CartRules.markUnavailable(marked, listOf("b"), 3))
    val capped = CartRules.capAt(c, "a", 2000, 4)
    assertEquals(2000, CartRules.qtyOf(capped, "a"))
    assertEquals(2000, capped.lines[0].maxQtyMilli)
    assertEquals(listOf("b"), CartRules.capAt(c, "a", 0, 5).lines.map { it.productId })
  }
}
