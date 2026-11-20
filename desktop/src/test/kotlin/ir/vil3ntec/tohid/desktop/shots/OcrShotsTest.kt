package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrInvoiceOcr
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.desktop.TestBackend
import ir.vil3ntec.tohid.ui.screens.DeskHeader
import ir.vil3ntec.tohid.ui.screens.DeskPurchasingScreen
import ir.vil3ntec.tohid.ui.screens.DeskRail
import ir.vil3ntec.tohid.ui.screens.DeskStatusBar
import ir.vil3ntec.tohid.ui.screens.LocalInvoicePhotoPicker
import ir.vil3ntec.tohid.ui.screens.PageWidth
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import org.junit.Assume
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «از عکسِ فاکتور» (PUR-12/AI-10، `claude/e-ocr`) — گوشیِ ۳۶۰×۷۶۰ (چگالیِ ۲) و کامپیوترِ ۱۳۶۶×۷۶۸، روشن و تیره، با
 *  [MoneyDemo] و سرورِ ساختگی (`TestBackend`) و عکس‌گیرِ ساختگی. «پیش» = کلیدِ خاموش (پیش‌فرضِ همهٔ دکان‌ها؛ همان صفحهٔ
 *  `origin/main`، چون خاموش هیچ چیز رسم نمی‌کند)؛ «پس» = دکمه، فهرستِ بازبینی و «نصب نیست». فقط با `OCR_SHOTS=<پوشه>`؛
 *  `<گوشی|کامپیوتر>-<صحنه>-<light|dark>.png` و `compare-phone.png` / `compare-desk.png`.
 */
@OptIn(ExperimentalTestApi::class)
class OcrShotsTest {

