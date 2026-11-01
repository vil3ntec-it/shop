package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  «انتخاب از بانک» در برنامه — IMG-BANK-03، ۰۵ و ۱۷: تطبیقِ دقیق بی تیک، ولی نامِ
 *  مشابه با وزن/برند/بسته‌بندیِ متفاوت دلیل نشان می‌دهد و تأییدِ دستی می‌خواهد؛
 *  بارکدِ متفاوت اصلاً انتخاب نمی‌شود. همان قاعدهٔ `classify` ِ سرور (`image-bank.test.js`).
 */
class ImageBankAssessTest {

  private val tea = "5901234123457"   //  EAN-13 با رقمِ کنترلِ درست

  private fun hit(
    certain: Boolean, kind: String, ambiguous: Boolean = false, distinguish: List<String> = emptyList(),
    gtin: String = tea, name: String = "چای سبز", alt: List<String> = emptyList(),
  ) = ImageBank.Hit(
    productId = "bp1", name = name, brand = "احمد", variant = "", size = "500 g", packCount = 1, packaging = "",
    gtin = gtin, altNames = alt, certain = certain, kind = kind, reasons = listOf(kind),
    ambiguous = ambiguous, distinguish = distinguish, images = emptyList(),
  )

  @Test fun `IMG-BANK-03 بارکدِ دقیق و همان کالا ⇒ بی تیک`() {
    val a = ImageBank.assess(hit(certain = true, kind = "barcode_exact"), "چای سبز", tea)
    assertEquals("", a.block)
    assertFalse(a.needsConfirm)
    assertTrue(a.why.isEmpty())
  }

  @Test fun `IMG-BANK-03 رقمِ فارسیِ بارکد همان بارکد است`() {
    val a = ImageBank.assess(hit(certain = true, kind = "barcode_exact"), "چای سبز", "۵۹۰۱۲۳۴۱۲۳۴۵۷")
    assertFalse(a.needsConfirm)
  }

  @Test fun `IMG-BANK-05 نامِ یکسان با وزنِ متفاوت ⇒ مبهم، دلیل و تیکِ اجباری`() {
    val a = ImageBank.assess(hit(certain = false, kind = "name_exact", ambiguous = true, distinguish = listOf("size")), "چای سبز", "")
    assertEquals("", a.block)
    assertTrue(a.needsConfirm)
    assertTrue(a.why.joinToString(), a.why.any { "وزن/حجم" in it })
    assertTrue(a.why.joinToString(), a.why.any { "نه بارکد" in it })
  }

  @Test fun `IMG-BANK-17 نامِ مشابه ولی برند و بسته‌بندیِ متفاوت ⇒ دلیلِ تطبیق و تأییدِ دستی`() {
    val a = ImageBank.assess(
      hit(certain = false, kind = "spelling_tolerant", ambiguous = true, distinguish = listOf("brand", "packaging"), gtin = ""),
      "چاي سبز", "",
    )
    assertTrue(a.needsConfirm)
    assertTrue(a.why.joinToString(), a.why.any { "برند" in it && "بسته‌بندی" in it })
    assertTrue(a.why.joinToString(), a.why.any { "املای نزدیک" in it })
  }

  @Test fun `حتی بارکدِ دقیق با چند نوعِ هم‌نام تیک می‌خواهد`() {
    val a = ImageBank.assess(hit(certain = true, kind = "barcode_exact", ambiguous = true, distinguish = listOf("variant")), "چای سبز", tea)
    assertTrue(a.needsConfirm)
  }

  @Test fun `بارکدِ متفاوت ⇒ انتخاب نمی‌شود، حتی با تیک`() {
    val a = ImageBank.assess(hit(certain = false, kind = "name_exact"), "چای سبز", "4006381333931")
    assertTrue(a.block.isNotEmpty())
  }

  @Test fun `بی بارکدِ بانک ⇒ تأییدِ دستی با دلیل`() {
    val a = ImageBank.assess(hit(certain = true, kind = "sku_exact", gtin = ""), "چای سبز", "")
    assertTrue(a.needsConfirm)
    assertTrue(a.why.isNotEmpty())
  }
}
