package ir.vil3ntec.tohid.core.net

/**
 *  مسیرهای API — همه در یک جا.
 *
 *  هیچ رشتهٔ مسیری جای دیگری در برنامه نوشته نمی‌شود. تا دیروز مسیرها
 *  داخلِ `ServerClient` پخش بودند و `/api/v1/` بیست‌وچند بار تکرار شده
 *  بود؛ یعنی رفتن به `v2` یعنی بیست‌وچند ویرایش و یکی‌اش حتماً فراموش
 *  می‌شد. حالا نسخه در `ApiConfig.API_VERSION` است و اینجا فقط مسیرِ
 *  بعد از آن نوشته می‌شود.
 *
 *  هر مسیری که اینجا نیست، صدا زده نمی‌شود. اگر قابلیتی مسیرِ تازه لازم
 *  دارد، اول قرارِ API نوشته می‌شود و بعد اینجا یک خط اضافه می‌شود — نه
 *  اینکه یک صفحه برای خودش نشانی بسازد.
 */
object ApiEndpoints {

  /* ------------------------------ عمومی ------------------------------ */

  const val HEALTH = "/health"

  /** تنظیماتِ باز سرور: کدام راهِ ورود روشن است، کمینهٔ نسخه، … */
  const val CONFIG = "/config"

  /* ------------------------------ ورود ------------------------------ */

  object Auth {
    const val REGISTER = "/auth/register"
    const val LOGIN = "/auth/login"
    //  ورود شاگرد فقط با کد — نه ایمیل، نه شماره، نه رمز
    const val STAFF = "/auth/staff"
    const val GOOGLE = "/auth/google"
    const val REFRESH = "/auth/refresh"
    const val LOGOUT = "/auth/logout"
    const val OTP_REQUEST = "/auth/otp/request"
    const val OTP_VERIFY = "/auth/otp/verify"
    const val PASSWORD = "/auth/password"
    const val PASSWORD_FORGOT = "/auth/password/forgot"
    const val PASSWORD_RESET = "/auth/password/reset"
  }

  /* ------------------------------ حساب ------------------------------ */

  object Me {
    const val ROOT = "/me"
    const val DEVICES = "/me/devices"
    const val SUBSCRIPTION = "/me/subscription"
    const val PLANS = "/me/plans"
    const val PURCHASE_REQUEST = "/me/purchase-request"

    fun device(id: String) = "$DEVICES/${enc(id)}"
  }

  /* ------------------------------ دکان ------------------------------ */

  object Shop {
    const val ME = "/shop/me"
    const val CREATE = "/shop/create"
    const val JOIN = "/shop/join"
    const val MEMBERS = "/shop/members"
    const val STAFF_CODE = "/shop/staff-code"
    const val STAFF_CODE_ROTATE = "/shop/staff-code/rotate"
    const val STAFF_CODES = "/shop/staff-codes"

    fun member(id: String) = "$MEMBERS/${enc(id)}"
    fun staffCode(id: String) = "$STAFF_CODES/${enc(id)}"
  }

  /* ------------------------------ همگام‌سازی ------------------------------ */

  /*
   *  سرور هم `/sync` را دارد و هم `/shop/sync` را؛ دومی نامِ قدیمی است و
   *  هنوز روی همان کد سوار می‌شود. عمداً همان قدیمی نگه داشته شده تا
   *  نسخهٔ تازهٔ برنامه روی سروری که هنوز به‌روز نشده هم کار کند.
   */
  object Sync {
    const val PUSH = "/shop/sync/push"
    const val PULL = "/shop/sync/pull"
  }

  /* ------------------------------ خبرها ------------------------------ */

  /**
   *  خبرهای دکان — «در نبودِ من چه گذشت».
   *
   *  جدا از همگام‌سازی است، عمداً: همگام‌سازی دفترِ دکان را جابه‌جا
   *  می‌کند و پشتِ اشتراک است؛ خبر فقط یک پیام است و صاحب دکان باید
   *  حتی با اشتراکِ تمام‌شده هم ببیندش.
   */
  object Events {
    const val ROOT = "/events"
    const val SEEN = "/events/seen"
  }

  /* ------------------------------ اشتراک ------------------------------ */

  object License {
    const val PUBLIC_KEY = "/license/public-key"
    const val SYNC = "/license/sync"
  }

  /**
   *  چسباندنِ پرسمان به مسیر، با encode شدنِ مقدارها.
   *
   *  تا دیروز مقدارها با `+` به رشته چسبانده می‌شدند. شناسهٔ دستگاه
   *  همیشه شانزده‌تایی hex است و مشکلی پیش نمی‌آورد، ولی همان الگو
   *  روی هر مقدارِ دیگری (نامِ دکان، جست‌وجو) با `&` یا فاصله، پرسمان
   *  را می‌شکست.
   */
  fun withQuery(path: String, params: Map<String, Any?>): String {
    val query = params.entries
      .filter { it.value != null && it.value.toString().isNotEmpty() }
      .joinToString("&") { (key, value) -> "${enc(key)}=${enc(value.toString())}" }
    if (query.isEmpty()) return path
    return path + (if (path.contains('?')) "&" else "?") + query
  }

  private fun enc(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
