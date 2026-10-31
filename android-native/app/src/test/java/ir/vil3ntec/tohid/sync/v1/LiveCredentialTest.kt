package ir.vil3ntec.tohid.sync.v1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  F0-11 — توکنِ دسترسی دیگر در آدرسِ سوکتِ زنده نمی‌نشیند.
 */
class LiveCredentialTest {
  private val token = "SECRET-ACCESS-TOKEN-0123456789"

  @Test fun ticketFromServerIsUsedNotToken() {
    val cred = LiveCredential.from(200, """{"ticket":"T-1","expires_in":60}""", token)
    assertEquals("ticket" to "T-1", cred)
    val url = LiveCredential.url("https://api.example.test", cred!!, "dev 1", "shop")
    //  S78: `caps=expiry` — سرور سوکت را سرِ انقضای توکن (4403) می‌بندد چون این نسخه با توکنِ تازه برمی‌گردد
    assertEquals("wss://api.example.test/api/sync/v1/live?ticket=T-1&device_id=dev+1&app=shop&caps=expiry", url)
    assertFalse(url.contains(token))
  }

  @Test fun oldServerWithoutTicketFallsBackToToken() {
    assertEquals("token" to token, LiveCredential.from(404, "{}", token))
  }

  @Test fun otherFailuresRetryWithoutPuttingTokenInUrl() {
    assertNull(LiveCredential.from(401, "{}", token))
    assertNull(LiveCredential.from(500, "oops", token))
    assertNull(LiveCredential.from(200, "<html>", token))
    assertNull(LiveCredential.from(200, null, token))
  }

  @Test fun plainHttpBecomesWs() {
    val url = LiveCredential.url("http://10.0.2.2:3000/", "ticket" to "a+b", "", "shop")
    assertEquals("ws://10.0.2.2:3000/api/sync/v1/live?ticket=a%2Bb&device_id=&app=shop&caps=expiry", url)
  }
}
