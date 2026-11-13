package ir.vil3ntec.tohid.core.courier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  DLV-06 — «تقریباً کی می‌رسم» ِ خودِ پیک (`POST /api/courier/missions/:id/eta`، پرچمِ `shop.delivery_customer_view` ⇐
 *  `etaEnabled` ِ `/courier/me`): فقط «در راه» (`picked_up`، `moving` — همان `ON_THE_WAY` ِ سرور)، دقیقه‌ها همان بازهٔ
 *  سرور (۱ تا ۲۴۰) و گزینه‌های آماده.
 */
class CourierEtaTest {

  @Test fun onlyOnTheWayAndOnlyWithTheShopFlag() {
    assertTrue(CourierEta.visible(enabled = true, status = "picked_up"))
    assertTrue(CourierEta.visible(enabled = true, status = "moving"))
    for (s in listOf("requested", "assigned", "arrived", "delivered", "failed", "returned")) {
      assertFalse(CourierEta.visible(enabled = true, status = s), s)
    }
    assertFalse(CourierEta.visible(enabled = false, status = "moving"))
  }

  @Test fun choicesStayInsideTheServerRange() {
    assertEquals(listOf(5, 10, 15, 20, 30, 45, 60), CourierEta.CHOICES)
    assertTrue(CourierEta.CHOICES.all { CourierEta.valid(it) })
    assertFalse(CourierEta.valid(0))
    assertFalse(CourierEta.valid(241))
    assertTrue(CourierEta.valid(240))
  }
}
