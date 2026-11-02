package ir.vil3ntec.tohid.ios

import android.content.IosContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.Move
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.AppRoot
import ir.vil3ntec.tohid.ui.ProvideLang
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
 *  ترتیبِ همگام‌سازی، سوکتِ زنده، پیوندِ `vill3n://`، لمسِ اعلان و `CrashNotice` همان `MainActivity` است (‎IosApp.kt‎).
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
    Motion.load(IosContext)
    LocaleChoice.load(IosContext)
    ShopDayChoice.load(IosContext)
    ir.vil3ntec.tohid.data.ExpiryPref.load(IosContext)
    ThemeStore.load(IosContext)
    ModeState.load(IosContext)
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
  //  نقشِ نشست عوض شد ⇐ کارِ دوره‌ای (همان `LaunchedEffect(courier)` ِ `MainActivity`؛ KEEP: بی اثرِ دوم)
  LaunchedEffect(courier) {
    runCatching {
      if (courier) ir.vil3ntec.tohid.sync.v1.SyncV1Worker.cancel(IosContext)
      else ir.vil3ntec.tohid.sync.v1.SyncV1Worker.schedule(IosContext)
    }
  }
  //  اجازهٔ اعلان (سفارشِ تازه، دیدبان، یادآور) — اندرویدِ ۱۳+ همین را هنگامِ باز شدنِ دکان می‌پرسد
  if (welcomed && !courier) LaunchedEffect(Unit) { IosNotifications.askOnce() }
  val welcome: () -> Unit = {
    prefs.edit().putBoolean("welcomed", true).apply()
    welcomed = true
  }
  val signOut: () -> Unit = {
    prefs.edit().putBoolean("welcomed", false).apply()
    welcomed = false
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
      CrashNotice()
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

/**
 *  OPS-03: برنامه دفعهٔ پیش ناگهان بسته شد ⇒ یک بار کدِ گزارش را بگو (برای تماس با پشتیبانی) — همان
 *  `CrashNotice` ِ خصوصیِ `MainActivity`، با همان متن‌ها (`StrShell`) و همان `CrashReporter`. خطا ⇒ هیچ.
 */
@Composable
private fun CrashNotice() {
  var code by remember {
    mutableStateOf(runCatching { ir.vil3ntec.tohid.data.platform.CrashReporter.notice(IosContext) }.getOrNull())
  }
  val shown = code ?: return
  val done = {
    runCatching { ir.vil3ntec.tohid.data.platform.CrashReporter.markShown(IosContext, shown) }
    code = null
  }
  AlertDialog(
    onDismissRequest = done,
    title = { Text(ir.vil3ntec.tohid.core.i18n.StrShell.crashTitle) },
    text = {
      Column {
        Text(ir.vil3ntec.tohid.core.i18n.StrShell.crashBody)
        SelectionContainer {
          Text(shown, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
      }
    },
    confirmButton = {
      TextButton(onClick = done) { Text(ir.vil3ntec.tohid.core.i18n.StrShell.crashOk) }
    },
  )
}
