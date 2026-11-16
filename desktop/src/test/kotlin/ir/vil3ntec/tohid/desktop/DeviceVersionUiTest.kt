package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.model.DeviceMemberDto
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.model.ShopDevicesDto
import ir.vil3ntec.tohid.core.staff.DeviceBuilds
import ir.vil3ntec.tohid.data.PurchaseFxPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.desktop.shots.V3RoutePreview
import ir.vil3ntec.tohid.ui.screens.DevicesSection
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.account.team.DeviceVersionModel
import ir.vil3ntec.tohid.ui.v3.account.team.MemberActions
import ir.vil3ntec.tohid.ui.v3.account.team.MemberContent
import ir.vil3ntec.tohid.ui.v3.account.team.TeamData
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import ir.vil3ntec.tohid.ui.v3.settings.sections.PurchaseFxDevicesSheet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  نسخهٔ برنامهٔ دستگاه‌های دکان روی صفحه (`appVersion` ِ `GET /api/shop/devices`، `DeviceVersionModel`): فهرستِ دستگاه‌های هر
 *  کارمند و فهرستِ همهٔ دستگاه‌ها نسخه، «نسخهٔ کهنه» و «نسخهٔ نامعلوم» را نشان می‌دهند؛ کلیدِ «خرید به ارزِ دیگر» با دستگاهِ کهنه
 *  یا نامعلوم پیش از روشن شدن فهرستِ همان دستگاه‌ها و تأییدِ صریح می‌خواهد، با همهٔ دستگاه‌های به‌روز همان دم روشن می‌شود، و
 *  بی فهرست (بی اینترنت/سرور) همان هشدار و تأیید. ساختِ «همین برنامه» در آزمون گوشیِ 3.2.615 است (`DeviceVersionModel.LocalSelf`).
 */
