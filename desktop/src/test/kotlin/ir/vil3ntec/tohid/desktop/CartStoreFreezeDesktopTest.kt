package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalePost
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  POS-12 — `CartStore`ِ واقعی وسطِ جابه‌جاییِ فاکتور (`freeze`): افزودن، مشتری، پاک
 *  کردن و ثبت رد می‌شوند و `set` می‌گوید (`false`) تا صفحه بوقِ خطا بزند و «اضافه نشد» بگوید.
 */
class CartStoreFreezeDesktopTest {
  private val draft get() = File(DesktopContext.filesDir, CartStore.FILE)

  @Before fun before() { draft.delete() }
  @After fun after() { draft.delete() }

  @Test
  fun `سبدِ قفل چیزی نمی‌پذیرد و می‌گوید`() = runBlocking<Unit> {
    val cart = CartStore(DesktopContext)
    val rice = SalesEngine.CartLine("p1", 2.0)
    assertTrue(cart.set(listOf(rice)))
    cart.freeze(true)
    assertFalse("اسکن وسطِ جابه‌جایی", cart.set(listOf(rice, SalesEngine.CartLine("p2", 1.0))))
    assertFalse(cart.setDebtor("d1"))
    assertFalse(cart.clear())
    assertEquals(SalePost.Outcome.Busy, cart.post(ShopStore(DesktopContext)) { error("وسطِ جابه‌جایی ثبت نمی‌شود") })
    assertEquals(listOf(rice), cart.lines.value)
    assertNull(cart.debtorId.value)
    cart.freeze(false)
    assertTrue(cart.set(listOf(rice, SalesEngine.CartLine("p2", 1.0))))
    assertEquals(2, cart.lines.value.size)
    assertTrue(cart.clear())
  }
}
