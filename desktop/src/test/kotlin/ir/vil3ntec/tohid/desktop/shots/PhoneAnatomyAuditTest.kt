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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.PurchaseDemo
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3Anatomy
import ir.vil3ntec.tohid.ui.v3.V3DataRowSpec
import ir.vil3ntec.tohid.ui.v3.V3NavRowSpec
import ir.vil3ntec.tohid.ui.v3.V3_STATE_BLOCK
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  ممیزیِ کالبدِ همهٔ صفحه‌های گوشی (کارِ `claude/e-phone-p0`): هر مسیرِ `TAB`/`PAGE` ِ حالتِ دکان از جدولِ یکتای مسیرها
 *  (`V3Modules.table`) همان‌طور که برنامه بازش می‌کند (`V3RoutePreview`: پوسته، نوار، پشتهٔ پدرها)، در ۳۶۰×۷۶۰dp، فارسی، روز و شب،
 *  با دفترِ نمایشی. برای هر صفحه عکس (`<نام>-<light|dark>.png`) و یک ردیف در `audit.json`: شمارِ اجزای کالبد از نشان‌های
 *  `V3Anatomy` (قاب، عنوانِ درشت، خلاصه، دکمهٔ شناور، تراشه، کارت، ردیفِ داده/ناوبری/ساده، حالتِ خالی)، عنوانِ نوار، و هر
 *  نوشتهٔ درشتِ (≥ ۲۰sp) بیرون از نوار و خلاصه — نشانِ عنوانِ درشتِ بدنه یا کارتِ سرخطِ دست‌ساز.
 *
 *  فقط با `P0_AUDIT=<پوشه>`؛ `P0_AUDIT_ONLY=a,b` فقط مسیرهایی که یکی از این‌ها را دارند؛ `P0_AUDIT_LIGHT_ONLY=1` بی شب.
 *  عکس‌ها برای صاحب‌کار و هماهنگ‌کننده‌اند؛ سدِ همیشگی همان `PageAnatomyGuardTest` است.
 */
@OptIn(ExperimentalTestApi::class)
class PhoneAnatomyAuditTest {

  private val out: File? = System.getenv("P0_AUDIT")?.takeIf { it.isNotBlank() }?.let(::File)
  private val only: List<String> = System.getenv("P0_AUDIT_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
  private val lightOnly = System.getenv("P0_AUDIT_LIGHT_ONLY") == "1"

  /** یک صفحه برای ممیزی: نامِ پرونده، مسیرِ باز، الگو و دفترِ نمایشی */
  private class Page(val name: String, val path: String, val pattern: String, val kind: RouteKind, val module: String, val chrome: String, val data: () -> ShopData)

  private fun merged(): ShopData {
    val a = MoneyDemo.full()
    val b = StockDemo.full()
    return a.copy(
      products = a.products + b.products, productCategories = (a.productCategories + b.productCategories).distinct(),
      productUnits = (a.productUnits + b.productUnits).distinct(), warehouseEntries = a.warehouseEntries + b.warehouseEntries,
      sales = a.sales + b.sales, saleItems = a.saleItems + b.saleItems, stockMovements = a.stockMovements + b.stockMovements,
    )
  }

  /** پارامترِ نمایشیِ هر الگو — شناسه‌های همان دفترها */
  private fun fill(pattern: String): String? {
    var p = pattern
    val id = when {
      p.startsWith("products/") || p.startsWith("stock/") || p.startsWith("storefront/products/") -> StockDemo.RICE
      p.startsWith("debtors/") -> MoneyDemo.OWING
      p.startsWith("expenses/") -> "e1"
      p.startsWith("purchasing/suppliers/") -> PurchaseDemo.ALBORZ
      else -> "1"
    }
    p = p.replace("{id}", id).replace("{saleId}", HistoryDemo.CASH).replace("{docId}", "1").replace("{key}", "1")
      .replace("{i}", "0").replace("{txId}", "t1").replace("{entryId}", "e1").replace("{productId}", StockDemo.RICE)
      .replace("{orderId}", "1").replace("{conversationId}", "1").replace("{keep}", MoneyDemo.OWING).replace("{drop}", MoneyDemo.SETTLED)
      .replace("{old}", "1")
    return p.takeIf { '{' !in it }
  }

  private fun dataFor(path: String): () -> ShopData = when {
    path.startsWith("purchasing") -> { { PurchaseDemo.full() } }
    path == "sales" || path.startsWith("sales/") -> { { HistoryDemo.busy() } }
    else -> { { merged() } }
  }

  private fun pages(): List<Page> {
    val list = ArrayList<Page>()
    val seen = HashSet<String>()
    for (m in V3Modules.ALL.filter { AppMode.SHOP in it.modes }) {
      for (r in m.routes) {
        if (r.kind != RouteKind.TAB && r.kind != RouteKind.PAGE) continue
        val paths = when (r.pattern) {
          "settings/{group}" -> listOf(SettingsLayout.SHOP, SettingsLayout.SALES, SettingsLayout.GOODS, SettingsLayout.DEVICES, SettingsLayout.DATA, SettingsLayout.LOOK)
            .map { "settings/$it" }
          "settings/{group}/{section}" -> emptyList()
          else -> listOfNotNull(fill(r.pattern))
        }
        for (path in paths) {
          if (!seen.add(path)) continue
          val name = path.replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF]+"), "_").trim('_').ifEmpty { "root" }
          list += Page(name, path, r.pattern, r.kind, m.id, r.chrome.name, dataFor(path))
        }
      }
    }
    return list.filter { p -> only.isEmpty() || only.any { p.path.contains(it) } }
  }

