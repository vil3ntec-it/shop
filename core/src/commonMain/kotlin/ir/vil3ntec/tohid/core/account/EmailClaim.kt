package ir.vil3ntec.tohid.core.account

import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.core.text.TextNormalizer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 *  AUTH-08 ⇐ «فقط ایمیل» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳) — افزودنِ ایمیل به حسابی که ایمیل ندارد (کهنهٔ فقط‌شماره)، فقط
 *  با کدِ همان ایمیل. شماره دیگر راهِ ورود نیست و بی کد ثبت/عوض می‌شود؛ کدِ پیامکیِ شماره برداشته شد.
 *
 *  قراردادِ سرور (`server/src/routes/me.js`):
 *    ۱) `PUT /api/me { name, email }` بی کد ⇒ ۴۰۰ `email_code_required`؛
 *    ۲) `POST /api/me/email/code { email }` ⇒ کدِ یک‌بارمصرف به همان ایمیل (نشستِ تازه لازم؛ ایمیل‌دار ⇒ ۴۰۹ `email_locked`)؛
 *    ۳) `PUT /api/me { name, email, emailCode }` ⇒ ذخیره، یا `otp_*`، یا (فقط پس از کدِ درست) `email_taken`.
 *
 *  این‌جا فقط سه چیز است، بی شبکه و بی رابط: گامِ بعد از روی کدِ ماشین‌خوانِ پاسخ، پاک‌سازیِ کدِ تایپ‌شده و مهلتِ
 *  «ارسالِ دوباره» ِ پاسخِ سرور. حالِ کادرِ کد در فرمِ نسخهٔ ۳: [EmailClaimForm].
 */
object EmailClaim {

  enum class Step {
    /** ذخیره شد */
    SAVED,

    /** سرور کدِ همان ایمیل را می‌خواهد ⇒ کد را بخواه و کادرِ کد را باز کن */
    SEND_CODE,

    /** کد رفت (یا کدِ پیشین هنوز زنده است) ⇒ کادرِ کد باز، منتظرِ کاربر */
    ENTER_CODE,

    /** کد غلط بود ⇒ همان کادر و همان کد، با پیامِ سرور */
    RETRY_CODE,

    /** کد دیگر کار نمی‌کند (منقضی، مصرف‌شده، قفل) ⇒ کدِ تازه بخواه */
    NEW_CODE,

    /** پایان با پیامِ سرور: ایمیلِ گرفته‌شده یا قفل، شبکه، نشستِ کهنه و … */
    STOP,
  }

  /** پس از `PUT /api/me`. `errorCode == null` یعنی موفق. */
  fun afterSave(errorCode: String?): Step = when (errorCode) {
    null -> Step.SAVED
    "email_code_required" -> Step.SEND_CODE
    "otp_wrong" -> Step.RETRY_CODE
    "otp_expired", "otp_used", "otp_not_found", "otp_locked", "otp_required" -> Step.NEW_CODE
    else -> Step.STOP
  }

  /**
   *  پس از `POST /api/me/email/code`. «صبر کنید» (`otp_resend_wait`) یعنی کدی برای همین ایمیل و همین
   *  حساب تازه رفته و هنوز زنده است (فاصلهٔ ارسالِ دوباره از عمرِ کد کوتاه‌تر است) ⇒ کادرِ کد باز می‌شود،
   *  حتی اگر برنامه میانِ کار بسته و باز شده باشد.
   */
  fun afterSend(errorCode: String?): Step = when (errorCode) {
    null, "otp_resend_wait" -> Step.ENTER_CODE
    else -> Step.STOP
  }

  /**
   *  کدِ تایپ‌شده ⇒ رقم‌های لاتین، بی فاصله و نویسهٔ نامرئی و خط‌تیره («۱۲۳ ۴۵۶» ⇒ «123456»).
   *  فقط ۴ تا ۸ رقم پذیرفته است (طولِ کد را سرور تعیین می‌کند)؛ هر چیزِ دیگر ⇒ null، و چیزی فرستاده نمی‌شود.
   */
  fun normalizeCode(input: String): String? {
    val clean = LocaleFormat.asciiDigits(input).filterNot {
      TextNormalizer.isSpace(it) || TextNormalizer.isInvisible(it) || it == '-'
    }
    return if (clean.length in 4..8 && clean.all { it in '0'..'9' }) clean else null
  }

  /** `resendSeconds` ِ پاسخِ موفقِ `POST /api/me/email/code` — فقط عددِ صریحِ نامنفی؛ هر چیزِ دیگر ⇒ ۰ (همین حالا) */
  fun resendSeconds(o: JsonObject): Int =
    (o["resendSeconds"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it > 0 } ?: 0
}
