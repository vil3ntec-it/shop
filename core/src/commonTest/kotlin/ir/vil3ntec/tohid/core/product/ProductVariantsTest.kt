package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.ProductVariants.Axis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  PRD-11 — تنوع با ماتریس: ویژگی‌ها از کادر، ترکیب‌ها، نامِ هر تنوع، ترکیب‌های ساخته‌نشده و ماتریسِ نمایش.
 *  پیش از این «پیراهنِ قرمزِ M» و «آبیِ L» باید یکی‌یکی و بی هیچ پیوندی ساخته می‌شدند.
 */
class ProductVariantsTest {

  private val axes = listOf(Axis("رنگ", listOf("قرمز", "آبی")), Axis("اندازه", listOf("S", "M", "L")))

  @Test
  fun parseReadsAxesAndRejectsBadLines() {
    val p = ProductVariants.parse("رنگ: قرمز، آبی\nاندازه: S, M, L\n\nبی‌دونقطه\nرنگ: سبز\n: x")
    assertEquals(axes, p.axes)
    assertEquals(
      listOf(ProductVariants.Problem.BAD_LINE, ProductVariants.Problem.DUP_AXIS, ProductVariants.Problem.BAD_LINE),
      p.problems.map { it.second },
    )
    assertEquals("رنگ: قرمز، آبی\nاندازه: S، M، L", ProductVariants.format(p.axes))
    assertEquals(axes, ProductVariants.parse(ProductVariants.format(axes)).axes)
  }

  @Test
  fun tooManyCombinationsIsRefusedWhole() {
    val big = (1..3).joinToString("\n") { a -> "w$a: " + (1..5).joinToString("، ") { "v$it" } }
    val p = ProductVariants.parse(big)
    assertEquals(emptyList(), p.axes)
    assertTrue(p.problems.any { it.second == ProductVariants.Problem.TOO_MANY_COMBOS })
    assertEquals(3, ProductVariants.parse("a: 1، 2، 3\nb: x\nc: y، z").axes.size)
  }

  @Test
  fun combosAreTheCartesianProductInOrder() {
    val c = ProductVariants.combos(axes)
    assertEquals(6, c.size)
    assertEquals(listOf("رنگ" to "قرمز", "اندازه" to "S"), c.first())
    assertEquals(listOf("رنگ" to "آبی", "اندازه" to "L"), c.last())
    assertEquals("پیراهن — قرمز / S", ProductVariants.name(" پیراهن ", c.first()))
    assertEquals(emptyList(), ProductVariants.combos(emptyList()))
  }

  @Test
  fun missingSkipsCombosThatAlreadyHaveAVariantWhateverTheSpelling() {
    //  «ي» ِ عربی و فاصلهٔ اضافه همان ترکیب‌اند
    val have = listOf(listOf("رنگ" to "قرمز", "اندازه" to "S"), listOf("رنگ " to "آبي", "اندازه" to "m"))
    val miss = ProductVariants.missing(axes, have)
    assertEquals(4, miss.size)
    assertTrue(miss.none { ProductVariants.key(it) == ProductVariants.key(have[0]) })
  }

  @Test
  fun matrixPutsEachVariantInItsCell() {
    val all = ProductVariants.combos(axes)
    val m = ProductVariants.matrix(axes, listOf("v1" to all[0], "v6" to all[5]))
    assertEquals(listOf("قرمز", "آبی"), m.rows)
    assertEquals(listOf("S", "M", "L"), m.cols)
    assertEquals(mapOf((0 to 0) to "v1", (1 to 2) to "v6"), m.cells)
    //  یک ویژگی ⇒ یک ستون
    val one = ProductVariants.matrix(listOf(Axis("رنگ", listOf("قرمز"))), listOf("x" to listOf("رنگ" to "قرمز")))
    assertEquals(listOf(""), one.cols)
    assertEquals(mapOf((0 to 0) to "x"), one.cells)
  }
}
