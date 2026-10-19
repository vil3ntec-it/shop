package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 *  توکن‌های برگهٔ تیرهٔ گوشی (`PhoneSheet.kt`) و `--sheet*`/`--nav-pill*`ِ
 *  `index.html` عدد به عدد یکی‌اند — همان قاعدهٔ `WebPaletteParityTest`، بی drift.
 *  و کنتراست: متن ≥ ۴٫۵:۱ (WCAG 1.4.3)، شکلِ غیرمتنی ≥ ۳:۱ (1.4.11)، روز و شب.
 */
class PhoneSheetParityTest {

  private val html: File? = listOf("../../index.html", "../index.html", "index.html")
    .map { File(it) }.firstOrNull { it.isFile && "--primary" in it.readText() }

  private val tokens: List<Pair<String, (SheetColors) -> Color>> = listOf(
    "sheet" to { it.sheet }, "on-sheet" to { it.onSheet }, "on-sheet-muted" to { it.onSheetMuted },
    "sheet-accent" to { it.accent }, "on-sheet-accent" to { it.onAccent },
    "sheet-line" to { it.line }, "sheet-raised" to { it.raised },
    "nav-pill" to { it.navPill }, "on-nav-pill" to { it.onNavPill },
  )

  private fun block(src: String, selector: String): Map<String, String> {
    val body = src.substringAfter(selector, "").substringBefore("}")
    assertTrue("بلوکِ $selector در index.html پیدا نشد", body.isNotEmpty())
    return Regex("""--([a-z0-9-]+)\s*:\s*([^;]+);""").findAll(body).associate { it.groupValues[1] to it.groupValues[2].trim() }
  }

  private fun css(v: String): List<Int>? {
    if (v.startsWith("#")) {
      val h = v.drop(1).let { if (it.length == 3) it.map { c -> "$c$c" }.joinToString("") else it }
      return listOf(255) + (0..2).map { h.substring(it * 2, it * 2 + 2).toInt(16) }
    }
    val m = Regex("""rgba?\(([^)]*)\)""").matchEntire(v) ?: return null
    val p = m.groupValues[1].split(',').map { it.trim() }
    val a = if (p.size > 3) (p[3].toFloat() * 255).roundToInt() else 255
    return listOf(a) + p.take(3).map { it.toInt() }
  }

  private fun argb(c: Color) = listOf(c.alpha, c.red, c.green, c.blue).map { (it * 255).roundToInt() }

  private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
  }

  @Test
  fun `برگهٔ گوشی در وب و برنامه یکی است`() {
    assertTrue("index.html پیدا نشد (cwd=${File(".").absoluteFile})", html != null)
    val src = html!!.readText()
    val bad = mutableListOf<String>()
    for ((theme, web, app) in listOf(
      Triple("light", block(src, ":root{"), LightSheet),
      Triple("dark", block(src, "html[data-theme=\"dark\"]{"), DarkSheet),
    )) {
      for ((name, token) in tokens) {
        val w = web[name]?.let(::css)
        if (w == null) { bad += "$theme:$name در وب نیست (${web[name]})"; continue }
        val a = argb(token(app))
        if (!(abs(w[0] - a[0]) <= 1 && w.drop(1) == a.drop(1))) bad += "$theme:$name وب ${web[name]} ≠ برنامه $a"
      }
    }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `تم از روشنیِ زمینه انتخاب می‌شود`() {
    assertEquals(LightSheet, PhoneSheet.of(LightColors.bg))
    assertEquals(DarkSheet, PhoneSheet.of(DarkColors.bg))
  }

  @Test
  fun `برگه و قرص در روز و شب خوانا هستند`() {
    val bad = mutableListOf<String>()
    for ((theme, s, c) in listOf(Triple("روز", LightSheet, LightColors), Triple("شب", DarkSheet, DarkColors))) {
      listOf(
        "onSheet روی sheet" to contrast(s.onSheet, s.sheet),
        "onSheetMuted روی sheet" to contrast(s.onSheetMuted, s.sheet),
        "onSheetMuted روی raised" to contrast(s.onSheetMuted, s.raised.compositeOver(s.sheet)),
        "accent (متن) روی sheet" to contrast(s.accent, s.sheet),
        "onAccent روی accent" to contrast(s.onAccent, s.accent),
        "onNavPill روی navPill" to contrast(s.onNavPill, s.navPill),
      ).filter { it.second < 4.5f }.forEach { bad += "$theme: ${it.first} = ${"%.2f".format(it.second)}" }
      listOf(
        "navPill روی نوار" to contrast(s.navPill, c.surfaceSolid),
        "accent روی sheet" to contrast(s.accent, s.sheet),
      ).filter { it.second < 3f }.forEach { bad += "$theme: ${it.first} = ${"%.2f".format(it.second)}" }
    }
    assertTrue("کنتراستِ کم — $bad", bad.isEmpty())
  }
}
