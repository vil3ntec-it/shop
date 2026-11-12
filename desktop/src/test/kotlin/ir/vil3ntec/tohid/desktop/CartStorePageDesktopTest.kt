package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.HeldExtras
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.SaleDraftCodec
import ir.vil3ntec.tohid.data.SalesEngine
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
 *  POS-13 — `CartStore` ِ واقعی: تخفیف، روشِ پرداخت و دریافتیِ صفحهٔ صندوق (`setPage`) با پیش‌نویس به دیسک می‌روند و پس
 *  از بسته شدنِ برنامه (نمونهٔ تازه از همان فایل) برمی‌گردند. پیش از رفع فقط ردیف‌ها برمی‌گشتند و فاکتورِ ۵۰۰ ؋ تخفیف و
 *  نسیه، نقد و بی تخفیف ثبت می‌شد. تا پاسخِ پرسشِ بازیابی، صفحهٔ تازه‌باز با کادرهای خالی پیش‌نویس را پاک نمی‌کند.
 */
class CartStorePageDesktopTest {
  private val draft get() = File(DesktopContext.filesDir, CartStore.FILE)

  //  نوشتن روی رشتهٔ جداست: پیش از پاک کردن، نوشتنِ در صفِ آزمونِ پیشین باید رسیده باشد
  @Before fun before() { CartStore.awaitWrites(); draft.delete() }
  @After fun after() { CartStore.awaitWrites(); draft.delete() }

  private val x = HeldExtras("500", SalesEngine.Payment.CREDIT, "200", discountAmount = true, seller = "زهرا")

  /** نوشتنِ پیش‌نویس روی رشتهٔ جداست — تا رسیدنِ [want] به دیسک */
  private fun onDisk(want: (SaleDraft?) -> Boolean): SaleDraft? {
    val until = System.currentTimeMillis() + 5_000
    while (System.currentTimeMillis() < until) {
      val d = SaleDraftCodec.decode(runCatching { draft.readText() }.getOrNull())
      if (want(d)) return d
      Thread.sleep(10)
    }
    return SaleDraftCodec.decode(runCatching { draft.readText() }.getOrNull())
  }

  @Test
  fun `تخفیف و روشِ پرداخت پس از بسته شدنِ برنامه برمی‌گردند`() {
    val cart = CartStore(DesktopContext)
    assertTrue(cart.set(listOf(SalesEngine.CartLine("p1", 2.0))))
    assertTrue(cart.setPage(x))
    assertEquals(SaleDraft.Page.of(x), onDisk { it?.page != null }?.page)

    //  برنامه دوباره باز شد
    val again = CartStore(DesktopContext)
    assertEquals(SaleDraft.Page.of(x), again.page.value)
    assertEquals(SaleDraft.Page.of(x), again.recovered.value?.page)
    //  صفحه با کادرهای خالی باز شد و پرسش هنوز بی‌پاسخ ⇒ پیش‌نویس دست نمی‌خورد
    assertFalse(again.setPage(HeldExtras(payment = SalesEngine.Payment.CASH)))
    assertEquals(SaleDraft.Page.of(x), again.page.value)
    //  «ادامه» ⇒ همان تخفیف و روش (صفحه از `HeldSession` می‌گیرد)
    again.resolveRecovery(again.recovered.value)
    assertEquals(x, again.page.value!!.extras())
    assertEquals(x, again.snapshot().page!!.extras())
  }

  @Test
  fun `خالی کردن، دور انداختن و جابه‌جایی صفحه را با فاکتور می‌برند`() = runBlocking<Unit> {
    val cart = CartStore(DesktopContext)
    //  سبدِ خالی چیزی نگه نمی‌دارد
    assertTrue(cart.setPage(x))
    assertNull(cart.page.value)
    cart.set(listOf(SalesEngine.CartLine("p1", 1.0)))
    cart.setPage(x)
    assertTrue(cart.clear())
    assertNull(cart.page.value)
    assertNull(onDisk { it?.lines?.isEmpty() == true && it.page == null }?.page)

    //  فاکتورِ نگه‌داشته‌ای که برگشت: صفحه همراهِ ردیف‌ها در همان نوشتن
    val held = SaleDraft.of("H1", listOf(SalesEngine.CartLine("p2", 3.0)), null, 1, page = SaleDraft.Page.of(x))
    assertTrue(cart.replace(held))
    assertEquals(SaleDraft.Page.of(x), cart.page.value)
    assertEquals(SaleDraft.Page.of(x), SaleDraftCodec.decode(draft.readText())!!.page)
    //  فاکتورِ دیگر بی صفحه ⇒ صفحهٔ فاکتورِ پیشین به آن نمی‌رسد
    assertTrue(cart.replace(SaleDraft.of("H2", listOf(SalesEngine.CartLine("p3", 1.0)), null, 1)))
    assertNull(cart.page.value)

    //  وسطِ جابه‌جایی ⇒ نه
    cart.freeze(true)
    assertFalse(cart.setPage(x))
    cart.freeze(false)
    assertTrue(cart.setPage(x))
    onDisk { it?.key == "H2" && it.page != null }
    //  «دور انداختن» ِ پیش‌نویسِ بازیابی ⇒ صفحه هم
    val reopened = CartStore(DesktopContext)
    assertEquals(SaleDraft.Page.of(x), reopened.page.value)
    reopened.resolveRecovery(null)
    assertNull(reopened.page.value)
    assertTrue(reopened.lines.value.isEmpty())
  }
}
