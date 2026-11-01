package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.pos.SerialNumbers.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  POS-28 — سریالِ کالای سریال‌دار: بی سریالِ کامل، با سریالِ تکراری یا فروخته‌شده، یا با مقدارِ اعشاری
 *  فاکتور بسته نمی‌شود. پیش از این گوشی و یخچال بی هیچ سریالی فروخته می‌شد و گارانتی دنبال‌شدنی نبود.
 */
class SerialNumbersTest {

  @Test
  fun parseSplitsLinesAndCommasAndLatinizesDigits() {
    assertEquals(listOf("SN-001", "356938035643809", "ab12"), SerialNumbers.parse(" SN-001 \n۳۵۶۹۳۸۰۳۵۶۴۳۸۰۹،ab١٢\n\n ; "))
    assertEquals(SerialNumbers.MAX_LEN, SerialNumbers.parse("x".repeat(100)).single().length)
    assertEquals("a\nb", SerialNumbers.format(listOf("a", "b")))
  }

  @Test
  fun completeLineHasNoProblem() {
    assertNull(SerialNumbers.problem(listOf("a", "b"), 2000, emptySet()))
    assertNull(SerialNumbers.problem(emptyList(), 0, emptySet()))
  }

  @Test
  fun missingTooManyDuplicateSoldAndFractionalAreProblems() {
    assertEquals(Problem.Missing(1, 3), SerialNumbers.problem(listOf("a"), 3000, emptySet()))
    assertEquals(Problem.TooMany(2, 1), SerialNumbers.problem(listOf("a", "b"), 1000, emptySet()))
    assertEquals(Problem.Duplicate("a"), SerialNumbers.problem(listOf("a", "a"), 2000, emptySet()))
    assertEquals(Problem.AlreadySold("b"), SerialNumbers.problem(listOf("a", "b"), 2000, setOf("b")))
    assertEquals(Problem.Fractional, SerialNumbers.problem(listOf("a"), 1500, emptySet()))
  }
}
