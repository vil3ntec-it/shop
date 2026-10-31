package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.PriceWhy.Kind
import ir.vil3ntec.tohid.core.pos.PriceWhy.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  POS-22 — ردیفِ سبدِ صندوق ⇐ «چرا این قیمت؟»: قیمتِ فهرست از کالا، قیمتِ دستی از سبدِ پیش از
 *  تخفیفِ خودکار، سهمِ هر قاعده از `PromoEngine.Outcome.tags` و جمع از سبدِ پس از تخفیف — همان که
 *  سبد نشان می‌دهد.
 */
class PriceWhyLineTest {

  private val tea = Product(id = "p1", name = "چای", unit = "بسته", salePrice = 125.0)
  private val d = ShopData(products = listOf(tea))

  @Test
  fun `فهرست، تخفیفِ خودکار با نام و گرد کردن`() {
    val cart = listOf(SalesEngine.CartLine("p1", 2.0))
    //  تخفیفِ ۳۷٫۵۰ روی ردیف ⇒ قیمتِ واحدِ سبد ۱۰۶٫۲۵ ⇒ جمعِ ۲۱۲٫۵۰ که سبد به ۲۱۳ گرد می‌کند
    val priced = listOf(SalesEngine.CartLine("p1", 2.0, unitPrice = 106.25))
    val promo = PromoEngine.Outcome(
      gross = 250.0, discount = 37.5, applied = emptyList(), codes = emptyList(), cart = priced,
      tags = mapOf("p1" to PromoEngine.LineTag(12_500, listOf(SalePromo(id = "r1", name = "زمستانه", discountMinor = 3_750)))),
    )
    assertEquals(
      listOf(
        Row(Kind.LIST, 12_500), Row(Kind.BASE, 25_000), Row(Kind.PROMO, -3_750, "زمستانه"),
        Row(Kind.ROUNDING, 50), Row(Kind.TOTAL, 21_300),
      ),
      PriceWhyLine.rows(d, cart, priced, promo, "p1"),
    )
  }

  @Test
  fun `قیمتِ دستی بی تخفیفِ خودکار`() {
    val cart = listOf(SalesEngine.CartLine("p1", 3.0, unitPrice = 100.0))
    assertEquals(
      listOf(Row(Kind.LIST, 12_500), Row(Kind.MANUAL, 10_000, deltaMinor = -2_500), Row(Kind.TOTAL, 30_000)),
      PriceWhyLine.rows(d, cart, cart, null, "p1"),
    )
  }

  @Test
  fun `ردیفِ ناشناخته یا بیرون از بازه پنل ندارد`() {
    assertNull(PriceWhyLine.rows(d, emptyList(), emptyList(), null, "p1"))
    assertNull(PriceWhyLine.rows(d, listOf(SalesEngine.CartLine("gone", 1.0)), emptyList(), null, "gone"))
    assertNull(PriceWhyLine.rows(d, listOf(SalesEngine.CartLine("p1", 1_000_000.0)), emptyList(), null, "p1"))
  }
}