  /** یک صفحه در یک تم: عکس و شمارِ کالبد */
  private fun audit(page: Page, dark: Boolean, write: Boolean): kotlinx.serialization.json.JsonObject {
    var result: kotlinx.serialization.json.JsonObject? = null
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
              val store = remember { RouteUiHarness.loadedStore(page.data()) }
              V3RoutePreview(page.path, store, dark = dark)
            }
          }
        }
      }
      repeat(14) {
        mainClock.advanceTimeBy(250)
        Thread.sleep(25)
      }
      if (write) {
        val img = captureToImage().toAwtImage()
        ImageIO.write(img, "png", File(out!!, "${page.name}-${if (dark) "dark" else "light"}.png"))
      }
      val roots = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
      val counts = HashMap<String, Int>()
      val big = ArrayList<String>()
      val barTitles = ArrayList<String>()
      fun layout(n: SemanticsNode): TextLayoutResult? {
        val get = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val l = ArrayList<TextLayoutResult>()
        return if (runCatching { get(l) }.getOrDefault(false)) l.firstOrNull() else null
      }
      fun walk(n: SemanticsNode, inside: Set<String>) {
        val tag = n.config.getOrNull(SemanticsProperties.TestTag)
        if (tag != null) counts[tag] = (counts[tag] ?: 0) + 1
        val here = if (tag != null) inside + tag else inside
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        if (!text.isNullOrBlank()) {
          if (V3Anatomy.BAR in here && barTitles.size < 3 && n.config.getOrNull(SemanticsProperties.Heading) != null) barTitles += text
          val sp = layout(n)?.layoutInput?.style?.fontSize?.value ?: 0f
          val framed = V3Anatomy.BAR in here || V3Anatomy.SUMMARY in here || V3Anatomy.LARGE_TITLE in here
          if (sp >= 20f && !framed && big.size < 6) big += "$text (${sp.toInt()}sp)"
        }
        n.children.forEach { walk(it, here) }
      }
      roots.forEach { walk(it, emptySet()) }
      fun c(tag: String) = counts[tag] ?: 0
      result = buildJsonObject {
        put("name", page.name)
        put("path", page.path)
        put("pattern", page.pattern)
        put("kind", page.kind.name)
        put("module", page.module)
        put("chrome", page.chrome)
        put("dark", dark)
        putJsonObject("counts") {
          put("page", c(V3Anatomy.PAGE))
          put("bar", c(V3Anatomy.BAR))
          put("largeTitle", c(V3Anatomy.LARGE_TITLE))
          put("summary", c(V3Anatomy.SUMMARY))
          put("fab", c(V3Anatomy.FAB))
          put("chips", c(V3Anatomy.CHIPS))
          put("card", c(V3Anatomy.CARD))
          put("dataRow", c(V3DataRowSpec.ROW_TAG))
          put("navRow", c(V3NavRowSpec.ROW_TAG))
          put("listRow", c(V3Anatomy.LIST_ROW))
          put("state", c(V3_STATE_BLOCK))
        }
        putJsonArray("barTitle") { barTitles.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("bigText") { big.forEach { add(JsonPrimitive(it)) } }
      }
    }
    return result!!
  }

  @Test
  fun audit() {
    Assume.assumeTrue("P0_AUDIT نیست", out != null)
    out!!.mkdirs()
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val ui = DesktopContext.getSharedPreferences("tohid-ui", 0)
    ui.edit().putBoolean("report_fresh", false).commit()
    ir.vil3ntec.tohid.ui.screens.ReportFresh.on.value = false
    val rows = ArrayList<kotlinx.serialization.json.JsonElement>()
    val list = pages()
    println("P0_AUDIT: ${list.size} صفحه")
    for ((i, p) in list.withIndex()) {
      for (dark in if (lightOnly) listOf(false) else listOf(false, true)) {
        val row = runCatching { audit(p, dark, write = true) }.getOrElse { e ->
          buildJsonObject {
            put("name", p.name); put("path", p.path); put("pattern", p.pattern); put("kind", p.kind.name); put("module", p.module)
            put("chrome", p.chrome); put("dark", dark); put("error", (e.message ?: e.toString()).take(300))
          }
        }
        rows += row
      }
      if (i % 10 == 0) println("P0_AUDIT: ${i + 1}/${list.size} ${p.path}")
      File(out, "audit.json").writeText(Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), JsonArray(rows)))
    }
    ui.edit().remove("report_fresh").commit()
  }
}
