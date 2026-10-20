package ir.vil3ntec.tohid.core.shopper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopperCartTest {

  private val clock = Clock()
  private val storage = MemoryStorage()
  private val shop = CartShop("nan", "نانوایی", "#108B81")

  private fun carts() = ShopperCarts(storage, clock.now)

  private fun product(id: String, price: Long? = 1500, max: Long? = null, stock: String = "unverified") =
    StoreProduct(id = id, name = "کالا $id", unit = "عدد", priceMinor = price, maxQtyMilli = max, stock = StockBadge(stock))

  @Test
  fun `افزودن یکی یکی و سقفِ هر سفارش`() {
    val c = carts()
    assertEquals(AddOutcome.Added(1000), c.add(shop, product("p1", max = 2000)))
    assertEquals(AddOutcome.Added(2000), c.add(shop, product("p1", max = 2000)))
    assertEquals(AddOutcome.AtLimit(2000), c.add(shop, product("p1", max = 2000)))
    assertEquals(2000L, c.cart("nan")!!.qtyOf("p1"))
  }

  @Test
  fun `ناموجود و بی‌قیمت به سبد نمی‌رود؛ «در حالِ بررسی» می‌رود`() {
    val c = carts()
    assertEquals(AddOutcome.NotOrderable, c.add(shop, product("p1", stock = "out_of_stock")))
    assertEquals(AddOutcome.NotOrderable, c.add(shop, product("p2", stock = "unavailable")))
    assertEquals(AddOutcome.NotOrderable, c.add(shop, product("p3", price = null)))
    assertNull(c.cart("nan"))
    assertTrue(c.add(shop, product("p4", stock = "unverified")) is AddOutcome.Added)
  }

  @Test
  fun `جمع با همان گردکردنِ سرور`() {
    val c = carts()
    //  ۲۵ × ۰٫۵۸۰ = ۱۴٫۵ ⇒ سرور ۱۵ افغانی (نیمه رو به بالا)
    c.add(shop, product("p1", price = 2500))
    c.setQty("nan", "p1", 580)
    assertEquals(1500L, c.cart("nan")!!.subtotalMinor)
    //  ۳ × ۱۲٫۳۴ = ۳۷٫۰۲ ⇒ ۳۷ افغانی
    c.add(shop, product("p2", price = 1234))
    c.setQty("nan", "p2", 3000)
    assertEquals(1500L + 3700L, c.cart("nan")!!.subtotalMinor)
  }

  @Test
  fun `مقدارِ صفر برمی‌دارد و سبدِ خالی پاک می‌شود`() {
    val c = carts()
    c.add(shop, product("p1"))
    c.decrement("nan", "p1")
    assertNull(c.cart("nan"))
    assertTrue(c.openCarts().isEmpty())
  }

  @Test
  fun `بیشتر از سقف ⇒ همان سقف`() {
    val c = carts()
    c.add(shop, product("p1", max = 3000))
    c.setQty("nan", "p1", 9000)
    assertEquals(3000L, c.cart("nan")!!.qtyOf("p1"))
  }

  @Test
  fun `سبد پس از بسته شدنِ برنامه می‌ماند و هر دکان جداست`() {
    carts().apply {
      add(shop, product("p1"))
      clock.at += 10
      add(CartShop("gul", "گل‌فروشی"), product("g1"))
    }
    val again = carts()
    assertEquals(listOf("gul", "nan"), again.openCarts().map { it.slug })
    assertEquals("نانوایی", again.cart("nan")!!.title)
    assertEquals(1, again.cart("gul")!!.lines.size)
  }

  @Test
  fun `حافظهٔ خراب سبدِ خالی است، نه استثنا`() {
    storage.write(ShopperStorage.CARTS, "{not json")
    assertTrue(carts().openCarts().isEmpty())
  }

  @Test
  fun `تازه کردن — قیمتِ تازه، ناموجود و نبوده شمرده می‌شود`() {
    val c = carts()
    c.add(shop, product("p1", price = 1000))
    c.add(shop, product("p2", price = 1000))
    c.add(shop, product("p3", price = 1000))
    c.setQty("nan", "p1", 5000)
    val notice = c.applyRefresh(
      "nan",
      listOf(product("p1", price = 1200, max = 3000), product("p2", price = 1000, stock = "out_of_stock")),
    )
    assertEquals(RefreshNotice(priceChanged = 1, gone = 2), notice)
    val cart = c.cart("nan")!!
    assertEquals(1200L, cart.line("p1")!!.priceMinor)
    assertEquals(3000L, cart.line("p1")!!.qtyMilli)
    assertTrue(cart.line("p2")!!.unavailable)
    assertTrue(cart.line("p3")!!.unavailable)
    assertTrue(cart.hasUnavailable)
    //  دوباره همان ⇒ «تازه ناموجود» دوباره شمرده نمی‌شود
    assertEquals(RefreshNotice(0, 0), c.applyRefresh("nan", listOf(product("p1", price = 1200, max = 3000))))
    c.removeUnavailable("nan")
    assertFalse(c.cart("nan")!!.hasUnavailable)
  }

  @Test
  fun `برگهٔ قیمتِ سرور تغییرها را می‌گوید و سبد را تازه می‌کند`() {
    val c = carts()
    c.add(shop, product("p1", price = 1000))
    c.add(shop, product("p2", price = 2000))
    val changes = c.applyQuote(
      "nan",
      Quote(lines = listOf(OrderLine("p1", "نان", unitPriceMinor = 1100), OrderLine("p2", "شیر", unitPriceMinor = 2000))),
    )
    assertEquals(listOf(PriceChange("p1", "نان", 1000, 1100)), changes)
    assertEquals(1100L, c.cart("nan")!!.line("p1")!!.priceMinor)
  }

  @Test
  fun `سقفِ سرور و ناموجودیِ هنگامِ ثبت روی سبد`() {
    val c = carts()
    c.add(shop, product("p1"))
    c.setQty("nan", "p1", 4000)
    c.add(shop, product("p2"))
    c.applyLimit("nan", "p1", 2000)
    c.markUnavailable("nan", listOf("p2"))
    assertEquals(2000L, c.cart("nan")!!.qtyOf("p1"))
    assertTrue(c.cart("nan")!!.line("p2")!!.unavailable)
  }

  @Test
  fun `«خرید دوباره» فقط قلم‌های موجود با قیمتِ امروز`() {
    val c = carts()
    val n = c.fillFromReorder(
      shop,
      Reorder(
        orderNo = 3,
        lines = listOf(
          ReorderLine("p1", "نان", "عدد", qtyMilli = 5000, previousPriceMinor = 900, priceMinor = 1000, maxQtyMilli = 2000, available = true),
          ReorderLine("p2", "شیر", "لیتر", qtyMilli = 1000, previousPriceMinor = 900, available = false, reason = "out_of_stock"),
        ),
      ),
    )
    assertEquals(1, n)
    val line = c.cart("nan")!!.line("p1")!!
    assertEquals(1000L, line.priceMinor)
    assertEquals(2000L, line.qtyMilli)
    assertNull(c.cart("nan")!!.line("p2"))
  }

  @Test
  fun `شمارِ واحدها برای نشانِ سبد`() {
    val c = carts()
    c.add(shop, product("p1"))
    c.setQty("nan", "p1", 2000)
    c.add(shop, product("p2"))
    assertEquals(3L, c.cart("nan")!!.unitCount)
  }
}
