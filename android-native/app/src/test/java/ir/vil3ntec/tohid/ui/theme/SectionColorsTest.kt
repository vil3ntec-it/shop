package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 *  ۶۵s — جدولِ رنگِ بخش‌ها (`SectionColors`، صاحب‌کار: «تمام رنگ‌ها باید با دقت باشن»):
 *   • هر خانواده روز و شب دارد و هر بخش همان خانوادهٔ کاشیِ ماژولِ خودش (یک منبع)؛
 *   • عدد (`strong`)، برچسب و نوشتهٔ اصلی روی تهِ‌رنگ دست‌کم ۴٫۵:۱ (WCAG AA)؛ تهِ‌رنگ از زمینهٔ صفحه جداست؛
 *   • جفت‌های کنارِ هم (قرض/دریافت، فروش/مصارف، خرید/فروش و رنگ‌های پولِ تاریخچه) در کوررنگیِ قرمز-سبز (پروتان، دوتان) هم
 *     دیده‌نی جدا (ΔE ≥ ۱۵)؛
 *   • هر `V3Summary(` در کدِ برنامه رنگِ بخشش را دارد و کارتِ خلاصه گرادیان نمی‌کشد.
 */
class SectionColorsTest {

  @Test fun `هر خانواده روز و شب دارد و هر بخش خانواده‌ای از جدول`() {
    assertEquals(SectionColors.DAY.keys, SectionColors.NIGHT.keys)
    V3Section.entries.forEach { s -> assertTrue("$s", SectionColors.family(s.hue) in SectionColors.DAY.keys) }
    //  هم‌معنی‌های پالت یک خانواده‌اند؛ ۶۸c (عمدی): نفتی (TEAL) خانوادهٔ خودش است، جدا از زمردی (MINT) — هفت خانوادهٔ پالتِ لوکس
    assertEquals(PhoneV3Palette.Hue.AMBER, SectionColors.family(PhoneV3Palette.Hue.ORANGE))
    assertEquals(PhoneV3Palette.Hue.SKY, SectionColors.family(PhoneV3Palette.Hue.BLUE))
    assertEquals(PhoneV3Palette.Hue.VIOLET, SectionColors.family(PhoneV3Palette.Hue.PURPLE))
    assertEquals(PhoneV3Palette.Hue.TEAL, SectionColors.family(PhoneV3Palette.Hue.TEAL))
    assertEquals(7, SectionColors.DAY.size)
    assertEquals(SectionColors.DAY.keys, SectionColors.ORDER.toSet())
  }

  /**
   *  یک منبع: رنگِ کارتِ خلاصهٔ هر بخش همان خانوادهٔ کاشیِ خودش در «بیشتر» است (`V3Modules.HUE_BY_ID`/ورودیِ ماژول) — بخش و
   *  کاشی دو رنگِ ناهم‌خوان ندارند.
   */
  @Test fun `رنگِ بخش همان خانوادهٔ کاشیِ ماژولِ خودش است`() {
    val modules = mapOf(
      V3Section.SALES to "history", V3Section.EXPENSES to "expenses", V3Section.PURCHASES to "purchasing",
      V3Section.DEBT to "debtors", V3Section.RECEIPTS to "receipts", V3Section.REPORTS to "reports",
      V3Section.PROMOS to "promos", V3Section.STOCK to "warehouse", V3Section.ORDERS to "orders",
    )
    modules.forEach { (s, id) ->
      val hue = ir.vil3ntec.tohid.ui.v3.nav.V3Modules.hueOf(id)
      assertEquals("$s ⇐ کاشیِ «$id»", SectionColors.family(hue!!), SectionColors.family(s.hue))
    }
  }

  /** همان منبع در تنظیمات (۶۵r ⇐ ۶۵s): بخش‌های هم‌پوشانِ `SettingsHues` همان خانوادهٔ بخشِ برنامه‌اند */
  @Test fun `نقشهٔ رنگِ تنظیمات با رنگِ بخش‌ها هم‌خوان است`() {
    val parts = mapOf("invoice" to V3Section.SALES, "reports" to V3Section.REPORTS, "goods" to V3Section.STOCK, "online" to V3Section.ORDERS)
    parts.forEach { (part, s) ->
      assertEquals("بخشِ «$part» ِ تنظیمات", SectionColors.family(s.hue), SectionColors.family(ir.vil3ntec.tohid.ui.v3.settings.SettingsHues.part(part)!!))
    }
  }

