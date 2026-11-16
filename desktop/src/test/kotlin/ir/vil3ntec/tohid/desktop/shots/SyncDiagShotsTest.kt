package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.sync.SyncOutage
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.v1.SyncDot
import ir.vil3ntec.tohid.sync.v1.SyncStatus
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.ServerPulse
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.settings.sections.SettingsShotState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عاملِ e-syncdiag — عکس‌های صفحهٔ همگام‌سازی (گوشیِ ۳۶۰×۷۶۰dp، چگالیِ ۲، فارسی، روز و شب) در حالت‌های شکستِ «سرور در
 *  دسترس» و حالتِ سالم: همان حالِ موتور (`SyncV1Engine.status`)، `AutoSync` و `ServerPulse` که صفحه می‌خواند. فقط با
 *  `SD_SHOTS=<پوشه>`.
 */
@OptIn(ExperimentalTestApi::class)
class SyncDiagShotsTest {

  private val out: File? = System.getenv("SD_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private fun frame(file: String, dark: Boolean, heightDp: Int = 760) =
    runDesktopComposeUiTest(720, heightDp * 2, testTimeout = 3.minutes) {
      I18n.source = { Lang.FA }
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(2f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(360, heightDp),
          LocalDeskLayout provides false,
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
              Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
                val store = remember { RouteUiHarness.loadedStore(ShopData()) }
                V3RoutePreview("settings/data/sync", store, dark = dark)
              }
            }
          }
        }
      }
      repeat(24) {
        mainClock.advanceTimeBy(250)
        Thread.sleep(60)
      }
      ImageIO.write(captureToImage().toAwtImage(), "png", File(out!!.apply { mkdirs() }, file))
    }

  /** ویژگیِ `by mutableStateOf` با `private set` (`ServerPulse`، `AutoSync`) — فقط برای عکس */
  @Suppress("UNCHECKED_CAST")
  private fun <T> poke(owner: Any, name: String, value: T) {
    val f = owner.javaClass.getDeclaredField("$name\$delegate").apply { isAccessible = true }
    when (val s = f.get(owner)) {
      is androidx.compose.runtime.MutableLongState -> s.longValue = value as Long
      else -> (s as MutableState<T>).value = value
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun engineStatus(s: SyncStatus) {
    val engine = SyncV1Engine.of(DesktopContext)
    val f = SyncV1Engine::class.java.getDeclaredField("_status").apply { isAccessible = true }
    (f.get(engine) as MutableStateFlow<SyncStatus>).value = s
  }

  private val day = 86_400_000L

  private fun status(queued: Int, dropped: Int = 0, error: String = "", code: String = "", holding: Boolean = false,
                     outage: SyncOutage.Kind = SyncOutage.Kind.OK, http: Int? = null, stale: Boolean = error.isNotBlank() || holding) = SyncStatus(
    dot = if (error.isNotBlank()) SyncDot.RED else if (queued > 0) SyncDot.YELLOW else SyncDot.GREEN,
    queued = queued, dropped = dropped, cursor = 120, lastOkAt = System.currentTimeMillis() - if (stale) day else 0,
    lastError = error, holding = holding, upgradeAvailable = false, schemaVersion = 3, serverSchema = 2, live = true,
    outage = outage, errorCode = code, errorStatus = http,
  )

  private fun state(name: String, s: SyncStatus, autoError: String? = null) {
    engineStatus(s)
    poke(AutoSync, "lastError", autoError)
    poke(ServerPulse, "state", ServerPulse.State.UP)
    poke(ServerPulse, "at", System.currentTimeMillis())
    for (dark in listOf(false, true)) frame("$name-${if (dark) "dark" else "light"}.png", dark)
    //  همهٔ صفحه (بخشِ «سرور» و «دفترِ تغییرات» زیرِ تای ۷۶۰) — فقط روشن
    if (System.getenv("SD_FULL") != null) frame("$name-full-light.png", false, heightDp = 1500)
  }

  @Test
  fun shots() {
    Assume.assumeTrue("SD_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val reduced = Motion.reducedForTest
    Motion.reducedForTest = true
    Motion.load(DesktopContext)
    SettingsShotState.signedIn = true
    try {
      //  حالِ صاحب‌کار: موتور بی خطای خودش، `AutoSync` با پرتابِ `SyncV1Worker.now`، چهار تغییر در صف
      state("1-owner-case", status(4, stale = true), autoError = "Expedited jobs cannot be delayed")
      state("2-device-error", status(4, error = "Expected JsonObject, but had JsonArray", outage = SyncOutage.Kind.ERROR, http = SyncOutage.LOCAL))
      state("3-server-400", status(4, error = "schema_version لازم است", code = "bad_schema_version", outage = SyncOutage.Kind.ERROR, http = 400))
      state("4-server-behind", status(4, error = "سرور schema نسخهٔ 2 را می‌شناسد و برنامه نسخهٔ 3 می‌فرستد؛ سرور باید به‌روز شود", holding = true))
      SettingsShotState.syncRejected = mapOf("unknown_table" to 1)
      state("5-rejected", status(0, dropped = 1))
      SettingsShotState.syncRejected = null
      state("6-sign-in", status(4, error = "نشست شما تمام شد؛ دوباره وارد شوید", code = "session_expired", outage = SyncOutage.Kind.AUTH, http = 401))
      state("7-no-shop", status(4, error = "برای این حساب دکانی ثبت نشده است", code = "no_shop", outage = SyncOutage.Kind.AUTH, http = 403))
      state("9-server-404", status(4, error = "این مسیر وجود ندارد", code = "not_found", outage = SyncOutage.Kind.ERROR, http = 404))
      state("8-healthy", status(0))
    } finally {
      SettingsShotState.signedIn = null
      SettingsShotState.syncRejected = null
      poke(AutoSync, "lastError", null as String?)
      Motion.reducedForTest = reduced
      SyncV1Engine.of(DesktopContext).publish()
    }
  }
}
