package ir.vil3ntec.tohid.rate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  RATE-01 — نگهبانِ جداییِ کتابخانهٔ نظرِ Google Play: APK ِ سایت/گیت‌هاب و کافه‌بازار (`src/main` + `src/review-none`)
 *  هیچ ارجاعی به `com.google.android.play.core` یا `com.google.android.play:review` ندارد؛ فقط `src/review-play` دارد و
 *  وابستگی فقط زیرِ `-Pstore=play` است. همین برای برنامهٔ خریدار (`customer-android`). روی کامپیوتر و اندروید هر دو.
 */
class PlayReviewSplitTest {

  private val banned = listOf("com.google.android.play.core", "com.google.android.play:review")

  /** ریشهٔ یک برنامهٔ اندروید از هر پوشهٔ کارِ آزمون (`android-native/app` یا `desktop`) */
  private fun app(rel: String): File? =
    listOf(".", "..", "../..", "../../..").map { File(it, rel) }.firstOrNull { File(it, "build.gradle.kts").isFile && File(it, "src/main").isDirectory }

  private fun shop(): File =
    listOf(File("."), File("../android-native/app"), File("android-native/app")).firstOrNull { File(it, "src/review-none").isDirectory }
      ?: error("android-native/app پیدا نشد (cwd=${File(".").absoluteFile})")

  private fun offenders(dir: File): List<String> =
    dir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java" || it.extension == "xml") }
      .filter { f -> f.readText().let { t -> banned.any { it in t } } }
      .map { it.path }.toList()

  private fun checkApp(root: File) {
    assertEquals("${root.path}: کدِ Play در APK ِ بی Play", emptyList<String>(), offenders(File(root, "src/main")) + offenders(File(root, "src/review-none")))
    val play = File(root, "src/review-play").walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
    assertTrue("${root.path}: نسخهٔ Play خودِ API را دارد", "com.google.android.play.core.review.ReviewManagerFactory" in play)
    val gradle = File(root, "build.gradle.kts").readLines()
    val deps = gradle.filter { "com.google.android.play:review" in it }
    assertEquals("${root.path}: یک وابستگیِ نظرِ Play", 1, deps.size)
    assertTrue("${root.path}: وابستگی فقط با -Pstore=play: ${deps.single()}", deps.single().trim().startsWith("if (rateStore == \"play\") implementation("))
    val split = gradle.single { "src/review-play/java" in it }
    assertTrue("${root.path}: پوشهٔ Play فقط برای Play، بقیه review-none: $split",
      Regex("""srcDir\(if \(rateStore == "play"\) "src/review-play/java" else "src/review-none/java"\)""").containsMatchIn(split))
  }

  @Test fun shopApkWithoutPlayHasNoPlayReviewCode() = checkApp(shop())

  @Test fun shopperApkWithoutPlayHasNoPlayReviewCode() {
    val root = app("customer-android/app") ?: return   //  پوشهٔ برنامهٔ خریدار در همین مخزن کنارِ android-native است
    checkApp(root)
  }

  @Test fun desktopNeverCompilesEitherReviewFolder() {
    val desk = listOf(File("."), File("../desktop"), File("desktop")).firstOrNull { File(it, "src/shim").isDirectory } ?: return
    val gradle = File(desk, "build.gradle.kts").readText()
    assertTrue("کامپیوتر فقط src/main ِ اندروید را کامپایل می‌کند", "review-play" !in gradle && "review-none" !in gradle)
    assertEquals(emptyList<String>(), offenders(File(desk, "src")))
  }
}
