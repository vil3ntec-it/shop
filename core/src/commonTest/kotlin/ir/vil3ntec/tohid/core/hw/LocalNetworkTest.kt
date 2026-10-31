package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  اجازهٔ شبکهٔ محلیِ اندروید ۱۷ — HW-13.
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class LocalNetworkTest {

  @Test
  fun permissionNameIsAndroids() {
    assertEquals("ACCESS_LOCAL_NETWORK", LocalNetwork.PERMISSION_NAME)
    assertEquals(37, LocalNetwork.ENFORCED_API)
  }

  @Test
  fun todayTarget35NeverNeedsOrDeclaresIt() {
    //  هدفِ امروزِ برنامه ۳۵ است: نه مانیفست و نه پرسش، روی هر اندرویدی — همان رفتارِ امروز
    assertFalse(LocalNetwork.declare(35))
    for (device in listOf(24, 35, 36, 37, 40)) assertFalse(LocalNetwork.needsPermission(35, device))
    assertFalse(LocalNetwork.needsPermission(36, 37))
    assertFalse(LocalNetwork.blocked(35, 37, granted = false, host = "192.168.1.50"))
  }

  @Test
  fun target37NeedsItOnlyOnAndroid17AndUp() {
    assertTrue(LocalNetwork.declare(37))
    assertTrue(LocalNetwork.needsPermission(37, 37))
    assertTrue(LocalNetwork.needsPermission(38, 40))
    //  دستگاهِ کهنه‌تر اجازهٔ ضمنی دارد
    assertFalse(LocalNetwork.needsPermission(37, 36))
  }

  @Test
  fun blockedOnlyForLocalHostWithoutGrant() {
    assertTrue(LocalNetwork.blocked(37, 37, granted = false, host = "192.168.1.50"))
    assertFalse(LocalNetwork.blocked(37, 37, granted = true, host = "192.168.1.50"))
    //  نشانیِ اینترنتی و نامِ DNS ِ معمولی را این اجازه نمی‌بندد
    assertFalse(LocalNetwork.blocked(37, 37, granted = false, host = "8.8.8.8"))
    assertFalse(LocalNetwork.blocked(37, 37, granted = false, host = "printer.example.com"))
    assertTrue(LocalNetwork.blocked(37, 37, granted = false, host = "printer.local"))
  }

  @Test
  fun ipv4LocalRangesFromAndroidDefinition() {
    for (h in listOf(
      "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.254", "192.168.1.50", "192.168.0.0",
      "169.254.10.20", "100.64.0.1", "100.127.255.255", "224.0.0.251", "239.255.255.250", "255.255.255.255",
      " 192.168.1.50 ", "192.168.001.050",
    )) assertTrue(LocalNetwork.isLocal(h), h)
    for (h in listOf(
      "172.15.255.255", "172.32.0.1", "192.169.0.1", "100.63.255.255", "100.128.0.1", "8.8.8.8",
      "127.0.0.1", "0.0.0.0", "223.255.255.255", "11.0.0.1",
    )) assertFalse(LocalNetwork.isLocal(h), h)
  }

  @Test
  fun ipv4ParsingIsStrict() {
    assertEquals(0xC0A80132L, LocalNetwork.ipv4("192.168.1.50"))
    assertNull(LocalNetwork.ipv4("192.168.1"))
    assertNull(LocalNetwork.ipv4("192.168.1.256"))
    assertNull(LocalNetwork.ipv4("192.168..1"))
    assertNull(LocalNetwork.ipv4("192.168.1.5a"))
    assertNull(LocalNetwork.ipv4("1.2.3.4.5"))
    assertNull(LocalNetwork.ipv4("۱۹۲.۱۶۸.۱.۵"))
  }

  @Test
  fun ipv6AndNames() {
    for (h in listOf("fe80::1", "FE80::1%wlan0", "[fe80::1]", "fd12:3456::1", "fc00::1", "ff02::fb", "::ffff:192.168.1.5", "PRINTER.LOCAL", "printer.local.")) {
      assertTrue(LocalNetwork.isLocal(h), h)
    }
    for (h in listOf("::1", "2001:db8::1", "2a00:1450::200e", "::ffff:8.8.8.8", "", "   ", "localhost", "printer", "local.example.com", "fe80")) {
      assertFalse(LocalNetwork.isLocal(h), h)
    }
  }
}
