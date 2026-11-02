package ir.vil3ntec.tohid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 *  «شکلِ قدیم هرگز» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۱۱) — رابطِ قدیم (پوشهٔ `ui/legacy`، رونوشت‌های `…Legacy`، پرچمِ
 *  `UiV3` و کلیدِ «رابطِ تازه (نسخهٔ ۳)») از برنامه پاک شد؛ رابطِ نسخهٔ ۳ تنها رابطِ گوشی، کامپیوتر و آیفون است.
 *  این آزمون نمی‌گذارد هیچ‌کدام برگردد. (جای `UiV3Test` ِ پیشین.)
 */
class NoLegacyUiTest {

  private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }
    .first { File(it, "android-native").isDirectory }

  private val ui = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui")

  /** کدِ برنامه‌ها: اندروید، کامپیوتر (افزوده‌های خودش) و آیفون (شیم‌ها) */
  private val sources: List<File> =
    listOf("android-native/app/src/main/java", "desktop/src/main/kotlin", "ios/shared/src/iosMain/kotlin")
      .map { File(repo, it) }.filter { it.isDirectory }
      .flatMap { d -> d.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

  @Test
  fun `پوشهٔ رابطِ قدیم و رونوشت‌های Legacy دیگر نیستند`() {
    assertFalse("ui/legacy برگشته است", File(ui, "legacy").exists())
    val copies = ui.walkTopDown().filter { it.isFile && it.name.endsWith("Legacy.kt") }
      .map { it.relativeTo(ui).invariantSeparatorsPath }.toList()
    assertEquals(emptyList<String>(), copies)
  }

  @Test
  fun `نه پرچمی، نه کلیدی، نه صفحهٔ رونوشتی که رابطِ قدیم را برگرداند`() {
    val copy = Regex("""\bfun (?:[A-Za-z0-9_.]+\.)?[A-Z]\w*Legacy\(""")
    val hits = sources.filter { f ->
      val t = f.readText()
      t.contains("UiV3") || t.contains("\"ui-v3\"") || t.contains("\"ui_v3\"") || copy.containsMatchIn(t)
    }.map { it.relativeTo(repo).invariantSeparatorsPath }
    assertEquals(emptyList<String>(), hits)
  }

  @Test
  fun `برگه‌ی ساخت‌های کامپیوتر و آیفون فایلِ پاک‌شده‌ای را نام نمی‌برند`() {
    val lists = listOf("ios/shared/ios-exclude.txt", "desktop/build.gradle.kts", "ios/shared/build.gradle.kts")
      .map { File(repo, it) }.filter { it.isFile }
    val hits = lists.filter { it.readText().contains("ui/legacy/") }.map { it.name }
    assertEquals(emptyList<String>(), hits)
  }
}
