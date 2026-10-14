package ir.vil3ntec.tohid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  F0-18 — رنگِ ثابت در کدِ صفحه‌ها، حالتِ تاریک را می‌شکند.
 *
 *  `Color(0xFFFF9800)` در `DeskCustomers` روی کارتِ روشن ۲٫۱:۱ خوانده می‌شد و
 *  پنجرهٔ «میان‌برهای صفحه‌کلید» در حالتِ تاریک سفید می‌ماند. رنگ جایش در
 *  `ui/theme` است (`Shop.colors.*` که برای روز و شب دو مقدار دارد).
 *
 *  این آزمون کدِ برنامه را می‌خواند و هر `Color(0x…)` یا زمینهٔ سفید/سیاهِ
 *  توپرِ تازه بیرون از `ui/theme` را می‌گیرد. فهرستِ پایین فقط جاهایی است
 *  که رنگِ ثابت عمدی است و حالتِ تاریک را نمی‌شکند؛ شمار هر فایل سقف است و
 *  بالا نمی‌رود. رنگِ تازه ⇐ توکن در `Theme.kt`، نه عدد در صفحه.
 *
 *  همین پوشهٔ آزمون در CIِ اندروید (`app/`) و کامپیوتر (`desktop/`) اجرا
 *  می‌شود؛ هر دو جا پوشه‌ها پیدا می‌شوند.
 */
class HardcodedColorsTest {

  private fun dir(vararg candidates: String): File? = candidates.map { File(it) }.firstOrNull { it.isDirectory }

  /** کدِ مشترکِ اندروید و کامپیوتر */
  private val shared = dir(
    "src/main/java/ir/vil3ntec/tohid",                         // android-native/app
    "../android-native/app/src/main/java/ir/vil3ntec/tohid",   // desktop
    "android-native/app/src/main/java/ir/vil3ntec/tohid",      // ریشهٔ مخزن
  )

  /** کدِ فقط‌کامپیوتر (پنجرهٔ میان‌برها، اسکنر، چاپ) */
  private val desk = dir(
    "src/main/kotlin/ir/vil3ntec/tohid",                       // desktop
    "../../desktop/src/main/kotlin/ir/vil3ntec/tohid",         // android-native/app
    "desktop/src/main/kotlin/ir/vil3ntec/tohid",               // ریشهٔ مخزن
  )

  /** `Color(0x…)` یا `background(Color.White)` / `background(Color.Black)` */
  private val pattern = Regex(
    """\bColor\(\s*0x|\bbackground\(\s*(?:androidx\.compose\.ui\.graphics\.)?Color\.(?:White|Black)\s*\)"""
  )

  /**
   *  رنگِ ثابتِ عمدی — سقفِ شمار در هر فایل. فقط کم شود، زیاد نه.
   */
  private val allowed = mapOf(
    //  سربرگ: پالتِ روز و شبِ خودش را دارد (BAR_DAY / BAR_NIGHT)
    "ui/screens/TopBar.kt" to 29,
    //  تصویرسازیِ صفحهٔ خوش‌آمد و نشانِ طلاییِ VIP — رنگِ برند، نه رنگِ سطح
    "ui/screens/Welcome.kt" to 14,
    "ui/screens/WelcomeDecor.kt" to 14,
    "ui/screens/Vip.kt" to 12,
    //  پالتِ دسته‌ایِ نمودارِ دونات + آیکونِ «تعدادِ فاکتور» (≥۳:۱ در هر دو تم)
    "ui/screens/DeskDashboard.kt" to 8,
    //  ایستگاهِ میانیِ گرادیانِ کارتِ بالای داشبورد
    "ui/screens/Dashboard.kt" to 1,
    //  سبزِ واتساپ — رنگِ برندِ همان برنامه
    "ui/screens/Debtors.kt" to 1,
    //  بومِ پیش‌نمایشِ عکسِ کالا (عکسِ پس‌زمینه‌سفید روی بومِ روشن دیده می‌شود)
    "ui/screens/PhotoStudioDialog.kt" to 1,
    //  متنِ تیره روی دکمهٔ گرادیانِ روشن (همان onPrimary)
    "ui/screens/Tohid.kt" to 1,
    //  روی تصویرِ دوربین — زمینه همیشه تیره است
    "ui/screens/ScanSheet.kt" to 1,
    "ui/screens/Sale.kt" to 1,
    "scan/BarcodeScanner.kt" to 1,
    "desk:scan/BarcodeScannerDesk.kt" to 6,
    //  چاپ — کاغذ همیشه سفید است
    "desk:print/ReportPrintDesk.kt" to 1,
  )

  private fun count(f: File): Int =
    f.readLines()
      .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
      .sumOf { pattern.findAll(it).count() }

  private fun scan(root: File, prefix: String): Map<String, Int> =
    root.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .map { it.relativeTo(root).invariantSeparatorsPath to it }
      .filterNot { (rel, _) -> rel.startsWith("ui/theme/") }
      .map { (rel, f) -> prefix + rel to count(f) }
      .filter { it.second > 0 }
      .toMap()

  @Test
  fun `هیچ صفحه‌ای رنگِ ثابتِ تازه ندارد — رنگ از Theme می‌آید`() {
    val root = shared
    assertTrue("پوشهٔ کدِ برنامه پیدا نشد (cwd=${File(".").absoluteFile})", root != null)
    val found = scan(root!!, "") + (desk?.let { scan(it, "desk:") } ?: emptyMap())
    val bad = found.filter { (rel, n) -> n > (allowed[rel] ?: 0) }
      .map { (rel, n) -> "$rel: $n > ${allowed[rel] ?: 0}" }
      .sorted()
    assertEquals(
      "رنگِ ثابت بیرون از ui/theme: $bad — از Shop.colors (یا توکنِ تازه در Theme.kt) استفاده کنید",
      emptyList<String>(), bad,
    )
  }

  @Test
  fun `پنجرهٔ میان‌برهای کامپیوتر تمِ برنامه را می‌گیرد`() {
    //  فقط در CIِ کامپیوتر یا از ریشهٔ مخزن پوشه‌اش هست
    val main = desk?.let { File(it, "desktop/Main.kt") } ?: return
    val src = main.readText()
    val help = src.substringAfter("private fun ShortcutsHelp()", "")
    assertTrue("ShortcutsHelp پیدا نشد", help.isNotEmpty())
    assertTrue("پنجرهٔ میان‌برها باید داخلِ TohidTheme باشد", Regex("""TohidTheme\([^)]*\)\s*\{\s*ShortcutsHelp\(\)""").containsMatchIn(src))
    assertTrue("زمینهٔ پنجرهٔ میان‌برها باید از Shop.colors بیاید", "Shop.colors" in help)
  }
}
