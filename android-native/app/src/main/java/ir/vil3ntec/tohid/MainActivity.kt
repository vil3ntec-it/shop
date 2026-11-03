package ir.vil3ntec.tohid

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import ir.vil3ntec.tohid.data.ShopStore
import kotlinx.coroutines.launch

/*
 *  نقطهٔ ورودِ اندروید. بدنهٔ صفحه، پیوند، لمسِ اعلان، تنظیم‌های پیش از صفحه و همگام‌سازی در
 *  `AppShellAndroid.kt` (`AppShell`، `AppStart`) است — همان که ریشهٔ آیفون هم صدا می‌زند؛ این‌جا فقط
 *  چرخهٔ عمرِ Activity و آنچه فقط اندروید دارد.
 *
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

  /** برنامهٔ باز (singleTask) با لمسِ اعلانِ سفارش، یا پیوندِ `vill3n://app/…` */
  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    openNotice(intent)
    handleLink(intent)
  }

  /** M6: لمسِ اعلانِ سفارش ⇒ همان سفارش؛ رسید همان دم (اگر نت هست)، وگرنه در صف تا دورِ بعدیِ OrdersLive */
  private fun openNotice(intent: android.content.Intent?) = AppStart.openNotice(applicationContext, intent, lifecycleScope)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    //  OPS-03: پیش از هر کارِ دیگر — خطای گرفته‌نشده گزارشِ بی دادهٔ شخصی با کد می‌شود
    ir.vil3ntec.tohid.data.platform.CrashReporter.install(applicationContext)
    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      runCatching { ir.vil3ntec.tohid.data.platform.CrashReporter.flush(applicationContext) }
    }
    enableEdgeToEdge()

    //  «سرورِ بعدی» (پرچمِ `app.multi_server`) فقط این‌جا، پیش از هر دفتر و درخواستی، عوض می‌شود
    ir.vil3ntec.tohid.platform.ServerSwitchAndroid.applyPending(applicationContext)
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

    //  M6: برنامه با لمسِ اعلانِ سفارش باز شد ⇒ رسید در صف و همان سفارش
    openNotice(intent)

    // کلیدِ انیمیشن و بقیهٔ تنظیم‌ها پیش از اولین کشیدنِ صفحه خوانده می‌شوند
    AppStart.loadSettings(applicationContext)
    //  «دستگاه‌ها»: بارکدخوانِ ثبت‌شده ⇒ سرویسِ اسکن در پس‌زمینه (بی بارکدخوان هیچ)
    runCatching { ir.vil3ntec.tohid.scan.ScanBackground.sync(applicationContext) }
    //  پیوندِ vill3n://app (رابطِ نسخهٔ ۳ تنها رابط است)
    syncLinkAlias()

    //  Sync v1 (کارِ دوره‌ای و سوکتِ زنده؛ هیچ‌کدام خطا بیرون نمی‌دهد). حالتِ پیک (DLV-01): دفترِ دکانی در کار نیست
    ir.vil3ntec.tohid.data.ShopRole.courierMode.value = ir.vil3ntec.tohid.data.ShopRole.isCourier(applicationContext)
    live = AppStart.startSync(applicationContext)

    setContent { AppShell(applicationContext, store) }
    handleLink(intent)
  }

  /**
   *  `AppLinkAlias` ِ مانیفست (intent-filter ِ `vill3n://app`، پیش‌فرض `enabled="false"`) در هر اجرا روشن
   *  می‌شود. فقط وقتی حالت فرق دارد چیزی نوشته می‌شود؛ خطا (مثلاً سیاستِ دستگاه) بی‌صدا.
   */
  private fun syncLinkAlias() {
    runCatching {
      val alias = android.content.ComponentName(this, "ir.vil3ntec.tohid.AppLinkAlias")
      val want = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      if (packageManager.getComponentEnabledSetting(alias) != want) {
        packageManager.setComponentEnabledSetting(alias, want, android.content.pm.PackageManager.DONT_KILL_APP)
      }
    }
  }

  /** پیوندِ بیرونی (`vill3n://app/…`) ⇐ حالت و صفحهٔ دقیقش (`AppStart.openLink`) */
  private fun handleLink(intent: android.content.Intent?) = AppStart.openLink(applicationContext, intent?.dataString)

  override fun onDestroy() {
    runCatching { live?.disconnect() }
    super.onDestroy()
  }

  /*
   *  چتِ دکان (v3 §۳۳، CHAT-04): «خوانده شد» فقط وقتی برنامه واقعاً جلوی
   *  چشم است. برنامه در پس‌زمینه پیام را می‌گیرد («رسید») ولی «خواند» نه.
   */
  override fun onResume() {
    super.onResume()
    ir.vil3ntec.tohid.data.ShopChatSync.foreground = true
    //  اسکنِ بارکدخوانِ وصل‌شده از این لحظه یک‌راست به صفحهٔ باز می‌رود، نه صف
    ir.vil3ntec.tohid.scan.ScanInbox.foreground = true
  }

  override fun onPause() {
    ir.vil3ntec.tohid.data.ShopChatSync.foreground = false
    ir.vil3ntec.tohid.scan.ScanInbox.foreground = false
    super.onPause()
  }
}
