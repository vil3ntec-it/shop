package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.runtime.Composable
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
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.PurchaseDemo
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.SummaryFold
import ir.vil3ntec.tohid.ui.v3.online.orders.ManualStep
import ir.vil3ntec.tohid.ui.v3.online.orders.NewOrderScreen
import ir.vil3ntec.tohid.ui.v3.summaryFoldStore
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «پیش/پس» ِ خواستِ صاحب‌کار در دورِ سوم (p0r3، عکسِ «مصارف»): ردیفِ داده (مبلغ هم‌خطِ عنوان، جزئیات یک خط)، خلاصهٔ بسته
 *  (بی برچسب، مبلغ کنارِ پیکان)، نمودارِ ستونیِ خلاصه و کادرِ کشوییِ بی جعبه — در مصارف (باز و بسته)، خریدها، تاریخچهٔ فروش،
 *  قرض‌داران (ظاهرش همان)، رسیدها و گزارش‌ها؛ و «سفارشِ دستی» بی «قلمِ دستی» (کاتالوگ و جزئیات). گوشیِ ۳۶۰×۷۶۰dp، چگالیِ ۲، فارسی،
 *  روز و شب. فقط با `P0OWN_SHOTS=<پوشه>` (زیرپوشه‌های `owner-rows` و `manual-order`)؛ `P0OWN_ONLY=a,b` فقط همین نام‌ها.
 */
@OptIn(ExperimentalTestApi::class)
class OwnerRowsShotsTest {

  private val out: File? = System.getenv("P0OWN_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val only: Set<String>? = System.getenv("P0OWN_ONLY")?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }?.toSet()

  private fun frame(dir: String, name: String, dark: Boolean, content: @Composable () -> Unit) {
    if (only != null && name !in only) return
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
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
          }
        }
      }
      repeat(28) {
        mainClock.advanceTimeBy(250)
        Thread.sleep(60)
      }
      ImageIO.write(captureToImage().toAwtImage(), "png", File(File(out!!, dir).apply { mkdirs() }, "$name-${if (dark) "dark" else "light"}.png"))
    }
  }

  private fun route(name: String, path: String, data: ShopData, folded: Pair<String, Boolean>? = null) {
    val raw = DesktopContext.getSharedPreferences(ViewModePref.PREFS, 0)
    try {
      for (dark in listOf(false, true)) {
        folded?.let { (key, f) -> SummaryFold.save(summaryFoldStore(DesktopContext), key, f) }
        frame("owner-rows", name, dark) {
          val store = remember { RouteUiHarness.loadedStore(data) }
          V3RoutePreview(path, store, dark = dark)
        }
      }
    } finally {
      folded?.let { (key, _) -> raw.edit().remove(SummaryFold.key(key)).commit() }
    }
  }

  @Test
  fun shots() {
    Assume.assumeTrue("P0OWN_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val today = todayIso()
    val money = MoneyDemo.full()
    //  همان سه مصرفِ عکسِ صاحب‌کار: دو امروز، یکی دیروز
    val owner = money.copy(
      expenses = listOf(
        Expense(id = "x1", category = "متفرقه", amount = 200.0, date = today, createdAt = 3),
        Expense(id = "x2", category = "برق", amount = 66.0, date = today, createdAt = 2),
        Expense(id = "x3", category = "کرایه", amount = 1000.0, date = if (today.endsWith("-01")) today else ir.vil3ntec.tohid.yesterdayIso(), createdAt = 1),
      ) + money.expenses,
    )
    route("expenses-collapsed", "expenses", owner, "expenses" to true)
    route("expenses-open", "expenses", owner, "expenses" to false)
    route("purchases", "purchasing", PurchaseDemo.full())
    route("sales-history", "sales", HistoryDemo.busy())
    route("debtors", "debtors", money)
    route("receipts", "receipts", money)
    route("reports", "reports", D3bDemo.full())

    //  «سفارشِ دستی»: کاتالوگ (گامِ ۱) و جزئیات با دو کالا (گامِ ۲)
    val stock = StockDemo.full()
    for (dark in listOf(false, true)) {
      frame("manual-order", "pick", dark) {
        NewOrderScreen(sources = listOf("phone", "manual"), busy = false, failure = null, onSubmit = {}, onBack = {}, d = stock, startStep = ManualStep.PICK)
      }
      frame("manual-order", "details", dark) {
        NewOrderScreen(
          sources = listOf("phone", "manual"), busy = false, failure = null, onSubmit = {}, onBack = {}, d = stock,
          startCart = stock.products.take(2).map { SalesEngine.CartLine(it.id, 1.0) }, startStep = ManualStep.DETAILS,
          start = OrderDraft.Form(),
        )
      }
    }
  }
}
