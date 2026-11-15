package ir.vil3ntec.tohid.core.account

import ir.vil3ntec.tohid.core.account.EmailClaim.Step

/**
 *  AUTH-08 ⇐ «فقط ایمیل» — حالِ کادرِ «کدِ همان ایمیل» در فرمِ «ویرایش حساب» ِ نسخهٔ ۳ (`ui/v3/account/profile/ProfileEditScreen`)،
 *  فقط برای حسابِ بی‌ایمیل، بی شبکه و بی رابط. گامِ بعد از [EmailClaim] (کدِ ماشین‌خوانِ پاسخِ سرور).
 *
 *  ⛔ سقفِ تلاش، فاصلهٔ ارسالِ دوباره، سقفِ روزانه و سقفِ نرخ فقط از سرور (`lib/otp.js`، `email-add` ِ `routes/me.js`):
 *     این‌جا هیچ شمارش یا قفلِ جدایی نیست؛ فقط آن‌چه سرور گفت نشان داده می‌شود ([resendSeconds] از پاسخِ خودش).
 *  ⛔ کد به ایمیل بسته است: ایمیل عوض شود ⇒ کادرِ کد بسته و کد پاک.
 *  ⛔ کادرِ کد باز و کدِ نادرست (نه ۴ تا ۸ رقم) ⇒ هیچ درخواستی نمی‌رود ([codeToSend] ⇒ null).
 */
data class EmailClaimForm(
  /** ایمیلی که کدش خواسته شد (بی فاصلهٔ دو سر)؛ خالی ⇒ کادرِ کد بسته */
  val codeFor: String = "",
  val code: String = "",
  val problem: Problem? = null,
  /** «ارسالِ دوباره» پس از این چند ثانیه — `resendSeconds` ِ پاسخِ سرور، ۰ ⇒ همین حالا */
  val resendSeconds: Int = 0,
  /** شمارِ کدهایی که در این فرم رفت؛ شمارشِ «ارسالِ دوباره» با هر کدِ تازه از نو آغاز می‌شود */
  val sent: Int = 0,
) {

  /** خطای درجا زیرِ کادرِ کد */
  enum class Problem {
    /** کد ۴ تا ۸ رقم نیست ⇒ چیزی فرستاده نشد */
    FORMAT,

    /** سرور گفت کد درست نیست (`otp_wrong`) ⇒ همان کد می‌ماند تا درستش کنند */
    WRONG,

    /** کدِ پیشین دیگر کار نمی‌کند (منقضی، مصرف‌شده، تلاشِ زیاد) ⇒ کدِ تازه خواسته می‌شود */
    DEAD,
  }

  /** پس از پاسخِ ناموفق، صفحه چه کند */
  enum class Next {
    /** کدِ همان ایمیل را بخواه (`POST /api/me/email/code`) */
    REQUEST_CODE,

    /** پیامِ خودِ سرور (ایمیلِ گرفته‌شده، «صبر کنید»، سقفِ روزانه، شبکه، …) */
    SERVER_MESSAGE,

    /** خطای درجا بس است */
    NONE,
  }

  val shown: Boolean get() = codeFor.isNotEmpty()

  /** ایمیلِ تایپ‌شده عوض شد ⇒ کدی که مالِ ایمیلِ دیگر بود کنار می‌رود */
  fun email(typed: String): EmailClaimForm = if (shown && typed.trim() != codeFor) EmailClaimForm() else this

  fun code(typed: String): EmailClaimForm = copy(code = typed, problem = null)

  /** «ذخیره»: کدی که با `PUT /api/me` می‌رود — `""` ⇒ بی کد (کادر بسته)؛ `null` ⇒ کادر باز و کد نادرست، هیچ درخواستی */
  fun codeToSend(): String? = if (!shown) "" else EmailClaim.normalizeCode(code)

  fun badCode(): EmailClaimForm = copy(problem = Problem.FORMAT)

  /** پس از `PUT /api/me` با [errorCode] (null ⇒ ذخیره شد)؛ [email] ایمیلی که افزوده می‌شد */
  fun afterSave(errorCode: String?, email: String): Pair<EmailClaimForm, Next> {
    val p = email.trim()
    return when (EmailClaim.afterSave(errorCode)) {
      Step.SAVED -> EmailClaimForm() to Next.NONE
      Step.SEND_CODE -> if (p.isEmpty()) this to Next.SERVER_MESSAGE else copy(code = "", problem = null) to Next.REQUEST_CODE
      Step.NEW_CODE -> if (p.isEmpty()) this to Next.SERVER_MESSAGE else copy(code = "", problem = Problem.DEAD) to Next.REQUEST_CODE
      Step.RETRY_CODE -> copy(problem = Problem.WRONG) to Next.NONE
      else -> this to Next.SERVER_MESSAGE
    }
  }

  /** کدِ تازه به [email] رفت ⇒ کادرِ خالی و باز، شمارشِ «ارسالِ دوباره» از [resendSeconds] ِ سرور */
  fun sent(email: String, resendSeconds: Int): EmailClaimForm =
    EmailClaimForm(codeFor = email.trim(), resendSeconds = resendSeconds.coerceIn(0, MAX_WAIT), sent = sent + 1)

  /** `POST /api/me/email/code` نشد */
  fun sendFailed(errorCode: String?, email: String): Pair<EmailClaimForm, Next> {
    val p = email.trim()
    return when (EmailClaim.afterSend(errorCode)) {
      //  «صبر کنید»: کدی برای همین ایمیل تازه رفته و زنده است ⇒ کادر باز (کدِ تایپ‌شدهٔ همان ایمیل می‌ماند)
      Step.ENTER_CODE -> copy(codeFor = p, code = if (p == codeFor) code else "", resendSeconds = 0) to Next.SERVER_MESSAGE
      else -> this to Next.SERVER_MESSAGE
    }
  }

  private companion object {
    /** شمارشِ بیش از یک ساعت یعنی پاسخِ بی‌معنا */
    const val MAX_WAIT = 3600
  }
}
