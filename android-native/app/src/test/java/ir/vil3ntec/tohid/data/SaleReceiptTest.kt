package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.unitMoney
import org.junit.Assert.assertEquals
import org.junit.Test

/** S3 — عددهای فاکتورِ صفحه و چاپ از خودِ قلمِ ثبت‌شده */
class SaleReceiptTest {

  private val sale = Sale(id = "s1", total = 1000.0, finalTotal = 950.0, discount = 50.0, promoDiscountMinor = 5000)

  @Test fun `قلمِ با تخفیفِ خودکار — قیمتِ فهرست، سهمِ تخفیف و جمعِ ثبت‌شده`() {
    //  ۳ × ۳۵۰ = ۱۰۵۰، تخفیفِ خودکار ۵۰ ⇒ ۱۰۰۰؛ قیمتِ واحدِ ثبت‌شده ۳۳۳٫۳۳…
    val item = SaleItem(id = "i", saleId = "s1", productId = "p", quantity = 3.0, unitPrice = 1000.0 / 3, totalPrice = 1000.0,
      listPriceMinor = 35000, promo = listOf(SalePromo("r1", 1, 5000)))
    val l = SaleReceipt.line(item)
    assertEquals(SaleReceipt.Line(350.0, 50.0, 1000.0), l)
    assertEquals(l.total, SalesEngine.lineTotal(l.unit, item.quantity) - l.promo, 0.0)
  }

  @Test fun `قلمِ قدیمی با قیمتِ واحدِ اعشاری — با اعشار نوشته می‌شود و جمع همان`() {
    val item = SaleItem(id = "i", saleId = "s1", productId = "p", quantity = 3.0, unitPrice = 1000.0 / 3, totalPrice = 1000.0)
    val l = SaleReceipt.line(item)
    assertEquals(0.0, l.promo, 0.0)
    assertEquals(1000.0, l.total, 0.0)
    assertEquals("333.33", unitMoney(l.unit))
    assertEquals("1,250", unitMoney(1250.0))
    assertEquals("1,234.50", unitMoney(1234.5))
    assertEquals("0", unitMoney(Double.NaN))
  }

  @Test fun `جمع‌ها مثلِ خلاصهٔ صندوق — پیش از تخفیفِ خودکار، ردیفِ آن با نام، بعد تخفیفِ دستی`() {
    val items = listOf(
      SaleItem(id = "a", saleId = "s1", productId = "p", promo = listOf(SalePromo("r1", 1, 3000), SalePromo("r2", 1, 1000))),
      SaleItem(id = "b", saleId = "s1", productId = "q", promo = listOf(SalePromo("r2", 1, 1000), SalePromo("gone", 1, 0))),
    )
    val names = mapOf("r1" to "ده درصد", "r2" to "کدِ عید")
    val t = SaleReceipt.totals(sale, items) { names[it] }
    assertEquals(SaleReceipt.Totals(1050.0, 50.0, listOf("ده درصد", "کدِ عید")), t)
    assertEquals("تخفیفِ خودکار (ده درصد، کدِ عید)", SaleReceipt.promoLabel(t.names))
    assertEquals("تخفیفِ خودکار", SaleReceipt.promoLabel(emptyList()))
    //  فاکتورِ بی فیلد (خاموش یا قدیمی) ⇒ همان «جمع اقلام» و بی ردیفِ تخفیفِ خودکار
    assertEquals(SaleReceipt.Totals(1000.0, 0.0, emptyList()), SaleReceipt.totals(sale.copy(promoDiscountMinor = null), items) { names[it] })
    assertEquals(SaleReceipt.Totals(1000.0, 0.0, emptyList()), SaleReceipt.totals(sale.copy(promoDiscountMinor = 0), items) { names[it] })
  }
}
