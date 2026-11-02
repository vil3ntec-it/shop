package ir.vil3ntec.tohid.ios

import android.content.IosContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.Move
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.AppRoot
import ir.vil3ntec.tohid.ui.ProvideLang
import ir.vil3ntec.tohid.ui.UiV3
import ir.vil3ntec.tohid.ui.courier.CourierHome
import ir.vil3ntec.tohid.ui.directionOf
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.WelcomeScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.ThemeStore
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3Transitions
import ir.vil3ntec.tohid.ui.v3.modes.ModeChooser
import ir.vil3ntec.tohid.ui.v3.modes.ModeState
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
 *  تفاوت با اندروید (docs/IOS-PLAN-fa.md بخشِ ۰.۷): همگام‌سازیِ دوره‌ای و سوکتِ زنده هنوز نیست (‎SyncV1WorkerIos‎،
 *  ‎LiveSocketIos‎)؛ دفتر محلی و رمزدار است (KEK در Keychain)؛ پیوندِ vill3n:// و لمسِ اعلان (`handleLink`،
 *  `openNotice`) و `CrashNotice` ِ خصوصیِ `MainActivity` گامِ ۳.
 */
private object Boot {
  val store: ShopStore by lazy {
    ir.vil3ntec.tohid.data.platform.CrashReporter.install(IosContext)
    ir.vil3ntec.tohid.platform.ServerSwitchAndroid.applyPending(IosContext)
    val s = ShopStore(IosContext)
    runCatching { ir.vil3ntec.tohid.sync.SavedLogins.purgeTokens(IosContext) }
    Motion.load(IosContext)
    LocaleChoice.load(IosContext)
    ShopDayChoice.load(IosContext)
    ir.vil3ntec.tohid.data.ExpiryPref.load(IosContext)
    ThemeStore.load(IosContext)
    ModeState.load(IosContext)
    UiV3.load(IosContext)
    ShopRole.courierMode.value = ShopRole.isCourier(IosContext)
    s
  }
}

@Composable
fun IosRoot() {
  val store = remember { Boot.store }
  LaunchedEffect(store) { store.loadAndSummarize() }
  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) { runCatching { ir.vil3ntec.tohid.data.platform.CrashReporter.flush(IosContext) } }
  }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    CompositionLocalProvider(
      LocalContext provides IosContext,
      LocalConfiguration provides Configuration(maxWidth.value.toInt(), maxHeight.value.toInt()),
    ) {
      IosApp(store)
    }
  }
}

/** همان بدنهٔ `setContent` ِ `MainActivity` */
@Composable
private fun IosApp(store: ShopStore) {
  val prefs = remember { IosContext.getSharedPreferences("tohid", 0) }
  var theme by remember {
    mutableStateOf(runCatching { ThemeChoice.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeChoice.SYSTEM))
  }
  val onTheme: (ThemeChoice) -> Unit = {
    theme = it
    prefs.edit().putString("theme", it.name).apply()
  }
  var welcomed by remember { mutableStateOf(prefs.getBoolean("welcomed", false)) }
  val courier by ShopRole.courierMode.collectAsState()
  val welcome: () -> Unit = {
    prefs.edit().putBoolean("welcomed", true).apply()
    welcomed = true
  }
  val signOut: () -> Unit = {
    prefs.edit().putBoolean("welcomed", false).apply()
    welcomed = false
  }

  if (!UiV3.on) {
    TohidTheme(theme) {
      ProvideLang {
        when {
          !welcomed -> WelcomeScreen(store, welcome)
          courier -> CourierHome(onSignedOut = signOut)
          else -> AppRoot(store = store, theme = theme, onTheme = onTheme)
        }
      }
    }
    return
  }

  val chosen = ModeState.chosen.value
  ModeState.onCourierSignedOut = signOut
  ModeState.onStartShop = signOut
  val mode = remember(chosen, courier, welcomed) { ModeState.current(IosContext) }
  val screen = when {
    !welcomed && chosen == null -> "choose"
    !welcomed -> "welcome"
    else -> mode.id
  }
  TohidTheme(theme) {
    ProvideLang {
      AnimatedContent(
        targetState = screen,
        transitionSpec = { V3Transitions.page(Move.NONE, directionOf(LocaleChoice.lang)) },
        label = "mode",
      ) { s ->
        when (s) {
          "choose" -> ModeChooser { picked -> ModeState.choose(IosContext, picked) }
          "welcome" -> WelcomeScreen(store, welcome)
          AppMode.COURIER.id -> CourierHome(onSignedOut = { ModeState.signedOutOfCourier() })
          else -> AppRoot(store = store, theme = theme, onTheme = onTheme)
        }
      }
    }
  }
}
