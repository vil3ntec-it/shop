package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.text.PlainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  دستهٔ ۶۳ (e-rows-63i) — نگهبانِ «خطای خام» (`PlainError`، کدِ مشترکِ core): متنِ ساده دست‌نخورده می‌ماند (متنِ دوستانهٔ
 *  صفحه‌های همگام‌سازی و سرور)، و هیچ نشانی، http، کد، نامِ استثنا، ردِ پشته، JSON یا HTML به صاحبِ دکان نمی‌رسد.
 */
class PlainErrorTest {

  private val leaks = listOf("http", "://", "www.", "Exception", "java.", "okhttp", "{", "<", "500", "503", "ECONN", "at ir.")

  private fun assertClean(shown: String?) {
    if (shown == null) return
    for (l in leaks) assertFalse("«$l» در «$shown»", shown.contains(l, ignoreCase = true))
  }

  @Test fun plainTextIsKeptAsIs() {
    for (s in listOf(
      "ارتباط با سرور برقرار نشد",
      "اینترنت وصل نیست؛ کارها روی همین دستگاه می‌ماند.",
      "این کد قبلاً استفاده شده است",
      "Couldn't reach the server. Try again in a moment.",
      "د سرور سره اړیکه ونه شوه",
      "ساعت ۱۲:۳۰ دوباره بزنید",
      "Sale at 12:30 was saved",
    )) {
      assertFalse(s, PlainError.isTechnical(s))
      assertEquals(s, PlainError.text(s))
    }
    assertNull(PlainError.text(null))
    assertNull(PlainError.text("   "))
  }

  @Test fun technicalTailIsCutAndFriendlyHeadKept() {
    assertEquals("سرور در دسترس نیست", PlainError.text("سرور در دسترس نیست: https://api.vil3ntec.ir/v1/sync?x=1"))
    assertEquals("Failed to connect", PlainError.text("Failed to connect: java.net.ConnectException: Connection refused"))
    assertEquals("Couldn't load orders", PlainError.text("Couldn't load orders (503)"))
    assertEquals("بارگذاری نشد", PlainError.text("بارگذاری نشد — HTTP 500"))
    assertEquals("Sync stopped", PlainError.text("Sync stopped. ECONNREFUSED 10.0.0.2:443"))
  }

  @Test fun fullyTechnicalTextGivesNothing() {
    val raw = listOf(
      "java.net.SocketTimeoutException: timeout",
      "IOException",
      "HTTP 500",
      "خطای 503",
      "{\"error\":\"bad_request\",\"code\":400}",
      "<html><body>502 Bad Gateway</body></html>",
      "https://vil3ntec.ir/api/shop/sync",
      "E_SYNC_CONFLICT",
      "kotlinx.serialization.SerializationException: Unexpected JSON token\n  at ir.vil3ntec.tohid.core.sync.Pull.run(Pull.kt:42)",
      "GET /api/v1/shop/devices failed",
    )
    for (s in raw) {
      assertTrue(s, PlainError.isTechnical(s))
      val shown = PlainError.text(s)
      assertClean(shown)
    }
    assertNull(PlainError.text("HTTP 500"))
    assertNull(PlainError.text("java.net.SocketTimeoutException: timeout"))
  }

  @Test fun kindFollowsTheRawText() {
    assertEquals(PlainError.Kind.OFFLINE, PlainError.kindOf("java.net.UnknownHostException: api.vil3ntec.ir"))
    assertEquals(PlainError.Kind.OFFLINE, PlainError.kindOf("اینترنت وصل نیست"))
    assertEquals(PlainError.Kind.OFFLINE, PlainError.kindOf("Sync failed", "SocketTimeoutException"))
    assertEquals(PlainError.Kind.SERVER, PlainError.kindOf("سرور فعلاً کار نمی‌کند"))
    assertEquals(PlainError.Kind.SERVER, PlainError.kindOf("HTTP 503"))
    assertEquals(PlainError.Kind.OTHER, PlainError.kindOf("موجودی کافی نیست", null))
    assertEquals(PlainError.Kind.OTHER, PlainError.kindOf())
  }
}
