package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.PackBarcodes.Checked
import ir.vil3ntec.tohid.core.product.PackBarcodes.Draft
import ir.vil3ntec.tohid.core.product.PackBarcodes.Pack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PRD-04: بارکدِ هر واحد — کارتنِ ۲۴ دانه‌ای با بارکدِ خودش؛ تکراری بین کالاها و بینِ واحدها نه. */
class PackBarcodesTest {

  private val owners = mapOf("111" to "p1", "222" to "p2", "900" to "p2")

  private fun check(vararg d: Draft, unit: String = "دانه", fractional: Boolean = false, own: List<String> = listOf("111"), self: String? = "p1") =
    PackBarcodes.check(d.toList(), unit, fractional, own, self) { owners[it] }

  private fun ok(c: Checked) = (c as? Checked.Ok)?.packs ?: error("رد شد: $c")
  private fun failed(c: Checked) = (c as? Checked.Failed)?.message ?: error("پذیرفته شد: $c")

  @Test fun cartonOfTwentyFourWithItsOwnBarcodes() {
    val packs = ok(check(Draft(" کارتن ", "۲۴", "۶۲۶۰۰۰۰۰۰۰۰۲۴، 6260000000031"), Draft(), Draft("شل", "6", "555")))
    assertEquals(listOf(Pack("کارتن", 24_000, listOf("6260000000024", "6260000000031")), Pack("شل", 6_000, listOf("555"))), packs)
    assertEquals("کارتن (24 دانه)", PackBarcodes.label(packs[0], "دانه"))
    //  فرم ⇐ بسته ⇐ فرم
    assertEquals(Draft("کارتن", "24", "6260000000024، 6260000000031"), PackBarcodes.draftOf(packs[0]))
    assertEquals(listOf(Pack("بسته", 250, listOf("7"))), ok(check(Draft("بسته", "0.25", "7"), unit = "کیلوگرم", fractional = true)))
    assertEquals("0.25", PackBarcodes.sizeText(250))
    assertEquals("1.5", PackBarcodes.sizeText(1_500))
    assertEquals("0.05", PackBarcodes.sizeText(50))
    assertEquals("2.005", PackBarcodes.sizeText(2_005))
  }

  @Test fun noBarcodeIsSharedWithAnotherProductOrWithTheUnitItself() {
    assertTrue(failed(check(Draft("کارتن", "24", "222"))).contains("کالای دیگری"))
    //  بارکدِ بستهٔ خودش در ویرایش آزاد است
    ok(check(Draft("کارتن", "24", "900"), own = emptyList(), self = "p2"))
    //  کالای تازه: هر بارکدِ گرفته تعارض است
    assertTrue(failed(check(Draft("کارتن", "24", "222"), self = null)).contains("کالای دیگری"))
    assertTrue(failed(check(Draft("کارتن", "24", "111"))).contains("خودِ کالا"))
    assertTrue(failed(check(Draft("کارتن", "24", "5"), Draft("شل", "6", "5"))).contains("دو بار"))
  }

  @Test fun everyPackNeedsANameASizeAndABarcode() {
    assertTrue(failed(check(Draft("", "24", "5"))).contains("نامِ بسته"))
    assertTrue(failed(check(Draft("کارتن", "", "5"))).contains("اندازه"))
    assertTrue(failed(check(Draft("کارتن", "0", "5"))).contains("صفر"))
    assertTrue(failed(check(Draft("کارتن", "-2", "5"))).contains("صفر"))
    assertTrue(failed(check(Draft("کارتن", "24", " "))).contains("بارکد ندارد"))
    //  یک واحد همان بارکدِ کالاست؛ همان نامِ واحد هم
    assertTrue(failed(check(Draft("جفت", "1", "5"))).contains("بارکدها"))
    assertTrue(failed(check(Draft("دانه", "24", "5"))).contains("همان واحد"))
    assertTrue(failed(check(Draft("کارتن", "24", "5"), Draft("کارتن ", "12", "6"))).contains("دو بار"))
    assertTrue(failed(check(Draft("ک".repeat(PackBarcodes.MAX_NAME + 1), "24", "5"))).contains("نویسه"))
  }

  @Test fun countedUnitsTakeOnlyWholePackSizes() {
    assertTrue(failed(check(Draft("کارتن", "2.5", "5"))).contains("اعشار"))
    ok(check(Draft("کارتن", "2.5", "5"), fractional = true))
  }

  @Test fun limits() {
    val many = (1..PackBarcodes.MAX_PACKS + 1).map { Draft("ب$it", "${it + 1}", "8$it") }.toTypedArray()
    assertTrue(failed(check(*many)).contains("بسته"))
    val codes = (1..PackBarcodes.MAX_CODES + 1).joinToString("،") { "7$it" }
    assertTrue(failed(check(Draft("کارتن", "24", codes))).contains("بارکد"))
    assertTrue(failed(check(Draft("کارتن", "2000000", "5"))).contains("بزرگ"))
  }

  @Test fun scanFindsOnlyWholeCodesAndRefusesOfflineClashes() {
    val carton = Pack("کارتن", 24_000, listOf("6260000000024"))
    val idx = PackBarcodes.Index(listOf("p1" to listOf(carton), "p2" to listOf(Pack("شل", 6_000, listOf("333")))))
    assertEquals(PackBarcodes.Hit("p1", carton), idx.find(" 6260000000024 "))
    assertEquals("p1", idx.find("۶۲۶۰۰۰۰۰۰۰۰۲۴")?.productId)
    //  نیمهٔ بارکد هرگز ۲۴ دانه به سبد نمی‌برد
    assertNull(idx.find("626000000002"))
    assertNull(idx.find(""))
    assertEquals(mapOf("6260000000024" to "p1", "333" to "p2"), idx.owners())
    //  تعارضِ آفلاین: یک بارکدِ بسته در دو کالا ⇒ هیچ‌کدام
    val clash = PackBarcodes.Index(listOf("p1" to listOf(carton), "p3" to listOf(Pack("کارتن", 12_000, listOf("6260000000024")))))
    assertNull(clash.find("6260000000024"))
    assertTrue(clash.ambiguous("6260000000024"))
    assertFalse(idx.ambiguous("6260000000024"))
    assertTrue(PackBarcodes.Index(emptyList()).isEmpty)
  }
}
