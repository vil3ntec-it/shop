package ir.vil3ntec.tohid.core.delivery

import ir.vil3ntec.tohid.core.delivery.DeskActions.Act
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  کارِ بعدی پس از ناموفق (DLV-09): تلاشِ دوباره، اصلاحِ نشانی، برگشت. «اصلاحِ نشانی»
 *  فقط برای مدیرِ سپردن (`delivery.assign`) و فقط مأموریتِ ارسالِ ناموفق دیده می‌شود.
 */
class DeskFixAddressTest {

  private val manager = DeskPerms(view = true, assign = true, handover = true, couriers = true, settle = true)
  private val staff = DeskPerms(view = true, handover = true)
  private val live = setOf("requested", "assigned", "picked_up", "moving", "arrived", "failed")

  private fun m(mode: String, status: String) = Mission(id = "A", mode = mode, status = status, live = status in live)

  @Test
  fun failedDeliveryOffersRetryOrRecallFixAddressAndReturn() {
    assertEquals(listOf(Act.RETRY, Act.RETURN, Act.FIX_ADDRESS), DeskActions.of(m("external", "failed"), manager))
    assertEquals(listOf(Act.RECALL, Act.RETURN, Act.FIX_ADDRESS), DeskActions.of(m("shop_courier", "failed"), manager))
  }

  @Test
  fun onlyFailedAndOnlyWithAssignPermission() {
    for (s in live - "failed") {
      assertFalse(Act.FIX_ADDRESS in DeskActions.of(m("shop_courier", s), manager), s)
      assertFalse(Act.FIX_ADDRESS in DeskActions.of(m("external", s), manager), s)
    }
    assertEquals(listOf(Act.RETURN), DeskActions.of(m("shop_courier", "failed"), staff))
    assertTrue(DeskActions.of(m("shop_courier", "delivered").copy(live = false), manager).isEmpty())
    assertFalse(Act.FIX_ADDRESS in DeskActions.of(m("pickup", "assigned"), manager))
  }

  @Test
  fun fixAddressIsNotAStatusChange() {
    assertNull(DeskActions.target(Act.FIX_ADDRESS))
    assertFalse(DeskActions.needsNote(m("shop_courier", "failed"), Act.FIX_ADDRESS))
    assertEquals("اصلاحِ نشانی", DeskActions.label(Act.FIX_ADDRESS))
  }
}
