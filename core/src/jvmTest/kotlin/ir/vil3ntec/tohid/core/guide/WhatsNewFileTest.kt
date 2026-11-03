package ir.vil3ntec.tohid.core.guide

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  OPS-05 — پروندهٔ واقعیِ `docs/WHATS-NEW-fa.md`: بی مشکل خوانده می‌شود، تازه‌ترین بخش همان خطِ نسخهٔ برنامه است
 *  (`versionBase` ِ `android-native/app/build.gradle.kts`)، و همان است که در برنامه بسته شد (`WhatsNewBundled`).
 */
class WhatsNewFileTest {

  private val repo: File by lazy {
    generateSequence(File("").absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "docs/WHATS-NEW-fa.md").isFile && File(it, "android-native").isDirectory }
      ?: error("ریشهٔ مخزن پیدا نشد (cwd=${File("").absoluteFile})")
  }

  private val text: String by lazy { File(repo, "docs/WHATS-NEW-fa.md").readText().replace("\r\n", "\n") }
  private val log by lazy { WhatsNew.parse(text) }

  @Test
  fun changelogParsesWithoutProblems() {
    assertEquals(emptyList(), log.problems, "docs/WHATS-NEW-fa.md:\n" + log.problems.joinToString("\n"))
    assertTrue(log.sections.isNotEmpty())
  }

  @Test
  fun newestSectionMatchesTheAppVersionLine() {
    val gradle = File(repo, "android-native/app/build.gradle.kts").readText()
    val versionBase = Regex("""val versionBase = "(\d+\.\d+)"""").find(gradle)?.groupValues?.get(1)
      ?: error("versionBase در android-native/app/build.gradle.kts پیدا نشد")
    assertEquals(versionBase, log.sections.first().line, "تازه‌ترین بخشِ «تازه‌ها» باید خطِ نسخهٔ برنامه ($versionBase) باشد")
  }

  @Test
  fun bundledTextIsTheFileOnDisk() {
    //  سطرِ تازه بی ساختِ دوباره به برنامه نمی‌رسد؛ کارِ generateWhatsNew همیشه از همین پرونده می‌سازد
    assertEquals(text, WhatsNewBundled.text)
  }

  @Test
  fun shippedNumbersGoDownTheFileAndOnlyKnownChannels() {
    for (ch in listOf(WhatsNew.CHANNEL_PREVIEW, WhatsNew.CHANNEL_BETA)) {
      val nums = log.sections.mapNotNull { it.shipped[ch] }
      assertEquals(nums.sortedDescending(), nums, "شمارهٔ $ch باید از بالا به پایین کم شود")
      assertEquals(nums.toSet().size, nums.size, "شمارهٔ تکراریِ $ch")
    }
    val channels = log.sections.flatMap { it.shipped.keys }.toSet()
    assertTrue(channels.all { it == WhatsNew.CHANNEL_PREVIEW || it == WhatsNew.CHANNEL_BETA }, channels.toString())
    //  بخشِ بی شماره فقط بالای بخش‌های شماره‌دار (کارِ تازه پس از این سامانه)
    val firstStamped = log.sections.indexOfFirst { it.shipped.isNotEmpty() }
    if (firstStamped >= 0) assertTrue(log.sections.drop(firstStamped).all { it.shipped.isNotEmpty() })
  }
}
