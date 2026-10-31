package ir.vil3ntec.tohid.core.delivery

import ir.vil3ntec.tohid.core.delivery.DeskActions.Act
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  کارهای کارمند همان‌هایی‌اند که `check()`ِ `delivery-states.js` برای
 *  `actor.kind='staff'` می‌پذیرد — دکمهٔ بی‌اثر نیست.
 */
class DeskActionsTest {

  private val manager = DeskPerms(view = true, assign = true, handover = true, couriers = true, settle = true)
  private val staff = DeskPerms(view = true, handover = true)

  private fun m(mode: String, status: String, orderStatus: String = "", hasCode: Boolean = false, recall: Boolean = false) =
    Mission(id = "A", mode = mode, status = status, live = status in setOf("requested", "assigned", "picked_up", "moving", "arrived", "failed"),
      orderStatus = orderStatus, hasCode = hasCode, recallRequested = recall)

  @Test fun `دریافتِ حضوری — تحویل با کد فقط وقتی سفارش آمادهٔ دریافت است`() {
    assertEquals(listOf(Act.PICKUP_HANDOVER, Act.NEW_CODE, Act.WITHDRAW), DeskActions.of(m("pickup", "assigned", "ready_for_pickup", hasCode = true), manager))
    assertEquals(listOf(Act.NEW_CODE, Act.WITHDRAW), DeskActions.of(m("pickup", "assigned", "packed", hasCode = true), manager))
    assertEquals("شاگرد تحویل می‌دهد ولی کدِ تازه و پس‌گرفتن نه", listOf(Act.PICKUP_HANDOVER), DeskActions.of(m("pickup", "assigned", "ready_for_pickup", hasCode = true), staff))
  }

  @Test fun `شرکتِ بیرونی — دادن به شرکت ⇐ تحویل یا ناموفق ⇐ تلاشِ دوباره یا برگشت`() {
    assertEquals(listOf(Act.HAND_TO_COMPANY, Act.WITHDRAW), DeskActions.of(m("external", "assigned"), manager))
    assertEquals(listOf(Act.DELIVER, Act.FAIL, Act.RETURN), DeskActions.of(m("external", "picked_up"), manager))
    assertEquals(listOf(Act.RETRY, Act.RETURN, Act.FIX_ADDRESS), DeskActions.of(m("external", "failed"), manager))
    assertEquals("شاگرد فقط برگشتِ بسته را تأیید می‌کند", listOf(Act.RETURN), DeskActions.of(m("external", "moving"), staff))
    assertTrue(DeskActions.of(m("external", "assigned"), staff).isEmpty())
  }

  @Test fun `پیکِ دکان — کارهای پیک این‌جا نیست`() {
    assertEquals(listOf(Act.WITHDRAW), DeskActions.of(m("shop_courier", "requested"), manager))
    assertEquals(listOf(Act.DELIVER, Act.FAIL, Act.RECALL, Act.RETURN), DeskActions.of(m("shop_courier", "moving"), manager))
    assertEquals("فراخوانیِ دوباره نیست", listOf(Act.DELIVER, Act.FAIL, Act.RETURN), DeskActions.of(m("shop_courier", "arrived", recall = true), manager))
    assertEquals(listOf(Act.RECALL, Act.RETURN, Act.FIX_ADDRESS), DeskActions.of(m("shop_courier", "failed"), manager))
    assertTrue(DeskActions.needsNote(m("shop_courier", "moving"), Act.DELIVER))
    assertTrue(!DeskActions.needsNote(m("external", "moving"), Act.DELIVER))
  }

  @Test fun `پایان‌یافته هیچ کاری ندارد و هر کار مقصدِ درست دارد`() {
    for (s in listOf("delivered", "returned", "withdrawn", "rejected")) {
      assertTrue(s, DeskActions.of(m("shop_courier", s), manager).isEmpty())
    }
    assertEquals("delivered", DeskActions.target(Act.PICKUP_HANDOVER))
    assertEquals("picked_up", DeskActions.target(Act.HAND_TO_COMPANY))
    assertEquals("moving", DeskActions.target(Act.RETRY))
    assertEquals(null, DeskActions.target(Act.RECALL))
  }
}
