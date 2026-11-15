package ir.vil3ntec.tohid.core.account

import ir.vil3ntec.tohid.core.account.EmailClaim.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  AUTH-08 ⇐ «فقط ایمیل» (۱۴۰۵/۰۸/۲۳) — گامِ کدِ افزودنِ ایمیل در برنامه. کدهای خطا همان‌اند که `server/src/routes/me.js`
 *  و `lib/otp.js` می‌دهند (`server/test/email-only-auth.test.js`). کدِ پیامکیِ شماره دیگر نیست.
 */
class EmailClaimTest {

  @Test fun saving_without_an_email_claim_is_plain() {
    assertEquals(Step.SAVED, EmailClaim.afterSave(null))
    for (code in listOf("email_taken", "email_locked", "email_first", "reauth_required", "offline", "bad_request", "http_500")) {
      assertEquals(Step.STOP, EmailClaim.afterSave(code), code)
    }
  }

  @Test fun adding_an_email_asks_for_the_code_of_the_same_email() {
    assertEquals(Step.SEND_CODE, EmailClaim.afterSave("email_code_required"))
    assertEquals(Step.ENTER_CODE, EmailClaim.afterSend(null))
    //  کدِ پیامکیِ شماره دیگر نیست: پاسخِ کهنهٔ «کدِ شماره» گامِ کد نیست
    assertEquals(Step.STOP, EmailClaim.afterSave("phone_code_required"))
  }

  @Test fun wrong_code_keeps_the_box_dead_code_asks_a_new_one() {
    assertEquals(Step.RETRY_CODE, EmailClaim.afterSave("otp_wrong"))
    for (code in listOf("otp_expired", "otp_used", "otp_not_found", "otp_locked", "otp_required")) {
      assertEquals(Step.NEW_CODE, EmailClaim.afterSave(code), code)
    }
    //  ایمیلِ حسابِ دیگر فقط پس از کدِ درست گفته می‌شود و پایانِ کار است
    assertEquals(Step.STOP, EmailClaim.afterSave("email_taken"))
  }

  @Test fun sending_the_code() {
    //  «صبر کنید»: کدِ همین ایمیل تازه رفته و زنده است ⇒ کادرِ کد باز (حتی پس از بستن و باز کردنِ برنامه)
    assertEquals(Step.ENTER_CODE, EmailClaim.afterSend("otp_resend_wait"))
    for (code in listOf("otp_daily_limit", "rate_limited", "delivery_failed", "email_locked", "reauth_required", "offline", "phone_code_disabled")) {
      assertEquals(Step.STOP, EmailClaim.afterSend(code), code)
    }
  }

  @Test fun typed_code_is_ascii_digits_only() {
    assertEquals("123456", EmailClaim.normalizeCode("123456"))
    assertEquals("123456", EmailClaim.normalizeCode("۱۲۳۴۵۶"))
    assertEquals("123456", EmailClaim.normalizeCode("١٢٣٤٥٦"))
    assertEquals("123456", EmailClaim.normalizeCode(" ۱۲۳ ۴۵۶ "))
    assertEquals("123456", EmailClaim.normalizeCode("123-456"))
    assertEquals("123456", EmailClaim.normalizeCode("‎123‌456 "))
    assertEquals("1234", EmailClaim.normalizeCode("1234"))
    assertEquals("12345678", EmailClaim.normalizeCode("12345678"))
    for (bad in listOf("", "   ", "123", "123456789", "12a456", "12.456", "۱۲۳۴۵x", "+123456")) {
      assertNull(EmailClaim.normalizeCode(bad), bad)
    }
  }
}
