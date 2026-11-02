package ir.vil3ntec.tohid.data

import android.content.Context

/*
 *  جایگزینِ iOS ِ `data/GoogleSignIn.kt` (Credential Manager ِ اندروید؛ کامپیوتر: ‎GoogleSignInDesk‎ همین را دارد).
 *  ورود با گوگل در iOS (و Sign in with Apple ِ لازمِ قاعدهٔ ۴.۸) گامِ ۳ و نیازمندِ حسابِ اپل است؛ تا آن روز
 *  ورود با ایمیل و کدِ شش‌رقمی (`EmailCodeSheet`) همان کار را می‌کند.
 */
object GoogleSignIn {
  data class Account(val idToken: String, val email: String)

  suspend fun pick(context: Context, clientId: String): Account? =
    throw IllegalStateException("ورود با گوگل روی آیفون هنوز نیست — با ایمیل وارد شوید")
}
