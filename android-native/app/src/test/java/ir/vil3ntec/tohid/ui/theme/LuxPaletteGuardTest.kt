package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.sales.history.MoneyKind
import ir.vil3ntec.tohid.ui.v3.sales.history.historyTone
import ir.vil3ntec.tohid.ui.v3.settings.SettingsHues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 *  ۶۸c — نگهبانِ پالتِ لوکسِ برند (صاحب‌کار: «رنگ صورتی کار نگیر، توی برنامه خیلی مسخره میشه. رنگ‌های برند بزار، لوکس»):
 *   • نامِ صورتی، ارغوانی یا لیمویی در پالت (`PhoneV3Palette.Hue`) نیست؛
 *   • هیچ رنگی از جدولِ بخش‌ها (`SectionColors` روز و شب: تهِ‌رنگ، مرز، عدد، نشانه)، رنگ‌های همراه و سری‌های نمودارِ هر شش
 *     پالت، رنگِ پولِ تاریخچه، کاشی‌های «بیشتر» و رنگِ بخش‌های تنظیمات صورتی/ارغوانی (فام ۲۸۵ تا ۳۴۵) یا لیمویی/زیتونی (فام ۶۲
 *     تا ۱۰۰) یا تند (OKLCH: روشن با کروما بیش از ۰٫۲) نیست — روی **خودِ رنگ**، نه نام؛
 *   • هر بخش خانواده‌ای از هفت خانوادهٔ پالت دارد و دو کاشیِ کنارِ همِ «بیشتر» هم‌رنگ نیستند (ΔE ≥ ۱۰ در روز و شب)؛
 *   • رنگِ خطر (عقیق) فقط برای قرض و برگشت/لغو است.
 */
class LuxPaletteGuardTest {

