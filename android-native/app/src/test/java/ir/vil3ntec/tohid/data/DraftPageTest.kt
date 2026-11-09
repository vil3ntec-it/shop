package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.ui.components.pos.repriceCart
import ir.vil3ntec.tohid.core.sale.PriceList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-13 — تخفیف، روشِ پرداخت، دریافتی و فروشندهٔ فاکتور با پیش‌نویس (`SaleDraft.page`) می‌روند و برمی‌گردند؛ پیش‌نویسِ بی آن‌ها بایت‌به‌بایت همان پیشین. و POS-18: ردیفِ فیِ ثابتِ برابر با
 *  خرده در سبدِ خرده «زنده» نمی‌شود (پیش‌تر تخفیفِ خودکار و قیمتِ تازهٔ کالا را می‌گرفت).
 */
class DraftPageTest {

  private val cart = listOf(SalesEngine.CartLine("p1", 2.0, unitPrice = 100.0))
  private val page = SaleDraft.Page(discount = "500", discountAmount = true, payment = "CREDIT", paid = "200", seller = "زهرا")

  @Test
  fun `تخفیف و روشِ پرداخت با پیش‌نویس می‌مانند`() {
    val d = SaleDraft.of("K1", cart, "d1", 42, page = page)
    val back = SaleDraftCodec.decode(SaleDraftCodec.encode(d))!!
    assertEquals(page, back.page)
    assertEquals(HeldExtras("500", SalesEngine.Payment.CREDIT, "200", discountAmount = true, seller = "زهرا"), back.page!!.extras())
    //  سبدِ خالی چیزی نگه نمی‌دارد
    assertNull(SaleDraft.of(null, emptyList(), null, 42, page = page).page)
  }

  @Test
  fun `پیش‌نویسِ بی صفحه بایت‌به‌بایت همان پیشین است`() {
    val plain = SaleDraftCodec.encode(SaleDraft.of("K1", cart, "d1", 42))
    assertFalse(plain.contains("page"))
    assertEquals(plain, SaleDraftCodec.encode(SaleDraft.of("K1", cart, "d1", 42, page = SaleDraft.Page.of(HeldExtras()))))
    //  پیش‌نویسِ نسخهٔ پیش ⇒ بی صفحه
    assertNull(SaleDraftCodec.decode("""{"v":2,"key":"K1","lines":[{"productId":"p1","quantity":2.0}]}""")!!.page)
  }

  @Test
  fun `هیچ ⇒ بی صفحه؛ واحدِ مبلغ بی عدد معنا ندارد`() {
    assertNull(SaleDraft.Page.of(HeldExtras()))
    assertNull(SaleDraft.Page.of(HeldExtras(discountAmount = true)))
    assertEquals("CASH", SaleDraft.Page.of(HeldExtras(payment = SalesEngine.Payment.CASH))!!.payment)
    //  نامِ ناشناختهٔ روش ⇒ صفحه از مشتری می‌سازد (`null`)
    assertNull(SaleDraft.Page(payment = "WALLET?").extras().payment)
  }

  @Test
  fun `صفحه فقط فیلدهای نوشته‌شده را دارد`() {
    val json = SaleDraftCodec.encode(SaleDraft.of("K1", cart, null, 42, page = SaleDraft.Page.of(HeldExtras("10"))))
    assertTrue(json, json.contains(""""page":{"discount":"10"}"""))
  }

  @Test
  fun `ردیفِ فیِ ثابتِ برابر با خرده زنده نمی‌شود`() {
    val p = Product(id = "p1", name = "چای", salePrice = 100.0, wholesalePrice = 90.0)
    val d = ShopData(products = listOf(p))
    val fixed = listOf(SalesEngine.CartLine("p1", 2.0, unitPrice = 100.0), SalesEngine.CartLine("p1", 1.0))
    val retail = repriceCart(d, fixed, PriceList.RETAIL)
    //  پیش از رفع: `unitPrice = null` ⇒ تخفیفِ خودکار و قیمتِ تازهٔ کالا می‌گرفت
    assertEquals(100.0, retail[0].unitPrice)
    assertNull(retail[1].unitPrice)
    assertTrue("بی تغییر ⇒ همان فهرست", retail === fixed)
    //  همان ردیف در لیستِ عمده عمده می‌شود (فیِ برابر با خرده «از لیست» است)
    assertEquals(90.0, repriceCart(d, fixed, PriceList.WHOLESALE)[0].unitPrice)
    //  قیمتِ کالا عوض شد ⇒ ردیفِ ثابت همان پول
    val dearer = d.copy(products = listOf(p.copy(salePrice = 120.0)))
    assertEquals(200.0, SalesEngine.cartTotal(dearer, repriceCart(dearer, retail.take(1), PriceList.RETAIL)), 0.0)
  }
}
