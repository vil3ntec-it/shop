package ir.vil3ntec.tohid.core.config

import android.content.Context
import ir.vil3ntec.tohid.BuildConfig

/**
 *  پیکربندیِ برنامه — تنها جایی که نشانیِ سرور از آن خوانده می‌شود.
 *
 *  هیچ صفحه‌ای، هیچ مخزنی و هیچ کلاسِ شبکه‌ای نشانی را از جای دیگری
 *  برنمی‌دارد. علتش ساده است: تا دیروز هر صفحه خودش نشانی را می‌خواند و
 *  خودش `trim().trimEnd('/')` می‌کرد؛ یعنی همان قاعده هشت جا تکرار شده
 *  بود و اگر یک جا فراموش می‌شد، همان یک صفحه ۴۰۴ می‌گرفت.
 *
 *  **یک سرچشمه و بس:** `BuildConfig.API_BASE` — دامنه‌ای که هنگامِ ساختِ
 *  نسخه داخلش نشسته. کاربر نه آن را می‌بیند و نه می‌تواند برنامه را به
 *  سرورِ دیگری ببرد؛ کادرِ «آدرس سرور» از هر دو صفحه برداشته شده و راهِ
 *  نوشتنش هم بسته است.
 *
 *  پیکربندی از منطقِ کار جداست: این کلاس فقط «کجا» را می‌داند، نه اینکه
 *  با آن چه می‌کنند.
 */
object AppConfig {

  /** نشانیِ زمانِ ساخت. خالی یعنی این نسخه به سروری بسته نشده. */
  val buildTimeBaseUrl: String get() = ApiConfig.normalize(BuildConfig.API_BASE)

  /**
   *  آیا `http://` ساده پذیرفته است.
   *
   *  فقط در نسخهٔ آزمایشی، وگرنه روی رایانهٔ خودی نمی‌شود چیزی را امتحان
   *  کرد. آنچه به دستِ کاربر می‌رسد فقط https است — هم اینجا و هم در
   *  `res/xml/network_security.xml`، که همان قاعده را در سطحِ خودِ
   *  اندروید هم می‌بندد.
   */
  val allowInsecure: Boolean get() = BuildConfig.DEBUG

  /**
   *  نشانیِ ریشه — بدونِ `/api/v1`، بدونِ `/` آخر.
   *
   *  ── تنها سرچشمه ───────────────────────────────────────────────────
   *  فقط از `BuildConfig` می‌آید. تا دیروز اگر نسخه بی‌نشانی ساخته شده
   *  بود، مقدارِ ذخیره‌شده روی گوشی هم خوانده می‌شد و کادرهای «آدرس
   *  سرور» همان را می‌نوشتند. آن کادرها برداشته شدند و این راه هم با
   *  آن‌ها بسته شد: هیچ چیزی روی گوشی نمی‌تواند برنامه را به سرورِ
   *  دیگری ببرد.
   *
   *  مقدارِ به‌جامانده از نسخه‌های قبل هم یک بار پاک می‌شود، وگرنه
   *  گوشی‌ای که دیروز دستی تنظیم شده بود همان‌جا می‌ماند.
   *  ──────────────────────────────────────────────────────────────────
   */
  fun baseUrl(context: Context): String {
    forgetStoredBase(context)
    return buildTimeBaseUrl
  }

  /**
   *  نشانیِ دستیِ نسخه‌های قبل — یک بار پاک می‌شود و دیگر خوانده نمی‌شود.
   *
   *  یک بار در هر اجرای برنامه، نه با هر درخواست: `baseUrl` از دلِ
   *  لایهٔ شبکه صدا زده می‌شود و آن مسیر جای خواندنِ حافظه نیست.
   */
  @Volatile private var cleaned = false

  private fun forgetStoredBase(context: Context) {
    if (cleaned) return
    cleaned = true
    runCatching {
      val prefs = prefs(context)
      if (prefs.contains(KEY_BASE)) prefs.edit().remove(KEY_BASE).apply()
    }
  }

  /** آیا برنامه می‌داند به کجا وصل شود */
  fun isConfigured(context: Context): Boolean =
    ApiConfig.isValid(baseUrl(context), allowInsecure)

  /**
   *  پیکربندی جداست از داده‌های کار، پس پروندهٔ خودش را دارد.
   *  پاک شدنِ حساب نباید نشانیِ سرور را ببرد و برعکس.
   */
  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences("tohid-config", Context.MODE_PRIVATE)

  private const val KEY_BASE = "api_base_url"
}