  private fun lin(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

  /** فام و اشباعِ HSL */
  private fun hsl(c: Color): Pair<Double, Double> {
    val r = c.red.toDouble(); val g = c.green.toDouble(); val b = c.blue.toDouble()
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val l = (mx + mn) / 2
    if (mx == mn) return 0.0 to 0.0
    val d = mx - mn
    val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
    val h = when (mx) {
      r -> ((g - b) / d + (if (g < b) 6 else 0))
      g -> ((b - r) / d + 2)
      else -> ((r - g) / d + 4)
    } * 60
    return h to s
  }

  /** OKLCH: روشنی و کروما */
  private fun oklch(c: Color): Pair<Double, Double> {
    val r = lin(c.red); val g = lin(c.green); val b = lin(c.blue)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val bigL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return bigL to hypot(a, bb)
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

  /** چرا این رنگ در پالت نیست — null یعنی رواست */
  private fun forbidden(c: Color): String? {
    val (h, s) = hsl(c)
    val (l, chroma) = oklch(c)
    return when {
      s >= 0.15 && h >= 285 && h < 345 -> "صورتی/ارغوانی (فام ${h.toInt()})"
      s >= 0.15 && h >= 62 && h <= 100 -> "لیمویی/زیتونی (فام ${h.toInt()})"
      l > 0.7 && chroma > 0.2 -> "تند (کروما ${"%.3f".format(chroma)})"
      chroma > 0.24 -> "تند (کروما ${"%.3f".format(chroma)})"
      else -> null
    }
  }

  private fun hex(c: Color) = "#%08X".format((c.alpha * 255).toInt() shl 24 or ((c.red * 255).toInt() shl 16) or ((c.green * 255).toInt() shl 8) or (c.blue * 255).toInt())

  private val shops: List<ShopColors> = ShopTheme.entries.flatMap { listOf(shopColors(it, false), shopColors(it, true)) }

  @Test fun `پالت نامِ صورتی، ارغوانی یا لیمویی ندارد`() {
    val names = PhoneV3Palette.Hue.entries.map { it.name }
    for (bad in listOf("PINK", "MAGENTA", "FUCHSIA", "ROSE", "LIME", "OLIVE", "CHARTREUSE")) assertFalse(bad, bad in names)
    //  هفت خانوادهٔ پالت، همه در جدول
    assertEquals(7, SectionColors.ORDER.size)
    assertEquals(SectionColors.ORDER.toSet(), SectionColors.DAY.keys)
    assertEquals(SectionColors.ORDER.toSet(), SectionColors.NIGHT.keys)
  }

  @Test fun `جدولِ بخش‌ها، رنگ‌های همراه و سری‌های نمودار نه صورتی‌اند، نه لیمویی، نه تند`() {
    val bad = mutableListOf<String>()
    fun check(where: String, c: Color) { forbidden(c)?.let { bad += "$where ${hex(c)}: $it" } }
    for ((name, table) in listOf("روز" to SectionColors.DAY, "شب" to SectionColors.NIGHT)) for ((h, t) in table) {
      check("$name $h تهِ‌رنگ", t.tint); check("$name $h مرز", t.edge); check("$name $h عدد", t.strong); check("$name $h نشانه", t.glyph)
    }
    ThemeAccents.DAY.forEachIndexed { i, c -> check("همراهِ روز $i", c) }
    ThemeAccents.NIGHT.forEachIndexed { i, c -> check("همراهِ شب $i", c) }
    shops.forEachIndexed { i, s ->
      s.accents.forEachIndexed { j, c -> check("پالتِ $i همراهِ $j", c) }
      s.chart.series.forEachIndexed { j, c -> check("پالتِ $i سریِ $j", c) }
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }

  @Test fun `هر بخش، هر کارتِ خلاصه، هر کاشیِ «بیشتر»، هر بخشِ تنظیمات و هر رنگِ پولِ تاریخچه از پالتِ لوکس است`() {
    val bad = mutableListOf<String>()
    for (dark in listOf(false, true)) {
      val p = if (dark) PhoneV3Dark else PhoneV3Light
      V3Section.entries.forEach { s ->
        assertTrue("$s", SectionColors.family(s.hue) in SectionColors.ORDER)
        val t = SectionColors.of(s, dark)
        listOf(t.tint, t.edge, t.strong, t.glyph).forEach { c -> forbidden(c)?.let { bad += "بخشِ $s ${hex(c)}: $it" } }
      }
      val tiles = V3Modules.ALL.flatMap { it.entries }.mapNotNull { e -> V3Modules.hueOf(e)?.let { e.id to it } } +
        V3Modules.HUE_BY_ID.toList()
      tiles.forEach { (id, h) -> forbidden(PhoneV3Palette.glyph(p, h))?.let { bad += "کاشیِ $id: $it" } }
      (SettingsHues.PART + SettingsHues.GROUP).forEach { (k, h) -> forbidden(PhoneV3Palette.glyph(p, h))?.let { bad += "تنظیماتِ $k: $it" } }
      MoneyKind.entries.forEach { k -> historyTone(p, k).let { forbidden(it.fg)?.let { r -> bad += "تاریخچهٔ $k: $r" } } }
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }

  /** همهٔ رنگ‌هایی که یک تم خودش دارد (سطح، متن، برند، دکمه، گرادیان، درخشش، نمودار) — بی آلفا */
  private fun themeOwned(s: ShopColors): List<Pair<String, Color>> = listOf(
    "background" to s.background, "backgroundSubtle" to s.backgroundSubtle, "surface" to s.surface, "surfaceElevated" to s.surfaceElevated,
    "surfaceSelected" to s.surfaceSelected, "textPrimary" to s.textPrimary, "textSecondary" to s.textSecondary, "textMuted" to s.textMuted,
    "icon" to s.icon, "iconActive" to s.iconActive, "borderCard" to s.borderCard, "borderInput" to s.borderInput, "divider" to s.divider,
    "brandPrimary" to s.brandPrimary, "brandDeep" to s.brandDeep, "brandText" to s.brandText, "link" to s.link, "focus" to s.focus,
    "inverse" to s.inverse, "inverseRaised" to s.inverseRaised, "onInverse" to s.onInverse, "onInverseMuted" to s.onInverseMuted,
    "brandOnInverse" to s.brandOnInverse, "glow" to s.glow, "auroraOne" to s.auroraOne, "auroraTwo" to s.auroraTwo, "auroraThree" to s.auroraThree,
  ) + listOf("primary" to s.button.primary, "secondary" to s.button.secondary, "neutral" to s.button.neutral, "danger" to s.button.danger, "disabled" to s.button.disabled)
    .flatMap { (k, t) -> listOf("button.$k.bg" to t.bg, "button.$k.text" to t.text, "button.$k.border" to t.border) } +
    s.heroGradient.mapIndexed { i, c -> "heroGradient[$i]" to c } +
    s.accents.mapIndexed { i, c -> "accents[$i]" to c } +
    s.chart.series.mapIndexed { i, c -> "chart[$i]" to c }

  /**
   *  ۶۹ p3 (صاحب‌کار: «از رنگ‌های مزخرف توی تم … همه چی افتضاحه»؛ پیش‌تر «رنگ صورتی کار نگیر»): هیچ رنگی که یک تم خودش دارد — در هر
   *  هشت پالت — صورتی/ارغوانی، لیمویی/زیتونی یا تند (نئون) نیست؛ «غروب» (#DB2777) و فیروزه‌ایِ تندِ «اقیانوس» (#22D3EE) با همین
   *  برداشته شدند. تنها استثنا گرادیانِ پسندیده و قفل‌شدهٔ کادرِ خانهٔ «آسمان» ِ روز (آبی⇐نیلی⇐بنفش؛ بنفشِ #7C3AED کرومای ۰٫۲۴۷ دارد)
   *  و لکه‌های نورِ صفحهٔ ورود که از همان سه رنگ‌اند. رنگ‌های معناییِ مشترک (وضعیت و پیام، در هر تم یکی) فقط قاعدهٔ فام را دارند —
   *  بنفشِ «در انتظارِ تأیید» (#6D28D9) قاعدهٔ ثابتِ THEME-V2 است.
   */
  @Test fun `هیچ رنگِ هیچ تمی صورتی، لیمویی یا نئون نیست`() {
    val pinned = SkyLight.heroGradient.map { it.copy(alpha = 1f) }.toSet()
    val bad = mutableListOf<String>()
    for (t in ShopTheme.entries) for (dark in listOf(false, true)) {
      val s = shopColors(t, dark)
      for ((k, c) in themeOwned(s)) {
        val solid = c.copy(alpha = 1f)
        if (solid in pinned) continue
        forbidden(solid)?.let { bad += "${t.id}${if (dark) "-شب" else ""} $k ${hex(solid)}: $it" }
      }
      val semantic = s.status.all.flatMap { (k, v) -> listOf("status.$k.fg" to v.fg, "status.$k.bg" to v.bg) } +
        s.message.all.flatMap { (k, v) -> listOf("message.$k.text" to v.text, "message.$k.icon" to v.icon, "message.$k.bg" to v.bg) }
      for ((k, c) in semantic) {
        val (h, sat) = hsl(c)
        if (sat >= 0.15 && ((h >= 285 && h < 345) || (h >= 62 && h <= 100))) bad += "${t.id} $k ${hex(c)}: فامِ صورتی/لیمویی"
      }
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
    //  استثنا فقط همان سه رنگِ قفل‌شده است
    assertEquals(listOf("#FF2563EB", "#FF4F46E5", "#FF7C3AED"), SkyLight.heroGradient.map(::hex))
  }

  @Test fun `بخش‌های مشهود رنگِ پیشنهادیِ پالتِ لوکس را دارند — مصارف زر، خرید نفتی، قرض عقیق، سفارش نفتی`() {
    assertEquals(PhoneV3Palette.Hue.AMBER, V3Section.EXPENSES.hue)
    assertEquals(PhoneV3Palette.Hue.TEAL, V3Section.PURCHASES.hue)
    assertEquals(PhoneV3Palette.Hue.CORAL, V3Section.DEBT.hue)
    assertEquals(PhoneV3Palette.Hue.TEAL, V3Section.ORDERS.hue)
    assertEquals(PhoneV3Palette.Hue.VIOLET, V3Section.SALES.hue)
    assertEquals(PhoneV3Palette.Hue.SLATE, V3Section.STOCK.hue)
    //  رنگِ خطر (عقیق) فقط برای قرض؛ هیچ بخشِ دیگری عقیقی نیست
    assertEquals(listOf(V3Section.DEBT), V3Section.entries.filter { SectionColors.family(it.hue) == PhoneV3Palette.Hue.CORAL })
  }

  @Test fun `دو کاشیِ کنارِ همِ «بیشتر» در هیچ گروهی هم‌رنگ نیستند — روز و شب`() {
    val bad = mutableListOf<String>()
    val more = V3Modules.ALL.flatMap { it.entries }.filter { Place.MORE in it.places }.sortedBy { it.order }.groupBy { it.group }
    for (p in listOf(PhoneV3Light, PhoneV3Dark)) for ((g, list) in more) {
      list.zipWithNext().forEach { (a, b) ->
        val x = V3Modules.hueOf(a)?.let { PhoneV3Palette.glyph(p, it) } ?: p.icon
        val y = V3Modules.hueOf(b)?.let { PhoneV3Palette.glyph(p, it) } ?: p.icon
        val d = deltaE(x, y)
        if (d < 10.0) bad += "$g: ${a.id}/${b.id} ΔE=${"%.1f".format(d)}"
      }
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }
}
