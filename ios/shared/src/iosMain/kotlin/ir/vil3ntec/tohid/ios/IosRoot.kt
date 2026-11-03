package ir.vil3ntec.tohid.ios

import android.content.IosContext
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.AppShell
import ir.vil3ntec.tohid.AppStart
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/*
 *  ریشهٔ برنامهٔ آیفون — همان کارِ `MainActivity` ِ اندروید و `desktop/…/Main.kt` ِ کامپیوتر، نه صفحه‌ای تازه.
 *
 *  هر چه دیده می‌شود همان composable ِ مشترکِ اندروید است که `ios/shared` برای iOS کامپایل می‌کند:
 *  انتخابِ حالت (`ModeChooser`)، ورود و خوش‌آمد (`WelcomeScreen`)، و خودِ برنامه (`AppRoot`: صندوق و فروش،
 *  کالاها، مشتری‌ها، گزارش…) یا حالتِ پیک (`CourierHome`). این فایل فقط آن‌چه `MainActivity` پیش از
 *  `setContent` می‌کند را می‌کند: بارگذاریِ تنظیم‌ها، دفترِ فروشگاه (`ShopStore`)، و فراهم کردنِ `LocalContext`
 *  و اندازهٔ پنجره (`LocalConfiguration`).
 *
 *  ترتیبِ کارها همان `onCreate` است: گزارشِ خطا، «سرورِ بعدی»، دفتر، شستنِ توکن‌های کهنه، تنظیم‌ها، نقشِ پیک.
 *
 *  از گامِ ۷ بدنهٔ صفحه، `CrashNotice`، تنظیم‌های پیش از صفحه، پیوند، لمسِ اعلان و همگام‌سازی **همان کدِ**
 *  `MainActivity` است، نه رونوشتش: `AppShell` و `AppStart` (‎android-native/…/AppShellAndroid.kt‎). این‌جا فقط
 *  آنچه iOS دارد: `LocalContext`/`LocalConfiguration`، کارگرها، `BGAppRefreshTask` و اجازهٔ اعلان.
 *  تفاوت با اندروید (docs/IOS-PLAN-fa.md بخشِ ۰.۸): همگام‌سازی داخلِ فرآیند و با `BGAppRefreshTask`، نه WorkManager ِ سیستم؛
 *  اجازهٔ اعلان هنگامِ نخستین نمایشِ دکان (همان لحظهٔ اندرویدِ ۱۳+).
 */
internal object IosBoot {
  /** دفتر و تنظیم‌ها بار شده‌اند (پیوند یا لمسِ اعلان پیش از نخستین صفحه هم همین را می‌خواهد) */
  var started = false
    private set

  fun ready() {
    store
  }

  val store: ShopStore by lazy {
    ir.vil3ntec.tohid.data.platform.CrashReporter.install(IosContext)
    ir.vil3ntec.tohid.platform.ServerSwitchAndroid.applyPending(IosContext)
    val s = ShopStore(IosContext)
    runCatching { ir.vil3ntec.tohid.sync.SavedLogins.purgeTokens(IosContext) }
    AppStart.loadSettings(IosContext)
    ShopRole.courierMode.value = ShopRole.isCourier(IosContext)
    IosWork.install()
    IosSync.start()
    started = true
    s
  }
}

@Composable
fun IosRoot() {
  val store = remember { IosBoot.store }
  LaunchedEffect(store) { store.loadAndSummarize() }
  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) { runCatching { ir.vil3ntec.tohid.data.platform.CrashReporter.flush(IosContext) } }
  }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    CompositionLocalProvider(
      LocalContext provides IosActivity,
      LocalConfiguration provides Configuration(maxWidth.value.toInt(), maxHeight.value.toInt()),
    ) {
      //  همان بدنهٔ `setContent` ِ `MainActivity`؛ اجازهٔ اعلان (سفارشِ تازه، دیدبان، یادآور) هنگامِ نخستین نمایشِ دکان
      AppShell(IosContext, store, onShopShown = { IosNotifications.askOnce() })
    }
  }
}
