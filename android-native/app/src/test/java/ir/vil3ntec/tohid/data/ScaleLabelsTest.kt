package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.hw.WeightBarcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  برچسبِ ترازو در صندوق (POS-29، S04): کالا با کدِ ترازو پیدا می‌شود، وزن به
 *  واحدِ کالا برمی‌گردد، رقمِ کنترلِ غلط رد می‌شود و بی‌قاعده هیچ اتفاقی نمی‌افتد.
 */
class ScaleLabelsTest {

  private val rice = Product(id = "rice", name = "برنج", unit = "کیلو", salePrice = 120.0, barcodes = listOf("00150"))
  private val saffron = Product(id = "saf", name = "زعفران", unit = "گرم", salePrice = 900.0, barcodes = listOf("2100777"))
  private val soap = Product(id = "soap", name = "صابون", unit = "عدد", salePrice = 50.0, barcodes = listOf("00999"))
  private val plain = Product(id = "p20", name = "کالای ۲۰", unit = "عدد", barcodes = listOf("2000000000008"))
  private val all = listOf(rice, saffron, soap, plain)
  private val index = buildMap { all.forEach { p -> p.barcodes.forEach { put(it, p.id) } } }
  private val rules = listOf(WeightBarcode.Rule(prefix = "21"), WeightBarcode.Rule(prefix = "20"))

  @Test fun `S04 — 2100150012507 یعنی ۱٫۲۵۰ کیلو برنج`() {
    val m = ScaleLabels.match(rules, "2100150012507", index, all)
    assertTrue(m is ScaleLabels.Match.Weighed)
    m as ScaleLabels.Match.Weighed
    assertEquals("rice", m.product.id)
    assertEquals(1.25, m.quantity, 0.0)
  }

  @Test fun `رقمِ کنترلِ غلط رد می‌شود، نه «کالای ناشناس»`() {
    assertTrue(ScaleLabels.match(rules, "2100150012508", index, all) is ScaleLabels.Match.Rejected)
  }

  @Test fun `کالای گرمی — وزن به گرم؛ کدِ هفت‌رقمیِ پیشوند و کد هم پیدا می‌شود`() {
    val code = WeightBarcode.build(rules[0], "00777", 25) // ۰٫۰۲۵ کیلو
    val m = ScaleLabels.match(rules, code, index, all) as ScaleLabels.Match.Weighed
    assertEquals("saf", m.product.id)
    assertEquals(25.0, m.quantity, 0.0)
  }

  @Test fun `کالای دانه‌ای با برچسبِ ترازو رد می‌شود`() {
    val code = WeightBarcode.build(rules[0], "00999", 1000)
    assertTrue(ScaleLabels.match(rules, code, index, all) is ScaleLabels.Match.Rejected)
  }

  @Test fun `بارکدِ ثبت‌شده و بی‌قاعده — راهِ همیشگی`() {
    //  بارکدِ کالایی که خودش با ۲۰ شروع می‌شود، برچسبِ ترازو حساب نمی‌شود
    assertNull(ScaleLabels.match(rules, "2000000000008", index, all))
    //  پیش‌فرض: هیچ قاعده‌ای ⇒ هیچ
    assertNull(ScaleLabels.match(emptyList(), "2100150012507", index, all))
    //  بارکدِ معمولی
    assertNull(ScaleLabels.match(rules, "6260100120014", index, all))
  }
}
