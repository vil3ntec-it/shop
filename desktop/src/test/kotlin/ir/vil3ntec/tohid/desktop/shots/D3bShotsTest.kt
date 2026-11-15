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
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.screens.DeskCustomersShot
import ir.vil3ntec.tohid.ui.screens.DeskCustomersScreen
import ir.vil3ntec.tohid.ui.desk.DeskDataShot
import ir.vil3ntec.tohid.ui.screens.DeskHeader
import ir.vil3ntec.tohid.ui.screens.DeskRail
import ir.vil3ntec.tohid.ui.screens.DeskStatusBar
import ir.vil3ntec.tohid.ui.screens.PageWidth
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.example.EXAMPLE_FLAG
import ir.vil3ntec.tohid.ui.v3.nav.KIT_GALLERY_FLAG
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.nav.V3RouteHost
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import org.junit.Assume
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های پیش/پسِ صفحه‌های داده‌ایِ **کامپیوتر** (`claude/e-desk-d3b`): تاریخچهٔ فروش، فاکتورها، مشتری‌ها و قرض‌داران،
 *  خرید و تأمین‌کننده، مصارف، رسیدها و گزارش‌ها — با پوستهٔ کامپیوتر (سربرگ، منوی کناری، نوارِ وضعیت) همان‌طور که
 *  `AppRoot` می‌چیند، در ۱۳۶۶×۷۶۸ و ۱۹۲۰×۱۰۸۰ (چگالیِ ۱)، فارسی و راست‌به‌چپ، روشن و تیره، با دفترِ نمایشیِ [D3bDemo].
 *  فقط با `D3B_SHOTS=<پوشه>` (آزمونِ سد نیست)؛ `D3B_TAG` (`before`/`after`) پسوندِ فایل: `<پوشه>/<صفحه>-<w>-<tag>-<light|dark>.png`.
 *  هر دو برچسب که بود ⇒ `compare-<صفحه>.png` (پیش | پس، ردیفِ روشن و تیرهٔ ۱۳۶۶ و روشنِ ۱۹۲۰).
 */
@OptIn(ExperimentalTestApi::class)
class D3bShotsTest {

  private val out: File? = System.getenv("D3B_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val tag: String = System.getenv("D3B_TAG")?.takeIf { it.isNotBlank() } ?: "after"
  private val only: Set<String> = System.getenv("D3B_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

  private fun shot(name: String, w: Int, h: Int, dark: Boolean, prepare: () -> Unit = {}, content: @Composable () -> Unit) {
    runDesktopComposeUiTest(w, h, testTimeout = 3.minutes) {
      I18n.source = { Lang.FA }
      prepare()
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(1f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(w, h),
          LocalDeskLayout provides true,
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
          }
        }
      }
      //  دفترِ تاریخچه و گزارش در پس‌زمینه ساخته می‌شوند: چند بار صبر تا بنشینند
      repeat(12) {
        waitForIdle()
        Thread.sleep(250)
      }
      val img = captureToImage().toAwtImage()
      val dir = out!!.apply { mkdirs() }
      ImageIO.write(img, "png", File(dir, "$name-$w-$tag-${if (dark) "dark" else "light"}.png"))
    }
  }

  /** پوستهٔ کامپیوتر دورِ مسیر — همان چیدمانِ `AppRoot` (سربرگ، منو در آغاز، صفحه، نوارِ وضعیت) */
  @Composable
  private fun DeskFrame(path: String, store: ShopStore, dark: Boolean, body: (@Composable (ShopStore) -> Unit)? = null) {
    val context = LocalContext.current
    val data by store.data.collectAsState()
    val nav = remember(path) {
      V3Nav(V3Modules.table, "home", NavStack.of("home")).also { n ->
        n.isTab = { it in setOf("home", "sale", "debtors", "products", "more") }
        n.openDeep(path)
      }
    }
    val snackbar = remember { SnackbarHostState() }
    val toasts = rememberV3Toasts(snackbar)
    val cart = remember { CartStore(context) }
    val shell = ShellContext(
      store = store, data = data, cart = cart, snackbar = snackbar, toasts = toasts,
      theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, onTheme = {},
      bottomReserve = 0.dp, wide = true,
      access = Access.all(AppMode.SHOP, setOf(EXAMPLE_FLAG, KIT_GALLERY_FLAG)),
      navAllowed = NavCatalog.allowed(canManage = true, ordersLive = true),
    )
    val current = nav.current
    val title = nav.resolve(current)?.route?.title?.invoke().orEmpty()
    CompositionLocalProvider(LocalV3Toasts provides toasts, LocalV3SystemBottom provides 0.dp) {
      //  همان چیدمانِ `AppRoot` ِ کامپیوتر: منوی کناری تمام‌قد در آغاز، ستونِ سربرگ و صفحه کنارش، نوارِ وضعیت در پایین
      Column(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
        Row(Modifier.weight(1f)) {
          DeskRail(current = V3Modules.railId(current), onPick = {})
          Column(Modifier.weight(1f)) {
            DeskHeader(title = title, d = data, onBack = null, onMenu = {}, onSearch = {}, onOpen = {}, page = V3Modules.legacyId(current))
            //  [body]: صفحه‌ای که پشتِ دروازهٔ اشتراک است (`VipGate`، قرض‌داران) همان‌جا کشیده می‌شود — پوسته همان
            Box(Modifier.weight(1f)) { PageWidth { if (body != null) body(store) else V3RouteHost(nav, shell, Modifier.fillMaxSize()) } }
          }
        }
        DeskStatusBar(data)
      }
    }
  }

