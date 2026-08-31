package ir.vil3ntec.tohid.core.config

import android.content.Context
import ir.vil3ntec.tohid.BuildConfig

/**
 *  پیکربندیِ برنامه — تنها جایی که نشانیِ سرور از آن خوانده می‌شود.
 *
 *  هیچ صفحه‌ای، هیچ مخزنی و هیچ کلاسِ شبکه‌ای نشانی را از جای دیگری
 *  برنمی‌دارد. علتش ساده است: تا دیروز هر صفحه خودش `state.serverUrl` را
 *  می‌خواند و خودش `trim().trimEnd('/')` می‌کرد؛ یعنی همان قاعده هشت جا
 *  تکرار شده بود و اگر یک جا فراموش می‌شد، همان یک صفحه ۴۰۴ می‌گرفت.
 *
 *  ترتیبِ مقدارها:
 *
 *    ۱) `BuildConfig.API_BASE` — دامنه‌ای که هنگامِ ساختِ نسخه گذاشته شده
 *    ۲) مقدارِ ذخیره‌شده — فقط در نسخه‌هایی که بندِ یک خالی است
 *
 *  در نسخهٔ منتشرشده بندِ یک پر است، پس کاربر نه نشانی را می‌بیند و نه
 *  می‌تواند برنامه را به سرورِ دیگری ببرد.
 *
 *  پیکربندی از منطقِ کار جداست: این کلاس فقط «کجا» را می‌داند، نه اینکه
 *  با آن چه می‌کنند.
 */
object AppConfig {

  /** نشانیِ زمانِ ساخت. خالی یعنی این نسخه به سروری بسته نشده. */
  val buildTimeBaseUrl: String get() = ApiConfig.normalize(BuildConfig.API_BASE)

  /** آیا نشانی در خودِ نسخه نشسته است */
  val isLocked: Boolean get() = buildTimeBaseUrl.isNotEmpty()

  /**
   *  آیا `http://` ساده پذیرفته است.
   *
   *  فقط در نسخهٔ آزمایشی، وگرنه روی رایانهٔ خودی نمی‌شود چیزی را امتحان
   *  کرد. آنچه به دستِ کاربر می‌رسد فقط https است — هم اینجا و هم در
   *  `res/xml/network_security.xml`، که همان قاعده را در سطحِ خودِ
   *  اندروید هم می‌بندد.
   */
  val allowInsecure: Boolean get() = BuildConfig.DEBUG

  /** نشانیِ ریشه — بدونِ `/api/v1`، بدونِ `/` آخر */
  fun baseUrl(context: Context): String =
    if (isLocked) buildTimeBaseUrl else ApiConfig.normalize(prefs(context).getString(KEY_BASE, ""))

  /** آیا برنامه می‌داند به کجا وصل شود */
  fun isConfigured(context: Context): Boolean =
    ApiConfig.isValid(baseUrl(context), allowInsecure)

  /**
   *  گذاشتنِ نشانی به دست — فقط در نسخهٔ بی‌نشانی.
   *
   *  اگر نسخه با دامنه ساخته شده باشد این کار بی‌اثر است و `false`
   *  برمی‌گرداند؛ بی‌سروصدا نادیده نمی‌گیرد تا صدازننده بفهمد چه شد.
   */
  fun setBaseUrl(context: Context, value: String): Boolean {
    if (isLocked) return false
    prefs(context).edit().putString(KEY_BASE, ApiConfig.normalize(value)).apply()
    return true
  }

  /** چرا نشانیِ فعلی کار نمی‌کند — برای نشان دادن در تنظیمات */
  fun rejection(context: Context): ApiConfig.Rejection? =
    ApiConfig.reject(baseUrl(context), allowInsecure)

  /**
   *  پیکربندی جداست از داده‌های کار، پس پروندهٔ خودش را دارد.
   *  پاک شدنِ حساب نباید نشانیِ سرور را ببرد و برعکس.
   */
  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences("tohid-config", Context.MODE_PRIVATE)

  private const val KEY_BASE = "api_base_url"
}
