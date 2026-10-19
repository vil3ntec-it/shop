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
   *  بخش‌های گروه‌ها از **متنِ** خودِ `SettingsLayout.kt` — نه از شیءِ کامپایل‌شده.
   *
   *  کامپیوتر پیش از کامپایل چند عبارت را در کدِ اندروید عوض می‌کند (`phrases` در
   *  `desktop/build.gradle.kts`، مثلاً «دوربین بارکدخوان» ⇐ «بارکدخوان»)، ولی این
   *  آزمون متنِ خام را می‌خواند؛ پس متن با متن سنجیده می‌شود و از شیءِ کامپایل‌شده
   *  فقط شمار. ردیف‌های گروه یا رشته‌اند یا نامِ یک `const val` در همان فایل؛ پیوندها
   *  (کلیدهای `LINKS`) بخش نیستند و کنار می‌روند.
   */
  private val layoutSections: List<String> by lazy {
    val text = src("SettingsLayout.kt")
    val consts = Regex("""const val ([A-Z_]+) = "([^"]+)"""").findAll(text)
      .associate { it.groupValues[1] to it.groupValues[2] }
    val linksBlock = Regex("""val LINKS[^=]*= mapOf\(([^)]*)\)""").find(text)?.groupValues?.get(1).orEmpty()
    val links = Regex("""([A-Z_]+) to """).findAll(linksBlock).mapNotNull { consts[it.groupValues[1]] }.toSet()
    Regex("""Group\(\s*[A-Z]+\s*,\s*listOf\(([^)]*)\)""").findAll(text)
      .flatMap { g ->
        Regex(""""([^"]+)"|\b([A-Z_]+)\b""").findAll(g.groupValues[1]).mapNotNull { m ->
          m.groupValues[1].ifEmpty { null } ?: consts[m.groupValues[2]]
        }
      }
      .filterNot { it in links }
      .toList()
  }

  @Test
  fun `هر بخشِ تنظیمات دقیقاً در یک گروه است`() {
    val found = (sectionTitle.findAll(src("Settings.kt")) + sectionTitle.findAll(src("LocaleSection.kt")))
      .map { it.groupValues[1] }.toList()
    assertTrue("بخشی پیدا نشد — الگوی متن عوض شده؟", found.size >= 10)
    assertEquals("بخشِ تکراری در کد", found.size, found.toSet().size)
    assertEquals(found.toSet(), layoutSections.toSet())
    assertEquals("بخشِ تکراری در گروه‌ها", layoutSections.size, layoutSections.toSet().size)
    assertEquals("متنِ گروه‌ها با شیءِ کامپایل‌شده نمی‌خواند", layoutSections.size, SettingsLayout.sections.size)
    SettingsLayout.sections.forEach { assertTrue("$it گروه ندارد", SettingsLayout.groupOf(it) != null) }
  }

  @Test
  fun `گروه‌ها همان سرخط‌های بندِ C (r5) اند و هیچ‌کدام خالی نیست`() {
    assertEquals(
      listOf("عمومی", "امنیت", "فروش و پرداخت", "دستگاه‌ها", "داده‌ها", "درباره"),
      SettingsLayout.GROUPS.map { it.title },
    )
    SettingsLayout.GROUPS.forEach { assertTrue("${it.title} خالی است", it.sections.isNotEmpty()) }
    assertEquals(SettingsLayout.SECURITY, SettingsLayout.groupOf("قفل برنامه"))
    //  بندِ C: «دستگاه‌های وصل» زیرِ امنیت، همگام‌سازی کنارِ پشتیبان، زبان و نوارِ پایین در عمومی
    assertEquals(SettingsLayout.SECURITY, SettingsLayout.groupOf("دستگاه‌های وارد شده"))
    assertEquals(SettingsLayout.DATA, SettingsLayout.groupOf("همگام‌سازی"))
    assertEquals(SettingsLayout.GENERAL, SettingsLayout.groupOf("زبان"))
    assertEquals(SettingsLayout.GENERAL, SettingsLayout.groupOf(SettingsLayout.NAV_BAR))
    assertEquals(SettingsLayout.SALES, SettingsLayout.groupOf(SettingsLayout.PRINTER))
    assertEquals(null, SettingsLayout.groupOf("ناموجود"))
  }

  @Test
  fun `Settings_kt هر گروه را به همان ترتیب می‌کشد`() {
    val s = src("Settings.kt")
    val order = Regex("""SettingsGroup\(\s*SettingsLayout\.([A-Z]+)""").findAll(s).map { it.groupValues[1] }.toList()
    assertEquals(listOf("GENERAL", "SECURITY", "SALES", "DEVICES", "DATA", "ABOUT"), order)
  }

  @Test
  fun `پیوندها بخش نیستند، مسیر دارند و Settings_kt همه را می‌کشد`() {
    assertEquals("nav-editor", SettingsLayout.LINKS[SettingsLayout.NAV_BAR])
    assertEquals("devices", SettingsLayout.LINKS[SettingsLayout.PRINTER])
    SettingsLayout.LINKS.keys.forEach { assertTrue(it !in SettingsLayout.sections) }
    val s = src("Settings.kt")
    val drawn = Regex("""SettingsLink\(SettingsLayout\.([A-Z_]+)\)""").findAll(s).map { it.groupValues[1] }.toSet()
    assertEquals(setOf("NAV_BAR", "PRINTER"), drawn)
  }

  @Test
  fun `نامِ کوتاهِ ردیف — کلید عوض نمی‌شود`() {
    assertEquals("پشتیبان و بازگردانی", SettingsLayout.labelOf("داده‌ها"))
    assertEquals("بارکدخوان", SettingsLayout.labelOf(SettingsLayout.CAMERA))
    assertEquals("دستگاه‌های وصل", SettingsLayout.labelOf("دستگاه‌های وارد شده"))
    assertEquals("هشدارها", SettingsLayout.labelOf("هشدارها"))
  }

  @Test
  fun `کامپیوتر — بخشِ تازه کنارِ هم‌گروهش، ترتیبِ دادهٔ AppRoot دست‌نخورده`() {
    //  همان فهرستِ امروزِ `AppRoot` برای `DeskSettings`
    val given = listOf(
      "هشدارها", "ظاهر", "تاریخ و رقم", "کالای منقضی", "قفل برنامه", "تغییر رمز حساب",
      "دستگاه‌های وارد شده", "کد شاگرد", "داده‌ها", "درباره برنامه",
    )
    val out = SettingsLayout.deskOrder(given)
    assertEquals(SettingsLayout.sections.toSet() - SettingsLayout.DESK_HIDDEN, out.toSet())
    assertEquals(out.size, out.toSet().size)
    assertEquals(given, out.filter { it in given })
    assertEquals(out.indexOf("ظاهر") + 1, out.indexOf("زبان"))
    assertTrue(out.indexOf("همگام‌سازی") < out.indexOf("داده‌ها"))
    SettingsLayout.LINKS.keys.forEach { assertTrue(it !in out) }
  }
}
