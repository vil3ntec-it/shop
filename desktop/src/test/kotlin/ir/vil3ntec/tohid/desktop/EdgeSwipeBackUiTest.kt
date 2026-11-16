package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.nav.EdgeSwipe
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.nav.V3RouteHost
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 *  کشیدنِ راست به چپ ⇒ برگشت (صاحب‌کار ۱۴۰۵/۰۸/۲۵: «وقتی صفحه رو از راست به چپ بکشم از اون بخش بیاد بیرون»؛ ۱۴۰۵/۰۸/۲۶: «از
 *  وسط یا آخرِ صفحه هم») در همان میزبانِ مسیرها (`V3RouteHost`): کشیدنِ لبه و کشیدنِ میانهٔ صفحه هر دو صفحهٔ درونی را می‌بندند؛
 *  کشیدنی که فرزندِ افقی خورد (برگه‌های «کالا/انبار»، تراشه، کاروسل، کشیدنِ ردیف)، کشیدنِ کوتاه، عمودی و کج دست نمی‌خورند.
 */
@OptIn(ExperimentalTestApi::class)
class EdgeSwipeBackUiTest {

  @get:Rule val rule = createComposeRule()

  private val detail = ProductsPaths.item("p-rice")

  private fun show(path: String = detail): V3Nav {
    I18n.source = { Lang.FA }
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val nav = V3Nav(V3Modules.table, "home", NavStack.of("home")).also { it.isTab = { p -> p in TillShellHarness.navRoutes }; it.openDeep(path) }
    val store = RouteUiHarness.loadedStore(MoneyDemo.full())
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          val snackbar = remember { SnackbarHostState() }
          val toasts = rememberV3Toasts(snackbar)
          val shell = ShellContext(
            store = store, data = store.data.value, cart = remember { CartStore(DesktopContext) }, snackbar = snackbar, toasts = toasts,
            theme = ThemeChoice.LIGHT, onTheme = {}, bottomReserve = PhoneNav.RESERVE, wide = false,
            access = Access.all(AppMode.SHOP, emptySet()), navAllowed = NavCatalog.allowed(canManage = true, ordersLive = true),
          )
          CompositionLocalProvider(LocalV3Toasts provides toasts, LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides false) {
            V3RouteHost(nav, shell, Modifier.fillMaxSize())
          }
        }
      }
    }
    rule.waitForIdle()
    assertEquals(path, nav.current)
    return nav
  }

  private fun drag(fromX: (Float) -> Float, toX: (Float) -> Float, dy: Float = 0f) {
    rule.onRoot().performTouchInput {
      swipe(Offset(fromX(width.toFloat()), centerY), Offset(toX(width.toFloat()), centerY + dy), durationMillis = 300)
    }
    rule.mainClock.advanceTimeBy(1_000)
    rule.waitForIdle()
  }

  @Test
  fun `کشیدن از لبهٔ راست به چپ صفحه را می‌بندد — همان برگشت`() {
    val nav = show()
    drag({ w -> w - 4f }, { w -> w * 0.35f })
    assertEquals("برگشت به فهرستِ کالاها", ProductsPaths.LIST, nav.current)
  }

  @Test
  fun `کشیدنِ کوتاه یا عمودی برگشت نیست؛ کشیدنی که برگه‌های افقیِ صفحه خوردند هم نه`() {
    val nav = show()
    drag({ w -> w - 4f }, { w -> w * 0.9f })
    assertEquals("کوتاه‌تر از ۳۰٪ ⇒ سرِ جا", detail, nav.current)
    drag({ w -> w - 4f }, { w -> w - 10f }, dy = 300f)
    assertEquals("عمودی از لبه ⇒ پیمایش، نه برگشت", detail, nav.current)
    //  جزئیاتِ کالا برگه‌های افقیِ «کالا/انبار» دارد: کشیدنِ میانه را همان برگه‌ها می‌خورند — برگشت نیست
    drag({ w -> w * 0.6f }, { w -> w * 0.05f })
    assertTrue("برگه‌های افقی کشیدن را خوردند، به فهرست برنگشت: ${nav.current}", nav.current != ProductsPaths.LIST && nav.current.endsWith("/p-rice"))
  }

  @Test
  fun `کشیدن از میانه یا آخرِ صفحه هم می‌بندد؛ کشیدنِ کج یا عمودی نه`() {
    val about = ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths.ABOUT
    var nav = show(about)
    drag({ w -> w * 0.6f }, { w -> w * 0.6f - w * 0.4f }, dy = 120f)
    assertEquals("کج (افقی کمتر از دو برابرِ عمودی) ⇒ سرِ جا", about, nav.current)
    drag({ w -> w * 0.5f }, { w -> w * 0.5f + 4f }, dy = 300f)
    assertEquals("عمودی از میانه ⇒ پیمایش", about, nav.current)
    drag({ w -> w * 0.6f }, { w -> w * 0.1f })
    assertTrue("میانهٔ صفحه ⇒ برگشت: ${nav.current}", nav.current != about)
    nav = show(about)
    drag({ w -> w * 0.35f }, { w -> 4f })
    assertTrue("آخرِ صفحه (سمتِ چپ) ⇒ برگشت: ${nav.current}", nav.current != about)
  }

  @Test
  fun `آستانه — ۳۰٪ ِ پهنا یا پرتابِ تند`() {
    assertFalse(EdgeSwipe.commits(0.29f, 0f))
    assertTrue(EdgeSwipe.commits(0.3f, 0f))
    assertTrue("پرتابِ تند به چپ", EdgeSwipe.commits(0.1f, -1_500f))
    assertFalse("پرتاب به راست", EdgeSwipe.commits(0.1f, 1_500f))
    assertTrue("لبه: افقیِ چیره", EdgeSwipe.starts(-30f, -20f, 8f, fromEdge = true))
    assertFalse("میانه: افقی باید دو برابرِ عمودی باشد", EdgeSwipe.starts(-30f, -20f, 8f, fromEdge = false))
    assertTrue("میانه: افقیِ روشن", EdgeSwipe.starts(-50f, -20f, 8f, fromEdge = false))
    assertFalse("به راست هرگز", EdgeSwipe.starts(50f, 0f, 8f, fromEdge = true))
    assertTrue("عمودی", EdgeSwipe.vertical(5f, 30f, 8f))
  }
}
