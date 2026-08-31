package ir.vil3ntec.tohid.data.repo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TokenStore

/**
 *  نقطهٔ اتصال — تنها جایی که لایه‌ها به هم بسته می‌شوند.
 *
 *      صفحه‌ها  →  مخزن‌ها  →  ApiClient  →  HttpEngine  →  HTTPS
 *                                  ↑
 *                          پیکربندی و توکن
 *
 *  هیچ صفحه‌ای `HttpEngine` یا `TokenStore` نمی‌سازد و هیچ صفحه‌ای نشانیِ
 *  سرور را دست نمی‌زند. صفحه فقط `Backend.shop(context)` را می‌گیرد و
 *  کارش را می‌کند.
 *
 *  چرا یک نمونه برای کلِ برنامه: قفلِ تازه‌سازیِ توکن باید بینِ همهٔ
 *  صدازننده‌ها مشترک باشد. اگر هر صفحه `ApiClient` خودش را می‌ساخت، هر
 *  کدام قفلِ خودش را داشت و چند تازه‌سازیِ موازی، توکنِ همدیگر را باطل
 *  می‌کردند.
 */
object Backend {

  @Volatile private var wiring: Wiring? = null

  /** وقتی نشست از دست رفت — تا برنامه کاربر را به صفحهٔ ورود ببرد */
  @Volatile var onSessionLost: (() -> Unit)? = null

  private class Wiring(context: Context) {
    val app: Context = context.applicationContext
    val tokens = TokenStore(app)

    val engine = HttpEngine(
      baseUrl = { AppConfig.baseUrl(app) },
      allowInsecure = AppConfig.allowInsecure,
      online = { isOnline(app) },
    )

    val api = ApiClient(engine, tokens, onSessionLost = { onSessionLost?.invoke() })

    val auth = AuthRepository(api, tokens)
    val shop = ShopRepository(api)
    val account = AccountRepository(api)
    val sync = SyncRepository(api)
  }

  private fun of(context: Context): Wiring =
    wiring ?: synchronized(this) { wiring ?: Wiring(context).also { wiring = it } }

  fun tokens(context: Context): TokenStore = of(context).tokens
  fun auth(context: Context): AuthRepository = of(context).auth
  fun shop(context: Context): ShopRepository = of(context).shop
  fun account(context: Context): AccountRepository = of(context).account
  fun sync(context: Context): SyncRepository = of(context).sync

  /** آیا برنامه هم نشانی دارد و هم حساب — یعنی اصلاً می‌شود به سرور زد */
  fun isReady(context: Context): Boolean =
    AppConfig.isConfigured(context) && of(context).tokens.signedIn

  /**
   *  آیا دستگاه نت دارد.
   *
   *  نبودنِ نت خطا نیست: برنامه آفلاین کار می‌کند و همگام‌سازی دفعهٔ بعد
   *  خودش می‌رود. این فقط جلوی انتظارِ بی‌فایده را می‌گیرد.
   */
  fun isOnline(context: Context): Boolean = runCatching {
    val manager = context.applicationContext
      .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }.getOrDefault(false)
}
