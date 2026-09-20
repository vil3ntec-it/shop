package ir.vil3ntec.tohid

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.lifecycleScope
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.AppRoot
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.WelcomeScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.launch

/*
 *  چرا `FragmentActivity` و نه `ComponentActivity`:
 *
 *  `BiometricPrompt` — که قفلِ برنامه با اثر انگشت را ممکن می‌کند —
 *  اکتیویتی از این نوع می‌خواهد. `FragmentActivity` خودش از
 *  `ComponentActivity` ارث می‌برد، پس `setContent` و بقیهٔ چیزها دست
 *  نخورده‌اند؛ فقط یک پله بالاتر در همان زنجیره.
 */
class MainActivity : androidx.fragment.app.FragmentActivity() {

  private lateinit var store: ShopStore

  /** سوکتِ «چیزی عوض شد» — با بسته شدنِ صفحه بسته می‌شود */
  private var live: ir.vil3ntec.tohid.sync.v1.LiveSocket? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    store = ShopStore(applicationContext)
    lifecycleScope.launch { store.loadAndSummarize() }

    /*
     *  شستنِ توکن‌هایی که نسخه‌های قبلی روی گوشی جا گذاشته‌اند.
     *
     *  تا دیروز فهرستِ «ورودِ سریع» کنارِ نامِ هر حساب، توکنِ تازه‌سازیِ
     *  همان حساب را هم — رمزنشده — نگه می‌داشت. آن راه بسته شد، ولی
     *  بستنش فقط جلوی نوشتنِ تازه را می‌گیرد؛ آنچه از قبل روی گوشیِ
     *  کاربر نوشته شده تا ابد آنجا می‌ماند مگر کسی پاکش کند.
     *
     *  اینجا، چون پیش از هر صفحه‌ای یک بار اجرا می‌شود. کارش چند
     *  میلی‌ثانیه است و اگر چیزی برای پاک کردن نباشد، هیچ.
     */
    runCatching { ir.vil3ntec.tohid.sync.SavedLogins.purgeTokens(applicationContext) }

    // کلیدِ انیمیشن پیش از اولین کشیدنِ صفحه خوانده می‌شود
    Motion.load(applicationContext)

    /*
     *  ── Sync v1: کارِ پس‌زمینه و سوکتِ زنده ──────────────────────────
     *  `schedule` کارِ دورهٔ پانزده‌دقیقه‌ای را ثبت می‌کند (از قبل ثبت
     *  شده باشد، `KEEP` دست نمی‌زند). سوکت فقط «چیزی عوض شد» را
     *  می‌آورد و خودِ Pull کارِ همان Worker است.
     *
     *  ⚠️ هیچ‌کدام خطا بیرون نمی‌دهند: برنامه باید بی‌اینترنت و بی‌حساب
     *  هم کامل بالا بیاید.
     */
    runCatching {
      ir.vil3ntec.tohid.sync.v1.SyncV1Worker.schedule(applicationContext)
      live = ir.vil3ntec.tohid.sync.v1.LiveSocket(applicationContext) {
        ir.vil3ntec.tohid.sync.v1.SyncV1Worker.now(applicationContext)
      }
      live?.connect()
    }

    setContent {
      // انتخابِ ظاهر بین اجراها می‌ماند
      val prefs = remember { getSharedPreferences("tohid", MODE_PRIVATE) }
      var theme by remember {
        mutableStateOf(
          runCatching { ThemeChoice.valueOf(prefs.getString("theme", "SYSTEM")!!) }
            .getOrDefault(ThemeChoice.SYSTEM)
        )
      }

      // دروازهٔ ورود فقط بارِ اول می‌آید: یا وارد می‌شوید یا «ادامه بدون
      // حساب» را می‌زنید. از آن به بعد سرِ راه کسی نمی‌ایستد.
      var welcomed by remember { mutableStateOf(prefs.getBoolean("welcomed", false)) }

      TohidTheme(theme) {
        // کلِ برنامه راست‌به‌چپ است
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          if (!welcomed) {
            WelcomeScreen(store) {
              prefs.edit().putBoolean("welcomed", true).apply()
              welcomed = true
            }
          } else {
            AppRoot(
              store = store,
              theme = theme,
              onTheme = {
                theme = it
                prefs.edit().putString("theme", it.name).apply()
              },
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    runCatching { live?.disconnect() }
    super.onDestroy()
  }
}
