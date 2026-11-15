package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerScreen
import ir.vil3ntec.tohid.ui.v3.money.DebtorsListActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorsListScreen
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های ۶۵q برای صاحب‌کار: «قرض‌داران» (کارت‌های سرخ/سبز و سه جعبهٔ رنگیِ بالا) و «پروندهٔ قرض‌دار» (ردیف‌های سرخِ برد و
 *  سبزِ رسید، یکی با «فاکتور #۱۲» در یادداشت) — گوشیِ ۳۶۰×۷۶۰dp، چگالیِ ۲، فارسی و راست‌به‌چپ مثلِ گوشی، روشن و تیره.
 *  فقط با `E65Q_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست)؛ `E65Q_PREFIX` پیشوندِ نامِ پرونده‌هاست (پیش‌فرض `restored`).
 */
@OptIn(ExperimentalTestApi::class)
class E65qShotsTest {

  private val out: File? = System.getenv("E65Q_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val prefix: String = System.getenv("E65Q_PREFIX")?.takeIf { it.isNotBlank() } ?: "restored"

  private val today = todayIso()
  private val now = System.currentTimeMillis()
  private val dayMs = 86_400_000L
  private fun iso(daysAgo: Int) = CivilDays.addDays(today, -daysAgo)!!

  private val rice = Product(id = "s-rice", name = "برنج", unit = "کیلو", salePrice = 200.0)
  private val data = ShopData(
    products = listOf(rice),
    debtors = listOf(
      Debtor(id = "s-file", name = "سمیع رحیمی", phone = "0700123456"),
      Debtor(id = "s-a", name = "نسیم احمدی", phone = "0799555123"),
      Debtor(id = "s-b", name = "کریم نوری"),
      Debtor(id = "s-c", name = "زهره کریمی", phone = "0788000111"),
      Debtor(id = "s-d", name = "داوود حیدری"),
      Debtor(id = "s-e", name = "هارون"),
    ),
    sales = listOf(Sale("s-s12", 12, finalTotal = 400.0, paymentMethod = "credit", debtorId = "s-file", remaining = 400.0, createdAt = now - 6 * dayMs, date = iso(6))),
    saleItems = listOf(SaleItem("s-i1", "s-s12", rice.id, quantity = 2.0, unitPrice = 200.0, totalPrice = 400.0)),
    transactions = listOf(
      DebtTransaction("s-1", "s-file", "give", 400.0, iso(6), notes = "فروش نسیه — فاکتور #12", createdAt = now - 6 * dayMs),
      DebtTransaction("s-2", "s-file", "receive", 150.0, iso(4), createdAt = now - 4 * dayMs),
      DebtTransaction("s-3", "s-file", "give", 300.0, iso(2), notes = "آرد و روغن", createdAt = now - 2 * dayMs),
      DebtTransaction("s-4", "s-file", "receive", 250.0, today, createdAt = now),
      DebtTransaction("s-5", "s-a", "give", 1250.0, iso(12), createdAt = now - 12 * dayMs),
      DebtTransaction("s-6", "s-b", "give", 500.0, iso(40), createdAt = now - 40 * dayMs),
      DebtTransaction("s-7", "s-c", "give", 200.0, iso(9), createdAt = now - 9 * dayMs),
      DebtTransaction("s-8", "s-c", "receive", 200.0, iso(3), createdAt = now - 3 * dayMs),
      DebtTransaction("s-9", "s-d", "receive", 100.0, iso(1), createdAt = now - dayMs),
      DebtTransaction("s-10", "s-e", "give", 300.0, iso(5), createdAt = now - 5 * dayMs),
      DebtTransaction("s-11", "s-e", "receive", 300.0, today, createdAt = now),
    ),
  )

  private val noDebtors = DebtorsListActions(onBack = {}, onOpen = {}, onEdit = {}, onDelete = {}, onAdd = {})
  private val noLedger = DebtorLedgerActions(
    onBack = {}, onCompose = {}, onEdit = {}, onDelete = {}, onOpenTx = {}, onDeleteTx = {}, onOpenSale = {}, onOlderSales = {},
  )

  /** «فاکتور #n» ِ زدنی همان‌طور که پوستهٔ برنامه (`V3RouteHost`) می‌گذارد */
  @Composable
  private fun Links(content: @Composable () -> Unit) {
    CompositionLocalProvider(ir.vil3ntec.tohid.ui.v3.LocalInvoiceLinks provides ir.vil3ntec.tohid.ui.v3.InvoiceLinks.of(data) {}) { content() }
  }

  /** پوستهٔ گوشی برای فهرست (مسیرش در آزمون پشتِ سدِ اشتراک است): همان جای نوار و همان نوار */
  @Composable
  private fun ShellLike(current: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides false, ir.vil3ntec.tohid.ui.v3.LocalV3NavSearch provides true) {
      Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
        Box(Modifier.fillMaxSize().padding(bottom = PhoneNav.RESERVE)) { content() }
        PhoneNavBar(NavLayouts.DEFAULT, current, onPick = {}, onEdit = null, modifier = Modifier.align(Alignment.BottomCenter))
      }
    }
  }

  private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) = runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
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
          //  فارسی راست‌به‌چپ، همان جهتِ گوشی
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
        }
      }
    }
    repeat(24) {
      mainClock.advanceTimeBy(250)
      Thread.sleep(60)
    }
    val img = captureToImage().toAwtImage()
    ImageIO.write(img, "png", File(out!!.apply { mkdirs() }, "$prefix-$name-${if (dark) "dark" else "light"}.png"))
  }

  @Test
  fun shots() {
    Assume.assumeTrue("E65Q_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    for (dark in listOf(false, true)) {
      shot("debtors", dark) { ShellLike("debtors") { DebtorsListScreen(data, null, null, noDebtors) } }
      shot("debtor-file", dark) {
        Links { CompositionLocalProvider(LocalV3SystemBottom provides 0.dp) { DebtorLedgerScreen("s-file", data, wide = false, actions = noLedger, drawerOpen = false) } }
      }
    }
  }
}
