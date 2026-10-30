package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  هیچ بخشِ تنظیمات بی‌راه نمی‌ماند (VILL3N، G9).
 *
 *  هر بخشِ `SettingsLayout.GROUPS` (از **متنِ** `SettingsLayout.kt`) دقیقاً یک صفحه در
 *  `ui/v3/settings/SettingsPages.kt` (از **متنِ** همان فایل) و یک نامِ نشانی دارد، و
 *  مسیرش در فهرستِ مسیرها به صفحهٔ خودش می‌رسد — «امنیت ⇐ قفلِ برنامه» یک‌راست.
 *
 *  ⚠️ متن با متن: کامپیوتر پیش از کامپایل چند عبارت را در کدِ اندروید عوض می‌کند
 *  (`phrases` در `desktop/build.gradle.kts`، مثلاً «دوربین بارکدخوان» ⇐ «بارکدخوان»)،
 *  ولی این آزمون متنِ خام را می‌خواند؛ از شیءِ کامپایل‌شده فقط شمار و مسیرها.
 */
class SettingsLayoutTest {

  private fun src(rel: String): String = listOf(
    "src/main/java/ir/vil3ntec/tohid/$rel",
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/$rel",
    "android-native/app/src/main/java/ir/vil3ntec/tohid/$rel",
  ).map { File(it) }.firstOrNull { it.isFile }?.readText()
    ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val layoutText by lazy { src("ui/screens/SettingsLayout.kt") }

