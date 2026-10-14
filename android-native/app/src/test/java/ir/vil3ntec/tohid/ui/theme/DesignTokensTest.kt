package ir.vil3ntec.tohid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  توکن‌های طراحی — MASTER-SPEC ۵.۴.
 *
 *  ۱. آزمونِ مرجع: گِردیِ همهٔ نقش‌ها (`Shape`، `Radius`، `ShopShapes`) همان عددِ پیش از
 *     یکی شدن در `Corner` است. پس یکی کردن ظاهر را عوض نکرده است. عدد فقط با تصمیمِ ظاهری
 *     این‌جا عوض می‌شود.
 *  ۲. کامپیوتر: گوشه‌ها ۴ تا ۱۲ (RULES «ظاهرِ کامپیوتری»). فاصله‌ها روی پلهٔ ۴ (۵.۴).
 *  ۳. تایپوگرافیِ وزیرمتن با آزمونِ پشتو (۵.۴): همهٔ وزن‌های برنامه و وب حرف‌های ویژهٔ پشتو،
 *     رقم‌های فارسی و عربی و نیم‌فاصله را دارند.
 *
 *  `Platform.DESKTOP` ثابت است: CIِ اندروید ستونِ لمسی و CIِ کامپیوتر ستونِ کامپیوتر را می‌سنجد.
 */
class DesignTokensTest {

  private val desk = Platform.DESKTOP
  private fun pick(desk: Int, touch: Int): Dp = (if (this.desk) desk else touch).dp
  private fun round(desk: Int, touch: Int) = RoundedCornerShape(pick(desk, touch))

  @Test
  fun `گِردیِ نقش‌ها همان عددهای پیشین است`() {
    assertEquals(round(6, 18), Shape.button)
    assertEquals(round(6, 16), Shape.field)
    assertEquals(round(8, 20), Shape.card)
    assertEquals(round(10, 24), Shape.cardLarge)
    assertEquals(round(10, 28), Shape.dialog)
    assertEquals(RoundedCornerShape(topStart = pick(10, 28), topEnd = pick(10, 28)), Shape.sheet)
    assertEquals(RoundedCornerShape(999.dp), Shape.chip)
    assertEquals(RoundedCornerShape(999.dp), Shape.badge)
    assertEquals(round(6, 12), Shape.icon)

    assertEquals(pick(6, 16), Radius.sm)
    assertEquals(pick(8, 20), Radius.md)
    assertEquals(pick(12, 28), Radius.lg)

    assertEquals(round(4, 12), ShopShapes.extraSmall)
    assertEquals(round(6, 16), ShopShapes.small)
    assertEquals(round(8, 18), ShopShapes.medium)
    assertEquals(round(10, 20), ShopShapes.large)
    assertEquals(round(12, 28), ShopShapes.extraLarge)
  }

  @Test
  fun `فاصله و سایه همان عددهای پیشین‌اند`() {
    assertEquals(listOf(4, 8, 12, 16, 20, 24, 32).map { it.dp }, listOf(Space.xxs, Space.xs, Space.sm, Space.md, Space.lg, Space.xl, Space.xxl))
    assertEquals(listOf(0, 1, 6, 10).map { it.dp }, listOf(Elevation.flat, Elevation.raised, Elevation.floating, Elevation.overlay))
  }

  @Test
  fun `همهٔ گِردی‌ها از جدولِ Corner می‌آیند`() {
    for (c in Corner.ALL) {
      assertTrue("${c.name}: گوشهٔ کامپیوتر ${c.desk} بیرون از ۴ تا ۱۲", c.desk in 4.dp..12.dp)
      assertTrue("${c.name}: گوشهٔ لمسی ${c.touch} بیرون از ۱۲ تا ۲۸", c.touch in 12.dp..28.dp)
      assertEquals("${c.name}: روی پلهٔ ۲", 0f, c.desk.value % 2f + c.touch.value % 2f)
      assertEquals(if (desk) c.desk else c.touch, c.current)
    }
    assertEquals("نامِ تکراری در Corner", Corner.ALL.size, Corner.ALL.map { it.name }.toSet().size)
  }

  @Test
  fun `فاصله‌ها روی پلهٔ ۴‌اند`() {
    for (s in listOf(Space.xxs, Space.xs, Space.sm, Space.md, Space.lg, Space.xl, Space.xxl)) {
      assertEquals("فاصلهٔ $s", 0f, s.value % 4f)
    }
  }

  @Test
  fun `وزیرمتن حرف‌های پشتو و رقم‌های فارسی را دارد`() {
    //  ټ ډ ړ ږ ښ ګ ڼ ۍ ې ځ څ ئ — بی این‌ها نامِ کالا و مشتریِ پشتو مربعِ خالی می‌شود
    val text = "ټډړږښګڼۍېځڅئ" + "۰۱۲۳۴۵۶۷۸۹" + "٠١٢٣٤٥٦٧٨٩" + "\u200c"
    val dirs = listOf(
      listOf("src/main/res/font", "../android-native/app/src/main/res/font", "android-native/app/src/main/res/font"),
      listOf("../../fonts", "../fonts", "fonts"),  //  وب
    ).map { c -> c.map { File(it) }.firstOrNull { it.isDirectory } }
    assertTrue("پوشهٔ قلمِ برنامه پیدا نشد (cwd=${File(".").absoluteFile})", dirs[0] != null)
    val fonts = dirs.filterNotNull().flatMap { d -> d.listFiles().orEmpty().filter { it.name.lowercase().startsWith("vazirmatn") && it.extension == "ttf" } }
    assertTrue("قلمِ وزیرمتن پیدا نشد", fonts.size >= 4)
    for (f in fonts) {
      val font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, f)
      assertEquals("${f.name} نویسهٔ «${text.getOrNull(font.canDisplayUpTo(text))}» را ندارد", -1, font.canDisplayUpTo(text))
    }
  }
}
