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
      exitApplication()
    }

    if (trayOk) {
      Tray(
        icon = icon,
        state = tray,
        tooltip = "توحید",
        onAction = { visible = true; SingleInstance.front() },
        menu = {
          Item("باز کردنِ توحید", onClick = { visible = true; SingleInstance.front() })
          Separator()
          Item("خروج", onClick = { quit() })
        },
      )
      LaunchedEffect(Unit) {
        DesktopNotify.sink = { title, text -> tray.sendNotification(androidx.compose.ui.window.Notification(title, text)) }
      }
    }

    Window(
      onCloseRequest = {
        //  بی سینی (بعضی میزکارهای لینوکس) بستن یعنی خروج
        if (trayOk) {
          visible = false
          if (!prefs.getBoolean("desktop_tray_told", false)) {
            prefs.edit().putBoolean("desktop_tray_told", true).apply()
            DesktopNotify.show("توحید هنوز باز است", "همگام‌سازی و هشدارها پشتِ صحنه می‌مانند. برای خروج: سینیِ سیستم ⇒ خروج")
          }
        } else quit()
      },
      visible = visible,
      state = windowState,
      title = "توحید — دفترِ فروشگاه",
      icon = icon,
      onPreviewKeyEvent = { e ->
        if (e.type != KeyEventType.KeyDown) return@Window false
        val mod = e.isCtrlPressed || e.isMetaPressed
        when {
          e.key == Key.Escape -> BackDispatcher.dispatch()
          mod && (e.key == Key.Equals || e.key == Key.Plus || e.key == Key.NumPadAdd) -> { setZoom(zoom + 0.1f); true }
          mod && (e.key == Key.Minus || e.key == Key.NumPadSubtract) -> { setZoom(zoom - 0.1f); true }
          mod && (e.key == Key.Zero || e.key == Key.NumPad0) -> { setZoom(1f); true }
          else -> false
        }
      },
    ) {
      LaunchedEffect(Unit) {
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
