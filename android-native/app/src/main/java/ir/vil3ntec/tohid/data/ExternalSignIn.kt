package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  ورود با «فراهم‌کنندهٔ بیرونی» — گوگل و اپل، یک قرارداد (`docs/IOS-PLAN-fa.md` §۰.۱۱).
 *
 *  سرور برای هر دو یک قاعده دارد (`server/src/routes/auth.js`، `PROVIDERS`): یافتن با شناسهٔ فراهم‌کننده،
 *  ساختن/وصل کردن فقط با ایمیلِ تأییدشده، و ایمیلِ دارای حساب ⇐ ۴۰۹ `<id>_link_required` و اثباتِ روشِ
 *  قبلیِ حساب (رمز یا کدِ ایمیل) — هرگز وصلِ خودکار با ایمیل. این فایل همان را سمتِ برنامه یکی می‌کند:
 *  `ExternalLinkAsk` (برگهٔ «وصل کردن به حسابِ موجود») برای هر دو.
 *
 *  ⛔ این فایل هیچ API ِ سکو ندارد. پنجرهٔ هر فراهم‌کننده مالِ لایهٔ همان سکوست:
 *  - گوگل: `data/GoogleSignIn.kt` (Credential Manager ِ اندروید؛ کامپیوتر و آیفون جایگزینِ خودشان).
 *  - اپل: فقط آیفون، `ExternalSignIn.apple` را لایهٔ آیفون هنگامِ شروعِ برنامه می‌گذارد
 *    (`ios/shared/src/iosMain/…/ios/AppleSignInIos.kt`، `ASAuthorizationAppleIDProvider`). اندروید و کامپیوتر
 *    آن را `null` می‌گذارند ⇒ دکمهٔ «ورود با اپل» هرگز ساخته نمی‌شود و رفتارشان همان پیشین است.
 */
enum class SignInProvider(val id: String) {
  GOOGLE("google"),
  APPLE("apple");

  /** کدِ ۴۰۹ ِ «این ایمیل حساب دارد؛ روشِ قبلی را ثابت کنید» */
  val linkRequiredCode: String get() = "${id}_link_required"
}

/** فراهم‌کننده‌ای که منتظرِ اثباتِ روشِ قبلیِ حساب است (۴۰۹ `<id>_link_required`) */
data class ExternalLinkAsk(
  val idToken: String,
  val email: String,
  val message: String,
  val codeSent: Boolean = false,
  val problem: String? = null,
  val provider: SignInProvider = SignInProvider.GOOGLE,
  /** اپل: nonce ِ خامِ سرور که توکن به آن بسته است — همان در گامِ اثبات دوباره می‌رود */
  val nonce: String? = null,
)

/** دستاوردِ پنجرهٔ اپل: توکنِ امضاشده، ایمیل (از توکن)، و نام — اپل نام را فقط بارِ نخست می‌دهد */
data class AppleCredential(val idToken: String, val email: String, val name: String = "")

/** پنجرهٔ «ورود با اپل» ِ سکو */
fun interface AppleSignInUi {
  /**
   *  `nonce` ِ خامِ سرور؛ پیاده‌سازی SHA-256 ِ هگزِ آن را به اپل می‌دهد (همان که سرور با ادعای `nonce` می‌سنجد).
   *  `null` یعنی کاربر خودش بست — خطا نیست و پیامِ قرمز نمی‌گیرد. هر چیزِ دیگر با پیامِ روشن پرتاب می‌شود.
   */
  suspend fun pick(context: Context, nonce: String): AppleCredential?
}

object ExternalSignIn {
  /** فقط لایهٔ آیفون می‌گذارد؛ اندروید و کامپیوتر `null` (دکمه‌ای نیست) */
  var apple: AppleSignInUi? = null
}