  private val sizes = listOf(1366 to 768, 1920 to 1080)

  private fun route(name: String, path: String, data: ShopData, prepare: () -> Unit = {}, cleanup: () -> Unit = {}, body: (@Composable (ShopStore) -> Unit)? = null) {
    if (only.isNotEmpty() && name !in only) return
    for ((w, h) in sizes) for (dark in listOf(false, true)) {
      try {
        shot(name, w, h, dark, prepare) {
          val store = remember { RouteUiHarness.loadedStore(data) }
          DeskFrame(path, store, dark, body)
        }
      } finally { cleanup() }
    }
  }

  @Test
  fun shots() {
    Assume.assumeTrue("D3B_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val d = D3bDemo.full()
    route("history", "sales", d)
    route("invoices", "sales/table", d)
    route("history-pick", "sales", d, prepare = { DeskDataShot.pick.value = "first" }, cleanup = { DeskDataShot.pick.value = null })
    val customers: @Composable (ShopStore) -> Unit = { st -> val live by st.data.collectAsState(); DeskCustomersScreen(st, live, remember { SnackbarHostState() }) }
    route("customers", "debtors", d, body = customers)
    route("customers-file", "debtors", d, prepare = { DeskCustomersShot.open.value = "first" }, cleanup = { DeskCustomersShot.open.value = null }, body = customers)
    route("purchasing", "purchasing", d)
    route("suppliers", "purchasing?tab=suppliers", d, prepare = { DeskDataShot.tab.value = "suppliers"; DeskDataShot.pick.value = "first" }, cleanup = { DeskDataShot.tab.value = null; DeskDataShot.pick.value = null })
    route("expenses", "expenses", d)
    route("expenses-pick", "expenses", d, prepare = { DeskDataShot.pick.value = "first" }, cleanup = { DeskDataShot.pick.value = null })
    route("receipts", "receipts", d)
    route("reports", "reports", d)
    route(
      "reports-sales", "reports", d,
      prepare = { ir.vil3ntec.tohid.ui.screens.ReportFresh.preset.value = ir.vil3ntec.tohid.core.report.ReportBoard.Preset("sales", "month", "product") },
      cleanup = { ir.vil3ntec.tohid.ui.screens.ReportFresh.preset.value = null },
    )
    compare()
  }

  /** `compare-<صفحه>.png`: پیش در آغاز، پس کنارش؛ ردیف‌ها ۱۳۶۶ روشن، ۱۳۶۶ تیره، ۱۹۲۰ روشن (نیم‌اندازه) */
  private fun compare() {
    val dir = out ?: return
    val groups = mapOf(
      "history" to listOf("history", "history-pick"),
      "customers" to listOf("customers", "customers-file"),
      "purchasing" to listOf("purchasing", "suppliers", "expenses", "expenses-pick", "receipts"),
      "reports" to listOf("reports", "reports-sales"),
    )
    for ((group, names) in groups) {
      val rows = names.flatMap { n -> listOf("$n-1366-%s-light", "$n-1366-%s-dark", "$n-1920-%s-light") }
      val pairs = rows.mapNotNull { r ->
        val b = File(dir, r.format("before") + ".png")
        val a = File(dir, r.format("after") + ".png")
        if (b.isFile && a.isFile) ImageIO.read(b) to ImageIO.read(a) else null
      }
      if (pairs.isEmpty()) continue
      val scale = 0.5
      val cellW = (1920 * scale).toInt()
      val heights = pairs.map { (b, a) -> (maxOf(b.height, a.height) * scale).toInt() }
      val gap = 12
      val img = BufferedImage(cellW * 2 + gap * 3, heights.sum() + gap * (pairs.size + 1), BufferedImage.TYPE_INT_RGB)
      val g = img.createGraphics()
      g.color = java.awt.Color(0x80, 0x80, 0x80)
      g.fillRect(0, 0, img.width, img.height)
      g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      var y = gap
      pairs.forEachIndexed { i, (b, a) ->
        //  پیش در راست (آغازِ راست‌به‌چپ)، پس در چپ
        g.drawImage(b, gap * 2 + cellW, y, (b.width * scale).toInt(), (b.height * scale).toInt(), null)
        g.drawImage(a, gap, y, (a.width * scale).toInt(), (a.height * scale).toInt(), null)
        y += heights[i] + gap
      }
      g.dispose()
      ImageIO.write(img, "png", File(dir, "compare-$group.png"))
    }
  }
}
