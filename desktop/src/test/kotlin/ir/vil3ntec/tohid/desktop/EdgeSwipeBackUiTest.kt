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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import ir.vil3ntec.tohid.ui.v3.nav.edgeSwipeBack
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

  private fun drag(fromX: (Float) -> Float, toX: (Float) -> Float, dy: Float = 0f, dyOfWidth: Float = 0f) {
    rule.onRoot().performTouchInput {
      swipe(Offset(fromX(width.toFloat()), centerY), Offset(toX(width.toFloat()), centerY + dy + dyOfWidth * width), durationMillis = 300)
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
  fun `کشیدنِ کوتاه یا عمودی برگشت نیست؛ کشیدن از میانهٔ جزئیاتِ کالا برگشت است`() {
    val nav = show()
    drag({ w -> w - 4f }, { w -> w * 0.9f })
    assertEquals("کوتاه‌تر از ۳۰٪ ⇒ سرِ جا", detail, nav.current)
    drag({ w -> w - 4f }, { w -> w - 10f }, dy = 300f)
    assertEquals("عمودی از لبه ⇒ پیمایش، نه برگشت", detail, nav.current)
    //  خواستِ ۱۴۰۵/۰۸/۲۶: میانهٔ صفحه هم برگشت است (جزئیاتِ کالا کشیدنِ افقیِ خودش را ندارد؛ آزمونِ پیشین چنین می‌پنداشت)
    drag({ w -> w * 0.6f }, { w -> w * 0.05f })
    assertEquals("میانهٔ جزئیاتِ کالا ⇒ فهرستِ کالاها", ProductsPaths.LIST, nav.current)
  }

  @Test
  fun `کشیدنی که ردیفِ پیمایشیِ افقی خورد برگشت نیست؛ همان کشیدن روی جای خالی برگشت است`() {
    var backs = 0
    rule.setContent {
      val scope = androidx.compose.runtime.rememberCoroutineScope()
      androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().edgeSwipeBack(true, scope, onProgress = {}) { backs++ }) {
        androidx.compose.foundation.lazy.LazyRow(
          Modifier.fillMaxWidth().height(80.dp).testTag("row"),
        ) {
          items(50) { i -> androidx.compose.foundation.layout.Box(Modifier.size(60.dp).testTag("i$i")) }
        }
      }
    }
    rule.onNodeWithTag("row").performTouchInput { swipe(Offset(width * 0.8f, centerY), Offset(width * 0.1f, centerY), durationMillis = 300) }
    rule.mainClock.advanceTimeBy(1_000)
    rule.waitForIdle()
    assertEquals("ردیفِ تراشه/کاروسل کشیدن را خورد ⇒ برگشت نیست", 0, backs)
    rule.onRoot().performTouchInput { swipe(Offset(width * 0.8f, height * 0.7f), Offset(width * 0.1f, height * 0.7f), durationMillis = 300) }
    rule.mainClock.advanceTimeBy(1_000)
    rule.waitForIdle()
    assertEquals("جای خالیِ صفحه ⇒ برگشت", 1, backs)
  }

  @Test
  fun `کشیدن از میانه یا آخرِ صفحه هم می‌بندد؛ کشیدنِ کج یا عمودی نه`() {
    val about = ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths.ABOUT
    var nav = show(about)
    drag({ w -> w * 0.6f }, { w -> w * 0.2f }, dyOfWidth = 0.3f)
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
