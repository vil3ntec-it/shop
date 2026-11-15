package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 *  ۶۹ p3 — یک سلسله‌مراتبِ دکمه در همهٔ برنامه (صاحب‌کار: «تم‌هایی که به دکمه‌ها داده شده — همه چی افتضاحه»):
 *   • اصلی و خطر: پُر با نوشتهٔ **سفید** در هر تم، روز و شب (دیگر آبیِ کم‌رنگ با نوشتهٔ تیره در شب نیست)؛ خطر عقیقِ برند و در هر
 *     چهار تم یکی؛
 *   • دوم: تهِ‌رنگِ برند (`surfaceSelected`) بی خطِ جدا؛ خنثی: خاکستریِ همان تم (کروما کم)؛ سوم: بی زمینه با نوشتهٔ برند؛
 *   • هر نوشته روی زمینهٔ خودش و روی زمینهٔ فشرده ≥ ۴٫۵؛ رده‌ها از هم جدا (ΔE ≥ ۲۰)؛
 *   • اندازه‌ها یک جا (`V3ButtonSpec`): ۴۸ / گوشهٔ ۱۲، دکمهٔ شناور ۵۶ / ۱۶، تراشه ۳۸، قرص ۳۲ با لمسِ ۴۰ — و اجزای کیت همین را می‌خوانند.
 */
class ButtonTokensTest {

