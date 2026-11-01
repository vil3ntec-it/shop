package ir.vil3ntec.tohid.core.pick

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  PCK-05/06 در برگهٔ جمع‌آور: وضعیتِ نمایشیِ هر پیشنهاد همان `subView` ِ سرور است و گزینه‌های حلال همان سه
 *  مقدارِ `setAttrs` (`halal`، `non_halal`، خالی = نامعلوم).
 */
class PickSubstituteUiTest {

  @Test
  fun shownStateFollowsServerView() {
    assertEquals("pending", PickSubstitute.shownState("pending", false, null))
    assertEquals("will_accept", PickSubstitute.shownState("pending", true, "accept"))
    assertEquals("will_reject", PickSubstitute.shownState("pending", true, "reject"))
    assertEquals("will_reject", PickSubstitute.shownState("pending", true, null), "بی ترجیح ⇒ رد")
    for (s in listOf("accepted", "rejected", "withdrawn")) assertEquals(s, PickSubstitute.shownState(s, false, null))
    assertEquals("accepted", PickSubstitute.shownState("accepted", true, "reject"), "بسته‌شده با مهلت عوض نمی‌شود")
  }

  @Test
  fun halalChoicesAreTheServerValues() {
    assertEquals(listOf("halal", "non_halal", ""), PickSubstitute.HALAL_CHOICES)
    for (v in PickSubstitute.HALAL_CHOICES) assertEquals(v, PickSubstitute.halalOf(v))
  }
}
