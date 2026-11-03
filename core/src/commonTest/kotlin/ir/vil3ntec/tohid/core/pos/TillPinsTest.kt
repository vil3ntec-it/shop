package ir.vil3ntec.tohid.core.pos

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  چیدمانِ کاشی‌های صندوقِ همین دستگاه — POS-06 (§۶.۱).
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class TillPinsTest {

  private val all = listOf("rice", "oil", "tea", "sugar", "salt")

  @Test
  fun noPinsKeepsTodaysOrder() {
    assertEquals(all, TillPins.arrange(all, emptyList()))
    assertEquals(all, TillPins.arrange(all, listOf("gone")))
  }

  @Test
  fun pinnedComeFirstInPinOrderRestKeepOrder() {
    assertEquals(listOf("salt", "tea", "rice", "oil", "sugar"), TillPins.arrange(all, listOf("salt", "tea")))
    //  سنجاقِ کالایی که در این دسته/فهرست نیست اثری ندارد
    assertEquals(listOf("tea", "rice", "oil", "sugar", "salt"), TillPins.arrange(all, listOf("bread", "tea")))
  }

  @Test
  fun toggleAddsAtEndRemovesAndRespectsCap() {
    assertEquals(listOf("tea"), TillPins.toggle(emptyList(), "tea"))
    assertEquals(listOf("tea", "oil"), TillPins.toggle(listOf("tea"), "oil"))
    assertEquals(listOf("oil"), TillPins.toggle(listOf("tea", "oil"), "tea"))
    assertEquals(listOf("tea"), TillPins.toggle(listOf("tea"), " "))
    val full = (1..TillPins.MAX).map { "p$it" }
    assertEquals(full, TillPins.toggle(full, "new"))
    assertEquals(full - "p3", TillPins.toggle(full, "p3"))
  }

  @Test
  fun firstMovesPinnedToFront() {
    assertEquals(listOf("oil", "tea", "salt"), TillPins.first(listOf("tea", "salt", "oil"), "oil"))
    assertEquals(listOf("tea", "salt"), TillPins.first(listOf("tea", "salt"), "rice"))
  }

  @Test
  fun encodeDecodeRoundTripAndCleans() {
    val pins = listOf("tea", "oil")
    assertEquals(pins, TillPins.decode(TillPins.encode(pins)))
    assertEquals(emptyList(), TillPins.decode(null))
    assertEquals(listOf("tea", "oil"), TillPins.decode("tea\n\n oil \ntea"))
    assertEquals(TillPins.MAX, TillPins.decode((1..40).joinToString("\n") { "p$it" }).size)
  }
}
