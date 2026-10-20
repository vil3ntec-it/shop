package ir.vil3ntec.tohid.core.shopper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShopperLinksTest {

  private fun parse(s: String, hosts: Set<String> = emptySet()) = ShopperLinks.link(s, hosts)

  @Test
  fun `زیردامنهٔ ویترین`() {
    assertEquals(ShopperLink.Shop("nan-e-gul"), parse("https://nan-e-gul.vill3n.top"))
    assertEquals(ShopperLink.Shop("nan"), parse("https://NAN.vill3n.top/"))
    assertEquals(ShopperLink.Shop("nan"), parse("nan.vill3n.top"))
    assertEquals(ShopperLink.Shop("nan"), parse("http://nan.vill3n.top/?utm=qr"))
  }

  @Test
  fun `مسیرِ store روی دامنه و سرورِ تنظیم‌شده`() {
    assertEquals(ShopperLink.Shop("nan"), parse("https://vill3n.top/store/nan"))
    assertEquals(ShopperLink.Shop("nan"), parse("https://www.vill3n.top/store/nan/"))
    assertEquals(ShopperLink.Shop("nan"), parse("https://shop.example.af/store/nan", hosts = setOf("shop.example.af")))
    //  سایتِ دیگر با مسیرِ شبیه، دکانِ ما نیست
    assertNull(parse("https://evil.example/store/nan"))
  }

  @Test
  fun `کالا، سفارش و سبد از هشِ نشانی`() {
    assertEquals(ShopperLink.Shop("nan", productId = "p-12"), parse("https://nan.vill3n.top/#/p/p-12"))
    assertEquals(
      ShopperLink.Order("nan", Fixtures.TOKEN),
      parse("https://vill3n.top/store/nan#/o/${Fixtures.TOKEN}"),
    )
    assertEquals(ShopperLink.Shop("nan", openCart = true), parse("https://nan.vill3n.top/#/cart"))
    //  توکنِ بدشکل ⇒ همان صفحهٔ دکان
    assertEquals(ShopperLink.Shop("nan"), parse("https://nan.vill3n.top/#/o/abc"))
  }

  @Test
  fun `نشانیِ نامعتبر یا رزرو`() {
    assertNull(parse(""))
    assertNull(parse("https://api.vill3n.top"))
    assertNull(parse("https://a--b.vill3n.top"))
    assertNull(parse("https://x.y.vill3n.top"))
    assertNull(parse("ftp://nan.vill3n.top"))
    assertNull(parse("https://vill3n.top/market"))
    assertNull(parse("https://vill3n.top/store/NAN_1"))
    assertNull(parse("not a link at all"))
  }

  @Test
  fun `نشانیِ کوتاهِ دامنه و پیوندِ خودِ برنامه`() {
    assertEquals(ShopperLink.Shop("nan"), parse("https://vill3n.top/nan"))
    //  مسیرهای خودِ سایت دکان نیستند
    assertNull(parse("https://vill3n.top/kit"))
    assertNull(parse("https://shop.example.af/nan", hosts = setOf("shop.example.af")))
    assertEquals(ShopperLink.Shop("nan"), parse("vill3n://app/nan"))
    assertEquals(ShopperLink.Shop("nan"), parse("vill3n://app/shopper/shop/nan"))
    assertEquals(ShopperLink.Shop("nan", productId = "p:1"), parse("vill3n://app/shopper/shop/nan?product=p%3A1"))
    assertEquals(ShopperLink.Shop("nan", openCart = true), parse("vill3n://app/shopper/cart/nan"))
    assertEquals(ShopperLink.Order("nan", Fixtures.TOKEN), parse("vill3n://app/shopper/order/nan/${Fixtures.TOKEN}"))
    assertNull(parse("vill3n://app/shopper/order/nan/bad"))
    assertNull(parse("vill3n://other/nan"))
    assertNull(parse("vill3n://app/settings/security"))
  }

  @Test
  fun `درِ بیرونی — پیوند به مسیرِ برنامه`() {
    assertEquals("shopper/shop/nan", ShopperLinks.parse("https://nan.vill3n.top"))
    assertEquals("shopper/shop/nan?product=p-12", ShopperLinks.parse("https://vill3n.top/store/nan#/p/p-12"))
    assertEquals("shopper/order/nan/${Fixtures.TOKEN}", ShopperLinks.parse("https://nan.vill3n.top/#/o/${Fixtures.TOKEN}"))
    assertEquals("shopper/cart/nan", ShopperLinks.parse("vill3n://app/shopper/cart/nan"))
    assertEquals("shopper/shop/nan", ShopperLinks.parse("https://shop.example.af/store/nan", hosts = setOf("shop.example.af")))
    assertNull(ShopperLinks.parse("https://evil.example/store/nan"))
  }

  @Test
  fun `مسیرِ برنامه`() {
    assertEquals("shopper/shop/nan", ShopperRoutes.of(ShopperLink.Shop("nan")))
    assertEquals("shopper/shop/nan?product=p%3A1", ShopperRoutes.of(ShopperLink.Shop("nan", productId = "p:1")))
    assertEquals("shopper/cart/nan", ShopperRoutes.of(ShopperLink.Shop("nan", openCart = true)))
    assertEquals("shopper/order/nan/t", ShopperRoutes.of(ShopperLink.Order("nan", "t")))
  }
}
