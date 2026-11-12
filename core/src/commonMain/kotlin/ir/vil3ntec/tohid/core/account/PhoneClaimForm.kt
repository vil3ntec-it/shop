package ir.vil3ntec.tohid.core.account

import ir.vil3ntec.tohid.core.account.PhoneClaim.Step

/**
 *  AUTH-08 — حالِ کادرِ «کدِ همان شماره» در فرمِ «ویرایش حساب» ِ نسخهٔ ۳ (`ui/v3/account/profile/ProfileEditScreen`)،
 *  بی شبکه و بی رابط. گامِ بعد از [PhoneClaim] (کدِ ماشین‌خوانِ پاسخِ سرور).
 *
 *  ⛔ سقفِ تلاش، فاصلهٔ ارسالِ دوباره، سقفِ روزانه و سقفِ نرخ فقط از سرور (`lib/otp.js`، `phone-add` ِ `routes/me.js`):
 *     این‌جا هیچ شمارش یا قفلِ جدایی نیست؛ فقط آن‌چه سرور گفت نشان داده می‌شود ([resendSeconds] از پاسخِ خودش).
 *  ⛔ کد به شماره بسته است: شماره عوض شود ⇒ کادرِ کد بسته و کد پاک.
 *  ⛔ کادرِ کد باز و کدِ نادرست (نه ۴ تا ۸ رقم) ⇒ هیچ درخواستی نمی‌رود ([codeToSend] ⇒ null).
 *  ⛔ پرچمِ خاموش (`auth.phone_add_code`) ⇒ سرور هرگز کد نمی‌خواهد، کادر هرگز باز نمی‌شود و `phoneCode` هرگز فرستاده
 *     نمی‌شود — همان درخواست و همان پیامِ پیشین.
 */
data class PhoneClaimForm(
  /** شماره‌ای که کدش خواسته شد (بی فاصلهٔ دو سر)؛ خالی ⇒ کادرِ کد بسته */
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
    /** کدِ همان شماره را بخواه (`POST /api/me/phone/code`) */
    REQUEST_CODE,

    /** پیامِ خودِ سرور (شمارهٔ گرفته‌شده، «صبر کنید»، سقفِ روزانه، شبکه، …) */
    SERVER_MESSAGE,

    /** سرور دیگر کد نمی‌خواهد (پرچم میانِ کار خاموش شد) ⇒ «دوباره ذخیره کنید» ِ بی کد */
    SAVE_AGAIN,

    /** خطای درجا بس است */
    NONE,
  }

  val shown: Boolean get() = codeFor.isNotEmpty()

  /** شمارهٔ تایپ‌شده عوض شد ⇒ کدی که مالِ شمارهٔ دیگر بود کنار می‌رود */
  fun phone(typed: String): PhoneClaimForm = if (shown && typed.trim() != codeFor) PhoneClaimForm() else this

  fun code(typed: String): PhoneClaimForm = copy(code = typed, problem = null)

  /** «ذخیره»: کدی که با `PUT /api/me` می‌رود — `""` ⇒ بی کد (کادر بسته)؛ `null` ⇒ کادر باز و کد نادرست، هیچ درخواستی */
  fun codeToSend(): String? = if (!shown) "" else PhoneClaim.normalizeCode(code)

  fun badCode(): PhoneClaimForm = copy(problem = Problem.FORMAT)

  /** پس از `PUT /api/me` با [errorCode] (null ⇒ ذخیره شد)؛ [phone] شماره‌ای که افزوده می‌شد */
  fun afterSave(errorCode: String?, phone: String): Pair<PhoneClaimForm, Next> {
    val p = phone.trim()
    return when (PhoneClaim.afterSave(errorCode)) {
      Step.SAVED -> PhoneClaimForm() to Next.NONE
      Step.SEND_CODE -> if (p.isEmpty()) this to Next.SERVER_MESSAGE else copy(code = "", problem = null) to Next.REQUEST_CODE
      Step.NEW_CODE -> if (p.isEmpty()) this to Next.SERVER_MESSAGE else copy(code = "", problem = Problem.DEAD) to Next.REQUEST_CODE
      Step.RETRY_CODE -> copy(problem = Problem.WRONG) to Next.NONE
      else -> this to Next.SERVER_MESSAGE
    }
  }

  /** کدِ تازه به [phone] رفت ⇒ کادرِ خالی و باز، شمارشِ «ارسالِ دوباره» از [resendSeconds] ِ سرور */
  fun sent(phone: String, resendSeconds: Int): PhoneClaimForm =
    PhoneClaimForm(codeFor = phone.trim(), resendSeconds = resendSeconds.coerceIn(0, MAX_WAIT), sent = sent + 1)

  /** `POST /api/me/phone/code` نشد */
  fun sendFailed(errorCode: String?, phone: String): Pair<PhoneClaimForm, Next> {
    val p = phone.trim()
    return when (PhoneClaim.afterSend(errorCode)) {
      //  «صبر کنید»: کدی برای همین شماره تازه رفته و زنده است ⇒ کادر باز (کدِ تایپ‌شدهٔ همان شماره می‌ماند)
      Step.ENTER_CODE -> copy(codeFor = p, code = if (p == codeFor) code else "", resendSeconds = 0) to Next.SERVER_MESSAGE
      Step.WITHOUT_CODE -> PhoneClaimForm() to Next.SAVE_AGAIN
      else -> this to Next.SERVER_MESSAGE
    }
  }

  private companion object {
    /** شمارشِ بیش از یک ساعت یعنی پاسخِ بی‌معنا */
    const val MAX_WAIT = 3600
  }
}
