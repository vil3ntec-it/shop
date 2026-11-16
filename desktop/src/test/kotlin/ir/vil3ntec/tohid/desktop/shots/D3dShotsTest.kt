package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrShift
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.PoBillDto
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.FakePurchaseOrders
import ir.vil3ntec.tohid.desktop.PoDemoOrders
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.desktop.TestBackend
import ir.vil3ntec.tohid.ui.components.common.BulkConfirmDialog
import ir.vil3ntec.tohid.ui.components.common.BulkPreviewRow
import ir.vil3ntec.tohid.ui.screens.DeskCreditLimitDialog
import ir.vil3ntec.tohid.ui.screens.DeskHeader
import ir.vil3ntec.tohid.ui.screens.DeskPurchasingScreen
import ir.vil3ntec.tohid.ui.screens.DeskRail
import ir.vil3ntec.tohid.ui.screens.DeskStatusBar
import ir.vil3ntec.tohid.ui.screens.PageWidth
import ir.vil3ntec.tohid.ui.screens.PoBillVoidDialog
import ir.vil3ntec.tohid.ui.screens.PriceHistoryDialog
import ir.vil3ntec.tohid.ui.screens.ShiftPanelV2
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import org.junit.Assume
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های پیش/پسِ پنجره‌های مشترکِ گوشی و کامپیوتر روی کامپیوتر (`claude/e-desk-d3d`، پلِ `ShopAlertDialog`) — پوستهٔ کامپیوتر (منو، سربرگ، نوارِ وضعیت) دورِ
 *  صفحهٔ خرید، ۱۳۶۶×۷۶۸، روشن و تیره، با [MoneyDemo]. فقط با `D3D_SHOTS=<پوشه>` (سد نیست)؛ `D3D_TAG` (`before`/`after`) ⇒
 *  `<پوشه>/<نام>-1366-<tag>-<light|dark>.png`؛ هر دو که بود ⇒ `compare-<نام>.png` (پیش | پس، روشن و تیره).
 *  فقط API ِ مشترکِ هر دو شاخه (همان نوشته‌ها و همان صفحه‌ها) ⇒ روی `origin/main` هم.
 */
@OptIn(ExperimentalTestApi::class)
class D3dShotsTest {

