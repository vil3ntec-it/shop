package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.print.PrintSpooler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  POS-41 — Alt+P ِ پنلِ پرداخت روی `CartStore` ِ واقعی: چاپِ همین فاکتور وارونهٔ «چاپِ خودکار» ِ دستگاه می‌شود، زدنِ دوباره
 *  همان تنظیم است، و سبدِ خالی (ثبت یا خالی کردن) انتخاب را می‌بَرد تا فاکتورِ بعدی همان امروز چاپ شود.
 */
class CartStorePrintDesktopTest {
  private val draft get() = File(DesktopContext.filesDir, CartStore.FILE)
  private var auto = false

  @Before fun before() { draft.delete(); auto = PrintSpooler.autoPrint(DesktopContext); PrintSpooler.setAutoPrint(DesktopContext, false) }
  @After fun after() { draft.delete(); PrintSpooler.setAutoPrint(DesktopContext, auto) }

  @Test
  fun `چاپِ همین فاکتور فقط تا خالی شدنِ سبد می‌ماند`() {
    val cart = CartStore(DesktopContext)
    assertNull(cart.print.value)
    assertTrue(cart.set(listOf(SalesEngine.CartLine("p1", 1.0))))
    //  «چاپِ خودکار» خاموش ⇒ «این یکی چاپ»؛ زدنِ دوباره ⇒ همان تنظیم
    cart.togglePrint()
    assertEquals(true, cart.print.value)
    cart.togglePrint()
    assertNull(cart.print.value)
    cart.togglePrint()
    assertTrue(cart.clear())
    assertNull(cart.print.value)

    //  «چاپِ خودکار» روشن ⇒ «این یکی نه»؛ برداشتنِ آخرین ردیف همان خالی کردن است
    PrintSpooler.setAutoPrint(DesktopContext, true)
    assertTrue(cart.set(listOf(SalesEngine.CartLine("p1", 1.0))))
    cart.togglePrint()
    assertEquals(false, cart.print.value)
    assertTrue(cart.set(emptyList()))
    assertNull(cart.print.value)
  }

}
