package ir.vil3ntec.tohid.core.account

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  O-09 / SUB-09 — سقفِ دستگاه در برنامه. پاسخ‌ها همان‌اند که `server/src/routes/license.js` می‌دهد
 *  (`server/test/sec-sub-device-cap.test.js`).
 */
class DeviceCapTest {

  private fun body(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  @Test fun owner_over_cap_gets_the_warning_with_counts() {
    val b = body("""{"license":"x.y","warning":{"code":"device_over_cap","devices":3,"maxDevices":2,"manage":"devices","message":"…"}}""")
    assertEquals(DeviceCap.Over(devices = 3, maxDevices = 2), DeviceCap.overOf(b))
  }

  @Test fun no_warning_no_license_or_another_code_means_nothing() {
    assertNull(DeviceCap.overOf(body("""{"license":"x.y"}""")))
    //  بی مجوز هشدار معنا ندارد (کارمندِ ردشده: `device_limit` ⇒ قفلِ `serverDenied`)
    assertNull(DeviceCap.overOf(body("""{"license":null,"reason":"device_limit","warning":{"code":"device_over_cap","devices":3,"maxDevices":2}}""")))
    assertNull(DeviceCap.overOf(body("""{"license":"x.y","warning":{"code":"other","devices":3,"maxDevices":2}}""")))
    //  شمارِ خراب یا زیرِ سقف ⇒ هیچ (نوارِ دروغ نشان داده نمی‌شود)
    assertNull(DeviceCap.overOf(body("""{"license":"x.y","warning":{"code":"device_over_cap","devices":2,"maxDevices":2}}""")))
    assertNull(DeviceCap.overOf(body("""{"license":"x.y","warning":{"code":"device_over_cap","devices":"a","maxDevices":2}}""")))
    assertNull(DeviceCap.overOf(body("""{"license":"x.y","warning":"device_over_cap"}""")))
  }

  @Test fun stored_form_round_trips_and_rejects_garbage() {
    val o = DeviceCap.Over(4, 2)
    assertEquals(o, DeviceCap.decode(DeviceCap.encode(o)))
    assertNull(DeviceCap.encode(null))
    for (bad in listOf(null, "", "4", "a/b", "2/2", "1/3", "-1/2", "4/0")) assertNull(DeviceCap.decode(bad), "$bad")
  }

  @Test fun locked_screen_never_sends_a_paying_shop_to_plans_for_a_full_device_list() {
    //  `device_limit`: اشتراک هست، جای دستگاه نیست ⇒ «دستگاه‌ها»، نه «قیمت‌ها» (بن‌بست)
    assertEquals(DeviceCap.Next.DEVICES, DeviceCap.lockedNext(signedIn = true, serverDenied = "device_limit"))
    assertEquals(DeviceCap.Next.PLANS, DeviceCap.lockedNext(signedIn = true, serverDenied = "no_subscription"))
    assertEquals(DeviceCap.Next.PLANS, DeviceCap.lockedNext(signedIn = true, serverDenied = null))
    assertEquals(DeviceCap.Next.SIGN_UP, DeviceCap.lockedNext(signedIn = false, serverDenied = "device_limit"))
  }
}
