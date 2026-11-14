package ir.vil3ntec.tohid.core.account

import ir.vil3ntec.tohid.core.account.EmailClaimForm.Next
import ir.vil3ntec.tohid.core.account.EmailClaimForm.Problem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  AUTH-08 ⇐ «فقط ایمیل» (۱۴۰۵/۰۸/۲۳) در «ویرایش حساب» ِ نسخهٔ ۳: حالِ کادرِ کدِ **ایمیلِ** تازه. کدهای خطا همان‌اند که
 *  `server/src/routes/me.js` و `lib/otp.js` می‌دهند (`server/test/email-only-auth.test.js`)؛ سقفِ تلاش و نرخ فقط از سرور.
 */
class EmailClaimFormTest {

  private val phone = "new.owner@test.local"

  @Test fun without_an_email_claim_the_code_box_never_opens() {
    val f = EmailClaimForm()
    assertFalse(f.shown)
    assertEquals("", f.codeToSend())
    for (code in listOf("email_taken", "email_locked", "reauth_required", "offline", "http_500")) {
      val (next, what) = f.afterSave(code, phone)
      assertEquals(Next.SERVER_MESSAGE, what, code)
      assertFalse(next.shown, code)
    }
    assertEquals(EmailClaimForm() to Next.NONE, f.afterSave(null, phone))
  }

  @Test fun code_required_asks_the_code_of_the_same_email_then_opens_the_box() {
    val (asked, what) = EmailClaimForm().afterSave("email_code_required", " $phone ")
    assertEquals(Next.REQUEST_CODE, what)
    assertFalse(asked.shown)
    //  بی ایمیل ⇒ کدی خواسته نمی‌شود، همان پیامِ سرور
    assertEquals(Next.SERVER_MESSAGE, EmailClaimForm().afterSave("email_code_required", "  ").second)

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

  @Test fun the_code_is_bound_to_the_email() {
    val open = EmailClaimForm().sent(phone, 60).code("۱۲۳۴۵۶")
    assertEquals(open, open.email(" $phone"))
    val other = open.email("other@test.local")
    assertFalse(other.shown)
    assertEquals("", other.code)
    assertEquals("", other.codeToSend())
  }

  @Test fun bad_code_sends_nothing_and_says_so_in_place() {
    val open = EmailClaimForm().sent(phone, 0)
    for (typed in listOf("", "12", "12a456", "123456789")) {
      assertNull(open.code(typed).codeToSend(), typed)
    }
    assertEquals(Problem.FORMAT, open.code("12").badCode().problem)
    assertEquals("123456", open.code(" ۱۲۳ ۴۵۶ ").codeToSend())
    //  نوشتنِ دوباره خطا را می‌برد
    assertNull(open.code("12").badCode().code("1").problem)
  }

  @Test fun wrong_code_keeps_the_box_and_the_code_dead_code_asks_a_new_one() {
    val typed = EmailClaimForm().sent(phone, 0).code("111111")
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
    //  ایمیلِ حسابِ دیگر فقط پس از کدِ درست، و پایانِ کار
    assertEquals(Next.SERVER_MESSAGE, typed.afterSave("email_taken", phone).second)
    //  ذخیره شد ⇒ همه‌چیز پاک
    assertEquals(EmailClaimForm(), typed.afterSave(null, phone).first)
  }

  @Test fun resend_wait_comes_from_the_server_answer() {
    fun json(s: String) = Json.parseToJsonElement(s).jsonObject
    assertEquals(60, EmailClaim.resendSeconds(json("""{"ok":true,"email":"$phone","sent":true,"resendSeconds":60}""")))
    assertEquals(0, EmailClaim.resendSeconds(json("""{"ok":true}""")))
    assertEquals(0, EmailClaim.resendSeconds(json("""{"resendSeconds":"60"}""")))
    assertEquals(0, EmailClaim.resendSeconds(json("""{"resendSeconds":-3}""")))
  }

  @Test fun sending_the_code_follows_the_server_only() {
    //  «صبر کنید»: کدِ همین ایمیل زنده است ⇒ کادر باز با پیامِ سرور (حتی پس از بستن و باز کردنِ برنامه)
    val (wait, w) = EmailClaimForm().sendFailed("otp_resend_wait", " $phone")
    assertEquals(Next.SERVER_MESSAGE, w)
    assertTrue(wait.shown)
    assertEquals(phone, wait.codeFor)
    val typed = EmailClaimForm().sent(phone, 0).code("1234")
    assertEquals("1234", typed.sendFailed("otp_resend_wait", phone).first.code)
    //  سقفِ روزانه، سقفِ نرخ، ایمیلِ نرفته و … ⇒ همان پیامِ سرور، کادر همان‌طور که بود
    for (code in listOf("otp_daily_limit", "rate_limited", "delivery_failed", "email_locked", "reauth_required", "offline")) {
      assertEquals(typed to Next.SERVER_MESSAGE, typed.sendFailed(code, phone), code)
    }
  }
}