  private val out: File? = System.getenv("OCR_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private val lines = """{"lines":[
    {"text":"شرکت پخش البرز - فاکتور فروش","conf":90},
    {"text":"ردیف شرح کالا تعداد فی مبلغ","conf":88},
    {"text":"۱ برنج سیلا ۵ کیلویی ۱۰ ٩,۵۰۰ ٩۵۰","conf":85},
    {"text":"۲ روغن آفتابگردان ۴ ۳۱۰ ۱,۲۴۰","conf":90},
    {"text":"۳ چای سبز ۶ ۴۲۰ ۲۵۲۰۰","conf":52},
    {"text":"جمع کل ۱۳,۲۶۰","conf":93}
  ],"lang":"fas+eng","ms":400,"notice":"x"}"""
  private fun config(enabled: Boolean, available: Boolean) =
    """{"enabled":$enabled,"permitted":true,"paid":true,"available":$available,"missing":${if (available) "[]" else "[\"tesseract-ocr\",\"tesseract-ocr-fas\"]"}}"""

  private val picker: @Composable (Boolean, () -> Unit, (ByteArray?) -> Unit) -> Unit = { open, dismiss, bytes ->
    LaunchedEffect(open) { if (open) { dismiss(); bytes(byteArrayOf(-1, -40, -1, -32, 1, 2, 3)) } }
  }

  private fun backend(enabled: Boolean, available: Boolean): TestBackend = TestBackend().also {
    it.server.on("/api/v1/shop/purchase-ocr/config") { 200 to config(enabled, available) }
    it.server.on("/api/v1/shop/purchase-ocr") { 200 to lines }
  }

  private fun ComposeUiTest.settle(n: Int = 8) = repeat(n) { waitForIdle(); Thread.sleep(150) }
  private fun ComposeUiTest.tap(text: String, i: Int = 0, scroll: Boolean = false) {
    waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().size > i }
    val node = onAllNodes(hasText(text) and hasClickAction())[i]
    if (scroll) runCatching { node.performScrollTo() }
    node.performSemanticsAction(SemanticsActions.OnClick)
    settle(4)
  }

  /** «پس»: فهرستِ بازبینی با یک ردیفِ تأییدشده و یک ردشده */
  private val review: ComposeUiTest.() -> Unit = {
    tap(StrInvoiceOcr.button, scroll = true)
    waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(StrInvoiceOcr.notice)).fetchSemanticsNodes().isNotEmpty() }
    tap(StrInvoiceOcr.confirm, 0)
    tap(StrInvoiceOcr.skip, 0)
  }
  private val unavailable: ComposeUiTest.() -> Unit = {
    tap(StrInvoiceOcr.button, scroll = true)
    waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(StrInvoiceOcr.unavailableTitle)).fetchSemanticsNodes().isNotEmpty() }
  }
  private val button: ComposeUiTest.() -> Unit = {
    waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(StrInvoiceOcr.button) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    runCatching { onAllNodes(hasText(StrInvoiceOcr.button) and hasClickAction())[0].performScrollTo() }
  }

  private fun save(img: BufferedImage, name: String) = ImageIO.write(img, "png", File(out!!.apply { mkdirs() }, "$name.png"))

  private fun phone(name: String, enabled: Boolean, available: Boolean, data: ShopData, interact: ComposeUiTest.() -> Unit) {
    for (dark in listOf(false, true)) {
      val b = backend(enabled, available)
      try {
        runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
          I18n.source = { Lang.FA }
          setContent {
            CompositionLocalProvider(
              LocalDensity provides Density(2f, 1f),
              LocalContext provides DesktopContext,
              LocalView provides remember { android.view.View(DesktopContext) },
              LocalConfiguration provides Configuration(360, 760),
              LocalInvoicePhotoPicker provides picker,
            ) {
              TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                  val store = remember { RouteUiHarness.loadedStore(data) }
                  V3RoutePreview("purchasing/new", store, dark = dark)
                }
              }
            }
          }
          settle()
          runCatching { interact() }.onFailure { System.err.println("OcrShots[$name]: ${it.message?.take(200)}") }
          settle(10)
          save(captureToImage().toAwtImage(), "phone-$name-${if (dark) "dark" else "light"}")
        }
      } finally { b.close() }
    }
  }

  @Composable
  private fun Frame(data: ShopData, body: @Composable () -> Unit) {
    CompositionLocalProvider(LocalV3SystemBottom provides 0.dp) {
      Column(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
        Row(Modifier.weight(1f)) {
          DeskRail(current = "purchasing", onPick = {})
          Column(Modifier.weight(1f)) {
            DeskHeader(title = StrPurchasePages.purchaseInvoices, d = data, onBack = null, onMenu = {}, onSearch = {}, onOpen = {}, page = "purchasing")
            Box(Modifier.weight(1f)) { PageWidth { body() } }
          }
        }
        DeskStatusBar(data)
      }
    }
  }

  private fun desk(name: String, enabled: Boolean, available: Boolean, interact: ComposeUiTest.() -> Unit) {
    for (dark in listOf(false, true)) {
      val b = backend(enabled, available)
      try {
        runDesktopComposeUiTest(1366, 768, testTimeout = 3.minutes) {
          I18n.source = { Lang.FA }
          val store = RouteUiHarness.loadedStore(MoneyDemo.full())
          setContent {
            CompositionLocalProvider(
              LocalDensity provides Density(1f, 1f),
              LocalContext provides DesktopContext,
              LocalView provides remember { android.view.View(DesktopContext) },
              LocalConfiguration provides Configuration(1366, 768),
              LocalDeskLayout provides true,
              LocalInvoicePhotoPicker provides picker,
            ) {
              TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                  val live by store.data.collectAsState()
                  Frame(live) { DeskPurchasingScreen(store, live, remember { SnackbarHostState() }) }
                }
              }
            }
          }
          settle()
          runCatching { tap(StrPurchasePages.newPurchaseKey); interact() }.onFailure { System.err.println("OcrShots[$name]: ${it.message?.take(200)}") }
          settle(10)
          save(captureToImage().toAwtImage(), "desk-$name-${if (dark) "dark" else "light"}")
        }
      } finally { b.close() }
    }
  }

  /** چهار صحنه کنارِ هم (پیش، پس، بازبینی، نصب‌نیست)؛ ردیفِ بالا روشن، پایین تیره، نیم‌اندازه */
  private fun compare(kind: String, w: Int, h: Int) {
    val dir = out ?: return
    val scenes = listOf("before", "after", "review", "unavailable")
    val cw = w / 2; val ch = h / 2; val gap = 12
    val img = BufferedImage(cw * scenes.size + gap * (scenes.size + 1), ch * 2 + gap * 3, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.color = java.awt.Color(0x80, 0x80, 0x80)
    g.fillRect(0, 0, img.width, img.height)
    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    listOf("light", "dark").forEachIndexed { r, m ->
      scenes.forEachIndexed { i, s ->
        val f = File(dir, "$kind-$s-$m.png")
        if (f.isFile) g.drawImage(ImageIO.read(f), img.width - (i + 1) * (cw + gap), gap + r * (ch + gap), cw, ch, null)
      }
    }
    g.dispose()
    ImageIO.write(img, "png", File(dir, "compare-$kind.png"))
  }

  @Test
  fun shots() {
    Assume.assumeTrue("OCR_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val money = MoneyDemo.full()
    phone("before", enabled = false, available = true, data = money) { settle() }
    phone("after", enabled = true, available = true, data = money, interact = button)
    phone("review", enabled = true, available = true, data = money, interact = review)
    phone("unavailable", enabled = true, available = false, data = money, interact = unavailable)
    desk("before", enabled = false, available = true) { settle() }
    desk("after", enabled = true, available = true, interact = button)
    desk("review", enabled = true, available = true, interact = review)
    desk("unavailable", enabled = true, available = false, interact = unavailable)
    compare("phone", 720, 1520)
    compare("desk", 1366, 768)
  }
}
