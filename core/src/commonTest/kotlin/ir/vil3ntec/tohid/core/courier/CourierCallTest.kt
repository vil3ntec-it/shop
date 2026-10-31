package ir.vil3ntec.tohid.core.courier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  دکمهٔ «تماس با مشتری» روی کارتِ پیک (DLV-02) فقط وقتی دیده می‌شود که سرور شماره را
 *  می‌دهد: پس از «قبول» و تا پایانِ مأموریت (`CALLABLE`ِ `server/src/lib/delivery.js`).
 */
class CourierCallTest {

  @Test
  fun callOnlyAfterAcceptAndUntilTheEnd() {
    for (s in listOf("assigned", "picked_up", "moving", "arrived", "failed")) assertTrue(CourierFlow.canCall(s), s)
    for (s in listOf("requested", "delivered", "rejected", "withdrawn", "returned", "")) assertFalse(CourierFlow.canCall(s), s)
  }

  @Test
  fun callableIsExactlyTheLiveStatesAfterAccept() {
    assertEquals(CourierFlow.LIVE - "requested", CourierFlow.CALLABLE)
  }
}
