package ir.vil3ntec.tohid.core.account

import ir.vil3ntec.tohid.core.account.PhoneClaimForm.Next
import ir.vil3ntec.tohid.core.account.PhoneClaimForm.Problem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  AUTH-08 در «ویرایش حساب» ِ نسخهٔ ۳: حالِ کادرِ کد. کدهای خطا همان‌اند که `server/src/routes/me.js` و `lib/otp.js`
 *  می‌دهند (`server/test/phone-add-code.test.js`)؛ سقفِ تلاش و نرخ فقط از سرور، این‌جا هیچ شمارشِ جدا.
 */
class PhoneClaimFormTest {

  private val phone = "0700123456"

  @Test fun flag_off_never_opens_the_code_box_and_never_sends_a_code() {
    val f = PhoneClaimForm()
    assertFalse(f.shown)
    assertEquals("", f.codeToSend())
    //  پرچمِ خاموش: هر خطا همان پیامِ سرور است و کادر بسته می‌ماند
    for (code in listOf("phone_taken", "phone_locked", "reauth_required", "offline", "http_500")) {
      val (next, what) = f.afterSave(code, phone)
      assertEquals(Next.SERVER_MESSAGE, what, code)
      assertFalse(next.shown, code)
    }
    assertEquals(PhoneClaimForm() to Next.NONE, f.afterSave(null, phone))
  }

  @Test fun code_required_asks_the_code_of_the_same_phone_then_opens_the_box() {
    val (asked, what) = PhoneClaimForm().afterSave("phone_code_required", " $phone ")
    assertEquals(Next.REQUEST_CODE, what)
    assertFalse(asked.shown)
    //  بی شماره ⇒ کدی خواسته نمی‌شود، همان پیامِ سرور
    assertEquals(Next.SERVER_MESSAGE, PhoneClaimForm().afterSave("phone_code_required", "  ").second)

    val open = asked.sent(" $phone ", resendSeconds = 60)
    assertTrue(open.shown)
    assertEquals(phone, open.codeFor)
    assertEquals(60, open.resendSeconds)
    assertEquals(1, open.sent)
    assertEquals(2, open.sent(phone, 60).sent)
    //  عددِ بی‌معنای سرور
    assertEquals(0, asked.sent(phone, -5).resendSeconds)
    assertEquals(3600, asked.sent(phone, 99_999).resendSeconds)
  }

  @Test fun the_code_is_bound_to_the_phone() {
    val open = PhoneClaimForm().sent(phone, 60).code("۱۲۳۴۵۶")
    assertEquals(open, open.phone(" $phone"))
    val other = open.phone("0799000000")
    assertFalse(other.shown)
    assertEquals("", other.code)
    assertEquals("", other.codeToSend())
  }

  @Test fun bad_code_sends_nothing_and_says_so_in_place() {
    val open = PhoneClaimForm().sent(phone, 0)
    for (typed in listOf("", "12", "12a456", "123456789")) {
      assertNull(open.code(typed).codeToSend(), typed)
    }
    assertEquals(Problem.FORMAT, open.code("12").badCode().problem)
    assertEquals("123456", open.code(" ۱۲۳ ۴۵۶ ").codeToSend())
    //  نوشتنِ دوباره خطا را می‌برد
    assertNull(open.code("12").badCode().code("1").problem)
  }

  @Test fun wrong_code_keeps_the_box_and_the_code_dead_code_asks_a_new_one() {
    val typed = PhoneClaimForm().sent(phone, 0).code("111111")
    val (wrong, w) = typed.afterSave("otp_wrong", phone)
    assertEquals(Next.NONE, w)
    assertEquals(Problem.WRONG, wrong.problem)
    assertEquals("111111", wrong.code)
    assertTrue(wrong.shown)
    for (code in listOf("otp_locked", "otp_expired", "otp_used", "otp_not_found", "otp_required")) {
      val (dead, d) = typed.afterSave(code, phone)
      assertEquals(Next.REQUEST_CODE, d, code)
      assertEquals(Problem.DEAD, dead.problem, code)
      assertEquals("", dead.code, code)
      //  کدِ تازه رفت ⇒ کادرِ خالی و بی خطا
      val again = dead.sent(phone, 60)
      assertNull(again.problem)
      assertEquals("", again.code)
    }
    //  شمارهٔ حسابِ دیگر فقط پس از کدِ درست، و پایانِ کار
    assertEquals(Next.SERVER_MESSAGE, typed.afterSave("phone_taken", phone).second)
    //  ذخیره شد ⇒ همه‌چیز پاک
    assertEquals(PhoneClaimForm(), typed.afterSave(null, phone).first)
  }

  @Test fun resend_wait_comes_from_the_server_answer() {
    fun json(s: String) = Json.parseToJsonElement(s).jsonObject
    assertEquals(60, PhoneClaim.resendSeconds(json("""{"ok":true,"phone":"$phone","sent":true,"resendSeconds":60}""")))
    assertEquals(0, PhoneClaim.resendSeconds(json("""{"ok":true}""")))
    assertEquals(0, PhoneClaim.resendSeconds(json("""{"resendSeconds":"60"}""")))
    assertEquals(0, PhoneClaim.resendSeconds(json("""{"resendSeconds":-3}""")))
  }

  @Test fun sending_the_code_follows_the_server_only() {
    //  «صبر کنید»: کدِ همین شماره زنده است ⇒ کادر باز با پیامِ سرور (حتی پس از بستن و باز کردنِ برنامه)
    val (wait, w) = PhoneClaimForm().sendFailed("otp_resend_wait", " $phone")
    assertEquals(Next.SERVER_MESSAGE, w)
    assertTrue(wait.shown)
    assertEquals(phone, wait.codeFor)
    val typed = PhoneClaimForm().sent(phone, 0).code("1234")
    assertEquals("1234", typed.sendFailed("otp_resend_wait", phone).first.code)
    //  پرچم میانِ کار خاموش شد ⇒ کادر بسته، «دوباره ذخیره کنید» ِ بی کد
    assertEquals(PhoneClaimForm() to Next.SAVE_AGAIN, typed.sendFailed("phone_code_disabled", phone))
    //  سقفِ روزانه، سقفِ نرخ، پیامکِ راه‌نیفتاده و … ⇒ همان پیامِ سرور، کادر همان‌طور که بود
    for (code in listOf("otp_daily_limit", "rate_limited", "verification_unavailable", "phone_locked", "reauth_required", "offline")) {
      assertEquals(typed to Next.SERVER_MESSAGE, typed.sendFailed(code, phone), code)
    }
  }
}
