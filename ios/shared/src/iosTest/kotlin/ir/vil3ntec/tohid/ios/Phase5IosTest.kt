package ir.vil3ntec.tohid.ios

import android.content.IosContext
import ir.vil3ntec.tohid.data.OrderNotify
import ir.vil3ntec.tohid.platform.HubRelayAndroid
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.ShimHttp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 *  گامِ ۵ ِ آیفون روی شبیه‌ساز (کارِ `ios-app`؛ روی لینوکس فقط کامپایل می‌شود): لمسِ اعلانِ سرور (APNs) ⇐ همان
 *  `Intent` ِ سفارش، و هابِ محلی با **خودِ** `HubRelayAndroid` ِ اندروید روی `Proxy.NO_PROXY` ِ shim.
 */
class Phase5IosTest {
  @Test
  fun remoteOrderPushOpensTheSameOrderAsTheLocalNotice() {
    val info = mapOf<Any?, Any?>(
      "aps" to mapOf("alert" to mapOf("title" to "سفارشِ تازه", "body" to "باز کنید")),
      "type" to "order_notify", "notifId" to "ntf_abc", "orderId" to "ord_xyz", "orderNo" to "12",
    )
    val intent = IosPush.remoteIntent(info)!!
    assertEquals("ntf_abc", intent.getStringExtra(OrderNotify.EXTRA_NOTIF))
    assertEquals("ord_xyz", intent.getStringExtra(OrderNotify.EXTRA_ORDER))

    //  اعلانِ محلی (بی `aps`) ⇐ همان راهِ گامِ ۳
    assertNull(IosPush.remoteIntent(mapOf<Any?, Any?>("tohid.action" to "x")))
    //  پیامِ سرورِ دیگر ⇐ فقط باز شدنِ برنامه، بی سفارش
    val other = IosPush.remoteIntent(mapOf<Any?, Any?>("aps" to mapOf<Any?, Any?>(), "type" to "support"))!!
    assertNull(other.getStringExtra(OrderNotify.EXTRA_NOTIF))
  }

  @Test
  fun noProxyOpensADirectPinnableConnectionAndOtherProxiesAreRefused() {
    val c = URL("https://192.168.1.10:8443/hub/v1/hello").openConnection(Proxy.NO_PROXY)
    assertTrue(c is HttpsURLConnection)
    assertFailsWith<UnsupportedOperationException> { URL("https://shop.local/").openConnection(Proxy(Proxy.Type.HTTP)) }

    assertSame(ShimHttp.of(pinned = true, direct = true), ShimHttp.of(pinned = true, direct = true))
    val all = listOf(ShimHttp.of(false, false), ShimHttp.of(true, false), ShimHttp.of(false, true), ShimHttp.of(true, true))
    for (i in all.indices) for (j in all.indices) if (i != j) assertNotSame(all[i], all[j])
  }

  @Test
  fun androidHubRelayRunsOnIosAndStaysOffWithoutTheFlag() {
    //  پرچمِ `app.multi_server` خاموش (پیش‌فرض) ⇐ null ⇐ موتورِ v3 همان امروز، مثلِ اندروید
    assertNull(HubRelayAndroid.of(IosContext))
  }
}