  private val out: File? = System.getenv("D3D_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val tag: String = System.getenv("D3D_TAG")?.takeIf { it.isNotBlank() } ?: "after"
  private val names = ArrayList<String>()

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

  private fun shot(name: String, interact: ComposeUiTest.() -> Unit = {}, content: @Composable (ShopStore, ShopData) -> Unit) {
    names += name
    for (dark in listOf(false, true)) {
      val backend = TestBackend()
      FakePurchaseOrders(backend.server, seed = PoDemoOrders.all)
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
            ) {
              TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                  val live by store.data.collectAsState()
                  Frame(live) { content(store, live) }
                }
              }
            }
          }
          repeat(6) { waitForIdle(); Thread.sleep(150) }
          //  کاری که در یک شاخه نشد عکس را نمی‌گیرد: همان صحنه تا جایی که رسید
          runCatching { interact() }.onFailure { System.err.println("D3dShots[$name/$tag]: ${it.message?.take(200)}") }
          repeat(8) { waitForIdle(); Thread.sleep(200) }
          val img = captureToImage().toAwtImage()
          ImageIO.write(img, "png", File(out!!.apply { mkdirs() }, "$name-1366-$tag-${if (dark) "dark" else "light"}.png"))
        }
      } finally { backend.close() }
    }
  }

  private fun ComposeUiTest.tap(text: String, substring: Boolean = false, last: Boolean = false) {
    waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text, substring = substring) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    val all = onAllNodes(hasText(text, substring = substring) and hasClickAction())
    all[if (last) all.fetchSemanticsNodes().size - 1 else 0].performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
  }

  private fun ComposeUiTest.typeInto(desc: String, text: String, enter: Boolean = false) {
    //  کادرِ نام‌دار؛ وگرنه (فرمِ پیشینِ بی‌برچسب) همان کادری که فرم به آن فوکوس داده
    val named = onAllNodes(hasSetTextAction() and (hasContentDescription(desc) or hasText(desc)))
    val f = if (named.fetchSemanticsNodes().isNotEmpty()) named[0] else onAllNodes(hasSetTextAction() and isFocused())[0]
    f.performTextInput(text)
    if (enter) f.performKeyInput { pressKey(Key.Enter) }
    waitForIdle()
  }

  private val page: @Composable (ShopStore, ShopData) -> Unit = { st, live -> DeskPurchasingScreen(st, live, remember { SnackbarHostState() }) }

  @Test
  fun shots() {
    Assume.assumeTrue("D3D_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val sale = D3bDemo.full().sales.first { it.debtorId.isNullOrBlank() }
    shot("wipe-all") { st, live -> page(st, live); ir.vil3ntec.tohid.ui.screens.WipeAllDialog("دکانِ آزمون", {}, {}) }
    shot("wipe-all-name", interact = { tap(StrSettings.wipeNext) }) { st, live -> page(st, live); ir.vil3ntec.tohid.ui.screens.WipeAllDialog("دکانِ آزمون", {}, {}) }
    shot("reprint-reason") { st, live -> page(st, live); ir.vil3ntec.tohid.ui.screens.ReprintReasonDialog({}, {}) }
    shot("crm-pricing") { st, live -> page(st, live); ir.vil3ntec.tohid.ui.screens.CrmPricingDialog(live, live.debtors.first(), {}, {}) }
    shot("reopen-invoice") { st, live -> page(st, live); ir.vil3ntec.tohid.ui.screens.ReopenInvoiceDialog(sale, {}, {}) }
    shot("cancel-invoice") { st, live -> page(st, live); ir.vil3ntec.tohid.ui.screens.CancelInvoiceDialog(sale, {}, {}) }
    shot("safe-currency") { st, live -> page(st, live); ir.vil3ntec.tohid.ui.screens.SafeAddCurrencyDialog({ _, _ -> null }, {}) }
    shot("import-history") { st, live -> page(st, live); ir.vil3ntec.tohid.ui.screens.ProductImportHistoryDialog(emptyList(), {}) }
    //  صندوقِ باز ⇒ «ورود/خروجِ نقد» و «بستن با شمارش» (همان صفحهٔ `ShiftPanelV2`)
    val open: @Composable (ShopStore, ShopData) -> Unit = { st, live ->
      val d = remember {
        val r = ir.vil3ntec.tohid.data.CashShift.open(live, 1500.0, "علی", ir.vil3ntec.tohid.todayIso(), ir.vil3ntec.tohid.data.ReportBridge.nowMs(), { ir.vil3ntec.tohid.ui.screens.newId() })
        (r as ir.vil3ntec.tohid.data.CashShift.Result.Ok).data
      }
      ShiftPanelV2(d, st, "علی", toast = {})
    }
    shot("shift-cash-move", interact = { tap(StrShift.cashInOut) }, content = open)
    shot("shift-close-count", interact = { tap(StrShift.closeRegister) }, content = open)
    compare()
  }

  /** `compare-<نام>.png`: پیش در راست، پس در چپ؛ ردیف‌ها روشن و تیره (نیم‌اندازه) */
  private fun compare() {
    val dir = out ?: return
    for (n in names) {
      val pairs = listOf("light", "dark").mapNotNull { m ->
        val b = File(dir, "$n-1366-before-$m.png"); val a = File(dir, "$n-1366-after-$m.png")
        if (b.isFile && a.isFile) ImageIO.read(b) to ImageIO.read(a) else null
      }
      if (pairs.isEmpty()) continue
      val scale = 0.5
      val cw = (1366 * scale).toInt(); val ch = (768 * scale).toInt(); val gap = 12
      val img = BufferedImage(cw * 2 + gap * 3, ch * pairs.size + gap * (pairs.size + 1), BufferedImage.TYPE_INT_RGB)
      val g = img.createGraphics()
      g.color = java.awt.Color(0x80, 0x80, 0x80)
      g.fillRect(0, 0, img.width, img.height)
      g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      pairs.forEachIndexed { i, (b, a) ->
        val y = gap + i * (ch + gap)
        g.drawImage(b, gap * 2 + cw, y, cw, ch, null)
        g.drawImage(a, gap, y, cw, ch, null)
      }
      g.dispose()
      ImageIO.write(img, "png", File(dir, "compare-$n.png"))
    }
  }
}
