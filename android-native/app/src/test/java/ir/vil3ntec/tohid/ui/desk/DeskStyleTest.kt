package ir.vil3ntec.tohid.ui.desk

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.ShopColors
import ir.vil3ntec.tohid.ui.theme.ShopTheme
import ir.vil3ntec.tohid.ui.theme.shopColors
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.settings.SettingsHues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  سیستمِ طراحیِ کامپیوتر (`ui/desk/DeskStyle.kt`، e-desk-d1):
 *   • رنگِ هر بخش فقط نگاشت به نقشه‌های یکتای گوشی است (`V3Modules.hueOf`، `SettingsHues`) — پالتِ دوم نیست؛
 *   • هر پلهٔ رنگ در هر سه تم، روز و شب، WCAG AA: متن ≥ ۴٫۵:۱ روی کارت و زمینهٔ نرم، آیکن ≥ ۳:۱؛
 *   • هیچ پرونده‌ای رنگِ بخش را دستی برنمی‌گزیند (پالت فقط از همان منبع، خواستِ «رنگ‌های برند، لوکس»)؛
 *   • فاصله‌ها روی پلهٔ ۴، گِردیِ کارت ۱۲ و کنترل ۸، و پرونده‌های `ui/desk` هیچ HEX ندارند.
 */
class DeskStyleTest {

  /** همان فرمولِ `ThemeContrastTest` */
  private fun contrast(a: Color, b: Color): Double {
    val la = a.compositeOver(Color.Black).luminance() + 0.05
    val lb = b.compositeOver(Color.Black).luminance() + 0.05
    return (maxOf(la, lb) / minOf(la, lb)).toDouble()
  }

  private val themes: List<Pair<String, ShopColors>> =
    ShopTheme.entries.flatMap { t -> listOf(false, true).map { d -> "${t.id}/${if (d) "dark" else "light"}" to shopColors(t, d) } }

  @Test fun `پله‌های رنگِ هر بخش در همهٔ تم‌ها AA`() {
    for ((name, s) in themes) for (hue in V3Hue.entries) {
      val t = DeskHues.tone(s, hue)
      assertTrue("$name $hue ink/surface ${contrast(t.ink, s.surface)}", contrast(t.ink, s.surface) >= 4.5)
      assertTrue("$name $hue ink/tile ${contrast(t.ink, t.tile)}", contrast(t.ink, t.tile) >= 4.5)
      assertTrue("$name $hue glyph/tile ${contrast(t.glyph, t.tile)}", contrast(t.glyph, t.tile) >= 3.0)
      assertTrue("$name $hue tile از کارت جداست", t.tile != s.surface)
      //  نوارِ رنگیِ ردیفِ فعال و ستونِ نمودار: نشانِ غیرِ متنی ≥ ۳:۱ روی کارت یا روی زمینهٔ نرم (بی تکیه به رنگِ تنها)
      assertTrue("$name $hue onSolid ${contrast(t.onSolid, t.solid)}", contrast(t.onSolid, t.solid) >= 3.0)
    }
  }

  @Test fun `سطح و متنِ کامپیوتر در همهٔ تم‌ها AA`() {
    for ((name, s) in themes) {
      val c = DeskColors.from(s)
      for ((label, fg, bg) in listOf(
        Triple("ink/surface", c.ink, c.surface),
        Triple("ink/app", c.ink, c.app),
        Triple("secondary/surface", c.inkSecondary, c.surface),
        Triple("secondary/app", c.inkSecondary, c.app),
        Triple("secondary/surfaceAlt", c.inkSecondary, c.surfaceAlt),
        Triple("secondary/hover", c.inkSecondary, c.hover),
        Triple("ink/selected", c.ink, c.selected),
        Triple("brandInk/surface", c.brandInk, c.surface),
        Triple("onPrimary/primary", c.onPrimary, c.primary),
        Triple("positive/soft", c.positive, c.positiveSoft),
        Triple("negative/soft", c.negative, c.negativeSoft),
        Triple("warning/surface", c.warning, c.surface),
        Triple("info/soft", c.info, c.infoSoft),
        Triple("onAlert/alert", c.onAlert, c.alert),
      )) assertTrue("$name $label ${contrast(fg, bg)}", contrast(fg, bg) >= 4.5)
      //  حلقهٔ تمرکز، آیکن و نقطهٔ «کاری مانده» (غیرِ متنی) ≥ ۳:۱
      assertTrue("$name attention", contrast(c.attention, c.surface) >= 3.0)
      assertTrue("$name focus", contrast(c.focus, c.surface) >= 3.0)
      assertTrue("$name icon", contrast(c.icon, c.surface) >= 3.0)
    }
  }

