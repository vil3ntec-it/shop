package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.model.ShopDevicesDto
import ir.vil3ntec.tohid.core.model.StaffCodeDto
import ir.vil3ntec.tohid.data.PurchaseFxPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.desktop.TestBackend
import ir.vil3ntec.tohid.desktop.wireShop
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.AppRoot
import ir.vil3ntec.tohid.ui.screens.DevicesSection
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.account.team.MemberActions
import ir.vil3ntec.tohid.ui.v3.account.team.MemberContent
import ir.vil3ntec.tohid.ui.v3.account.team.TeamActions
import ir.vil3ntec.tohid.ui.v3.account.team.TeamContent
import ir.vil3ntec.tohid.ui.v3.account.team.TeamData
import ir.vil3ntec.tohid.ui.v3.account.team.TeamSession
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import kotlinx.serialization.json.Json
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های عاملِ e-devver برای صاحب‌کار — پیش (`DEVVER_TAG=before` روی `claude/e-pur11`) و پس (`after`، همین شاخه):
 *  فهرستِ دستگاه‌ها با نسخه (صفحهٔ کارمند و «دستگاه‌ها» ِ صفحهٔ کارمندان) و کلیدِ «خرید به ارزِ دیگر» پس از زدنِ «روشن» —
 *  گوشیِ ۳۶۰×۷۶۰dp (چگالیِ ۲، راست‌به‌چپ) روشن و تیره؛ کامپیوترِ ۱۳۶۶×۷۶۸ (پوستهٔ واقعیِ `AppRoot`). فقط با
 *  `DEVVER_SHOTS=<پوشه>` (آزمونِ سد نیست). ⚠️ هیچ نامِ تازه‌ای از این کار را مستقیم صدا نمی‌زند تا روی شاخهٔ پیشین هم
 *  کامپایل شود: نسخهٔ دستگاه از JSON ِ پاسخ (ساختِ پیشین کلید را نمی‌شناسد) و ساختِ «همین برنامه» با بازتاب.
 */
@OptIn(ExperimentalTestApi::class)
class DeviceVersionShotsTest {