@OptIn(ExperimentalTestApi::class)
class DeviceVersionUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var h: RouteUiHarness
  private val ctx = DesktopContext
  private val json = Json { encodeDefaults = true }
  private val self = "3.2.615"
  private val switch = SettingsPaths.section(SettingsLayout.PURCHASE_FX)

  private fun dev(id: String, name: String, v: String?, current: Boolean = false, user: String = "u2", who: String = "نوید") =
    ShopDeviceDto(id = id, name = name, appVersion = v, current = current, lastSeenAt = 1_763_000_000_000, member = DeviceMemberDto(user, "manager", who))

  private fun serve(devices: List<ShopDeviceDto>) =
    h.server.on("/api/v1/shop/devices") { 200 to json.encodeToString(ShopDevicesDto(devices)) }

  @Before fun up() {
    I18n.source = { Lang.FA }
    ShopRole.remember(ctx, ShopRole.OWNER)
    PurchaseFxPref.set(ctx, false)
    h = RouteUiHarness(rule)
    h.backend.wireShop()
  }

  @After fun down() {
    h.close()
    PurchaseFxPref.set(ctx, false)
    ShopRole.remember(ctx, ShopRole.OWNER)
  }

  @Composable
  private fun Frame(desk: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(
      LocalContext provides DesktopContext,
      LocalView provides remember { android.view.View(DesktopContext) },
      LocalConfiguration provides Configuration(360, 760),
      DeviceVersionModel.LocalSelf provides self,
      LocalDeskLayout provides desk,
    ) { TohidTheme(ThemeChoice.LIGHT) { content() } }
  }

  private fun showSwitch() {
    rule.setContent { Frame { V3RoutePreview(switch, remember { RouteUiHarness.memoryStore(ShopData()) }, dark = false) } }
    h.await(StrShell.switchOn)
  }

  private fun count(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

  /* ---------------- فهرستِ دستگاه‌ها ---------------- */

  @Test fun `دستگاه‌های کارمند — نسخه، نسخهٔ کهنه و نسخهٔ نامعلوم`() {
    val team = TeamData().apply {
      loaded = true
      members = listOf(MemberDto(id = "m2", userId = "u2", name = "نوید", role = "manager"))
      devices = listOf(dev("d1", "Redmi 9", "3.2.600"), dev("d2", "Galaxy A52", null), dev("d3", "Galaxy S24", "3.2.640"))
    }
    rule.setContent { Frame { MemberContent(team, team.member("m2"), canEdit = true, busy = false, actions = MemberActions(null, { _, _ -> }, {}, {}, {})) } }
    h.await("Redmi 9")
    assertTrue(h.has("نسخهٔ 3.2.600", substring = true))
    assertTrue(h.has("نسخهٔ 3.2.640", substring = true))
    assertEquals("فقط Redmi 9 کهنه است", 1, count(StrAccount.versionOld))
    assertEquals("فقط Galaxy A52 نامعلوم است", 1, count(StrAccount.versionUnknown))
  }

  @Test fun `فهرستِ همهٔ دستگاه‌های دکان — نسخه از سرور، همین دستگاه ساختِ خودش`() {
    serve(listOf(
      dev("me", "Pixel 8", "3.2.500", current = true, user = "u1", who = "کریم"), dev("d1", "Redmi 9", "3.2.600"),
      dev("d2", "Galaxy A52", null, user = "u3", who = "سمیع"), dev("pc", "کامپیوترِ دخل", "1.0.366", user = "u1", who = "کریم"),
    ))
    rule.setContent {
      Frame {
        Column(Modifier.verticalScroll(rememberScrollState())) { DevicesSection(Backend.shop(DesktopContext), canEdit = true, snackbar = remember { SnackbarHostState() }) }
      }
    }
    h.await("Redmi 9", substring = true)
    assertTrue("همین دستگاه ساختِ همین برنامه", h.has("نسخهٔ $self"))
    assertFalse("نه نسخه‌ای که سرور پیش‌تر دیده", h.has("نسخهٔ 3.2.500"))
    assertTrue(h.has("نسخهٔ 3.2.600"))
    assertTrue(h.has("نسخهٔ 1.0.366"))
    assertEquals(1, count(StrAccount.versionOld))
    assertEquals(1, count(StrAccount.versionUnknown))
  }

  /* ---------------- کلیدِ «خرید به ارزِ دیگر» ---------------- */

  @Test fun `دستگاهِ کهنه — روشن کردن فهرست و تأییدِ صریح می‌خواهد`() {
    serve(listOf(dev("me", "Pixel 8", self, current = true), dev("d1", "Redmi 9", "3.2.600"), dev("d3", "Galaxy S24", "3.2.640")))
    showSwitch()
    h.click(StrShell.switchOn)
    h.await(StrPurchaseFx.devicesTitle)
    assertFalse("پیش از تأیید روشن نمی‌شود", PurchaseFxPref.on(ctx))
    assertTrue(h.has("Redmi 9", substring = true))
    assertFalse("دستگاهِ به‌روز در فهرستِ پرسش نیست", h.has("Galaxy S24", substring = true))
    assertTrue(h.has(StrAccount.versionOld))
    h.click(StrPurchaseFx.turnOnAnyway)
    h.awaitThat { PurchaseFxPref.on(ctx) }
  }

  @Test fun `دستگاهِ نامعلوم — انصراف کلید را خاموش نگه می‌دارد`() {
    serve(listOf(dev("me", "Pixel 8", self, current = true), dev("d2", "Galaxy A52", null)))
    showSwitch()
    h.click(StrShell.switchOn)
    h.await(StrPurchaseFx.devicesTitle)
    assertTrue(h.has(StrAccount.versionUnknown))
    h.click(StrKit.cancel)
    h.awaitThat { !h.has(StrPurchaseFx.devicesTitle) }
    rule.waitForIdle()
    assertFalse(PurchaseFxPref.on(ctx))
  }

  @Test fun `همهٔ دستگاه‌ها به‌روز — همان دم روشن، بی پرسش`() {
    serve(listOf(dev("me", "Pixel 8", self, current = true), dev("d1", "Redmi 9", self), dev("d3", "Galaxy S24", "3.2.640")))
    showSwitch()
    h.click(StrShell.switchOn)
    h.awaitThat { PurchaseFxPref.on(ctx) }
    rule.waitForIdle()
    assertFalse(h.has(StrPurchaseFx.devicesTitle))
    assertEquals(1, h.server.hits("/api/v1/shop/devices"))
    //  خاموش کردن هیچ پرسشی ندارد
    h.click(StrShell.switchOff)
    h.awaitThat { !PurchaseFxPref.on(ctx) }
    assertEquals(1, h.server.hits("/api/v1/shop/devices"))
  }

  @Test fun `فهرست نرسید — همان هشدار و تأییدِ صریح`() {
    //  مسیری ثبت نشده ⇒ ۴۰۴ (سرورِ کهنه یا بی اجازه)؛ بی شبکه همان راه
    showSwitch()
    h.click(StrShell.switchOn)
    h.await(StrPurchaseFx.noListTitle)
    assertTrue(h.has(StrPurchaseFx.updateFirst))
    assertFalse(PurchaseFxPref.on(ctx))
    h.click(StrPurchaseFx.turnOnAnyway)
    h.awaitThat { PurchaseFxPref.on(ctx) }
  }

  @Test fun `شاگرد — نه پرسشِ فهرست، نه نوشتنِ کلید`() {
    ShopRole.remember(ctx, ShopRole.STAFF)
    serve(listOf(dev("d1", "Redmi 9", "3.2.600")))
    showSwitch()
    h.click(StrShell.switchOn)
    rule.waitForIdle()
    assertFalse(PurchaseFxPref.on(ctx))
    assertEquals(0, h.server.hits("/api/v1/shop/devices"))
    assertFalse(h.has(StrPurchaseFx.devicesTitle))
  }

  @Test fun `کامپیوتر — همان فهرست در پنجرهٔ ui-desk و «با این حال روشن کن»`() {
    val list = listOf(dev("me", "کامپیوترِ دخل", "1.0.366", current = true), dev("d1", "Redmi 9", "3.2.600"), dev("d4", "Galaxy S24", "3.2.640"))
    val g = DeviceVersionModel.gate(list, "1.0.366") as DeviceBuilds.Gate.Confirm
    val rows = DeviceVersionModel.gateRows(g, list, "1.0.366")
    var confirmed = 0
    var dismissed = 0
    rule.setContent { Frame(desk = true) { PurchaseFxDevicesSheet(rows, onConfirm = { confirmed++ }, onDismiss = { dismissed++ }) } }
    h.await(StrPurchaseFx.devicesTitle)
    assertTrue(h.has("Redmi 9", substring = true))
    assertTrue(h.has(StrAccount.versionOld))
    assertTrue("گوشیِ تازه‌ترین با کامپیوتر سنجیده نمی‌شود", h.has(StrPurchaseFx.notChecked))
    assertTrue(h.has(StrPurchaseFx.notCheckedNote))
    h.click(StrPurchaseFx.turnOnAnyway)
    assertEquals(1, confirmed)
  }
}
