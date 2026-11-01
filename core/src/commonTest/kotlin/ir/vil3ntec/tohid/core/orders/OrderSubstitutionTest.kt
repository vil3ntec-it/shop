package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  ترجیحِ جایگزینی (CHK-15) — همان مقدارهای `server/src/lib/order-substitution.js`
 *  (`server/test/order-substitution.test.js` همان‌ها را در سرور می‌سنجد).
 */
class OrderSubstitutionTest {

  @Test
  fun codesMatchServerAndOrderLevelHasNoSpecific() {
    assertEquals(listOf("", "best_match", "specific", "none", "refund"), OrderSubstitution.LINE_CODES)
    assertEquals(listOf("", "best_match", "none", "refund"), OrderSubstitution.ORDER_CODES)
    for (c in OrderSubstitution.LINE_CODES) assertTrue(OrderSubstitution.label(c).isNotBlank(), c)
  }

  @Test
  fun lineLabelShowsTheNamedSubstituteAndNothingWithoutPreference() {
    assertNull(OrderSubstitution.lineLabel(""))
    assertEquals("اگر نبود: «شیرِ کابل»", OrderSubstitution.lineLabel("specific", " شیرِ کابل "))
    assertEquals("اگر نبود: کالای مشخص", OrderSubstitution.lineLabel("specific", ""))
    assertEquals("اگر نبود: بهترین مشابه", OrderSubstitution.lineLabel("best_match"))
    assertEquals("اگر نبود: بی جایگزین", OrderSubstitution.lineLabel("none"))
    assertEquals("اگر نبود: بازپرداخت", OrderSubstitution.lineLabel("refund"))
    assertNull(OrderSubstitution.orderLabel(""))
    assertEquals("اگر کالایی نبود: بازپرداخت", OrderSubstitution.orderLabel("refund"))
  }

  @Test
  fun manualFormSendsOnlyAKnownOrderLevelPreference() {
    val base = OrderDraft.Form(name = "احمد", lines = listOf(OrderDraft.LineInput("برنج", "1", "120")))
    fun sent(f: OrderDraft.Form) = (OrderDraft.build(f) as OrderDraft.Result.Ok).body.substitution
    assertEquals("", sent(base))
    assertEquals("best_match", sent(base.copy(substitution = "best_match")))
    assertEquals("refund", sent(base.copy(substitution = "refund")))
    //  «کالای مشخص» برای کلِ سفارش نیست و مقدارِ ناشناس فرستاده نمی‌شود
    assertEquals("", sent(base.copy(substitution = "specific")))
    assertEquals("", sent(base.copy(substitution = "whatever")))
  }
}
