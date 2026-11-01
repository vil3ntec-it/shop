package ir.vil3ntec.tohid.hw

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  POS-30 / HW-10 — نمایشگرِ مشتری (پنجرهٔ دوم در کامپیوتر): فقط نام، تعداد، واحد و مبلغِ هر ردیف و جمع؛
 *  نه سود، نه موجودی، نه قیمتِ خرید، نه نامِ مشتری. پس از فروش «سپاس» با مبلغ تا نخستین کالای سبدِ بعدی.
 *  پنجرهٔ بسته ⇒ هیچ چیز عوض نمی‌شود؛ نمایشگرِ قطبیِ تنظیم‌نشده ⇒ هیچ فرستادنی.
 */
class CustomerFacingTest {

  private val tea = Product(id = "p1", name = "چای سبز", unit = "بسته", purchasePrice = 100.0, salePrice = 150.0)
  private val rice = Product(id = "p2", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0)
  private val d = ShopData(products = listOf(tea, rice))

  @Before fun reset() {
    CustomerFacing.open.value = false
    CustomerFacing.view.value = CustomerFacing.View()
    Pole.port = ""
  }

  @After fun close() { CustomerFacing.open.value = false }

  @Test fun `پنجرهٔ بسته — نمای مشتری دست نمی‌خورد`() {
    CustomerFacing.showCart(d, listOf(SalesEngine.CartLine("p1", 2.0)))
    assertEquals(CustomerFacing.View(), CustomerFacing.view.value)
  }

  @Test fun `سبد — نام، تعداد، واحد و مبلغِ هر ردیف با قیمتِ همان ردیف، و جمع`() {
    CustomerFacing.open.value = true
    val cart = listOf(
      SalesEngine.CartLine("p1", 2.0),
      SalesEngine.CartLine("p2", 1.5, unitPrice = 280.0),          // قیمتِ دستیِ ردیف
      SalesEngine.CartLine("free-1", 1.0, unitPrice = 40.0, label = "کیسه"),
    )
    CustomerFacing.showCart(d, cart)
    val v = CustomerFacing.view.value
    assertEquals(
      listOf(
        CustomerFacing.Line("چای سبز", 2.0, "بسته", 300.0),
        CustomerFacing.Line("برنج", 1.5, "کیلو", 420.0),
        CustomerFacing.Line("کیسه", 1.0, "", 40.0),
      ),
      v.lines,
    )
    assertEquals(SalesEngine.cartTotal(d, cart), v.total, 0.0)
    assertEquals(760.0, v.total, 0.0)
    assertNull(v.paid)
  }

  @Test fun `نمای مشتری جای سود، موجودی، قیمتِ خرید یا نامِ مشتری ندارد`() {
    fun fields(c: Class<*>) = c.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
    assertEquals(setOf("name", "quantity", "unit", "total"), fields(CustomerFacing.Line::class.java))
    assertEquals(setOf("lines", "total", "paid"), fields(CustomerFacing.View::class.java))
  }

  @Test fun `پس از فروش «سپاس» با مبلغ می‌ماند تا نخستین کالای سبدِ بعدی`() {
    CustomerFacing.open.value = true
    CustomerFacing.showCart(d, listOf(SalesEngine.CartLine("p1", 1.0)))
    CustomerFacing.sold(150.0)
    assertEquals(CustomerFacing.View(emptyList(), 150.0, paid = 150.0), CustomerFacing.view.value)
    //  سبدِ خالیِ پس از فروش «سپاس» را پاک نمی‌کند
    CustomerFacing.showCart(d, emptyList())
    assertEquals(150.0, CustomerFacing.view.value.paid!!, 0.0)
    //  نخستین کالای سبدِ بعدی
    CustomerFacing.showCart(d, listOf(SalesEngine.CartLine("p2", 1.0)))
    assertNull(CustomerFacing.view.value.paid)
    assertEquals(listOf("برنج"), CustomerFacing.view.value.lines.map { it.name })
  }

  @Test fun `نمایشگرِ قطبیِ تنظیم‌نشده — هیچ فرستادنی و هیچ خطا`() {
    Pole.port = ""
    CustomerFacing.open.value = true
    CustomerFacing.showCart(d, listOf(SalesEngine.CartLine("p1", 1.0)))
    CustomerFacing.sold(150.0)
    assertTrue(CustomerFacing.view.value.paid == 150.0)
  }
}
