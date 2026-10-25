package ir.vil3ntec.tohid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  **وبِ منجمد، تصمیمِ صاحب‌کار (۱۴۰۵/۰۸/۰۲).** وبِ قدیم (`index.html`، پنلِ پروانه) منجمد است و فقط
 *  درمانِ ایمنیِ داده می‌گیرد، نه رنگ. پس از THEME-V2 پالتِ برنامه (`ShopThemes.kt`، سه تم) دیگر با وب
 *  سنجیده نمی‌شود؛ این آزمون وب را روی پالتِ منجمدِ خودش (سیستمِ رنگِ VILL3N، ۱۴۰۵/۰۷/۲۸) ثابت نگه
 *  می‌دارد تا رنگی بی‌صدا عوض نشود. عوض کردنِ هر عددِ این‌جا تصمیمِ صاحب‌کار می‌خواهد.
 *
 *  گِردی‌ها (`--radius-*`) هنوز همان ستونِ لمسیِ `Corner` ِ برنامه‌اند — آن‌ها عوض نشده‌اند.
 */
class WebPaletteParityTest {

  private val html: File? = listOf("../../index.html", "../index.html", "index.html")
    .map { File(it) }.firstOrNull { it.isFile && "--primary" in it.readText() }

  /** پالتِ منجمدِ روزِ وب — عینِ `index.html` در لحظهٔ انجماد */
  private val frozenLight = mapOf(
    "bg" to "#f3f6fa", "surface" to "#ffffff", "surface-2" to "#f8fafc", "surface-solid" to "#ffffff",
    "border" to "#d5dee9", "text" to "#17212b", "muted" to "#5b687a", "muted-2" to "#5b687a",
    "primary" to "#08665f", "primary-dark" to "#08665f", "primary-tint" to "#e0f2ef", "accent" to "#108b81",
    "success" to "#15803d", "success-tint" to "#dcfce7", "warning" to "#92400e", "warning-tint" to "#fef3c7",
    "danger" to "#b91c1c", "danger-tint" to "#fee2e2",
    "aurora-1" to "rgba(16,139,129,.30)", "aurora-2" to "rgba(37,99,235,.24)", "aurora-3" to "rgba(20,184,166,.20)",
    "info" to "#1d4ed8", "info-tint" to "#dbeafe", "zone-nav" to "#eaf0f6", "zone-cart" to "#f8fafc", "field-border" to "#c5d0de",
  )

  /** پالتِ منجمدِ شبِ وب */
  private val frozenDark = mapOf(
    "bg" to "#0b1220", "surface" to "#162235", "surface-2" to "#203149", "surface-solid" to "#162235",
    "border" to "#34445a", "text" to "#f1f5f9", "muted" to "#b6c2d2", "muted-2" to "#b6c2d2",
    "primary" to "#19a985", "primary-dark" to "#6ee7d1", "primary-tint" to "#123c3a", "accent" to "#6ee7d1",
    "success" to "#86efac", "success-tint" to "#123c2b", "warning" to "#fcd34d", "warning-tint" to "#3d2e0e",
    "danger" to "#fca5a5", "danger-tint" to "#451a1e",
    "aurora-1" to "rgba(16,139,129,.55)", "aurora-2" to "rgba(37,99,235,.38)", "aurora-3" to "rgba(20,184,166,.34)",
    "info" to "#93c5fd", "info-tint" to "#172f57", "zone-nav" to "#050a14", "zone-cart" to "#203149", "field-border" to "#40516a",
  )

  private fun block(src: String, selector: String): Map<String, String> {
    val body = src.substringAfter(selector, "").substringBefore("}")
    assertTrue("بلوکِ $selector در index.html پیدا نشد", body.isNotEmpty())
    return Regex("""--([a-z0-9-]+)\s*:\s*([^;]+);""").findAll(body).associate { it.groupValues[1] to it.groupValues[2].trim() }
  }

  @Test
  fun `پالتِ وبِ منجمد عوض نشده است — وبِ منجمد، تصمیمِ صاحب‌کار`() {
    assertTrue("index.html پیدا نشد (cwd=${File(".").absoluteFile})", html != null)
    val src = html!!.readText()
    val bad = mutableListOf<String>()
    for ((theme, web, frozen) in listOf(
      Triple("light", block(src, ":root{"), frozenLight),
      Triple("dark", block(src, "html[data-theme=\"dark\"]{"), frozenDark),
    )) {
      for ((name, want) in frozen) {
        val got = web[name]?.lowercase()?.replace(" ", "")
        if (got != want) bad += "$theme:$name: وب $got ≠ منجمد $want — وب فقط درمانِ ایمنیِ داده می‌گیرد، نه رنگ"
      }
    }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `گردی‌های وب همان ستونِ لمسیِ برنامه‌اند`() {
    assertTrue("index.html پیدا نشد", html != null)
    val root = block(html!!.readText(), ":root{")
    val bad = mutableListOf<String>()
    for ((name, token) in listOf("radius-sm" to Corner.panelSm, "radius-md" to Corner.panelMd, "radius-lg" to Corner.panelLg)) {
      val w = root[name]?.removeSuffix("px")?.toFloatOrNull()
      if (w != token.touch.value) bad += "$name: وب ${root[name]} ≠ برنامه ${token.touch}"
    }
    assertEquals(emptyList<String>(), bad)
  }
}