  @Test fun `رنگِ بخش فقط از نقشه‌های گوشی`() {
    for (id in listOf("debtors", "warehouse", "purchasing", "expenses", "reports", "receipts", "orders", "team", "devices", "myplan", "support", "promos", "products")) {
      assertEquals(id, V3Modules.hueOf(id) ?: V3Hue.BRAND, DeskHues.of(id))
    }
    assertEquals(V3Modules.hueOf("history"), DeskHues.of("sales"))
    assertEquals(V3Modules.hueOf("orders"), DeskHues.of("fulfil"))
    assertEquals(V3Modules.hueOf("orders"), DeskHues.of("off:orders"))
    assertEquals(SettingsHues.ROOT, DeskHues.of("settings"))
    for (g in SettingsLayout.GROUPS) {
      assertEquals(g.title, SettingsHues.of(g.title), DeskHues.of(g.title))
      assertEquals(g.title, SettingsHues.of(g.title), DeskHues.of("settings:" + g.title))
    }
  }

  @Test fun `کامپیوتر هیچ رنگِ بخشی را دستی برنمی‌گزیند — فقط نقشه‌های گوشی`() {
    //  خواستِ صاحب‌کار (۱۴۰۵/۰۸/۲۴): پالتِ برند از یک منبع؛ صورتی/لیمویی/نئون را صفحه‌ای دستی نمی‌گذارد
    val dir = listOf(
      "src/main/java/ir/vil3ntec/tohid/ui/desk",
      "../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/desk",
      "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/desk",
    ).map { File(it) }.first { it.isDirectory }
    val picked = dir.listFiles { f -> f.extension == "kt" }!!.flatMap { f ->
      Regex("""V3Hue\.([A-Z]+)""").findAll(f.readText()).map { "${f.name}:${it.groupValues[1]}" }.toList()
    }
    assertEquals("فقط برندِ پیش‌فرضِ شناسهٔ ناشناخته", listOf("DeskStyle.kt:BRAND"), picked.distinct())
    assertEquals(ir.vil3ntec.tohid.ui.screens.MoreLayout.APP_HUE, DeskHues.of("app"))
  }

  @Test fun `پله‌ها و گِردی`() {
    val scale = setOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp)
    assertEquals(scale, setOf(DeskSpace.x1, DeskSpace.x2, DeskSpace.x3, DeskSpace.x4, DeskSpace.x6, DeskSpace.x8))
    assertTrue(DeskSpace.page in scale && DeskSpace.gap in scale)
    assertEquals(12.dp, DeskRadius.card)
    assertEquals(8.dp, DeskRadius.control)
    assertEquals(240.dp, DeskSize.sidebar)
    assertEquals(72.dp, DeskSize.sidebarCompact)
  }

  @Test fun `پرونده‌های کامپیوتر رنگِ ثابت ندارند و فقط با Platform_DESKTOP یا LocalDeskLayout کشیده می‌شوند`() {
    val dir = listOf(
      "src/main/java/ir/vil3ntec/tohid/ui/desk",
      "../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/desk",
      "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/desk",
    ).map { File(it) }.first { it.isDirectory }
    val files = dir.listFiles { f -> f.extension == "kt" }!!.toList()
    assertTrue(files.size >= 5)
    for (f in files) assertTrue("${f.name}: رنگِ ثابت", !Regex("""\bColor\(\s*0x""").containsMatchIn(f.readText()))
    //  درِ ورود: منوی کناری فقط از `DeskRail` با `Platform.DESKTOP`، داشبورد فقط از `DeskHome` (مسیرِ `desk`)، «همهٔ بخش‌ها» فقط
    //  پس از شاخهٔ گوشیِ `MoreScreen`
    val ui = dir.parentFile
    assertTrue(File(ui, "screens/DeskRail.kt").readText().contains("if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) {\n    ir.vil3ntec.tohid.ui.desk.DeskSidebar("))
    assertTrue(File(ui, "v3/shell/ShellRoutes.kt").readText().contains("if (desk) DeskHomeScreen("))
    val more = File(ui, "screens/More.kt").readText()
    assertTrue(more.indexOf("PhoneMoreScreen(d, onOpen = onOpen)") in 0 until more.indexOf("DeskMoreScreen(d, onOpen)"))
  }
}
