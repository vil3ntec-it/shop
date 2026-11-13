package ir.vil3ntec.tohid.ui.v3.account.profile

import ir.vil3ntec.tohid.core.account.EmailClaimForm
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrAuth

/**
 *  قاعده‌های خالصِ «ویرایش حساب و دکان» (آزمون: `ProfileEditModelTest`) — قاعده‌های حساب پس از «فقط ایمیل»
 *  (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳): **نام عوض می‌شود**، **شمارهٔ تماس آزاد است** (راهِ ورود نیست؛ بی کد عوض/پاک)،
 *  **ایمیل راهِ ورود است**: ثبت‌شده ⇒ قفل؛ حسابِ بی‌ایمیل ⇒ یک بار با کدِ همان ایمیل (AUTH-08 ⇐ ایمیل).
 *  نام و نامِ دکان همان لحظه روی گوشی می‌نشینند؛ فقط ثبت روی سرور اینترنت می‌خواهد.
 */
object ProfileEditModel {

  /** با «ذخیره» چه می‌شود */
  enum class Step {
    /** نام خالی است ⇒ خطای درجا زیرِ کادرِ نام */
    NAME_MISSING,
    /** بی‌اینترنت/بی‌سرور، ایمیل یا شمارهٔ تازه‌ای نیست ⇒ نام روی گوشی ماند (پیام) */
    LOCAL_ONLY,
    /** بی‌اینترنت و ایمیل یا شمارهٔ تازه هست ⇒ نام روی گوشی ماند، خطای درجا */
    NEEDS_INTERNET,
    /** به سرور فرستاده می‌شود (`updateProfile`) */
    SERVER,
  }

  /** [claim] هر چیزی جز نام که سرور باید ثبت کند (ایمیلِ تازه یا شمارهٔ تماسِ عوض‌شده)؛ خالی ⇒ هیچ */
  fun step(name: String, claim: String, ready: Boolean, online: Boolean): Step = when {
    name.isBlank() -> Step.NAME_MISSING
    !ready || !online -> if (claim.isBlank()) Step.LOCAL_ONLY else Step.NEEDS_INTERNET
    else -> Step.SERVER
  }

  /** حسابِ بی‌ایمیل ⇒ کادرِ افزودنِ ایمیل (با کد)؛ ایمیل‌دار ⇒ ردیفِ قفل */
  fun emailClaimable(email: String): Boolean = email.isBlank()

  /** شمارهٔ تماس عوض شده؟ (فاصلهٔ دو سر مهم نیست) */
  fun phoneChanged(typed: String, saved: String): Boolean = typed.trim() != saved.trim()

  /** نوشتهٔ ذخیره‌نشده هست؟ (برگشت ⇒ «دور ریخته شود؟») */
  fun dirty(name: String, savedName: String, shop: String, savedShop: String, claim: String): Boolean =
    name.trim() != savedName.trim() || shop.trim() != savedShop.trim() || claim.isNotBlank()

  /** پیامِ پس از ثبتِ موفق روی سرور ([email] ایمیلی که افزوده شد) */
  fun savedMessage(email: String): String = if (email.isNotBlank()) StrAccount.phoneSaved else StrAccount.saved

  /** راهنمای زیرِ ایمیل */
  fun emailNote(email: String): String = if (email.isBlank()) StrAccount.emailByPhone else StrAccount.emailLocked

  /** AUTH-08 ⇐ ایمیل: خطای درجای زیرِ کادرِ کدِ ایمیلِ تازه (حالِ کادر: `core/account/EmailClaimForm`) */
  fun codeError(p: EmailClaimForm.Problem?): String? = when (p) {
    null -> null
    EmailClaimForm.Problem.FORMAT -> StrAccount.codeIncomplete
    EmailClaimForm.Problem.WRONG -> StrAccount.codeWrong
    EmailClaimForm.Problem.DEAD -> StrAccount.codeDead
  }

  /** «ارسالِ دوباره» با شمارشِ مهلتی که خودِ سرور داد (`resendSeconds`) */
  fun resendLabel(wait: Int): String = if (wait > 0) StrAuth.resendIn(wait) else StrAuth.resendCode
}
