package ir.vil3ntec.tohid.core.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  نامِ برنامه «Daily Shop» است (خواستِ صاحب‌کار ۱۴۰۵/۰۸/۲۵: «اسم برنامه رو هم daily Shop بزار») — همین املای لاتین در هر زبان.
 *  پیش‌نمایش «Daily Shop · پیش‌نمایش» است تا کنارِ نسخهٔ اصلی روی گوشی دو برنامهٔ هم‌نام دیده نشود.
 *  فقط نامِ دیدنی؛ شناسه‌ها (`com.vill3n.dailyshop`، `ir.vil3ntec.tohid`، `AppId` ِ نصاب، پوشهٔ داده) همان‌اند.
 */
class AppNameTest {

  private val repo = DesktopPhrases.repo()

  @After fun reset() {
    I18n.source = { Lang.FA }
  }

  private fun appName(path: String): String {
    val xml = repo.resolve(path).readText()
    val m = Regex("""<string name="app_name">([^<]*)</string>""").findAll(xml).toList()
    assertEquals("یک و فقط یک app_name در $path", 1, m.size)
    return m.single().groupValues[1]
  }

  @Test fun `نامِ گوشیِ نسخهٔ اصلی و پیش‌نمایش`() {
    assertEquals("Daily Shop", appName("android-native/app/src/main/res/values/strings.xml"))
    assertEquals("Daily Shop · پیش‌نمایش", appName("android-native/app/src/preview/res/values/strings.xml"))
    val manifest = repo.resolve("android-native/app/src/main/AndroidManifest.xml").readText()
    assertTrue("برچسبِ برنامه از app_name", manifest.contains("android:label=\"@string/app_name\""))
  }

  @Test fun `نامِ درونِ برنامه در هر زبان همان Daily Shop`() {
    for (l in Lang.entries) {
      I18n.source = { l }
      assertEquals("$l", "Daily Shop", Str.appName)
    }
  }

  @Test fun `نصابِ ویندوز، مک و آیفون همان نام را نشان می‌دهند و شناسه‌ها همان‌اند`() {
    val iss = repo.resolve("desktop/installer/Tohid.iss").readText()
    assertTrue(iss.contains("\nAppName=Daily Shop\n"))
    assertTrue(iss.contains("\nUninstallDisplayName=Daily Shop\n"))
    assertTrue(iss.contains("Name: \"{autoprograms}\\Daily Shop\"; Filename: \"{app}\\Tohid.exe\""))
    assertTrue("AppId ِ نصاب", iss.contains("AppId={{8C2E4F1A-5B7D-4E3C-9A61-2F0D7B3E5C14}"))
    assertTrue("پوشهٔ نصب", iss.contains("DefaultDirName={localappdata}\\Tohid"))
    val gradle = repo.resolve("desktop/build.gradle.kts").readText()
    assertTrue(gradle.contains("packageName = \"Daily Shop\"") && gradle.contains("dockName = \"Daily Shop\""))
    assertTrue("نامِ درونیِ بسته", gradle.contains("packageName = \"Tohid\""))
    assertTrue("bundleID ِ مک", gradle.contains("bundleID = \"ir.vil3ntec.tohid.desktop\""))
    assertTrue(repo.resolve("ios/iosApp/Configuration/Config.xcconfig").readText().contains("\nAPP_NAME=Daily Shop"))
  }
}