  private val out: File? = System.getenv("DEVVER_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val tag: String = System.getenv("DEVVER_TAG")?.takeIf { it.isNotBlank() } ?: "after"
  private val json = Json { ignoreUnknownKeys = true }
  private val seen = System.currentTimeMillis() - 3 * 3_600_000L

  /** همان پاسخِ سرور؛ ساختِ پیشین `appVersion` را نمی‌خواند */
  private fun devices(vararg rows: String): List<ShopDeviceDto> =
    json.decodeFromString<ShopDevicesDto>("""{"devices":[${rows.joinToString(",")}]}""").devices

  private fun row(id: String, name: String, v: String?, user: String, who: String, role: String = "staff", current: Boolean = false) =
    """{"id":"$id","name":"$name","status":"active","lastSeenAt":$seen,"current":$current,""" +
      (if (v != null) """"appVersion":"$v",""" else "") +
      """"member":{"userId":"$user","role":"$role","name":"$who"}}"""

  /** گوشیِ صاحب (۳٫۲٫۶۱۵): یک گوشیِ کهنه، یک نامعلوم، یک به‌روز و کامپیوترِ دخل (خطِ دیگر) */
  private val phoneShop = arrayOf(
    row("me", "Pixel 8", "3.2.615", "u1", "کریم احمدی", "owner", current = true),
    row("d1", "Redmi 9", "3.2.600", "u2", "نوید", "manager"),
    row("d2", "Galaxy A52", null, "u2", "نوید", "manager"),
    row("d3", "Galaxy S24", "3.2.640", "u2", "نوید", "manager"),
    row("pc", "کامپیوترِ دخل", "1.0.366", "u3", "سمیع"),
  )

  /** کامپیوترِ صاحب (۱٫۰٫۳۶۶): کامپیوترِ انبارِ کهنه، دو گوشی (یکی پشتِ دیگری) و یک نامعلوم */
  private val deskShop = arrayOf(
    row("me", "کامپیوترِ دخل", "1.0.366", "u1", "کریم احمدی", "owner", current = true),
    row("pc2", "کامپیوترِ انبار", "1.0.360", "u3", "سمیع"),
    row("d1", "Redmi 9", "3.2.600", "u2", "نوید", "manager"),
    row("d3", "Galaxy S24", "3.2.615", "u2", "نوید", "manager"),
    row("d2", "Galaxy A52", null, "u2", "نوید", "manager"),
  )

  private fun team(list: List<ShopDeviceDto>): TeamData = TeamData().apply {
    loaded = true
    members = listOf(
      MemberDto(id = "m1", userId = "u1", name = "کریم احمدی", role = "owner"),
      MemberDto(id = "m2", userId = "u2", name = "نوید", role = "manager"),
      MemberDto(id = "m3", userId = "u3", name = "سمیع", role = "staff"),
    )
    maxMembers = 5
    codes = listOf(StaffCodeDto(id = "c1", hint = "AB", role = "staff", maxUses = 5, usedCount = 2))
    standing = "SHOP-7K2Q"
    devices = list
  }

  /** ساختِ «همین برنامه» برای صفحه‌ها — فقط روی شاخه‌ای که آن را دارد (`DeviceVersionModel.LocalSelf`) */
  @Composable
  private fun WithSelf(v: String, content: @Composable () -> Unit) {
    val local = remember {
      runCatching {
        val c = Class.forName("ir.vil3ntec.tohid.ui.v3.account.team.DeviceVersionModel")
        @Suppress("UNCHECKED_CAST")
        c.getMethod("getLocalSelf").invoke(c.getField("INSTANCE").get(null)) as ProvidableCompositionLocal<String>
      }.getOrNull()
    }
    if (local == null) content() else CompositionLocalProvider(local provides v) { content() }
  }

  private fun DesktopComposeUiTest.save(name: String, dark: Boolean) {
    waitForIdle()
    repeat(12) { mainClock.advanceTimeBy(250); Thread.sleep(40) }
    val img = captureToImage().toAwtImage()
    ImageIO.write(img, "png", File(File(out!!, tag).apply { mkdirs() }, "$name-${if (dark) "dark" else "light"}.png"))
  }

  private fun ComposeUiTest.click(text: String) {
    waitUntil(timeoutMillis = 20_000) { onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
  }

  private fun phone(name: String, dark: Boolean, drive: DesktopComposeUiTest.() -> Unit = {}, content: @Composable () -> Unit) =
    runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
      I18n.source = { Lang.FA }
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(2f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(360, 760),
          LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) { WithSelf("3.2.615") { content() } }
        }
      }
      waitForIdle()
      drive()
      save(name, dark)
    }

  /** مالکِ چرخهٔ عمرِ ثابت در «ساخته‌شده» (همان `DeskD1ShotsTest`) */
  private class Lifecycle : androidx.lifecycle.LifecycleOwner {
    override val lifecycle: androidx.lifecycle.LifecycleRegistry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply {
      currentState = androidx.lifecycle.Lifecycle.State.CREATED
    }
  }

  private fun loaded(d: ShopData): ShopStore = RouteUiHarness.memoryStore(d).also { s ->
    @Suppress("UNCHECKED_CAST")
    (ShopStore::class.java.getDeclaredField("_loaded").apply { isAccessible = true }.get(s) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>).value = true
  }

  /** کامپیوتر: پوستهٔ واقعی (`AppRoot`) روی همان مسیر */
  private fun desk(name: String, route: String, drive: DesktopComposeUiTest.() -> Unit = {}) = runDesktopComposeUiTest(1366, 768, testTimeout = 4.minutes) {
    I18n.source = { Lang.FA }
    GlobalsWarm.run()
    mainClock.autoAdvance = false
    val store = loaded(ShopData())
    AppNav.request.value = route
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(1f, 1f),
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(1366, 768),
        androidx.lifecycle.compose.LocalLifecycleOwner provides remember { Lifecycle() },
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            WithSelf("1.0.366") { AppRoot(store = store, theme = ThemeChoice.LIGHT, onTheme = {}) }
          }
        }
      }
    }
    repeat(20) { mainClock.advanceTimeBy(250); Thread.sleep(50) }
    drive()
    repeat(8) { mainClock.advanceTimeBy(250); Thread.sleep(50) }
    val img = captureToImage().toAwtImage()
    ImageIO.write(img, "png", File(File(out!!, tag).apply { mkdirs() }, "$name-light.png"))
  }

  private fun DesktopComposeUiTest.clickDesk(text: String) {
    repeat(40) {
      if (onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) return@repeat
      mainClock.advanceTimeBy(250); Thread.sleep(50)
    }
    onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    repeat(12) { mainClock.advanceTimeBy(250); Thread.sleep(80) }
  }

  @Test
  fun shots() {
    Assume.assumeTrue("DEVVER_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    ir.vil3ntec.tohid.data.WhatsNewPref.setKeys(
      DesktopContext,
      ir.vil3ntec.tohid.core.guide.WhatsNew.ack(ir.vil3ntec.tohid.core.guide.WhatsNew.bundled.sections, ir.vil3ntec.tohid.data.WhatsNewPref.keys(DesktopContext)),
    )
    val switch = SettingsPaths.section(SettingsLayout.PURCHASE_FX)
    val phoneList = devices(*phoneShop)
    val noTeam = TeamActions(null, {}, {}, {}, {}, {}, {}, {}, {}, {})
    TestBackend().use { b ->
      b.wireShop()
      b.server.on("/api/v1/shop/devices") { 200 to """{"devices":[${phoneShop.joinToString(",")}]}""" }
      for (dark in listOf(false, true)) {
        //  ۱) صفحهٔ یک کارمند: دستگاه‌هایش با نسخه
        phone("phone-member-devices", dark) {
          val t = remember { team(phoneList) }
          MemberContent(t, t.member("m2"), canEdit = true, busy = false, actions = MemberActions(null, { _, _ -> }, {}, {}, {}))
        }
        //  ۲) «کارمندان» ← «دستگاه‌ها»: همهٔ دستگاه‌های دکان
        phone("phone-team-devices", dark, drive = {
          waitUntil(timeoutMillis = 20_000) { onAllNodes(hasText("Redmi 9", substring = true)).fetchSemanticsNodes().isNotEmpty() }
          //  تهِ فهرست (آخرین دستگاه) تا همهٔ «دستگاه‌ها» در قاب باشد
          onAllNodes(hasText("کامپیوترِ دخل", substring = true))[0].performScrollTo()
          waitForIdle()
        }) {
          val t = remember { team(phoneList) }
          TeamContent(t, signedIn = true, isOwner = true, rotating = false, actions = noTeam) {
            DevicesSection(Backend.shop(DesktopContext), canEdit = true, snackbar = remember { SnackbarHostState() })
          }
        }
        //  ۳) کلیدِ «خرید به ارزِ دیگر» پس از زدنِ «روشن»
        PurchaseFxPref.set(DesktopContext, false)
        phone("phone-fx-switch-on", dark, drive = { click(StrShell.switchOn); repeat(8) { mainClock.advanceTimeBy(250); Thread.sleep(60) } }) {
          Box(Modifier.fillMaxSize()) { V3RoutePreview(switch, remember { RouteUiHarness.memoryStore(ShopData()) }, dark) }
        }
        PurchaseFxPref.set(DesktopContext, false)
      }
    }
    //  کامپیوتر: صفحهٔ کارمند و همان کلید
    TestBackend().use { b ->
      b.wireShop()
      b.server.on("/api/v1/shop/devices") { 200 to """{"devices":[${deskShop.joinToString(",")}]}""" }
      val deskList = devices(*deskShop)
      TeamSession.data.apply {
        loaded = true
        members = team(deskList).members
        maxMembers = 5
        devices = deskList
      }
      desk("desk-member-devices", "team/members/m2")
      PurchaseFxPref.set(DesktopContext, false)
      desk("desk-fx-switch-on", switch) { clickDesk(StrShell.switchOn) }
      PurchaseFxPref.set(DesktopContext, false)
    }
  }
}
