package ir.vil3ntec.tohid.core.account

/**
 *  «حذفِ حساب» به دستِ خودِ کاربر (قاعدهٔ Google Play؛ `docs/GOOGLE-PLAY-fa.md`).
 *
 *  قراردادِ سرور (`server/src/routes/account-deletion.js`، پرچمِ سراسریِ `account.self_delete`، پیش‌فرض خاموش):
 *    • `GET /api/me/account/deletion` ⇒ `enabled`، `blocked` (`has_pump` یا null)، `confirmWith` (`email`/`phone`/`id`)،
 *      `confirmValue` (نشانیِ همان حساب) و دکان‌هایی که می‌روند؛
 *    • `POST … { confirm }` ⇒ حذف، یا ۴۰۳ `reauth_required` (پنجرهٔ رمز ِ `ReauthGate`)، ۴۰۳ `self_delete_disabled`،
 *      ۴۰۹ `has_pump`، ۴۰۰ `confirm_mismatch`.
 *
 *  این‌جا فقط دو چیز است، بی شبکه و بی رابط: برگه با کدام مرحله باز شود، و آیا نوشتهٔ کاربر همان نشانی است.
 *  داورِ نهایی سرور است؛ این سنجه فقط دکمه را تا نوشتنِ درست خاموش نگه می‌دارد.
 */
object AccountDeletion {

  /** صفحهٔ حریمِ خصوصی (GitHub Pages ِ همین مخزن، `privacy.html`) — همان نشانیِ Play Console */
  const val PRIVACY_URL = "https://vil3ntec-it.github.io/shop/privacy.html"

  enum class Stage {
    /** پیش‌نمایش هنوز نیامده */
    LOADING,

    /** بی اینترنت یا خطای سرور ⇒ پیام و «دوباره» */
    OFFLINE,

    /** پرچمِ سرور خاموش ⇒ راهِ «از پشتیبانی بخواهید» */
    SUPPORT,

    /** حسابِ پمپ‌دار ⇒ فقط از پشتیبانی */
    PUMP,

    /** پیش‌نمایش و کادرِ نوشتنِ نشانی */
    CONFIRM,
  }

  /** مرحلهٔ برگه از پاسخِ پیش‌نمایش؛ `failed` یعنی پیش‌نمایش نیامد */
  fun stage(loaded: Boolean, failed: Boolean, enabled: Boolean, blocked: String?): Stage = when {
    failed -> Stage.OFFLINE
    !loaded -> Stage.LOADING
    blocked == "has_pump" -> Stage.PUMP
    !enabled -> Stage.SUPPORT
    else -> Stage.CONFIRM
  }

  /**
   *  نوشتهٔ کاربر همان نشانیِ حساب است؟ ایمیل بی حساسیت به حروف و فاصلهٔ دو سر؛ شماره با رقمِ فارسی/عربی هم
   *  و با «۰» یا «+۹۳» ِ اول (نُه رقمِ آخر)؛ شناسه دقیق.
   */
  fun matches(typed: String, confirmWith: String, confirmValue: String): Boolean {
    val t = typed.trim()
    if (t.isEmpty() || confirmValue.isBlank()) return false
    return when (confirmWith) {
      "email" -> t.lowercase() == confirmValue.trim().lowercase()
      "phone" -> {
        val a = digits(t)
        val b = digits(confirmValue)
        a.length >= 9 && b.length >= 9 && a.takeLast(9) == b.takeLast(9)
      }
      else -> t == confirmValue.trim()
    }
  }

  /** فقط رقم‌ها، فارسی و عربی هم به لاتین */
  private fun digits(s: String): String = buildString {
    for (c in s) {
      when (c) {
        in '0'..'9' -> append(c)
        in '۰'..'۹' -> append('0' + (c - '۰'))
        in '٠'..'٩' -> append('0' + (c - '٠'))
      }
    }
  }
}