  /** `const val NAME = "…"` ِ همان فایل */
  private val consts: Map<String, String> by lazy {
    Regex("""const val ([A-Z_]+) = "([^"]+)"""").findAll(layoutText).associate { it.groupValues[1] to it.groupValues[2] }
  }

  private fun names(block: String): List<String> =
    Regex(""""([^"]+)"|\b([A-Z_]+)\b""").findAll(block).mapNotNull { m -> m.groupValues[1].ifEmpty { null } ?: consts[m.groupValues[2]] }.toList()

  private val links: Set<String> by lazy {
    val block = Regex("""val LINKS[^=]*= mapOf\(([^)]*)\)""").find(layoutText)?.groupValues?.get(1).orEmpty()
    Regex("""([A-Z_]+) to """).findAll(block).mapNotNull { consts[it.groupValues[1]] }.toSet()
  }

  /** بخش‌های گروه‌ها از متنِ `SettingsLayout.kt`، بی پیوندها */
  private val layoutSections: List<String> by lazy {
    Regex("""Group\(\s*[A-Z]+\s*,\s*listOf\(([^)]*)\)""").findAll(layoutText).flatMap { names(it.groupValues[1]) }.filterNot { it in links }.toList()
  }

  /** صفحه‌ها از متنِ `SettingsPages.kt`: `Page("…"` یا `Page(SettingsLayout.X` */
  private val pageKeys: List<String> by lazy {
    Regex("""Page\(\s*(?:"([^"]+)"|SettingsLayout\.([A-Z_]+))""").findAll(src("ui/v3/settings/SettingsPages.kt"))
      .mapNotNull { m -> m.groupValues[1].ifEmpty { null } ?: consts[m.groupValues[2]] }.toList()
  }

  @Test
  fun `هر بخشِ تنظیمات دقیقاً یک صفحه دارد و دقیقاً در یک گروه است`() {
    assertTrue("بخشی پیدا نشد — الگوی متن عوض شده؟", layoutSections.size >= 12)
    assertEquals("بخشِ تکراری در گروه‌ها", layoutSections.size, layoutSections.toSet().size)
    assertEquals("صفحهٔ تکراری", pageKeys.size, pageKeys.toSet().size)
    assertEquals(layoutSections.toSet(), pageKeys.toSet())
    assertEquals("متنِ گروه‌ها با شیءِ کامپایل‌شده نمی‌خواند", layoutSections.size, SettingsLayout.sections.size)
    SettingsLayout.sections.forEach { assertTrue("$it گروه ندارد", SettingsLayout.groupOf(it) != null) }
  }

  @Test
  fun `هر بخش نامِ نشانیِ خودش را دارد و مسیرش به صفحهٔ تنظیمات می‌رسد`() {
    val slugBlock = Regex("""val SECTION_SLUGS[^=]*= mapOf\(([\s\S]*?)\n\s*\)""").find(layoutText)?.groupValues?.get(1).orEmpty()
    val slugged = Regex("""(?:"([^"]+)"|\b([A-Z_]+)\b) to """").findAll(slugBlock)
      .mapNotNull { m -> m.groupValues[1].ifEmpty { null } ?: consts[m.groupValues[2]] }.toSet()
    assertEquals(layoutSections.toSet(), slugged)
    val table = V3Modules.table
    SettingsLayout.GROUPS.forEach { g ->
      val slugs = g.sections.filterNot(SettingsLayout::isLink).map { SettingsLayout.SECTION_SLUGS.getValue(it) }
      assertEquals("نامِ تکراری در ${g.title}", slugs.size, slugs.toSet().size)
      assertNotNull(g.title, table.resolve(SettingsPaths.group(g.title)))
      g.sections.forEach { s ->
        val path = SettingsPaths.section(s)
        val r = table.resolve(path)
        assertNotNull("$s ⇐ $path", r)
        if (!SettingsLayout.isLink(s) && r!!.route.pattern == "settings/{group}/{section}") {
          assertEquals(s, SettingsPaths.sectionOfSlug(g.title, path.substringAfterLast('/')))
        }
      }
    }
    assertEquals("settings/security/app-lock", SettingsPaths.section("قفل برنامه"))
    assertEquals("settings/data/sync", SettingsPaths.section("همگام‌سازی"))
    assertEquals("settings/general/navbar", SettingsPaths.section(SettingsLayout.NAV_BAR))
    assertEquals("settings/about", SettingsPaths.section(SettingsLayout.ABOUT_APP))
    assertEquals("settings/about", SettingsPaths.UPDATES)
  }

  @Test
  fun `گروه‌ها همان سرخط‌های r5 اند و هیچ‌کدام خالی نیست`() {
    assertEquals(
      listOf("عمومی", "امنیت", "فروش و پرداخت", "دستگاه‌ها", "داده‌ها", "درباره"),
      SettingsLayout.GROUPS.map { it.title },
    )
    SettingsLayout.GROUPS.forEach { assertTrue("${it.title} خالی است", it.sections.isNotEmpty()) }
    assertEquals(SettingsLayout.GROUPS.map { it.title }.toSet(), SettingsLayout.GROUP_SLUGS.keys)
    assertEquals(SettingsLayout.SECURITY, SettingsLayout.groupOf("قفل برنامه"))
    assertEquals(SettingsLayout.SECURITY, SettingsLayout.groupOf("دستگاه‌های وارد شده"))
    assertEquals(SettingsLayout.DATA, SettingsLayout.groupOf("همگام‌سازی"))
    assertEquals(SettingsLayout.GENERAL, SettingsLayout.groupOf("زبان"))
    assertEquals(SettingsLayout.GENERAL, SettingsLayout.groupOf(SettingsLayout.NAV_BAR))
    assertEquals(SettingsLayout.SALES, SettingsLayout.groupOf(SettingsLayout.PRINTER))
    assertEquals(SettingsLayout.DEVICES, SettingsLayout.groupOf(SettingsLayout.PERIPHERALS))
    assertEquals(null, SettingsLayout.groupOf("ناموجود"))
  }

  @Test
  fun `پیوندها بخش نیستند و به صفحهٔ دستگاه‌های جانبی می‌روند`() {
    assertEquals(setOf(SettingsLayout.PRINTER, SettingsLayout.PERIPHERALS), links)
    assertEquals("devices?tab=printers", SettingsLayout.LINKS[SettingsLayout.PRINTER])
    assertEquals("devices", SettingsLayout.LINKS[SettingsLayout.PERIPHERALS])
    SettingsLayout.LINKS.keys.forEach { assertTrue(it !in SettingsLayout.sections) }
  }

  @Test
  fun `نامِ ردیف و گروه به زبانِ صفحه — کلید عوض نمی‌شود`() {
    assertEquals("پشتیبان و بازگردانی", SettingsLayout.labelOf("داده‌ها"))
    assertEquals("بارکدخوان", SettingsLayout.labelOf(SettingsLayout.CAMERA))
    assertEquals("دستگاه‌های وصل", SettingsLayout.labelOf("دستگاه‌های وارد شده"))
    assertEquals("نسخه و به‌روزرسانی", SettingsLayout.labelOf(SettingsLayout.ABOUT_APP))
    assertEquals("امنیت", SettingsLayout.groupLabel(SettingsLayout.SECURITY))
    (SettingsLayout.sections + SettingsLayout.LINKS.keys).forEach { assertTrue(it, SettingsLayout.labelOf(it).isNotBlank()) }
  }

  @Test
  fun `کامپیوتر — بخشِ تازه کنارِ هم‌گروهش، ترتیبِ داده دست‌نخورده، بی بخش‌های فقط‌گوشی`() {
    val given = listOf(
      "هشدارها", "ظاهر", "تاریخ و رقم", "کالای منقضی", "قفل برنامه", "تغییر رمز حساب",
      "دستگاه‌های وارد شده", "کد شاگرد", "داده‌ها", SettingsLayout.ABOUT_APP,
    )
    val out = SettingsLayout.deskOrder(given)
    assertEquals(SettingsLayout.sections.toSet() - SettingsLayout.DESK_HIDDEN, out.toSet())
    assertEquals(out.size, out.toSet().size)
    assertEquals(given, out.filter { it in given })
    assertEquals(out.indexOf("ظاهر") + 1, out.indexOf("زبان"))
    assertTrue(out.indexOf("همگام‌سازی") < out.indexOf("داده‌ها"))
    SettingsLayout.LINKS.keys.forEach { assertTrue(it !in out) }
    assertEquals(SettingsLayout.sections.filterNot { it in SettingsLayout.DESK_HIDDEN }, SettingsLayout.deskSections)
  }
}
