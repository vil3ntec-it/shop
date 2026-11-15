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
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.PurchaseDemo
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.SummaryFold
import ir.vil3ntec.tohid.ui.v3.summaryFoldStore
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «پس» ِ ۶۵s برای صاحب‌کار — سربرگِ هر بخش: عنوان در نوار، کارتِ خلاصهٔ وسط‌چین و تاشو به رنگِ همان بخش، کادرِ
 *  کشویی و نمودارِ تفکیک درونِ کارت. هر صفحه باز (`<صفحه>-after-<روز|شب>.png`) و بسته (`<صفحه>-collapsed-<روز|شب>.png`).
 *  گوشیِ ۳۶۰×۷۶۰dp با چگالیِ ۲، فارسی و راست‌به‌چپ (همان جهتِ گوشی). «بسته» با همان ترجیحِ دستگاه (`SummaryFold`) کشیده و پس
 *  از عکس پاک می‌شود. «پیش» همان `E63wShotsTest`/`E63bShotsTest` روی شاخهٔ پایه است. فقط با `E65S_SHOTS=<پوشه>`.
 */
@OptIn(ExperimentalTestApi::class)
class E65sShotsTest {

  private val out: File? = System.getenv("E65S_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private fun shot(file: String, path: String, data: ShopData, dark: Boolean) = runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
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
    repeat(24) {
      mainClock.advanceTimeBy(250)
      Thread.sleep(60)
    }
    val img = captureToImage().toAwtImage()
    ImageIO.write(img, "png", File(out!!.apply { mkdirs() }, file))
  }

  /** همان صفحه باز و بسته، روز و شب؛ [key] همان `collapseKey` ِ خلاصهٔ آن صفحه */
  private fun all(name: String, path: String, key: String, data: ShopData) {
    val prefs = summaryFoldStore(DesktopContext)
    val raw = DesktopContext.getSharedPreferences(ViewModePref.PREFS, 0)
    try {
      for (dark in listOf(false, true)) {
        val mode = if (dark) "dark" else "light"
        raw.edit().remove(SummaryFold.key(key)).commit()
        shot("$name-after-$mode.png", path, data, dark)
        SummaryFold.save(prefs, key, true)
        shot("$name-collapsed-$mode.png", path, data, dark)
      }
    } finally {
      raw.edit().remove(SummaryFold.key(key)).commit()
    }
  }

  @Test
  fun shots() {
    Assume.assumeTrue("E65S_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val buying = PurchaseDemo.full()
    val money = MoneyDemo.full()
    val stock = StockDemo.full()
    all("purchases", MoneyPaths.PURCHASING, MoneyPaths.PURCHASING, buying)
    all("suppliers", MoneyPaths.purchasing(tab = "suppliers"), "purchasing/suppliers", buying)
    all("supplier-page", MoneyPaths.supplier(PurchaseDemo.ALBORZ), "purchasing/supplier", buying)
    all("expenses", "expenses", "expenses", money)
    all("sales-history", "sales", "sales", money)
    all("receipts", MoneyPaths.RECEIPTS, MoneyPaths.RECEIPTS, money)
    all("reports", "reports", "reports/profit", money)
    all("promos", "promos", "promos", stock)
    all("products", "products", "products", stock)
    all("stock", "stock", "stock", stock)
  }
}
