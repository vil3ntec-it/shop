package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  گروه‌بندیِ تنظیماتِ گوشی هیچ تنظیمی را جا نمی‌اندازد (phone-ui).
 *
 *  عنوانِ هر `SettingsSection(...)` از متنِ `Settings.kt` و `LocaleSection.kt`
 *  خوانده می‌شود و باید دقیقاً یک بار در `SettingsLayout.GROUPS` باشد، و
 *  `Settings.kt` هر گروه را با همان سرخط بکشد.
 */
class SettingsLayoutTest {

  private fun src(name: String): String = listOf(
    "src/main/java/ir/vil3ntec/tohid/ui/screens/$name",
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/$name",
    "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/$name",
  ).map { File(it) }.firstOrNull { it.isFile }?.readText()
    ?: error("$name پیدا نشد (cwd=${File(".").absoluteFile})")

  private val sectionTitle = Regex("""SettingsSection\(\s*icon\s*=[^\n]*\n\s*title\s*=\s*"([^"]+)"""")

  /**
   *  عنوان‌های گروه‌ها از متنِ خودِ `SettingsLayout.kt`. کامپیوتر پیش از کامپایل
   *  چند عبارت را عوض می‌کند (`phrases` در `desktop/build.gradle.kts`، مثلاً
   *  «دوربین بارکدخوان» ⇐ «بارکدخوان»)، پس متن با متن سنجیده می‌شود نه با شیءِ کامپایل‌شده.
   */
  private val layoutTitles: List<String> by lazy {
    Regex("""Group\(\s*[A-Z]+\s*,\s*listOf\(([^)]*)\)""").findAll(src("SettingsLayout.kt"))
      .flatMap { g -> Regex("\"([^\"]+)\"").findAll(g.groupValues[1]).map { it.groupValues[1] } }
      .toList()
  }

  @Test
  fun `هر بخشِ تنظیمات دقیقاً در یک گروه است`() {
    val found = (sectionTitle.findAll(src("Settings.kt")) + sectionTitle.findAll(src("LocaleSection.kt")))
      .map { it.groupValues[1] }.toList()
    assertTrue("بخشی پیدا نشد — الگوی متن عوض شده؟", found.size >= 10)
    assertEquals("بخشِ تکراری در کد", found.size, found.toSet().size)
    assertEquals(found.toSet(), layoutTitles.toSet())
    assertEquals("متنِ گروه‌ها با شیءِ کامپایل‌شده نمی‌خواند", layoutTitles.size, SettingsLayout.sections.size)
    assertEquals("بخشِ تکراری در گروه‌ها", SettingsLayout.sections.size, SettingsLayout.sections.toSet().size)
    SettingsLayout.sections.forEach { assertTrue("$it گروه ندارد", SettingsLayout.groupOf(it) != null) }
  }

  @Test
  fun `گروه‌ها همان شش سرخطِ سفارش‌اند و هیچ‌کدام خالی نیست`() {
    assertEquals(
      listOf("عمومی", "فروش و پرداخت", "امنیت", "دستگاه‌ها", "همگام‌سازی و پشتیبان", "درباره"),
      SettingsLayout.GROUPS.map { it.title },
    )
    SettingsLayout.GROUPS.forEach { assertTrue("${it.title} خالی است", it.sections.isNotEmpty()) }
    assertEquals(SettingsLayout.SECURITY, SettingsLayout.groupOf("قفل برنامه"))
    assertEquals(null, SettingsLayout.groupOf("ناموجود"))
  }

  @Test
  fun `Settings_kt هر گروه را به همان ترتیب می‌کشد`() {
    val s = src("Settings.kt")
    val order = Regex("""SettingsGroup\(\s*SettingsLayout\.([A-Z]+)""").findAll(s).map { it.groupValues[1] }.toList()
    assertEquals(listOf("GENERAL", "SALES", "SECURITY", "DEVICES", "DATA", "ABOUT"), order)
  }
}
