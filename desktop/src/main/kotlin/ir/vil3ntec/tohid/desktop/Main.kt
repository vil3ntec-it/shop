package ir.vil3ntec.tohid.desktop

import android.app.DesktopNotify
import androidx.activity.compose.BackDispatcher
import androidx.activity.result.contract.DesktopFiles
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.MenuBar
import ir.vil3ntec.tohid.scan.scanChar
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.window.DialogWindow
import ir.vil3ntec.tohid.R
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.LiveSocket
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import ir.vil3ntec.tohid.ui.AppRoot
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.WelcomeScreen
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.Frame
import java.awt.event.MouseEvent

/**
 *  برنامهٔ کامپیوترِ فروشگاه — همان کارِ `MainActivity`ِ اندروید.
 *
 *  آن‌چه این‌جا تازه است مالِ خودِ کامپیوتر است:
 *
 *  - **یک نمونه**: دو پنجرهٔ هم‌زمان یعنی دو نویسنده روی یک دفتر؛ اجرای
 *    دوم فقط پنجرهٔ اولی را جلو می‌آورد (`SingleInstance`).
 *  - **سینیِ سیستم**: بستنِ پنجره برنامه را نمی‌بندد — همگام‌سازی، دیدبانِ
 *    موجودی و پشتیبانِ خودکار مثلِ گوشی پشتِ صحنه می‌مانند و اعلان‌ها از
 *    همان سینی می‌آیند. «خروج» در منوی سینی است.
 *  - **Esc** همان «برگشت»ِ گوشی است، و دکمهٔ «عقب»ِ ماوس هم.
 *  - **Ctrl + / − / 0** بزرگ‌نمایی (روی مانیتورِ بزرگِ پشتِ دخل).
 */
