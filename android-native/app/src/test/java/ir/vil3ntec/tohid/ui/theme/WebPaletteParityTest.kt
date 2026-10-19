package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 *  پالتِ وب همان `Theme.kt` است، عدد به عدد (MASTER-SPEC ۵.۴، «قاعدهٔ موجود»).
 *
 *  `index.html` سرِ پالتش همین را می‌گوید، ولی تا حالا هیچ آزمونی آن را نمی‌سنجید.
 *  این آزمون متغیرهای `:root` و `html[data-theme="dark"]` را با `LightColors` و
 *  `DarkColors` می‌سنجد، و `--radius-*` را با ستونِ لمسیِ `Corner`.
 *
 *  `drift` جاهایی است که امروز با هم نمی‌خوانند. یکی کردنشان تغییرِ ظاهر است و
 *  تصمیمِ جدا می‌خواهد. این فهرست فقط کوتاه می‌شود: ناهم‌خوانیِ تازه رد می‌شود، و
 *  اگر یکی درست شد باید از فهرست هم برداشته شود.
 *
 *  آلفا یک پله (۱/۲۵۵) جا دارد، چون `.95`ِ CSS همان `0xF2`ِ Kotlin است.
 */
class WebPaletteParityTest {

  private val html: File? = listOf("../../index.html", "../index.html", "index.html")
    .map { File(it) }.firstOrNull { it.isFile && "--primary" in it.readText() }

  /** متغیرِ وب ⇐ توکنِ `ShopColors` */
  private val colors: List<Pair<String, (ShopColors) -> Color>> = listOf(
    "bg" to { it.bg }, "surface" to { it.surface }, "surface-2" to { it.surface2 },
    "surface-solid" to { it.surfaceSolid }, "border" to { it.border },
    "text" to { it.text }, "muted" to { it.muted }, "muted-2" to { it.muted2 },
    "primary" to { it.primary }, "primary-dark" to { it.primaryDark }, "primary-tint" to { it.primaryTint },
    "accent" to { it.accent },
    "success" to { it.success }, "success-tint" to { it.successTint },
    "warning" to { it.warning }, "warning-tint" to { it.warningTint },
    "danger" to { it.danger }, "danger-tint" to { it.dangerTint },
    "aurora-1" to { it.auroraOne }, "aurora-2" to { it.auroraTwo }, "aurora-3" to { it.auroraThree },
    //  نسخهٔ ۲ (۱۴۰۵/۰۷/۲۷): رنگِ اطلاع و سه ناحیه — وب و گوشی همین عددها را می‌گیرند
    "info" to { it.info }, "info-tint" to { it.infoTint },
    "zone-nav" to { it.zoneNav }, "zone-cart" to { it.zoneCart },
    "field-border" to { it.fieldBorder },
  )

  /**
   *  ناهم‌خوانیِ امروز — فقط کم شود. نسخهٔ ۲ (۱۴۰۵/۰۷/۲۷) هر هشت موردِ پیشین را یکی
   *  کرد (accent، تینت‌ها، آرورا و `radius-lg`)؛ فهرست خالی است و خالی می‌ماند.
   */
  private val drift = emptySet<String>()

  private fun block(src: String, selector: String): Map<String, String> {
    val body = src.substringAfter(selector, "").substringBefore("}")
    assertTrue("بلوکِ $selector در index.html پیدا نشد", body.isNotEmpty())
    return Regex("""--([a-z0-9-]+)\s*:\s*([^;]+);""").findAll(body).associate { it.groupValues[1] to it.groupValues[2].trim() }
  }

  /** `#rgb`، `#rrggbb` یا `rgba(r,g,b,a)` ⇐ [a, r, g, b] */
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

  @Test
  fun `پالتِ وب و برنامه یکی‌اند، جز ناهم‌خوانی‌های ثبت‌شده`() {
    assertTrue("index.html پیدا نشد (cwd=${File(".").absoluteFile})", html != null)
    val src = html!!.readText()
    val bad = mutableListOf<String>()
    for ((theme, web, app) in listOf(
      Triple("light", block(src, ":root{"), LightColors),
      Triple("dark", block(src, "html[data-theme=\"dark\"]{"), DarkColors),
    )) {
      for ((name, token) in colors) {
        val key = "$theme:$name"
        val w = web[name]?.let(::css)
        if (w == null) { bad += "$key: در وب نیست یا خوانده نشد (${web[name]})"; continue }
        val a = argb(token(app))
        val same = abs(w[0] - a[0]) <= 1 && w.drop(1) == a.drop(1)
        if (!same && key !in drift) bad += "$key: وب ${web[name]} ≠ برنامه ${a} — یکی را به دیگری برسانید"
        if (same && key in drift) bad += "$key درست شده — از drift بردارید"
      }
    }
    val root = block(src, ":root{")
    for ((name, token) in listOf("radius-sm" to Corner.panelSm, "radius-md" to Corner.panelMd, "radius-lg" to Corner.panelLg)) {
      val w = root[name]?.removeSuffix("px")?.toFloatOrNull()
      val same = w == token.touch.value
      if (!same && name !in drift) bad += "$name: وب ${root[name]} ≠ برنامه ${token.touch}"
      if (same && name in drift) bad += "$name درست شده — از drift بردارید"
    }
    assertEquals(emptyList<String>(), bad)
  }
}
