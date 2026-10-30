package ir.vil3ntec.tohid.ui.v3.account.profile

import ir.vil3ntec.tohid.core.i18n.StrAccount

/**
 *  قاعده‌های خالصِ «ویرایش حساب و دکان» (آزمون: `ProfileEditModelTest`) — همان سه
 *  قاعدهٔ حساب: **نام عوض می‌شود**، **ایمیل قفل است**، **شماره یک بار ثبت و بعد قفل**.
 *  نام و نامِ دکان همان لحظه روی گوشی می‌نشینند؛ فقط ثبت روی سرور اینترنت می‌خواهد.
 */
object ProfileEditModel {

  /** با «ذخیره» چه می‌شود */
  enum class Step {
    /** نام خالی است ⇒ خطای درجا زیرِ کادرِ نام */
    NAME_MISSING,
    /** بی‌اینترنت/بی‌سرور، شمارهٔ تازه‌ای نیست ⇒ نام روی گوشی ماند (پیام) */
    LOCAL_ONLY,
    /** بی‌اینترنت و شمارهٔ تازه هست ⇒ نام روی گوشی ماند، خطای درجا زیرِ شماره */
    PHONE_NEEDS_INTERNET,
    /** به سرور فرستاده می‌شود (`updateProfile`) */
    SERVER,
  }

  fun step(name: String, claim: String, ready: Boolean, online: Boolean): Step = when {
    name.isBlank() -> Step.NAME_MISSING
    !ready || !online -> if (claim.isBlank()) Step.LOCAL_ONLY else Step.PHONE_NEEDS_INTERNET
    else -> Step.SERVER
  }

  /** شماره ثبت شده ⇒ ردیفِ قفل؛ وگرنه کادرِ یک‌بارهٔ ثبت */
  fun phoneLocked(phone: String): Boolean = phone.isNotBlank()

  /** نوشتهٔ ذخیره‌نشده هست؟ (برگشت ⇒ «دور ریخته شود؟») */
  fun dirty(name: String, savedName: String, shop: String, savedShop: String, claim: String): Boolean =
    name.trim() != savedName.trim() || shop.trim() != savedShop.trim() || claim.isNotBlank()

  /** پیامِ پس از ثبتِ موفق روی سرور */
  fun savedMessage(claim: String): String = if (claim.isNotBlank()) StrAccount.phoneSaved else StrAccount.saved

  /** راهنمای زیرِ ایمیل */
  fun emailNote(email: String): String = if (email.isBlank()) StrAccount.emailByPhone else StrAccount.emailLocked
}