fun main() {
  if (!SingleInstance.acquire()) return
  DevShot.start()

  val context = DesktopContext
  val prefs = context.getSharedPreferences("tohid", 0)
  val store = ShopStore(context)
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  scope.launch { store.loadAndSummarize() }

  runCatching { ir.vil3ntec.tohid.sync.SavedLogins.purgeTokens(context) }
  //  HW-09/HW-10: ترازوی سریال و پنجرهٔ دومِ مشتری فقط این‌جا هستند
  ir.vil3ntec.tohid.hw.HwPlatform.serial = DeskSerial
  ir.vil3ntec.tohid.hw.CustomerFacing.supported = true
  Motion.load(context)
  ir.vil3ntec.tohid.LocaleChoice.load(context)

  var live: LiveSocket? = null
  runCatching {
    SyncV1Worker.schedule(context)
    live = LiveSocket(context) { SyncV1Worker.now(context) }
    live?.connect()
  }

  application(exitProcessOnExit = true) {
    var visible by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(prefs.getFloat("desktop_zoom", 1f).coerceIn(0.7f, 1.8f)) }
    fun setZoom(z: Float) { zoom = z.coerceIn(0.7f, 1.8f); prefs.edit().putFloat("desktop_zoom", zoom).apply() }

    val windowState = rememberWindowState(
      placement = if (prefs.getBoolean("desktop_max", false)) WindowPlacement.Maximized else WindowPlacement.Floating,
      position = WindowPosition.PlatformDefault,
      size = DpSize(prefs.getInt("desktop_w", 1280).dp, prefs.getInt("desktop_h", 820).dp),
    )

    val tray = rememberTrayState()
    val trayOk = remember { java.awt.SystemTray.isSupported() }
    val icon = painterResource(R.drawable.logo_mark)

    fun quit() {
      prefs.edit()
        .putInt("desktop_w", windowState.size.width.value.toInt())
        .putInt("desktop_h", windowState.size.height.value.toInt())
        .putBoolean("desktop_max", windowState.placement == WindowPlacement.Maximized)
        .commit()
      runCatching { live?.disconnect() }
      //  نسخهٔ محلیِ پایانِ روز (OPS-02) — فقط اگر دفتر از نسخهٔ نیم‌ساعتهٔ آخر
      //  عوض شده؛ یک مقایسه و یک رونوشت، چند ده میلی‌ثانیه
      runCatching { ir.vil3ntec.tohid.data.AutoBackup.take(context, ir.vil3ntec.tohid.todayIso()) }
      //  پشتیبانِ کامل هنگامِ بستن — پایانِ روزِ کاری بی پشتیبان نمی‌ماند
      //  (دفتر و عکس‌ها، چند صد میلی‌ثانیه)
      if (ir.vil3ntec.tohid.data.BackupMirror.enabled(context)) runCatching { ir.vil3ntec.tohid.data.BackupMirror.write(context) }
      exitApplication()
    }

    if (trayOk) {
      Tray(
        icon = icon,
        state = tray,
        tooltip = "فروشگاه من",
        onAction = { visible = true; SingleInstance.front() },
        menu = {
          Item("باز کردنِ فروشگاه من", onClick = { visible = true; SingleInstance.front() })
          Separator()
          Item("خروج", onClick = { quit() })
        },
      )
      LaunchedEffect(Unit) {
        DesktopNotify.sink = { title, text -> tray.sendNotification(androidx.compose.ui.window.Notification(title, text)) }
      }
    }

    //  HW-10: نمایشگرِ مشتری — پنجرهٔ دوم که روی صفحهٔ دوم/نمایشگرِ رو به مشتری کشیده می‌شود
    val customerOpen by ir.vil3ntec.tohid.hw.CustomerFacing.open
    if (customerOpen) {
      Window(
        onCloseRequest = { ir.vil3ntec.tohid.hw.CustomerFacing.open.value = false },
        title = "نمایشگرِ مشتری — فروشگاه من",
        icon = icon,
        state = rememberWindowState(size = DpSize(800.dp, 600.dp)),
      ) {
        val theme = remember {
          runCatching { ThemeChoice.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeChoice.SYSTEM)
        }
        //  زبان و جهت همان برنامه (ProvideLang): فارسی و پشتو راست‌به‌چپ، انگلیسی چپ‌به‌راست
        CompositionLocalProvider(LocalContext provides context) {
          ir.vil3ntec.tohid.ui.ProvideLang {
            TohidTheme(theme) { ir.vil3ntec.tohid.ui.screens.CustomerDisplayScreen(prefs.getString("store_name", "") ?: "") }
          }
        }
      }
    }

    var shortcutsOpen by ir.vil3ntec.tohid.ui.AppNav.help
    val windowHid = remember { ir.vil3ntec.tohid.scan.HidScan() }
    Window(
      onCloseRequest = {
        //  بی سینی (بعضی میزکارهای لینوکس) بستن یعنی خروج
        if (trayOk) {
          visible = false
          if (!prefs.getBoolean("desktop_tray_told", false)) {
            prefs.edit().putBoolean("desktop_tray_told", true).apply()
            DesktopNotify.show("فروشگاه من هنوز باز است", "همگام‌سازی و هشدارها پشتِ صحنه می‌مانند. برای خروج: سینیِ سیستم ⇒ خروج")
          }
        } else quit()
      },
      visible = visible,
      state = windowState,
      title = "فروشگاه من",
      icon = icon,
      /*
       *  بارکدخوانِ USB/بلوتوث وقتی هیچ کادرِ نوشتنی فوکوس ندارد: نویسه‌های
       *  تندِ پشتِ هم + Enter ⇐ `ScanRouter` ⇐ همان صفحه‌ای که باز است (صندوق
       *  سبد، کالاها ویرایش/کالای تازه، انبار ورود). کادرِ فوکوس‌دار نویسه را
       *  خودش برمی‌دارد و این‌جا نمی‌رسد — پس تایپِ آدم دست نمی‌خورد.
       */
      onKeyEvent = { e ->
        if (e.type != KeyEventType.KeyDown) return@Window false
        val now = System.currentTimeMillis()
        if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
          val code = windowHid.submit(now) ?: return@Window false
          ir.vil3ntec.tohid.scan.ScanRouter.dispatch(code) { ir.vil3ntec.tohid.ui.AppNav.go("sale") }
          true
        } else {
          //  F0-06: با کلیدِ فیزیکی — چیدمانِ فارسی/پشتو «۶» و «ش» می‌سازد
          val ch = e.scanChar()
          if (ch != null && !e.isCtrlPressed && !e.isAltPressed) { windowHid.feed(ch, now); true } else false
        }
      },
      onPreviewKeyEvent = { e ->
        if (e.type != KeyEventType.KeyDown) return@Window false
        val mod = e.isCtrlPressed || e.isMetaPressed
        //  Ctrl+۱ تا ۹ ⇒ صفحه‌ها (`AppNav.SHORTCUTS`)؛ F11 ⇒ تمام‌صفحه
        val digit = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
          .indexOf(e.key)
        when {
          e.key == Key.F1 -> { shortcutsOpen = true; true }
          e.key == Key.F11 -> {
            windowState.placement =
              if (windowState.placement == WindowPlacement.Fullscreen) {
                if (prefs.getBoolean("desktop_max", false)) WindowPlacement.Maximized else WindowPlacement.Floating
              } else WindowPlacement.Fullscreen
            true
          }
          e.key == Key.Escape && windowState.placement == WindowPlacement.Fullscreen && !BackDispatcher.hasHandler() -> {
            windowState.placement = WindowPlacement.Floating
            true
          }
          mod && digit >= 0 && digit < ir.vil3ntec.tohid.ui.AppNav.SHORTCUTS.size -> {
            ir.vil3ntec.tohid.ui.AppNav.go(ir.vil3ntec.tohid.ui.AppNav.SHORTCUTS[digit].first)
            true
          }
          //  Ctrl+M ⇒ مرکزِ مدیریت (Ctrl+0 برای اندازهٔ متن است)
          //  Ctrl+K ⇒ جست‌وجوی سریع (صفحه، کالا، بارکد، قرض‌دار)
          mod && e.key == Key.K -> { ir.vil3ntec.tohid.ui.AppNav.palette.value = !ir.vil3ntec.tohid.ui.AppNav.palette.value; true }
          mod && e.key == Key.M -> { ir.vil3ntec.tohid.ui.AppNav.go("admin"); true }
          //  Ctrl+N ⇒ «تازه»ِ همین صفحه (قرض‌دار، کالا، مصرف، تأمین‌کننده)
          mod && e.key == Key.N -> ir.vil3ntec.tohid.ui.AppNav.addAction.value?.let { it(); true } ?: false
          e.key == Key.Escape -> BackDispatcher.dispatch()
          mod && (e.key == Key.Equals || e.key == Key.Plus || e.key == Key.NumPadAdd) -> { setZoom(zoom + 0.1f); true }
          mod && (e.key == Key.Minus || e.key == Key.NumPadSubtract) -> { setZoom(zoom - 0.1f); true }
          mod && (e.key == Key.Zero || e.key == Key.NumPad0) -> { setZoom(1f); true }
          else -> false
        }
      },
    ) {
      /*
       *  نوارِ منوی سیستم (فایل · برو · نما · راهنما) برداشته شد: خواستهٔ صاحب
       *  مخزن بود و ظاهرِ کهنهٔ ویندوزِ قدیم داشت. همان کارها حالا در سربرگِ
       *  برنامه (`DeskHeader`: جست‌وجو، هشدارها، تنظیمات، فروشِ تازه)، منوی کناری
       *  و میان‌برها هستند؛ F1 فهرستِ میان‌برها را باز می‌کند.
       */
      if (shortcutsOpen) {
        DialogWindow(onCloseRequest = { shortcutsOpen = false }, title = "میان‌برهای صفحه‌کلید",
          state = androidx.compose.ui.window.rememberDialogState(size = DpSize(560.dp, 680.dp))) {
          //  پنجرهٔ جدا بیرونِ TohidThemeِ برنامه است ⇐ همان ظاهرِ ذخیره‌شده (روز/شب) را خودش می‌گیرد
          val dialogTheme = remember {
            runCatching { ThemeChoice.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeChoice.SYSTEM)
          }
          TohidTheme(dialogTheme) { ShortcutsHelp() }
        }
      }
      LaunchedEffect(Unit) {
        //  منوی سیستم راست‌به‌چپ، مثلِ خودِ برنامه
        window.jMenuBar?.applyComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT)
        window.minimumSize = java.awt.Dimension(420, 560)
        DesktopFiles.owner = window
        SingleInstance.onActivate = {
          java.awt.EventQueue.invokeLater {
            visible = true
            window.isVisible = true
            if (window.extendedState and Frame.ICONIFIED != 0) window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
            window.toFront(); window.requestFocus()
          }
        }
        //  دکمهٔ «عقب»ِ ماوس (دکمهٔ ۴) = برگشت
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener({ ev ->
          val m = ev as? MouseEvent ?: return@addAWTEventListener
          if (m.id == MouseEvent.MOUSE_RELEASED && m.button == 4) java.awt.EventQueue.invokeLater { BackDispatcher.dispatch() }
        }, java.awt.AWTEvent.MOUSE_EVENT_MASK)
      }

      val base = LocalDensity.current
      val density = Density(base.density * zoom, base.fontScale)
      val w = (windowState.size.width.value / zoom).toInt().coerceAtLeast(320)
      val h = (windowState.size.height.value / zoom).toInt().coerceAtLeast(320)

      CompositionLocalProvider(
        LocalContext provides context,
        androidx.compose.ui.platform.LocalView provides remember { android.view.View(context) },
        LocalDensity provides density,
        LocalConfiguration provides Configuration(w, h),
      ) {
        var theme by remember {
          mutableStateOf(
            runCatching { ThemeChoice.valueOf(prefs.getString("theme", "SYSTEM")!!) }
              .getOrDefault(ThemeChoice.SYSTEM)
          )
        }
        //  عکس‌های خودکار در ساختِ dev ⇐ صفحهٔ خوش‌آمد رد می‌شود (همان «ادامه بدون حساب»)
        val shotRun = ir.vil3ntec.tohid.BuildConfig.DEBUG && System.getProperty("tohid.shot.script") != null
        var welcomed by remember { mutableStateOf(shotRun || prefs.getBoolean("welcomed", false)) }
        //  نوارِ منو ⇐ نما ⇐ ظاهر
        val themeRequest = ir.vil3ntec.tohid.ui.AppNav.theme.value
        LaunchedEffect(themeRequest) {
          val picked = themeRequest?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: return@LaunchedEffect
          theme = picked
          prefs.edit().putString("theme", picked.name).apply()
          ir.vil3ntec.tohid.ui.AppNav.theme.value = null
        }

        TohidTheme(theme) {
          //  زبان و جهت: فارسی و پشتو راست‌به‌چپ، انگلیسی چپ‌به‌راست
          ir.vil3ntec.tohid.ui.ProvideLang {
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
  }
}


/** پنجرهٔ «میان‌برهای صفحه‌کلید» — همان چیزهایی که `onPreviewKeyEvent` می‌گیرد */
@androidx.compose.runtime.Composable
private fun ShortcutsHelp() {
  //  همهٔ میان‌برها، بخش‌به‌بخش — هر کلیدی که در برنامه کار می‌کند همین‌جا نوشته شده
  val pages = ir.vil3ntec.tohid.ui.AppNav.SHORTCUTS.mapIndexed { i, (_, label) -> "Ctrl+${i + 1}" to label }
  val groups: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "همه‌جا" to pages + listOf(
      "F1" to "همین فهرستِ میان‌برها",
      "Ctrl+K" to "جست‌وجوی سریع: صفحه، کالا، بارکد، مشتری",
      "Ctrl+M" to "مرکزِ مدیریت",
      "Ctrl+N" to "موردِ تازه در همین صفحه",
      "F11" to "تمام‌صفحه",
      "Ctrl+= / Ctrl+− / Ctrl+0" to "بزرگ‌نمایی / کوچک‌نمایی / عادی",
      "Esc" to "بستنِ پنجره یا برگشت",
      "دکمهٔ عقبِ ماوس" to "برگشت",
      "کلیکِ راست روی ردیف" to "منوی کارها",
    ),
    "بارکدخوان" to listOf(
      "صندوق" to "کالا به سبد",
      "کالاها" to "ثبت‌شده: ویرایش — تازه: ردیفِ «کالای تازه»",
      "انبار و خرید" to "ثبت‌شده: ورودِ انبار — تازه: فرمِ کالای تازه",
      "صفحه‌های دیگر" to "صندوق باز می‌شود و کالا به سبد می‌رود",
    ),
    "صندوقِ فروش" to listOf(
      "Enter" to "افزودنِ کالا (بارکد یا نام)",
      "↑ / ↓" to "رفتن میانِ ردیف‌های سبد",
      "3*" to "تعدادِ کالای بعدی (مثلاً 3*بارکد)",
      "+ / −" to "یکی بیشتر / کمتر",
      "Delete" to "حذفِ ردیفِ انتخاب‌شده",
      "F2" to "انتخابِ مشتری",
      "F3" to "ویرایشِ ردیف (قیمت، تخفیف)",
      "F4" to "ردیفِ آزاد بی‌کالا",
      "F8" to "چاپِ آخرین فاکتور",
      "F9 / F12 / Ctrl+Enter" to "ثبتِ فروش",
      "Esc" to "خالی کردنِ کادر",
    ),
    "کالاها" to listOf(
      "Ctrl+F" to "جست‌وجو در جدول",
      "Tab / Enter" to "رفتن به کادرِ بعدیِ «کالای تازه» و ثبت",
      "Ctrl+S" to "ذخیرهٔ ویرایش",
      "Esc" to "بستنِ پنلِ ویرایش",
    ),
    "مشتری‌ها و قرض" to listOf(
      "F7" to "دریافت از مشتری",
      "F6" to "دادن / قرضِ تازه",
      "Enter" to "ثبتِ ردیف",
      "Ctrl+P" to "چاپِ صورت‌حساب",
      "Ctrl+E" to "خروجیِ اکسل",
      "Esc" to "بستن",
    ),
  )
  ir.vil3ntec.tohid.ui.ProvideLang {
    androidx.compose.foundation.layout.Column(
      //  رنگ‌ها از تم (F0-18): در حالتِ تاریک پنجره دیگر سفید نمی‌ماند
      androidx.compose.ui.Modifier.fillMaxSize().background(Shop.colors.surfaceSolid)
        .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp)
    ) {
      //  OPS-06: راهنمای همین صفحه — فقط وقتی متنش آماده است (امروز هیچ صفحه‌ای؛ جای خالی دیده نمی‌شود)
      val guide = ir.vil3ntec.tohid.core.guide.ScreenGuides.forScreen(ir.vil3ntec.tohid.ui.AppNav.screen.value)
      if (guide != null) {
        val lang = ir.vil3ntec.tohid.LocaleChoice.lang.code
        androidx.compose.material3.Text(guide.title.of(lang), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
          color = Shop.colors.primary, modifier = androidx.compose.ui.Modifier.padding(bottom = 4.dp))
        guide.steps.forEachIndexed { i, step ->
          androidx.compose.material3.Text("${i + 1}. ${step.of(lang)}", color = Shop.colors.text,
            modifier = androidx.compose.ui.Modifier.padding(vertical = 2.dp))
        }
      }
      groups.forEach { (title, rows) ->
        androidx.compose.material3.Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
          color = Shop.colors.primary, modifier = androidx.compose.ui.Modifier.padding(top = 10.dp, bottom = 4.dp))
        rows.forEach { (k, v) ->
          androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            androidx.compose.material3.Text(v, color = Shop.colors.text, modifier = androidx.compose.ui.Modifier.weight(1f))
            androidx.compose.material3.Text(k, color = Shop.colors.text,
              modifier = androidx.compose.ui.Modifier.background(Shop.colors.primaryTint).padding(horizontal = 6.dp, vertical = 1.dp))
          }
        }
      }
    }
  }
}
