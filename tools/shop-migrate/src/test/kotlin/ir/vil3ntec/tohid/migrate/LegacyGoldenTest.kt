package ir.vil3ntec.tohid.migrate

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  نیمهٔ این‌جاییِ نگهبانِ «محاسبهٔ قدیم».
 *
 *  `golden/fixture-seed1.json` = `fixture --seed 1 --anomalies` و
 *  `golden/legacy-golden.txt` = `golden` روی همان فایل.
 *  نیمهٔ دیگر `android-native/app/src/test/.../MigrationGoldenTest.kt` است که
 *  همین فایل‌ها را با **موتورِ واقعیِ** برنامه می‌سنجد. پس کپیِ `LegacyFigures`
 *  و موتورِ برنامه نمی‌توانند بی‌صدا از هم جدا شوند.
 */
class LegacyGoldenTest {

  private fun resource(name: String): String =
    javaClass.classLoader.getResource(name)!!.readText(Charsets.UTF_8)

  @Test fun committedFixtureIsReproducible() {
    val fresh = Fixtures.backupJson(Fixtures.generate(1, Fixtures.Size.SMALL, anomalies = true))
    assertEquals(resource("golden/fixture-seed1.json"), fresh)
  }

  @Test fun portMatchesCommittedEngineNumbers() {
    val d = BackupReader.parse(resource("golden/fixture-seed1.json"))
    val expected = resource("golden/legacy-golden.txt").lines().filter { it.isNotBlank() }
    assertEquals(expected, LegacyFigures.golden(d))
  }
}
