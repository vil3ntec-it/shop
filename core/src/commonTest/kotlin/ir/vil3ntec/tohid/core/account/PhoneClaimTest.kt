package ir.vil3ntec.tohid.core.account

import ir.vil3ntec.tohid.core.account.PhoneClaim.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  AUTH-08 — گامِ کد در برنامه. کدهای خطا همان‌اند که `server/src/routes/me.js` و `lib/otp.js` می‌دهند
 *  (`server/test/phone-add-code.test.js`).
 */
class PhoneClaimTest {

  @Test fun flag_off_save_is_the_old_flow() {
    //  پرچمِ خاموش: سرور هرگز `phone_code_required` نمی‌دهد ⇒ موفق یا همان پیامِ پیشین
    assertEquals(Step.SAVED, PhoneClaim.afterSave(null))
    for (code in listOf("phone_taken", "phone_locked", "reauth_required", "offline", "bad_request", "http_500")) {
      assertEquals(Step.STOP, PhoneClaim.afterSave(code), code)
    }
  }

  @Test fun flag_on_asks_for_the_code_of_the_same_phone() {
    assertEquals(Step.SEND_CODE, PhoneClaim.afterSave("phone_code_required"))
    assertEquals(Step.ENTER_CODE, PhoneClaim.afterSend(null))
  }

  @Test fun wrong_code_keeps_the_box_dead_code_asks_a_new_one() {
    assertEquals(Step.RETRY_CODE, PhoneClaim.afterSave("otp_wrong"))
    for (code in listOf("otp_expired", "otp_used", "otp_not_found", "otp_locked", "otp_required")) {
      assertEquals(Step.NEW_CODE, PhoneClaim.afterSave(code), code)
    }
    //  شمارهٔ حسابِ دیگر فقط پس از کدِ درست گفته می‌شود و پایانِ کار است
    assertEquals(Step.STOP, PhoneClaim.afterSave("phone_taken"))
  }

  @Test fun sending_the_code() {
    //  پرچم میانِ کار خاموش شد ⇒ کادرِ کد بسته و «ذخیره» ِ بی کد
    assertEquals(Step.WITHOUT_CODE, PhoneClaim.afterSend("phone_code_disabled"))
    //  «صبر کنید»: کدِ همین شماره تازه رفته و زنده است ⇒ کادرِ کد باز (حتی پس از بستن و باز کردنِ برنامه)
    assertEquals(Step.ENTER_CODE, PhoneClaim.afterSend("otp_resend_wait"))
    for (code in listOf("otp_daily_limit", "rate_limited", "verification_unavailable", "phone_locked", "reauth_required", "offline")) {
      assertEquals(Step.STOP, PhoneClaim.afterSend(code), code)
    }
  }

  @Test fun typed_code_is_ascii_digits_only() {
    assertEquals("123456", PhoneClaim.normalizeCode("123456"))
    assertEquals("123456", PhoneClaim.normalizeCode("۱۲۳۴۵۶"))
    assertEquals("123456", PhoneClaim.normalizeCode("١٢٣٤٥٦"))
    assertEquals("123456", PhoneClaim.normalizeCode(" ۱۲۳ ۴۵۶ "))
    assertEquals("123456", PhoneClaim.normalizeCode("123-456"))
    assertEquals("123456", PhoneClaim.normalizeCode("‎123‌456 "))
    assertEquals("1234", PhoneClaim.normalizeCode("1234"))
    assertEquals("12345678", PhoneClaim.normalizeCode("12345678"))
    for (bad in listOf("", "   ", "123", "123456789", "12a456", "12.456", "۱۲۳۴۵x", "+123456")) {
      assertNull(PhoneClaim.normalizeCode(bad), bad)
    }
  }
}
