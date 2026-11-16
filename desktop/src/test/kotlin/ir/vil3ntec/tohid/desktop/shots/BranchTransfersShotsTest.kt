package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.data.repo.BranchTransfersRepository
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.FakeBranchTransfers
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.desktop.TestBackend
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.branches.BranchTransfersState
import ir.vil3ntec.tohid.ui.v3.branches.DeskBranchTransfers
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «انتقال میانِ شعبه‌ها» (فازِ ۴ ِ شعبه‌ها): فهرست، جزئیات (دریافت) و فرمِ گوشی (۳۶۰×۷۶۰dp، چگالیِ ۲) و جدول با پنلِ
 *  کامپیوتر (۱۳۶۶×۷۶۸)، روز و شب، فارسی و راست‌به‌چپ، با سرورِ ساختگیِ [FakeBranchTransfers]. فقط با `BR4_SHOTS=<پوشه>` (سد نیست).
 */
@OptIn(ExperimentalTestApi::class)
class BranchTransfersShotsTest {

  private val out: File? = System.getenv("BR4_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private val data = ShopData(
    products = listOf(
      Product("tea", "چای سبزِ احمد ۵۰۰ گرمی", "خوراکی", "دانه"), Product("rice", "برنجِ سلامِ هندی", "خوراکی", "کیلو"),
      Product("oil", "روغنِ آفتاب‌گردانِ ۱٫۸ لیتری", "خوراکی", "دانه"),
    ),
    warehouseEntries = listOf(WarehouseEntry(id = "w-tea", productId = "tea", units = 40.0), WarehouseEntry(id = "w-rice", productId = "rice", units = 25.0)),
  )

  private fun seed(f: FakeBranchTransfers) = f.seed(
    FakeBranchTransfers.T(FakeBranchTransfers.id(1), out = false, status = "in_transit", lines = listOf(Triple("tea", 6000L, null), Triple("oil", 4000L, null)), note = "بارِ صبح"),
    FakeBranchTransfers.T(FakeBranchTransfers.id(2), out = false, status = "partial", lines = listOf(Triple("rice", 5000L, 2000L))),
    FakeBranchTransfers.T(FakeBranchTransfers.id(3), out = true, status = "in_transit", lines = listOf(Triple("rice", 3000L, null))),
    FakeBranchTransfers.T(FakeBranchTransfers.id(4), out = true, status = "draft", lines = listOf(Triple("tea", 2000L, null)), sentAt = null),
    FakeBranchTransfers.T(FakeBranchTransfers.id(5), out = false, status = "received", lines = listOf(Triple("tea", 3000L, 2500L))),
  )

  private fun phone(name: String, path: String, dark: Boolean) {
    val backend = TestBackend().also { seed(FakeBranchTransfers(it.server)) }
    try {
      runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
        I18n.source = { Lang.FA }
        mainClock.autoAdvance = false
        setContent {
          CompositionLocalProvider(
            LocalDensity provides Density(2f, 1f),
            LocalContext provides DesktopContext,
            LocalView provides remember { android.view.View(DesktopContext) },
            LocalConfiguration provides Configuration(360, 760),
          ) {
            TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
              CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val store = remember { RouteUiHarness.loadedStore(data) }
                V3RoutePreview(path, store, dark = dark)
              }
            }
          }
        }
        repeat(32) { mainClock.advanceTimeBy(250); Thread.sleep(80) }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(out!!.apply { mkdirs() }, "$name-${if (dark) "dark" else "light"}.png"))
      }
    } finally {
      backend.close()
    }
  }

  private fun desk(dark: Boolean) {
    val backend = TestBackend().also { seed(FakeBranchTransfers(it.server)) }
    try {
      val repo = BranchTransfersRepository(backend.api)
      val state = BranchTransfersState(repo, "")
      runBlocking { state.load() }
      runDesktopComposeUiTest(1366, 768, testTimeout = 3.minutes) {
        I18n.source = { Lang.FA }
        mainClock.autoAdvance = false
        setContent {
          CompositionLocalProvider(
            LocalContext provides DesktopContext,
            LocalView provides remember { android.view.View(DesktopContext) },
            LocalConfiguration provides Configuration(1366, 768),
            LocalDeskLayout provides true,
          ) {
            TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
              CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DeskBranchTransfers(state, repo, data, "", {}, move = true, onRefresh = {})
              }
            }
          }
        }
        repeat(12) { mainClock.advanceTimeBy(250); Thread.sleep(60) }
        //  ردیفِ نخست ⇒ پنلِ جزئیات
        onAllNodes(androidx.compose.ui.test.hasText("از الف") and androidx.compose.ui.test.hasClickAction())[0]
          .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        repeat(24) { mainClock.advanceTimeBy(250); Thread.sleep(60) }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(out!!.apply { mkdirs() }, "desk-transfers-${if (dark) "dark" else "light"}.png"))
      }
    } finally {
      backend.close()
    }
  }

  @Test fun shots() {
    Assume.assumeTrue("BR4_SHOTS نیست", out != null)
    val role = ShopRole.current(DesktopContext)
    ShopRole.remember(DesktopContext, ShopRole.MANAGER)
    try {
      for (dark in listOf(false, true)) {
        phone("phone-transfers", "branches/transfers", dark)
        phone("phone-transfer-detail", "branches/transfers/${FakeBranchTransfers.id(1)}", dark)
        phone("phone-transfer-new", "branches/transfers/new", dark)
        desk(dark)
      }
    } finally {
      ShopRole.remember(DesktopContext, role.ifBlank { ShopRole.OWNER })
    }
  }
}
