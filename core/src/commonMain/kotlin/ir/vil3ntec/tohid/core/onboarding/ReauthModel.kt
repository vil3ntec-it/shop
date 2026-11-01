package ir.vil3ntec.tohid.core.onboarding

/** راهِ اثباتِ دوبارهٔ هویت — همانی که سرور می‌پذیرد (`POST /auth/reauth`) */
enum class ReauthProof { PASSWORD, CODE }

object ReauthRules {
  /** سرور (`otp.verify`) فقط ۰-۹ ِ لاتین را نگه می‌دارد؛ تا هشت رقم مثلِ پیش */
  const val MAX_CODE = 8

  fun codeDigits(raw: String): String = OnboardingText.latinDigits(raw).filter { it in '0'..'9' }.take(MAX_CODE)

  /** حسابِ نامعلوم یا دارای رمز ⇒ رمز (با راهِ کد کنارش)؛ حسابِ بی‌رمز ⇒ کد، همان لحظه */
  fun proofFor(known: Boolean, hasPassword: Boolean): ReauthProof =
    if (!known || hasPassword) ReauthProof.PASSWORD else ReauthProof.CODE

  fun canSubmit(busy: Boolean, proof: ReauthProof?, secret: String): Boolean = !busy && proof != null && secret.isNotBlank()
}
