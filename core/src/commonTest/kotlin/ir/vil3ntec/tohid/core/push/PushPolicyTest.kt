package ir.vil3ntec.tohid.core.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  پوشِ گوشی (O-01) — همان قراردادِ `server/src/routes/shop-push.js` و `server/src/lib/order-notify.js`
 *  (`server/test/push-fcm.test.js`).
 */
class PushPolicyTest {
  private val on = PushPolicy.ServerConfig(enabled = true, serverReady = true, registered = true, orderAlerts = true)

  @Test
  fun registersOnlyWhenNeeded() {
    val key = PushPolicy.sentKey("tok", "usr_1", "shp_1")
    val now = 1_000_000_000L
    assertFalse(PushPolicy.needsRegister(true, key, key, now - 1000, now), "همان توکن، همان کس، امروز ⇒ نه")
    assertTrue(PushPolicy.needsRegister(false, key, key, now - 1000, now), "سرور نمی‌شناسد (خروج، UNREGISTERED) ⇒ بله")
    assertTrue(PushPolicy.needsRegister(true, key, PushPolicy.sentKey("tok2", "usr_1", "shp_1"), now - 1000, now), "توکنِ تازه")
    assertTrue(PushPolicy.needsRegister(true, key, PushPolicy.sentKey("tok", "usr_2", "shp_1"), now - 1000, now), "کسِ دیگر")
    assertTrue(PushPolicy.needsRegister(true, key, PushPolicy.sentKey("tok", "usr_1", "shp_2"), now - 1000, now), "دکانِ دیگر")
    assertTrue(PushPolicy.needsRegister(true, key, key, now - PushPolicy.RESEND_MS, now), "یک روز گذشت")
    assertTrue(PushPolicy.needsRegister(true, key, key, now + 5000, now), "ساعتِ گوشی عقب رفت")
    assertTrue(PushPolicy.needsRegister(true, null, key, 0, now), "هرگز نفرستاده")
  }

  @Test
  fun stateFollowsTheChain() {
    assertEquals(PushPolicy.State.FLAG_OFF, PushPolicy.stateOf(on.copy(enabled = false), hasToken = true))
    assertEquals(PushPolicy.State.NO_TOKEN, PushPolicy.stateOf(on, hasToken = false))
    assertEquals(PushPolicy.State.SERVER_OFF, PushPolicy.stateOf(on.copy(serverReady = false), hasToken = true))
    assertEquals(PushPolicy.State.ACTIVE, PushPolicy.stateOf(on, hasToken = true))
  }

  @Test
  fun tapOpensOnlyOrdersWithValidIds() {
    val notice = mapOf("type" to "order_notify", "event" to "order_created", "notifId" to "01JNOTIF", "orderId" to "ord_1", "orderNo" to "12")
    assertEquals(PushPolicy.Open("ord_1", "01JNOTIF"), PushPolicy.openOf(notice))
    assertEquals(PushPolicy.Open("ord_1", null), PushPolicy.openOf(mapOf("type" to "order_start_alert", "orderId" to "ord_1", "notifId" to "x")))
    assertNull(PushPolicy.openOf(mapOf("type" to "event", "orderId" to "ord_1")), "خبرِ کالا سفارش باز نمی‌کند")
    assertNull(PushPolicy.openOf(mapOf("type" to "order_notify", "orderId" to "../x")), "شناسهٔ نادرست")
    assertNull(PushPolicy.openOf(mapOf("type" to "order_notify")))
    assertEquals(PushPolicy.Open("ord_1", null), PushPolicy.openOf(notice + ("notifId" to "bad id")))
    assertNull(PushPolicy.openOf(emptyMap()))
  }

  @Test
  fun channelsMatchServer() {
    assertEquals("orders", PushPolicy.channelOf("orders"))
    assertEquals("events", PushPolicy.channelOf("events"))
    assertEquals("support", PushPolicy.channelOf(null))
    assertEquals("support", PushPolicy.channelOf("whatever"))
  }
}
