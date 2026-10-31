package ir.vil3ntec.tohid

import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.DeepLinks
import ir.vil3ntec.tohid.core.nav.Move
import ir.vil3ntec.tohid.ui.v3.V3Transitions
import ir.vil3ntec.tohid.ui.v3.modes.ModeChooser
import ir.vil3ntec.tohid.ui.v3.modes.ModeState
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
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

  /** برنامهٔ باز (singleTask) با لمسِ اعلانِ سفارش، یا پیوندِ `vill3n://app/…` (فقط با پرچمِ `UiV3`) */
  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    openNotice(intent)
    handleLink(intent)
  }

  private fun openNotice(intent: android.content.Intent?) {
    val orderId = runCatching { ir.vil3ntec.tohid.data.OrderNotify.fromIntent(applicationContext, intent) }.getOrNull()
    if (orderId != null) {
      ir.vil3ntec.tohid.ui.OrdersLive.openFromNotice(orderId)
      //  رسید همان دم (اگر نت هست)؛ وگرنه در صف تا دورِ بعدیِ OrdersLive
      lifecycleScope.launch { runCatching { ir.vil3ntec.tohid.data.OrderNotify.flush(applicationContext) } }
    }
  }

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

    //  M6: برنامه با لمسِ اعلانِ سفارش باز شد ⇒ رسید در صف و همان سفارش
    openNotice(intent)

    // کلیدِ انیمیشن پیش از اولین کشیدنِ صفحه خوانده می‌شود
    Motion.load(applicationContext)
    LocaleChoice.load(applicationContext)
    ShopDayChoice.load(applicationContext)
    ir.vil3ntec.tohid.ui.theme.ThemeStore.load(applicationContext)
    ModeState.load(applicationContext)
    ir.vil3ntec.tohid.ui.UiV3.load(applicationContext)
    //  پیوندِ vill3n://app فقط با پرچمِ روشن؛ با هر بار عوض شدنِ پرچم (تنظیمات) همان دم
    lifecycleScope.launch { snapshotFlow { ir.vil3ntec.tohid.ui.UiV3.on }.collect { syncLinkAlias(it) } }

    /*
     *  ── Sync v1: کارِ پس‌زمینه و سوکتِ زنده ──────────────────────────
     *  `schedule` کارِ دورهٔ پانزده‌دقیقه‌ای را ثبت می‌کند (از قبل ثبت
     *  شده باشد، `KEEP` دست نمی‌زند). سوکت فقط «چیزی عوض شد» را
     *  می‌آورد و خودِ Pull کارِ همان Worker است.
     *
     *  ⚠️ هیچ‌کدام خطا بیرون نمی‌دهند: برنامه باید بی‌اینترنت و بی‌حساب
     *  هم کامل بالا بیاید.
     */
    //  حالتِ پیک (DLV-01): دفترِ دکانی در کار نیست — نه کارِ دوره‌ای، نه سوکت
    ir.vil3ntec.tohid.data.ShopRole.courierMode.value = ir.vil3ntec.tohid.data.ShopRole.isCourier(applicationContext)
    if (ir.vil3ntec.tohid.data.ShopRole.courierMode.value) {
      runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Worker.cancel(applicationContext) }
    } else runCatching {
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
      //  نقشِ نشست: «پیک» ⇒ حالتِ پیک به‌جای دکان — زنده، از هر راهی که نقش عوض شود
      val courier by ir.vil3ntec.tohid.data.ShopRole.courierMode.collectAsState()
      LaunchedEffect(courier) {
        runCatching {
          if (courier) ir.vil3ntec.tohid.sync.v1.SyncV1Worker.cancel(applicationContext)
          else ir.vil3ntec.tohid.sync.v1.SyncV1Worker.schedule(applicationContext)   // KEEP: بی اثرِ دوم
        }
      }

      //  پرچمِ رابطِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز، بی انتخابگرِ حالت (`UiV3`)
      if (!ir.vil3ntec.tohid.ui.UiV3.on) {
        TohidTheme(theme) {
          ir.vil3ntec.tohid.ui.ProvideLang {
            if (!welcomed) {
              WelcomeScreen(store) {
                prefs.edit().putBoolean("welcomed", true).apply()
                welcomed = true
              }
            } else if (courier) {
              ir.vil3ntec.tohid.ui.courier.CourierHome(onSignedOut = {
                //  خروج از حسابِ پیک ⇒ صفحهٔ ورود؛ دفترِ دکانِ این گوشی دست نخورده است
                prefs.edit().putBoolean("welcomed", false).apply()
                welcomed = false
              })
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
        return@setContent
      }

      //  حالتِ برنامه (دکان / پیک) — انتخابِ کاربر؛ نصبِ پیشین همان رفتارِ امروز
      val chosen = ModeState.chosen.value
      ModeState.onCourierSignedOut = {
        //  خروج از حسابِ پیک ⇒ صفحهٔ ورود؛ دفترِ دکانِ این گوشی دست نخورده است
        prefs.edit().putBoolean("welcomed", false).apply()
        welcomed = false
      }
      ModeState.onStartShop = {
        prefs.edit().putBoolean("welcomed", false).apply()
        welcomed = false
      }
      val mode = remember(chosen, courier, welcomed) { ModeState.current(applicationContext) }
      val screen = when {
        !welcomed && chosen == null -> "choose"
        !welcomed -> "welcome"
        else -> mode.id
      }

      TohidTheme(theme) {
        // زبانِ صفحه و جهتش: فارسی و پشتو راست‌به‌چپ (پیش‌فرض، مثلِ پیش)، انگلیسی چپ‌به‌راست
        ir.vil3ntec.tohid.ui.ProvideLang {
          //  عوض شدنِ حالت (سوییچرِ «حساب من»، پیوند) با محو و بزرگ‌شدنِ نرم
          AnimatedContent(
            targetState = screen,
            transitionSpec = { V3Transitions.page(Move.NONE, ir.vil3ntec.tohid.ui.directionOf(LocaleChoice.lang)) },
            label = "mode",
          ) { s ->
            when (s) {
              "choose" -> ModeChooser { picked -> ModeState.choose(applicationContext, picked) }
              "welcome" -> WelcomeScreen(store) {
                prefs.edit().putBoolean("welcomed", true).apply()
                welcomed = true
              }
              AppMode.COURIER.id -> ir.vil3ntec.tohid.ui.courier.CourierHome(onSignedOut = { ModeState.signedOutOfCourier() })
              else -> AppRoot(
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
    handleLink(intent)
  }

  /**
   *  `AppLinkAlias` ِ مانیفست (intent-filter ِ `vill3n://app`) فقط وقتی روشن است که رابطِ نسخهٔ ۳ روشن
   *  است. خاموش ⇒ همان حالتِ مانیفست (`enabled="false"`)، یعنی مثلِ main هیچ پیوندِ بیرونی برنامه را
   *  باز نمی‌کند. فقط وقتی حالت فرق دارد چیزی نوشته می‌شود؛ خطا (مثلاً سیاستِ دستگاه) بی‌صدا.
   */
  private fun syncLinkAlias(on: Boolean) {
    runCatching {
      val alias = android.content.ComponentName(this, "ir.vil3ntec.tohid.AppLinkAlias")
      val want = if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
      if (packageManager.getComponentEnabledSetting(alias) != want) {
        packageManager.setComponentEnabledSetting(alias, want, android.content.pm.PackageManager.DONT_KILL_APP)
      }
    }
  }

  /**
   *  پیوندِ بیرونی (`vill3n://app/…`) ⇐ حالتِ همان پیوند و صفحهٔ دقیقش
   *  (`DeepLinks`)؛ پوستهٔ همان حالت پیوند را پس از دروازه‌ها (قفل، ورود) باز
   *  می‌کند. پیوندِ ویترین و QRِ دکان مالِ برنامهٔ خرید است و در مرورگر می‌ماند.
   */
  private fun handleLink(intent: android.content.Intent?) {
    //  رابطِ امروز (پرچمِ نسخهٔ ۳ خاموش) پیوندِ درونی نداشت — همان رفتار
    if (!ir.vil3ntec.tohid.ui.UiV3.on) return
    val link = DeepLinks.parse(intent?.dataString) ?: return
    ModeState.pendingLink.value = link
    if (link.mode in ModeState.available(applicationContext) && ModeState.chosen.value != link.mode) {
      ModeState.choose(applicationContext, link.mode)
    }
  }

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
  }

  override fun onPause() {
    ir.vil3ntec.tohid.data.ShopChatSync.foreground = false
    super.onPause()
  }
}