  @Test fun `نوشته روی تهِ‌رنگ AA است`() {
    for (dark in listOf(false, true)) for (h in SectionColors.DAY.keys) {
      val t = SectionColors.ofHue(h, dark)
      val where = "$h ${if (dark) "شب" else "روز"}"
      assertTrue("$where عدد ${SectionColors.contrast(t.strong, t.tint)}", SectionColors.contrast(t.strong, t.tint) >= 4.5)
      assertTrue("$where برچسب ${SectionColors.contrast(t.label, t.tint)}", SectionColors.contrast(t.label, t.tint) >= 4.5)
      assertTrue("$where نوشته", SectionColors.contrast(t.ink, t.tint) >= 7.0)
      //  مرز از تهِ‌رنگ پررنگ‌تر (روز تیره‌تر، شب روشن‌تر)
      val edgeL = SectionColors.luminance(t.edge)
      val tintL = SectionColors.luminance(t.tint)
      assertTrue("$where مرز", if (dark) edgeL > tintL else edgeL < tintL)
      //  ۶۸c: نشانهٔ کاشی روی زمینهٔ صفحه/کارتِ همان تم دست‌کم ۳:۱ (WCAG 1.4.11، آیکن) — روز روی سفید، شب روی کارتِ شب (۶۹ p3: کارتِ
      //  تازهٔ سه‌پله‌ای `DarkColors.surface` به‌جای #11151E ِ پیشین؛ کفِ ۴٫۵ همان)
      val ground = if (dark) DarkColors.surface else Color.White
      assertTrue("$where نشانه ${SectionColors.contrast(t.glyph, ground)}", SectionColors.contrast(t.glyph, ground) >= if (dark) 4.5 else 3.0)
    }
  }

  @Test fun `کنتراستِ WCAG درست حساب می‌شود`() {
    assertEquals(21.0, SectionColors.contrast(Color.Black, Color.White), 0.01)
    assertEquals(1.0, SectionColors.contrast(Color(0xFF777777), Color(0xFF777777)), 0.0001)
    assertEquals(4.48, SectionColors.contrast(Color(0xFF777777), Color.White), 0.02)
  }

  /** Viénot ۱۹۹۹ در RGB ِ خطی */
  private val protan = arrayOf(doubleArrayOf(0.11238, 0.88762, 0.0), doubleArrayOf(0.11238, 0.88762, 0.0), doubleArrayOf(0.00401, -0.00401, 1.0))
  private val deutan = arrayOf(doubleArrayOf(0.29275, 0.70725, 0.0), doubleArrayOf(0.29275, 0.70725, 0.0), doubleArrayOf(-0.02234, 0.02234, 1.0))

  private fun lin(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

  private fun lab(rgb: DoubleArray): DoubleArray {
    val (r, g, b) = Triple(rgb[0], rgb[1], rgb[2])
    val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
    fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
    return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
  }

  private fun seen(c: Color, m: Array<DoubleArray>?): DoubleArray {
    val v = doubleArrayOf(lin(c.red), lin(c.green), lin(c.blue))
    if (m == null) return lab(v)
    return lab(DoubleArray(3) { i -> (m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2]).coerceIn(0.0, 1.0) })
  }

  private fun deltaE(a: Color, b: Color, m: Array<DoubleArray>?): Double {
    val x = seen(a, m)
    val y = seen(b, m)
    return sqrt((x[0] - y[0]).pow(2) + (x[1] - y[1]).pow(2) + (x[2] - y[2]).pow(2))
  }

  @Test fun `جفت‌های کنارِ هم در کوررنگی هم جدا می‌مانند`() {
    val pairs = SectionColors.PAIRS.map { (a, b) -> a.hue to b.hue } + SectionColors.MONEY_PAIRS
    for (dark in listOf(false, true)) for ((a, b) in pairs) {
      val x = SectionColors.ofHue(a, dark).strong
      val y = SectionColors.ofHue(b, dark).strong
      for ((name, m) in listOf("بینا" to null, "پروتان" to protan, "دوتان" to deutan)) {
        val d = deltaE(x, y, m)
        assertTrue("$a/$b ${if (dark) "شب" else "روز"} $name ΔE=$d", d >= 15.0)
      }
    }
  }

  private val main: File = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
    .map { File(it, "ir/vil3ntec/tohid") }.first { it.isDirectory }

  @Test fun `هر کارتِ خلاصه رنگِ بخشش را دارد و گرادیان ندارد`() {
    val missing = mutableListOf<String>()
    main.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "V3Summary.kt" }.forEach { f ->
      val text = f.readText()
      Regex("""(?<!\w)V3Summary\(""").findAll(text).forEach { m ->
        var depth = 0
        var i = m.range.last
        while (i < text.length) {
          when (text[i]) { '(' -> depth++; ')' -> { depth--; if (depth == 0) break } }
          i++
        }
        if (!text.substring(m.range.last, i).contains("section = ")) missing += f.name
      }
    }
    assertTrue("خلاصهٔ بی رنگِ بخش: $missing", missing.isEmpty())
    val summary = File(main, "ui/v3/V3Summary.kt").readText() + File(main, "ui/theme/SectionColors.kt").readText()
    assertTrue("کارتِ خلاصه گرادیان نمی‌کشد", !summary.contains("Brush.") && !summary.contains("heroGradient"))
  }
}
