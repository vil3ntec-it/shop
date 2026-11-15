package ir.vil3ntec.tohid.rate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  RATE-01 — نگهبانِ «بی کتابخانهٔ نظرِ Google Play در هیچ ساختی»: «ستاره می‌دهم» ِ برگهٔ خودمان یک دکمه است و راهنمای Google
 *  می‌گوید دکمه API ِ نظرِ درون‌برنامه را صدا نزند (سهمیه ⇒ شاید هیچ کارتی نیاید) و صفحهٔ Play را باز کند. پس در هیچ
 *  پوشهٔ سورسِ برنامهٔ دکان، خریدار و کامپیوتر (`src/main`، Play، بازار، پیش‌نمایش…) ارجاعی به `com.google.android.play.core`
 *  یا `com.google.android.play:review` نیست، و Play فقط با `market://details?id=` و `https://play.google.com/store/apps/details?id=`
 *  باز می‌شود. روی کامپیوتر و اندروید هر دو.
 */
class PlayReviewSplitTest {

  private val banned = listOf("com.google.android.play.core", "com.google.android.play:review", "ReviewManagerFactory")

  /** ریشهٔ مخزن از هر پوشهٔ کارِ آزمون (`android-native/app` یا `desktop`) */
  private val repo: File =
    listOf(File("."), File(".."), File("../.."), File("../../..")).map { it.absoluteFile.normalize() }
      .firstOrNull { File(it, "android-native/app/build.gradle.kts").isFile }
      ?: error("ریشهٔ مخزن پیدا نشد (cwd=${File(".").absoluteFile})")

  private fun offenders(dir: File): List<String> =
    dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "kts", "java", "xml", "toml") && it.name != "PlayReviewSplitTest.kt" }
      .filter { f -> f.readText().let { t -> banned.any { it in t } } }
      .map { it.relativeTo(repo).invariantSeparatorsPath }.toList()

  private fun checkApp(rel: String) {
    val root = File(repo, rel)
    assertTrue("$rel پیدا نشد", File(root, "src/main").isDirectory)
    val bad = offenders(File(root, "src")) + offenders(File(root, "build.gradle.kts"))
    assertEquals("$rel: کدِ نظرِ درون‌برنامهٔ Play در یکی از ساخت‌ها", emptyList<String>(), bad)
    val folders = File(root, "src").listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("review") }.map { it.name }
    assertEquals("$rel: پوشهٔ ساختِ جدای نظرِ Play دیگر نیست", emptyList<String>(), folders)
  }

  @Test fun shopHasNoPlayReviewCodeInAnyBuild() = checkApp("android-native/app")

  @Test fun shopperHasNoPlayReviewCodeInAnyBuild() = checkApp("customer-android/app")

  @Test fun desktopAndCoreHaveNoPlayReviewCode() {
    assertEquals(emptyList<String>(), offenders(File(repo, "desktop/src")) + offenders(File(repo, "core/src")))
  }

  @Test fun rateButtonOpensThePlayListingDirectly() {
    val shop = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/rate/RateStoreAndroid.kt").readText()
    assertTrue("Play ⇒ صفحهٔ برنامه", Regex("""RateTarget\.PLAY -> openFirst\(context, RateLinks\.playListing\(""").containsMatchIn(shop))
    val shopper = File(repo, "customer-android/app/src/main/java/ir/vil3ntec/tohid/customer/RateAndroid.kt").readText()
    assertTrue("خریدار: Play ⇒ صفحهٔ برنامه", Regex("""RateHook\(RateTarget\.PLAY\) \{ listing\(activity\) \}""").containsMatchIn(shopper))
    assertTrue("خریدار: همان درهای core", "RateLinks.playListing(" in shopper)
  }
}
