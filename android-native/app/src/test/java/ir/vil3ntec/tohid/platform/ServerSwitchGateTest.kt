package ir.vil3ntec.tohid.platform

import ir.vil3ntec.tohid.core.config.ApiConfig
import ir.vil3ntec.tohid.core.net.ApiFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  پرچمِ `app.multi_server` خاموش ⇒ درِ شبکه دقیقاً همان قاعدهٔ امروز (`ApiConfig.reject`) است؛ و
 *  گواهیِ سنجاق‌شده با همان اثرِ انگشتی سنجیده می‌شود که `core/server` و سرور (`fingerprint256`) دارند.
 */
class ServerSwitchGateTest {

  @Test
  fun flagOffKeepsTodaysAddressRule() {
    //  هیچ پروفایلی فعال نشده (بی Context) ⇒ همان `ApiConfig.reject`
    val cases = listOf(
      "https://api.example.com", "", "http://api.example.com", "https://203.0.113.7", "ftp://x.example",
    )
    for (base in cases) {
      for (insecure in listOf(false, true)) {
        val today = ApiConfig.reject(base, insecure)
        val now = ServerSwitchAndroid.reject(base, insecure)
        when (today) {
          null -> assertNull(base, now)
          ApiConfig.Rejection.MISSING -> assertTrue(base, now is ApiFailure.NotConfigured)
          else -> {
            assertTrue(base, now is ApiFailure.BadConfiguration)
            assertEquals(base, today.message, now?.message)
          }
        }
      }
    }
  }

  @Test
  fun certificatePinIsSha256OfDerInLowercaseHex() {
    //  SHA-256 ِ «abc» — بردارِ مرجعِ FIPS 180-2
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      PinnedTls.sha256Hex("abc".toByteArray()),
    )
  }
}
