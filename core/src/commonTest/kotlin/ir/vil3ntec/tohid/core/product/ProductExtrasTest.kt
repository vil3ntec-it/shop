package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.ProductAlternatives.Candidate
import ir.vil3ntec.tohid.core.product.ProductAlternatives.Ref
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  PRD-24 (کالای جایگزین برای پیشنهاد هنگامِ ناموجودی) و PRD-25 (فیلدهای سفارشیِ کالا). پیش از این
 *  صندوق برای کالای تمام‌شده فقط «فقط ۰ موجود است» می‌گفت و کالا جز فیلدهای ثابت هیچ ویژگیِ دیگری نداشت.
 */
class ProductExtrasTest {

  private val catalog = listOf(
    Ref("coke", "کوکاکولا ۱ لیتری", listOf("6260001000017")),
    Ref("pepsi", "پپسی ۱ لیتری", listOf("6260002000016")),
    Ref("fanta", "فانتا", emptyList()),
    Ref("tea", "چای‌سبز", emptyList()),
  )

  @Test
  fun alternativesResolveByBarcodeOrExactName() {
    val r = ProductAlternatives.resolve("پپسی ۱ لیتری، ۶۲۶۰۰۰۱۰۰۰۰۱۷، چاي سبز، دوغ، فانتا، FANTA", "x", catalog)
    assertEquals(listOf("pepsi", "coke", "tea", "fanta"), r.ids)
    //  نامِ لاتینِ «FANTA» نامِ کالا نیست (نامِ انگلیسی جورِ جست‌وجوست، نه نامِ دقیق)
    assertEquals(listOf("دوغ", "FANTA"), r.unknown)
  }

  @Test
  fun selfAndDuplicatesAreDroppedAndCountIsCapped() {
    assertEquals(listOf("pepsi"), ProductAlternatives.resolve("کوکاکولا ۱ لیتری، پپسی ۱ لیتری، پپسی ۱ لیتری", "coke", catalog).ids)
    val many = (1..15).map { Ref("p$it", "کالا $it") }
    val r = ProductAlternatives.resolve(many.joinToString("، ") { it.name }, null, many)
    assertEquals(ProductAlternatives.MAX, r.ids.size)
    assertEquals("p1", r.ids.first())
    assertEquals(ProductAlternatives.Resolved(emptyList(), emptyList()), ProductAlternatives.resolve(" ، ", null, catalog))
  }

  @Test
  fun suggestionsAreActiveInStockInOrderAndAtMostThree() {
    val state = mapOf(
      "a" to Candidate("a", "الف", active = true, inStock = false),   // تمام شده
      "b" to Candidate("b", "ب", active = false, inStock = true),     // غیرفعال
      "c" to Candidate("c", "ج", active = true, inStock = true),
      "d" to Candidate("d", "د", active = true, inStock = true),
      "e" to Candidate("e", "ه", active = true, inStock = true),
      "f" to Candidate("f", "و", active = true, inStock = true),
    )
    val got = ProductAlternatives.suggest(listOf("a", "gone", "b", "self", "c", "d", "c", "e", "f"), "self") { state[it] }
    assertEquals(listOf("c", "d", "e"), got.map { it.id })
    assertEquals(emptyList(), ProductAlternatives.suggest(emptyList(), "self") { state[it] })
  }

  @Test
  fun customFieldsParseNameValueLines() {
    val p = CustomFields.parse(" گارانتی : ۱۲ ماه \nکشورِ ساخت=ترکیه\n\nرنگ\n: بی‌نام\nگارانتي: ۲۴ ماه\nلینک: http://x.test/a:b")
    assertEquals(
      listOf("گارانتی" to "۱۲ ماه", "کشورِ ساخت" to "ترکیه", "لینک" to "http://x.test/a:b"),
      p.fields,
    )
    assertEquals(listOf("رنگ", ": بی‌نام"), p.bad)
  }

  @Test
  fun customFieldsRoundTripAndCaps() {
    val f = CustomFields.parse("الف: ۱\nب: دو کلمه").fields
    assertEquals(f, CustomFields.parse(CustomFields.format(f)).fields)
    val many = CustomFields.parse((1..30).joinToString("\n") { "k$it: v$it" }).fields
    assertEquals(CustomFields.MAX_FIELDS, many.size)
    val long = CustomFields.parse("${"k".repeat(99)}: ${"v".repeat(999)}").fields.single()
    assertEquals(CustomFields.MAX_KEY, long.first.length)
    assertEquals(CustomFields.MAX_VALUE, long.second.length)
  }

  @Test
  fun infoCarriesAlternativesAndCustomAndSearchesCustomValues() {
    val info = ProductInfo.of(null, null, null, null, null, null, null, alternatives = listOf("a", "a", "b"), custom = "گارانتی: ۱۲ ماه")
    assertEquals(listOf("a", "b"), info.alternatives)
    assertEquals(listOf("گارانتی" to "۱۲ ماه"), info.custom)
    assertTrue(!info.isEmpty)
    assertTrue(info.matches("12 ماه"))
    assertTrue(ProductInfo.of(null, null, null, null, null, null, null).isEmpty)
  }
}
