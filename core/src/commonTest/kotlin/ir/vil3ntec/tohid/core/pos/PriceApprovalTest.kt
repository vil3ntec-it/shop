package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.pos.LinePricing.Direction
import ir.vil3ntec.tohid.core.pos.LinePricing.Policy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S45 — «درخواستِ تأیید» برای تغییرِ قیمت به‌جای پیامِ رد */
class PriceApprovalTest {

  private val upOnly = Policy(on = true, direction = Direction.UP, mayReduce = true)

  @Test
  fun directionAndFreeLineBlocksAreAskableButDiscountDenyIsNot() {
    val down = LinePricing.checkPrice(upOnly, 10_000, 8_000)
    assertEquals(LinePricing.PRICE_DOWN_BLOCKED, down)
    assertTrue(PriceApproval.askable(upOnly, down))
    assertTrue(PriceApproval.askable(upOnly, LinePricing.FREE_LINE_OVER_CAP))
    assertTrue(PriceApproval.askable(upOnly, LinePricing.FREE_LINE_CLOSED))
    assertFalse(PriceApproval.askable(upOnly, LinePricing.REDUCTION_DENIED))
    assertFalse(PriceApproval.askable(upOnly, null))
    //  خاموش یا صاحب ⇒ ردی نیست که تأیید بخواهد
    assertFalse(PriceApproval.askable(upOnly.copy(on = false), down))
    assertFalse(PriceApproval.askable(upOnly.copy(exempt = true), down))
  }

  @Test
  fun approvalLiftsOnlyTheBlockingRule() {
    val lifted = PriceApproval.lifted(upOnly, LinePricing.PRICE_DOWN_BLOCKED)
    assertNull(LinePricing.checkPrice(lifted, 10_000, 8_000))
    //  «تخفیف ممنوع» با تأییدِ قیمت برداشته نمی‌شود
    val noReduce = upOnly.copy(mayReduce = false)
    assertEquals(LinePricing.REDUCTION_DENIED, LinePricing.checkPrice(PriceApproval.lifted(noReduce, LinePricing.PRICE_DOWN_BLOCKED), 10_000, 8_000))
    val capped = Policy(on = true, freeLineCapMinor = 5_000)
    assertEquals(LinePricing.FREE_LINE_OVER_CAP, LinePricing.checkFreeLine(capped, 9_000))
    assertNull(LinePricing.checkFreeLine(PriceApproval.lifted(capped, LinePricing.FREE_LINE_OVER_CAP), 9_000))
    assertEquals(upOnly, PriceApproval.lifted(upOnly, LinePricing.REDUCTION_DENIED))
  }

  @Test
  fun grantCoversOnlyTheApprovedPrice() {
    val g = PriceApproval.Grant(LinePricing.PRICE_DOWN_BLOCKED, "مدیر", "manager", "مشتریِ همیشگی", "علی", 10_000, 8_000)
    assertTrue(PriceApproval.covers(g, 8_000))
    assertFalse(PriceApproval.covers(g, 7_000))
    assertFalse(PriceApproval.covers(null, 8_000))
    assertFalse(PriceApproval.covers(g, null))
  }
}
