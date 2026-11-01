package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  RPT-09 — `CartStore`ِ واقعی فقط «حذفِ ردیف» ِ کاربر را خبر می‌دهد (`onLinesRemoved`): ردیفی که با
 *  `set` از سبد رفت و `clear` (خالی کردنِ فاکتور)، با کلیدِ همان فاکتور. افزودن، کم کردنِ مقدار،
 *  جابه‌جاییِ فاکتورِ نگه‌داشته (`replace`) و `prune` ِ کالای پاک‌شده حذف نیستند، و خطای شنونده سبد را نمی‌شکند.
 */
class CartStoreLineVoidDesktopTest {
  private val draft get() = File(DesktopContext.filesDir, CartStore.FILE)

  @Before fun before() { draft.delete() }
  @After fun after() { draft.delete() }

  @Test
  fun `فقط حذفِ کاربر و با کلیدِ همان فاکتور`() = runBlocking<Unit> {
    val cart = CartStore(DesktopContext)
    val heard = mutableListOf<Pair<List<String>, String?>>()
    cart.onLinesRemoved = { lines, key -> heard += lines.map { it.productId } to key }
    val rice = SalesEngine.CartLine("p1", 2.0)
    val oil = SalesEngine.CartLine("p2", 1.0)

    assertTrue(cart.set(listOf(rice, oil)))
    val key = cart.saleKey()
    assertTrue(cart.set(listOf(rice, oil.copy(quantity = 3.0))))
    assertTrue(cart.set(listOf(rice, oil.copy(quantity = 1.0))))
    assertTrue("افزودن و مقدار حذف نیست", heard.isEmpty())

    assertTrue(cart.set(listOf(rice)))
    assertEquals(listOf(listOf("p2") to key), heard)

    assertTrue(cart.set(listOf(rice, oil)))
    assertTrue(cart.clear())
    assertEquals(listOf("p1", "p2") to key, heard.last())
    assertEquals(2, heard.size)
  }

  @Test
  fun `جابه‌جاییِ فاکتور و پاک‌سازیِ کالای حذف‌شده حذفِ ردیف نیستند`() = runBlocking<Unit> {
    val cart = CartStore(DesktopContext)
    val heard = mutableListOf<List<String>>()
    cart.onLinesRemoved = { lines, _ -> heard += lines.map { it.productId } }
    val rice = SalesEngine.CartLine("p1", 2.0)
    val oil = SalesEngine.CartLine("p2", 1.0)

    assertTrue(cart.set(listOf(rice, oil)))
    assertTrue(cart.replace(null))
    assertTrue(cart.lines.value.isEmpty())

    assertTrue(cart.set(listOf(rice, oil)))
    cart.prune(ShopData(products = listOf(Product(id = "p1", name = "برنج"))))
    assertEquals(listOf(rice), cart.lines.value)
    assertTrue(heard.isEmpty())

    //  شنونده‌ای که می‌شکند سبد را نمی‌شکند
    cart.onLinesRemoved = { _, _ -> error("شنونده") }
    assertTrue(cart.set(emptyList()))
    assertTrue(cart.lines.value.isEmpty())
  }

  @Test
  fun `بی شنونده همان رفتارِ پیشین`() {
    val cart = CartStore(DesktopContext)
    assertTrue(cart.set(listOf(SalesEngine.CartLine("p1", 2.0))))
    assertTrue(cart.clear())
    assertTrue(cart.lines.value.isEmpty())
  }
}
