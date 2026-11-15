package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Text
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
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ShopTheme
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.ThemeStore
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3PillAction
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.money.DebtorsListActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorsListScreen
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleActions
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleScreen
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  ۶۹ p3 — عکس‌های پیش/پسِ ظاهرِ سراسری (دارک‌مود، دکمه‌ها، فهرستِ تم‌ها): گوشیِ ۳۶۰×۷۶۰dp با چگالیِ ۲، فارسی، روز و شب —
 *  خانه، صندوق (خالی و با سبد)، کالاها، انبار، قرض‌داران، تاریخچهٔ فروش، مصارف، تنظیمات، «بیشتر»، «ظاهر» (انتخابِ تم)، برگهٔ تأیید،
 *  تختهٔ دکمه‌ها و خانهٔ هر تم. «پیش» همین آزمون روی شاخهٔ پایه است. فقط با `P3_SHOTS=<پوشه>` (`P3_ONLY=home,buttons` فقط همین‌ها).
 */
@OptIn(ExperimentalTestApi::class)
class E69p3ShotsTest {

  private val out: File? = System.getenv("P3_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val only: Set<String>? = System.getenv("P3_ONLY")?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }?.toSet()
  private fun wanted(name: String) = only == null || name in only

  private val noSale = SaleActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
  private val noDebtors = DebtorsListActions(onBack = null, onOpen = {}, onEdit = {}, onDelete = {}, onAdd = {})

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
          LocalDeskLayout provides false,
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
              Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) { content() }
            }
          }
        }
      }
      repeat(24) {
        mainClock.advanceTimeBy(250)
        Thread.sleep(60)
      }
      ImageIO.write(captureToImage().toAwtImage(), "png", File(out!!.apply { mkdirs() }, file))
    }

  private fun both(name: String, heightDp: Int = 760, content: @Composable (dark: Boolean) -> Unit) {
    if (!wanted(name)) return
    for (dark in listOf(false, true)) frame("$name-${if (dark) "dark" else "light"}.png", dark, heightDp) { content(dark) }
  }

  private fun route(name: String, path: String, data: ShopData) = both(name) { dark ->
    val store = remember { RouteUiHarness.loadedStore(data) }
    V3RoutePreview(path, store, dark = dark)
  }

  /** پوستهٔ گوشی برای صفحه‌ای که مسیرش پشتِ سدِ اشتراک است: همان جای نوار و همان نوار */
  @Composable
  private fun ShellLike(current: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalV3SystemBottom provides 0.dp, ir.vil3ntec.tohid.ui.v3.LocalV3NavSearch provides true) {
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(bottom = PhoneNav.RESERVE)) { content() }
        PhoneNavBar(NavLayouts.DEFAULT, current, onPick = {}, onEdit = null, modifier = Modifier.align(Alignment.BottomCenter))
      }
    }
  }

  /** تختهٔ دکمه‌ها: چهار گونه در حالت‌های عادی/غیرفعال/در حالِ کار، دکمهٔ متنی، قرص، تراشه، قطعه‌ای، کادر و دکمهٔ شناور */
  @Composable
  private fun ButtonBoard() {
    val c = PhoneV3.colors
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("دکمه‌ها", style = V3Type.headline, color = c.ink)
      V3Button("ثبتِ فروش", {}, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3Button("پیش‌نویس", {}, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
        V3Button("انصراف", {}, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3Button("حذف", {}, Modifier.weight(1f), kind = V3ButtonKind.DANGER, icon = Icons.Outlined.DeleteOutline)
        V3Button("غیرفعال", {}, Modifier.weight(1f), enabled = false)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3Button("در حالِ ثبت", {}, Modifier.weight(1f), loading = true)
        V3Button("دوم غیرفعال", {}, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, enabled = false)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        V3TextButton("همه", {})
        V3TextButton("پاک کردن", {}, danger = true)
        V3PillAction("پرداخت", {})
        V3PillAction("دوباره", {}, neutral = true)
        V3IconButton(Icons.Outlined.NotificationsNone, "زنگ", dot = true, onClick = {})
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V3Chip("همه", selected = true, onClick = {})
        V3Chip("نسیه", selected = false, onClick = {}, count = 3)
        V3Chip("کم", selected = false, onClick = {})
      }
      V3Segmented(listOf("روشن", "تیره", "خودکار"), selected = 1, onSelect = {})
      V3SearchField("", {}, "جست‌وجوی کالا")
      V3TextField("۱۲۵۰", {}, "قیمت", modifier = Modifier.fillMaxWidth())
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        V3Fab(Icons.Outlined.Add, "کالای تازه", expanded = true, onClick = {})
      }
    }
  }

  @Test
  fun shots() {
    Assume.assumeTrue("P3_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val reduced = Motion.reducedForTest
    Motion.reducedForTest = true
    Motion.load(DesktopContext)
    val stock = StockDemo.full()
    val money = MoneyDemo.full()
    val lines = stock.products.take(3).mapIndexed { i, p -> SalesEngine.CartLine(p.id, (i + 1).toDouble()) }
    val total = stock.products.take(3).mapIndexed { i, p -> p.salePrice * (i + 1) }.sum()
    try {
      ThemeStore.preview(ShopTheme.SKY)
      route("home", "home", money)
      both("till") { ShellLike("sale") { SaleScreen(stock, emptyList(), 0.0, null, listMode = false, actions = noSale) } }
      both("till-cart") { ShellLike("sale") { SaleScreen(stock, lines, total, null, listMode = false, actions = noSale) } }
      route("products", "products", stock)
      route("stock", "stock", stock)
      both("debtors") { ShellLike("debtors") { DebtorsListScreen(money, null, null, noDebtors) } }
      route("sales-history", "sales", HistoryDemo.busy())
      route("expenses", "expenses", money)
      route("settings", "settings", stock)
      route("more", "more", money)
      route("appearance", "settings/general/appearance", stock)
      both("sheet") {
        Box(Modifier.fillMaxSize()) {
          val store = remember { RouteUiHarness.loadedStore(stock) }
          V3RoutePreview("products", store, dark = it)
          V3ConfirmSheet(
            title = "این کالا حذف شود؟", message = "موجودی و سابقهٔ فروشش می‌ماند؛ فقط از فهرست برداشته می‌شود.",
            confirm = "حذف", onConfirm = {}, onDismiss = {}, icon = Icons.Outlined.DeleteOutline,
          )
        }
      }
      both("buttons", heightDp = 900) { ButtonBoard() }
      if (wanted("themes")) for (t in ShopTheme.entries) {
        ThemeStore.preview(t)
        for (dark in listOf(false, true)) {
          frame("theme-${t.id}-home-${if (dark) "dark" else "light"}.png", dark) {
            val store = remember { RouteUiHarness.loadedStore(money) }
            V3RoutePreview("home", store, dark = dark)
          }
          frame("theme-${t.id}-buttons-${if (dark) "dark" else "light"}.png", dark, heightDp = 900) { ButtonBoard() }
          frame("theme-${t.id}-gallery-${if (dark) "dark" else "light"}.png", dark, heightDp = 900) { ir.vil3ntec.tohid.ui.v3.gallery.ThemeGallery() }
        }
      }
    } finally {
      ThemeStore.preview(ShopTheme.SKY)
      Motion.reducedForTest = reduced
      Motion.load(DesktopContext)
    }
  }
}
