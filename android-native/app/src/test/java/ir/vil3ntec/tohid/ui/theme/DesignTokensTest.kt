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
 *  توکن‌های طراحی — MASTER-SPEC ۵.۴ و VISUAL-DIRECTION «نسخهٔ ۲».
 *
 *  ۱. گِردیِ همهٔ نقش‌ها (`Shape`، `Radius`، `ShopShapes`) همان جدولِ نسخهٔ ۲ است:
 *     کنترل ۱۲، کارت ۱۶، پنل/پنجره ۲۰، تراشه قرصی. عدد فقط با تصمیمِ ظاهری این‌جا
 *     عوض می‌شود (نسخهٔ ۱: کامپیوتر ۶/۸/۱۰، لمسی ۱۸/۲۰/۲۸ — نقدِ صاحب محصول در ۱۴۰۵/۰۷/۲۷:
 *     «همهٔ جعبه‌ها چهارگوش و تیزند»).
 *  ۲. یک پلهٔ گِردی برای همهٔ سکوها (کامپیوتر = لمسی = وب)، ۸ تا ۲۰ و روی پلهٔ ۲.
 *     فاصله‌ها روی پلهٔ ۴ (شبکهٔ ۸ با نیم‌پلهٔ ۴).
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
  fun `گِردیِ نقش‌ها همان جدولِ نسخهٔ ۲ است — کنترل ۱۲، کارت ۱۶، پنل ۲۰، تراشه قرصی`() {
    assertEquals(round(12, 12), Shape.button)
    assertEquals(round(12, 12), Shape.field)
    assertEquals(round(16, 16), Shape.card)
    assertEquals(round(20, 20), Shape.cardLarge)
    assertEquals(round(20, 20), Shape.dialog)
    assertEquals(RoundedCornerShape(topStart = pick(20, 20), topEnd = pick(20, 20)), Shape.sheet)
    assertEquals(RoundedCornerShape(999.dp), Shape.chip)
    assertEquals(RoundedCornerShape(999.dp), Shape.badge)
    assertEquals(round(10, 10), Shape.icon)

    assertEquals(pick(12, 12), Radius.sm)
    assertEquals(pick(16, 16), Radius.md)
    assertEquals(pick(20, 20), Radius.lg)

    //  کادرِ متن و منوی متریال از extraSmall ⇒ کنترل؛ کارت از medium؛ پنجره از extraLarge
    assertEquals(round(12, 12), ShopShapes.extraSmall)
    //  تراشه‌های متریال از small ⇒ قرصیِ کامل («chips/pills full»)
    assertEquals(RoundedCornerShape(999.dp), ShopShapes.small)
    assertEquals(round(16, 16), ShopShapes.medium)
    assertEquals(round(16, 16), ShopShapes.large)
    assertEquals(round(20, 20), ShopShapes.extraLarge)

    assertEquals(12.dp, Corner.CONTROL)
    assertEquals(16.dp, Corner.CARD)
    assertEquals(20.dp, Corner.PANEL)
  }

  @Test
  fun `فاصله و سایه همان عددهای پیشین‌اند`() {
    assertEquals(listOf(4, 8, 12, 16, 20, 24, 32).map { it.dp }, listOf(Space.xxs, Space.xs, Space.sm, Space.md, Space.lg, Space.xl, Space.xxl))
    assertEquals(listOf(0, 1, 6, 10).map { it.dp }, listOf(Elevation.flat, Elevation.raised, Elevation.floating, Elevation.overlay))
    //  نسخهٔ ۲: سایهٔ نرمِ کارت و چیزِ شناور — کارت پایین‌تر از شناور
    assertEquals(listOf(6, 14).map { it.dp }, listOf(Elevation.card, Elevation.lifted))
  }

  @Test
  fun `همهٔ گِردی‌ها از جدولِ Corner می‌آیند — یک پله برای همهٔ سکوها`() {
    for (c in Corner.ALL) {
      assertTrue("${c.name}: گوشهٔ کامپیوتر ${c.desk} بیرون از ۸ تا ۲۰", c.desk in 8.dp..20.dp)
      assertTrue("${c.name}: گوشهٔ لمسی ${c.touch} بیرون از ۸ تا ۲۰", c.touch in 8.dp..20.dp)
      //  نسخهٔ ۲: کامپیوتر، گوشی و وب یک عدد دارند (نسخهٔ ۱ دو ستونِ جدا داشت)
      assertEquals("${c.name}: کامپیوتر و لمسی یکی", c.desk, c.touch)
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
    //  java.awt در classpathِ کامپایلِ آزمونِ اندروید (android.jar) نیست ولی JVMِ آزمون دارد —
    //  پس با بازتاب صدا زده می‌شود تا همین آزمون در CIِ اندروید و کامپیوتر هر دو کامپایل شود
    val fontClass = Class.forName("java.awt.Font")
    val trueType = fontClass.getField("TRUETYPE_FONT").getInt(null)
    val createFont = fontClass.getMethod("createFont", Int::class.javaPrimitiveType, File::class.java)
    val canDisplayUpTo = fontClass.getMethod("canDisplayUpTo", String::class.java)
    for (f in fonts) {
      val font = createFont.invoke(null, trueType, f)
      val missing = canDisplayUpTo.invoke(font, text) as Int
      assertEquals("${f.name} نویسهٔ «${text.getOrNull(missing)}» را ندارد", -1, missing)
    }
  }
}
