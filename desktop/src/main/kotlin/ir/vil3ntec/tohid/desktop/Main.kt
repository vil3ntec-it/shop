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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.MenuBar
import androidx.compose.foundation.background
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
  Motion.load(context)

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

    var shortcutsOpen by remember { mutableStateOf(false) }
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
      onPreviewKeyEvent = { e ->
        if (e.type != KeyEventType.KeyDown) return@Window false
        val mod = e.isCtrlPressed || e.isMetaPressed
        //  Ctrl+۱ تا ۹ ⇒ صفحه‌ها (`AppNav.SHORTCUTS`)؛ F11 ⇒ تمام‌صفحه
        val digit = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
          .indexOf(e.key)
        when {
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
       *  نوارِ منوی کامپیوتر — منوی سیستمِ خودِ ویندوز/مک/لینوکس.
       *
       *  همهٔ کارهای برنامه از این‌جا هم پیدا می‌شوند، با میان‌برشان کنارِ
       *  نام: کاربرِ کامپیوتر اول بالای پنجره را می‌گردد، نه دکمه‌های صفحه.
       *  ⚠️ میان‌برها این‌جا «ثبت» نمی‌شوند (فقط نوشته می‌شوند): همان
       *  `onPreviewKeyEvent` بالا آن‌ها را می‌گیرد، وگرنه هر کلید دو بار
       *  کار می‌کرد.
       */
      MenuBar {
        Menu("فایل", mnemonic = 'F') {
          Item("فروشِ تازه    Ctrl+2", onClick = { ir.vil3ntec.tohid.ui.AppNav.go("sale") })
          Item("موردِ تازه در همین صفحه    Ctrl+N", onClick = { ir.vil3ntec.tohid.ui.AppNav.addAction.value?.invoke() })
          Item("جست‌وجو…    Ctrl+K", onClick = { ir.vil3ntec.tohid.ui.AppNav.palette.value = true })
          Separator()
          Item("تنظیمات و پشتیبان‌گیری", onClick = { ir.vil3ntec.tohid.ui.AppNav.go("settings") })
          Item("دستگاه‌های جانبی (چاپگر، بارکدخوان)", onClick = { ir.vil3ntec.tohid.ui.AppNav.go("devices") })
          Item("پشتیبانِ کامل همین حالا (پوشه)", onClick = {
            val r = ir.vil3ntec.tohid.data.BackupMirror.write(context)
            DesktopNotify.show("پشتیبان", r.fold({ "نشست: " + it.path }, { "نشد: " + (it.message ?: "") }))
          })
          Item("باز کردنِ پوشهٔ پشتیبان", onClick = {
            ir.vil3ntec.tohid.data.BackupMirror.folder(context)?.let { ir.vil3ntec.tohid.ui.Platform.openFolder(it.path) }
          })
          Separator()
          Item("خروج از برنامه", onClick = { quit() })
        }
        Menu("برو", mnemonic = 'G') {
          ir.vil3ntec.tohid.ui.AppNav.SHORTCUTS.forEachIndexed { i, (id, label) ->
            Item("$label    Ctrl+${i + 1}", onClick = { ir.vil3ntec.tohid.ui.AppNav.go(id) })
          }
          Separator()
          Item("مرکزِ مدیریت    Ctrl+M", onClick = { ir.vil3ntec.tohid.ui.AppNav.go("admin") })
          Item("رسیدها", onClick = { ir.vil3ntec.tohid.ui.AppNav.go("receipts") })
          Item("کارمندان", onClick = { ir.vil3ntec.tohid.ui.AppNav.go("team") })
          Item("سابقهٔ عملیات", onClick = { ir.vil3ntec.tohid.ui.AppNav.go("audit") })
        }
        Menu("نما", mnemonic = 'V') {
          Item("تمام‌صفحه    F11", onClick = {
            windowState.placement =
              if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
          })
          Separator()
          Item("بزرگ‌نمایی    Ctrl+=", onClick = { setZoom(zoom + 0.1f) })
          Item("کوچک‌نمایی    Ctrl+−", onClick = { setZoom(zoom - 0.1f) })
          Item("اندازهٔ عادی    Ctrl+0", onClick = { setZoom(1f) })
          Separator()
          Item("منوی کناری: ثابت", onClick = { ir.vil3ntec.tohid.ui.AppNav.rail.value = "full" })
          Item("منوی کناری: فقط آیکن", onClick = { ir.vil3ntec.tohid.ui.AppNav.rail.value = "compact" })
          Item("منوی کناری: پنهان", onClick = { ir.vil3ntec.tohid.ui.AppNav.rail.value = "hidden" })
          Separator()
          Item("ظاهرِ روشن", onClick = { ir.vil3ntec.tohid.ui.AppNav.theme.value = "LIGHT" })
          Item("ظاهرِ تاریک", onClick = { ir.vil3ntec.tohid.ui.AppNav.theme.value = "DARK" })
          Item("ظاهرِ سیستم", onClick = { ir.vil3ntec.tohid.ui.AppNav.theme.value = "SYSTEM" })
        }
        Menu("راهنما", mnemonic = 'H') {
          Item("میان‌برهای صفحه‌کلید", onClick = { shortcutsOpen = true })
          Item("دربارهٔ فروشگاه من", onClick = {
            javax.swing.JOptionPane.showMessageDialog(window,
              "فروشگاه من\nنسخهٔ ${ir.vil3ntec.tohid.BuildConfig.VERSION_NAME}",
              "دربارهٔ فروشگاه من", javax.swing.JOptionPane.INFORMATION_MESSAGE)
          })
        }
      }
      if (shortcutsOpen) {
        DialogWindow(onCloseRequest = { shortcutsOpen = false }, title = "میان‌برهای صفحه‌کلید",
          state = androidx.compose.ui.window.rememberDialogState(size = DpSize(460.dp, 520.dp))) {
          ShortcutsHelp()
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
        var welcomed by remember { mutableStateOf(prefs.getBoolean("welcomed", false)) }
        //  نوارِ منو ⇐ نما ⇐ ظاهر
        val themeRequest = ir.vil3ntec.tohid.ui.AppNav.theme.value
        LaunchedEffect(themeRequest) {
          val picked = themeRequest?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: return@LaunchedEffect
          theme = picked
          prefs.edit().putString("theme", picked.name).apply()
          ir.vil3ntec.tohid.ui.AppNav.theme.value = null
        }

        TohidTheme(theme) {
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
  }
}


/** پنجرهٔ «میان‌برهای صفحه‌کلید» — همان چیزهایی که `onPreviewKeyEvent` می‌گیرد */
@androidx.compose.runtime.Composable
private fun ShortcutsHelp() {
  val rows = ir.vil3ntec.tohid.ui.AppNav.SHORTCUTS.mapIndexed { i, (_, label) -> "Ctrl+${i + 1}" to label } + listOf(
    "Ctrl+M" to "مرکزِ مدیریت",
    "Ctrl+N" to "موردِ تازه در همین صفحه (کالا، قرض‌دار، مصرف…)",
    "Ctrl+K" to "جست‌وجوی سریع: صفحه، کالا، بارکد، قرض‌دار",
    "F11" to "تمام‌صفحه",
    "Ctrl+= / Ctrl+− / Ctrl+0" to "بزرگ‌نمایی / کوچک‌نمایی / عادی",
    "Esc" to "بستنِ پنجره یا برگشت",
    "دکمهٔ عقبِ ماوس" to "برگشت",
    "کلیکِ راست روی ردیف" to "منوی کارها",
  )
  androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
    androidx.compose.foundation.layout.Column(
      androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White).padding(16.dp)
    ) {
      rows.forEach { (k, v) ->
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
          androidx.compose.material3.Text(v, modifier = androidx.compose.ui.Modifier.weight(1f))
          androidx.compose.material3.Text(k, color = androidx.compose.ui.graphics.Color(0xFF475569))
        }
      }
    }
  }
}
