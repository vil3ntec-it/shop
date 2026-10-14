package ir.vil3ntec.tohid.sync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 *  F0-02 — هیچ جای برنامه دفتر را از راهِ قدیم (`Syncer.run`) نمی‌فرستد.
 *
 *  `Syncer.run()` همان push/pullِ قدیمی است که مستقیم در دفتر ادغام می‌کند
 *  و سایهٔ Sync v1 را جلو نمی‌برد؛ v1 همان ردیف‌ها را «تغییرِ محلی» می‌بیند
 *  و دوباره می‌فرستد — فیلدهای `$inc` (پرداخت‌ها، مانده‌ها) دو بار جمع
 *  می‌شوند. دکمهٔ «همگام‌سازی حالا» در `Profile.kt` تنها جایی بود که هنوز
 *  صدایش می‌زد.
 *
 *  ⚠️ این سنجه متنِ کد را می‌خواند، چون دکمهٔ Compose بی دستگاه آزمودنی
 *  نیست. توضیح‌ها (comment) پیش از جست‌وجو برداشته می‌شوند تا شرحِ
 *  همین قاعده خودش سنجه را قرمز نکند.
 *
 *  کلاسِ `Syncer` عمداً می‌ماند: `AutoSync` از `refreshLicense`ِ آن
 *  استفاده می‌کند؛ پس فقط فراخوانیِ `run` ممنوع است.
 */
class LegacySyncCallTest {

  private val legacyRun = Regex("""\bSyncer\s*\([^)]*\)\s*\.\s*run\s*\(""")

  /** ریشهٔ کدِ اندروید — هم از `android-native/app` (اندروید) و هم از `desktop` (کامپیوتر). */
  private fun mainSources(): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      for (rel in listOf("src/main/java", "app/src/main/java", "android-native/app/src/main/java")) {
        val f = File(dir, "$rel/ir/vil3ntec/tohid")
        if (f.isDirectory && File(f, "ui/screens/Profile.kt").isFile) return File(dir, rel)
      }
      dir = dir.parentFile
    }
    fail("کدِ اندروید پیدا نشد (از ${File("").absolutePath})")
    error("unreachable")
  }

  private fun withoutComments(src: String): String =
    src.replace(Regex("""/\*[\s\S]*?\*/"""), " ").replace(Regex("""//[^\n]*"""), " ")

  @Test
  fun `دکمهٔ همگام‌سازی حالا دورِ v1 را می‌اندازد نه Syncer_run`() {
    val src = withoutComments(File(mainSources(), "ir/vil3ntec/tohid/ui/screens/Profile.kt").readText())
    assertTrue("Profile.kt هنوز Syncer(...).run() را صدا می‌زند", !legacyRun.containsMatchIn(src))
    assertTrue("Profile.kt باید SyncV1Worker.now(context) را صدا بزند", src.contains("SyncV1Worker.now(context)"))
  }

  @Test
  fun `هیچ جای برنامه Syncer_run را صدا نمی‌زند`() {
    val root = mainSources()
    val offenders = root.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .filter { legacyRun.containsMatchIn(withoutComments(it.readText())) }
      .map { it.relativeTo(root).invariantSeparatorsPath }
      .toList()
    assertEquals("راهِ قدیمِ دفتر هنوز صدا زده می‌شود: $offenders", emptyList<String>(), offenders)
  }
}
