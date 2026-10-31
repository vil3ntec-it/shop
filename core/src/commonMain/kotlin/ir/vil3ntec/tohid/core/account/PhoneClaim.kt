package ir.vil3ntec.tohid.core.account

import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.core.text.TextNormalizer

/**
 *  AUTH-08 — افزودنِ شماره به حسابی که شماره ندارد، فقط با کدِ همان شماره.
 *
 *  قراردادِ سرور (`server/src/routes/me.js`، پرچمِ سراسریِ `auth.phone_add_code`، پیش‌فرض خاموش):
 *    ۱) `PUT /api/me { name, phone }` بی کد ⇒ با پرچمِ روشن ۴۰۰ `phone_code_required`؛
 *    ۲) `POST /api/me/phone/code { phone }` ⇒ کدِ یک‌بارمصرف به همان شماره (پرچمِ خاموش ⇒ ۴۰۴ `phone_code_disabled`)؛
 *    ۳) `PUT /api/me { name, phone, phoneCode }` ⇒ ذخیره، یا `otp_*`، یا (فقط پس از کدِ درست) `phone_taken`.
 *
 *  پرچمِ خاموش ⇒ گامِ ۱ همان پاسخِ امروز را می‌دهد و برنامه هیچ کدی نمی‌خواهد؛ رفتار بیت‌به‌بیت همان است.
 *  این‌جا فقط دو چیز است، بی شبکه و بی رابط: گامِ بعد از روی کدِ ماشین‌خوانِ پاسخ، و پاک‌سازیِ کدِ تایپ‌شده.
 */
object PhoneClaim {

  enum class Step {
    /** ذخیره شد */
    SAVED,

    /** سرور کدِ همان شماره را می‌خواهد ⇒ کد را بخواه و کادرِ کد را باز کن */
    SEND_CODE,

    /** کد رفت (یا کدِ پیشین هنوز زنده است) ⇒ کادرِ کد باز، منتظرِ کاربر */
    ENTER_CODE,

    /** کد غلط بود ⇒ همان کادر و همان کد، با پیامِ سرور */
    RETRY_CODE,

    /** کد دیگر کار نمی‌کند (منقضی، مصرف‌شده، قفل) ⇒ کدِ تازه بخواه */
    NEW_CODE,

    /** سرور دیگر کد نمی‌خواهد (پرچم میانِ کار خاموش شد) ⇒ کادرِ کد بسته، «ذخیره» ِ بی کد */
    WITHOUT_CODE,

    /** پایان با پیامِ سرور: شمارهٔ گرفته‌شده یا قفل، شبکه، نشستِ کهنه و … */
    STOP,
  }

  /** پس از `PUT /api/me`. `errorCode == null` یعنی موفق. */
  fun afterSave(errorCode: String?): Step = when (errorCode) {
    null -> Step.SAVED
    "phone_code_required" -> Step.SEND_CODE
    "otp_wrong" -> Step.RETRY_CODE
    "otp_expired", "otp_used", "otp_not_found", "otp_locked", "otp_required" -> Step.NEW_CODE
    else -> Step.STOP
  }

  /**
   *  پس از `POST /api/me/phone/code`. «صبر کنید» (`otp_resend_wait`) یعنی کدی برای همین شماره و همین
   *  حساب تازه رفته و هنوز زنده است (فاصلهٔ ارسالِ دوباره از عمرِ کد کوتاه‌تر است) ⇒ کادرِ کد باز می‌شود،
   *  حتی اگر برنامه میانِ کار بسته و باز شده باشد.
   */
  fun afterSend(errorCode: String?): Step = when (errorCode) {
    null, "otp_resend_wait" -> Step.ENTER_CODE
    "phone_code_disabled" -> Step.WITHOUT_CODE
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
}