  private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
  }

  private fun lin(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

  private fun chroma(c: Color): Double {
    val r = lin(c.red); val g = lin(c.green); val b = lin(c.blue)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return hypot(1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)
  }

  private fun lab(c: Color): DoubleArray {
    val r = lin(c.red); val g = lin(c.green); val b = lin(c.blue)
    val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
    fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
    return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
  }

  private fun deltaE(a: Color, b: Color): Double = lab(a).zip(lab(b)).sumOf { (x, y) -> (x - y).pow(2) }.let(::sqrt)

  private val palettes: List<Pair<String, ShopColors>> =
    ShopTheme.entries.flatMap { t -> listOf("${t.id}-روز" to shopColors(t, false), "${t.id}-شب" to shopColors(t, true)) }

  @Test
  fun `پنج گونه، یک ترتیب`() {
    assertEquals(listOf("PRIMARY", "SECONDARY", "NEUTRAL", "TERTIARY", "DANGER"), V3ButtonKind.entries.map { it.name })
  }

  @Test
  fun `اصلی و خطر پُر با نوشتهٔ سفید؛ دوم تهِ‌رنگِ برند؛ خنثی خاکستری؛ سوم بی زمینه — در هر هشت پالت`() {
    val bad = mutableListOf<String>()
    for ((name, c) in palettes) {
      val b = c.button
      val p = PhoneV3Colors.from(c)
      if (b.primary.text != Color.White) bad += "$name: نوشتهٔ دکمهٔ اصلی سفید نیست"
      if (b.danger.text != Color.White) bad += "$name: نوشتهٔ دکمهٔ خطر سفید نیست"
      if (b.secondary.bg != c.surfaceSelected) bad += "$name: دکمهٔ دوم تهِ‌رنگِ برند (surfaceSelected) نیست"
      for ((k, t) in listOf("primary" to b.primary, "secondary" to b.secondary, "neutral" to b.neutral, "danger" to b.danger, "disabled" to b.disabled)) {
        if (t.border != t.bg) bad += "$name: $k خطِ دورِ جدا دارد"
      }
      if (chroma(b.neutral.bg) > 0.03) bad += "$name: خنثی رنگی است (${"%.3f".format(chroma(b.neutral.bg))})"
      if (chroma(b.disabled.bg) > 0.03) bad += "$name: غیرفعال رنگی است"
      //  غیرفعال دیده می‌شود (≥ ۲) ولی آشکارا کم‌رنگ‌تر از هر دکمهٔ فعال است (< ۴٫۵؛ WCAG 1.4.3 معافش می‌کند)
      contrast(b.disabled.text, b.disabled.bg).let { if (it < 2f || it >= 4.5f) bad += "$name: نوشتهٔ غیرفعال ${"%.2f".format(it)}" }
      val ghost = PhoneV3Roles.button(p, V3ButtonKind.TERTIARY)
      if (ghost.bg != Color.Transparent || ghost.fg != c.brandText) bad += "$name: دکمهٔ سوم"
      //  نوشته روی زمینه و روی زمینهٔ فشرده
      for (k in V3ButtonKind.entries) {
        val pair = PhoneV3Roles.button(p, k)
        val ground = if (pair.bg.alpha == 0f) c.surface else pair.bg
        if (contrast(pair.fg, ground) < 4.5f) bad += "$name: $k نوشته ${"%.2f".format(contrast(pair.fg, ground))}"
        val down = PhoneV3Roles.pressed(p, k)
        if (down == pair.bg) bad += "$name: $k حالتِ فشرده ندارد"
        val downGround = if (down.alpha < 1f) c.surface else down
        if (contrast(pair.fg, downGround) < 4.5f) bad += "$name: $k فشرده ${"%.2f".format(contrast(pair.fg, downGround))}"
      }
      //  رده‌ها از هم جدا (ΔE ≥ ۲۰): پُرِ اصلی، پُرِ خطر و رده‌ی تهِ‌رنگ (دوم و خنثی هم‌رده‌اند و با رنگِ نوشته جدا می‌شوند)
      val pairs = listOf(
        "primary/secondary" to (b.primary.bg to b.secondary.bg), "primary/neutral" to (b.primary.bg to b.neutral.bg),
        "primary/danger" to (b.primary.bg to b.danger.bg), "danger/secondary" to (b.danger.bg to b.secondary.bg),
        "danger/neutral" to (b.danger.bg to b.neutral.bg), "disabled/primary" to (b.disabled.bg to b.primary.bg),
      )
      for ((k, v) in pairs) {
        val d = deltaE(v.first, v.second)
        if (d < 20.0) bad += "$name: $k ΔE=${"%.1f".format(d)}"
      }
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }

  @Test
  fun `خطر عقیقِ برند و در هر تم یکی`() {
    val day = palettes.filter { !it.second.isDark }.map { it.second.button.danger.bg }.toSet()
    val night = palettes.filter { it.second.isDark }.map { it.second.button.danger.bg }.toSet()
    assertEquals(1, day.size)
    assertEquals(1, night.size)
    //  روز همان عقیقِ جدولِ بخش‌ها (نشانهٔ CORAL)
    assertEquals(SectionColors.glyph(PhoneV3Palette.Hue.CORAL, false), day.single())
    //  شب: همان خانواده، کمی روشن‌تر تا روی زمینهٔ شب ≥ ۳:۱ بماند
    assertTrue(deltaE(day.single(), night.single()) < 10.0)
  }

  @Test
  fun `اندازه‌ها یک جا — و کیت همان را می‌خواند`() {
    assertEquals(48.dp, V3ButtonSpec.height)
    assertEquals(12.dp, V3ButtonSpec.radius)
    assertEquals(V3ButtonSpec.height, V3Space.control)
    assertEquals(V3ButtonSpec.radius, V3Space.controlRadius)
    assertEquals(56.dp, V3ButtonSpec.fabHeight)
    assertEquals(16.dp, V3ButtonSpec.fabRadius)
    assertEquals(38.dp, V3ButtonSpec.chipHeight)
    assertEquals(32.dp, V3ButtonSpec.pillHeight)
    assertTrue(V3ButtonSpec.pillTouch >= 40.dp)
    assertEquals(2.dp, V3ButtonSpec.focusRing)
    val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
    val v3 = File(root, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3")
    val button = File(v3, "V3Button.kt").readText()
    assertTrue(button.contains("RoundedCornerShape(V3ButtonSpec.radius)"))
    assertTrue(button.contains("height: Dp = V3Space.control"))
    assertTrue("حالتِ فشرده", button.contains("PhoneV3Roles.pressed(c, kind)"))
    assertTrue("حلقهٔ تمرکز", button.contains("v3FocusRing("))
    val fab = File(v3, "V3Fab.kt").readText()
    assertTrue(fab.contains("V3ButtonSpec.fabHeight") && fab.contains("V3ButtonSpec.fabRadius"))
    assertTrue("دکمهٔ شناور پُرِ برند", fab.contains("PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)"))
    assertFalse("هالهٔ رنگیِ دکمهٔ شناور", fab.contains("ambientColor = pair.bg"))
    assertTrue(File(v3, "V3Chips.kt").readText().contains("V3ButtonSpec.chipHeight"))
    assertTrue(File(v3, "V3RowBits.kt").readText().contains("V3ButtonSpec.pillHeight"))
  }
}
