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
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3Anatomy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
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

  /** یک صفحه در یک تم: عکس و شمارِ کالبد */
  private fun audit(page: AnatomyProbe.Page, dark: Boolean, write: Boolean): kotlinx.serialization.json.JsonObject {
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
      val k = AnatomyProbe.census(onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes())
      result = buildJsonObject {
        put("name", page.name)
        put("path", page.path)
        put("pattern", page.pattern)
        put("kind", page.kind.name)
        put("module", page.module)
        put("chrome", page.chrome)
        put("dark", dark)
        putJsonObject("counts") {
          put("page", k.page)
          put("bar", k.n(V3Anatomy.BAR))
          put("largeTitle", k.largeTitle)
          put("summary", k.summary)
          put("fab", k.fab)
          put("chips", k.chips)
          put("card", k.card)
          put("dataRow", k.dataRow)
          put("navRow", k.navRow)
          put("listRow", k.listRow)
          put("state", k.state)
        }
        putJsonArray("barTitle") { k.barTitles.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("bigText") { k.bigText.forEach { add(JsonPrimitive(it)) } }
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
    val list = AnatomyProbe.pages(only)
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
