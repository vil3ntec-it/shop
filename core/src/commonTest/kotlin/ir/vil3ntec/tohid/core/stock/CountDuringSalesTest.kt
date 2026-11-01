package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.stock.CountDuringSales.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** INV-10 (S20): فروش‌های حینِ شمارش خودکار لحاظ می‌شوند — عددها دستی، مقدار هزارم */
class CountDuringSalesTest {

  @Test fun saleAfterCountIsNotAShortage() {
    //  ۲۰ بود؛ ساعتِ ۱۰۰۰ بیست شمرده شد؛ ساعتِ ۲۰۰۰ صندوق ۳ فروخت ⇒ برنامه ۱۷
    val l = CountDuringSales.lines(mapOf("x" to 17_000), mapOf("x" to 20_000), mapOf("x" to 1_000), listOf(Move("x", 2_000, -3_000))).single()
    assertEquals(-3_000, l.movedAfterMilli)
    assertEquals(20_000, l.expectedMilli)
    assertEquals(0, l.diffMilli)
    assertEquals(17_000, l.afterMilli)
  }

  @Test fun saleBeforeCountIsAlreadyInTheCount() {
    val l = CountDuringSales.lines(mapOf("x" to 17_000), mapOf("x" to 17_000), mapOf("x" to 1_000), listOf(Move("x", 500, -3_000))).single()
    assertEquals(0, l.movedAfterMilli)
    assertEquals(0, l.diffMilli)
  }

  @Test fun realShortageSurvivesSaleDuringCount() {
    //  یکی گم شده: ۱۹ شمرده، بعد ۳ فروش ⇒ اختلاف فقط −۱ و موجودی ۱۶
    val l = CountDuringSales.lines(mapOf("x" to 17_000), mapOf("x" to 19_000), mapOf("x" to 1_000), listOf(Move("x", 2_000, -3_000))).single()
    assertEquals(-1_000, l.diffMilli)
    assertEquals(16_000, l.afterMilli)
  }

  @Test fun sameInstantCountsAsBeforeAndReceiptsAfterAreSubtracted() {
    val moves = listOf(Move("x", 1_000, -2_000), Move("x", 3_000, 10_000), Move("x", 4_000, -1_000))
    val l = CountDuringSales.lines(mapOf("x" to 27_000), mapOf("x" to 18_000), mapOf("x" to 1_000), moves).single()
    assertEquals(9_000, l.movedAfterMilli)
    assertEquals(18_000, l.expectedMilli)
    assertEquals(0, l.diffMilli)
  }

  @Test fun withoutInstantItIsTodaysBehaviour() {
    val l = CountDuringSales.lines(mapOf("x" to 17_000), mapOf("x" to 20_000), emptyMap(), listOf(Move("x", 2_000, -3_000))).single()
    assertNull(l.countedAt)
    assertEquals(17_000, l.expectedMilli)
    assertEquals(3_000, l.diffMilli)
  }

  @Test fun onlyCountedProductsAndTheirOwnMoves() {
    val lines = CountDuringSales.lines(
      mapOf("x" to 5_000, "y" to 9_000, "z" to 1_000), mapOf("x" to 5_000, "z" to 1_500), mapOf("x" to 10, "z" to 10),
      listOf(Move("y", 20, -1_000), Move("z", 20, -1_000)),
    )
    assertEquals(listOf("x", "z"), lines.map { it.productId })
    //  z هنگامِ شمارش ۲ بود (۱ پس از آن فروخته شد) و ۱٫۵ شمرده شد ⇒ −۰٫۵؛ حرکتِ y بی اثر
    assertEquals(listOf(0L, -500L), lines.map { it.diffMilli })
  }

  @Test fun latestSheetWinsPerProduct() {
    val at = CountDuringSales.latest(listOf(100L to setOf("a", "b"), 300L to setOf("b"), 200L to setOf("c", "a")))
    assertEquals(mapOf("a" to 200L, "b" to 300L, "c" to 200L), at)
  }

  @Test fun overflowIsRefused() {
    assertFailsWith<ArithmeticException> {
      CountDuringSales.movedAfter(mapOf("x" to 0L), listOf(Move("x", 1, Long.MAX_VALUE), Move("x", 2, 1)))
    }
  }
}
