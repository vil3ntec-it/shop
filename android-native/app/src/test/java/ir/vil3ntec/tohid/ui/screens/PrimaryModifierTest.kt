package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  F0-19 — میان‌برِ صفحه‌ها روی مک با Cmd هم کار کند.
 *
 *  صفحه‌ها پیش از این `e.isCtrlPressed` را مستقیم می‌خواندند (Ctrl+S،
 *  Ctrl+F، Ctrl+Enter، Ctrl+P، Ctrl+E، Ctrl+N) و Cmd هیچ کاری نمی‌کرد.
 *  حالا همه از `isPrimaryModifier` (Ctrl یا Cmd) می‌گذرند. این آزمون
 *  کدِ صفحه‌ها را می‌خواند تا `isCtrlPressed`ِ تازه‌ای بی‌صدا برنگردد.
 *
 *  همین پوشهٔ آزمون در CIِ اندروید (`app/`) و کامپیوتر (`desktop/`) اجرا
 *  می‌شود؛ هر دو جا پوشهٔ صفحه‌ها پیدا می‌شود.
 */
class PrimaryModifierTest {

  private val screens: File? = listOf(
    "src/main/java/ir/vil3ntec/tohid/ui/screens",                          // android-native/app
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens",    // desktop
    "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens",       // ریشهٔ مخزن
  ).map { File(it) }.firstOrNull { it.isDirectory }

  @Test
  fun `هیچ صفحه‌ای isCtrlPressed را مستقیم نمی‌خواند`() {
    val dir = screens
    assertTrue("پوشهٔ صفحه‌ها پیدا نشد (cwd=${File(".").absoluteFile})", dir != null)
    val bad = dir!!.walkTopDown()
      .filter { it.isFile && it.extension == "kt" && it.name != "PrimaryModifier.kt" }
      .flatMap { f ->
        f.readLines().withIndex()
          .filter { (_, line) -> "isCtrlPressed" in line && !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*") }
          .map { (i, _) -> "${f.name}:${i + 1}" }
      }
      .toList()
    assertEquals("Ctrl تنها، بی Cmd: $bad — از isPrimaryModifier استفاده کنید", emptyList<String>(), bad)
  }

  @Test
  fun `isPrimaryModifier هم Ctrl و هم Cmd است`() {
    val src = File(screens, "PrimaryModifier.kt").readText()
    assertTrue(src.contains("isCtrlPressed || isMetaPressed"))
  }
}
