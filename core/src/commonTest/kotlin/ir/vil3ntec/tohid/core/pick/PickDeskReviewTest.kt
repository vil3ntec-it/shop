package ir.vil3ntec.tohid.core.pick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  PCK-13 (شاخص‌ها) و PCK-14 (رسیدگیِ «پیدا نشد») در میزِ جمع‌آوری — متنِ عددها و شرطِ دکمهٔ رسیدگی، همان
 *  که سرور می‌دهد و می‌پذیرد (`metrics`: میلی‌ثانیه و یک‌دهمِ درصد؛ `resolveIssue`: کار + توضیحِ لازم).
 */
class PickDeskReviewTest {

  @Test
  fun minutesFromMillis() {
    assertEquals("—", PickDesk.minutes(null))
    assertEquals("0.0", PickDesk.minutes(0))
    assertEquals("1.0", PickDesk.minutes(60_000))
    assertEquals("2.5", PickDesk.minutes(150_000))
    assertEquals("2.5", PickDesk.minutes(147_000), "گرد به نزدیک‌ترین دهم")
    assertEquals("12.0", PickDesk.minutes(720_000))
  }

  @Test
  fun percentFromBasisPoints() {
    assertEquals("—", PickDesk.percent(null))
    assertEquals("100.0", PickDesk.percent(10_000))
    assertEquals("97.5", PickDesk.percent(9_750))
    assertEquals("33.3", PickDesk.percent(3_333))
    assertEquals("0.0", PickDesk.percent(0))
  }

  @Test
  fun resolveNeedsActionAndNote() {
    assertTrue(PickDesk.canResolve("resolved", "دوباره چیده شد"))
    assertTrue(PickDesk.canResolve("dismissed", "تمام شده بود"))
    assertFalse(PickDesk.canResolve("resolved", "  "))
    assertFalse(PickDesk.canResolve("open", "x"))
  }
}
