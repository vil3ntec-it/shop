package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalePost
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3Celebrate
import ir.vil3ntec.tohid.ui.v3.V3CelebrationKey
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.art.V3Burst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  دستهٔ ۶۳ (e-rows-63i) — جشنِ ثبت از راهِ مشترک: فروشِ واقعیِ `CartStore.post` (همان راهِ همهٔ صندوق‌ها) `V3Celebrate`
 *  را می‌زند و پیامِ «فروش ثبت شد» ِ صندوقِ کامپیوتر (همان `showSnackbar` ِ `DeskSale`) تیکِ جشن می‌گیرد؛ وسطِ جشن
 *  زدنِ صفحهٔ زیر (اسکنِ بعدی) همان دم می‌رسد — جشن هیچ لمسی نمی‌گیرد. جشن یک بار است: پیامِ موفقِ بعدی بی آن.
 */
class CelebrationUiTest {

  @get:Rule val rule = createComposeRule()

  private val draft get() = File(DesktopContext.filesDir, CartStore.FILE)

  @Before fun before() { I18n.source = { Lang.FA }; CartStore.awaitWrites(); draft.delete(); V3Celebrate.reset() }
  @After fun after() { CartStore.awaitWrites(); draft.delete(); V3Celebrate.reset() }

  private val celebration = SemanticsMatcher.keyIsDefined(V3CelebrationKey)

  @Test fun successOverlayAppearsAfterASaleAndNeverBlocksTheNextTap() {
    //  ۱) فروشِ واقعی از راهِ مشترک
    val before = V3Celebrate.fired
    val cart = CartStore(DesktopContext)
    assertTrue(cart.set(listOf(SalesEngine.CartLine("p1", 1.0))))
    val out = runBlocking { cart.post(ShopStore(DesktopContext), ShopData()) { key -> SalesEngine.Result.Ok(ShopData(), 1, key) } }
    assertTrue("$out", out is SalePost.Outcome.Done)
    assertEquals(before + 1, V3Celebrate.fired)

    //  ۲) پیامِ صندوق: جشن روی همان پیام، صفحهٔ زیر همچنان زدنی
    val host = SnackbarHostState()
    var scope: CoroutineScope? = null
    var taps = 0
    rule.setContent {
      scope = rememberCoroutineScope()
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable { taps++ }) { Text("scan") }
            V3SnackbarHost(host, Modifier.align(Alignment.TopCenter))
          }
        }
      }
    }
    rule.runOnIdle { scope!!.launch { host.showSnackbar(StrPos.saleRecorded("۱۲۰")) } }
    rule.waitUntil(5_000) { rule.onAllNodes(celebration, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    //  وسطِ جشن (کمتر از [V3Burst.MS]) درست روی تیک زده می‌شود ⇒ به صفحهٔ زیر می‌رسد
    rule.mainClock.autoAdvance = false
    rule.mainClock.advanceTimeBy(V3Burst.MS / 3L)
    val at = rule.onAllNodes(celebration, useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot.center
    rule.onRoot().performTouchInput { click(at) }
    rule.mainClock.advanceTimeBy(100)
    assertEquals("جشن لمس را نگرفت", 1, taps)
    rule.mainClock.autoAdvance = true

    //  ۳) یک بار: پیامِ موفقِ بعدی (بی ثبتِ تازه) جشن ندارد
    rule.runOnIdle { host.currentSnackbarData?.dismiss() }
    rule.waitUntil(5_000) { host.currentSnackbarData == null }
    rule.runOnIdle { scope!!.launch { host.showSnackbar(StrPos.saleRecorded("۵۰")) } }
    rule.waitUntil(5_000) { host.currentSnackbarData != null }
    rule.waitForIdle()
    assertEquals(0, rule.onAllNodes(celebration, useUnmergedTree = true).fetchSemanticsNodes().size)
    assertNull(V3Celebrate.claim())
  }

  @Test fun anErrorToastNeverCelebrates() {
    V3Celebrate.fire()
    val host = SnackbarHostState()
    var scope: CoroutineScope? = null
    rule.setContent {
      scope = rememberCoroutineScope()
      TohidTheme(ThemeChoice.LIGHT) { V3SnackbarHost(host) }
    }
    rule.runOnIdle { scope!!.launch { host.showSnackbar(ir.vil3ntec.tohid.core.i18n.StrLedger.saleNotSaved) } }
    rule.waitUntil(5_000) { host.currentSnackbarData != null }
    rule.waitForIdle()
    assertEquals(0, rule.onAllNodes(celebration, useUnmergedTree = true).fetchSemanticsNodes().size)
  }
}
