package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.PurchaseDemo
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.theme.V3Section
import ir.vil3ntec.tohid.ui.v3.SummaryFold
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3Summary
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersScreen
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueActions
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueState
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «پیش/پس» ِ ۶۸c برای صاحب‌کار — فهرست‌های داده (خریدها، تأمین‌کننده‌ها، صفحهٔ تأمین‌کننده، مصارف، تاریخچهٔ فروش،
 *  رسیدها، کالاها، انبار، سفارش‌های آنلاین) و برگهٔ رنگِ بخش‌ها (کارتِ خلاصهٔ هر بخش و کاشی‌های «بیشتر»)، روز و شب.
 *  گوشیِ ۳۶۰×۷۶۰dp با چگالیِ ۲، فارسی و راست‌به‌چپ؛ کارتِ خلاصه بسته (`SummaryFold`) تا ردیف‌ها دیده شوند. «پیش» همین آزمون
 *  روی شاخهٔ پایه (۶۵s) است. فقط با `E68C_SHOTS=<پوشه>` (`<نام>-<light|dark>.png`).
 */
@OptIn(ExperimentalTestApi::class)
class E68cShotsTest {

  private val out: File? = System.getenv("E68C_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  /** `E68C_ONLY=purchases,orders` ⇒ فقط همین عکس‌ها */
  private val only: Set<String>? = System.getenv("E68C_ONLY")?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }?.toSet()

  private fun wanted(name: String) = only == null || name in only

  private fun frame(file: String, dark: Boolean, heightDp: Int = 760, content: @Composable () -> Unit) =
    runDesktopComposeUiTest(720, heightDp * 2, testTimeout = 3.minutes) {
      I18n.source = { Lang.FA }
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(2f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(360, heightDp),
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
          }
        }
      }
      repeat(24) {
        mainClock.advanceTimeBy(250)
        Thread.sleep(60)
      }
      ImageIO.write(captureToImage().toAwtImage(), "png", File(out!!.apply { mkdirs() }, file))
    }

  /** یک مسیر، روز و شب، با کارتِ خلاصهٔ بسته ([key] همان `collapseKey`) */
  private fun route(name: String, path: String, key: String?, data: ShopData) {
    if (!wanted(name)) return
    val prefs = ir.vil3ntec.tohid.ui.v3.summaryFoldStore(DesktopContext)
    val raw = DesktopContext.getSharedPreferences(ViewModePref.PREFS, 0)
    try {
      for (dark in listOf(false, true)) {
        if (key != null) SummaryFold.save(prefs, key, true)
        frame("$name-${if (dark) "dark" else "light"}.png", dark) {
          val store = remember { RouteUiHarness.loadedStore(data) }
          V3RoutePreview(path, store, dark = dark)
        }
      }
    } finally {
      if (key != null) raw.edit().remove(SummaryFold.key(key)).commit()
    }
  }

  private val now = 1_763_200_000_000L

  private fun orders() = listOf(
    OrderDto(
      id = "o1", orderNo = 1042, status = "new", tab = "new", next = listOf("accepted", "rejected"), source = "storefront",
      fulfillment = "delivery", paymentMethod = "cod", paymentStatus = "unpaid", customer = OrderCustomerDto("مریم احمدی", "0799•••412"),
      itemsCount = 3, itemsPreview = listOf("چای سبز", "شکر", "روغن"), totalMinor = 145_000, createdAt = now - 6 * 60_000,
    ),
    OrderDto(
      id = "o2", orderNo = 1041, status = "new", tab = "new", next = listOf("accepted", "rejected"), source = "storefront",
      fulfillment = "pickup", paymentMethod = "cash", paymentStatus = "paid", customer = OrderCustomerDto("احمد کریمی", "0700•••118"),
      itemsCount = 1, itemsPreview = listOf("برنج سیلا ۵ کیلو"), totalMinor = 832_000, createdAt = now - 25 * 60_000, isTest = true,
    ),
    OrderDto(
      id = "o3", orderNo = 1039, status = "new", tab = "new", next = listOf("accepted", "rejected"), source = "market",
      fulfillment = "delivery", paymentMethod = "cod", paymentStatus = "unpaid", customer = OrderCustomerDto("فرید نوری", "0788•••905"),
      itemsCount = 5, itemsPreview = listOf("آرد", "صابون", "چای"), totalMinor = 410_500, createdAt = now - 70 * 60_000,
    ),
  )

  private fun ordersShot() {
    if (!wanted("orders")) return
    for (dark in listOf(false, true)) {
      frame("orders-${if (dark) "dark" else "light"}.png", dark) {
        OrdersScreen(
          state = QueueState(tab = "new", counts = mapOf("new" to 3), items = orders(), loading = false, perms = OrderPermsDto(manage = true, create = true), now = now),
          actions = QueueActions(onTab = {}, onOpen = {}, onStep = { _, _ -> }, onNew = {}, onRetry = {}, onMore = {}),
          onBack = {},
        )
      }
    }
  }

  private val sectionName = mapOf(
    V3Section.SALES to "فروش", V3Section.EXPENSES to "مصارف", V3Section.PURCHASES to "خرید", V3Section.DEBT to "قرض",
    V3Section.RECEIPTS to "رسیدها", V3Section.SAFE to "گاوصندوق", V3Section.REPORTS to "گزارش", V3Section.PROMOS to "تخفیف",
    V3Section.STOCK to "انبار", V3Section.ORDERS to "سفارش",
  )

  /** کاشی‌های «بیشتر» به ترتیبِ گروه‌ها — همان رنگِ ثبت‌شدهٔ هر بخش (`V3Modules.hueOf`) */
  private val tiles = listOf("orders", "storefront", "delivery", "debtors", "history", "promos", "receipts", "chat", "warehouse", "purchasing", "expenses", "reports", "admin", "team", "devices", "myplan", "support")

  private fun paletteShot() {
    if (!wanted("palette")) return
    for (dark in listOf(false, true)) {
      frame("palette-${if (dark) "dark" else "light"}.png", dark, heightDp = 1500) {
        val c = PhoneV3.colors
        Column(Modifier.fillMaxSize().background(c.bg).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          tiles.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              row.forEach { id -> V3IconTile(Icons.Outlined.Category, V3Modules.hueOf(id), size = 44.dp) }
            }
          }
          V3Section.entries.forEach { s ->
            V3Summary(label = sectionName.getValue(s), value = "۱۲٬۴۵۰ ؋", caption = "۸ مورد · ۳٬۳۲۰ ؋ مانده", section = s)
          }
          Text("${V3Section.entries.size}", style = V3Type.footnote, color = c.inkMuted)
        }
      }
    }
  }

  @Test
  fun shots() {
    Assume.assumeTrue("E68C_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val buying = PurchaseDemo.full()
    val money = MoneyDemo.full()
    val stock = StockDemo.full()
    route("purchases", MoneyPaths.PURCHASING, MoneyPaths.PURCHASING, buying)
    route("suppliers", MoneyPaths.purchasing(tab = "suppliers"), "purchasing/suppliers", buying)
    route("supplier-page", MoneyPaths.supplier(PurchaseDemo.ALBORZ), "purchasing/supplier", buying)
    route("expenses", "expenses", "expenses", money)
    route("sales-history", "sales", "sales", HistoryDemo.busy())
    route("receipts", MoneyPaths.RECEIPTS, MoneyPaths.RECEIPTS, money)
    route("products", "products", "products", stock)
    route("stock", "stock", "stock", stock)
    route("promos", "promos", "promos", stock)
    route("more", "more", null, money)
    ordersShot()
    paletteShot()
  }
}
