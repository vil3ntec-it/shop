package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStates
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.TohidErrorState
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ErrorState
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.art.V3ArtAnimatedKey
import ir.vil3ntec.tohid.ui.v3.art.V3ArtKind
import ir.vil3ntec.tohid.ui.v3.art.V3ArtKindKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  دستهٔ ۶۳ (e-rows-63i) — حالت‌های مشترکِ خالی و خطا:
 *   ۱) حالتِ خالی عنوان، راهنما، کار و تصویرِ همان زمینه را دارد (از روی همان آیکنِ امروزِ صدها جای صدا زدن)؛
 *   ۲) «کاهشِ حرکت» ⇒ تصویر بی ساعتِ حرکت (ایستا)؛ مرزِ مقابل: با حرکتِ روشن ساعت زنده است (سنجه کور نیست)؛
 *   ۳) نگهبان: جزءِ خطا (تمام‌صفحه، درونِ صفحه، خالیِ خطایی، قدیم) هرگز نشانی، http یا نامِ استثنا نشان نمی‌دهد؛ متنِ
 *      دوستانه (صفحه‌های همگام‌سازی، e-rows-63h) دست‌نخورده؛ یک کار با هدفِ لمسیِ ≥ ۴۸dp.
 */
class StatesArtUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext

  @Before fun setUp() { I18n.source = { Lang.FA } }

  @After fun tearDown() {
    Motion.reducedForTest = null
    Motion.load(ctx)
  }

  private fun show(content: @Composable () -> Unit) = rule.setContent {
    CompositionLocalProvider(LocalContext provides ctx) { TohidTheme(ThemeChoice.LIGHT) { Column { content() } } }
  }

  private fun art() = rule.onAllNodes(SemanticsMatcher.keyIsDefined(V3ArtKindKey), useUnmergedTree = true).fetchSemanticsNodes()

  private fun texts(): List<String> =
    rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
      .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

  @Test fun emptyStateRendersTitleHintActionAndTheContextIllustration() {
    var taps = 0
    show {
      V3EmptyState(Icons.Outlined.Inventory2, "هنوز کالایی ندارید", body = "نخستین کالا را بیفزایید", action = "افزودنِ کالا", onAction = { taps++ })
    }
    rule.onNode(hasText("هنوز کالایی ندارید")).assertExists()
    rule.onNode(hasText("نخستین کالا را بیفزایید")).assertExists()
    rule.onNode(hasText("افزودنِ کالا") and hasClickAction()).assertHeightIsAtLeast(48.dp).performClick()
    assertEquals(1, taps)
    val nodes = art()
    assertEquals(1, nodes.size)
    assertEquals(V3ArtKind.PRODUCTS, nodes[0].config[V3ArtKindKey])
    //  تزئینی: صفحه‌خوان چیزی از تصویر نمی‌خواند
    assertEquals(null, nodes[0].config.getOrNull(SemanticsProperties.ContentDescription))
  }

  @Test fun todaysIconsPickTheirContextIllustration() {
    assertEquals(V3ArtKind.PRODUCTS, V3ArtKind.forIcon(Icons.Outlined.Inventory2))
    assertEquals(V3ArtKind.SALES, V3ArtKind.forIcon(Icons.AutoMirrored.Outlined.ReceiptLong))
    assertEquals(V3ArtKind.PEOPLE, V3ArtKind.forIcon(Icons.Outlined.PeopleOutline))
    assertEquals(V3ArtKind.ORDERS, V3ArtKind.forIcon(Icons.Outlined.ShoppingBasket))
    assertEquals(V3ArtKind.STOCK, V3ArtKind.forIcon(Icons.Outlined.SwapVert))
    assertEquals(V3ArtKind.SEARCH, V3ArtKind.forIcon(Icons.Outlined.SearchOff))
    assertEquals(V3ArtKind.ERROR_CLOUD, V3ArtKind.forIcon(Icons.Outlined.CloudOff))
    assertEquals(V3ArtKind.ERROR_CABLE, V3ArtKind.forIcon(Icons.Outlined.WifiOff))
    assertEquals(V3ArtKind.TREE, V3ArtKind.forIcon(Icons.Outlined.Extension))
    assertEquals(V3ArtKind.TREE, V3ArtKind.forIcon(null))
  }

  @Test fun reducedMotionShowsAStaticIllustrationAndFullMotionRunsTheClock() {
    Motion.reducedForTest = true
    Motion.load(ctx)
    assertFalse(Motion.enabled)
    show { V3EmptyState(Icons.Outlined.Inventory2, "خالی") }
    rule.waitForIdle()
    assertEquals(false, art().single().config[V3ArtAnimatedKey])
  }

  @Test fun fullMotionRunsTheIllustrationClock() {
    Motion.reducedForTest = false
    Motion.load(ctx)
    assertTrue(Motion.enabled)
    show { V3EmptyState(Icons.Outlined.Inventory2, "خالی") }
    rule.waitForIdle()
    assertEquals(true, art().single().config[V3ArtAnimatedKey])
  }

  @Test fun errorComponentsNeverShowAUrlHttpOrException() {
    val rawTitle = "Sync failed: https://api.vil3ntec.ir/v1/shop/sync (503)"
    val rawBody = "java.net.SocketTimeoutException: timeout\n  at okhttp3.internal.RealCall.execute(RealCall.kt:12)"
    show {
      V3ErrorState(rawTitle, body = rawBody, onRetry = {})
      V3EmptyState(Icons.Outlined.CloudOff, rawTitle, body = rawBody, action = StrKit.retry, onAction = {})
      V3Message(V3Tone.ERROR, "HTTP 500 {\"error\":\"internal\"}", body = rawBody)
      TohidErrorState("خطا", rawBody, onRetry = {})
    }
    rule.waitForIdle()
    val shown = texts()
    assertTrue(shown.isNotEmpty())
    for (t in shown) {
      for (leak in listOf("http", "://", "Exception", "java.", "okhttp", "{", "503", "500")) {
        assertFalse("«$leak» در «$t»", t.contains(leak, ignoreCase = true))
      }
    }
    //  پیش‌فرضِ ساده جای متنِ فنی: سرِ تکهٔ ساده، یا عنوان و «چه کنید» ِ همان گونه
    assertTrue(shown.toString(), "Sync failed" in shown)
    assertTrue(shown.toString(), StrStates.offlineHint in shown)
    assertTrue(shown.toString(), StrStates.errorTitle in shown || StrStates.offlineTitle in shown || StrStates.serverTitle in shown)
    //  هر خطای کاردار یک کار و هدفِ لمسیِ ≥ ۴۸dp (دو «دوباره» ِ نسخهٔ ۳؛ قدیم «تلاش دوباره» ِ خودش را دارد)
    rule.onAllNodes(hasText(StrKit.retry) and hasClickAction()).fetchSemanticsNodes().let { assertEquals(2, it.size) }
    rule.onNode(hasText(ir.vil3ntec.tohid.core.i18n.StrShellPages.tryAgain) and hasClickAction()).assertExists()
    rule.onAllNodes(hasText(StrKit.retry) and hasClickAction())[0].assertHeightIsAtLeast(48.dp)
  }

  @Test fun friendlyTextFromTheSyncScreensIsShownUnchanged() {
    val title = "ارتباط با سرور برقرار نشد"
    val body = "چند لحظهٔ دیگر «دوباره» را بزنید؛ کارهای شما روی همین دستگاه می‌ماند."
    var support = 0
    show { V3ErrorState(title, body = body, onSupport = { support++ }) }
    rule.onNode(hasText(title)).assertExists()
    rule.onNode(hasText(body)).assertExists()
    //  بی «دوباره» ⇒ تنها کار «پشتیبانی»
    rule.onNode(hasText(ir.vil3ntec.tohid.core.i18n.Str.support) and hasClickAction()).assertHeightIsAtLeast(48.dp).performClick()
    assertEquals(1, support)
    assertEquals(0, rule.onAllNodes(hasText(StrKit.retry) and hasClickAction()).fetchSemanticsNodes().size)
    assertEquals(V3ArtKind.ERROR_CLOUD, art().single().config[V3ArtKindKey])
  }
}
